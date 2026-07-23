package com.metallum.client.metal.render;

import com.metallum.client.metal.render.bridge.MetalNativeBridge;
import com.metallum.client.metal.render.mtl.MTLColorWriteMask;
import com.metallum.client.metal.render.mtl.MTLCompareFunction;
import com.metallum.client.metal.render.mtl.MTLPixelFormat;
import com.metallum.client.metal.render.mtl.MTLRenderPipelineDescriptor;
import com.metallum.client.metal.render.mtl.MTLVertexDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Metal render pipeline created from Iris shaderpack MSL source, bypassing
 * Mojang's {@code RenderPipeline} / {@code MetalCrossShaderCompiler.compile}
 * path entirely.
 *
 * <p>This class is in the {@code com.metallum.client.metal.render} package so
 * it can access {@link MetalDevice}'s package-private
 * {@link MetalDevice#getOrCompileFunction(String, String)} method and
 * {@link MetalDevice#metalDeviceHandle()}.
 *
 * <p>Construction steps:
 * <ol>
 *   <li>Compile the vertex and fragment MSL source strings into native
 *       {@code MTLFunction} handles via
 *       {@link MetalDevice#getOrCompileFunction}.</li>
 *   <li>Create an {@link MTLRenderPipelineDescriptor}, set the compiled
 *       functions, an empty {@link MTLVertexDescriptor} (no vertex buffers —
 *       Iris composite/final programs generate fullscreen positions from
 *       {@code vertex_id} in the vertex shader), and the color/depth pixel
 *       formats.</li>
 *   <li>Call {@link MetalNativeBridge#metallum_MTLDevice_makeRenderPipelineState}
 *       to create the native {@code MTLRenderPipelineState} handle.</li>
 * </ol>
 *
 * <p>Two pipeline-state handles are created: one with depth
 * ({@code Depth32Float}) and one without ({@code Invalid}), matching the
 * pattern in {@link MetalCompiledRenderPipeline}. The correct one is selected
 * at draw time via {@link #pipelineState(boolean)}.
 */
final class MetalIrisPipeline implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("MetalUniversal");

    private static final Pattern VERTEX_ENTRY_PATTERN =
            Pattern.compile("\\bvertex\\s+\\w+\\s+(\\w+)\\s*\\(");
    private static final Pattern FRAGMENT_ENTRY_PATTERN =
            Pattern.compile("\\bfragment\\s+\\w+\\s+(\\w+)\\s*\\(");

    private final String name;
    private final MemorySegment pipelineWithDepth;
    private final MemorySegment pipelineWithoutDepth;
    private final MemorySegment depthStencilState;
    private boolean closed;

    /**
     * @param device         the active Metal device
     * @param name           debug name (e.g. "final", "composite1")
     * @param vertexMsl      compiled MSL vertex shader source
     * @param fragmentMsl    compiled MSL fragment shader source
     * @param colorFormat    the color attachment pixel format
     * @param hasDepth       whether a depth attachment will be used
     */
    MetalIrisPipeline(
            final MetalDevice device,
            final String name,
            final String vertexMsl,
            final String fragmentMsl,
            final MTLPixelFormat colorFormat,
            final boolean hasDepth
    ) {
        this.name = name;

        String vertexEntry = extractEntryPoint(vertexMsl, VERTEX_ENTRY_PATTERN, "main0");
        String fragmentEntry = extractEntryPoint(fragmentMsl, FRAGMENT_ENTRY_PATTERN, "main0");

        MemorySegment vertexFn = device.getOrCompileFunction(vertexMsl, vertexEntry);
        MemorySegment fragmentFn = device.getOrCompileFunction(fragmentMsl, fragmentEntry);

        if (MetalNativeBridge.isNullHandle(vertexFn)) {
            throw new IllegalStateException("Failed to compile Iris vertex MSL function for '" + name + "'");
        }
        if (MetalNativeBridge.isNullHandle(fragmentFn)) {
            throw new IllegalStateException("Failed to compile Iris fragment MSL function for '" + name + "'");
        }

        this.pipelineWithDepth = hasDepth
                ? createPipelineState(device, vertexFn, fragmentFn, colorFormat, MTLPixelFormat.Depth32Float)
                : MemorySegment.NULL;
        this.pipelineWithoutDepth = createPipelineState(device, vertexFn, fragmentFn, colorFormat, MTLPixelFormat.Invalid);

        this.depthStencilState = hasDepth
                ? MetalNativeBridge.MTLDevice_makeDepthStencilState(
                        device.metalDeviceHandle(), MTLCompareFunction.Always, 0)
                : MemorySegment.NULL;

        LOGGER.info("[MetalUniversal] MetalIrisPipeline '{}' created (colorFormat={}, hasDepth={})",
                name, colorFormat, hasDepth);
    }

    private static MemorySegment createPipelineState(
            final MetalDevice device,
            final MemorySegment vertexFn,
            final MemorySegment fragmentFn,
            final MTLPixelFormat colorFormat,
            final MTLPixelFormat depthFormat
    ) {
        try (MTLRenderPipelineDescriptor desc = new MTLRenderPipelineDescriptor()) {
            desc.setCompiledFunctions(vertexFn, fragmentFn);
            // Empty vertex descriptor: no vertex buffers, no attributes.
            // Iris composite/final vertex shaders use vertex_id to generate
            // fullscreen triangle positions.
            try (MTLVertexDescriptor vertexDesc = new MTLVertexDescriptor()) {
                desc.setVertexDescriptor(vertexDesc);
            }
            desc.setAttachmentFormats(colorFormat, depthFormat, MTLPixelFormat.Invalid);
            // Disable blending for now (final pass writes directly)
            desc.disableBlending(MTLColorWriteMask.All.value);

            MemorySegment pipeline = MetalNativeBridge.metallum_MTLDevice_makeRenderPipelineState(
                    device.metalDeviceHandle(), desc.handle());
            if (MetalNativeBridge.isNullHandle(pipeline)) {
                throw new IllegalStateException("metallum_MTLDevice_makeRenderPipelineState returned null");
            }
            return pipeline;
        }
    }

    private static String extractEntryPoint(final String msl, final Pattern pattern, final String fallback) {
        Matcher matcher = pattern.matcher(msl);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    String name() {
        return name;
    }

    MemorySegment pipelineState(final boolean useDepth) {
        return useDepth && pipelineWithDepth != MemorySegment.NULL
                ? pipelineWithDepth
                : pipelineWithoutDepth;
    }

    MemorySegment depthStencilState() {
        return depthStencilState;
    }

    boolean hasDepth() {
        return depthStencilState != MemorySegment.NULL;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        // Pipeline states and depth-stencil states are managed by MetalNativeBridge.
        // We release them through the device's deferred release queue to ensure
        // they're not in use by the GPU when freed.
        // For simplicity in this beta, we leak them — the pipeline lives for the
        // lifetime of MetalIrisRenderingPipeline which is only destroyed on
        // shaderpack reload (infrequent).
        LOGGER.debug("[MetalUniversal] MetalIrisPipeline '{}' closed (native handles deferred)", name);
    }
}

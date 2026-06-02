package com.metallum.client.metal.render;

import com.metallum.client.metal.render.bridge.MetalNativeBridge;

import java.lang.foreign.MemorySegment;

public final class MetalRenderPipelineDescriptor implements AutoCloseable {
    private final MemorySegment handle;
    private boolean closed;

    public MetalRenderPipelineDescriptor() {
        this.handle = MetalNativeBridge.INSTANCE.metallum_MTLRenderPipelineDescriptor_create();
    }

    public MemorySegment handle() {
        return this.handle;
    }

    public boolean setFunctions(
            final MetalDevice device,
            final String vertexSource,
            final String fragmentSource,
            final String vertexEntry,
            final String fragmentEntry
    ) {
        return MetalNativeBridge.INSTANCE.metallum_MTLRenderPipelineDescriptor_setFunctions(
                this.handle,
                device.metalDeviceHandle(),
                vertexSource,
                fragmentSource,
                vertexEntry,
                fragmentEntry
        );
    }

    public void setVertexDescriptor(final MetalVertexDescriptor vertexDescriptor) {
        MetalNativeBridge.INSTANCE.metallum_MTLRenderPipelineDescriptor_setVertexDescriptor(
                this.handle,
                vertexDescriptor.handle()
        );
    }

    public void setAttachmentFormats(final long colorFormat, final long depthFormat, final long stencilFormat) {
        MetalNativeBridge.INSTANCE.metallum_MTLRenderPipelineDescriptor_setAttachmentFormats(
                this.handle,
                colorFormat,
                depthFormat,
                stencilFormat
        );
    }

    public void setBlendState(
            final int enabled,
            final long srcRgb,
            final long dstRgb,
            final long opRgb,
            final long srcAlpha,
            final long dstAlpha,
            final long opAlpha,
            final long writeMask
    ) {
        MetalNativeBridge.INSTANCE.metallum_MTLRenderPipelineDescriptor_setBlendState(
                this.handle,
                enabled,
                srcRgb,
                dstRgb,
                opRgb,
                srcAlpha,
                dstAlpha,
                opAlpha,
                writeMask
        );
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            MetalNativeBridge.INSTANCE.metallum_release_object(this.handle);
        }
    }
}

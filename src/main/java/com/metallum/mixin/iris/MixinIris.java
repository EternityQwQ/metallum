package com.metallum.mixin.iris;

import com.metallum.client.metal.iris.MetalIrisBridge;
import com.metallum.client.metal.iris.MetalIrisRenderingPipeline;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.VanillaRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redirects Iris's pipeline creation and initialization on non-OpenGL
 * backends (Metal / Vulkan).
 *
 * <p><b>Canceled init methods</b> (at HEAD, when
 * {@link MetalIrisBridge#isNonGlBackend()} returns {@code true}):
 * <ul>
 *   <li>{@code duringRenderSystemInit} &mdash; calls {@code setDebug()} which
 *       casts the backend to {@code GlDevice} → {@code ClassCastException} on
 *       Metal.</li>
 *   <li>{@code onRenderSystemInit} &mdash; calls {@code GL.getCapabilities()}
 *       (for parallel shader compile detection) and {@code loadShaderpack()}
 *       which would crash without the {@link MixinStandardMacros} redirect.
 *       Canceled entirely to avoid all GL calls; shaderpack loading is
 *       deferred to user-triggered {@code reload()} (via ShaderPackScreen or
 *       reload keybind), which calls {@code loadShaderpack()} →
 *       {@code loadExternalShaderpack()} safely (with
 *       {@link MixinStandardMacros} redirecting {@code getGlVersion}).</li>
 * </ul>
 *
 * <p><b>Pipeline creation redirect</b> ({@code createPipeline}):
 * <p>When a shaderpack is loaded ({@code currentPack != null}) on a non-GL
 * backend, returns {@link MetalIrisRenderingPipeline} instead of
 * {@code IrisRenderingPipeline}. This avoids the massive GL resource
 * creation (hundreds of framebuffers, textures, shader programs) that
 * {@code IrisRenderingPipeline}'s constructor performs via native GL calls.
 *
 * <p>If {@code MetalIrisRenderingPipeline} construction fails, falls back to
 * {@link VanillaRenderingPipeline} (which is safe &mdash;
 * {@link MixinVanillaRenderingPipeline} cancels its {@code beginLevelRendering}).
 *
 * <p>If no shaderpack is loaded ({@code currentPack == null}), does not
 * cancel &mdash; lets Iris's original code return
 * {@code new VanillaRenderingPipeline()}.
 *
 * <p>On a real OpenGL backend this mixin is a no-op:
 * {@code isNonGlBackend()} returns {@code false}.
 *
 * <p>{@code remap = false} because these are Iris's own methods (not Mojang
 * obfuscated methods).
 */
@Mixin(Iris.class)
public class MixinIris {
    private static final Logger LOGGER = LoggerFactory.getLogger("MetalUniversal");

    @Shadow
    private static ShaderPack currentPack;

    @Inject(method = "duringRenderSystemInit", at = @At("HEAD"), cancellable = true, remap = false)
    private static void metallum$cancelDuringInitOnNonGl(CallbackInfo ci) {
        if (MetalIrisBridge.isNonGlBackend()) {
            ci.cancel();
        }
    }

    @Inject(method = "onRenderSystemInit", at = @At("HEAD"), cancellable = true, remap = false)
    private static void metallum$cancelOnRenderSystemInitOnNonGl(CallbackInfo ci) {
        if (MetalIrisBridge.isNonGlBackend()) {
            ci.cancel();
        }
    }

    @Inject(method = "createPipeline", at = @At("HEAD"), cancellable = true, remap = false)
    private static void metallum$createMetalPipelineOnNonGl(NamespacedId dimensionId, CallbackInfoReturnable<WorldRenderingPipeline> cir) {
        if (MetalIrisBridge.isNonGlBackend() && currentPack != null) {
            try {
                ProgramSet programs = currentPack.getProgramSet(dimensionId);
                cir.setReturnValue(new MetalIrisRenderingPipeline(programs));
            } catch (Exception e) {
                LOGGER.error("Failed to create MetalIrisRenderingPipeline, falling back to vanilla rendering", e);
                cir.setReturnValue(new VanillaRenderingPipeline());
            }
        }
    }
}

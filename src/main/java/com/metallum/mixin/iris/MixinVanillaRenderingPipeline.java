package com.metallum.mixin.iris;

import com.metallum.client.metal.iris.MetalIrisBridge;
import net.irisshaders.iris.pipeline.VanillaRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels {@link VanillaRenderingPipeline#beginLevelRendering()} on non-OpenGL
 * backends.
 *
 * <p>Iris's {@code MixinLevelRenderer.iris$setupPipeline} injects at HEAD of
 * {@code LevelRenderer.render} and calls {@code pipeline.beginLevelRendering()}
 * every frame. When no shaderpack is loaded (which is the case on Metal, since
 * {@link MixinIris} cancels {@code Iris.onRenderSystemInit()}), the pipeline is
 * a {@code VanillaRenderingPipeline}.
 *
 * <p>{@code VanillaRenderingPipeline.beginLevelRendering()} calls
 * {@code GL.getCapabilities().GL_ARB_clip_control} followed by
 * {@code GlStateManager._glUseProgram(0)}. The {@code GL.getCapabilities()}
 * call throws {@code IllegalStateException} on a Metal backend where no OpenGL
 * context exists.
 *
 * <p>Canceling this method at HEAD is safe because:
 * <ul>
 *   <li>The {@code GL_ARB_clip_control} check and {@code glClipControl} call
 *       are GL-specific and irrelevant on Metal.</li>
 *   <li>{@code GlStateManager._glUseProgram(0)} resets the active shader
 *       program to 0 — unnecessary on Metal where shader programs are managed
 *       by the Metal backend.</li>
 *   <li>All other {@code VanillaRenderingPipeline} methods are no-ops (stubs),
 *       so the pipeline object remains in a consistent state.</li>
 *   <li>The remaining GL calls in {@code iris$setupPipeline} are either safe
 *       ({@code backupAndDisableCullingState} just toggles
 *       {@code smartCull}) or guarded by conditions that evaluate to false
 *       on non-GL backends ({@code shouldActivateWireframe} returns false
 *       because {@code areDebugOptionsEnabled} is redirected to false by
 *       {@link MixinGLDebug}; {@code HandRenderer} methods return early
 *       because {@code isPackInUseQuick()} returns false).</li>
 * </ul>
 *
 * <p>{@code remap = false} because this is Iris's own method (not a Mojang
 * obfuscated method).
 */
@Mixin(VanillaRenderingPipeline.class)
public class MixinVanillaRenderingPipeline {
    @Inject(method = "beginLevelRendering", at = @At("HEAD"), cancellable = true, remap = false)
    private void metallum$cancelBeginLevelRenderingOnNonGl(CallbackInfo ci) {
        if (MetalIrisBridge.isNonGlBackend()) {
            ci.cancel();
        }
    }
}

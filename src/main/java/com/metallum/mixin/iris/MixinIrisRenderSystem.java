package com.metallum.mixin.iris;

import com.metallum.client.metal.iris.MetalIrisBridge;
import net.irisshaders.iris.gl.IrisRenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels {@link IrisRenderSystem#initRenderer()} on non-OpenGL backends.
 *
 * <p>{@code IrisRenderSystem.initRenderer()} performs OpenGL capability
 * detection and allocates GL resources (DSA state, projection matrix buffer,
 * sampler array). On a Metal backend there is no OpenGL context, so these
 * operations would either read meaningless stub values or attempt raw GL calls
 * that crash.
 *
 * <p>The static initializer ({@code <clinit>}) of {@code IrisRenderSystem}
 * runs before this method body (class loading happens before method execution).
 * The {@code <clinit>} is made safe by {@link MixinRenderSystem_StubCaps}
 * which installs a stub {@code GLCapabilities} beforehand. This mixin then
 * cancels the method body so the GL-dependent init code never runs.
 *
 * <p>The fields that {@code initRenderer()} would have set
 * ({@code dsaState}, {@code hasMultibind}, {@code supportsCompute},
 * {@code supportsTesselation}, {@code samplers}) remain at their default
 * values ({@code null}/{@code false}). This is acceptable because
 * {@link MixinIris} cancels {@code Iris.onRenderSystemInit()} which prevents
 * {@code loadShaderpack()}, so Iris's rendering pipeline never activates and
 * these fields are never read.
 *
 * <p>{@code remap = false} because this is Iris's own method (not a Mojang
 * obfuscated method).
 */
@Mixin(IrisRenderSystem.class)
public class MixinIrisRenderSystem {
    @Inject(method = "initRenderer", at = @At("HEAD"), cancellable = true, remap = false)
    private static void metallum$cancelInitRendererOnNonGl(CallbackInfo ci) {
        if (MetalIrisBridge.isNonGlBackend()) {
            ci.cancel();
        }
    }
}

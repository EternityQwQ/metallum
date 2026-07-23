package com.metallum.mixin.iris;

import com.metallum.client.metal.iris.MetalIrisBridge;
import net.irisshaders.iris.config.IrisConfig;
import net.irisshaders.iris.gl.GLDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Diverts {@link GLDebug#reloadDebugState()} on non-OpenGL backends.
 *
 * <p>Iris's {@code MixinRenderSystem.iris$onRendererInit} calls
 * {@code GLDebug.reloadDebugState()} as the second step of its init sequence.
 * The method body is:
 * <pre>{@code
 * if (Iris.getIrisConfig().areDebugOptionsEnabled() &&
 *     (GL.getCapabilities().GL_KHR_debug || GL.getCapabilities().OpenGL43)) {
 *     debugState = new KHRDebugState();
 * } else {
 *     debugState = new UnsupportedDebugState();
 * }
 * }</pre>
 *
 * <p>On a Metal backend, {@code GL.getCapabilities()} throws
 * {@code IllegalStateException} (no OpenGL context). This mixin redirects the
 * {@code areDebugOptionsEnabled()} call to return {@code false} on non-GL
 * backends. Java's {@code &&} short-circuit evaluation then skips the
 * {@code GL.getCapabilities()} reads entirely, and the {@code else} branch
 * runs &mdash; setting {@code debugState} to a no-op
 * {@code UnsupportedDebugState}.
 *
 * <p><b>Why redirect instead of cancel:</b> Canceling the method at HEAD
 * leaves {@code debugState} as {@code null}. Iris's {@code MixinGui} calls
 * {@code GLDebug.pushGroup(1000, "GUI")} during every frame's HUD render
 * (wrapping {@code Hud.extractRenderState}), which dereferences
 * {@code debugState} and throws {@code NullPointerException}. Letting the
 * method run (with a forced {@code false} predicate) sets the no-op state
 * and avoids the NPE.
 *
 * <p>On a real OpenGL backend this redirect is a no-op: the original
 * {@code areDebugOptionsEnabled()} is called unchanged.
 *
 * <p>{@code remap = false} because {@code reloadDebugState} is Iris's own
 * method (not a Mojang obfuscated method).
 */
@Mixin(GLDebug.class)
public class MixinGLDebug {
    @Redirect(
            method = "reloadDebugState",
            at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/config/IrisConfig;areDebugOptionsEnabled()Z"),
            remap = false
    )
    private static boolean metallum$forceDebugDisabledOnNonGl(IrisConfig config) {
        if (MetalIrisBridge.isNonGlBackend()) {
            return false;
        }
        return config.areDebugOptionsEnabled();
    }
}

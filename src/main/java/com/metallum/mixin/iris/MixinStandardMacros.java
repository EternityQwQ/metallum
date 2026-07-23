package com.metallum.mixin.iris;

import com.metallum.client.metal.iris.MetalIrisBridge;
import net.irisshaders.iris.gl.shader.StandardMacros;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redirects {@link StandardMacros#getGlVersion(int)} on non-GL backends to
 * return a static version string instead of calling
 * {@code GlStateManager._getString(name)} → {@code GL11C.glGetString()} —
 * a native GL call that causes a <b>SIGSEGV</b> on Metal (no OpenGL context).
 *
 * <p>{@code getGlVersion} is called from
 * {@code StandardMacros.createStandardEnvironmentDefines()} during shaderpack
 * loading ({@code Iris.loadExternalShaderpack()} →
 * {@code new ShaderPack(..., StandardMacros.createStandardEnvironmentDefines(), ...)}).
 * The returned value is used as the {@code MC_GL_VERSION} and
 * {@code MC_GLSL_VERSION} shader macro defines.
 *
 * <p>Returning {@code "460"} (corresponding to GL 4.6.0 / GLSL 4.60) is
 * appropriate because:
 * <ul>
 *   <li>Metal's feature set is roughly comparable to GL 4.6 (SSBOs, image
 *       load/store, compute shaders, multiple render targets are all
 *       supported).</li>
 *   <li>Shaderpacks use this macro for feature detection (e.g.,
 *       {@code #if MC_GL_VERSION >= 130} for GLSL 1.30+ features).</li>
 *   <li>The value is only used as a preprocessor define — no actual GL
 *       version checking occurs.</li>
 * </ul>
 *
 * <p>The other GL-dependent methods in {@code StandardMacros}
 * ({@code getVendor()}, {@code getRenderer()}, {@code getOsString()}) are safe
 * on Metal: {@code getVendor} and {@code getRenderer} use
 * {@code RenderSystem.getDevice().getDeviceInfo()} (which returns Metal device
 * info), and {@code getOsString} uses {@code Util.getPlatform()}.
 *
 * <p>{@code remap = false} because {@code StandardMacros} is an Iris class,
 * not a Mojang obfuscated class.
 */
@Mixin(StandardMacros.class)
public class MixinStandardMacros {
    @Inject(method = "getGlVersion", at = @At("HEAD"), cancellable = true, remap = false)
    private static void metallum$redirectGetGlVersionOnNonGl(int name, CallbackInfoReturnable<String> cir) {
        if (MetalIrisBridge.isNonGlBackend()) {
            cir.setReturnValue("460");
        }
    }
}

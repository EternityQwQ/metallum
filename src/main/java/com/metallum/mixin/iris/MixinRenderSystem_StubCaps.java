package com.metallum.mixin.iris;

import com.metallum.Metallum;
import com.metallum.mixin.accessor.MetallumGpuDeviceAccessor;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * Installs a stub {@link GLCapabilities} on non-OpenGL backends so that Iris's
 * many {@code GL.getCapabilities()} reads do not throw
 * {@code IllegalStateException: No GLCapabilities instance set for the current
 * thread}.
 *
 * <p>Iris is fundamentally an OpenGL mod: it queries {@code GL.getCapabilities()}
 * throughout its initialization (capability detection, debug state, sampler
 * limits, parallel shader compile, ...). On MetalUniversal's Metal backend
 * there is no OpenGL context, so those reads throw and crash the game during
 * {@code RenderSystem.initRenderer}.
 *
 * <p>This mixin injects at the HEAD of {@code RenderSystem.initRenderer}. If
 * the active backend is NOT a {@link GlDevice} (i.e. Metal or Vulkan), it
 * installs a stub {@code GLCapabilities} whose boolean version/extension flags
 * are all {@code false} and whose function pointers are all {@code NULL}. This
 * makes Iris take every "unsupported" code path (DSA-unsupported, no compute,
 * no SSBO, no parallel compile, unsupported debug state, ...) instead of
 * throwing. Actual GL function invocations (not just capability reads) are
 * still diverted by the other Iris mixins (e.g. SamplerLimits).
 *
 * <p>On a real OpenGL backend this mixin is a no-op: the backend is a
 * {@link GlDevice}, so it returns early and lets Mojang create real
 * capabilities.
 *
 * <p>{@code remap = false} matches Iris's own {@code MixinRenderSystem} which
 * also targets {@code initRenderer} without remapping.
 */
@Mixin(RenderSystem.class)
public class MixinRenderSystem_StubCaps {
    @Inject(method = "initRenderer", at = @At("HEAD"), remap = false)
    private static void metallum$installStubGlCapabilities(GpuDevice device, CallbackInfo ci) {
        try {
            if (((MetallumGpuDeviceAccessor) device).metallum$getBackend() instanceof GlDevice) {
                // Real OpenGL backend: Mojang will create real GLCapabilities.
                return;
            }
        } catch (Throwable ignored) {
            // Accessor not applied or device shape unexpected — fall through to
            // install the stub, which is the safe choice on a non-GL backend.
        }

        // Metal / Vulkan backend: no OpenGL context will ever exist. Install a
        // stub so GL.getCapabilities() returns a non-null object with all
        // features reported as unsupported, instead of throwing.
        try {
            GL.setCapabilities(new GLCapabilities((Function<String, Long>) name -> 0L));
        } catch (Throwable t) {
            Metallum.LOGGER.warn(
                    "[MetalUniversal] Failed to install stub GLCapabilities for Iris: {}",
                    t.toString()
            );
        }
    }
}

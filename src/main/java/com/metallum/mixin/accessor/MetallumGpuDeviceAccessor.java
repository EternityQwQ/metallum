package com.metallum.mixin.accessor;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private {@code backend} field of Mojang's {@link GpuDevice} so
 * that MetalUniversal can determine which concrete backend
 * ({@code GlDevice} / {@code MetalDevice} / {@code VulkanDevice}) is active
 * without going through Iris's own accessor.
 *
 * <p>This is used by {@link com.metallum.client.metal.iris.MetalIrisBridge} to
 * decide whether Iris's OpenGL-dependent initialization paths must be diverted
 * (Metal/Vulkan backends have no OpenGL context).
 */
@Mixin(GpuDevice.class)
public interface MetallumGpuDeviceAccessor {
    @Accessor("backend")
    GpuDeviceBackend metallum$getBackend();
}

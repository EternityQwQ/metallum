package com.metallum.mixin.sodium;

import com.metallum.client.metal.render.MetalDrawBatch;
import com.mojang.blaze3d.systems.RenderSystem;
import net.caffeinemc.mods.sodium.client.gpu.device.batch.MultiDrawBatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiDrawBatch.class)
public class MultiDrawBatchMixin {
    @Inject(method = "newBatch", at = @At("HEAD"), cancellable = true, remap = false)
    private static void metallum$createMetalDrawBatch(int capacity, CallbackInfoReturnable<MultiDrawBatch> cir) {
        if (RenderSystem.getDevice().getDeviceInfo().backendName().equals("Metal")) {
            cir.setReturnValue(new MetalDrawBatch(capacity));
        }
    }
}

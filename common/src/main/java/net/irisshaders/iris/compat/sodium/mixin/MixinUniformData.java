package net.irisshaders.iris.compat.sodium.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import net.irisshaders.iris.mixinterface.ShadowRenderListAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(UniformBufferManager.class)
public class MixinUniformData implements ShadowRenderListAccess {
	@Mutable
	@Shadow
	@Final
	private MappableRingBuffer uniformData;
	@Shadow
	private boolean hasUpdatedThisFrame;
	@Unique
	private MappableRingBuffer shadowUbo;

	@Unique
	private boolean shadowUboFrame;

	@Unique
	private boolean isSwappedToShadow = false;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void iris$init(ClientLevel level, int renderDistance, CallbackInfo ci) {
		this.shadowUbo = new MappableRingBuffer(() -> "Sodium uniform buffer (shadow map)", 130, 256);
	}

	@Override
	public void iris$beginShadowRenderListScope() {
		if (isSwappedToShadow) return;
		isSwappedToShadow = true;

		var x = this.uniformData;
		this.uniformData = shadowUbo;
		this.shadowUbo = x;

		var y = this.hasUpdatedThisFrame;
		this.hasUpdatedThisFrame = this.shadowUboFrame;
		this.shadowUboFrame = y;
	}

	@Override
	public void iris$endShadowRenderListScope() {
		if (!isSwappedToShadow) return;
		isSwappedToShadow = false;

		var x = this.uniformData;
		this.uniformData = this.shadowUbo;
		this.shadowUbo = x;

		var y = this.hasUpdatedThisFrame;
		this.hasUpdatedThisFrame = this.shadowUboFrame;
		this.shadowUboFrame = y;
	}
}

package net.irisshaders.iris.mixin.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.render.submit.ExtendedBlockModelSubmit;
import net.irisshaders.iris.mixinterface.ModelStorage;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.ImmediateState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Function;

@Mixin(ExtendedBlockModelSubmit.class)
public class MixinExtendedBlockModelSubmit implements ModelStorage {
	@Unique
	private int entityId, beId, itemId;

	@Unique
	private boolean isRenderingBEs;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void iris$c(PoseStack.Pose pose, Function renderTypeFunction, List modelParts, Mesh mesh, int[] tintLayers, int lightCoords, int overlayCoords, int tintColor, PoseStack.Pose sheetedDecalPose, CallbackInfo ci) {
		iris$capture();
	}

	@Override
	public void iris$capture() {
		entityId = CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();
		beId = CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
		itemId = CapturedRenderingState.INSTANCE.getCurrentRenderedItem();
		isRenderingBEs = ImmediateState.isRenderingBEs;
	}

	@Override
	public void iris$set() {
		CapturedRenderingState.INSTANCE.setCurrentEntity(entityId);
		CapturedRenderingState.INSTANCE.setCurrentBlockEntity(beId);
		CapturedRenderingState.INSTANCE.setCurrentRenderedItem(itemId);
	}

	@Override
	public boolean iris$wasBE() {
		return isRenderingBEs;
	}
}

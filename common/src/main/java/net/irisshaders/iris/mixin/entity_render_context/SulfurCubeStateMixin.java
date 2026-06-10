package net.irisshaders.iris.mixin.entity_render_context;

import net.irisshaders.iris.mixinterface.SulfurCubeStateExtension;
import net.minecraft.client.renderer.entity.state.SulfurCubeRenderState;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SulfurCubeRenderState.class)
public class SulfurCubeStateMixin implements SulfurCubeStateExtension {
	@Unique
	private BlockState block;

	@Override
	public void setBlock(BlockState block) {
		this.block = block;
	}

	@Override
	public BlockState getBlock() {
		return block;
	}
}

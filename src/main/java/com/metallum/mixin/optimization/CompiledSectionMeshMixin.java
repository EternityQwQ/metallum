package com.metallum.mixin.optimization;

import com.metallum.client.metal.optimization.MetalTerrainFaceCulling;
import com.metallum.mixin.optimization.accessor.SectionCompilerResultsAccessor;
import com.mojang.blaze3d.vertex.MeshData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Environment(EnvType.CLIENT)
@Mixin(CompiledSectionMesh.class)
public abstract class CompiledSectionMeshMixin implements MetalTerrainFaceCulling.SectionMeshSegments {
    @Unique
    private final MetalTerrainFaceCulling.SectionSegments metallum$segments = new MetalTerrainFaceCulling.SectionSegments();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void metallum$copyTerrainFaceSegments(
            final TranslucencyPointOfView translucencyPointOfView,
            final SectionCompiler.Results results,
            final CallbackInfo ci
    ) {
        Map<ChunkSectionLayer, MeshData> renderedLayers = ((SectionCompilerResultsAccessor) (Object) results).metallum$getRenderedLayers();
        MeshData solidMesh = renderedLayers.get(ChunkSectionLayer.SOLID);
        if (solidMesh instanceof MetalTerrainFaceCulling.MeshDataSegments segmentsHolder) {
            this.metallum$segments.solid = segmentsHolder.metallum$getTerrainFaceSegments();
        }
        MeshData cutoutMesh = renderedLayers.get(ChunkSectionLayer.CUTOUT);
        if (cutoutMesh instanceof MetalTerrainFaceCulling.MeshDataSegments segmentsHolder) {
            this.metallum$segments.cutout = segmentsHolder.metallum$getTerrainFaceSegments();
        }
    }

    @Override
    public MetalTerrainFaceCulling.SectionSegments metallum$getSegments() {
        return this.metallum$segments;
    }
}

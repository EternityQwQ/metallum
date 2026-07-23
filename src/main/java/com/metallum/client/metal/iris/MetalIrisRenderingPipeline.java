package com.metallum.client.metal.iris;

import com.mojang.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.irisshaders.iris.compat.dh.DHCompat;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.shaderpack.properties.CloudSetting;
import net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalInt;

/**
 * A Metal-native implementation of Iris's {@link WorldRenderingPipeline}
 * interface, used when a shaderpack is loaded on a non-OpenGL backend.
 *
 * <p>Instead of constructing Iris's {@code IrisRenderingPipeline} (which
 * creates hundreds of OpenGL resources — framebuffers, textures, shader
 * programs — via native GL calls that crash on Metal), this pipeline:
 * <ul>
 *   <li>Stores the {@link ProgramSet} for future shader compilation to MSL
 *       via {@link MetalIrisBridge}.</li>
 *   <li>Implements all {@code WorldRenderingPipeline} methods with the same
 *       safe defaults as {@code VanillaRenderingPipeline}.</li>
 *   <li>Makes {@link #beginLevelRendering()} a true no-op (no GL calls),
 *       unlike {@code VanillaRenderingPipeline} which calls
 *       {@code GL.getCapabilities()} and {@code glUseProgram(0)}.</li>
 * </ul>
 *
 * <p>This allows the game to run with a shaderpack "loaded" (the pack is
 * parsed, programs are available) without crashing, and provides a foundation
 * for future phases where shader programs will be compiled to Metal MSL and
 * rendered using Metal render passes.
 *
 * <p>The pipeline is selected by {@code MixinIris}'s {@code createPipeline}
 * redirect, which returns {@code new MetalIrisRenderingPipeline(programs)}
 * instead of {@code new IrisRenderingPipeline(programs)} when
 * {@link MetalIrisBridge#isNonGlBackend()} returns {@code true} and a
 * shaderpack is loaded.
 */
public class MetalIrisRenderingPipeline implements WorldRenderingPipeline {
    private static final Logger LOGGER = LoggerFactory.getLogger("MetalUniversal");

    private final ProgramSet programSet;
    private final FrameUpdateNotifier frameUpdateNotifier = new FrameUpdateNotifier();

    public MetalIrisRenderingPipeline(ProgramSet programSet) {
        this.programSet = programSet;

        WorldRenderingSettings.INSTANCE.setDisableDirectionalShading(false);
        WorldRenderingSettings.INSTANCE.setUseSeparateAo(false);
        WorldRenderingSettings.INSTANCE.setSeparateEntityDraws(false);
        WorldRenderingSettings.INSTANCE.setAmbientOcclusionLevel(1.0f);
        WorldRenderingSettings.INSTANCE.setVertexFormat(ChunkMeshFormats.COMPACT);
        WorldRenderingSettings.INSTANCE.setVoxelizeLightBlocks(false);
        WorldRenderingSettings.INSTANCE.setBreaksAnisotropy(false);
        WorldRenderingSettings.INSTANCE.setBlockTypeIds(Object2ObjectMaps.emptyMap());

        LOGGER.info("[MetalUniversal] MetalIrisRenderingPipeline created. Shaderpack loaded — programs available for Metal MSL compilation.");
    }

    @Override
    public void beginLevelRendering() {
        // No-op: safe on Metal (no GL calls)
    }

    @Override
    public void renderShadows(LevelRendererAccessor worldRenderer, Camera camera, CameraRenderState renderState) {
        // stub
    }

    @Override
    public void addDebugText(DebugScreenDisplayer messages) {
        // stub
    }

    @Override
    public OptionalInt getForcedShadowRenderDistanceChunksForDisplay() {
        return OptionalInt.empty();
    }

    @Override
    public Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> getTextureMap() {
        return Object2ObjectMaps.emptyMap();
    }

    @Override
    public WorldRenderingPhase getPhase() {
        return WorldRenderingPhase.NONE;
    }

    @Override
    public void setPhase(WorldRenderingPhase phase) {
    }

    @Override
    public void setOverridePhase(WorldRenderingPhase phase) {
    }

    @Override
    public int getCurrentNormalTexture() {
        return 0;
    }

    @Override
    public int getCurrentSpecularTexture() {
        return 0;
    }

    @Override
    public void onSetAlbedoTex(GpuTextureView id) {
    }

    @Override
    public void beginHand() {
    }

    @Override
    public void beginTranslucents() {
    }

    @Override
    public void finalizeLevelRendering() {
    }

    @Override
    public void finalizeGameRendering() {
    }

    @Override
    public void destroy() {
        LOGGER.info("[MetalUniversal] MetalIrisRenderingPipeline destroyed.");
    }

    @Override
    public FrameUpdateNotifier getFrameUpdateNotifier() {
        return this.frameUpdateNotifier;
    }

    @Override
    public boolean shouldDisableVanillaEntityShadows() {
        return false;
    }

    @Override
    public boolean shouldDisableDirectionalShading() {
        return false;
    }

    @Override
    public boolean shouldDisableFrustumCulling() {
        return false;
    }

    @Override
    public boolean shouldDisableOcclusionCulling() {
        return false;
    }

    @Override
    public CloudSetting getCloudSetting() {
        return CloudSetting.DEFAULT;
    }

    @Override
    public boolean shouldRenderUnderwaterOverlay() {
        return true;
    }

    @Override
    public boolean shouldRenderVignette() {
        return true;
    }

    @Override
    public boolean shouldRenderSun() {
        return true;
    }

    @Override
    public boolean shouldRenderWeather() {
        return true;
    }

    @Override
    public boolean shouldRenderWeatherParticles() {
        return true;
    }

    @Override
    public boolean shouldRenderMoon() {
        return true;
    }

    @Override
    public boolean shouldRenderStars() {
        return true;
    }

    @Override
    public boolean shouldRenderSkyDisc() {
        return true;
    }

    @Override
    public boolean shouldWriteRainAndSnowToDepthBuffer() {
        return false;
    }

    @Override
    public ParticleRenderingSettings getParticleRenderingSettings() {
        return ParticleRenderingSettings.MIXED;
    }

    @Override
    public boolean allowConcurrentCompute() {
        return false;
    }

    @Override
    public boolean hasFeature(FeatureFlags flags) {
        return false;
    }

    @Override
    public float getSunPathRotation() {
        return 0;
    }

    @Override
    public DHCompat getDHCompat() {
        return null;
    }

    @Override
    public void setIsMainBound(boolean mainBound) {
    }

    @Override
    public void onBeginClear() {
    }

    @Override
    public boolean supportsEndFlash() {
        return false;
    }

    @Override
    public int getAlbedoTex() {
        return 0;
    }
}

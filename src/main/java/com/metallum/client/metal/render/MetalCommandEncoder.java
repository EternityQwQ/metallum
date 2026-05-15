package com.metallum.client.metal.render;

import com.metallum.client.metal.render.bridge.MetalNativeBridge;
import com.metallum.client.metal.render.mtl.MTLCommandBuffer;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.*;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Vector4fc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

@Environment(EnvType.CLIENT)
final class MetalCommandEncoder implements CommandEncoderBackend {
    public static final int MAX_SUBMITS_IN_FLIGHT = 3;
    private final MetalDevice device;
    long currentSubmitIndex = MAX_SUBMITS_IN_FLIGHT;
    private long completedSubmitIndex = 0L;
    private final MetalDestructionQueue destroyQueue = new MetalDestructionQueue(MAX_SUBMITS_IN_FLIGHT + 1);
    private final Map<MetalGpuTexture, PendingTextureClear> pendingTextureClears = new IdentityHashMap<>();
    private final Map<Long, MTLCommandBuffer> inFlightCommandBuffers = new java.util.HashMap<>();
    @Nullable
    private MetalRenderPass currentRenderPass;
    @Nullable
    MTLCommandBuffer commandBuffer;
    private MemorySegment blitEncoder = MemorySegment.NULL;
    private MemorySegment renderEncoder = MemorySegment.NULL;
    private MemorySegment renderColorAttachment = MemorySegment.NULL;
    private MemorySegment renderDepthAttachment = MemorySegment.NULL;

    record ClearColor(float red, float green, float blue, float alpha) {
        static final ClearColor TRANSPARENT = new ClearColor(0.0F, 0.0F, 0.0F, 0.0F);

        static ClearColor copy(final Vector4fc color) {
            return new ClearColor(color.x(), color.y(), color.z(), color.w());
        }
    }

    MetalCommandEncoder(final MetalDevice device) {
        this.device = device;
    }

    MTLCommandBuffer commandBuffer() {
        if (commandBuffer != null) {
            return commandBuffer;
        }

        return commandBuffer = device.commandQueue.makeCommandBuffer(
                device.useLabels() ? "Metallum frame " + currentSubmitIndex : null
        );
    }

    MemorySegment blitCommandEncoder() {
        endRenderEncoder();
        return blitEncoder = commandBuffer().makeBlitCommandEncoder();
    }

    void endBlitEncoder() {
        if (MetalProbe.isNullHandle(blitEncoder)) {
            return;
        }
        MetalNativeBridge.INSTANCE.MTLCommandEncoder_endEncoding(blitEncoder);
        MetalNativeBridge.INSTANCE.metallum_release_object(blitEncoder);
        blitEncoder = MemorySegment.NULL;
    }

    @Override
    public void submit() {
        if (commandBuffer == null) {
            return;
        }

        submitRenderPass();
        endBlitEncoder();
        endRenderEncoder();

        commandBuffer.commit();
        inFlightCommandBuffers.put(currentSubmitIndex, commandBuffer);
        commandBuffer = null;
        currentSubmitIndex++;

        if (!awaitSubmitCompletion(currentSubmitIndex - MAX_SUBMITS_IN_FLIGHT, 5000L)) {
            throw new IllegalStateException("5s timeout reached when waiting for Metal submit completion");
        }

        destroyQueue.rotate();
    }

    MemorySegment renderCommandEncoder(
            final MetalGpuTextureView colorTextureView,
            @Nullable final MetalGpuTextureView depthTextureView,
            final int viewportWidth,
            final int viewportHeight,
            final Optional<ClearColor> clearColor,
            final OptionalDouble clearDepth
    ) {
        endBlitEncoder();
        MemorySegment colorAttachment = colorTextureView.nativeHandle();
        MemorySegment depthAttachment = depthTextureView == null ? MemorySegment.NULL : depthTextureView.nativeHandle();
        if (clearColor.isEmpty()
                && clearDepth.isEmpty()
                && !MetalProbe.isNullHandle(renderEncoder)
                && MetalPipelineSupport.sameHandle(renderColorAttachment, colorAttachment)
                && MetalPipelineSupport.sameHandle(renderDepthAttachment, depthAttachment)) {
            return renderEncoder;
        }

        endRenderEncoder();
        ClearColor colorClear = clearColor.orElse(ClearColor.TRANSPARENT);
        MemorySegment encoder = commandBuffer().makeRenderCommandEncoder(
                colorAttachment,
                depthAttachment,
                viewportWidth,
                viewportHeight,
                clearColor.isPresent() ? 1 : 0,
                colorClear.red(),
                colorClear.green(),
                colorClear.blue(),
                colorClear.alpha(),
                clearDepth.isPresent() ? 1 : 0,
                clearDepth.orElse(1.0)
        );
        if (MetalProbe.isNullHandle(encoder)) {
            return null;
        }
        renderEncoder = encoder;
        renderColorAttachment = colorAttachment;
        renderDepthAttachment = depthAttachment;
        return encoder;
    }

    void endRenderEncoder() {
        if (MetalProbe.isNullHandle(renderEncoder)) {
            return;
        }
        MetalNativeBridge.INSTANCE.MTLCommandEncoder_endEncoding(renderEncoder);
        MetalNativeBridge.INSTANCE.metallum_release_object(renderEncoder);
        renderEncoder = MemorySegment.NULL;
        renderColorAttachment = MemorySegment.NULL;
        renderDepthAttachment = MemorySegment.NULL;
    }

    @Override
    public @NonNull RenderPassBackend createRenderPass(final RenderPassDescriptor descriptor) {
        List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> colorAttachments = descriptor.colorAttachments();
        if (colorAttachments.isEmpty() || colorAttachments.getFirst() == null) {
            throw new UnsupportedOperationException("Metal render passes currently require one color attachment");
        }

        RenderPassDescriptor.Attachment<Optional<Vector4fc>> colorAttachment = colorAttachments.getFirst();
        GpuTextureView colorTexture = colorAttachment.textureView();
        Optional<ClearColor> resolvedColorClear = resolveColorAttachmentClear(colorTexture, toClearColor(colorAttachment.clearValue()));

        RenderPassDescriptor.Attachment<OptionalDouble> depthAttachment = descriptor.depthAttachment();
        GpuTextureView depthTexture = depthAttachment == null ? null : depthAttachment.textureView();
        OptionalDouble resolvedDepthClear = depthAttachment == null
                ? OptionalDouble.empty()
                : resolveDepthAttachmentClear(depthTexture, depthAttachment.clearValue());
        RenderPass.RenderArea renderArea = descriptor.renderArea != null
                ? descriptor.renderArea
                : new RenderPass.RenderArea(0, 0, colorTexture.getWidth(0), colorTexture.getHeight(0));
        MetalRenderPass renderPass = new MetalRenderPass(
                device,
                this,
                descriptor.label(),
                colorTexture,
                depthTexture,
                renderArea,
                resolvedColorClear,
                resolvedDepthClear
        );
        currentRenderPass = renderPass;
        renderPass.pushDebugGroup(descriptor.label());
        return renderPass;
    }

    @Override
    public void submitRenderPass() {
        if (currentRenderPass != null) {
            try {
                currentRenderPass.end();
            } finally {
                currentRenderPass.popDebugGroup();
            }
        }
        currentRenderPass = null;
    }

    @Override
    public void clearColorTexture(final @NonNull GpuTexture colorTexture, final @NonNull Vector4fc clearColor) {
        MetalGpuTexture texture = castTexture(colorTexture);
        deferColorClear(texture, ClearColor.copy(clearColor));
    }

    @Override
    public void clearColorAndDepthTextures(final @NonNull GpuTexture colorTexture, final @NonNull Vector4fc clearColor, final @NonNull GpuTexture depthTexture, final double clearDepth) {
        MetalGpuTexture color = castTexture(colorTexture);
        MetalGpuTexture depth = castTexture(depthTexture);
        deferColorClear(color, ClearColor.copy(clearColor));
        deferDepthClear(depth, clearDepth);
    }

    @Override
    public void clearColorAndDepthTextures(
            final @NonNull GpuTexture colorTexture,
            final @NonNull Vector4fc clearColor,
            final @NonNull GpuTexture depthTexture,
            final double clearDepth,
            final int regionX,
            final int regionY,
            final int regionWidth,
            final int regionHeight
    ) {
        MetalGpuTexture color = castTexture(colorTexture);
        MetalGpuTexture depth = castTexture(depthTexture);
        ClearColor resolvedClearColor = ClearColor.copy(clearColor);
        if (isFullTextureRegion(color, depth, regionX, regionY, regionWidth, regionHeight)) {
            deferColorClear(color, resolvedClearColor);
            deferDepthClear(depth, clearDepth);
            return;
        }
        submitRenderPass();
        endBlitEncoder();
        endRenderEncoder();
        commandBuffer().clearColorDepthTexturesRegion(
                color.nativeHandle(),
                resolvedClearColor.red(),
                resolvedClearColor.green(),
                resolvedClearColor.blue(),
                resolvedClearColor.alpha(),
                depth.nativeHandle(),
                clearDepth,
                regionX,
                regionY,
                regionWidth,
                regionHeight
        );
    }

    @Override
    public void clearDepthTexture(final @NonNull GpuTexture depthTexture, final double clearDepth) {
        MetalGpuTexture texture = castTexture(depthTexture);
        deferDepthClear(texture, clearDepth);
    }

    @Override
    public void writeToBuffer(final GpuBufferSlice destination, final ByteBuffer data) {
        MetalGpuBuffer buffer = castBuffer(destination.buffer());
        int length = data.remaining();

        try (MetalGpuBuffer stagingBuffer = createStagingBuffer(data)) {
            MemorySegment blit = blitCommandEncoder();
            MetalNativeBridge.INSTANCE.MTLBlitCommandEncoder_copyFromBufferToBuffer(
                    blit,
                    stagingBuffer.nativeHandle(),
                    0L,
                    buffer.nativeHandle(),
                    destination.offset(),
                    length
            );

            endBlitEncoder();
            queueForDestroy(stagingBuffer::close);
        }
    }

    @Override
    public void copyToBuffer(final GpuBufferSlice source, final GpuBufferSlice target) {
        MetalGpuBuffer sourceBuffer = castBuffer(source.buffer());
        MetalGpuBuffer targetBuffer = castBuffer(target.buffer());
        MemorySegment blit = blitCommandEncoder();
        MetalNativeBridge.INSTANCE.MTLBlitCommandEncoder_copyFromBufferToBuffer(
                blit,
                sourceBuffer.nativeHandle(),
                source.offset(),
                targetBuffer.nativeHandle(),
                target.offset(),
                source.length()
        );
        endBlitEncoder();
    }

    @Override
    public void writeToTexture(
            final @NonNull GpuTexture destination,
            final NativeImage source,
            final int mipLevel,
            final int depthOrLayer,
            final int destX,
            final int destY,
            final int width,
            final int height,
            final int sourceX,
            final int sourceY
    ) {
        int stagingBufferSize = source.getWidth() * source.getHeight() * destination.getFormat().pixelSize();
        int texelSize = destination.getFormat().pixelSize();
        int skipTexels = sourceX + sourceY * source.getWidth();
        long skipBytes = (long) skipTexels * texelSize;

        ByteBuffer sourceBytes = MemoryUtil.memByteBuffer(source.getPointer(), stagingBufferSize);
        writeToTexture((MetalGpuTexture) destination, sourceBytes, skipBytes, mipLevel, depthOrLayer, destX, destY, width, height, source.getWidth(), source.getHeight());
    }

    @Override
    public void writeToTexture(
            final @NonNull GpuTexture destination,
            final @NonNull ByteBuffer source,
            final NativeImage.@NonNull Format format,
            final int mipLevel,
            final int depthOrLayer,
            final int destX,
            final int destY,
            final int width,
            final int height
    ) {
        writeToTexture((MetalGpuTexture) destination, source, 0L, mipLevel, depthOrLayer, destX, destY, width, height, width, height);
    }

    private void writeToTexture(
            final MetalGpuTexture destination,
            final ByteBuffer source,
            final long sourceOffset,
            final int mipLevel,
            final int depthOrLayer,
            final int destX,
            final int destY,
            final int width,
            final int height,
            final int sourceWidth,
            final int sourceHeight
    ) {
        flushPendingClear(destination);

        int pixelSize = destination.pixelSize();
        long bytesPerRow = (long) sourceWidth * pixelSize;
        long bytesPerImage = bytesPerRow * sourceHeight;

        try (MetalGpuBuffer stagingBuffer = createStagingBuffer(source)) {
            MemorySegment blit = blitCommandEncoder();
            MetalNativeBridge.INSTANCE.MTLBlitCommandEncoder_copyFromBufferToTexture(
                    blit,
                    stagingBuffer.nativeHandle(),
                    sourceOffset,
                    destination.nativeHandle(),
                    mipLevel,
                    depthOrLayer,
                    destX,
                    destY,
                    width,
                    height,
                    bytesPerRow,
                    bytesPerImage
            );

            endBlitEncoder();
        }
    }

    @Override
    public void copyTextureToBuffer(final @NonNull GpuTexture source, final @NonNull GpuBuffer destination, final long offset, final @NonNull Runnable callback, final int mipLevel) {
        copyTextureToBuffer(source, destination, offset, callback, mipLevel, 0, 0, source.getWidth(mipLevel), source.getHeight(mipLevel));
    }

    @Override
    public void copyTextureToBuffer(
            final @NonNull GpuTexture source,
            final @NonNull GpuBuffer destination,
            final long offset,
            final @NonNull Runnable callback,
            final int mipLevel,
            final int x,
            final int y,
            final int width,
            final int height
    ) {
        MetalGpuTexture texture = castTexture(source);
        flushPendingClear(texture);
        MetalGpuBuffer buffer = castBuffer(destination);
        int bytesPerPixel = texture.pixelSize();
        int rowBytes = width * bytesPerPixel;
        int bytesPerImage = rowBytes * height;

        MemorySegment blit = blitCommandEncoder();
        MetalNativeBridge.INSTANCE.MTLBlitCommandEncoder_copyFromTextureToBuffer(
                blit,
                texture.nativeHandle(),
                buffer.nativeHandle(),
                offset,
                mipLevel,
                0,
                x,
                y,
                width,
                height,
                rowBytes,
                bytesPerImage
        );

        endBlitEncoder();
        queueForDestroy(callback);
    }

    @Override
    public void copyTextureToTexture(
            final @NonNull GpuTexture source,
            final @NonNull GpuTexture destination,
            final int mipLevel,
            final int destX,
            final int destY,
            final int sourceX,
            final int sourceY,
            final int width,
            final int height
    ) {
        MetalGpuTexture srcTexture = castTexture(source);
        MetalGpuTexture dstTexture = castTexture(destination);
        flushPendingClear(srcTexture);
        flushPendingClear(dstTexture);
        MemorySegment blit = blitCommandEncoder();
        MetalNativeBridge.INSTANCE.MTLBlitCommandEncoder_copyFromTextureToTexture(
                blit,
                srcTexture.nativeHandle(),
                dstTexture.nativeHandle(),
                mipLevel,
                sourceX,
                sourceY,
                destX,
                destY,
                width,
                height
        );
        endBlitEncoder();
    }

    @Override
    public @NonNull GpuFence createFence() {
        return new MetalFence(this, currentSubmitIndex);
    }

    void queueForDestroy(final Runnable destroyAction) {
        destroyQueue.add(destroyAction);
    }

    boolean awaitSubmitCompletion(final long submitIndex, final long timeoutMs) {
        if (completedSubmitIndex >= submitIndex) {
            return true;
        }
        if (submitIndex == currentSubmitIndex) {
            throw new IllegalStateException("Cannot wait on a fence for the current submit");
        }

        MTLCommandBuffer commandBuffer = inFlightCommandBuffers.get(submitIndex);
        if (commandBuffer == null) {
            releaseCompletedCommandBuffers(submitIndex);
            return true;
        }

        if (commandBuffer.isCompleted() || commandBuffer.waitUntilCompleted(timeoutMs)) {
            releaseCompletedCommandBuffers(submitIndex);
            return true;
        }
        return false;
    }

    void close() {
        submitRenderPass();
        endBlitEncoder();
        endRenderEncoder();
        for (MTLCommandBuffer commandBuffer : inFlightCommandBuffers.values()) {
            commandBuffer.close();
        }
        inFlightCommandBuffers.clear();
        if (commandBuffer != null) {
            commandBuffer.close();
            commandBuffer = null;
        }
        destroyQueue.close();
    }

    void waitForSubmittedGpuWork() {
        if (commandBuffer != null || currentRenderPass != null || !MetalProbe.isNullHandle(blitEncoder)) {
            submit();
        } else {
            endRenderEncoder();
        }
        long latestSubmit = currentSubmitIndex - 1L;
        if (latestSubmit > completedSubmitIndex) {
            awaitSubmitCompletion(latestSubmit, Long.MAX_VALUE);
        }
    }

    private void releaseCompletedCommandBuffers(final long completedSubmitIndex) {
        this.completedSubmitIndex = Math.max(this.completedSubmitIndex, completedSubmitIndex);
        inFlightCommandBuffers.entrySet().removeIf(entry -> {
            if (entry.getKey() > this.completedSubmitIndex) {
                return false;
            }
            entry.getValue().close();
            return true;
        });
    }

    @Override
    public void writeTimestamp(final @NonNull GpuQueryPool pool, final int index) {
        if (pool instanceof MetalGpuQueryPool metalPool && index >= 0 && index < pool.size()) {
            metalPool.setValue(index, device.getTimestampNow());
        }
    }

    static MetalGpuBuffer castBuffer(final GpuBuffer buffer) {
        return (MetalGpuBuffer) buffer;
    }

    static MetalGpuTexture castTexture(final GpuTexture texture) {
        return (MetalGpuTexture) texture;
    }

    void flushPendingTextureViewClear(final GpuTextureView textureView) {
        flushPendingClear(castTexture(textureView.texture()));
    }

    boolean deferRenderPassClear(
            final GpuTextureView colorTexture,
            final Optional<ClearColor> clearColor,
            @Nullable final GpuTextureView depthTexture,
            final OptionalDouble clearDepth
    ) {
        if (clearColor.isPresent() && !isFullTextureView(colorTexture)) {
            return false;
        }
        if (clearDepth.isPresent() && (depthTexture == null || !isFullTextureView(depthTexture))) {
            return false;
        }
        clearColor.ifPresent(color -> deferColorClear(castTexture(colorTexture.texture()), color));
        if (clearDepth.isPresent()) {
            deferDepthClear(castTexture(depthTexture.texture()), clearDepth.getAsDouble());
        }
        return true;
    }

    private void deferColorClear(final MetalGpuTexture texture, final ClearColor clearColor) {
        PendingTextureClear state = pendingTextureClears.computeIfAbsent(texture, ignored -> new PendingTextureClear());
        state.color = Optional.of(clearColor);
    }

    private void deferDepthClear(final MetalGpuTexture texture, final double clearDepth) {
        PendingTextureClear state = pendingTextureClears.computeIfAbsent(texture, ignored -> new PendingTextureClear());
        state.depth = OptionalDouble.of(clearDepth);
    }

    private void clearTexture(
            final MemorySegment texture,
            final int clearColorEnabled,
            final float clearColorRed,
            final float clearColorGreen,
            final float clearColorBlue,
            final float clearColorAlpha,
            final int clearDepthEnabled,
            final double clearDepth
    ) {
        endBlitEncoder();
        endRenderEncoder();
        MemorySegment encoder = commandBuffer().makeRenderCommandEncoder(
                clearColorEnabled != 0 ? texture : null,
                clearDepthEnabled != 0 ? texture : null,
                1.0,
                1.0,
                clearColorEnabled,
                clearColorRed,
                clearColorGreen,
                clearColorBlue,
                clearColorAlpha,
                clearDepthEnabled,
                clearDepth
        );
        if (MetalProbe.isNullHandle(encoder)) {
            return;
        }
        MetalNativeBridge.INSTANCE.MTLCommandEncoder_endEncoding(encoder);
        MetalNativeBridge.INSTANCE.metallum_release_object(encoder);
    }

    private void flushPendingClear(final MetalGpuTexture texture) {
        PendingTextureClear pending = pendingTextureClears.remove(texture);
        if (pending == null) {
            return;
        }
        if (pending.color.isPresent()) {
            clearTexture(
                    texture.nativeHandle(),
                    1,
                    pending.color.get().red(),
                    pending.color.get().green(),
                    pending.color.get().blue(),
                    pending.color.get().alpha(),
                    0,
                    1.0
            );
        }
        if (pending.depth.isPresent()) {
            clearTexture(
                    texture.nativeHandle(),
                    0,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    1,
                    pending.depth.getAsDouble()
            );
        }
    }

    private Optional<ClearColor> resolveColorAttachmentClear(
            final GpuTextureView textureView,
            final Optional<ClearColor> explicitClear
    ) {
        MetalGpuTexture texture = castTexture(textureView.texture());
        PendingTextureClear pending = pendingTextureClears.get(texture);
        if (pending == null || pending.color.isEmpty()) {
            return explicitClear;
        }
        if (!isFullTextureView(textureView)) {
            flushPendingClear(texture);
            return explicitClear;
        }
        Optional<ClearColor> clear = explicitClear.isPresent() ? explicitClear : pending.color;
        pending.color = Optional.empty();
        removePendingIfEmpty(texture, pending);
        return clear;
    }

    private OptionalDouble resolveDepthAttachmentClear(
            final GpuTextureView textureView,
            final OptionalDouble explicitClear
    ) {
        MetalGpuTexture texture = castTexture(textureView.texture());
        PendingTextureClear pending = pendingTextureClears.get(texture);
        if (pending == null || pending.depth.isEmpty()) {
            return explicitClear;
        }
        if (!isFullTextureView(textureView)) {
            flushPendingClear(texture);
            return explicitClear;
        }
        OptionalDouble clear = explicitClear.isPresent() ? explicitClear : pending.depth;
        pending.depth = OptionalDouble.empty();
        removePendingIfEmpty(texture, pending);
        return clear;
    }

    private void removePendingIfEmpty(final MetalGpuTexture texture, final PendingTextureClear pending) {
        if (pending.color.isEmpty() && pending.depth.isEmpty()) {
            pendingTextureClears.remove(texture);
        }
    }

    private static boolean isFullTextureView(final GpuTextureView textureView) {
        return textureView.baseMipLevel() == 0
                && textureView.mipLevels() >= textureView.texture().getMipLevels()
                && textureView.texture().getDepthOrLayers() == 1;
    }

    private static boolean isFullTextureRegion(
            final MetalGpuTexture color,
            final MetalGpuTexture depth,
            final int x,
            final int y,
            final int width,
            final int height
    ) {
        return x == 0
                && y == 0
                && width == color.getWidth(0)
                && height == color.getHeight(0)
                && width == depth.getWidth(0)
                && height == depth.getHeight(0);
    }

    private static Optional<ClearColor> toClearColor(final Optional<Vector4fc> clearColor) {
        return clearColor.map(ClearColor::copy);
    }

    private static final class PendingTextureClear {
        Optional<ClearColor> color = Optional.empty();
        OptionalDouble depth = OptionalDouble.empty();
    }

    private MetalGpuBuffer createStagingBuffer(final ByteBuffer source) {
        int length = source.remaining();
        MetalGpuBuffer stagingBuffer = new MetalGpuBuffer(
                device,
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_SRC,
                length
        );
        ByteBuffer staging = stagingBuffer.fullStorageView().order(ByteOrder.nativeOrder());
        staging.limit(length);
        staging.put(source);
        return stagingBuffer;
    }
}

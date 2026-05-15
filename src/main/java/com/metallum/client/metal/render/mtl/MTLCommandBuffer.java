package com.metallum.client.metal.render.mtl;

import com.metallum.client.metal.render.MetalProbe;
import com.metallum.client.metal.render.bridge.MetalNativeBridge;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.foreign.MemorySegment;

@Environment(EnvType.CLIENT)
public final class MTLCommandBuffer {
    private MemorySegment handle;

    MTLCommandBuffer(final MemorySegment handle) {
        this.handle = handle;
    }

    public MemorySegment makeBlitCommandEncoder() {
        MemorySegment encoder = MetalNativeBridge.INSTANCE.MTLCommandBuffer_makeBlitCommandEncoder(this.requireHandle());
        if (MetalProbe.isNullHandle(encoder)) {
            throw new IllegalStateException("Failed to create MTLBlitCommandEncoder");
        }
        return encoder;
    }

    public MemorySegment makeRenderCommandEncoder(
            final MemorySegment colorTexture,
            final MemorySegment depthTexture,
            final double viewportWidth,
            final double viewportHeight,
            final int clearColorEnabled,
            final float clearColorRed,
            final float clearColorGreen,
            final float clearColorBlue,
            final float clearColorAlpha,
            final int clearDepthEnabled,
            final double clearDepth
    ) {
        return MetalNativeBridge.INSTANCE.MTLCommandBuffer_makeRenderCommandEncoder(
                this.requireHandle(),
                colorTexture,
                depthTexture,
                viewportWidth,
                viewportHeight,
                clearColorEnabled,
                clearColorRed,
                clearColorGreen,
                clearColorBlue,
                clearColorAlpha,
                clearDepthEnabled,
                clearDepth
        );
    }

    public void clearColorDepthTexturesRegion(
            final MemorySegment colorTexture,
            final float clearColorRed,
            final float clearColorGreen,
            final float clearColorBlue,
            final float clearColorAlpha,
            final MemorySegment depthTexture,
            final double clearDepth,
            final int regionX,
            final int regionY,
            final int regionWidth,
            final int regionHeight
    ) {
        MetalNativeBridge.INSTANCE.MTLCommandBuffer_clearColorDepthTexturesRegion(
                this.requireHandle(),
                colorTexture,
                clearColorRed,
                clearColorGreen,
                clearColorBlue,
                clearColorAlpha,
                depthTexture,
                clearDepth,
                regionX,
                regionY,
                regionWidth,
                regionHeight
        );
    }

    public void encodePresentTextureToDrawable(final MemorySegment drawable, final MemorySegment sourceTexture) {
        MetalNativeBridge.INSTANCE.MTLCommandBuffer_encodePresentTextureToDrawable(this.requireHandle(), drawable, sourceTexture);
    }

    public void presentDrawable(final MemorySegment drawable) {
        MetalNativeBridge.INSTANCE.MTLCommandBuffer_presentDrawable(this.requireHandle(), drawable);
    }

    public void commit() {
        MetalNativeBridge.INSTANCE.MTLCommandBuffer_commit(this.requireHandle());
    }

    public boolean isCompleted() {
        return MetalNativeBridge.INSTANCE.MTLCommandBuffer_isCompleted(this.requireHandle()) == 1;
    }

    public boolean waitUntilCompleted(final long timeoutMs) {
        return MetalNativeBridge.INSTANCE.MTLCommandBuffer_waitUntilCompleted(this.requireHandle(), Math.max(timeoutMs, 0L)) == 0;
    }

    public void pushDebugGroup(final String label) {
        MetalNativeBridge.INSTANCE.MTLCommandBuffer_pushDebugGroup(this.requireHandle(), label);
    }

    public void popDebugGroup() {
        MetalNativeBridge.INSTANCE.MTLCommandBuffer_popDebugGroup(this.requireHandle());
    }

    public void close() {
        if (MetalProbe.isNullHandle(this.handle)) {
            return;
        }
        MetalNativeBridge.INSTANCE.metallum_release_object(this.handle);
        this.handle = MemorySegment.NULL;
    }

    private MemorySegment requireHandle() {
        if (MetalProbe.isNullHandle(this.handle)) {
            throw new IllegalStateException("MTLCommandBuffer is closed");
        }
        return this.handle;
    }
}

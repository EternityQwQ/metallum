package com.metallum.client.metal.render;

import com.metallum.client.metal.render.bridge.MetalNativeBridge;

import java.lang.foreign.MemorySegment;

public final class MetalVertexDescriptor implements AutoCloseable {
    private final MemorySegment handle;
    private boolean closed;

    public MetalVertexDescriptor() {
        this.handle = MetalNativeBridge.INSTANCE.metallum_MTLVertexDescriptor_create();
    }

    public MemorySegment handle() {
        return this.handle;
    }

    public void setAttribute(long index, long format, long offset, long bufferIndex) {
        MetalNativeBridge.INSTANCE.metallum_MTLVertexDescriptor_setAttribute(this.handle, index, format, offset, bufferIndex);
    }

    public void setLayout(long bufferIndex, long stride, long stepFunction, long stepRate) {
        MetalNativeBridge.INSTANCE.metallum_MTLVertexDescriptor_setLayout(this.handle, bufferIndex, stride, stepFunction, stepRate);
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            MetalNativeBridge.INSTANCE.metallum_release_object(this.handle);
        }
    }
}

package com.metallum.client.metal.render;

import net.caffeinemc.mods.sodium.api.memory.MemoryIntrinsics;
import net.caffeinemc.mods.sodium.client.gpu.device.batch.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.gpu.device.context.DrawContext;
import net.caffeinemc.mods.sodium.client.util.UInt32;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

public final class MetalDrawBatch extends MultiDrawBatch {
    private final long pElementPointer;
    private final long pElementCount;
    private final long pBaseVertex;

    public MetalDrawBatch(int capacity) {
        this.pElementPointer = MemoryUtil.nmemAlignedAlloc(32L, (long) capacity * Pointer.POINTER_SIZE);
        MemoryUtil.memSet(this.pElementPointer, 0, (long) capacity * Pointer.POINTER_SIZE);
        this.pElementCount = MemoryUtil.nmemAlignedAlloc(32L, (long) capacity * 4L);
        this.pBaseVertex = MemoryUtil.nmemAlignedAlloc(32L, (long) capacity * 4L);
    }

    @Override
    public int getIndexBufferSize() {
        int elements = 0;
        for (int index = 0; index < this.size; ++index) {
            elements = Math.max(elements, MemoryIntrinsics.getInt(this.pElementCount + (long) index * 4L));
        }
        return elements;
    }

    @Override
    public void put(int size, int elementCount, int baseVertex, long elementOffset) {
        MemoryIntrinsics.putInt(this.pElementCount + (long) (size << 2), UInt32.uncheckedDowncast(elementCount));
        MemoryIntrinsics.putInt(this.pBaseVertex + (long) (size << 2), UInt32.uncheckedDowncast(baseVertex));
        MemoryIntrinsics.putAddress(this.pElementPointer + (long) (size << Pointer.POINTER_SHIFT), elementOffset << 2);
    }

    @Override
    public void draw(DrawContext context) {
        context.getPass().multiDrawIndexed(
            MemoryUtil.memPointerBuffer(this.pElementPointer, this.size),
            MemoryUtil.memIntBuffer(this.pElementCount, this.size),
            MemoryUtil.memIntBuffer(this.pBaseVertex, this.size),
            this.size
        );
    }

    @Override
    public void delete() {
        MemoryUtil.nmemAlignedFree(this.pElementPointer);
        MemoryUtil.nmemAlignedFree(this.pElementCount);
        MemoryUtil.nmemAlignedFree(this.pBaseVertex);
    }
}

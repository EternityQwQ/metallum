package com.metallum.client.metal.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;

@Environment(EnvType.CLIENT)
final class EncoderBindingCache {
    static final int SKIP = 0;
    static final int OFFSET_ONLY = 1;
    static final int FULL = 2;

    private static final int SIZE = 64;

    private final long[] bufferHandle = new long[SIZE];
    private final long[] bufferOffset = new long[SIZE];
    private final int[] bufferMask = new int[SIZE];
    private final long[] textureHandle = new long[SIZE];
    private final long[] samplerHandle = new long[SIZE];
    private final int[] textureMask = new int[SIZE];

    EncoderBindingCache() {
        reset();
    }

    void reset() {
        Arrays.fill(bufferHandle, 0L);
        Arrays.fill(bufferOffset, -1L);
        Arrays.fill(bufferMask, 0);
        Arrays.fill(textureHandle, 0L);
        Arrays.fill(samplerHandle, 0L);
        Arrays.fill(textureMask, 0);
    }

    int buffer(final int index, final MemorySegment handle, final long offset, final int mask) {
        long addr = handle.address();
        if (bufferHandle[index] == addr && bufferMask[index] == mask) {
            if (bufferOffset[index] == offset) {
                return SKIP;
            }
            bufferOffset[index] = offset;
            return OFFSET_ONLY;
        }
        bufferHandle[index] = addr;
        bufferOffset[index] = offset;
        bufferMask[index] = mask;
        return FULL;
    }

    void invalidateTexture(final int index) {
        textureHandle[index] = -1L;
    }

    boolean texture(final int index, final MemorySegment texture, final MemorySegment sampler, final int mask) {
        long tex = texture.address();
        long smp = sampler.address();
        if (textureHandle[index] == tex && samplerHandle[index] == smp && textureMask[index] == mask) {
            return true;
        }
        textureHandle[index] = tex;
        samplerHandle[index] = smp;
        textureMask[index] = mask;
        return false;
    }
}

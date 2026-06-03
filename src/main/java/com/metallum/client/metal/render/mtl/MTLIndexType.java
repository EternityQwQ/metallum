package com.metallum.client.metal.render.mtl;

import com.mojang.blaze3d.IndexType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum MTLIndexType {
    UInt16(0L, 2),
    UInt32(1L, 4);

    public final long value;
    public final int bytes;

    MTLIndexType(final long value, final int bytes) {
        this.value = value;
        this.bytes = bytes;
    }

    public static MTLIndexType from(final IndexType indexType) {
        return indexType == IndexType.INT ? UInt32 : UInt16;
    }
}

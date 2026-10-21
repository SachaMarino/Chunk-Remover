package com.sachamarino.chunkremover.common;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.BitSet;
import java.util.Collection;

/**
 * An inclusive, axis-aligned rectangle of chunk coordinates.
 */
public record ChunkArea(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
    public static final StreamCodec<ByteBuf, ChunkArea> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChunkArea::minChunkX,
            ByteBufCodecs.VAR_INT, ChunkArea::minChunkZ,
            ByteBufCodecs.VAR_INT, ChunkArea::maxChunkX,
            ByteBufCodecs.VAR_INT, ChunkArea::maxChunkZ,
            ChunkArea::new
    );

    /**
     * Creates an area from any two opposite chunk corners, in any order.
     */
    public static ChunkArea ofChunks(int x1, int z1, int x2, int z2) {
        return new ChunkArea(Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2));
    }

    /**
     * Creates an area from any two opposite block corners, in any order.
     */
    public static ChunkArea ofBlocks(int x1, int z1, int x2, int z2) {
        return ofChunks(x1 >> 4, z1 >> 4, x2 >> 4, z2 >> 4);
    }

    /**
     * Smallest area containing every given area.
     */
    public static ChunkArea bounding(Collection<ChunkArea> areas) {
        if (areas.isEmpty()) {
            throw new IllegalArgumentException("no areas");
        }
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (ChunkArea area : areas) {
            minX = Math.min(minX, area.minChunkX);
            minZ = Math.min(minZ, area.minChunkZ);
            maxX = Math.max(maxX, area.maxChunkX);
            maxZ = Math.max(maxZ, area.maxChunkZ);
        }
        return new ChunkArea(minX, minZ, maxX, maxZ);
    }

    /**
     * Number of distinct chunks covered by the union of the given areas (overlaps counted once).
     * Returns {@link Long#MAX_VALUE} when the bounding box is too large to compute exactly.
     */
    public static long unionCount(Collection<ChunkArea> areas) {
        if (areas.isEmpty()) {
            return 0L;
        }
        ChunkArea bounds = bounding(areas);
        if (bounds.chunkCount() > 64L * 1024L * 1024L) {
            return Long.MAX_VALUE;
        }
        BitSet bits = new BitSet((int) bounds.chunkCount());
        for (ChunkArea area : areas) {
            for (int z = area.minChunkZ; z <= area.maxChunkZ; z++) {
                int row = bounds.index(area.minChunkX, z);
                bits.set(row, row + area.width());
            }
        }
        return bits.cardinality();
    }

    public int width() {
        return maxChunkX - minChunkX + 1;
    }

    public int height() {
        return maxChunkZ - minChunkZ + 1;
    }

    public long chunkCount() {
        return (long) width() * (long) height();
    }

    public boolean contains(int chunkX, int chunkZ) {
        return chunkX >= minChunkX && chunkX <= maxChunkX && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
    }

    /**
     * Row-major index of a chunk inside this area, used for the "deleted chunks" bit set.
     */
    public int index(int chunkX, int chunkZ) {
        return (chunkZ - minChunkZ) * width() + (chunkX - minChunkX);
    }

    public int minBlockX() {
        return minChunkX << 4;
    }

    public int minBlockZ() {
        return minChunkZ << 4;
    }

    public int maxBlockX() {
        return (maxChunkX << 4) + 15;
    }

    public int maxBlockZ() {
        return (maxChunkZ << 4) + 15;
    }

    public int minRegionX() {
        return minChunkX >> 5;
    }

    public int minRegionZ() {
        return minChunkZ >> 5;
    }

    public int maxRegionX() {
        return maxChunkX >> 5;
    }

    public int maxRegionZ() {
        return maxChunkZ >> 5;
    }
}

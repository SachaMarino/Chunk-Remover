package com.sachamarino.chunkremover.common;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Summary of one deletion run.
 *
 * @param deleted             chunks removed from the chunk storage ("region" folder)
 * @param skippedLoaded       chunks skipped because they were loaded (players, force-load, spawn chunks...)
 * @param skippedClaimed      chunks skipped because they are claimed in FTB Chunks
 * @param notGenerated        chunks in the selection that did not exist on disk
 * @param regionFilesDeleted  region files removed entirely (all three storages combined)
 * @param regionFilesRewritten region files rewritten without the deleted chunks (all three storages combined)
 * @param bytesFreed          disk space reclaimed, in bytes
 */
public record DeletionStats(int deleted, int skippedLoaded, int skippedClaimed, int notGenerated,
                            int regionFilesDeleted, int regionFilesRewritten, long bytesFreed) {
    public static final DeletionStats EMPTY = new DeletionStats(0, 0, 0, 0, 0, 0, 0L);

    public static final StreamCodec<ByteBuf, DeletionStats> STREAM_CODEC = StreamCodec.of(DeletionStats::encode, DeletionStats::decode);

    private static void encode(ByteBuf buf, DeletionStats stats) {
        ByteBufCodecs.VAR_INT.encode(buf, stats.deleted);
        ByteBufCodecs.VAR_INT.encode(buf, stats.skippedLoaded);
        ByteBufCodecs.VAR_INT.encode(buf, stats.skippedClaimed);
        ByteBufCodecs.VAR_INT.encode(buf, stats.notGenerated);
        ByteBufCodecs.VAR_INT.encode(buf, stats.regionFilesDeleted);
        ByteBufCodecs.VAR_INT.encode(buf, stats.regionFilesRewritten);
        ByteBufCodecs.VAR_LONG.encode(buf, stats.bytesFreed);
    }

    private static DeletionStats decode(ByteBuf buf) {
        return new DeletionStats(
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_LONG.decode(buf)
        );
    }

    public DeletionStats add(DeletionStats other) {
        return new DeletionStats(
                deleted + other.deleted,
                skippedLoaded + other.skippedLoaded,
                skippedClaimed + other.skippedClaimed,
                notGenerated + other.notGenerated,
                regionFilesDeleted + other.regionFilesDeleted,
                regionFilesRewritten + other.regionFilesRewritten,
                bytesFreed + other.bytesFreed
        );
    }

    /**
     * Human readable size, e.g. "12.3 MB".
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        while (value >= 1024D && unit < units.length - 1) {
            value /= 1024D;
            unit++;
        }
        return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unit]);
    }
}

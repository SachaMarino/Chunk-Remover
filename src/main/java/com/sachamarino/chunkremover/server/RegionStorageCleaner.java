package com.sachamarino.chunkremover.server;

import com.mojang.datafixers.util.Either;
import com.sachamarino.chunkremover.ChunkRemover;
import com.sachamarino.chunkremover.common.ChunkArea;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.BitSet;
import java.util.concurrent.CompletableFuture;

/**
 * Removes chunks from one region-file storage (the "region", "entities" or "poi" folder of a dimension).
 * <p>
 * Region files never shrink when a chunk is cleared, so instead of clearing chunks in place this either deletes the
 * whole region file (when nothing else is left in it) or rewrites it with only the surviving chunks. The work runs
 * on the storage's own IO thread, so it can never race with vanilla reads or writes.
 */
public final class RegionStorageCleaner {
    private RegionStorageCleaner() {
    }

    public record Result(int deleted, int notGenerated, int filesDeleted, int filesRewritten, long bytesFreed, BitSet deletedBits) {
        public static final Result EMPTY = new Result(0, 0, 0, 0, 0L, new BitSet());
    }

    /**
     * @param wanted  bits (see {@link ChunkArea#index}) of the chunks that should be removed
     * @param primary true for the block storage: it decides which chunks count as "deleted" / "not generated"
     */
    public static Result clean(IOWorker worker, ChunkArea area, BitSet wanted, boolean primary) {
        CompletableFuture<Result> future = worker.submitTask(() -> {
            try {
                return Either.left(cleanOnIoThread(worker, area, wanted, primary));
            } catch (IOException e) {
                return Either.right(e);
            }
        });
        return future.join();
    }

    private static Result cleanOnIoThread(IOWorker worker, ChunkArea area, BitSet wanted, boolean primary) throws IOException {
        RegionFileStorage storage = worker.storage;
        RegionStorageInfo info = storage.info();
        Path folder = storage.folder;

        int deleted = 0;
        int notGenerated = 0;
        int filesDeleted = 0;
        int filesRewritten = 0;
        long bytesFreed = 0L;
        BitSet deletedBits = new BitSet();

        for (int regionZ = area.minRegionZ(); regionZ <= area.maxRegionZ(); regionZ++) {
            for (int regionX = area.minRegionX(); regionX <= area.maxRegionX(); regionX++) {
                BitSet local = localWanted(area, wanted, regionX, regionZ);
                if (local.isEmpty()) {
                    continue;
                }

                long regionKey = ChunkPos.asLong(regionX, regionZ);
                worker.regionCacheForBlender.remove(regionKey);
                RegionFile cached = storage.regionCache.remove(regionKey);
                if (cached != null) {
                    cached.close();
                }

                Path file = folder.resolve("r." + regionX + "." + regionZ + RegionFileStorage.ANVIL_EXTENSION);
                if (!Files.isRegularFile(file)) {
                    notGenerated += local.cardinality();
                    continue;
                }

                long sizeBefore = Files.size(file);
                BitSet existing = new BitSet(1024);
                BitSet toDelete;
                BitSet toKeep;

                RegionFile region = new RegionFile(info, file, folder, false);
                boolean closed = false;
                try {
                    for (int i = 0; i < 1024; i++) {
                        if (region.hasChunk(chunkPos(regionX, regionZ, i))) {
                            existing.set(i);
                        }
                    }

                    toDelete = (BitSet) existing.clone();
                    toDelete.and(local);
                    toKeep = (BitSet) existing.clone();
                    toKeep.andNot(local);

                    BitSet missing = (BitSet) local.clone();
                    missing.andNot(existing);
                    notGenerated += missing.cardinality();

                    if (toDelete.isEmpty()) {
                        continue;
                    }

                    if (toKeep.isEmpty()) {
                        region.close();
                        closed = true;
                        Files.delete(file);
                        bytesFreed += sizeBefore;
                        filesDeleted++;
                    } else {
                        Path temp = folder.resolve(file.getFileName() + ".chunkremover-tmp");
                        Files.deleteIfExists(temp);
                        RegionFile rewritten = new RegionFile(info, temp, folder, false);
                        try {
                            for (int i = toKeep.nextSetBit(0); i >= 0; i = toKeep.nextSetBit(i + 1)) {
                                copyChunk(region, rewritten, chunkPos(regionX, regionZ, i));
                            }
                        } finally {
                            rewritten.close();
                        }
                        region.close();
                        closed = true;
                        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                        bytesFreed += sizeBefore - Files.size(file);
                        filesRewritten++;
                    }

                    bytesFreed += deleteExternalFiles(folder, regionX, regionZ, toDelete);
                    deleted += toDelete.cardinality();

                    if (primary) {
                        for (int i = toDelete.nextSetBit(0); i >= 0; i = toDelete.nextSetBit(i + 1)) {
                            ChunkPos pos = chunkPos(regionX, regionZ, i);
                            deletedBits.set(area.index(pos.x, pos.z));
                        }
                    }
                } finally {
                    if (!closed) {
                        region.close();
                    }
                }
            }
        }

        return new Result(deleted, notGenerated, filesDeleted, filesRewritten, bytesFreed, deletedBits);
    }

    /**
     * Bits of the wanted chunks inside one region, indexed by {@code localX + localZ * 32}.
     */
    private static BitSet localWanted(ChunkArea area, BitSet wanted, int regionX, int regionZ) {
        BitSet local = new BitSet(1024);
        for (int localZ = 0; localZ < 32; localZ++) {
            for (int localX = 0; localX < 32; localX++) {
                int chunkX = (regionX << 5) + localX;
                int chunkZ = (regionZ << 5) + localZ;
                if (area.contains(chunkX, chunkZ) && wanted.get(area.index(chunkX, chunkZ))) {
                    local.set(localX + localZ * 32);
                }
            }
        }
        return local;
    }

    private static ChunkPos chunkPos(int regionX, int regionZ, int localIndex) {
        return new ChunkPos((regionX << 5) + (localIndex & 31), (regionZ << 5) + (localIndex >> 5));
    }

    private static void copyChunk(RegionFile from, RegionFile to, ChunkPos pos) throws IOException {
        CompoundTag tag;
        try (DataInputStream in = from.getChunkDataInputStream(pos)) {
            if (in == null) {
                ChunkRemover.LOGGER.warn("Chunk {} of {} could not be read and will be dropped while rewriting the file", pos, from.getPath());
                return;
            }
            tag = NbtIo.read(in);
        }
        try (DataOutputStream out = to.getChunkDataOutputStream(pos)) {
            NbtIo.write(tag, out);
        }
    }

    /**
     * Oversized chunks are stored next to the region file as {@code c.<x>.<z>.mcc}.
     */
    private static long deleteExternalFiles(Path folder, int regionX, int regionZ, BitSet toDelete) throws IOException {
        long freed = 0L;
        for (int i = toDelete.nextSetBit(0); i >= 0; i = toDelete.nextSetBit(i + 1)) {
            ChunkPos pos = chunkPos(regionX, regionZ, i);
            Path external = folder.resolve("c." + pos.x + "." + pos.z + ".mcc");
            if (Files.isRegularFile(external)) {
                freed += Files.size(external);
                Files.delete(external);
            }
        }
        return freed;
    }
}

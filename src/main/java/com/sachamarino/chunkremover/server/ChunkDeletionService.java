package com.sachamarino.chunkremover.server;

import com.sachamarino.chunkremover.ChunkRemover;
import com.sachamarino.chunkremover.ChunkRemoverConfig;
import com.sachamarino.chunkremover.common.ChunkArea;
import com.sachamarino.chunkremover.common.DeletionStats;
import com.sachamarino.chunkremover.common.DeletionStatus;
import com.sachamarino.chunkremover.network.DeleteChunksResultPayload;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.List;

/**
 * Deletes generated chunks from a server level so that they regenerate on the next visit.
 * <p>
 * Must be called on the server thread. The level's block, entity and POI storages are flushed, then the
 * region files are rewritten (or removed) on each storage's IO thread, so the operation is serialized with
 * any vanilla read or write. Chunks that are loaded in any way, and (by default) chunks claimed in FTB Chunks,
 * are left untouched.
 */
public final class ChunkDeletionService {
    private ChunkDeletionService() {
    }

    /**
     * @param bounds      bounding box of the selection, the frame of reference of {@code deletedBits}
     * @param deletedBits chunks actually removed from the block storage (see {@link ChunkArea#index})
     */
    public record Outcome(ChunkArea bounds, DeletionStats stats, BitSet deletedBits) {
    }

    public static boolean isAllowed(MinecraftServer server, ServerPlayer player) {
        return player.hasPermissions(ChunkRemoverConfig.REQUIRED_PERMISSION_LEVEL.get())
                || server.isSingleplayerOwner(player.getGameProfile());
    }

    /**
     * Validates and runs a deletion request. Never throws: failures are reported through the returned status.
     *
     * @param requester the player who asked, or null for the server console
     * @param areas     rectangles to delete (their union is used)
     */
    public static DeleteChunksResultPayload handleRequest(MinecraftServer server, @Nullable ServerPlayer requester,
                                                          ResourceKey<Level> dimension, List<ChunkArea> areas) {
        String who = requester != null ? requester.getGameProfile().getName() : "console";

        if (areas.isEmpty()) {
            return DeleteChunksResultPayload.error(dimension, new ChunkArea(0, 0, 0, 0), DeletionStatus.FAILED);
        }
        ChunkArea bounds = ChunkArea.bounding(areas);

        if (requester != null && !isAllowed(server, requester)) {
            ChunkRemover.LOGGER.warn("{} tried to delete chunks in {} without permission", who, dimension.location());
            return DeleteChunksResultPayload.error(dimension, bounds, DeletionStatus.NO_PERMISSION);
        }

        long max = ChunkRemoverConfig.MAX_CHUNKS_PER_REQUEST.get();
        long selected = ChunkArea.unionCount(areas);
        // The bounding box is also capped: it is the frame of the per-chunk bookkeeping.
        if (selected > max || bounds.chunkCount() > 64L * max) {
            return DeleteChunksResultPayload.error(dimension, bounds, DeletionStatus.TOO_LARGE);
        }

        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return DeleteChunksResultPayload.error(dimension, bounds, DeletionStatus.UNKNOWN_DIMENSION);
        }

        try {
            long start = System.nanoTime();
            Outcome outcome = deleteChunks(level, areas);
            if (ChunkRemoverConfig.LOG_DELETIONS.get()) {
                DeletionStats s = outcome.stats();
                ChunkRemover.LOGGER.info(
                        "{} deleted {} chunks in {} ({} rectangle(s) between chunk ({}, {}) and ({}, {})) in {} ms: {} skipped (loaded), {} skipped (claimed), {} not generated, {} region files deleted, {} rewritten, {} freed",
                        who, s.deleted(), dimension.location(), areas.size(), bounds.minChunkX(), bounds.minChunkZ(), bounds.maxChunkX(), bounds.maxChunkZ(),
                        (System.nanoTime() - start) / 1_000_000L, s.skippedLoaded(), s.skippedClaimed(), s.notGenerated(),
                        s.regionFilesDeleted(), s.regionFilesRewritten(), DeletionStats.formatBytes(s.bytesFreed()));
            }
            return DeleteChunksResultPayload.success(dimension, bounds, outcome.stats(), outcome.deletedBits());
        } catch (Exception e) {
            ChunkRemover.LOGGER.error("Chunk deletion requested by {} failed for {} in {}", who, bounds, dimension.location(), e);
            return DeleteChunksResultPayload.error(dimension, bounds, DeletionStatus.FAILED);
        }
    }

    public static Outcome deleteChunks(ServerLevel level, ChunkArea area) {
        return deleteChunks(level, List.of(area));
    }

    public static Outcome deleteChunks(ServerLevel level, List<ChunkArea> areas) {
        ChunkArea bounds = ChunkArea.bounding(areas);
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        PersistentEntitySectionManager<Entity> entityManager = level.entityManager;
        PoiManager poiManager = level.getPoiManager();
        ClaimedChunkManager claims = ChunkRemoverConfig.PROTECT_CLAIMED_CHUNKS.get() ? FTBChunksAPI.api().getManager() : null;

        // 1. Decide which chunks may be deleted (each chunk of the union considered once).
        BitSet seen = new BitSet((int) bounds.chunkCount());
        BitSet wanted = new BitSet((int) bounds.chunkCount());
        int skippedLoaded = 0;
        int skippedClaimed = 0;
        for (ChunkArea area : areas) {
            for (int chunkZ = area.minChunkZ(); chunkZ <= area.maxChunkZ(); chunkZ++) {
                for (int chunkX = area.minChunkX(); chunkX <= area.maxChunkX(); chunkX++) {
                    int index = bounds.index(chunkX, chunkZ);
                    if (seen.get(index)) {
                        continue;
                    }
                    seen.set(index);
                    if (isLoaded(chunkMap, entityManager, ChunkPos.asLong(chunkX, chunkZ))) {
                        skippedLoaded++;
                    } else if (claims != null && claims.getChunk(new ChunkDimPos(level.dimension(), chunkX, chunkZ)) != null) {
                        skippedClaimed++;
                    } else {
                        wanted.set(index);
                    }
                }
            }
        }

        if (wanted.isEmpty()) {
            return new Outcome(bounds, new DeletionStats(0, skippedLoaded, skippedClaimed, 0, 0, 0, 0L), new BitSet());
        }

        // 2. Drop in-memory POI sections of those chunks so nothing writes them back later.
        forgetPoiSections(poiManager, level, bounds, wanted);

        // 3. Flush every pending write, then rewrite the region files on the IO threads.
        IOWorker chunkWorker = chunkMap.worker;
        IOWorker poiWorker = poiManager.simpleRegionStorage.worker;
        IOWorker entityWorker = entityManager.permanentStorage instanceof EntityStorage entityStorage
                ? entityStorage.simpleRegionStorage.worker
                : null;

        chunkWorker.synchronize(true).join();
        poiWorker.synchronize(true).join();
        if (entityWorker != null) {
            entityWorker.synchronize(true).join();
        }

        RegionStorageCleaner.Result chunks = RegionStorageCleaner.clean(chunkWorker, bounds, wanted, true);
        RegionStorageCleaner.Result poi = RegionStorageCleaner.clean(poiWorker, bounds, wanted, false);
        RegionStorageCleaner.Result entities = entityWorker != null
                ? RegionStorageCleaner.clean(entityWorker, bounds, wanted, false)
                : RegionStorageCleaner.Result.EMPTY;

        DeletionStats stats = new DeletionStats(
                chunks.deleted(),
                skippedLoaded,
                skippedClaimed,
                chunks.notGenerated(),
                chunks.filesDeleted() + poi.filesDeleted() + entities.filesDeleted(),
                chunks.filesRewritten() + poi.filesRewritten() + entities.filesRewritten(),
                chunks.bytesFreed() + poi.bytesFreed() + entities.bytesFreed()
        );
        return new Outcome(bounds, stats, chunks.deletedBits());
    }

    public static boolean isChunkLoaded(ServerLevel level, long chunkKey) {
        return isLoaded(level.getChunkSource().chunkMap, level.entityManager, chunkKey);
    }

    /**
     * True when the chunk is known to the chunk map in any way (loaded, loading, or waiting to be unloaded and saved),
     * or when its entities are still loaded or queued for unloading.
     */
    private static boolean isLoaded(ChunkMap chunkMap, PersistentEntitySectionManager<Entity> entityManager, long chunkKey) {
        return chunkMap.getVisibleChunkIfPresent(chunkKey) != null
                || chunkMap.updatingChunkMap.containsKey(chunkKey)
                || chunkMap.pendingUnloads.containsKey(chunkKey)
                || entityManager.chunkLoadStatuses.containsKey(chunkKey)
                || entityManager.chunksToUnload.contains(chunkKey);
    }

    private static void forgetPoiSections(PoiManager poiManager, ServerLevel level, ChunkArea bounds, BitSet wanted) {
        int minSection = level.getMinSection();
        int maxSection = level.getMaxSection();
        int width = bounds.width();
        for (int index = wanted.nextSetBit(0); index >= 0; index = wanted.nextSetBit(index + 1)) {
            int chunkX = bounds.minChunkX() + index % width;
            int chunkZ = bounds.minChunkZ() + index / width;
            for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
                long sectionKey = SectionPos.asLong(chunkX, sectionY, chunkZ);
                poiManager.remove(sectionKey);
                poiManager.dirty.remove(sectionKey);
            }
        }
    }
}

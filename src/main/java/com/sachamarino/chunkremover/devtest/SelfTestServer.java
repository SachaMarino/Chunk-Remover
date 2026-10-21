package com.sachamarino.chunkremover.devtest;

import com.sachamarino.chunkremover.ChunkRemover;
import com.sachamarino.chunkremover.common.ChunkArea;
import com.sachamarino.chunkremover.common.DeletionStats;
import com.sachamarino.chunkremover.server.ChunkDeletionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Dedicated-server integration test of the deletion itself:
 * <ol>
 *     <li>force-load two groups of chunks far from spawn so they generate, put marker blocks in them, unload them;</li>
 *     <li>delete a whole region (the file must disappear) and part of another one (the file must be rewritten);</li>
 *     <li>reload the chunks: deleted ones regenerated (marker gone), kept ones intact (marker still there);</li>
 *     <li>check that loaded chunks are skipped.</li>
 * </ol>
 * Logs "SERVER SELFTEST PASSED" or "SERVER SELFTEST FAILED" and stops the server.
 */
final class SelfTestServer {
    /** Group A: 3 x 3 chunks inside region (10, 10); the whole region gets deleted. */
    private static final ChunkArea GROUP_A = ChunkArea.ofChunks(330, 330, 332, 332);
    private static final ChunkArea REGION_A = ChunkArea.ofChunks(320, 320, 351, 351);
    private static final BlockPos MARKER_A = new BlockPos(330 * 16 + 8, 100, 330 * 16 + 8);

    /** Group B: 4 x 4 chunks inside region (12, 12); only a 2 x 2 corner gets deleted. */
    private static final ChunkArea GROUP_B = ChunkArea.ofChunks(400, 400, 403, 403);
    private static final ChunkArea DELETE_B = ChunkArea.ofChunks(402, 402, 403, 403);
    private static final BlockPos MARKER_B_KEPT = new BlockPos(400 * 16 + 8, 100, 400 * 16 + 8);
    private static final BlockPos MARKER_B_DELETED = new BlockPos(403 * 16 + 8, 100, 403 * 16 + 8);

    private record Step(String name, Predicate<ServerLevel> tick, int timeoutTicks) {
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> FAILURES = new ArrayList<>();
    private static ServerLevel level;
    private static int index;
    private static int waited;
    private static boolean finished;

    private SelfTestServer() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(SelfTestServer::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SelfTestServer::onServerTick);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        buildSteps();
        ChunkRemover.LOGGER.info("[SelfTest] starting server self test with {} steps", STEPS.size());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (level == null || finished) {
            return;
        }
        if (index >= STEPS.size()) {
            finish(event.getServer());
            return;
        }

        Step step = STEPS.get(index);
        boolean done;
        try {
            done = step.tick().test(level);
        } catch (Throwable t) {
            ChunkRemover.LOGGER.error("[SelfTest] step '{}' threw", step.name(), t);
            fail(step.name() + " threw " + t);
            done = true;
        }

        if (done) {
            ChunkRemover.LOGGER.info("[SelfTest] step {}/{} done: {}", index + 1, STEPS.size(), step.name());
            index++;
            waited = 0;
        } else if (++waited > step.timeoutTicks()) {
            fail("timeout while waiting for: " + step.name());
            index++;
            waited = 0;
        }
    }

    private static void finish(MinecraftServer server) {
        finished = true;
        if (FAILURES.isEmpty()) {
            ChunkRemover.LOGGER.info("[SelfTest] SERVER SELFTEST PASSED");
        } else {
            ChunkRemover.LOGGER.error("[SelfTest] SERVER SELFTEST FAILED ({} failure(s)):", FAILURES.size());
            FAILURES.forEach(f -> ChunkRemover.LOGGER.error("[SelfTest]   - {}", f));
        }
        server.halt(false);
    }

    private static void fail(String message) {
        ChunkRemover.LOGGER.error("[SelfTest] FAIL: {}", message);
        FAILURES.add(message);
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            fail(message);
        }
    }

    private static void act(String name, Predicate<ServerLevel> action) {
        STEPS.add(new Step(name, action, 1));
    }

    private static void waitFor(String name, Predicate<ServerLevel> condition, int timeoutTicks) {
        STEPS.add(new Step(name, condition, timeoutTicks));
    }

    private static void buildSteps() {
        act("force-load groups A and B", l -> {
            setForced(l, GROUP_A, true);
            setForced(l, GROUP_B, true);
            return true;
        });
        waitFor("groups A and B fully generated", l -> allFullyLoaded(l, GROUP_A) && allFullyLoaded(l, GROUP_B), 20 * 180);
        act("place marker blocks", l -> {
            for (BlockPos pos : List.of(MARKER_A, MARKER_B_KEPT, MARKER_B_DELETED)) {
                l.setBlock(pos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
                expect(l.getBlockState(pos).is(Blocks.DIAMOND_BLOCK), "marker placed at " + pos);
            }
            return true;
        });
        act("save the level", l -> {
            l.save(null, true, false);
            return true;
        });
        act("unforce groups A and B", l -> {
            setForced(l, GROUP_A, false);
            setForced(l, GROUP_B, false);
            return true;
        });
        waitFor("groups A and B unloaded", l -> noneLoaded(l, GROUP_A) && noneLoaded(l, GROUP_B), 20 * 120);
        act("region files exist on disk", l -> {
            expect(Files.isRegularFile(regionFile(l, 10, 10)), "region file r.10.10.mca exists before deletion");
            expect(Files.isRegularFile(regionFile(l, 12, 12)), "region file r.12.12.mca exists before deletion");
            return true;
        });
        act("delete whole region (10, 10)", l -> {
            DeletionStats stats = ChunkDeletionService.deleteChunks(l, REGION_A).stats();
            ChunkRemover.LOGGER.info("[SelfTest] region A deletion: {}", stats);
            expect(stats.deleted() >= 9, "at least the 9 chunks of group A deleted, got " + stats.deleted());
            expect(stats.skippedLoaded() == 0, "no loaded chunk in region A, got " + stats.skippedLoaded());
            expect(stats.regionFilesDeleted() >= 1, "region file deleted, got " + stats.regionFilesDeleted());
            expect(!Files.exists(regionFile(l, 10, 10)), "region file r.10.10.mca gone after deletion");
            expect(stats.bytesFreed() > 0, "bytes freed > 0, got " + stats.bytesFreed());
            return true;
        });
        act("delete 2 x 2 chunks of region (12, 12)", l -> {
            long sizeBefore = fileSize(regionFile(l, 12, 12));
            DeletionStats stats = ChunkDeletionService.deleteChunks(l, DELETE_B).stats();
            ChunkRemover.LOGGER.info("[SelfTest] partial deletion in region B: {}", stats);
            expect(stats.deleted() == 4, "exactly 4 chunks deleted, got " + stats.deleted());
            expect(stats.regionFilesRewritten() >= 1, "region file rewritten, got " + stats.regionFilesRewritten());
            expect(Files.isRegularFile(regionFile(l, 12, 12)), "region file r.12.12.mca still exists");
            long sizeAfter = fileSize(regionFile(l, 12, 12));
            expect(sizeAfter < sizeBefore, "region file shrank: " + sizeBefore + " -> " + sizeAfter);
            return true;
        });
        act("re-load one chunk of A and two of B", l -> {
            l.setChunkForced(330, 330, true);
            l.setChunkForced(400, 400, true);
            l.setChunkForced(403, 403, true);
            return true;
        });
        waitFor("chunks reloaded", l -> l.getChunkSource().getChunkNow(330, 330) != null
                && l.getChunkSource().getChunkNow(400, 400) != null
                && l.getChunkSource().getChunkNow(403, 403) != null, 20 * 180);
        act("verify markers", l -> {
            expect(!l.getBlockState(MARKER_A).is(Blocks.DIAMOND_BLOCK), "chunk (330, 330) was regenerated (marker gone)");
            expect(l.getBlockState(MARKER_B_KEPT).is(Blocks.DIAMOND_BLOCK), "chunk (400, 400) kept its data through the rewrite (marker present)");
            expect(!l.getBlockState(MARKER_B_DELETED).is(Blocks.DIAMOND_BLOCK), "chunk (403, 403) was regenerated (marker gone)");
            return true;
        });
        act("loaded chunks are skipped", l -> {
            DeletionStats stats = ChunkDeletionService.deleteChunks(l, ChunkArea.ofChunks(400, 400, 400, 400)).stats();
            expect(stats.deleted() == 0, "nothing deleted for a loaded chunk, got " + stats.deleted());
            expect(stats.skippedLoaded() == 1, "one chunk skipped as loaded, got " + stats.skippedLoaded());
            return true;
        });
        act("cleanup", l -> {
            l.setChunkForced(330, 330, false);
            l.setChunkForced(400, 400, false);
            l.setChunkForced(403, 403, false);
            return true;
        });
    }

    private static void setForced(ServerLevel level, ChunkArea area, boolean forced) {
        for (int z = area.minChunkZ(); z <= area.maxChunkZ(); z++) {
            for (int x = area.minChunkX(); x <= area.maxChunkX(); x++) {
                level.setChunkForced(x, z, forced);
            }
        }
    }

    private static boolean allFullyLoaded(ServerLevel level, ChunkArea area) {
        for (int z = area.minChunkZ(); z <= area.maxChunkZ(); z++) {
            for (int x = area.minChunkX(); x <= area.maxChunkX(); x++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean noneLoaded(ServerLevel level, ChunkArea area) {
        for (int z = area.minChunkZ(); z <= area.maxChunkZ(); z++) {
            for (int x = area.minChunkX(); x <= area.maxChunkX(); x++) {
                if (ChunkDeletionService.isChunkLoaded(level, ChunkPos.asLong(x, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Path regionFile(ServerLevel level, int regionX, int regionZ) {
        return level.getChunkSource().chunkMap.worker.storage.folder.resolve("r." + regionX + "." + regionZ + ".mca");
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception e) {
            return -1L;
        }
    }
}

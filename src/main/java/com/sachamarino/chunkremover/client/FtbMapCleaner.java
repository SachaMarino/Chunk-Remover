package com.sachamarino.chunkremover.client;

import com.sachamarino.chunkremover.ChunkRemover;
import com.sachamarino.chunkremover.common.ChunkArea;
import com.sachamarino.chunkremover.mixin.client.MapRegionAccessor;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.client.map.MapManager;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftbchunks.client.map.MapRegionData;
import dev.ftb.mods.ftbchunks.util.HeightUtils;
import dev.ftb.mods.ftblibrary.math.XZ;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Removes deleted chunks from the client-side FTB Chunks map data, so they show up as unexplored again.
 */
public final class FtbMapCleaner {
    private FtbMapCleaner() {
    }

    public static void forgetChunks(ResourceKey<Level> dimensionKey, ChunkArea area, BitSet deleted) {
        MapManager.getInstance().ifPresent(manager -> forgetChunks(manager, dimensionKey, area, deleted));
    }

    private static void forgetChunks(MapManager manager, ResourceKey<Level> dimensionKey, ChunkArea area, BitSet deleted) {
        MapDimension dimension = manager.getDimensions().get(dimensionKey);
        if (dimension == null) {
            return;
        }

        Set<MapRegion> touched = new LinkedHashSet<>();
        int removed = 0;

        synchronized (manager.lock) {
            Map<XZ, MapRegion> regions = dimension.getRegions();

            for (int rz = area.minRegionZ(); rz <= area.maxRegionZ(); rz++) {
                for (int rx = area.minRegionX(); rx <= area.maxRegionX(); rx++) {
                    MapRegion region = regions.get(XZ.of(rx, rz));
                    if (region == null) {
                        continue;
                    }

                    Map<XZ, MapChunk> chunks = ((MapRegionAccessor) region).chunkremover$getChunks();
                    MapRegionData data = null;

                    for (int cz = 0; cz < 32; cz++) {
                        for (int cx = 0; cx < 32; cx++) {
                            int chunkX = (rx << 5) + cx;
                            int chunkZ = (rz << 5) + cz;
                            if (!area.contains(chunkX, chunkZ) || !deleted.get(area.index(chunkX, chunkZ))) {
                                continue;
                            }
                            if (chunks.remove(XZ.of(cx, cz)) == null) {
                                continue;
                            }
                            if (data == null) {
                                data = region.getDataBlocking();
                            }
                            clearChunkPixels(data, cx, cz);
                            touched.add(region);
                            removed++;
                        }
                    }
                }
            }

            for (MapRegion region : touched) {
                if (((MapRegionAccessor) region).chunkremover$getChunks().isEmpty()) {
                    // Nothing left in this region: drop it and its file so the stale data can never be read back.
                    regions.remove(region.pos);
                    region.release(true);
                    dimension.markDirty();
                    Path file = dimension.directory.resolve(region.pos.toRegionString() + ".zip");
                    FTBChunksClient.MAP_EXECUTOR.execute(() -> {
                        try {
                            Files.deleteIfExists(file);
                        } catch (IOException e) {
                            ChunkRemover.LOGGER.warn("Could not delete FTB Chunks map region file {}", file, e);
                        }
                    });
                } else {
                    region.update(true);
                }
            }
        }

        if (removed > 0) {
            manager.saveAllRegions();
            FTBChunksClient.INSTANCE.scheduleMinimapUpdate();
            ChunkRemover.LOGGER.debug("Forgot {} chunks on the FTB Chunks map in {}", removed, dimensionKey.location());
        }
    }

    private static void clearChunkPixels(MapRegionData data, int cx, int cz) {
        for (int z = 0; z < 16; z++) {
            int row = (cz * 16 + z) * 512 + cx * 16;
            for (int x = 0; x < 16; x++) {
                int index = row + x;
                data.height[index] = (short) HeightUtils.UNKNOWN;
                data.waterLightAndBiome[index] = 0;
                data.foliage[index] = 0;
                data.grass[index] = 0;
                data.water[index] = 0;
            }
        }
    }
}

package com.sachamarino.chunkremover.mixin.client;

import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftblibrary.math.XZ;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = MapRegion.class, remap = false)
public interface MapRegionAccessor {
    @Accessor("chunks")
    Map<XZ, MapChunk> chunkremover$getChunks();
}

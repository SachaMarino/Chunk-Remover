package com.sachamarino.chunkremover.network;

import com.sachamarino.chunkremover.ChunkRemover;
import com.sachamarino.chunkremover.common.ChunkArea;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Client to server: "delete every generated chunk inside these areas of this dimension" (union of rectangles).
 */
public record DeleteChunksRequestPayload(ResourceKey<Level> dimension, List<ChunkArea> areas) implements CustomPacketPayload {
    public static final Type<DeleteChunksRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ChunkRemover.MOD_ID, "delete_chunks"));

    public static final int MAX_AREAS = 256;

    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteChunksRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), DeleteChunksRequestPayload::dimension,
            ChunkArea.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_AREAS)), DeleteChunksRequestPayload::areas,
            DeleteChunksRequestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

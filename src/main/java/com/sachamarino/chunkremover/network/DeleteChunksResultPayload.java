package com.sachamarino.chunkremover.network;

import com.sachamarino.chunkremover.ChunkRemover;
import com.sachamarino.chunkremover.common.ChunkArea;
import com.sachamarino.chunkremover.common.DeletionStats;
import com.sachamarino.chunkremover.common.DeletionStatus;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.BitSet;

/**
 * Server to client: result of a deletion request.
 *
 * @param deletedBits row-major bit set over {@code area} (see {@link ChunkArea#index}); a set bit means the chunk was
 *                    deleted and the client map should forget it. Empty when the server chose not to send it.
 */
public record DeleteChunksResultPayload(ResourceKey<Level> dimension, ChunkArea area, DeletionStatus status,
                                        DeletionStats stats, byte[] deletedBits) implements CustomPacketPayload {
    public static final Type<DeleteChunksResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ChunkRemover.MOD_ID, "delete_chunks_result"));

    /**
     * Keep the payload well under the 1 MiB custom payload limit.
     */
    public static final int MAX_BITS_BYTES = 512 * 1024;

    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteChunksResultPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), DeleteChunksResultPayload::dimension,
            ChunkArea.STREAM_CODEC, DeleteChunksResultPayload::area,
            DeletionStatus.STREAM_CODEC, DeleteChunksResultPayload::status,
            DeletionStats.STREAM_CODEC, DeleteChunksResultPayload::stats,
            ByteBufCodecs.byteArray(MAX_BITS_BYTES), DeleteChunksResultPayload::deletedBits,
            DeleteChunksResultPayload::new
    );

    public static DeleteChunksResultPayload error(ResourceKey<Level> dimension, ChunkArea area, DeletionStatus status) {
        return new DeleteChunksResultPayload(dimension, area, status, DeletionStats.EMPTY, new byte[0]);
    }

    public static DeleteChunksResultPayload success(ResourceKey<Level> dimension, ChunkArea area, DeletionStats stats, BitSet deletedBits) {
        byte[] bytes = deletedBits.toByteArray();
        if (bytes.length > MAX_BITS_BYTES) {
            bytes = new byte[0];
        }
        return new DeleteChunksResultPayload(dimension, area, DeletionStatus.OK, stats, bytes);
    }

    public boolean hasDeletedBits() {
        return deletedBits.length > 0;
    }

    public BitSet deletedBitSet() {
        return BitSet.valueOf(deletedBits);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

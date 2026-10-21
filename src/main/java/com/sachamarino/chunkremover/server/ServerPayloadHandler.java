package com.sachamarino.chunkremover.server;

import com.sachamarino.chunkremover.network.DeleteChunksRequestPayload;
import com.sachamarino.chunkremover.network.DeleteChunksResultPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerPayloadHandler {
    private ServerPayloadHandler() {
    }

    /**
     * Runs on the server thread (NeoForge default for play payloads), which the deletion relies on.
     */
    public static void handleDeleteRequest(DeleteChunksRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        DeleteChunksResultPayload result = ChunkDeletionService.handleRequest(player.server, player, payload.dimension(), payload.areas());
        PacketDistributor.sendToPlayer(player, result);
    }
}

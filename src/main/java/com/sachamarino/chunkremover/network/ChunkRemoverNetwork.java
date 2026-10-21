package com.sachamarino.chunkremover.network;

import com.sachamarino.chunkremover.ChunkRemover;
import com.sachamarino.chunkremover.client.ClientPayloadHandler;
import com.sachamarino.chunkremover.server.ServerPayloadHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = ChunkRemover.MOD_ID)
public final class ChunkRemoverNetwork {
    private ChunkRemoverNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(DeleteChunksRequestPayload.TYPE, DeleteChunksRequestPayload.STREAM_CODEC,
                (payload, context) -> ServerPayloadHandler.handleDeleteRequest(payload, context));

        // Lambda body so the client-only class is never loaded on a dedicated server.
        registrar.playToClient(DeleteChunksResultPayload.TYPE, DeleteChunksResultPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleResult(payload, context));
    }
}

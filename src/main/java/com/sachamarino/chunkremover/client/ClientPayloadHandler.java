package com.sachamarino.chunkremover.client;

import com.sachamarino.chunkremover.common.DeletionMessages;
import com.sachamarino.chunkremover.common.DeletionStats;
import com.sachamarino.chunkremover.common.DeletionStatus;
import com.sachamarino.chunkremover.network.DeleteChunksResultPayload;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import dev.ftb.mods.ftblibrary.ui.misc.SimpleToast;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void handleResult(DeleteChunksResultPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Component toastTitle = Component.translatable("chunkremover.toast.title");

        if (payload.status() == DeletionStatus.OK) {
            DeletionStats stats = payload.stats();
            mc.player.displayClientMessage(DeletionMessages.result(stats), false);
            mc.player.displayClientMessage(DeletionMessages.files(stats), false);
            SimpleToast.info(toastTitle, Component.translatable("chunkremover.toast.deleted",
                    DeletionMessages.number(stats.deleted()), DeletionStats.formatBytes(stats.bytesFreed())));

            if (payload.hasDeletedBits()) {
                FtbMapCleaner.forgetChunks(payload.dimension(), payload.area(), payload.deletedBitSet());
            }
        } else {
            Component message = DeletionMessages.error(payload.status(), payload.area(), payload.dimension());
            mc.player.displayClientMessage(message, false);
            SimpleToast.error(toastTitle, message);
        }

        if (mc.screen instanceof ScreenWrapper wrapper && wrapper.getGui() instanceof ChunkRemoverMapScreen mapScreen) {
            mapScreen.chunkremover$onDeletionFinished();
        }
    }
}

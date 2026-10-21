package com.sachamarino.chunkremover.common;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * Chat / command feedback shared by the client and the server.
 */
public final class DeletionMessages {
    private DeletionMessages() {
    }

    public static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    public static MutableComponent result(DeletionStats stats) {
        return Component.translatable("chunkremover.message.result",
                Component.literal(number(stats.deleted())).withStyle(ChatFormatting.GREEN),
                number(stats.skippedLoaded()),
                number(stats.skippedClaimed()),
                number(stats.notGenerated()));
    }

    public static MutableComponent files(DeletionStats stats) {
        return Component.translatable("chunkremover.message.result.files",
                number(stats.regionFilesDeleted()),
                number(stats.regionFilesRewritten()),
                Component.literal(DeletionStats.formatBytes(stats.bytesFreed())).withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY);
    }

    public static MutableComponent error(DeletionStatus status, ChunkArea area, ResourceKey<Level> dimension) {
        MutableComponent message = switch (status) {
            case TOO_LARGE -> Component.translatable(status.translationKey(), number(area.chunkCount()));
            case UNKNOWN_DIMENSION -> Component.translatable(status.translationKey(), dimension.location().toString());
            default -> Component.translatable(status.translationKey());
        };
        return message.withStyle(ChatFormatting.RED);
    }
}

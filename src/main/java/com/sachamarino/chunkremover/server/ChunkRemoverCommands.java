package com.sachamarino.chunkremover.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.sachamarino.chunkremover.ChunkRemoverConfig;
import com.sachamarino.chunkremover.common.ChunkArea;
import com.sachamarino.chunkremover.common.DeletionMessages;
import com.sachamarino.chunkremover.common.DeletionStatus;
import com.sachamarino.chunkremover.network.DeleteChunksResultPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Command fallback, usable from the server console or without opening the map:
 * <pre>
 *   /chunkremover delete chunks &lt;x1&gt; &lt;z1&gt; &lt;x2&gt; &lt;z2&gt; [dimension]   (chunk coordinates)
 *   /chunkremover delete blocks &lt;x1&gt; &lt;z1&gt; &lt;x2&gt; &lt;z2&gt; [dimension]   (block coordinates)
 * </pre>
 */
public final class ChunkRemoverCommands {
    private ChunkRemoverCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("chunkremover")
                .requires(source -> source.hasPermission(ChunkRemoverConfig.REQUIRED_PERMISSION_LEVEL.get()))
                .then(Commands.literal("delete")
                        .then(Commands.literal("chunks").then(coordinates(false)))
                        .then(Commands.literal("blocks").then(coordinates(true)))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> coordinates(boolean blocks) {
        return Commands.argument("x1", IntegerArgumentType.integer())
                .then(Commands.argument("z1", IntegerArgumentType.integer())
                        .then(Commands.argument("x2", IntegerArgumentType.integer())
                                .then(Commands.argument("z2", IntegerArgumentType.integer())
                                        .executes(ctx -> run(ctx, blocks, ctx.getSource().getLevel()))
                                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                .executes(ctx -> run(ctx, blocks, DimensionArgument.getDimension(ctx, "dimension")))))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, boolean blocks, ServerLevel level) {
        int x1 = IntegerArgumentType.getInteger(ctx, "x1");
        int z1 = IntegerArgumentType.getInteger(ctx, "z1");
        int x2 = IntegerArgumentType.getInteger(ctx, "x2");
        int z2 = IntegerArgumentType.getInteger(ctx, "z2");
        ChunkArea area = blocks ? ChunkArea.ofBlocks(x1, z1, x2, z2) : ChunkArea.ofChunks(x1, z1, x2, z2);

        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        DeleteChunksResultPayload result = ChunkDeletionService.handleRequest(source.getServer(), player, level.dimension(), List.of(area));

        if (result.status() != DeletionStatus.OK) {
            source.sendFailure(DeletionMessages.error(result.status(), area, level.dimension()));
            return 0;
        }

        source.sendSuccess(() -> DeletionMessages.result(result.stats()), true);
        source.sendSuccess(() -> DeletionMessages.files(result.stats()), false);

        if (player != null && result.hasDeletedBits()) {
            // Let the player's FTB Chunks map forget the deleted chunks too.
            PacketDistributor.sendToPlayer(player, result);
        }
        return result.stats().deleted();
    }
}

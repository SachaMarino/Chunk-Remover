package com.sachamarino.chunkremover.client.gui;

import com.sachamarino.chunkremover.client.ChunkRemoverMapScreen;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * The bin icon added to the left-hand button bar of the FTB Chunks large map.
 */
public class DeleteChunksButton extends SimpleButton {
    private static final Color4I ACTIVE_BACKGROUND = Color4I.rgba(255, 70, 70, 110);

    private final ChunkRemoverMapScreen mapScreen;

    public DeleteChunksButton(Panel panel, ChunkRemoverMapScreen mapScreen) {
        super(panel, Component.literal("Chunk Remover"), Icons.BIN,
                (button, mouseButton) -> mapScreen.chunkremover$onDeleteButtonClicked());
        this.mapScreen = mapScreen;
    }

    /**
     * Only operators and the owner of a singleplayer world get the button. The server checks again anyway.
     */
    public static boolean canUse() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && (mc.player.hasPermissions(Commands.LEVEL_GAMEMASTERS) || mc.isSingleplayer());
    }

    @Override
    public void addMouseOverText(TooltipList list) {
        super.addMouseOverText(list);
        String key = mapScreen.chunkremover$getSelection().isActive()
                ? "chunkremover.gui.delete_chunks.tooltip.active"
                : "chunkremover.gui.delete_chunks.tooltip";
        list.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        if (mapScreen.chunkremover$getSelection().isActive()) {
            ACTIVE_BACKGROUND.draw(graphics, x - 1, y - 1, w + 2, h + 2);
        }
    }
}

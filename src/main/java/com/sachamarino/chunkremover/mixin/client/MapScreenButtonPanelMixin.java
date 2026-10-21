package com.sachamarino.chunkremover.mixin.client;

import com.sachamarino.chunkremover.client.ChunkRemoverMapScreen;
import com.sachamarino.chunkremover.client.gui.DeleteChunksButton;
import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftblibrary.ui.Panel;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the "delete chunks" button to the button bar on the left of the FTB Chunks large map.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.MapScreenButtonPanel", remap = false)
public abstract class MapScreenButtonPanelMixin extends Panel {
    @Unique
    private DeleteChunksButton chunkremover$deleteButton;

    protected MapScreenButtonPanelMixin(Panel panel) {
        super(panel);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void chunkremover$init(LargeMapScreen largeMapScreen, CallbackInfo ci) {
        chunkremover$deleteButton = new DeleteChunksButton(this, (ChunkRemoverMapScreen) largeMapScreen);
    }

    @Inject(method = "addWidgets", at = @At("TAIL"))
    private void chunkremover$addWidgets(CallbackInfo ci) {
        if (chunkremover$deleteButton != null && DeleteChunksButton.canUse()) {
            add(chunkremover$deleteButton);
        }
    }

    @Inject(method = "alignWidgets", at = @At("TAIL"))
    private void chunkremover$alignWidgets(CallbackInfo ci) {
        if (chunkremover$deleteButton != null) {
            // Lower button group of FTB Chunks: minimap info sort, client settings, server settings (operators only).
            // The bin goes right above them, in the same 18-pixel grid.
            int yOff = Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(Commands.LEVEL_GAMEMASTERS) ? 18 : 0;
            chunkremover$deleteButton.setPosAndSize(1, height - 54 - yOff, 16, 16);
        }
    }
}

package com.sachamarino.chunkremover.mixin.client;

import com.sachamarino.chunkremover.client.ChunkRemoverMapScreen;
import com.sachamarino.chunkremover.client.MapSelection;
import com.sachamarino.chunkremover.common.ChunkArea;
import com.sachamarino.chunkremover.common.DeletionMessages;
import com.sachamarino.chunkremover.network.DeleteChunksRequestPayload;
import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.Key;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Adds the "delete chunks" selection tool to the FTB Chunks large map: keyboard handling, confirmation
 * and the on-screen hint. Mouse handling on the map itself is in {@link RegionMapPanelMixin}.
 */
@Mixin(value = LargeMapScreen.class, remap = false)
public abstract class LargeMapScreenMixin extends BaseScreen implements ChunkRemoverMapScreen {
    @Shadow
    MapDimension dimension;

    @Unique
    private MapSelection chunkremover$selection;

    @Override
    public MapSelection chunkremover$getSelection() {
        if (chunkremover$selection == null) {
            chunkremover$selection = new MapSelection();
        }
        return chunkremover$selection;
    }

    /**
     * The bin icon toggles the tool: on when off, cancel when on. Deleting is only done with Enter / Delete.
     */
    @Override
    public void chunkremover$onDeleteButtonClicked() {
        MapSelection selection = chunkremover$getSelection();
        selection.setActive(!selection.isActive());
    }

    @Override
    public void chunkremover$onDeletionFinished() {
        chunkremover$getSelection().reset();
        refreshWidgets();
    }

    @Inject(method = "mousePressed", at = @At("HEAD"), cancellable = true)
    private void chunkremover$mousePressed(MouseButton button, CallbackInfoReturnable<Boolean> cir) {
        MapSelection selection = chunkremover$getSelection();
        if (selection.isActive() && button.isRight() && !anyModalPanelOpen()) {
            // Right click clears the current selection, or leaves the tool when there is none.
            if (selection.hasArea() || selection.isDragging()) {
                selection.clearAreas();
            } else {
                selection.reset();
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void chunkremover$keyPressed(Key key, CallbackInfoReturnable<Boolean> cir) {
        MapSelection selection = chunkremover$getSelection();
        if (!selection.isActive() || anyModalPanelOpen()) {
            return;
        }

        if (key.esc()) {
            selection.reset();
            cir.setReturnValue(true);
        } else if (key.enter() || key.is(GLFW.GLFW_KEY_KP_ENTER) || key.is(GLFW.GLFW_KEY_DELETE)) {
            if (selection.hasArea()) {
                chunkremover$confirm();
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "drawForeground", at = @At("TAIL"))
    private void chunkremover$drawForeground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h, CallbackInfo ci) {
        MapSelection selection = chunkremover$getSelection();
        if (!selection.isActive()) {
            return;
        }

        Component hint;
        if (selection.hasArea() || selection.isDragging()) {
            hint = Component.translatable("chunkremover.gui.hint.selected", DeletionMessages.number(selection.chunkCount()));
        } else {
            hint = Component.translatable("chunkremover.gui.hint.select");
        }

        int textWidth = theme.getStringWidth(hint);
        int boxX = x + (w - textWidth) / 2 - 4;
        int boxY = y + 12;
        Color4I.rgba(160, 30, 30, 170).draw(graphics, boxX, boxY, textWidth + 8, theme.getFontHeight() + 6);
        theme.drawString(graphics, hint, boxX + 4, boxY + 3, Theme.SHADOW);
    }

    @Unique
    private void chunkremover$confirm() {
        MapSelection selection = chunkremover$getSelection();
        List<ChunkArea> areas = selection.areas();
        ResourceKey<Level> dimensionKey = dimension.dimension;
        String count = DeletionMessages.number(ChunkArea.unionCount(areas));

        Component title = Component.translatable("chunkremover.gui.confirm.title", count);
        Component description = Component.translatable("chunkremover.gui.confirm.desc", count, dimensionKey.location().toString());

        openYesNo(title, description, () -> {
            PacketDistributor.sendToServer(new DeleteChunksRequestPayload(dimensionKey, areas));
            selection.reset();
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.translatable("chunkremover.message.request_sent"), true);
            }
        });
    }
}

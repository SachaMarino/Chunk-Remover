package com.sachamarino.chunkremover.mixin.client;

import com.sachamarino.chunkremover.client.ChunkRemoverMapScreen;
import com.sachamarino.chunkremover.client.MapSelection;
import com.sachamarino.chunkremover.common.ChunkArea;
import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.gui.RegionMapPanel;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mouse handling and overlay drawing of the "delete chunks" selection on the map panel of the large map.
 */
@Mixin(value = RegionMapPanel.class, remap = false)
public abstract class RegionMapPanelMixin extends Panel {
    @Unique
    private static final Color4I CHUNKREMOVER_FILL = Color4I.rgba(230, 50, 50, 70);
    @Unique
    private static final Color4I CHUNKREMOVER_BORDER = Color4I.rgba(255, 80, 80, 220);
    @Unique
    private static final Color4I CHUNKREMOVER_HOVER = Color4I.rgba(255, 255, 255, 120);

    @Shadow
    @Final
    LargeMapScreen largeMap;
    @Shadow
    int regionMinX;
    @Shadow
    int regionMinZ;
    @Shadow
    int blockX;
    @Shadow
    int blockZ;

    protected RegionMapPanelMixin(Panel panel) {
        super(panel);
    }

    @Unique
    private MapSelection chunkremover$selection() {
        return ((ChunkRemoverMapScreen) largeMap).chunkremover$getSelection();
    }

    @Inject(method = "mousePressed", at = @At("HEAD"), cancellable = true)
    private void chunkremover$mousePressed(MouseButton button, CallbackInfoReturnable<Boolean> cir) {
        MapSelection selection = chunkremover$selection();
        if (!selection.isActive() || !isMouseOver()) {
            return;
        }

        if (button.isLeft()) {
            // Shift keeps the rectangles already selected and adds a new one.
            selection.beginDrag(blockX >> 4, blockZ >> 4, isShiftKeyDown());
            cir.setReturnValue(true);
        } else if (button.isMiddle()) {
            // The left button selects while the tool is active, so the middle button pans the map instead.
            LargeMapScreenAccessor accessor = (LargeMapScreenAccessor) largeMap;
            accessor.chunkremover$setPrevMouseX(getMouseX());
            accessor.chunkremover$setPrevMouseY(getMouseY());
            accessor.chunkremover$setGrabbed(1);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("TAIL"))
    private void chunkremover$mouseReleased(MouseButton button, CallbackInfo ci) {
        MapSelection selection = chunkremover$selection();
        if (selection.isDragging() && button.isLeft()) {
            selection.endDrag(blockX >> 4, blockZ >> 4);
        }
    }

    @Inject(method = "draw", at = @At("TAIL"))
    private void chunkremover$draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h, CallbackInfo ci) {
        MapSelection selection = chunkremover$selection();
        if (!selection.isActive()) {
            return;
        }

        if (selection.isDragging()) {
            selection.updateDrag(blockX >> 4, blockZ >> 4);
        }

        if (selection.hasArea() || selection.isDragging()) {
            for (ChunkArea area : selection.visibleAreas()) {
                chunkremover$drawChunkRect(graphics, x, y, w, h, area.minChunkX(), area.minChunkZ(), area.maxChunkX(), area.maxChunkZ(),
                        CHUNKREMOVER_FILL, CHUNKREMOVER_BORDER);
            }
        }
        if (isMouseOver() && !selection.isDragging()) {
            int chunkX = blockX >> 4;
            int chunkZ = blockZ >> 4;
            chunkremover$drawChunkRect(graphics, x, y, w, h, chunkX, chunkZ, chunkX, chunkZ, Color4I.empty(), CHUNKREMOVER_HOVER);
        }
    }

    /**
     * Screen X of the west edge of a chunk column. Inverse of the mouse -> block mapping done in RegionMapPanel.draw().
     */
    @Unique
    private int chunkremover$screenX(int chunkX, int panelX) {
        double tile = largeMap.getRegionTileSize();
        return (int) Math.round((chunkX / 32D - regionMinX) * tile - getScrollX() + panelX);
    }

    @Unique
    private int chunkremover$screenY(int chunkZ, int panelY) {
        double tile = largeMap.getRegionTileSize();
        return (int) Math.round((chunkZ / 32D - regionMinZ) * tile - getScrollY() + panelY);
    }

    @Unique
    private void chunkremover$drawChunkRect(GuiGraphics graphics, int x, int y, int w, int h,
                                            int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ,
                                            Color4I fill, Color4I border) {
        int x0 = Math.max(x, chunkremover$screenX(minChunkX, x));
        int y0 = Math.max(y, chunkremover$screenY(minChunkZ, y));
        int x1 = Math.min(x + w, chunkremover$screenX(maxChunkX + 1, x));
        int y1 = Math.min(y + h, chunkremover$screenY(maxChunkZ + 1, y));
        if (x1 <= x0 || y1 <= y0) {
            return;
        }

        if (!fill.isEmpty()) {
            fill.draw(graphics, x0, y0, x1 - x0, y1 - y0);
        }
        GuiHelper.drawHollowRect(graphics, x0, y0, x1 - x0, y1 - y0, border, false);
    }
}

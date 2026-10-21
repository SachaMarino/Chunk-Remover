package com.sachamarino.chunkremover.mixin.client;

import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = LargeMapScreen.class, remap = false)
public interface LargeMapScreenAccessor {
    @Accessor("grabbed")
    void chunkremover$setGrabbed(int grabbed);

    @Accessor("prevMouseX")
    void chunkremover$setPrevMouseX(int prevMouseX);

    @Accessor("prevMouseY")
    void chunkremover$setPrevMouseY(int prevMouseY);
}

package com.sachamarino.chunkremover.client;

import com.sachamarino.chunkremover.ChunkRemover;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client entry point. The FTB Chunks map integration itself lives in the mixins under
 * {@code com.sachamarino.chunkremover.mixin.client}.
 */
@Mod(value = ChunkRemover.MOD_ID, dist = Dist.CLIENT)
public class ChunkRemoverClient {
    public ChunkRemoverClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}

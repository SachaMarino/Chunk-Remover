package com.sachamarino.chunkremover;

import com.mojang.logging.LogUtils;
import com.sachamarino.chunkremover.server.ChunkRemoverCommands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Chunk Remover: select an area on the FTB Chunks map and delete every generated chunk inside it,
 * so the world regenerates them on the next visit and the save file shrinks.
 */
@Mod(ChunkRemover.MOD_ID)
public class ChunkRemover {
    public static final String MOD_ID = "chunkremover";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ChunkRemover(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, ChunkRemoverConfig.SPEC);

        // Network payloads are registered by ChunkRemoverNetwork (mod bus, @EventBusSubscriber).
        NeoForge.EVENT_BUS.addListener(ChunkRemoverCommands::register);

        // Development self test (class excluded from the released jar, never touched unless the property is set).
        if (Boolean.getBoolean("chunkremover.selftest")) {
            com.sachamarino.chunkremover.devtest.SelfTest.register();
        }
    }
}

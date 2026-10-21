package com.sachamarino.chunkremover.devtest;

import com.sachamarino.chunkremover.ChunkRemover;
import com.sachamarino.chunkremover.client.ChunkRemoverMapScreen;
import com.sachamarino.chunkremover.mixin.client.LargeMapScreenAccessor;
import com.sachamarino.chunkremover.mixin.client.MapRegionAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client smoke test: loads the FTB Chunks classes targeted by the mixins so that Mixin applies them, and checks
 * that the injected interfaces are present. Then closes the game.
 */
final class SelfTestClient {
    private static boolean done;

    private SelfTestClient() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(SelfTestClient::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (done || !(mc.screen instanceof TitleScreen)) {
            return;
        }
        done = true;

        int failures = 0;
        failures += check("LargeMapScreen implements ChunkRemoverMapScreen", () ->
                ChunkRemoverMapScreen.class.isAssignableFrom(Class.forName("dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen")));
        failures += check("LargeMapScreen implements LargeMapScreenAccessor", () ->
                LargeMapScreenAccessor.class.isAssignableFrom(Class.forName("dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen")));
        failures += check("MapRegion implements MapRegionAccessor", () ->
                MapRegionAccessor.class.isAssignableFrom(Class.forName("dev.ftb.mods.ftbchunks.client.map.MapRegion")));
        failures += check("RegionMapPanel loads with mixin", () -> {
            Class<?> type = Class.forName("dev.ftb.mods.ftbchunks.client.gui.RegionMapPanel");
            return type.getDeclaredMethod("chunkremover$screenX", int.class, int.class) != null;
        });
        failures += check("MapScreenButtonPanel loads with mixin", () -> {
            Class<?> type = Class.forName("dev.ftb.mods.ftbchunks.client.gui.MapScreenButtonPanel");
            return type.getDeclaredField("chunkremover$deleteButton") != null;
        });

        if (failures == 0) {
            ChunkRemover.LOGGER.info("[SelfTest] CLIENT SELFTEST PASSED");
        } else {
            ChunkRemover.LOGGER.error("[SelfTest] CLIENT SELFTEST FAILED: {} check(s) failed", failures);
        }
        mc.stop();
    }

    private interface Check {
        boolean run() throws Exception;
    }

    private static int check(String name, Check check) {
        try {
            if (check.run()) {
                ChunkRemover.LOGGER.info("[SelfTest] OK: {}", name);
                return 0;
            }
            ChunkRemover.LOGGER.error("[SelfTest] FAIL: {}", name);
        } catch (Throwable t) {
            ChunkRemover.LOGGER.error("[SelfTest] FAIL: {} threw", name, t);
        }
        return 1;
    }
}

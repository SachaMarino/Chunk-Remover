package com.sachamarino.chunkremover.devtest;

import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Development-only self test, excluded from the built jar (see build.gradle).
 * Enable with {@code -Dchunkremover.selftest=true}: the {@code runSelftest} and {@code runSelftestClient} Gradle tasks do it.
 */
public final class SelfTest {
    public static final String PROPERTY = "chunkremover.selftest";

    private SelfTest() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void register() {
        if (FMLEnvironment.dist.isClient()) {
            SelfTestClient.register();
        } else {
            SelfTestServer.register();
        }
    }
}

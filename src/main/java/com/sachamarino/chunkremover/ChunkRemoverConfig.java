package com.sachamarino.chunkremover;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ChunkRemoverConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue REQUIRED_PERMISSION_LEVEL = BUILDER
            .comment("Permission level required to delete chunks (2 = operator). The owner of a singleplayer world is always allowed.")
            .defineInRange("requiredPermissionLevel", 2, 0, 4);

    public static final ModConfigSpec.IntValue MAX_CHUNKS_PER_REQUEST = BUILDER
            .comment("Maximum number of chunks a single deletion request may cover. 262144 = a 512 x 512 chunk square (8192 x 8192 blocks).")
            .defineInRange("maxChunksPerRequest", 262_144, 1, 8_000_000);

    public static final ModConfigSpec.BooleanValue PROTECT_CLAIMED_CHUNKS = BUILDER
            .comment("Skip chunks claimed by any team in FTB Chunks (recommended: claimed chunks usually contain bases).")
            .define("protectClaimedChunks", true);

    public static final ModConfigSpec.BooleanValue LOG_DELETIONS = BUILDER
            .comment("Write a summary of every deletion to the server log.")
            .define("logDeletions", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ChunkRemoverConfig() {
    }
}

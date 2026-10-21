package com.sachamarino.chunkremover.common;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Outcome of a deletion request, as reported back to the requester.
 */
public enum DeletionStatus {
    OK,
    NO_PERMISSION,
    TOO_LARGE,
    UNKNOWN_DIMENSION,
    FAILED;

    private static final DeletionStatus[] VALUES = values();

    public static final StreamCodec<ByteBuf, DeletionStatus> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            ordinal -> ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : FAILED,
            DeletionStatus::ordinal
    );

    public String translationKey() {
        return "chunkremover.message.error." + name().toLowerCase(java.util.Locale.ROOT);
    }
}

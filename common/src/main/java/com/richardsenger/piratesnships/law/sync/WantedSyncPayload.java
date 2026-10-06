package com.richardsenger.piratesnships.law.sync;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.crime.WantedLevel;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: the receiving player's own wanted level and displayed criminal score (rounded up, see
 * {@code CriminalRecord.displayScore}). Sent on login and when either value changes, throttled by
 * {@code law.world.wanted_sync_interval_ticks}.
 */
public record WantedSyncPayload(WantedLevel level, int score) implements CustomPacketPayload {

    public static final Type<WantedSyncPayload> TYPE = new Type<>(Constants.id("wanted_sync"));

    private static final StreamCodec<ByteBuf, WantedLevel> LEVEL_CODEC = ByteBufCodecs.VAR_INT.map(
            i -> WantedLevel.values()[Math.clamp(i, 0, WantedLevel.values().length - 1)], WantedLevel::ordinal);

    public static final StreamCodec<ByteBuf, WantedSyncPayload> CODEC = StreamCodec.composite(
            LEVEL_CODEC, WantedSyncPayload::level,
            ByteBufCodecs.VAR_INT, WantedSyncPayload::score,
            WantedSyncPayload::new);

    public WantedSyncPayload {
        if (level == null) level = WantedLevel.CLEAN;
        score = Math.max(0, score);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.richardsenger.piratesnships.rpg.reputation;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: the receiving player's own shown reputation scores (−100..100). Sent on login and, throttled by
 * {@code reputation.sync_interval_ticks}, whenever a shown score changed ({@link ReputationSync}).
 */
public record ReputationSyncPayload(int navy, int pirates, int villagers) implements CustomPacketPayload {

    public static final Type<ReputationSyncPayload> TYPE = new Type<>(Constants.id("reputation_sync"));

    public static final StreamCodec<ByteBuf, ReputationSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ReputationSyncPayload::navy,
            ByteBufCodecs.VAR_INT, ReputationSyncPayload::pirates,
            ByteBufCodecs.VAR_INT, ReputationSyncPayload::villagers,
            ReputationSyncPayload::new);

    public static final ReputationSyncPayload NEUTRAL = new ReputationSyncPayload(0, 0, 0);

    public ReputationSyncPayload {
        navy = clamp(navy);
        pirates = clamp(pirates);
        villagers = clamp(villagers);
    }

    public int get(Faction faction) {
        return switch (faction) {
            case NAVY -> navy;
            case PIRATES -> pirates;
            case VILLAGERS -> villagers;
        };
    }

    private static int clamp(int v) {
        return Math.max((int) ReputationRules.MIN, Math.min((int) ReputationRules.MAX, v));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

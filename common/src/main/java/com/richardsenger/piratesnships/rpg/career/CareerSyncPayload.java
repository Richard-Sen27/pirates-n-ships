package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: the receiving player's own career (ranks, service, letter, prize money waiting), for the HUD and
 * titles later (HON1). Sent at login and whenever it changes ({@link CareerSync}).
 */
public record CareerSyncPayload(NavyRank navy, boolean enlisted, InfamyRank infamy, LetterState letter, long prizeMoney)
        implements CustomPacketPayload {

    public static final Type<CareerSyncPayload> TYPE = new Type<>(Constants.id("career_sync"));

    private static <E extends Enum<E>> StreamCodec<ByteBuf, E> enumCodec(E[] values) {
        return ByteBufCodecs.VAR_INT.map(i -> values[Math.max(0, Math.min(values.length - 1, i))], Enum::ordinal);
    }

    public static final StreamCodec<ByteBuf, CareerSyncPayload> CODEC = StreamCodec.composite(
            enumCodec(NavyRank.values()), CareerSyncPayload::navy,
            ByteBufCodecs.BOOL, CareerSyncPayload::enlisted,
            enumCodec(InfamyRank.values()), CareerSyncPayload::infamy,
            enumCodec(LetterState.values()), CareerSyncPayload::letter,
            ByteBufCodecs.VAR_LONG, CareerSyncPayload::prizeMoney,
            CareerSyncPayload::new);

    public static final CareerSyncPayload NONE = of(CareerRecord.EMPTY);

    public static CareerSyncPayload of(CareerRecord r) {
        return new CareerSyncPayload(r.navy(), r.enlisted(), r.infamy(), r.letter(), r.prizeMoney());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

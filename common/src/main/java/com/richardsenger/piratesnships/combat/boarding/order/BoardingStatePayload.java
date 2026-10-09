package com.richardsenger.piratesnships.combat.boarding.order;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.UUID;

/**
 * Server → client (BRD3): the ships on whose deck the whistle's boarding entry reads "Back aboard" for this player
 * (his party's own ship and the ship it boards); empty when none. Sent by {@link BoardingSync} when the set changes. The
 * label is a hint only: the server decides "Board!" or "Back aboard" by the party's state.
 */
public record BoardingStatePayload(List<UUID> ships) implements CustomPacketPayload {

    /** Most ships listed (two per party; a player rarely sends more than one). */
    public static final int MAX_SHIPS = 16;

    public static final Type<BoardingStatePayload> TYPE = new Type<>(Constants.id("boarding_state"));
    public static final StreamCodec<ByteBuf, BoardingStatePayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SHIPS)), BoardingStatePayload::ships,
            BoardingStatePayload::new);

    public BoardingStatePayload {
        ships = List.copyOf(ships.size() > MAX_SHIPS ? ships.subList(0, MAX_SHIPS) : ships);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

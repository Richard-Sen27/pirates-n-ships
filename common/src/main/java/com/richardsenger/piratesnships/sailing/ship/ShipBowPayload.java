package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Server → client (VIS1c): the bow of one ship, its {@link BowFrame} in the ship's plot frame, for the sail renderers
 * (the cloth reads the apparent wind off the real bow). Sent by {@link ShipBowSync} to every player Sable tracks the
 * ship for, once per player and bow. {@code bow} is 0 north, 1 east, 2 south, 3 west (plot directions); anything else
 * means unknown.
 */
public record ShipBowPayload(UUID ship, int bow) implements CustomPacketPayload {

    public static final Type<ShipBowPayload> TYPE = new Type<>(Constants.id("ship_bow"));
    public static final StreamCodec<ByteBuf, ShipBowPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ShipBowPayload::ship,
            ByteBufCodecs.VAR_INT, ShipBowPayload::bow,
            ShipBowPayload::new);

    public static ShipBowPayload of(UUID ship, BowFrame bow) {
        return new ShipBowPayload(ship, index(bow));
    }

    /** The bow, or null for an unknown index. */
    public @Nullable BowFrame bowFrame() {
        return switch (bow) {
            case 0 -> new BowFrame(0, -1);
            case 1 -> new BowFrame(1, 0);
            case 2 -> new BowFrame(0, 1);
            case 3 -> new BowFrame(-1, 0);
            default -> null;
        };
    }

    static int index(BowFrame b) {
        if (b.dx() > 0) {
            return 1;
        }
        if (b.dx() < 0) {
            return 3;
        }
        return b.dz() > 0 ? 2 : 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

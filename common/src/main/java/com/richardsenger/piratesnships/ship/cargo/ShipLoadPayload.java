package com.richardsenger.piratesnships.ship.cargo;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.trade.cargo.CargoWeight;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Server → client (CW1): the load level of the ship whose helm the player holds, for the helm overlay. Sent when a
 * steering session starts and whenever the level changes while it lasts. {@code level} is the
 * {@link CargoWeight.LoadLevel} ordinal, or -1 for none.
 */
public record ShipLoadPayload(int level) implements CustomPacketPayload {

    public static final Type<ShipLoadPayload> TYPE = new Type<>(Constants.id("ship_load"));
    public static final StreamCodec<ByteBuf, ShipLoadPayload> CODEC = ByteBufCodecs.VAR_INT.map(ShipLoadPayload::new, ShipLoadPayload::level);

    public static ShipLoadPayload of(@Nullable CargoWeight.LoadLevel level) {
        return new ShipLoadPayload(level == null ? -1 : level.ordinal());
    }

    /** The level, or null for none or an unknown ordinal. */
    public @Nullable CargoWeight.LoadLevel loadLevel() {
        CargoWeight.LoadLevel[] all = CargoWeight.LoadLevel.values();
        return level >= 0 && level < all.length ? all[level] : null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

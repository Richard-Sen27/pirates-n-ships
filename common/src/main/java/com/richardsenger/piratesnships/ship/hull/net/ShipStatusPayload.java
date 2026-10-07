package com.richardsenger.piratesnships.ship.hull.net;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.trade.cargo.CargoWeight;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Server → client: the status of the ship the player is aboard, for the ship HUD (docs/design.md §4.6, HUD1). Sent by
 * {@link ShipStatusSync} every {@code ships.ship_status_sync_interval_ticks} while something changed, and every fifth
 * interval regardless ({@link ShipStatusThrottle}), to players standing on or riding in the ship. The values are rounded
 * ({@link #quantize}) so that a ship at rest produces equal payloads.
 *
 * @param ship    the ship's id
 * @param name    the ship's name, empty when it has none
 * @param heading compass bearing of the bow [degrees, 0 = north, 90 = east]
 * @param speed   horizontal speed through the water [blocks/s]
 * @param rudder  rudder angle [degrees, positive = starboard], {@code NaN} when the ship has no helm
 * @param load    the ship's cargo load level ({@code CargoWeight.LoadLevel} ordinal, CW1's last weighing), -1 when unknown
 * @param cells   the compartments, bow first, at most {@link CompartmentStrip#MAX_CELLS}
 */
public record ShipStatusPayload(UUID ship, String name, float heading, float speed, float rudder, int load, List<Cell> cells)
        implements CustomPacketPayload {

    public static final Type<ShipStatusPayload> TYPE = new Type<>(Constants.id("ship_status"));

    /** Longest name sent (anvil names are shorter). */
    public static final int MAX_NAME = 64;

    /**
     * One cell of the hull strip: a compartment, or several small ones merged ({@link CompartmentStrip}).
     *
     * @param id       compartment id of the analysis (the largest part's for a merged cell); only stable until the next re-analysis
     * @param volume   volume in blocks
     * @param water    water in blocks, 0..volume
     * @param breaches open breaches into this compartment
     * @param pumping  a pump is draining it right now
     */
    public record Cell(int id, int volume, float water, int breaches, boolean pumping) {

        public static final StreamCodec<ByteBuf, Cell> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Cell::id,
                ByteBufCodecs.VAR_INT, Cell::volume,
                ByteBufCodecs.FLOAT, Cell::water,
                ByteBufCodecs.VAR_INT, Cell::breaches,
                ByteBufCodecs.BOOL, Cell::pumping,
                Cell::new);

        /** Water as a fraction of the volume, 0..1. */
        public float fraction() {
            return volume <= 0 ? 0f : Math.max(0f, Math.min(1f, water / volume));
        }
    }

    private static final StreamCodec<ByteBuf, List<Cell>> CELLS = Cell.CODEC.apply(ByteBufCodecs.list(CompartmentStrip.MAX_CELLS));

    /** Seven fields: more than {@code StreamCodec.composite} takes in 1.21.1, so written out. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ShipStatusPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, p.ship());
                ByteBufCodecs.stringUtf8(MAX_NAME).encode(buf, p.name());
                buf.writeFloat(p.heading());
                buf.writeFloat(p.speed());
                buf.writeFloat(p.rudder());
                ByteBufCodecs.VAR_INT.encode(buf, p.load());
                CELLS.encode(buf, p.cells());
            },
            buf -> new ShipStatusPayload(UUIDUtil.STREAM_CODEC.decode(buf), ByteBufCodecs.stringUtf8(MAX_NAME).decode(buf),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), ByteBufCodecs.VAR_INT.decode(buf), CELLS.decode(buf)));

    public ShipStatusPayload {
        name = name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
        cells = List.copyOf(cells.size() > CompartmentStrip.MAX_CELLS ? cells.subList(0, CompartmentStrip.MAX_CELLS) : cells);
    }

    /** The cargo load level, or null when unknown. */
    public @Nullable CargoWeight.LoadLevel loadLevel() {
        CargoWeight.LoadLevel[] all = CargoWeight.LoadLevel.values();
        return load >= 0 && load < all.length ? all[load] : null;
    }

    /** Whether the ship has a helm (and so a rudder angle to show). */
    public boolean hasRudder() {
        return !Float.isNaN(rudder);
    }

    /**
     * The payload with its values rounded: heading and rudder to whole degrees, speed and water to 0.05 (blocks/s,
     * blocks). Equal rounded payloads count as "unchanged" for the throttle.
     */
    public ShipStatusPayload quantize() {
        List<Cell> q = cells.stream()
                .map(c -> new Cell(c.id(), c.volume(), round(Math.max(0f, Math.min(c.volume(), c.water()))),
                        c.breaches(), c.pumping()))
                .toList();
        int h = Math.floorMod(Math.round(heading), 360);
        return new ShipStatusPayload(ship, name, h, round(Math.max(0f, speed)),
                Float.isNaN(rudder) ? Float.NaN : Math.round(rudder), load, q);
    }

    /** Rounds to a multiple of 0.05 (never −0). */
    private static float round(float v) {
        return Math.round(v * 20f) / 20f;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

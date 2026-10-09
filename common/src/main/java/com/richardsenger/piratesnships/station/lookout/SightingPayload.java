package com.richardsenger.piratesnships.station.lookout;

import com.richardsenger.piratesnships.Constants;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: one lookout's call (LKO2, docs/design.md §6 "The lookout's call"). Sent by {@link Lookouts} to the
 * players aboard the ship and in its nests for every sighting the repeat rule lets through ({@link SightingMemory});
 * the client shows a short action-bar line ({@link SightingCue}), plays the hail from the nest and marks the bearing
 * on the ship HUD's compass rose for {@code freshTicks}.
 *
 * @param ship       the lookout's ship
 * @param what       what was seen, by the kind the scan knows and, for a ship, the flag it flies
 * @param name       a ship the lookout knows by sight (TPL2), else empty
 * @param relative   bearing relative to the bow [degrees, (−180, 180], positive to starboard]
 * @param compass    compass bearing from the nest [degrees, 0 = north, 90 = east], for the mark on the north-up rose
 * @param distance   the called distance [blocks]
 * @param nestX      world position of the nest that called (the hail is played there), with {@code nestY}, {@code nestZ}
 * @param key        the sighting's identity (a hash of its memory key): a new call of it replaces its mark
 * @param freshTicks how long the mark stays on the rose ({@code lookout.mark_seconds})
 */
public record SightingPayload(UUID ship, What what, String name, float relative, float compass, int distance, double nestX,
                              double nestY, double nestZ, int key, int freshTicks) implements CustomPacketPayload {

    public static final Type<SightingPayload> TYPE = new Type<>(Constants.id("lookout_sighting"));

    /** Longest name sent. */
    public static final int MAX_NAME = 64;

    /** What the lookout saw: the kind the scan knows, a ship told apart by its flag (or as a wreck). */
    public enum What {
        SAIL, MERCHANT, NAVY, PIRATE, WRECK, LAND, SHARK, KRAKEN;

        public boolean ship() {
            return ordinal() <= WRECK.ordinal();
        }

        /** The mark's tint (ARGB): white for land, red for a pirate ship, blue for the navy, grey otherwise. */
        public int tint() {
            return switch (this) {
                case LAND -> 0xFFFFFFFF;
                case PIRATE -> 0xFFE03C32;
                case NAVY -> 0xFF4A86F0;
                default -> 0xFFA0A0A0;
            };
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SightingPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, p.ship());
                ByteBufCodecs.VAR_INT.encode(buf, p.what().ordinal());
                ByteBufCodecs.stringUtf8(MAX_NAME).encode(buf, p.name());
                buf.writeFloat(p.relative());
                buf.writeFloat(p.compass());
                ByteBufCodecs.VAR_INT.encode(buf, p.distance());
                buf.writeDouble(p.nestX());
                buf.writeDouble(p.nestY());
                buf.writeDouble(p.nestZ());
                buf.writeInt(p.key());
                ByteBufCodecs.VAR_INT.encode(buf, p.freshTicks());
            },
            buf -> {
                UUID ship = UUIDUtil.STREAM_CODEC.decode(buf);
                What[] all = What.values();
                What what = all[Math.floorMod(ByteBufCodecs.VAR_INT.decode(buf), all.length)];
                String name = ByteBufCodecs.stringUtf8(MAX_NAME).decode(buf);
                float relative = buf.readFloat();
                float compass = buf.readFloat();
                int distance = ByteBufCodecs.VAR_INT.decode(buf);
                double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
                int key = buf.readInt();
                return new SightingPayload(ship, what, name, relative, compass, distance, x, y, z, key, ByteBufCodecs.VAR_INT.decode(buf));
            });

    public SightingPayload {
        name = name == null ? "" : name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
    }

    /** The relative bearing in points, as the cue names it. */
    public Bearings.Relative points() {
        return Bearings.points(relative);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

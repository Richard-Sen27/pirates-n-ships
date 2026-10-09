package com.richardsenger.piratesnships.crew.walk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringRepresentable;

/**
 * Where a crew member walks (WALK1) and why: the ship (Sable sub-level UUID), the plot cell it walks to
 * ({@code spot}), the plot position of what it walks to ({@code anchor}: the station block, the pantry or water barrel,
 * the hammock's foot) and the game time the walk started. Saved on the crew member.
 */
public record WalkTarget(Purpose purpose, UUID ship, BlockPos spot, BlockPos anchor, long started) {

    /** What the crew member does on arrival. */
    public enum Purpose implements StringRepresentable {
        /** Takes the seat of its assigned station ({@code crew.npc.CrewStations}). */
        STATION,
        /** Sits down at a meal beside the provisions block ({@code crew.galley.MealVisits}). */
        MEAL,
        /** Lies down in its hammock for the night ({@code crew.hammock.CrewRest}). */
        HAMMOCK;

        public static final Codec<Purpose> CODEC = StringRepresentable.fromEnum(Purpose::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<WalkTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
            Purpose.CODEC.fieldOf("purpose").forGetter(WalkTarget::purpose),
            UUIDUtil.CODEC.fieldOf("ship").forGetter(WalkTarget::ship),
            BlockPos.CODEC.fieldOf("spot").forGetter(WalkTarget::spot),
            BlockPos.CODEC.fieldOf("anchor").forGetter(WalkTarget::anchor),
            Codec.LONG.fieldOf("started").forGetter(WalkTarget::started)
    ).apply(i, WalkTarget::new));

    public WalkTarget {
        spot = spot.immutable();
        anchor = anchor.immutable();
    }

    /** Whether this walk goes to {@code anchor} of ship {@code ship} for {@code purpose}. */
    public boolean goesTo(Purpose purpose, UUID ship, BlockPos anchor) {
        return this.purpose == purpose && this.ship.equals(ship) && this.anchor.equals(anchor);
    }
}

package com.richardsenger.piratesnships.station.winch;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.station.order.CrewOrder;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * Sail orders a crew member at the sail winch carries out (docs/design.md §7.2: hoist / reef / furl). Pure mapping
 * from order to target trim and to the work time. The {@link CrewOrder} of the sail winch station.
 */
public enum SailOrder implements StringRepresentable, CrewOrder {
    HOIST(SailTrim.FULL),
    REEF(SailTrim.HALF),
    FURL(SailTrim.FURLED);

    public static final Codec<SailOrder> CODEC = StringRepresentable.fromEnum(SailOrder::values);

    private final SailTrim target;

    SailOrder(SailTrim target) {
        this.target = target;
    }

    public SailTrim target() {
        return target;
    }

    /** The whistle cycle: hoist → reef → furl → hoist. */
    public SailOrder next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Trim steps between the current trim and the target (furled → full is two steps). */
    public int steps(SailTrim current) {
        return Math.abs(target.ordinal() - current.ordinal());
    }

    /** Work time in ticks: {@code ticksPerStep} per trim step, 0 when the sails already have the target trim. */
    public int durationTicks(SailTrim current, int ticksPerStep) {
        return steps(current) * Math.max(0, ticksPerStep);
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String id() {
        return getSerializedName();
    }

    /** Translation key of the order name ("hoist the sails"). */
    @Override
    public String nameKey() {
        return "sail_order." + Constants.MOD_ID + "." + getSerializedName();
    }

    /** Translation key of the crew's acknowledgement ("Aye, hoisting the sails!"). */
    @Override
    public String ackKey() {
        return "message." + Constants.MOD_ID + ".crew.ack." + getSerializedName();
    }

    /** "The sails are already set, captain (%s)": the same key for every sail order. */
    @Override
    public String nothingToDoKey() {
        return "message." + Constants.MOD_ID + ".crew.nothing_to_do";
    }

    /** "This ship has no sails, captain!" */
    @Override
    public String unableKey() {
        return "message." + Constants.MOD_ID + ".crew.no_sails";
    }
}

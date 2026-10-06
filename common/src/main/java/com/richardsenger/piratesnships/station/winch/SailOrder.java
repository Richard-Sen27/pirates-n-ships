package com.richardsenger.piratesnships.station.winch;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * Sail orders a crew member at the sail winch carries out (docs/design.md §7.2: hoist / reef / furl). Pure mapping
 * from order to target trim and to the work time.
 */
public enum SailOrder implements StringRepresentable {
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

    /** Translation key of the order name ("hoist the sails"). */
    public String nameKey() {
        return "sail_order." + Constants.MOD_ID + "." + getSerializedName();
    }

    /** Translation key of the crew's acknowledgement ("Aye, hoisting the sails!"). */
    public String ackKey() {
        return "message." + Constants.MOD_ID + ".crew.ack." + getSerializedName();
    }
}

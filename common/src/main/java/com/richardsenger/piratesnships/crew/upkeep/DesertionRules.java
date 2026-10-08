package com.richardsenger.piratesnships.crew.upkeep;

import com.richardsenger.piratesnships.world.port.Berth;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * When a deserter leaves the ship (CRW2, docs/design.md §7.3, §7.5). Pure.
 * <ul>
 *   <li>At a dawn {@link UpkeepDay} decides that a member deserts. With {@code crew.desertion.at_port_only} it is then
 *       only <em>marked</em> as deserting ({@link #afterDawn}); without it, it leaves at once, as before CRW2.</li>
 *   <li>Every later dawn a deserting member counts one more day; a dawn that finds its morale fine again (its low-day
 *       counter back at 0) takes the mark off: it changed its mind.</li>
 *   <li>The level tick ({@code Desertions}) lets a marked member go ({@link #decide}) when its ship is at a port (the
 *       port's box inflated by {@code desert_port_radius} touches the ship's bounds, {@link #atPort}), or anywhere once
 *       it has waited {@code desert_anywhere_after_days} dawns.</li>
 * </ul>
 */
public final class DesertionRules {

    private DesertionRules() {
    }

    /**
     * The {@code crew.desertion} keys of CRW2.
     *
     * @param atPortOnly        deserters wait for a port (false: they leave at the dawn they desert)
     * @param portRadius        blocks around a port's box within which a ship is at that port
     * @param anywhereAfterDays dawns a deserter waits for a port before it leaves wherever the ship is
     */
    public record Settings(boolean atPortOnly, double portRadius, int anywhereAfterDays) {
        public static final Settings DEFAULTS = new Settings(true, 48, 3);
    }

    /** What a deserting member does now. */
    public enum Leave {
        /** Stays aboard, still deserting. */
        STAY,
        /** Walks off onto the quay of the port the ship is at. */
        AT_PORT,
        /** Leaves where it stands: it waited long enough, or deserters do not wait for a port. */
        ANYWHERE
    }

    /** A member's deserting state after a dawn: marked or not, and the dawns it has been marked. */
    public record Mark(boolean deserting, int days) {
        public static final Mark NONE = new Mark(false, 0);
    }

    /**
     * The deserting mark after a dawn. {@code before}: the mark until now; {@code deserts}: {@link UpkeepDay} says it
     * deserts at this dawn; {@code lowDays}: its low-morale counter after this dawn (0 when its morale is fine).
     */
    public static Mark afterDawn(Mark before, boolean deserts, int lowDays) {
        if (before.deserting()) {
            return lowDays <= 0 ? Mark.NONE : new Mark(true, before.days() + 1);
        }
        return deserts ? new Mark(true, 0) : Mark.NONE;
    }

    /** Whether a deserter leaves at once at the dawn it deserts, without being marked. */
    public static boolean leavesAtOnce(Settings s) {
        return !s.atPortOnly();
    }

    /** What a member marked for {@code days} dawns does now, its ship at a port or not. */
    public static Leave decide(Settings s, boolean atPort, int days) {
        if (!s.atPortOnly() || days >= s.anywhereAfterDays()) {
            return atPort ? Leave.AT_PORT : Leave.ANYWHERE;
        }
        return atPort ? Leave.AT_PORT : Leave.STAY;
    }

    /** Whether a ship with world bounds {@code ship} is at the port with box {@code portBox}, {@code radius} around it. */
    public static boolean atPort(BoundingBox portBox, double radius, AABB ship) {
        return AABB.of(portBox).inflate(Math.max(0, radius)).intersects(ship);
    }

    /** The berth nearest {@code from} (horizontal distance; ties keep list order). */
    public static Optional<Berth> nearestBerth(List<Berth> berths, Vec3 from) {
        Berth best = null;
        double bestD = Double.MAX_VALUE;
        for (Berth b : berths) {
            double dx = b.pos().getX() + 0.5 - from.x, dz = b.pos().getZ() + 0.5 - from.z;
            double d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }
        return Optional.ofNullable(best);
    }
}

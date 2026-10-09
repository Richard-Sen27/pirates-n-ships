package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pure rules of the boarding nets (RAIL5, docs/design.md §4.8 "Railing extras"): a ratline net strung between two
 * chainplates that stand on top of railing blocks along one rail edge hangs as a vertical net in the block row above
 * the rail. Here: whether two such chainplates make a boarding net ({@link #layout}), who passes it freely
 * ({@link #passes}), how long it holds a boarder who comes over the rail from outboard ({@link #contact}) and where it
 * holds it ({@link #pushOut}, {@link #withoutInboard}), the cutlass hits that cut it ({@link #hit}) and the mesh the
 * renderer draws ({@link #mesh}). Block coordinates in one frame (the world, or one ship's plot).
 */
public final class BoardingNetRules {

    /** The outcome of {@link #layout}. */
    public enum Verdict {
        OK,
        /** Not in one straight row along a railed edge both railings share (or not at one height). */
        NOT_ALONG,
        /** Next to each other: no block between them to hang the net in. */
        TOO_SHORT,
        /** Further apart than {@code rigging.boarding_net_length}. */
        TOO_LONG
    }

    /** A boarding net's layout: the verdict, the outboard side (the shared railed edge) and the length in blocks. */
    public record Layout(Verdict verdict, @Nullable Direction outboard, int length) {
    }

    /** A boarder's hold by one net side's rule: its last contact, the ticks held so far and until when it may pass. */
    public record Hold(long lastContact, int held, long freeUntil) {
        static final Hold NONE = new Hold(Long.MIN_VALUE / 4, 0, Long.MIN_VALUE / 4);
    }

    /** One contact: the hold after it and whether the net holds the entity this tick. */
    public record Step(Hold next, boolean holds) {
    }

    /** A net's cutlass hits: the last hit's time and how many in a row. */
    public record Cuts(long last, int hits) {
    }

    /**
     * Movement multiplier while held: half a cobweb's strength, halfway between free movement (1) and vanilla's cobweb
     * ({@code WebBlock#entityInside}: 0.25 across, 0.05 up and down).
     */
    public static final double HELD_ACROSS = (1.0 + 0.25) / 2.0;
    public static final double HELD_VERTICAL = (1.0 + 0.05) / 2.0;
    /** The thin wall of a boarding net: this far inside the net cell's outboard face [blocks] (2 px). */
    public static final double WALL = 2.0 / 16.0;
    /** Ticks without contact after which a boarder's next contact is a new attempt (held again from the start). */
    public static final int RESET_TICKS = 10;
    /** Ticks a boarder held to the end may keep crossing freely, renewed while it stays in contact. */
    public static final int FREE_TICKS = 40;
    /** Ticks between two cutlass hits after which the count starts again. */
    public static final int CUT_RESET_TICKS = 100;
    /** Height of the net's foot rope over the rail top, and of the top rope at the chainplates [blocks]. */
    public static final double FOOT = 0.06;
    public static final double TOP = 1.0;
    /** How far the top rope sags in the middle of the net [blocks]. */
    public static final double SAG = 0.18;

    private BoardingNetRules() {
    }

    /**
     * Whether chainplates on top of the railing blocks at {@code a} and {@code b} (the railings' own positions, railed
     * on {@code edgesA} and {@code edgesB}) make a boarding net: one height, one straight row along the edge (a row along
     * x needs a north or south edge, along z an east or west one) that both railings have railed, at least two and at most
     * {@code maxLength} blocks apart. The shared edge is the outboard side (north before south, east before west).
     */
    public static Layout layout(BlockPos a, Set<Direction> edgesA, BlockPos b, Set<Direction> edgesB, int maxLength) {
        if (a.getY() != b.getY() || (a.getX() != b.getX() && a.getZ() != b.getZ())) {
            return new Layout(Verdict.NOT_ALONG, null, 0);
        }
        boolean alongX = a.getZ() == b.getZ();
        int length = alongX ? Math.abs(a.getX() - b.getX()) : Math.abs(a.getZ() - b.getZ());
        Direction outboard = null;
        for (Direction d : alongX ? new Direction[] {Direction.NORTH, Direction.SOUTH} : new Direction[] {Direction.EAST, Direction.WEST}) {
            if (edgesA.contains(d) && edgesB.contains(d)) {
                outboard = d;
                break;
            }
        }
        if (outboard == null) {
            return new Layout(Verdict.NOT_ALONG, null, length);
        }
        if (length < 2) {
            return new Layout(Verdict.TOO_SHORT, outboard, length);
        }
        if (length > maxLength) {
            return new Layout(Verdict.TOO_LONG, outboard, length);
        }
        return new Layout(Verdict.OK, outboard, length);
    }

    /**
     * Whether an entity crosses a boarding net freely: a crew member ({@code isCrew}) of the net's ship
     * {@code netShip}, i.e. belonging to it now ({@code crewShip}: its station's, hammock's or deck's ship) or hired by
     * the ship's owner ({@code hiredBy} = {@code owner}: a boarder coming back aboard). Nobody passes a net ashore.
     */
    public static boolean passes(boolean isCrew, @Nullable UUID netShip, @Nullable UUID crewShip, Optional<UUID> hiredBy,
                                 Optional<UUID> owner) {
        if (!isCrew || netShip == null) {
            return false;
        }
        if (netShip.equals(crewShip)) {
            return true;
        }
        return hiredBy.isPresent() && hiredBy.equals(owner);
    }

    /**
     * A contact of an entity with a boarding net at game time {@code now}: {@code fromOutboard} when its centre is on
     * the outboard side of the net's line. An attempt starts only from outboard; the net then holds it for
     * {@code holdTicks} ticks of contact, after which it may cross for {@link #FREE_TICKS} (renewed while it stays in
     * contact). Several contacts in one tick (two cells) count once. A pause of more than {@link #RESET_TICKS} starts a
     * new attempt.
     */
    public static Step contact(@Nullable Hold hold, long now, boolean fromOutboard, int holdTicks) {
        Hold h = hold == null ? Hold.NONE : hold;
        if (now <= h.freeUntil()) {
            return new Step(new Hold(now, h.held(), Math.max(h.freeUntil(), now + FREE_TICKS)), false);
        }
        boolean fresh = now - h.lastContact() > RESET_TICKS;
        if (!fresh && now == h.lastContact()) {
            return new Step(h, h.held() > 0 && h.held() <= holdTicks);
        }
        int held = fresh ? 0 : h.held();
        if (held == 0 && !fromOutboard) {
            return new Step(new Hold(now, 0, h.freeUntil()), false);
        }
        held++;
        if (held > holdTicks) {
            return new Step(new Hold(now, held, now + FREE_TICKS), false);
        }
        return new Step(new Hold(now, held, h.freeUntil()), true);
    }

    /**
     * How far to move a held entity outboard so it stays behind the net's thin wall: {@code s} is its centre's distance
     * outboard of the net cell's centre (along the outboard side), {@code halfWidth} half its width; zero when it is
     * clear of the wall.
     */
    public static double pushOut(double s, double halfWidth) {
        return Math.max(0.0, 0.5 - WALL + halfWidth - s);
    }

    /** The horizontal velocity {@code (vx, vz)} without its part towards inboard (against the outboard unit {@code (ox, oz)}). */
    public static double[] withoutInboard(double vx, double vz, double ox, double oz) {
        double dot = vx * ox + vz * oz;
        if (dot >= 0) {
            return new double[] {vx, vz};
        }
        return new double[] {vx - dot * ox, vz - dot * oz};
    }

    /** A cutlass hit at {@code now} on a net with the hits {@code cuts} so far (a long pause starts again at one). */
    public static Cuts hit(@Nullable Cuts cuts, long now) {
        if (cuts == null || now - cuts.last() > CUT_RESET_TICKS) {
            return new Cuts(now, 1);
        }
        return new Cuts(now, cuts.hits() + 1);
    }

    /**
     * The rope segments of a boarding net between rings {@code a} and {@code b} (on top of the rails, same height): a
     * foot rope {@link #FOOT} over the rail top (the chainplates' block bottom), a top rope from {@link #TOP} at the ends
     * sagging {@link #SAG} in the middle, two ropes between them at a third and two thirds, and an upright every
     * {@code spacing} blocks (both ends included). Each segment is a pair of points in the rings' frame.
     */
    public static List<Point[]> mesh(Point a, Point b, double spacing) {
        double base = Math.floor(a.y());
        double dx = b.x() - a.x(), dz = b.z() - a.z();
        double len = Math.sqrt(dx * dx + dz * dz);
        List<Point[]> out = new ArrayList<>();
        if (len < 1.0e-6) {
            return out;
        }
        int segments = Math.max(2, (int) Math.ceil(len / 0.5));
        double[] levels = {0.0, 1.0 / 3.0, 2.0 / 3.0, 1.0};
        for (double level : levels) {
            for (int k = 0; k < segments; k++) {
                double t0 = (double) k / segments, t1 = (double) (k + 1) / segments;
                out.add(new Point[] {at(a, dx, dz, base, t0, level), at(a, dx, dz, base, t1, level)});
            }
        }
        int uprights = Math.max(1, (int) Math.round(len / spacing));
        for (int k = 0; k <= uprights; k++) {
            double t = (double) k / uprights;
            out.add(new Point[] {at(a, dx, dz, base, t, 0.0), at(a, dx, dz, base, t, 1.0)});
        }
        return out;
    }

    /** The point at fraction {@code t} along the net and {@code level} (0 foot, 1 top rope) up it. */
    static Point at(Point a, double dx, double dz, double base, double t, double level) {
        double foot = base + FOOT;
        double top = base + TOP - SAG * 4.0 * t * (1.0 - t);
        return new Point(a.x() + dx * t, foot + (top - foot) * level, a.z() + dz * t);
    }

    /** The height of the top rope at fraction {@code t} along the net, over the chainplates' block bottom. */
    public static double topAt(double t) {
        return TOP - SAG * 4.0 * t * (1.0 - t);
    }
}

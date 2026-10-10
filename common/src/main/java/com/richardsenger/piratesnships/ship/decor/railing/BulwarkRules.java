package com.richardsenger.piratesnships.ship.decor.railing;

import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The pure rules of the bulwark and the bulwark gun port (RAIL4, docs/design.md §4.8 "Railing extras (RAIL3–RAIL5)"),
 * without world access, in pixels of the block and in the north frame (the outboard edge is z 0) like
 * {@link RailingRules}, whose edge, placement and post rules the bulwark reuses unchanged: a bulwark is a railing whose
 * edges are solid plank walls and whose posts are stanchions.
 *
 * <p><b>Bulwark.</b> Per railed edge a plank wall {@link #WALL_DEPTH} deep from the deck to {@link #CAP_BOTTOM} and a cap
 * rail on top of it from there to {@link #WALL_TOP}, {@link #CAP_DEPTH} deep in the outline (RAIL6: the railing's cap
 * rail, drawn 4 px wide over the wall's line).
 * Per post (the {@link RailingRules#posts} rule) a {@link #STANCHION} px square stanchion up to {@link #STANCHION_TOP}.
 * A run continues only into another bulwark or a bulwark port on the same edge ({@link #continues}); against a railing,
 * a railing port or a gate it ends in a stanchion, and a railing ends in a post against it.
 *
 * <p><b>Gun port.</b> The bulwark with a notch {@link #OPENING_LEFT}..{@link #OPENING_RIGHT} (10 px) wide from the sill
 * ({@link #SILL_TOP}, 4 px) up through the cap: a carriage cannon's barrel crosses the wall 12 px (−5°) to 22 px (20°)
 * above the deck, so a port with a lintel at the cap would sit across the barrel at every elevation but the lowest. A
 * lid hinged at the notch's top closes it; it stands open while a carriage cannon stands inboard facing the port, or
 * while a player has propped it open ({@link #lidOpen}).
 */
public final class BulwarkRules {

    /** Depth of the plank wall from the block's side, in pixels (as a railing's rail). */
    public static final double WALL_DEPTH = RailingRules.RAIL_DEPTH;
    /** Bottom of the cap rail (the top of the plank wall), in pixels. */
    public static final double CAP_BOTTOM = 14.0;
    /** Top of the wall with its cap rail, in pixels. */
    public static final double WALL_TOP = 16.0;
    /**
     * Depth of the cap rail in the outline, in pixels: RAIL6's cap (z -1..3, the railing's) is 1 px proud of the wall's
     * inboard face; its outboard overhang past the block's face is drawn only.
     */
    public static final double CAP_DEPTH = 3.0;
    /** Side of a stanchion, in pixels. */
    public static final double STANCHION = 4.0;
    /** Top of a stanchion (its head stands 2 px above the cap rail), in pixels. */
    public static final double STANCHION_TOP = 18.0;
    /** Top of the gun port's sill, in pixels (the railing port's). */
    public static final double SILL_TOP = RailingRules.SILL_TOP;
    /** The gun port's notch, from its left (west, looking outboard north) jamb to its right one, in pixels. */
    public static final double OPENING_LEFT = 3.0;
    public static final double OPENING_RIGHT = 13.0;
    /**
     * Width of a gun port's jambs in its open collision, in pixels: 2 px like the railing port's posts, so a player
     * (9.6 px wide, widened 0.8 px on each side by Sable's step-up test) walks out over the sill onto a gangway.
     */
    public static final double COLLISION_JAMB = RailingRules.PORT_COLLISION_POST;

    private BulwarkRules() {
    }

    // ------------------------------------------------------------------ the run

    /** What stands next to a bulwark or a railing, as far as the run's posts are concerned. */
    public enum Kind {
        /** A railing, a railing port or a railing gate. */
        RAILING,
        /** A bulwark or a bulwark gun port. */
        BULWARK,
        /** RAIL6: a rope railing (no ports or gates of its own). */
        ROPE,
        /** Anything else. */
        NONE
    }

    /**
     * Whether a run of {@code self}'s kind continues into a neighbour of kind {@code neighbour} that carries the edge:
     * railings into railings (ports, gates), bulwarks into bulwarks (gun ports), rope railings into rope railings. Two
     * kinds that meet end in a post each, since a rail cannot run on into a wall or a rope line into a cap rail.
     */
    public static boolean continues(Kind self, Kind neighbour, boolean carriesEdge) {
        return carriesEdge && self != Kind.NONE && self == neighbour;
    }

    // ------------------------------------------------------------------ the lid

    /**
     * Whether the gun port's lid stands open: always while a carriage cannon stands in the block inboard of the port
     * with its muzzle toward it ({@code cannonInboard}), otherwise as a player left it ({@code propped}).
     */
    public static boolean lidOpen(boolean cannonInboard, boolean propped) {
        return cannonInboard || propped;
    }

    /**
     * Whether a carriage cannon facing {@code cannonFacing} in the block inboard of a port facing {@code portFacing}
     * points through the port: the same direction (a cannon block next to the port facing it is always its master).
     */
    public static boolean cannonFacesPort(Direction portFacing, Direction cannonFacing) {
        return portFacing == cannonFacing;
    }

    // ------------------------------------------------------------------ shapes (pixels, {x0, y0, z0, x1, y1, z1})

    /**
     * The boxes of a bulwark: per edge its wall ({@link #WALL_DEPTH} deep, full width) up to {@code wallTop}, with the
     * cap rail ({@link #CAP_DEPTH} deep, from {@link #CAP_BOTTOM} to {@link #WALL_TOP}) when {@code cap}; per post a
     * stanchion up to {@code postTop}. The outline uses the model's heights with the cap; the collision the wall and
     * the stanchions raised to the configured collision height, without the cap.
     */
    public static List<double[]> boxes(Set<Direction> edges, Set<Direction> posts, double wallTop, double postTop, boolean cap) {
        List<double[]> out = new ArrayList<>();
        for (Direction d : edges) {
            out.add(RailingRules.rotate(new double[]{0, 0, 0, 16, wallTop, WALL_DEPTH}, d));
            if (cap) out.add(RailingRules.rotate(new double[]{0, CAP_BOTTOM, 0, 16, WALL_TOP, CAP_DEPTH}, d));
        }
        for (Direction d : posts) out.add(RailingRules.rotate(new double[]{0, 0, 0, STANCHION, postTop, STANCHION}, d));
        return out;
    }

    /**
     * The boxes of a gun port facing {@code facing}. Outline ({@code collision} false): the two jambs with their cap
     * rail ends, the sill, and while the lid is closed the lid in the notch. Collision: the jambs
     * {@link #COLLISION_JAMB} wide in the outer corners and the sill, raised to {@code top} while open; closed, the
     * whole wall up to {@code top} (the lid holds like the bulwark beside it).
     */
    public static List<double[]> portBoxes(Direction facing, boolean open, boolean collision, double top) {
        List<double[]> out = new ArrayList<>();
        if (collision) {
            if (!open) {
                out.add(RailingRules.rotate(new double[]{0, 0, 0, 16, top, WALL_DEPTH}, facing));
                return out;
            }
            out.add(RailingRules.rotate(new double[]{0, 0, 0, COLLISION_JAMB, top, WALL_DEPTH}, facing));
            out.add(RailingRules.rotate(new double[]{16 - COLLISION_JAMB, 0, 0, 16, top, WALL_DEPTH}, facing));
            out.add(RailingRules.rotate(new double[]{COLLISION_JAMB, 0, 0, 16 - COLLISION_JAMB, SILL_TOP, WALL_DEPTH}, facing));
            return out;
        }
        out.add(RailingRules.rotate(new double[]{0, 0, 0, OPENING_LEFT, CAP_BOTTOM, WALL_DEPTH}, facing));
        out.add(RailingRules.rotate(new double[]{OPENING_RIGHT, 0, 0, 16, CAP_BOTTOM, WALL_DEPTH}, facing));
        out.add(RailingRules.rotate(new double[]{0, CAP_BOTTOM, 0, OPENING_LEFT, WALL_TOP, CAP_DEPTH}, facing));
        out.add(RailingRules.rotate(new double[]{OPENING_RIGHT, CAP_BOTTOM, 0, 16, WALL_TOP, CAP_DEPTH}, facing));
        out.add(RailingRules.rotate(new double[]{OPENING_LEFT, 0, 0, OPENING_RIGHT, SILL_TOP, WALL_DEPTH}, facing));
        if (!open) out.add(RailingRules.rotate(new double[]{OPENING_LEFT, SILL_TOP, 0, OPENING_RIGHT, WALL_TOP, 1.0}, facing));
        return out;
    }
}

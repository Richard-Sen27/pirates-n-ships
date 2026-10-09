package com.richardsenger.piratesnships.ship.decor.railing;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The pure rules of the railing (RAIL1, docs/design.md §4.8 "Railings (RAIL1/RAIL2)"), without world access: which edge
 * a click rails, where the posts stand, and the shapes, all in pixels of the block.
 *
 * <p><b>Edges.</b> A railing lies along one or more of its block's four horizontal edges, 2 px deep (the north edge is
 * z 0..2). <b>Placement</b> ({@link #edgeForClick}): the edge nearest the click point; a click on the top (or bottom)
 * of a block within {@link #CENTRE_PX} of the centre rails the edge the player faces instead. A click on an existing
 * railing adds the free edge nearest the click ({@link #nearestFree}).
 *
 * <p><b>Posts</b> ({@link #posts}): a 3 x 3 px post stands in a corner where two edges of the block meet (one corner
 * post), and where an edge ends without the run going on: the neighbour along the edge (the block beside this one in
 * the direction of that end) does not carry the same edge (a railing with it, or a railing port facing it). So a lone
 * edge has a post at either end, a straight run has posts only at its two ends, a run with a one-block gap has a post
 * on both sides of the gap, and two edges in one block make a corner. A corner is named by the edge it closes
 * clockwise: {@link Direction#NORTH} is the north-west corner, {@code EAST} north-east, {@code SOUTH} south-east,
 * {@code WEST} south-west (the corner between {@code d} and {@code d.getCounterClockWise()}), so turning the block by a
 * y rotation turns the corner keys like edges.
 */
public final class RailingRules {

    /** Half the side of the centre square of a top click that rails the edge the player faces, in pixels. */
    public static final double CENTRE_PX = 4.0;
    /** Depth of an edge's rail from the block's side, in pixels. */
    public static final double RAIL_DEPTH = 2.0;
    /** Side of a post, in pixels. */
    public static final double POST = 3.0;
    /** Top of a post's cap (the model's highest point), in pixels. */
    public static final double POST_TOP = 17.0;
    /** Top of the railing port's sill, in pixels. */
    public static final double SILL_TOP = 4.0;
    /** Side of a railing port's posts in its collision (the drawn posts are {@link #POST}), in pixels. */
    public static final double PORT_COLLISION_POST = 2.0;
    /** The top of an edge's rail and of the outline, in pixels. */
    public static final double RAIL_TOP = 16.0;

    /** What the block beside a railing holds: whether it carries {@code edge} (a railing with it, or a port facing it). */
    @FunctionalInterface
    public interface Neighbours {
        boolean carries(Direction towards, Direction edge);
    }

    private RailingRules() {
    }

    // ------------------------------------------------------------------ placement

    /**
     * The edge a click rails: {@code fx}, {@code fz} are the click point's position in the target block (0..1). A click
     * on a top or bottom face ({@code vertical}) within {@link #CENTRE_PX} of the centre (both axes) rails the edge the
     * player faces; otherwise the nearest edge (ties: north, east, south, west).
     */
    public static Direction edgeForClick(double fx, double fz, boolean vertical, Direction playerFacing) {
        double c = CENTRE_PX / 16.0;
        if (vertical && Math.abs(fx - 0.5) <= c && Math.abs(fz - 0.5) <= c && playerFacing.getAxis().isHorizontal()) {
            return playerFacing;
        }
        Direction best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            double dist = distance(fx, fz, d);
            if (dist < bestDistance - 1.0e-9) {
                best = d;
                bestDistance = dist;
            }
        }
        return best;
    }

    /** The free edge nearest the click point ({@code fx}, {@code fz} in the block, 0..1), or null when all four are taken. */
    public static @Nullable Direction nearestFree(double fx, double fz, Set<Direction> taken) {
        Direction best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (taken.contains(d)) continue;
            double dist = distance(fx, fz, d);
            if (dist < bestDistance - 1.0e-9) {
                best = d;
                bestDistance = dist;
            }
        }
        return best;
    }

    /** Distance of the point from the edge {@code d} of the block (0..1 units). */
    private static double distance(double fx, double fz, Direction d) {
        return switch (d) {
            case NORTH -> fz;
            case SOUTH -> 1.0 - fz;
            case WEST -> fx;
            case EAST -> 1.0 - fx;
            default -> Double.MAX_VALUE;
        };
    }

    // ------------------------------------------------------------------ posts

    /** The corner keyed {@code key} is between {@code key} and {@code key.getCounterClockWise()} (see the class comment). */
    public static Direction cornerOf(Direction a, Direction b) {
        return a.getCounterClockWise() == b ? a : b;
    }

    /** The posts of a railing with {@code edges}, by corner key (see the class comment). */
    public static Set<Direction> posts(Set<Direction> edges, Neighbours neighbours) {
        Set<Direction> out = EnumSet.noneOf(Direction.class);
        for (Direction key : Direction.Plane.HORIZONTAL) {
            Direction other = key.getCounterClockWise();
            boolean a = edges.contains(key);
            boolean b = edges.contains(other);
            if (a && b) {
                out.add(key); // a corner of two edges in this block
            } else if (a) {
                // edge `key` ends at this corner, which lies toward `other`
                if (!neighbours.carries(other, key)) out.add(key);
            } else if (b) {
                // edge `other` ends at this corner, which lies toward `key`
                if (!neighbours.carries(key, other)) out.add(key);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ shapes (pixels, {x0, y0, z0, x1, y1, z1})

    /** The box of the north edge's rail from y 0 to {@code top}. */
    private static double[] northRail(double top) {
        return new double[]{0, 0, 0, 16, top, RAIL_DEPTH};
    }

    /** The north-west post from y 0 to {@code top}. */
    private static double[] northWestPost(double top) {
        return new double[]{0, 0, 0, POST, top, POST};
    }

    /**
     * The boxes of a railing: each edge's rail ({@link #RAIL_DEPTH} deep, full width) and each post (3 x 3 px), the rails
     * up to {@code railTop} and the posts up to {@code postTop}. The outline uses the model's heights ({@link #RAIL_TOP},
     * {@link #POST_TOP}); the collision raises both to the configured collision height.
     */
    public static List<double[]> boxes(Set<Direction> edges, Set<Direction> posts, double railTop, double postTop) {
        List<double[]> out = new ArrayList<>();
        for (Direction d : edges) out.add(rotate(northRail(railTop), d));
        for (Direction d : posts) out.add(rotate(northWestPost(postTop), d));
        return out;
    }

    /**
     * The boxes of a railing port facing {@code facing} (its posts on that edge): the two posts at the edge's ends up
     * to {@code postTop} and the sill between them ({@link #SILL_TOP} high, {@link #RAIL_DEPTH} deep), no rails.
     */
    public static List<double[]> portBoxes(Direction facing, double postTop) {
        return portBoxes(facing, postTop, POST);
    }

    /**
     * {@link #portBoxes(Direction, double)} with posts {@code post} px square in the outer corners: the collision uses
     * {@link #PORT_COLLISION_POST} so a player (9.6 px wide) clears the posts with room to step over the sill, also on a
     * ship, whose step-up test inflates the player by 0.05 blocks on each side (Sable
     * {@code SubLevelEntityCollision#tryStepUp}).
     */
    public static List<double[]> portBoxes(Direction facing, double postTop, double post) {
        List<double[]> out = new ArrayList<>();
        out.add(rotate(new double[]{0, 0, 0, post, postTop, post}, facing));
        out.add(rotate(new double[]{16 - post, 0, 0, 16, postTop, post}, facing));
        out.add(rotate(new double[]{POST, 0, 0, 16 - POST, SILL_TOP, RAIL_DEPTH}, facing));
        return out;
    }

    // ------------------------------------------------------------------ the gate (RAIL3)

    /**
     * Whether a railing gate's hinge goes to its left post (looking outboard, toward {@code facing}): a click in the
     * left half of the edge hinges it there, the right half on the right post. {@code fx}, {@code fz}: the click point in
     * the block (0..1).
     */
    public static boolean gateHingeLeft(double fx, double fz, Direction facing) {
        double fromLeft = switch (facing.getCounterClockWise()) {
            case WEST -> fx;
            case EAST -> 1.0 - fx;
            case NORTH -> fz;
            default -> 1.0 - fz;
        };
        return fromLeft < 0.5;
    }

    /**
     * The boxes of a railing gate facing {@code facing}: open, the port's (posts and sill, the leaf swung outboard out
     * of the block); closed, the port's with the leaf between the posts, its rails and balusters ({@link #RAIL_DEPTH}
     * deep) up to {@code leafTop}. {@code post}: the posts' side ({@link #POST} drawn, {@link #PORT_COLLISION_POST} for
     * an open gate's collision so a player steps through as through a port).
     */
    public static List<double[]> gateBoxes(Direction facing, boolean open, double postTop, double leafTop, double post) {
        List<double[]> out = portBoxes(facing, postTop, post);
        if (!open) {
            out.add(rotate(new double[]{POST, 0, 0, 16 - POST, leafTop, RAIL_DEPTH}, facing));
        }
        return out;
    }

    // ------------------------------------------------------------------ hanging from a post (RAIL3)

    /** The centre column of a block, as vanilla's centre support ({@code SupportType.CENTER}) tests it, in pixels. */
    public static final double[] CENTRE_COLUMN = {7, 0, 7, 9, 16, 9};

    /**
     * The support shape of a railing block (RAIL3): what vanilla's lantern, chain and torch rules see of its top and
     * bottom faces. With a post ({@code hasPost}) the block counts as a sturdy centre: the posts and the centre column
     * ({@link #CENTRE_COLUMN}, which vanilla's centre support needs; a post stands in a corner), so a lantern hangs
     * below it or stands on it. Without one only the rails ({@code boxes}, cut to the block) and nothing at the centre.
     */
    public static List<double[]> supportBoxes(List<double[]> boxes, boolean hasPost) {
        List<double[]> out = new ArrayList<>(boxes.size() + 1);
        for (double[] b : boxes) out.add(new double[]{b[0], b[1], b[2], b[3], Math.min(16, b[4]), b[5]});
        if (hasPost) out.add(CENTRE_COLUMN.clone());
        return out;
    }

    /** Whether vanilla's centre support (a 2 x 2 px column at the centre, top or bottom face) is covered by {@code boxes}. */
    public static boolean supportsCentre(List<double[]> boxes, boolean top) {
        for (double[] b : boxes) {
            boolean face = top ? b[4] >= 16 : b[1] <= 0;
            if (face && b[0] <= 7 && b[2] <= 7 && b[3] >= 9 && b[5] >= 9) return true;
        }
        return false;
    }

    /**
     * A box given in the north frame turned to {@code facing} like a block state's y rotation (east maps (x, z) to
     * (16 - z, x)); the same turn as {@code ship.decor.DecorShapes#rotate}.
     */
    public static double[] rotate(double[] box, Direction facing) {
        double x0 = box[0], y0 = box[1], z0 = box[2], x1 = box[3], y1 = box[4], z1 = box[5];
        return switch (facing) {
            case EAST -> new double[]{16 - z1, y0, x0, 16 - z0, y1, x1};
            case SOUTH -> new double[]{16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0};
            case WEST -> new double[]{z0, y0, 16 - x1, z1, y1, 16 - x0};
            default -> box.clone();
        };
    }
}

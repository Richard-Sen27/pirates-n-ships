package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.sailing.sail.ClothGeometry;
import com.richardsenger.piratesnships.sailing.sail.SquareSail;
import com.richardsenger.piratesnships.sailing.sail.YardRow;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.List;

/**
 * Placement and support rules of the ratlines (RL1, docs/design.md §4.8 "Visual backlog 2", item 1), without world
 * access. A ratlines block is either hung {@link Kind#WALL against a face} like a ladder or laid {@link Kind#SLOPE
 * sloped} at 45 degrees; see {@link RatlinesBlock} for what {@code facing} means in each case.
 *
 * <p><b>Placement</b> ({@link #candidates}): the block tries these placements in order and takes the first that can
 * stand ({@link #survives}).
 * <ul>
 *   <li>Clicked on a <b>side face</b>: hung on that face ({@code WALL}, facing the clicked face); else sloped, rising
 *       the way the player looks; else hung on whatever the player looks at (vanilla ladder order).</li>
 *   <li>Clicked on a <b>top face</b> (deck, gunwale): sloped, rising the way the player looks, so standing at the
 *       gunwale and looking at the mast lays the first shroud towards the mast; else hung like a ladder.</li>
 *   <li>Clicked on a <b>bottom face</b>: hung like a ladder in the player's look order; else sloped.</li>
 * </ul>
 * Clicking an existing ratlines block without sneaking extends its run instead ({@link #next}), see
 * {@link RatlinesItem}.
 *
 * <p><b>Support</b>: a hung block needs a support on the face it hangs from (a sturdy face, a fence or a wall; it
 * falls like a ladder when that goes). A sloped block stands on a block below with a sturdy centre (the deck, the
 * gunwale), on another ratlines block straight below, on the previous sloped block of its run (one down and one back),
 * or leans with its top edge on a support in front of it (the mast).
 *
 * <p><b>Yards and the nest</b> (RL1b): a yard block and the crow's nest are anchors too
 * ({@code #pirates_n_ships:ratlines_anchors}), so a net hangs on a yard's face, a sloped net stands on a yard or leans
 * its top edge on a yard or the nest, and a run passes the yard rows on its way to the masthead. A square sail's cloth
 * hangs below its upper yard down to the lower one and bellies up to a block to either side of the yards' plane
 * ({@link #inCloth}); a net that only a yard holds up is refused there ({@link #refusedByCloth}): on a yard's end face
 * (beyond the cloth's width) or on any face of a yard that carries no cloth it hangs, on the fore or aft face of a
 * sail's yard it does not.
 */
public final class RatlinesRules {

    /** Longest run {@link RatlinesItem} walks along to find the free end of a run it extends. */
    public static final int MAX_EXTEND = 32;

    /** The two shapes of a ratlines block. */
    public enum Kind implements StringRepresentable {
        /** Hung on a vertical face like a ladder. */
        WALL("wall"),
        /** Laid at 45 degrees from the bottom edge of one side to the top edge of the opposite side. */
        SLOPE("slope");

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** A placement: the kind and the block state's {@code facing}. */
    public record Placement(Kind kind, Direction facing) {
    }

    /**
     * What holds a ratlines block up, read from the world by {@link RatlinesBlock}. For a hung block only
     * {@code wall} counts; for a sloped block the other four.
     *
     * @param wall      the block behind a hung net (opposite its facing) supports it on that face
     * @param floor     the block below has a sturdy centre on its top face (deck, gunwale, a fence post)
     * @param ratlinesBelow the block below is a ratlines block of either kind
     * @param runBelow  the block one down and one back (opposite the facing) is a sloped ratlines block with the same facing
     * @param leanOn    the block in front (towards the facing) supports the net's top edge on its facing side
     */
    public record Supports(boolean wall, boolean floor, boolean ratlinesBelow, boolean runBelow, boolean leanOn) {
    }

    private RatlinesRules() {
    }

    /**
     * The placements to try, in order (see the class comment).
     *
     * @param clickedFace the face of the block the player clicked
     * @param looking     the horizontal direction the player looks
     * @param lookOrder   the player's nearest looking directions, nearest first ({@code BlockPlaceContext#getNearestLookingDirections})
     */
    public static List<Placement> candidates(Direction clickedFace, Direction looking, Direction[] lookOrder) {
        Direction rise = looking.getAxis().isHorizontal() ? looking : Direction.NORTH;
        List<Placement> out = new ArrayList<>();
        if (clickedFace.getAxis().isHorizontal()) {
            add(out, new Placement(Kind.WALL, clickedFace));
            add(out, new Placement(Kind.SLOPE, rise));
            ladderOrder(out, lookOrder);
        } else if (clickedFace == Direction.UP) {
            add(out, new Placement(Kind.SLOPE, rise));
            ladderOrder(out, lookOrder);
        } else {
            ladderOrder(out, lookOrder);
            add(out, new Placement(Kind.SLOPE, rise));
        }
        return out;
    }

    /** Vanilla ladder order: hung on the block the player looks at, facing back at the player. */
    private static void ladderOrder(List<Placement> out, Direction[] lookOrder) {
        for (Direction d : lookOrder) {
            if (d.getAxis().isHorizontal()) {
                add(out, new Placement(Kind.WALL, d.getOpposite()));
            }
        }
    }

    private static void add(List<Placement> out, Placement p) {
        if (!out.contains(p)) {
            out.add(p);
        }
    }

    /** Whether a block of this kind can stand with these supports. */
    public static boolean survives(Kind kind, Supports s) {
        return switch (kind) {
            case WALL -> s.wall();
            case SLOPE -> s.floor() || s.ratlinesBelow() || s.runBelow() || s.leanOn();
        };
    }

    /**
     * The offset from a ratlines block to the next cell of its run going up: straight up for a hung net, one up and
     * one forward (towards the facing) for a sloped one.
     */
    public static Vec3i next(Kind kind, Direction facing) {
        return switch (kind) {
            case WALL -> new Vec3i(0, 1, 0);
            case SLOPE -> new Vec3i(facing.getStepX(), 1, facing.getStepZ());
        };
    }

    /** The offset to the block a sloped net rests on as the previous link of its run: one down, one back. */
    public static Vec3i previous(Direction facing) {
        return new Vec3i(-facing.getStepX(), -1, -facing.getStepZ());
    }

    // ------------------------------------------------------------------ the square sail's cloth (RL1b)

    /** Slack for the cloth's edges, so a cell that only touches an edge is outside. */
    private static final float EDGE_EPS = 1.0e-3f;

    /**
     * Whether the cell {@code (x, y, z)} lies in the span of {@code sail}'s cloth: from the upper yard's row down to the
     * lower yard's row, within the cloth's width at the cell's height (the trapezoid between the two yards, judged at
     * the cell's centre, so at a yard's row exactly the yard's own length), and at most one block off the yards' plane
     * to either side (the cloth leaves the yard at {@link ClothGeometry#AT_YARD}, stands
     * {@link ClothGeometry#STANDOFF} off it half a block lower and bellies on the side the wind blows to, so it reaches
     * into the cells fore and aft of the yards). Independent of the trim: a furled sail is set again later.
     */
    public static boolean inCloth(SquareSail sail, int x, int y, int z) {
        YardRow upper = sail.upper();
        int v = upper.y() - y;
        if (v < 0 || v > sail.drop()) {
            return false;
        }
        int across = upper.alongX() ? z : x;
        if (Math.abs(across - upper.fixed()) > 1) {
            return false;
        }
        int u = (upper.alongX() ? x : z) - upper.middle();
        ClothGeometry g = sail.geometry();
        return u + 0.5f > g.negativeEdge(v) + EDGE_EPS && u - 0.5f < g.positiveEdge(v) - EDGE_EPS;
    }

    /**
     * Whether a net of {@code kind} is refused at a cell because only a yard holds it there and the cell lies in a
     * sail's cloth ({@link #inCloth}). {@code withYards} counts yards as supports, {@code withoutYards} does not; a net
     * that also stands without the yard (on a run, a deck, the mast) is not refused.
     */
    public static boolean refusedByCloth(Kind kind, Supports withYards, Supports withoutYards, boolean inCloth) {
        return inCloth && survives(kind, withYards) && !survives(kind, withoutYards);
    }

    // ------------------------------------------------------------------ shapes (north frame, pixels)

    /** Height of a tread: a sloped net's collision is a stair of four treads under its four ratlines. */
    public static final double TREAD_THICKNESS = 1.5;
    /** Rise between two ratlines (pixels), on the wall and along the slope's height. */
    public static final int RATLINE_SPACING = 4;

    /**
     * The collision and outline boxes {@code {x0, y0, z0, x1, y1, z1}} of a ratlines block facing north (pixels).
     * Hung: a 3 px plate against the south side, like a ladder. Sloped (rising to the north, the bottom edge at the
     * south): four treads 4 px apart whose tops lie at heights 2, 6, 10 and 14 on the 45 degree line, so a player walks
     * up a run like a stair and stands on the ratlines.
     */
    public static List<double[]> boxes(Kind kind) {
        List<double[]> out = new ArrayList<>();
        if (kind == Kind.WALL) {
            out.add(new double[]{0, 0, 13, 16, 16, 16});
            return out;
        }
        for (int k = 0; k < 16 / RATLINE_SPACING; k++) {
            double top = RATLINE_SPACING * k + RATLINE_SPACING / 2.0;
            double south = 16 - RATLINE_SPACING * k;
            out.add(new double[]{0, top - TREAD_THICKNESS, south - RATLINE_SPACING, 16, top, south});
        }
        return out;
    }
}

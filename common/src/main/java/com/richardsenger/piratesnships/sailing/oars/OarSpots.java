package com.richardsenger.piratesnships.sailing.oars;

import com.richardsenger.piratesnships.sailing.oars.OarRules.Side;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where a crew member without an oar bench rows from (OAR1, docs/design.md §5.3b): a spot at the ship's rail, pure.
 *
 * <h2>Spots</h2>
 * A spot is a deck cell the crew can stand in ({@link Deck#standable}: room for feet and head over a sturdy floor) on
 * the ship's port or starboard side: its neighbour toward that side ({@code outboard}, across the bow, never toward
 * bow or stern) is
 * <ul>
 *   <li>a railing ({@link Deck#railing}, RAIL1): the rower stands inboard of the rail;</li>
 *   <li>open ({@link Deck#open}): nothing of the ship in it or under it, the hull's edge; or</li>
 *   <li>a bulwark ({@link Deck#bulwark}: a solid block with room above it, so the oar passes over) whose own outboard
 *       neighbour is open.</li>
 * </ul>
 * Every plot cell is in plot coordinates; the side follows from the ship's bow.
 *
 * <h2>Choosing</h2>
 * {@link #assign}: the crew members take spots one after the other, in the order given. Each goes to the side with
 * fewer rowers so far (benches included, {@code already}), on a tie to the side whose nearest free spot is closer to
 * it, and there to the nearest free spot; with no free spot on that side, to the other. So the rowers alternate and
 * the thrust stays balanced. A spot takes one rower; spots in {@code taken} are skipped.
 */
public final class OarSpots {

    /** What the spot rule needs to know of the deck, by plot cell. */
    public interface Deck {
        /** Room for a crew member: the cell and the one above have no collision, the floor below is sturdy. */
        boolean standable(BlockPos cell);

        /** Nothing of the ship in the cell and in the cell below it: beyond the hull's edge. */
        boolean open(BlockPos cell);

        /** The cell holds a railing (any railed edge). */
        boolean railing(BlockPos cell);

        /** The cell holds a solid block with room above it: a bulwark the oar passes over. */
        boolean bulwark(BlockPos cell);
    }

    /** A place at the rail: the cell the rower stands in, the outboard direction its oar points to, the side. */
    public record Spot(BlockPos cell, Direction outboard, Side side) {
        public Spot {
            cell = cell.immutable();
        }
    }

    /** A crew member to place: its id and where it stands (plot coordinates). */
    public record Hand(UUID id, Vec3 at) {
    }

    private OarSpots() {
    }

    /** The plot direction of the ship's port side for a bow along {@code (bowX, bowZ)} (ship +x turned into the plot). */
    public static Direction port(int bowX, int bowZ) {
        // BowFrame.toPlot((1, 0, 0)) = (bowZ, 0, −bowX)
        Direction d = Direction.fromDelta(bowZ, 0, -bowX);
        if (d == null) throw new IllegalArgumentException("bow must be a horizontal unit axis: " + bowX + ", " + bowZ);
        return d;
    }

    /** The side an outboard plot direction lies on, for the ship's port direction {@code port}. */
    public static Side sideOf(Direction outboard, Direction port) {
        if (outboard == port) return Side.PORT;
        if (outboard == port.getOpposite()) return Side.STARBOARD;
        return Side.NONE;
    }

    /** The spot at {@code cell} toward {@code outboard}, or null when it is none (rule above). */
    public static @Nullable Spot spotAt(Deck deck, BlockPos cell, Direction outboard, Direction port) {
        Side side = sideOf(outboard, port);
        if (side == Side.NONE || !deck.standable(cell)) {
            return null;
        }
        BlockPos n = cell.relative(outboard);
        boolean rail = deck.railing(n) || deck.open(n) || deck.bulwark(n) && deck.open(n.relative(outboard));
        return rail ? new Spot(cell, outboard, side) : null;
    }

    /**
     * Every spot among the deck cells {@code cells} (plot), sorted by position (y, then along x, then z) so the result
     * does not depend on the order the cells come in. A cell beside both rails (a one-block-wide deck) gives two spots.
     */
    public static List<Spot> find(Deck deck, Collection<BlockPos> cells, Direction port) {
        Comparator<BlockPos> order = Comparator.comparingInt(BlockPos::getY);
        Set<BlockPos> seen = new TreeSet<>(order.thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        seen.addAll(cells);
        List<Spot> out = new ArrayList<>();
        for (BlockPos c : seen) {
            for (Direction d : new Direction[]{port, port.getOpposite()}) {
                Spot s = spotAt(deck, c, d, port);
                if (s != null) out.add(s);
            }
        }
        return out;
    }

    /**
     * Places {@code hands} at the free spots (rule above). Returns crew id → spot, in the order of {@code hands}; a
     * crew member without a free spot left is not in the map.
     *
     * @param already rowers already at their oars per side (benches, earlier rail rowers)
     */
    public static Map<UUID, Spot> assign(List<Spot> spots, Set<BlockPos> taken, List<Hand> hands, Map<Side, Integer> already) {
        Map<Side, Integer> count = new EnumMap<>(Side.class);
        count.put(Side.PORT, already.getOrDefault(Side.PORT, 0));
        count.put(Side.STARBOARD, already.getOrDefault(Side.STARBOARD, 0));
        Set<BlockPos> used = new HashSet<>(taken);
        List<Spot> free = new ArrayList<>();
        for (Spot s : spots) {
            if (!used.contains(s.cell())) free.add(s);
        }
        Map<UUID, Spot> out = new LinkedHashMap<>();
        for (Hand hand : hands) {
            Spot port = nearest(free, used, hand.at(), Side.PORT);
            Spot stbd = nearest(free, used, hand.at(), Side.STARBOARD);
            Spot pick;
            if (port == null || stbd == null) {
                pick = port != null ? port : stbd;
            } else {
                int cp = count.get(Side.PORT), cs = count.get(Side.STARBOARD);
                if (cp != cs) {
                    pick = cp < cs ? port : stbd;
                } else {
                    pick = distance(hand.at(), port) <= distance(hand.at(), stbd) ? port : stbd;
                }
            }
            if (pick == null) {
                continue;
            }
            used.add(pick.cell());
            count.merge(pick.side(), 1, Integer::sum);
            out.put(hand.id(), pick);
        }
        return out;
    }

    private static @Nullable Spot nearest(List<Spot> free, Set<BlockPos> used, Vec3 at, Side side) {
        Spot best = null;
        double bestD = Double.MAX_VALUE;
        for (Spot s : free) {
            if (s.side() != side || used.contains(s.cell())) continue;
            double d = distance(at, s);
            if (d < bestD - 1e-9) {
                best = s;
                bestD = d;
            }
        }
        return best;
    }

    private static double distance(Vec3 at, Spot s) {
        return at.distanceToSqr(s.cell().getX() + 0.5, s.cell().getY(), s.cell().getZ() + 0.5);
    }
}

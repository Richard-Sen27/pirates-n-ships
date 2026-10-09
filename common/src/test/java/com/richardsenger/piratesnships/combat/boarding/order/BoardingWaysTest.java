package com.richardsenger.piratesnships.combat.boarding.order;

import com.richardsenger.piratesnships.combat.boarding.order.BoardingWays.Crossing;
import com.richardsenger.piratesnships.combat.boarding.order.BoardingWays.Deck;
import com.richardsenger.piratesnships.combat.boarding.order.BoardingWays.Frame;
import com.richardsenger.piratesnships.combat.boarding.order.BoardingWays.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ways across of the "Board!" order (BRD3): gap crossings between two flat 5×5 decks in their own plot frames
 * (ship B's plot lies 1000 blocks east of A's, so every map goes through the offset), plank crossings both ways, and
 * the choice of the nearest.
 */
class BoardingWaysTest {

    /** B's plot lies this far east of A's: a point of A's plot maps to B's plot by adding it. */
    private static final int PLOT_OFFSET = 1000;

    /** A flat deck of stand cells at row {@code y} over x {@code x0..x0+4}, z 0..4, with optional blocked cells. */
    private record Grid(Set<BlockPos> stands, Set<BlockPos> blocks) implements Deck {
        static Grid flat(int x0, int y) {
            Set<BlockPos> s = new HashSet<>();
            Set<BlockPos> b = new HashSet<>();
            for (int x = x0; x <= x0 + 4; x++) {
                for (int z = 0; z <= 4; z++) {
                    s.add(new BlockPos(x, y, z));
                    b.add(new BlockPos(x, y - 1, z)); // the deck itself
                }
            }
            return new Grid(s, b);
        }

        Grid block(BlockPos p) {
            stands.remove(p);
            blocks.add(p);
            return this;
        }

        @Override
        public boolean stand(BlockPos cell) {
            return stands.contains(cell);
        }

        @Override
        public boolean blocked(BlockPos cell) {
            return blocks.contains(cell);
        }
    }

    /** A at x 0..4 (row y 9); B with {@code gap} blocks of water east of it, its deck {@code dy} higher, in B's plot. */
    private static List<Crossing> gaps(int gap, int dy, int maxGap, Grid a) {
        int bx = 5 + gap;
        Grid b = Grid.flat(bx + PLOT_OFFSET, 9 + dy);
        UnaryOperator<Vec3> map = v -> v.add(PLOT_OFFSET, 0, 0);
        return BoardingWays.gaps(a.stands(), a, b, map, maxGap);
    }

    @Test
    void touchingHullsAreCrossedAlongTheWholeSide() {
        List<Crossing> ways = gaps(0, 0, 2, Grid.flat(0, 9));
        assertEquals(5, ways.size(), "one crossing per cell of the east edge: " + ways);
        for (Crossing c : ways) {
            assertEquals(Kind.GAP, c.kind());
            assertEquals(4, c.start().getX());
            assertEquals(5 + PLOT_OFFSET, c.landing().getX());
            assertEquals(0, c.gap());
            assertEquals(0, c.rise());
            assertTrue(!c.hop(), "touching hulls are stepped over, not jumped");
        }
    }

    @Test
    void aTwoBlockGapIsJumped() {
        List<Crossing> ways = gaps(2, 0, 2, Grid.flat(0, 9));
        assertEquals(5, ways.size());
        Crossing c = ways.get(0);
        assertEquals(2, c.gap());
        assertEquals(new BlockPos(7 + PLOT_OFFSET, 9, 0), c.landing());
        assertTrue(c.hop());
        assertTrue(c.via().isEmpty());
    }

    @Test
    void aThreeBlockGapIsRefused() {
        assertTrue(gaps(3, 0, 2, Grid.flat(0, 9)).isEmpty(), "three blocks of water is too far without a plank");
        assertEquals(5, gaps(3, 0, 3, Grid.flat(0, 9)).size(), "unless board_gap allows it");
    }

    @Test
    void aGapOfZeroOnlyCrossesTouchingHulls() {
        assertTrue(gaps(1, 0, 0, Grid.flat(0, 9)).isEmpty());
        assertEquals(5, gaps(0, 0, 0, Grid.flat(0, 9)).size());
    }

    @Test
    void aDeckOneBlockUpOrDownIsReachedTwoIsNot() {
        List<Crossing> up = gaps(1, 1, 2, Grid.flat(0, 9));
        assertEquals(5, up.size());
        assertEquals(1, up.get(0).rise());
        assertTrue(up.get(0).hop(), "a higher deck is jumped even across touching hulls");
        List<Crossing> down = gaps(1, -1, 2, Grid.flat(0, 9));
        assertEquals(5, down.size());
        assertEquals(-1, down.get(0).rise());
        assertTrue(gaps(1, 2, 2, Grid.flat(0, 9)).isEmpty(), "two blocks up is a wall");
        assertTrue(gaps(1, -2, 2, Grid.flat(0, 9)).isEmpty(), "two blocks down is a drop");
    }

    @Test
    void aRailingAtTheEdgeStopsTheLineAGateOpensIt() {
        Grid a = Grid.flat(0, 9);
        for (int z = 0; z <= 4; z++) a.block(new BlockPos(4, 9, z)); // a railing in every edge cell
        assertTrue(gaps(1, 0, 2, a).isEmpty(), "a railed side has no way across");
        Grid gated = Grid.flat(0, 9);
        for (int z = 0; z <= 4; z++) if (z != 2) gated.block(new BlockPos(4, 9, z));
        List<Crossing> ways = gaps(1, 0, 2, gated);
        assertEquals(1, ways.size(), "only the open gate: " + ways);
        assertEquals(new BlockPos(4, 9, 2), ways.get(0).start());
    }

    @Test
    void theOtherShipsHullSideStopsTheLine() {
        Grid a = Grid.flat(0, 9);
        Grid b = Grid.flat(7 + PLOT_OFFSET, 9);
        // B's bulwark: a wall two high along its west edge, nothing to stand on there
        for (int z = 0; z <= 4; z++) {
            b.block(new BlockPos(7 + PLOT_OFFSET, 9, z));
            b.block(new BlockPos(7 + PLOT_OFFSET, 10, z));
        }
        List<Crossing> ways = BoardingWays.gaps(a.stands(), a, b, v -> v.add(PLOT_OFFSET, 0, 0), 4);
        assertTrue(ways.isEmpty(), "a bulwark is no landing and the line stops at it: " + ways);
    }

    @Test
    void aRotatedShipIsMappedThroughItsFrame() {
        // B's plot turned a quarter: A's +x is B's +z
        Grid a = Grid.flat(0, 9);
        Set<BlockPos> s = new HashSet<>();
        for (int x = 0; x <= 4; x++) for (int z = 7; z <= 11; z++) s.add(new BlockPos(x + PLOT_OFFSET, 9, z));
        Grid b = new Grid(s, new HashSet<>());
        UnaryOperator<Vec3> map = v -> new Vec3(v.z + PLOT_OFFSET, v.y, v.x); // swap x and z
        List<Crossing> ways = BoardingWays.gaps(a.stands(), a, b, map, 2);
        assertEquals(5, ways.size());
        assertEquals(7, ways.get(0).landing().getZ());
        assertEquals(2, ways.get(0).gap());
    }

    @Test
    void aPlankLaidFromTheLeavingShipIsWalkedFromItsBase() {
        Crossing c = BoardingWays.plank(true, new BlockPos(5, 9, 2), Direction.EAST, 4, 0.125, 0.125,
                new BlockPos(4, 9, 2), new BlockPos(10 + PLOT_OFFSET, 9, 2));
        assertEquals(Kind.PLANK, c.kind());
        assertEquals(2, c.via().size());
        assertEquals(Frame.FROM, c.via().get(0).frame());
        assertEquals(5.5, c.via().get(0).at().x, 1e-9);
        assertEquals(8.5, c.via().get(1).at().x, 1e-9, "then the tip");
        assertTrue(!c.hop());
    }

    @Test
    void aPlankLaidFromTheOtherShipIsWalkedFromItsTip() {
        Crossing c = BoardingWays.plank(false, new BlockPos(PLOT_OFFSET + 8, 9, 2), Direction.WEST, 3, 0.125, 0.25,
                new BlockPos(4, 9, 2), new BlockPos(PLOT_OFFSET + 9, 9, 2));
        assertEquals(Frame.TO, c.via().get(0).frame());
        assertEquals(PLOT_OFFSET + 6.5, c.via().get(0).at().x, 1e-9, "the tip first");
        assertEquals(9.25, c.via().get(0).at().y, 1e-9);
        assertEquals(PLOT_OFFSET + 8.5, c.via().get(1).at().x, 1e-9, "then segment 0");
    }

    @Test
    void theNearestStartWinsAndAPlankWinsATie() {
        Crossing gap = new Crossing(Kind.GAP, new BlockPos(4, 9, 0), new BlockPos(7, 9, 0), List.of(), 2, 0);
        Crossing far = new Crossing(Kind.GAP, new BlockPos(4, 9, 4), new BlockPos(7, 9, 4), List.of(), 2, 0);
        Crossing plank = new Crossing(Kind.PLANK, new BlockPos(4, 9, 0), new BlockPos(9, 9, 0), List.of(), 0, 0);
        assertSame(gap, BoardingWays.nearest(List.of(far, gap), new Vec3(1, 9, 0)));
        assertSame(plank, BoardingWays.nearest(List.of(gap, plank), new Vec3(1, 9, 0)));
        assertNull(BoardingWays.nearest(List.of(), new Vec3(1, 9, 0)), "no way: the order is refused");
    }

    // ------------------------------------------------------------------ BRD5: off the landing, in file

    /**
     * B's deck as in the boarding GameTests (x 10..14, the helm right ahead of the landing), landing at 11 from a board
     * running east (z shifted down by 9): never behind the landing (under the board's end), never the helm nor past its
     * corners (a body-wide straight walk), nearest first.
     */
    @Test
    void roomCellsLieBesideOrAheadOfTheLandingAndClearOfTheHelm() {
        Grid b = Grid.flat(10, 9);
        BlockPos landing = new BlockPos(11, 9, 2);
        b.block(new BlockPos(12, 9, 2)); // the helm
        List<BlockPos> cells = BoardingWays.roomCells(landing, new Vec3(4, 0, 0), b::stand, c -> true, 3);
        assertEquals(Set.of(new BlockPos(11, 9, 1), new BlockPos(11, 9, 3)), Set.copyOf(cells.subList(0, 2)), "beside the landing");
        assertEquals(Set.of(new BlockPos(11, 9, 0), new BlockPos(11, 9, 4)), Set.copyOf(cells.subList(2, 4)), "two beside it");
        assertEquals(4, cells.size(), "every other cell lies behind the helm or its corners: " + cells);
    }

    @Test
    void roomCellsPreferStraightAheadAndStayWithinTheRadius() {
        Grid b = Grid.flat(0, 9);
        BlockPos landing = new BlockPos(1, 9, 2);
        List<BlockPos> cells = BoardingWays.roomCells(landing, new Vec3(1, 0, 0), b::stand, c -> true, 1);
        assertEquals(new BlockPos(2, 9, 2), cells.get(0), "straight ahead first");
        assertEquals(5, cells.size(), "ahead, two beside, two diagonals ahead: " + cells);
        for (BlockPos c : cells) assertTrue(c.getX() >= landing.getX(), "behind the landing (under the board): " + c);
        // a ship turned a little: the cells beside the landing are still beside it
        List<BlockPos> turned = BoardingWays.roomCells(landing, new Vec3(1, 0, 0.03), b::stand, c -> true, 1);
        assertTrue(turned.contains(new BlockPos(1, 9, 1)) && turned.contains(new BlockPos(1, 9, 3)), "beside on a turned ship: " + turned);
        assertTrue(BoardingWays.roomCells(landing, new Vec3(1, 0, 0), c -> false, c -> true, 3).isEmpty(), "no deck: no room");
    }

    /** Taken cells are left out, and a cell whose way passes a shipmate comes after every cell with a clear way. */
    @Test
    void roomCellsSkipTakenCellsAndAvoidWalkingThroughShipmates() {
        Grid b = Grid.flat(0, 9);
        BlockPos landing = new BlockPos(1, 9, 2);
        BlockPos mate = new BlockPos(2, 9, 2);
        List<BlockPos> cells = BoardingWays.roomCells(landing, new Vec3(1, 0, 0), b::stand, c -> !c.equals(mate), 3);
        assertTrue(!cells.contains(mate), "a shipmate's cell");
        int behindMate = cells.indexOf(new BlockPos(3, 9, 2));
        assertTrue(behindMate >= 0, "the cell behind the mate is still a way aside: " + cells);
        int beside = cells.indexOf(new BlockPos(1, 9, 0));
        assertTrue(beside >= 0 && beside < behindMate, "two beside the landing (a clear way) before as far ahead past the mate");
    }

    @Test
    void aBoarderWaitsForTheMateAheadOnly() {
        Vec3 me = new Vec3(0, 0, 0);
        Vec3 aim = new Vec3(4, 0, 0);
        assertTrue(BoardingWays.waitsFor(me, aim, new Vec3(0.8, 0, 0.1), 1.0), "a mate just ahead");
        assertTrue(!BoardingWays.waitsFor(me, aim, new Vec3(1.2, 0, 0), 1.0), "a block and more ahead: walk on");
        assertTrue(!BoardingWays.waitsFor(me, aim, new Vec3(-0.6, 0, 0), 1.0), "a mate behind");
        assertTrue(!BoardingWays.waitsFor(me, aim, new Vec3(0, 0, 0.7), 1.0), "a mate beside");
        assertTrue(!BoardingWays.waitsFor(me, me, new Vec3(0.5, 0, 0), 1.0), "at its aim already");
    }
}

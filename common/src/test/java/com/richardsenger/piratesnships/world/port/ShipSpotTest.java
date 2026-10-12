package com.richardsenger.piratesnships.world.port;

import com.richardsenger.piratesnships.ship.template.TemplatePlacement;
import com.richardsenger.piratesnships.world.port.ShipSpot.Anchor;
import com.richardsenger.piratesnships.world.port.ShipSpot.Failure;
import com.richardsenger.piratesnships.world.port.ShipSpot.Hull;
import com.richardsenger.piratesnships.world.port.ShipSpot.Rect;
import com.richardsenger.piratesnships.world.port.ShipSpot.Rules;
import com.richardsenger.piratesnships.world.port.ShipSpot.Search;
import com.richardsenger.piratesnships.world.port.ShipSpot.Spot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** STR7's delivery spot rule ({@link ShipSpot}) on hand-made harbours of column flags. */
class ShipSpotTest {

    /** The shipped sloops: 9 wide, 29 long. */
    private static final Hull SLOOP = new Hull(9, 29);
    private static final Rules RULES = new Rules(3, 64);
    private static final int SEA = ShipSpot.WATER | ShipSpot.DEEP | ShipSpot.OPEN;
    private static final int SHALLOWS = ShipSpot.WATER | ShipSpot.OPEN | ShipSpot.LAND;
    private static final int KELP = ShipSpot.WATER | ShipSpot.OPEN;
    private static final int PIER = 0;
    private static final ShipSpot.Occupied FREE = r -> false;

    /** SW1's test harbour: a 38 by 38 basin (stone rim), a pier deck x 19..21 along z, berths at x 18 and 22. */
    private static int harbour(int x, int z) {
        if (x <= 0 || x >= 39 || z <= 0 || z >= 39) return ShipSpot.LAND;
        if (x >= 19 && x <= 21) return PIER;
        return SEA;
    }

    private static final Anchor BERTH_1 = new Anchor(18, 20, Direction.NORTH, true, Direction.EAST);
    private static final Anchor BERTH_2 = new Anchor(22, 20, Direction.NORTH, true, Direction.WEST);

    @Test
    void footprintIsCentredOnTheColumn() {
        assertEquals(new Rect(-4, -14, 4, 14), ShipSpot.footprint(SLOOP, Direction.NORTH, 0, 0));
        assertEquals(new Rect(-14, -4, 14, 4), ShipSpot.footprint(SLOOP, Direction.EAST, 0, 0));
        assertEquals(new Rect(10, 20, 13, 21), ShipSpot.footprint(new Hull(2, 4), Direction.WEST, 11, 20));
        assertThrows(IllegalArgumentException.class, () -> new Hull(0, 3));
    }

    /** The berth's moorings are SW1's: the same box as {@code TemplatePlacement.berthOrigin} for every bow and side. */
    @Test
    void berthFootprintMatchesTheBerthPlacement() {
        Vec3i size = new Vec3i(9, 21, 29);   // the sloops' template, bow north
        for (Direction bow : Direction.Plane.HORIZONTAL) {
            Rotation rotation = TemplatePlacement.rotationFor(Direction.NORTH, bow);
            Anchor berth = new Anchor(100, -40, bow, true, null);
            for (Direction side : List.of(bow.getClockWise(), bow.getCounterClockWise())) {
                for (int slide : ShipSpot.BERTH_SLIDES) {
                    BlockPos origin = TemplatePlacement.berthOrigin(size, rotation, bow, side, new BlockPos(100, 62, -40),
                            ShipSpot.BERTH_GAP, 62, 2).relative(bow, slide);
                    BoundingBox box = TemplatePlacement.worldBox(size, rotation, origin);
                    assertEquals(new Rect(box.minX(), box.minZ(), box.maxX(), box.maxZ()),
                            ShipSpot.berthFootprint(SLOOP, berth, side, slide), bow + " " + side + " " + slide);
                }
            }
        }
    }

    @Test
    void berthSidesPutThePierLast() {
        assertEquals(List.of(Direction.WEST, Direction.EAST), ShipSpot.berthSides(BERTH_1));
        assertEquals(List.of(Direction.EAST, Direction.WEST), ShipSpot.berthSides(BERTH_2));
        assertEquals(List.of(Direction.EAST, Direction.WEST), ShipSpot.berthSides(new Anchor(0, 0, Direction.NORTH, true, null)));
    }

    /** In deep water the first berth's own mooring wins: alongside, away from the pier, centred. */
    @Test
    void deepBerthTakesTheShipAlongside() {
        Search s = ShipSpot.find(ShipSpotTest::harbour, List.of(BERTH_1, BERTH_2), SLOOP, RULES, FREE);
        Spot spot = s.spot().orElseThrow();
        assertEquals(new Rect(9, 6, 17, 34), spot.footprint());
        assertEquals(Direction.NORTH, spot.heading());
        assertEquals(0, spot.anchor());
        assertEquals(0, spot.distance());
        assertEquals(Failure.NONE, s.failure());
    }

    /** A ship at berth 1 sends the next to berth 2; with both taken and no other room the failure says occupied. */
    @Test
    void occupiedBerthsMoveOnAndThenRefuse() {
        List<Rect> ships = new ArrayList<>();
        ShipSpot.Occupied occupied = r -> ships.stream().anyMatch(s -> overlaps(s.inflate(1), r));
        Spot first = ShipSpot.find(ShipSpotTest::harbour, List.of(BERTH_1, BERTH_2), SLOOP, RULES, occupied).spot().orElseThrow();
        ships.add(first.footprint());
        Spot second = ShipSpot.find(ShipSpotTest::harbour, List.of(BERTH_1, BERTH_2), SLOOP, RULES, occupied).spot().orElseThrow();
        assertEquals(1, second.anchor());
        assertEquals(new Rect(23, 6, 31, 34), second.footprint());
        ships.add(second.footprint());
        Search third = ShipSpot.find(ShipSpotTest::harbour, List.of(BERTH_1, BERTH_2), SLOOP, RULES, occupied);
        assertTrue(third.spot().isEmpty(), "no third spot: " + third.spot());
        assertEquals(Failure.OCCUPIED, third.failure());
    }

    /**
     * A shallow shore (sand under the keel for 20 blocks north of the beach at z 0, then the open sea) with a pier
     * running out over it: the berth is shallow, so the ship lies farther out, all its columns deep and no sand within
     * the clearance, still over water reached from the berth.
     */
    @Test
    void shallowBerthSendsTheShipFartherOut() {
        ShipSpot.Columns shore = (x, z) -> z >= 0 ? ShipSpot.LAND
                : x >= -1 && x <= 1 && z >= -20 ? PIER
                : z >= -20 ? SHALLOWS : SEA;
        Anchor berth = new Anchor(-2, -10, Direction.NORTH, true, Direction.EAST);
        Search s = ShipSpot.find(shore, List.of(berth), SLOOP, RULES, FREE);
        Spot spot = s.spot().orElseThrow();
        Rect r = spot.footprint();
        assertTrue(spot.distance() > 0, "not at the berth itself");
        for (int x = r.minX() - RULES.clearance(); x <= r.maxX() + RULES.clearance(); x++) {
            for (int z = r.minZ() - RULES.clearance(); z <= r.maxZ() + RULES.clearance(); z++) {
                assertEquals(0, shore.flags(x, z) & ShipSpot.LAND, "no land at " + x + " " + z + " within the clearance of " + r);
                if (r.contains(x, z)) assertEquals(SEA, shore.flags(x, z), "deep at " + x + " " + z);
            }
        }
        // the nearest spot: its landward side lies just past the clearance from the shallows
        assertEquals(-21 - RULES.clearance(), r.maxZ() + 0, "nearest to the shore: " + r);
        assertTrue(spot.distance() <= RULES.search());
    }

    @Test
    void noWaterAtAllIsNoWater() {
        Search s = ShipSpot.find((x, z) -> ShipSpot.LAND, List.of(BERTH_1), SLOOP, RULES, FREE);
        assertTrue(s.spot().isEmpty());
        assertEquals(Failure.NO_WATER, s.failure());
        assertEquals(Failure.NO_WATER, ShipSpot.find(ShipSpotTest::harbour, List.of(), SLOOP, RULES, FREE).failure());
    }

    /** Deep water behind a sand bar that the walk cannot cross (a lake behind the village) is never used. */
    @Test
    void waterNotReachedFromTheBerthIsNotUsed() {
        // a small pond round the berth (too small for a ship), a dune at z -30, a big lake beyond it
        ShipSpot.Columns map = (x, z) -> z == -30 ? ShipSpot.LAND : z > -30 && (Math.abs(x) > 6 || z < -20 || z > 0) ? ShipSpot.LAND : SEA;
        Search s = ShipSpot.find(map, List.of(Anchor.point(0, -10, Direction.NORTH)), SLOOP, RULES, FREE);
        assertTrue(s.spot().isEmpty(), "the lake beyond the dune is not reached: " + s.spot());
        assertEquals(Failure.NO_WATER, s.failure());
    }

    /** A kelp column (water, not deep) in the berth's footprint keeps the ship off it. */
    @Test
    void kelpInTheFootprintMovesTheShip() {
        ShipSpot.Columns map = (x, z) -> x == 16 && z == 10 ? KELP : harbour(x, z);
        Spot spot = ShipSpot.find(map, List.of(BERTH_1), SLOOP, RULES, FREE).spot().orElseThrow();
        assertFalse(spot.footprint().contains(16, 10), "kelp under " + spot.footprint());
        assertFalse(spot.footprint().equals(new Rect(9, 6, 17, 34)));
    }

    /**
     * Nothing solid may touch the hull: a footprint with a post (solid, not ground) right beside it is refused even in
     * deep water, so the assembler never gathers a pier with the ship; the next column over fits.
     */
    @Test
    void theHullNeverTouchesAPost() {
        ShipSpot.Columns map = (x, z) -> x == 9 && z == 20 ? PIER : harbour(x, z) == PIER ? SEA : harbour(x, z);
        Spot spot = ShipSpot.find(map, List.of(Anchor.point(14, 20, Direction.NORTH)), SLOOP, RULES, FREE).spot().orElseThrow();
        assertEquals(1, spot.distance(), "the centre itself has the post in its ring: " + spot);
        assertEquals(new Rect(11, 5, 19, 33), spot.footprint());
    }

    /** In an east-west channel too narrow for a ship heading north, the ship turns across the berth's bow. */
    @Test
    void aNarrowChannelTurnsTheShip() {
        ShipSpot.Columns channel = (x, z) -> z >= -8 && z <= 8 && x > -80 && x < 80 ? SEA : ShipSpot.LAND;
        Spot spot = ShipSpot.find(channel, List.of(Anchor.point(0, 0, Direction.NORTH)), new Hull(5, 21), new Rules(2, 64), FREE)
                .spot().orElseThrow();
        assertEquals(Direction.EAST, spot.heading());
        assertEquals(new Rect(-10, -2, 10, 2), spot.footprint());
    }

    /** Deep water only beyond the search distance: none. */
    @Test
    void theSearchStopsAtItsDistance() {
        ShipSpot.Columns far = (x, z) -> z > -40 ? (z >= 0 ? ShipSpot.LAND : SHALLOWS) : SEA;
        Search s = ShipSpot.find(far, List.of(Anchor.point(0, -1, Direction.NORTH)), SLOOP, new Rules(3, 40), FREE);
        assertTrue(s.spot().isEmpty());
        Search wider = ShipSpot.find(far, List.of(Anchor.point(0, -1, Direction.NORTH)), SLOOP, new Rules(3, 64), FREE);
        assertTrue(wider.spot().isPresent());
        assertEquals(-43, wider.spot().get().footprint().maxZ());
        assertEquals(Direction.EAST, wider.spot().get().heading(), "across: nearer than along the bow");
    }

    private static boolean overlaps(Rect a, Rect b) {
        return a.minX() <= b.maxX() && b.minX() <= a.maxX() && a.minZ() <= b.maxZ() && b.minZ() <= a.maxZ();
    }
}

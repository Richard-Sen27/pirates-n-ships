package com.richardsenger.piratesnships.sailing.anchor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.ship.ShipAnchor;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class AnchorRulesTest {

    /**
     * A 7-wide (x -3..3) by 9-long (z -4..4) deck at y = -1 relative to the capstan, with a bulwark at x = -3 on the
     * capstan's layer and a hatch (gap) at x = 1. The capstan stands at x = -1, so starboard/port depend on the bow.
     */
    private static HullSide.Cells deck(int capstanX) {
        Set<String> cells = new HashSet<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -4; z <= 4; z++) {
                if (x != 1) {
                    cells.add(x + ",-1," + z);
                }
            }
        }
        for (int z = -4; z <= 4; z++) {
            cells.add("-3,0," + z);
        }
        return (dx, dy, dz) -> cells.contains((capstanX + dx) + "," + dy + "," + dz);
    }

    @Test
    void starboardIsTheRightHandOfTheBow() {
        assertArrayEquals(new int[] {-1, 0}, HullSide.starboard(0, 1));  // bow south: starboard west
        assertArrayEquals(new int[] {1, 0}, HullSide.starboard(0, -1));  // bow north: starboard east
        assertArrayEquals(new int[] {0, 1}, HullSide.starboard(1, 0));   // bow east: starboard south
        assertArrayEquals(new int[] {0, -1}, HullSide.starboard(-1, 0)); // bow west: starboard north
    }

    @Test
    void hangsOnTheNearerSideAcrossTheShipForEveryBow() {
        // capstan at x = -1: west face is 3 cells away (x -2, -3 are ship), east face 5 cells (the hatch at x = 1 is a gap)
        HullSide.Cells cells = deck(-1);
        HullSide.Placement south = HullSide.find(0, 1, 32, cells);
        assertEquals(new HullSide.Placement(-1, 0, 3, true), south);
        HullSide.Placement north = HullSide.find(0, -1, 32, cells);
        assertEquals(new HullSide.Placement(-1, 0, 3, false), north);
        // bow along x: across the ship is z; the deck runs to z = ±4, so both faces are 5 away: tie goes to starboard
        assertEquals(new HullSide.Placement(0, 1, 5, true), HullSide.find(1, 0, 32, cells));
        assertEquals(new HullSide.Placement(0, -1, 5, true), HullSide.find(-1, 0, 32, cells));
    }

    @Test
    void hatchGapDoesNotEndTheSearchAndReachLimitsIt() {
        assertEquals(5, HullSide.distance(1, 0, 32, deck(-1)));
        assertEquals(2, HullSide.distance(1, 0, 1, deck(-1)));
        assertEquals(1, HullSide.distance(1, 0, 32, (dx, dy, dz) -> false));
    }

    @Test
    void hawseHangsJustOutsideTheFaceBelowDeck() {
        double[] o = new HullSide.Placement(-1, 0, 3, true).hawseOffset();
        // relative to the capstan block [0, 1]: the outermost ship cell spans [-2, -1], the face is at -2, the outside cell [-3, -2]
        assertEquals(0.5 - (3 - 0.5 + HullSide.HULL_GAP), o[0], 1e-9);
        assertEquals(-HullSide.RING_BELOW_DECK, o[1], 1e-9);
        assertEquals(0.5, o[2], 1e-9);
        assertTrue(o[0] < -2.0 && o[0] > -3.0, "hawse not in the first cell outside the hull: " + o[0]);
    }

    @Test
    void depthIsFromTheStowedCrownToTheGround() {
        assertEquals(8.0, AnchorTravel.distance(12.0, 2.0), 1e-9);
        assertEquals(0.0, AnchorTravel.distance(3.0, 2.0), 1e-9);
    }

    @Test
    void anchorRoundTripsAndPreAn2aSavesLoad() {
        ShipAnchor a = new ShipAnchor(AnchorState.RAISING, new Vec3(12.5, 2.0, -3000.25), new Vec3(1.5, -3.25, 0.0), 11.75, true,
                new BlockPos(20_000_001, 70, -19_999_000), new Vec3(20_000_003.9, 69.75, -19_998_999.5));
        assertEquals(a, ShipAnchor.CODEC.parse(NbtOps.INSTANCE, ShipAnchor.CODEC.encodeStart(NbtOps.INSTANCE, a).getOrThrow()).getOrThrow());
        assertEquals(a, ShipAnchor.CODEC.parse(JsonOps.INSTANCE, ShipAnchor.CODEC.encodeStart(JsonOps.INSTANCE, a).getOrThrow()).getOrThrow());

        // the format before AN2a: a timed ramp to a fixed point, with and without a hawse
        CompoundTag state = new CompoundTag();
        state.putString("phase", "HOLDING");
        state.putDouble("hold", 1.0);
        CompoundTag legacy = new CompoundTag();
        legacy.put("state", state);
        legacy.put("point", Vec3.CODEC.encodeStart(NbtOps.INSTANCE, new Vec3(12.5, 2.0, -3000.25)).getOrThrow());
        legacy.put("capstan", BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, new BlockPos(20_000_001, 70, -19_999_000)).getOrThrow());
        legacy.putInt("drop_ticks", 40);
        ShipAnchor old = ShipAnchor.CODEC.parse(NbtOps.INSTANCE, legacy).getOrThrow();
        assertEquals(AnchorState.HOLDING, old.state());
        assertEquals(new Vec3(12.5, 2.0, -3000.25), old.position());
        assertTrue(old.resting());
        assertEquals(ShipAnchor.UNKNOWN_CHAIN, old.paidOut());
        assertEquals(Vec3.atCenterOf(old.capstan()), old.hawse());
        legacy.put("hawse", Vec3.CODEC.encodeStart(NbtOps.INSTANCE, new Vec3(1, 2, 3)).getOrThrow());
        state.putString("phase", "DROPPING");
        ShipAnchor dropping = ShipAnchor.CODEC.parse(NbtOps.INSTANCE, legacy).getOrThrow();
        assertEquals(new Vec3(1, 2, 3), dropping.hawse());
        assertEquals(AnchorState.DROPPING, dropping.state());
        assertFalse(dropping.resting());
    }

    @Test
    void stateFollowsTheBody() {
        assertEquals(AnchorState.DROPPING, AnchorState.RAISED.drop());
        assertEquals(AnchorState.HOLDING, AnchorState.DROPPING.landed());
        assertEquals(AnchorState.DROPPING, AnchorState.HOLDING.lifted());
        assertEquals(AnchorState.RAISING, AnchorState.HOLDING.raise());
        assertEquals(AnchorState.RAISING, AnchorState.DROPPING.raise());
        assertEquals(AnchorState.DROPPING, AnchorState.RAISING.drop());
        assertEquals(AnchorState.RAISING, AnchorState.RAISING.landed(), "a heaved anchor touching ground keeps winding");
        assertEquals(AnchorState.HOLDING, AnchorState.HOLDING.drop());
        assertFalse(AnchorState.RAISED.isOut());
    }

    @Test
    void aDropLeavesTheHawseWithItsVelocity() {
        ShipAnchor a = ShipAnchor.dropped(new BlockPos(5, 9, 5), new Vec3(2.6, 8.75, 5.5), new Vec3(100.0, 70.0, 200.0), new Vec3(0, 0, 4));
        assertEquals(new Vec3(100.0, 70.0, 200.0), a.ring());
        assertEquals(new Vec3(0, 0, 4), a.velocity());
        assertEquals(0.0, a.paidOut());
        assertFalse(a.resting());
        assertEquals(AnchorState.DROPPING, a.state());
    }
}

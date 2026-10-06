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
    void travelTimeIsDepthOverSpeedClamped() {
        assertEquals(40, AnchorTravel.ticks(12.0, 6.0, 20, 400));
        assertEquals(20, AnchorTravel.ticks(1.0, 6.0, 20, 400));   // minimum
        assertEquals(400, AnchorTravel.ticks(500.0, 6.0, 20, 400)); // maximum
        assertEquals(20, AnchorTravel.ticks(-3.0, 6.0, 20, 400));  // ground above the stowed crown
        assertEquals(34, AnchorTravel.ticks(10.0, 6.0, 1, 400));   // 33.3 rounds up
        assertEquals(400, AnchorTravel.ticks(10.0, 0.0, 20, 400));
        assertEquals(8.0, AnchorTravel.distance(12.0, 2.0), 1e-9);
        assertEquals(0.0, AnchorTravel.distance(3.0, 2.0), 1e-9);
        assertEquals(5.0, AnchorTravel.lerp(0.0, 10.0, 0.5), 1e-9);
        assertEquals(10.0, AnchorTravel.lerp(0.0, 10.0, 2.0), 1e-9);
    }

    @Test
    void extendedAnchorRoundTripsAndLegacySavesLoad() {
        ShipAnchor a = new ShipAnchor(new AnchorState(AnchorState.Phase.RAISING, 0.375), new Vec3(12.5, 2.0, -3000.25),
                new BlockPos(20_000_001, 70, -19_999_000), new Vec3(20_000_003.9, 69.75, -19_998_999.5), 57, 133);
        assertEquals(a, ShipAnchor.CODEC.parse(NbtOps.INSTANCE, ShipAnchor.CODEC.encodeStart(NbtOps.INSTANCE, a).getOrThrow()).getOrThrow());
        assertEquals(a, ShipAnchor.CODEC.parse(JsonOps.INSTANCE, ShipAnchor.CODEC.encodeStart(JsonOps.INSTANCE, a).getOrThrow()).getOrThrow());
        assertEquals(a.withState(AnchorState.RAISED.drop()).dropTicks(), 57);

        CompoundTag legacy = (CompoundTag) ShipAnchor.CODEC.encodeStart(NbtOps.INSTANCE, a).getOrThrow();
        legacy.remove("hawse");
        legacy.remove("drop_ticks");
        legacy.remove("raise_ticks");
        ShipAnchor old = ShipAnchor.CODEC.parse(NbtOps.INSTANCE, legacy).getOrThrow();
        assertEquals(Vec3.atCenterOf(a.capstan()), old.hawse());
        assertEquals(ShipAnchor.LEGACY_DROP_TICKS, old.dropTicks());
        assertEquals(ShipAnchor.LEGACY_RAISE_TICKS, old.raiseTicks());
        assertFalse(old.equals(a));
    }

    @Test
    void stateMovesInStepWithTheChain() {
        // the state's ramp is the position along the chain: with this trip's times it lands on the last tick
        ShipAnchor a = new ShipAnchor(AnchorState.RAISED.drop(), Vec3.ZERO, BlockPos.ZERO, Vec3.ZERO, 25, 60);
        var p = a.travelParams(com.richardsenger.piratesnships.sailing.force.SailingParams.AnchorParams.DEFAULTS);
        AnchorState s = a.state();
        for (int i = 0; i < 24; i++) {
            s = s.tick(p);
            assertEquals(AnchorState.Phase.DROPPING, s.phase(), "landed early at tick " + (i + 1));
        }
        assertEquals(AnchorState.Phase.HOLDING, s.tick(p).phase());
    }
}

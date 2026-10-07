package com.richardsenger.piratesnships.crew.hammock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.crew.hammock.HammockRules.Support;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.junit.jupiter.api.Test;

class HammockRulesTest {

    private static final BlockPos FOOT = new BlockPos(10, 64, -3);

    /** Supports at the given positions, each with the face it was asked about recorded. */
    private static final class World {
        final Map<BlockPos, Support> supports = new HashMap<>();
        final Map<BlockPos, Direction> askedFace = new HashMap<>();

        Support at(BlockPos p, Direction face) {
            askedFace.put(p, face);
            return supports.getOrDefault(p, Support.NONE);
        }
    }

    @Test
    void headLiesOneBlockFurtherInFacingForEveryFacing() {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            assertEquals(FOOT.relative(d), HammockRules.head(FOOT, d), d.toString());
            assertEquals(FOOT, HammockRules.foot(FOOT.relative(d), BedPart.HEAD, d), d.toString());
            assertEquals(FOOT, HammockRules.foot(FOOT, BedPart.FOOT, d), d.toString());
        }
    }

    @Test
    void bothHalvesFindEachOtherAndTheirOwnSupport() {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos head = HammockRules.head(FOOT, d);
            assertEquals(head, HammockRules.otherHalf(FOOT, BedPart.FOOT, d));
            assertEquals(FOOT, HammockRules.otherHalf(head, BedPart.HEAD, d));
            assertEquals(FOOT.relative(d.getOpposite()), HammockRules.supportOf(FOOT, BedPart.FOOT, d), "foot support " + d);
            assertEquals(head.relative(d), HammockRules.supportOf(head, BedPart.HEAD, d), "head support " + d);
            assertEquals(d.getOpposite(), HammockRules.outward(BedPart.FOOT, d));
            assertEquals(d, HammockRules.outward(BedPart.HEAD, d));
        }
    }

    @Test
    void hangsOnlyWithASupportAtBothEndsForEveryFacing() {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos behindFoot = FOOT.relative(d.getOpposite());
            BlockPos beyondHead = FOOT.relative(d, 2);
            for (Support a : Support.values()) {
                for (Support b : Support.values()) {
                    World w = new World();
                    w.supports.put(behindFoot, a);
                    w.supports.put(beyondHead, b);
                    boolean expected = a != Support.NONE && b != Support.NONE;
                    assertEquals(expected, HammockRules.canHang(FOOT, d, w::at), d + ": " + a + " / " + b);
                }
            }
        }
    }

    @Test
    void supportsAreAskedForTheFaceThatPointsAtTheHammock() {
        World w = new World();
        w.supports.put(FOOT.west(), Support.SOLID);
        w.supports.put(FOOT.east(2), Support.POST);
        assertTrue(HammockRules.canHang(FOOT, Direction.EAST, w::at));
        assertEquals(Direction.EAST, w.askedFace.get(FOOT.west()), "the wall behind the foot shows its east face");
        assertEquals(Direction.WEST, w.askedFace.get(FOOT.east(2)), "the post beyond the head shows its west face");
    }

    @Test
    void supportsBelowOrBesideDoNotCount() {
        World w = new World();
        w.supports.put(FOOT.below(), Support.SOLID);
        w.supports.put(FOOT.north().below(), Support.SOLID);
        w.supports.put(FOOT.east(), Support.POST);
        w.supports.put(FOOT.west(), Support.POST);
        assertFalse(HammockRules.canHang(FOOT, Direction.NORTH, w::at), "floor and side posts must not hold a north-facing hammock");
    }

    @Test
    void verticalFacingNeverHangs() {
        World w = new World();
        for (Direction d : new Direction[] {Direction.UP, Direction.DOWN}) {
            w.supports.put(FOOT.relative(d.getOpposite()), Support.SOLID);
            w.supports.put(FOOT.relative(d, 2), Support.SOLID);
            assertFalse(HammockRules.canHang(FOOT, d, w::at), d.toString());
        }
    }

    @Test
    void aHalfSurvivesOnlyWithItsPartnerAndItsSupport() {
        assertTrue(HammockRules.survives(true, Support.POST));
        assertTrue(HammockRules.survives(true, Support.SOLID));
        assertFalse(HammockRules.survives(true, Support.NONE), "support broken");
        assertFalse(HammockRules.survives(false, Support.POST), "other half broken");
        assertTrue(Support.POST.holds() && Support.SOLID.holds() && !Support.NONE.holds());
    }
}

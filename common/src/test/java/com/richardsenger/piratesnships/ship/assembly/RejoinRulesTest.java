package com.richardsenger.piratesnships.ship.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RejoinRulesTest {

    private static final UUID ORIGIN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static final RejoinTransform.Fit ALIGNED = new RejoinTransform.Fit(new RejoinTransform(0, BlockPos.ZERO, BlockPos.ZERO), 1, 1, 0.1);
    private static final RejoinTransform.Fit TURNED = new RejoinTransform.Fit(new RejoinTransform(0, BlockPos.ZERO, BlockPos.ZERO), 0, 30, 0.1);
    private static final RejoinRules.Contact TOUCHING = new RejoinRules.Contact(1, true, false);
    private static final RejoinRules.Contact BRIDGE = new RejoinRules.Contact(2, false, false);
    private static final RejoinRules.Contact FAR = new RejoinRules.Contact(3, false, false);
    private static final RejoinRules.Contact OVERLAPPING = new RejoinRules.Contact(0, true, true);

    private static RejoinRules.Check check(UUID a, UUID b, int blocks, RejoinTransform.Fit fit, RejoinRules.Contact contact, int nails) {
        return RejoinRules.check(a, b, blocks, 200, () -> fit, 5, 0.5, () -> contact, nails, 4);
    }

    /** A 3×1×1 bar of keeper cells along x from x = 0. */
    private static Set<BlockPos> keeper() {
        return new HashSet<>(List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)));
    }

    @Test
    void allChecksPass() {
        assertEquals(RejoinRules.Check.OK, check(ORIGIN, ORIGIN, 46, ALIGNED, TOUCHING, 4));
    }

    @Test
    void differentOriginsRefuse() {
        assertEquals(RejoinRules.Check.DIFFERENT_SHIP, check(ORIGIN, OTHER, 46, ALIGNED, TOUCHING, 4));
        assertEquals(RejoinRules.Check.DIFFERENT_SHIP, check(null, ORIGIN, 46, ALIGNED, TOUCHING, 4));
        assertEquals(RejoinRules.Check.DIFFERENT_SHIP, check(ORIGIN, null, 46, ALIGNED, TOUCHING, 4));
    }

    @Test
    void refusalOrderIsOriginSizeAlignmentContactOverlapNails() {
        // every check fails: the first one wins, then each in turn once the earlier ones pass
        assertEquals(RejoinRules.Check.DIFFERENT_SHIP, check(ORIGIN, OTHER, 500, TURNED, FAR, 0));
        assertEquals(RejoinRules.Check.TOO_BIG, check(ORIGIN, ORIGIN, 500, TURNED, FAR, 0));
        assertEquals(RejoinRules.Check.NOT_ALIGNED, check(ORIGIN, ORIGIN, 200, TURNED, FAR, 0));
        assertEquals(RejoinRules.Check.TOO_FAR, check(ORIGIN, ORIGIN, 200, ALIGNED, FAR, 0));
        assertEquals(RejoinRules.Check.ADD_PLANKS, check(ORIGIN, ORIGIN, 200, ALIGNED, BRIDGE, 0));
        assertEquals(RejoinRules.Check.OVERLAP, check(ORIGIN, ORIGIN, 200, ALIGNED, OVERLAPPING, 0));
        assertEquals(RejoinRules.Check.NO_NAILS, check(ORIGIN, ORIGIN, 200, ALIGNED, TOUCHING, 3));
        assertEquals(RejoinRules.Check.OK, check(ORIGIN, ORIGIN, 200, ALIGNED, TOUCHING, 4));
    }

    @Test
    void laterChecksAreNotComputedAfterARefusal() {
        Supplier<RejoinTransform.Fit> noFit = () -> {
            throw new AssertionError("fit computed after a refusal");
        };
        Supplier<RejoinRules.Contact> noContact = () -> {
            throw new AssertionError("contact computed after a refusal");
        };
        assertEquals(RejoinRules.Check.TOO_BIG, RejoinRules.check(ORIGIN, ORIGIN, 201, 200, noFit, 5, 0.5, noContact, 4, 4));
        assertEquals(RejoinRules.Check.NOT_ALIGNED, RejoinRules.check(ORIGIN, ORIGIN, 10, 200, () -> TURNED, 5, 0.5, noContact, 4, 4));
    }

    @Test
    void sizeLimitIsInclusive() {
        assertEquals(RejoinRules.Check.OK, check(ORIGIN, ORIGIN, 200, ALIGNED, TOUCHING, 4));
        assertEquals(RejoinRules.Check.TOO_BIG, check(ORIGIN, ORIGIN, 201, ALIGNED, TOUCHING, 4));
    }

    @Test
    void nailCost() {
        assertTrue(RejoinRules.hasNails(4, 4));
        assertFalse(RejoinRules.hasNails(3, 4));
        assertTrue(RejoinRules.hasNails(0, 0));
        assertEquals(4, RejoinRules.nailsToTake(4, false));
        assertEquals(0, RejoinRules.nailsToTake(4, true));
        assertEquals(RejoinRules.Check.OK, check(ORIGIN, ORIGIN, 10, ALIGNED, TOUCHING, Integer.MAX_VALUE)); // creative
    }

    @Test
    void faceContactIsDetected() {
        RejoinRules.Contact c = RejoinRules.contact(keeper(), List.of(new BlockPos(3, 0, 0), new BlockPos(4, 0, 0)));
        assertEquals(1, c.distance());
        assertTrue(c.touching());
        assertFalse(c.overlap());
        // on top of the bar also touches
        assertTrue(RejoinRules.contact(keeper(), List.of(new BlockPos(1, 1, 0))).touching());
    }

    @Test
    void edgeContactIsNotEnough() {
        // diagonal neighbour: shares an edge, not a face; one plank in the corner would join them
        RejoinRules.Contact c = RejoinRules.contact(keeper(), List.of(new BlockPos(3, 0, 1)));
        assertFalse(c.touching());
        assertEquals(2, c.distance());
    }

    @Test
    void oneBlockGapAsksForPlanksWiderGapForCloser() {
        assertEquals(2, RejoinRules.contact(keeper(), List.of(new BlockPos(4, 0, 0), new BlockPos(5, 0, 0))).distance());
        assertEquals(3, RejoinRules.contact(keeper(), List.of(new BlockPos(5, 0, 0), new BlockPos(6, 0, 0))).distance());
        assertEquals(3, RejoinRules.contact(keeper(), List.of(new BlockPos(3, 0, 2))).distance());
    }

    @Test
    void overlapIsDetected() {
        RejoinRules.Contact c = RejoinRules.contact(keeper(), List.of(new BlockPos(2, 0, 0), new BlockPos(3, 0, 0)));
        assertTrue(c.overlap());
        assertEquals(0, c.distance());
        assertEquals(RejoinRules.Check.OVERLAP, check(ORIGIN, ORIGIN, 2, ALIGNED, c, 4));
    }

    @Test
    void seamIsTheKeeperBlocksNearTheOtherPieceNearestFirst() {
        List<Vec3> keeper = List.of(new Vec3(0, 0, 0), new Vec3(5, 0, 0), new Vec3(9, 0, 0), new Vec3(20, 0, 0));
        List<Vec3> other = List.of(new Vec3(11, 0, 0), new Vec3(12, 0, 0));
        assertEquals(List.of(2, 1), RejoinRules.seam(keeper, other, 6, 10));
        assertEquals(List.of(2), RejoinRules.seam(keeper, other, 6, 1));
        assertEquals(List.of(), RejoinRules.seam(keeper, List.of(), 6, 10));
    }
}

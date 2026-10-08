package com.richardsenger.piratesnships.station.capstan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.crew.npc.StationPoses;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.ship.ShipControls.AnchorResult;
import com.richardsenger.piratesnships.station.order.CrewOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** The capstan station's pure rules (CRW3): work times, the pose, the orders. */
class CapstanStationTest {

    @Test
    void dropWorksWhileTheAnchorCanGoNothingWhenOutUnableOtherwise() {
        assertEquals(40, CapstanStation.dropTicks(AnchorResult.DROPPING, 40));
        assertEquals(1, CapstanStation.dropTicks(AnchorResult.DROPPING, 0), "a drop that can go always has work");
        assertEquals(0, CapstanStation.dropTicks(AnchorResult.ALREADY_OUT, 40));
        for (AnchorResult r : new AnchorResult[]{AnchorResult.NO_GROUND, AnchorResult.DISABLED, AnchorResult.NOT_ON_SHIP}) {
            assertTrue(CapstanStation.dropTicks(r, 40) < 0, r + " must be unable");
        }
    }

    @Test
    void raiseLastsTheWindingAtLeastTheMinimumAndIsUnableWhenStowed() {
        // 8 blocks of chain at 2.5 blocks/s: 64 ticks
        assertEquals(64, CapstanStation.raiseTicks(AnchorState.Phase.HOLDING, 8 / 2.5 * 20, 20));
        assertEquals(65, CapstanStation.raiseTicks(AnchorState.Phase.DROPPING, 64.2, 20), "rounded up");
        assertEquals(20, CapstanStation.raiseTicks(AnchorState.Phase.RAISING, 3.0, 20), "at least min_raise_ticks");
        assertEquals(20, CapstanStation.raiseTicks(AnchorState.Phase.HOLDING, Double.NaN, 20), "no estimate: the minimum");
        assertTrue(CapstanStation.raiseTicks(AnchorState.Phase.RAISED, 64, 20) < 0);
        assertTrue(CapstanStation.raiseTicks(null, 64, 20) < 0, "no ship");
    }

    @Test
    void pushingFacesTheCapstanOnlyWhileWorking() {
        BlockPos capstan = new BlockPos(3, 9, 6);
        StationPoses.Shown s = CapstanPoses.shown(true, capstan.east(), capstan);
        assertEquals(CrewPose.CAPSTAN_PUSH, s.pose());
        assertEquals(Direction.WEST, s.facing());
        assertNull(CapstanPoses.shown(false, capstan.east(), capstan));
        assertNull(CapstanPoses.shown(true, capstan.above(), capstan).facing(), "on top of the drum: no facing");
    }

    @Test
    void anchorOrdersAreCrewOrders() {
        assertEquals(AnchorOrder.DROP_ANCHOR, CrewOrder.byId("drop_anchor").orElseThrow());
        assertEquals(AnchorOrder.RAISE_ANCHOR, CrewOrder.byId("raise_anchor").orElseThrow());
        assertTrue(CapstanStation.INSTANCE.accepts(AnchorOrder.DROP_ANCHOR));
        assertTrue(!CapstanStation.INSTANCE.accepts(com.richardsenger.piratesnships.station.pump.PumpOrder.PUMP));
        assertEquals("capstan", CapstanStation.INSTANCE.id());
    }
}

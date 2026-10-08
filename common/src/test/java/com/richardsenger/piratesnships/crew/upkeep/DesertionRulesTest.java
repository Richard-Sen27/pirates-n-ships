package com.richardsenger.piratesnships.crew.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.crew.upkeep.DesertionRules.Leave;
import com.richardsenger.piratesnships.crew.upkeep.DesertionRules.Mark;
import com.richardsenger.piratesnships.crew.upkeep.DesertionRules.Settings;
import com.richardsenger.piratesnships.world.port.Berth;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** When deserters leave (CRW2). */
class DesertionRulesTest {

    private static final Settings S = Settings.DEFAULTS;

    @Test
    void defaultsMatchTheConfig() {
        assertEquals(new Settings(true, 48, 3), S);
    }

    @Test
    void aDeserterIsMarkedNotGone() {
        assertFalse(DesertionRules.leavesAtOnce(S));
        assertEquals(new Mark(true, 0), DesertionRules.afterDawn(Mark.NONE, true, 2));
        assertEquals(Mark.NONE, DesertionRules.afterDawn(Mark.NONE, false, 1), "one low dawn is not desertion yet");
    }

    @Test
    void atPortOnlyOffLeavesAtOnce() {
        Settings off = new Settings(false, 48, 3);
        assertTrue(DesertionRules.leavesAtOnce(off));
        assertEquals(Leave.ANYWHERE, DesertionRules.decide(off, false, 0));
        assertEquals(Leave.AT_PORT, DesertionRules.decide(off, true, 0), "at a port it still uses the quay");
    }

    @Test
    void markedDaysCountAndAFineMoraleClearsTheMark() {
        Mark m = new Mark(true, 0);
        m = DesertionRules.afterDawn(m, true, 3);
        assertEquals(new Mark(true, 1), m);
        m = DesertionRules.afterDawn(m, true, 4);
        assertEquals(new Mark(true, 2), m);
        assertEquals(Mark.NONE, DesertionRules.afterDawn(m, false, 0), "morale fine again: it stays aboard");
    }

    @Test
    void leavesOnlyAtAPortUntilItHasWaitedLongEnough() {
        assertEquals(Leave.STAY, DesertionRules.decide(S, false, 0));
        assertEquals(Leave.STAY, DesertionRules.decide(S, false, 2));
        assertEquals(Leave.AT_PORT, DesertionRules.decide(S, true, 0));
        assertEquals(Leave.ANYWHERE, DesertionRules.decide(S, false, 3));
        assertEquals(Leave.AT_PORT, DesertionRules.decide(S, true, 7));
        assertEquals(Leave.ANYWHERE, DesertionRules.decide(new Settings(true, 48, 0), false, 0), "0 days: at once");
    }

    @Test
    void aShipWithinTheRadiusOfTheBoxIsAtThePort() {
        BoundingBox box = new BoundingBox(0, 60, 0, 10, 70, 10);
        AABB near = new AABB(50, 62, 0, 58, 66, 4);   // 40 blocks east of the box
        AABB far = new AABB(70, 62, 0, 78, 66, 4);    // 60 blocks east
        AABB inside = new AABB(2, 62, 2, 6, 66, 6);
        assertTrue(DesertionRules.atPort(box, 48, near));
        assertFalse(DesertionRules.atPort(box, 48, far));
        assertFalse(DesertionRules.atPort(box, 0, near));
        assertTrue(DesertionRules.atPort(box, 0, inside));
    }

    @Test
    void nearestBerthIsHorizontal() {
        Berth a = new Berth(new BlockPos(0, 62, 0), Direction.NORTH);
        Berth b = new Berth(new BlockPos(20, 10, 0), Direction.NORTH);
        assertEquals(a, DesertionRules.nearestBerth(List.of(a, b), new Vec3(8, 200, 0)).orElseThrow());
        assertEquals(b, DesertionRules.nearestBerth(List.of(a, b), new Vec3(12, 62, 0)).orElseThrow());
        assertTrue(DesertionRules.nearestBerth(List.of(), Vec3.ZERO).isEmpty());
    }
}

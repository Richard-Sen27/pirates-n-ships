package com.richardsenger.piratesnships.combat.cannon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** The pure rules of crew loading (C9): need, range, the take plan over partial stacks, timing, auto reload. */
class CrewSupplyRulesTest {

    @Test
    void anEmptyGunNeedsPowderAndAmmoAPowderedOneOnlyAmmoALoadedOneNothing() {
        assertEquals(new CrewSupplyRules.Need(1, 1), CrewSupplyRules.need(CannonLoad.EMPTY, 1));
        assertEquals(new CrewSupplyRules.Need(1, 3), CrewSupplyRules.need(CannonLoad.EMPTY, 3));
        assertEquals(new CrewSupplyRules.Need(0, 3), CrewSupplyRules.need(CannonLoad.POWDER, 3));
        assertTrue(CrewSupplyRules.need(CannonLoad.LOADED, 3).nothing());
        assertFalse(CrewSupplyRules.need(CannonLoad.POWDER, 1).nothing());
    }

    @Test
    void rangeIsAStraightLineFromTheGunInclusive() {
        BlockPos gun = new BlockPos(10, 64, 10);
        assertTrue(CrewSupplyRules.inRange(gun, new BlockPos(14, 64, 10), 4), "4 blocks along x");
        assertFalse(CrewSupplyRules.inRange(gun, new BlockPos(15, 64, 10), 4), "5 blocks along x");
        assertTrue(CrewSupplyRules.inRange(gun, new BlockPos(12, 65, 12), 4), "a diagonal of 3");
        assertFalse(CrewSupplyRules.inRange(gun, new BlockPos(13, 64, 13), 4), "a diagonal of 4.24");
        assertTrue(CrewSupplyRules.inRange(gun, new BlockPos(10, 60, 10), 4), "below deck");
        assertTrue(CrewSupplyRules.inRange(gun, gun, 0));
        assertFalse(CrewSupplyRules.inRange(gun, new BlockPos(11, 64, 10), 0), "range 0 reaches nothing beside the gun");
        assertFalse(CrewSupplyRules.inRange(gun, gun, -1));
    }

    @Test
    void theTakePlanUsesTheEarlierStacksFirstAndHandlesPartialStacks() {
        assertArrayEquals(new int[]{1, 0, 0}, CrewSupplyRules.takePlan(new int[]{5, 64, 1}, 1));
        assertArrayEquals(new int[]{2, 1, 0}, CrewSupplyRules.takePlan(new int[]{2, 3, 4}, 3), "a partial stack then the next");
        assertArrayEquals(new int[]{1, 1, 1}, CrewSupplyRules.takePlan(new int[]{1, 1, 1}, 3), "exactly all");
        assertArrayEquals(new int[]{0, 2}, CrewSupplyRules.takePlan(new int[]{0, 5}, 2), "an empty count is skipped");
        assertArrayEquals(new int[]{0, 0}, CrewSupplyRules.takePlan(new int[]{3, 3}, 0), "nothing needed");
        assertArrayEquals(new int[0], CrewSupplyRules.takePlan(new int[0], 0));
        assertNull(CrewSupplyRules.takePlan(new int[]{1, 1}, 3), "short by one");
        assertNull(CrewSupplyRules.takePlan(new int[0], 1), "no stacks");
        assertTrue(CrewSupplyRules.covers(new int[]{2, 1}, 3));
        assertFalse(CrewSupplyRules.covers(new int[]{2}, 3));
    }

    @Test
    void aLoadTakesTheWorkTimeButNotLessThanTheRestOfTheCooldown() {
        assertEquals(80, CrewSupplyRules.loadTicks(80, 0));
        assertEquals(80, CrewSupplyRules.loadTicks(80, 50));
        assertEquals(100, CrewSupplyRules.loadTicks(80, 100));
        assertEquals(1, CrewSupplyRules.loadTicks(0, 0), "at least one tick");
        assertEquals(Integer.MAX_VALUE, CrewSupplyRules.loadTicks(1, Long.MAX_VALUE));
    }

    @Test
    void fireDuringALoadWaitsForTheLoadThenTheFuse() {
        assertEquals(30 + CannonStation.FUSE_TICKS, CrewSupplyRules.fireAfterLoad(30, CannonStation.FUSE_TICKS));
        assertEquals(CannonStation.FUSE_TICKS, CrewSupplyRules.fireAfterLoad(-5, CannonStation.FUSE_TICKS));
    }

    @Test
    void theCrewReloadsOnlyAfterItsShotWithCrewLoadingAndAutoReloadOn() {
        assertTrue(CrewSupplyRules.reloadAfterShot(true, true, true));
        assertFalse(CrewSupplyRules.reloadAfterShot(true, true, false), "no shot");
        assertFalse(CrewSupplyRules.reloadAfterShot(true, false, true), "auto_reload off");
        assertFalse(CrewSupplyRules.reloadAfterShot(false, true, true), "crew loading off");
    }
}

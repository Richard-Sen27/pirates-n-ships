package com.richardsenger.piratesnships.worldsim.materialize;

import com.richardsenger.piratesnships.law.flag.Faction;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GunStockingTest {

    @Test
    void navyAndPiratesStockMerchantsDoNot() {
        assertTrue(GunStocking.stocks(Faction.NAVY));
        assertTrue(GunStocking.stocks(Faction.PIRATES));
        assertFalse(GunStocking.stocks(Faction.MERCHANTS));
    }

    /** The armed sloop: four guns, one locker in the hold within 4 blocks of all of them, 12 rounds each. */
    @Test
    void armedSloopPutsAllRoundsInItsOneLocker() {
        List<BlockPos> guns = List.of(new BlockPos(1, 5, 13), new BlockPos(1, 5, 15), new BlockPos(7, 5, 13), new BlockPos(7, 5, 15));
        int[] of = GunStocking.lockerOf(guns, List.of(new BlockPos(4, 3, 14)), 4);
        assertArrayEquals(new int[] {0, 0, 0, 0}, of);
        assertArrayEquals(new int[] {48}, GunStocking.roundsPerLocker(of, 1, 12));
    }

    @Test
    void eachGunUsesItsNearestLockerInReach() {
        List<BlockPos> guns = List.of(new BlockPos(0, 0, 0), new BlockPos(10, 0, 0), new BlockPos(30, 0, 0));
        List<BlockPos> lockers = List.of(new BlockPos(3, 0, 0), new BlockPos(1, 0, 0), new BlockPos(13, 0, 0));
        int[] of = GunStocking.lockerOf(guns, lockers, 4);
        assertArrayEquals(new int[] {1, 2, GunStocking.NO_LOCKER}, of);
        assertArrayEquals(new int[] {0, 5, 5}, GunStocking.roundsPerLocker(of, 3, 5));
    }

    @Test
    void reachIsAStraightLineAndInclusive() {
        List<BlockPos> guns = List.of(new BlockPos(0, 0, 0));
        assertEquals(0, GunStocking.lockerOf(guns, List.of(new BlockPos(0, 4, 0)), 4)[0]);
        assertEquals(GunStocking.NO_LOCKER, GunStocking.lockerOf(guns, List.of(new BlockPos(3, 3, 0)), 4)[0]); // 4.24
        assertEquals(GunStocking.NO_LOCKER, GunStocking.lockerOf(guns, List.of(), 4)[0]);
        assertEquals(GunStocking.NO_LOCKER, GunStocking.lockerOf(guns, List.of(new BlockPos(0, 0, 0)), -1)[0]);
    }

    @Test
    void tiesGoToTheEarlierLocker() {
        int[] of = GunStocking.lockerOf(List.of(new BlockPos(0, 0, 0)), List.of(new BlockPos(2, 0, 0), new BlockPos(-2, 0, 0)), 4);
        assertEquals(0, of[0]);
    }

    @Test
    void noRoundsGiveNothing() {
        assertArrayEquals(new int[] {0}, GunStocking.roundsPerLocker(new int[] {0, 0}, 1, 0));
        assertArrayEquals(new int[] {0}, GunStocking.roundsPerLocker(new int[] {0}, 1, -3));
    }

    @Test
    void stacksSplitByMaximum() {
        assertEquals(List.of(16, 16, 16), GunStocking.stacks(48, 16));
        assertEquals(List.of(64, 4), GunStocking.stacks(68, 64));
        assertEquals(List.of(), GunStocking.stacks(0, 16));
    }

    /** A ship heading north (-z) with guns on both sides; the quarry to the east: the east guns are manned first. */
    @Test
    void gunsBearingOnTheQuarryAreMannedFirst() {
        List<GunStocking.Gun> guns = List.of(
                new GunStocking.Gun(-2, 0, -1, 0),  // port, facing west
                new GunStocking.Gun(2, 0, 1, 0),    // starboard, facing east
                new GunStocking.Gun(-2, 2, -1, 0),
                new GunStocking.Gun(2, 2, 1, 0));
        List<Integer> order = GunStocking.manningOrder(guns, 30, 1);
        assertEquals(List.of(1, 3), order.subList(0, 2).stream().sorted().toList());
        assertEquals(List.of(0, 2), order.subList(2, 4).stream().sorted().toList());
        // the quarry to the west: the other way round
        List<Integer> west = GunStocking.manningOrder(guns, -30, 1);
        assertEquals(List.of(0, 2), west.subList(0, 2).stream().sorted().toList());
    }
}

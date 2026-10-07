package com.richardsenger.piratesnships.crew.npc;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/**
 * The server side of the crew member's animation (M1): the synced "working" flag the client's animation controller
 * reads is set while the crew member's station carries out an order and cleared when it is done. Which animation then
 * plays ({@link CrewPose}) is chosen on the client and covered by JUnit.
 */
public final class CrewPoseGameTests {

    private static final int STEP = 10;
    /**
     * Distance of the checks from the expected flag changes. The order starts in a test callback in server tick T
     * (the GameTest ticker runs after the level ticks); the station counts it down at the end of each level tick
     * ({@code Stations.onLevelTick}, ticks T+1..T+20) and the crew member copies the phase into the flag in its entity
     * tick, which comes before that, so the flag is set in T+1 and cleared in T+21. The checks at T+3 and T+2·STEP+6
     * keep 2 and 5 ticks to those; nothing on this path depends on wall-clock time or on a check interval.
     */
    private static final int MARGIN = 3;
    /*
     * Each test has its own batch. Tests of one batch run at the same time but do not start on the same tick (each
     * waits for its chunks, which load asynchronously), and ConfigOverrides restores a value to what it was when that
     * test set it. With both tests in one batch, the release test (started about 6 ticks earlier) restored the outer
     * ticks_per_trim_step (40) around the moment the other test started; under CPU load the other test took its
     * override before that restore and gave its order after it, so the hoist took 80 ticks instead of 20 (Q3).
     */
    private static final String BATCH = "pirates_n_ships_config_crew_npc_pose_";

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CrewPoseGameTests.class);
    }

    /** A 5×4×5 plank hull floating in a 40×40 basin, a furled square sail amidships and a winch on deck; returns the winch. */
    private static BlockPos shipWithWinch(GameTestHelper h) {
        SailingGameTestsShips.openSky(h, 40);
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 39 || z == 0 || z == 39;
                for (int y = 2; y <= 8; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : y <= 7 ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
        int x0 = 17, z0 = 17;
        for (int x = x0; x <= x0 + 4; x++) {
            for (int z = z0; z <= z0 + 4; z++) {
                for (int y = 5; y <= 8; y++) {
                    boolean shell = y == 5 || y == 8 || x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
                    h.setBlock(new BlockPos(x, y, z), shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
        BlockPos helm = new BlockPos(x0 + 2, 9, z0 + 1);
        h.setBlock(helm, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        SailingGameTestsShips.rig(h, x0 + 2, z0 + 2, 1, 3, SailTrim.FURLED);
        h.setBlock(new BlockPos(x0 + 1, 9, z0 + 3), SailingBlocks.SAIL_WINCH.get());
        AssemblyResult r = ShipTestCleanup.assemble(h, helm);
        if (r.shipId() == null) throw new AssertionError("assembly failed: " + r);
        ShipBody ship = SableShips.byId(h.getLevel(), r.shipId());
        if (ship == null) throw new AssertionError("no ship after assembly");
        return ship.plotBlocks().stream().filter(p -> h.getLevel().getBlockState(p).is(SailingBlocks.SAIL_WINCH.get())).findFirst()
                .orElseThrow(() -> new AssertionError("no winch on the ship"));
    }

    /** Hoisting from furled takes 2 steps × 10 ticks: working during the order, not before and not after. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "working")
    public static void workingFlagFollowsTheOrder(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.ENABLED, true);
        ConfigOverrides.during(h, StationConfig.TICKS_PER_TRIM_STEP, STEP);
        ConfigOverrides.during(h, StationConfig.SEAT_CHECK_INTERVAL, 5);
        BlockPos winch = shipWithWinch(h);
        CrewMember c = h.spawn(StationContent.CREW_MEMBER.get(), new BlockPos(2, 9, 2));
        h.assertTrue(CrewStations.assign(h.getLevel(), c, winch) == CrewStations.AssignResult.ASSIGNED, "not assigned");
        h.assertTrue(c.isAtStation(), "not at the station");
        h.assertFalse(c.isSeated(), "the station seat must not count as seated (the crew member stands there)");
        h.runAfterDelay(2, () -> {
            h.assertFalse(c.isWorking(), "working without an order");
            h.assertTrue(c.pose(false) == CrewPose.IDLE, "pose at the station without an order: " + c.pose(false));
            h.assertTrue(CrewStations.order(h.getLevel(), c, SailOrder.HOIST) == Stations.OrderResult.STARTED, "hoist not started");
        });
        h.runAfterDelay(2 + MARGIN, () -> {
            h.assertTrue(c.isWorking(), "not working while the hoist runs");
            h.assertTrue(c.pose(false) == CrewPose.WORK, "pose while hoisting: " + c.pose(false));
        });
        h.runAfterDelay(2 + 2 * STEP + 2 * MARGIN, () -> {
            h.assertFalse(c.isWorking(), "still working after the hoist was done");
            h.assertTrue(c.isAtStation(), "left the station");
            c.discard();
            h.succeed();
        });
    }

    /** Released from its station, the crew member stops working at once even if an order was running. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "release")
    public static void releaseStopsWorking(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.ENABLED, true);
        ConfigOverrides.during(h, StationConfig.TICKS_PER_TRIM_STEP, STEP);
        ConfigOverrides.during(h, StationConfig.SEAT_CHECK_INTERVAL, 5);
        BlockPos winch = shipWithWinch(h);
        CrewMember c = h.spawn(StationContent.CREW_MEMBER.get(), new BlockPos(2, 9, 2));
        h.assertTrue(CrewStations.assign(h.getLevel(), c, winch) == CrewStations.AssignResult.ASSIGNED, "not assigned");
        h.assertTrue(CrewStations.order(h.getLevel(), c, SailOrder.HOIST) == Stations.OrderResult.STARTED, "hoist not started");
        h.runAfterDelay(MARGIN, () -> {
            h.assertTrue(c.isWorking(), "not working while the hoist runs");
            CrewStations.release(h.getLevel(), c);
        });
        h.runAfterDelay(2 * MARGIN, () -> {
            h.assertFalse(c.isWorking(), "still working after the release");
            h.assertTrue(c.pose(false) == CrewPose.IDLE, "pose after the release: " + c.pose(false));
            c.discard();
            h.succeed();
        });
    }
}

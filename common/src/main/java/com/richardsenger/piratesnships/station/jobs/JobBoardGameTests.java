package com.richardsenger.piratesnships.station.jobs;

import com.mojang.authlib.GameProfile;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.ship.hull.pump.HullRepairContent;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationGameTests;
import com.richardsenger.piratesnships.station.StationGameTests.Fixture;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import com.richardsenger.piratesnships.station.order.WhistleOrders;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * The job board of CR1 (docs/design.md §7.2 "Job board"): a ship-wide order turns unmanned stations into open jobs
 * that free crew on board claim by themselves. The ship is the 5×4×5 hull of {@link StationGameTests} resting on land
 * (deck top at relative y = 9, a furled square sail, the winch at relative (18, 9, 20)); some tests add a second
 * station on the deck. Crew members stand on the deck without AI so that they cannot stroll off the small deck before
 * the board's pass (the board does not care whether they move). Tests that wait for the sails pin
 * {@code ticks_per_trim_step} and therefore run in config batches of their own.
 */
public final class JobBoardGameTests {

    private static final int STEP = 10;
    private static final int MARGIN = 5;
    /** The default claim interval: the board passes every 20 ticks. */
    private static final int CLAIM = 20;
    private static final String BATCH = "pirates_n_ships_config_station_jobs_";

    private JobBoardGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(JobBoardGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** A mock captain that keeps the action-bar lines it is shown. */
    private static final class Captain extends Player {
        final List<Component> actionBar = new ArrayList<>();

        Captain(ServerLevel level) {
            super(level, BlockPos.ZERO, 0f, new GameProfile(UUID.randomUUID(), "test-captain"));
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public void displayClientMessage(Component message, boolean overlay) {
            if (overlay) actionBar.add(message);
        }

        long count(String key) {
            return actionBar.stream().filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals(key)).count();
        }
    }

    /** The captain on the deck next to the helm with a whistle in hand. */
    private static Captain captain(GameTestHelper h, Fixture f) {
        Captain p = new Captain(h.getLevel());
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StationContent.CAPTAINS_WHISTLE.get()));
        Vec3 deck = f.ship().toWorld(Vec3.atBottomCenterOf(f.helm()).add(1, 0, 1));
        p.moveTo(deck.x, deck.y, deck.z);
        return p;
    }

    /** A crew member standing on the deck above the plot cell {@code plot} (y = deck top), without AI. */
    private static CrewMember onDeck(GameTestHelper h, Fixture f, BlockPos plot) {
        CrewMember c = StationContent.CREW_MEMBER.get().create(h.getLevel());
        if (c == null) throw new AssertionError("no crew member");
        Vec3 p = f.ship().toWorld(Vec3.atBottomCenterOf(plot));
        c.moveTo(p.x, p.y, p.z, 0, 0);
        c.setNoAi(true);
        h.getLevel().addFreshEntity(c);
        return c;
    }

    /** The plot position of the {@code block} station nearest to the relative position {@code rel}. */
    private static BlockPos stationNear(GameTestHelper h, Fixture f, Block block, BlockPos rel) {
        Vec3 target = Vec3.atCenterOf(h.absolutePos(rel));
        return f.ship().plotBlocks().stream().filter(p -> h.getLevel().getBlockState(p).is(block))
                .min(Comparator.comparingDouble(p -> f.ship().toWorld(Vec3.atCenterOf(p)).distanceTo(target)))
                .orElseThrow(() -> new AssertionError("no " + block + " near " + rel));
    }

    /** The plot cell of the deck (top at y = 9) at relative (x, z). */
    private static BlockPos deck(GameTestHelper h, Fixture f, int x, int z) {
        // the helm stands on the deck at relative (19, 9, 18); the ship rests on land, so plot = helm + offset
        return f.helm().offset(x - 19, 0, z - 18);
    }

    private static void cleanup(CrewMember... crew) {
        for (CrewMember c : crew) c.discard();
    }

    // ------------------------------------------------------------------ tests

    /**
     * Nobody mans the winch: the whistle's "Hoist" posts it as an open job, the free crew member on deck claims it
     * within one claim interval (not pinned), and the sails are full after the work time.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "freecrewhoist")
    public static void freeCrewTakeTheHoistOrder(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.TICKS_PER_TRIM_STEP, STEP);
        Fixture f = StationGameTests.ship(h, false, x -> { });
        CrewMember c = onDeck(h, f, deck(h, f, 20, 20));
        Captain p = captain(h, f);
        WhistleOrders.Result r = WhistleOrders.handle(p, WhistleOrder.HOIST.id());
        h.assertTrue(r.outcome() == WhistleOrders.Outcome.ISSUED && r.crew() == 0 && r.jobs() == 1, "hoist: " + r);
        h.assertTrue(JobBoard.jobs(f.ship().id()).containsKey(f.winch()), "the unmanned winch is no open job");
        h.assertTrue(p.count(JobBoard.KEY_NO_FREE_HANDS) == 0, "a free hand is on deck, yet the captain heard 'no free hands'");
        h.assertTrue(c.assignment() == null, "the board assigned before its pass");
        h.runAfterDelay(CLAIM + 3, () -> {
            h.assertTrue(c.assignment() != null && c.assignment().pos().equals(f.winch()), "nobody claimed the winch: " + c.assignment());
            h.assertTrue(c.isAtStation(), "the claiming crew member is not seated at the winch");
            h.assertFalse(c.isPinned(), "a board-assigned crew member is pinned");
            h.assertTrue(JobBoard.jobs(f.ship().id()).isEmpty(), "the claimed job is still on the board");
        });
        h.runAfterDelay(CLAIM + 2 * STEP + MARGIN, () -> {
            h.assertTrue(StationGameTests.trim(h, f) == SailTrim.FULL, "sails not hoisted: " + StationGameTests.trim(h, f));
            h.assertTrue(c.isAtStation(), "the crew member left the winch after the order");
            cleanup(c);
            h.succeed();
        });
    }

    /**
     * A crew member put at the pump by hand is pinned: a hoist job stays open next to it instead of moving it; a free
     * crew member who comes aboard later takes the job, and the pinned one stays at the pump.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200)
    public static void pinnedCrewStayWhileAFreeHandTakesTheJob(GameTestHelper h) {
        Fixture f = StationGameTests.ship(h, false, x -> x.setBlock(new BlockPos(20, 9, 18), HullRepairContent.BILGE_PUMP.get()));
        ServerLevel level = h.getLevel();
        BlockPos pump = stationNear(h, f, HullRepairContent.BILGE_PUMP.get(), new BlockPos(20, 9, 18));
        CrewMember pinned = onDeck(h, f, deck(h, f, 20, 20));
        h.assertTrue(CrewStations.assign(level, pinned, pump) == CrewStations.AssignResult.ASSIGNED, "assign at the pump");
        h.assertTrue(pinned.isPinned(), "assigned by hand, but not pinned");
        StationRef pumpRef = pinned.assignment();
        WhistleOrders.Result r = WhistleOrders.handle(captain(h, f), WhistleOrder.HOIST.id());
        h.assertTrue(r.jobs() == 1, "hoist: " + r);
        CrewMember[] free = {null};
        h.runAfterDelay(CLAIM + 3, () -> {
            h.assertTrue(pumpRef.equals(pinned.assignment()) && pinned.isAtStation(), "the board moved the pinned crew member: " + pinned.assignment());
            h.assertTrue(JobBoard.jobs(f.ship().id()).containsKey(f.winch()), "the hoist job left the board unclaimed");
            free[0] = onDeck(h, f, deck(h, f, 18, 18));
        });
        h.runAfterDelay(2 * CLAIM + 6, () -> {
            CrewMember c = free[0];
            h.assertTrue(c.assignment() != null && c.assignment().pos().equals(f.winch()), "the free hand did not take the winch: " + c.assignment());
            h.assertTrue(pumpRef.equals(pinned.assignment()) && pinned.isAtStation(), "the pinned crew member left the pump");
            h.assertTrue(JobBoard.jobs(f.ship().id()).isEmpty(), "the job is still open");
            cleanup(c, pinned);
            h.succeed();
        });
    }

    /** Two winches are open jobs and one free hand is on deck: it takes the nearer winch, the other job stays open. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200)
    public static void aSingleFreeHandTakesTheNearerStation(GameTestHelper h) {
        Fixture f = StationGameTests.ship(h, false, x -> x.setBlock(new BlockPos(20, 9, 18), SailingBlocks.SAIL_WINCH.get()));
        BlockPos near = stationNear(h, f, SailingBlocks.SAIL_WINCH.get(), new BlockPos(20, 9, 18));
        BlockPos far = stationNear(h, f, SailingBlocks.SAIL_WINCH.get(), new BlockPos(18, 9, 20));
        h.assertFalse(near.equals(far), "expected two winches");
        CrewMember c = onDeck(h, f, deck(h, f, 21, 18));
        WhistleOrders.Result r = WhistleOrders.handle(captain(h, f), WhistleOrder.HOIST.id());
        h.assertTrue(r.jobs() == 2, "hoist: " + r);
        h.runAfterDelay(CLAIM + 3, () -> {
            h.assertTrue(c.assignment() != null && c.assignment().pos().equals(near), "took " + c.assignment() + ", the nearer winch is " + near);
            h.assertTrue(JobBoard.jobs(f.ship().id()).keySet().equals(java.util.Set.of(far)), "open jobs: " + JobBoard.jobs(f.ship().id()));
            JobBoard.clear(f.ship().id());
            cleanup(c);
            h.succeed();
        });
    }

    /** "Release crew" clears the board: a free hand coming aboard afterwards finds no job. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200)
    public static void releaseClearsTheBoard(GameTestHelper h) {
        Fixture f = StationGameTests.ship(h, false, x -> { });
        Captain p = captain(h, f);
        h.assertTrue(WhistleOrders.handle(p, WhistleOrder.HOIST.id()).jobs() == 1, "hoist posted no job");
        h.assertTrue(!JobBoard.jobs(f.ship().id()).isEmpty(), "the board is empty after hoist");
        WhistleOrders.Result rel = WhistleOrders.handle(p, WhistleOrder.RELEASE.id());
        h.assertTrue(rel.outcome() == WhistleOrders.Outcome.ISSUED, "release: " + rel);
        h.assertTrue(JobBoard.jobs(f.ship().id()).isEmpty(), "release left open jobs: " + JobBoard.jobs(f.ship().id()));
        CrewMember c = onDeck(h, f, deck(h, f, 20, 20));
        h.runAfterDelay(CLAIM + 3, () -> {
            h.assertTrue(c.assignment() == null, "a free hand took a released job: " + c.assignment());
            h.assertTrue(StationGameTests.trim(h, f) == SailTrim.FURLED, "the sails changed");
            cleanup(c);
            h.succeed();
        });
    }

    /** No crew on board: the captain hears "no free hands" once for the order, not again on the board's passes. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200)
    public static void noFreeHandsIsSaidOncePerOrder(GameTestHelper h) {
        Fixture f = StationGameTests.ship(h, false, x -> { });
        Captain p = captain(h, f);
        WhistleOrders.Result r = WhistleOrders.handle(p, WhistleOrder.HOIST.id());
        h.assertTrue(r.jobs() == 1, "hoist: " + r);
        h.assertTrue(p.count(JobBoard.KEY_NO_FREE_HANDS) == 1, "expected one 'no free hands' line: " + p.actionBar);
        h.runAfterDelay(2 * CLAIM + 6, () -> {
            h.assertTrue(p.count(JobBoard.KEY_NO_FREE_HANDS) == 1, "'no free hands' repeated: " + p.actionBar);
            h.assertTrue(JobBoard.jobs(f.ship().id()).get(f.winch()) != null
                    && JobBoard.jobs(f.ship().id()).get(f.winch()).order() == SailOrder.HOIST, "the open job went away");
            JobBoard.clear(f.ship().id());
            h.succeed();
        });
    }

    /** With the board off, an unmanned winch stays idle: no job, the free hand stays free, the sails stay furled. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = "pirates_n_ships_config_crew_stations_job_board")
    public static void disabledBoardLeavesUnmannedStationsIdle(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.JOB_BOARD_ENABLED, false);
        Fixture f = StationGameTests.ship(h, false, x -> { });
        CrewMember c = onDeck(h, f, deck(h, f, 20, 20));
        Captain p = captain(h, f);
        WhistleOrders.Result r = WhistleOrders.handle(p, WhistleOrder.HOIST.id());
        h.assertTrue(r.outcome() == WhistleOrders.Outcome.ISSUED && r.crew() == 0 && r.jobs() == 0, "hoist: " + r);
        h.assertTrue(JobBoard.jobs(f.ship().id()).isEmpty(), "the disabled board has jobs");
        h.assertTrue(p.count(JobBoard.KEY_NO_FREE_HANDS) == 0, "the disabled board spoke");
        h.runAfterDelay(CLAIM + 3, () -> {
            h.assertTrue(c.assignment() == null, "a free hand took a station with the board off");
            h.assertTrue(StationGameTests.trim(h, f) == SailTrim.FURLED, "the sails changed");
            cleanup(c);
            h.succeed();
        });
    }
}

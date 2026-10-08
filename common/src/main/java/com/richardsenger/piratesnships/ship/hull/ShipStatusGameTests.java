package com.richardsenger.piratesnships.ship.hull;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.ShipConfig;
import com.richardsenger.piratesnships.ship.hull.net.ShipStatusPayload;
import com.richardsenger.piratesnships.ship.hull.net.ShipStatusSync;
import com.richardsenger.piratesnships.ship.hull.net.ShipStatusThrottle;
import com.richardsenger.piratesnships.ship.hull.pump.BilgePumps;
import com.richardsenger.piratesnships.ship.hull.pump.HullRepairContent;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The ship status behind the ship HUD (docs/design.md §4.6, HUD1) on a real ship: the closed 5×4×5 plank hull of
 * {@link DryHullGameTests} afloat in its basin (hold 3×2×3 = 18 cells, one compartment). Each test runs its own
 * {@link ShipStatusSync} with mock players (they are not in {@code level.players()}) and records what it would send.
 */
public final class ShipStatusGameTests {

    /** A freshly assembled floating ship rises about 1.4 blocks first: board once it has settled. */
    private static final int BOARD_AT = 60;

    private ShipStatusGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(ShipStatusGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private record Sent(Player player, ShipStatusPayload payload, long time) {
    }

    private static Fixture ship(GameTestHelper h, boolean pump) {
        DryHullGameTests.basin(h, 0, 23, true);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        if (pump) {
            h.setBlock(new BlockPos(12, 6, 12), HullRepairContent.BILGE_PUMP.get());
        }
        Fixture f = DryHullGameTests.assemble(h, helm);
        h.assertTrue(f.runtime().simulation().analysis().compartments().size() == 1, "the hold is not one compartment");
        return f;
    }

    /** A mock player standing on the deck beside the helm. */
    private static Player boardPlayer(GameTestHelper h, Fixture f) {
        Vec3 deck = f.ship().toWorld(Vec3.atBottomCenterOf(f.helmPlot().offset(1, 0, 0)));
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.moveTo(deck.x, deck.y + 0.05, deck.z, 0f, 0f);
        h.getLevel().addFreshEntity(p);
        return p;
    }

    /** A mock player on the basin's stone rim, next to the ship but not aboard. */
    private static Player shorePlayer(GameTestHelper h) {
        Vec3 rim = h.absoluteVec(new Vec3(0.5, 9.0, 4.5));
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.moveTo(rim.x, rim.y, rim.z, 0f, 0f);
        h.getLevel().addFreshEntity(p);
        return p;
    }

    private static boolean aboard(Player p, ShipBody ship) {
        ShipBody on = ShipEntities.standingOrRiding(p);
        return on != null && on.id().equals(ship.id());
    }

    private static void discard(Player... players) {
        for (Player p : players) {
            if (p != null) p.discard();
        }
    }

    // ------------------------------------------------------------------ tests

    /**
     * Water in the hold and a breach in its wall: the player aboard gets a status whose one cell shows half the hold
     * flooded and the breach; the player on the shore gets nothing.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300)
    public static void floodedHoldAndBreachReachThePlayerAboardOnly(GameTestHelper h) {
        Fixture f = ship(h, false);
        ServerLevel level = h.getLevel();
        Player[] players = new Player[2];
        h.runAfterDelay(BOARD_AT, () -> {
            players[0] = boardPlayer(h, f);
            players[1] = shorePlayer(h);
        });
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0] || players[0] == null || !aboard(players[0], f.ship())) return;
            done[0] = true;
            BlockPos wall = f.hold(-2, -3, 0);
            h.assertTrue(level.getBlockState(wall).is(Blocks.OAK_PLANKS), "no hull wall at " + wall);
            level.setBlock(wall, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            h.assertTrue(f.runtime().breaches().contains(wall), "the removed hull block is not a breach");
            f.runtime().simulation().setVolume(0, 9.0);

            List<Sent> sent = new ArrayList<>();
            new ShipStatusSync().sync(level, List.of(players[0], players[1]), (p, s) -> sent.add(new Sent(p, s, 0)));
            h.assertTrue(sent.size() == 1, "expected one status, got " + sent.size());
            h.assertTrue(sent.get(0).player() == players[0], "the status went to the player on the shore");
            ShipStatusPayload s = sent.get(0).payload();
            h.assertTrue(s.ship().equals(f.ship().id()), "the status is about another ship");
            h.assertTrue(s.cells().size() == 1, "expected one cell, got " + s.cells());
            ShipStatusPayload.Cell c = s.cells().get(0);
            h.assertTrue(c.volume() == 18, "the hold's volume is " + c.volume());
            h.assertTrue(Math.abs(c.water() - 9.0f) <= 0.05f && Math.abs(c.fraction() - 0.5f) < 0.01f,
                    "the cell shows " + c.water() + " blocks of water (" + c.fraction() + ")");
            h.assertTrue(c.breaches() == 1, "the cell shows " + c.breaches() + " breaches");
            h.assertFalse(c.pumping(), "the cell is pumped without a pump");
            h.assertTrue(s.hasRudder() && s.rudder() == 0f, "a fresh ship's rudder is " + s.rudder());
            h.assertTrue(s.heading() >= 0 && s.heading() < 360, "heading " + s.heading());
            discard(players);
            h.succeed();
        });
    }

    /** A running bilge pump sets the pumping flag of the hold's cell; it clears once the pump stops. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120)
    public static void runningPumpSetsThePumpingFlag(GameTestHelper h) {
        Fixture f = ship(h, true);
        ServerLevel level = h.getLevel();
        BlockPos pump = f.hold(1, -3, 1);
        float[] water = new float[1];
        h.runAfterDelay(5, () -> {
            f.runtime().simulation().setVolume(0, 10.0);
            ShipStatusPayload idle = ShipStatusSync.build(level, f.ship());
            h.assertFalse(idle.cells().get(0).pumping(), "an idle pump shows as pumping");
            water[0] = idle.cells().get(0).water();
            BilgePumps.Use use = BilgePumps.operate(level, pump, null);
            h.assertTrue(use.outcome() == BilgePumps.Outcome.PUMPING, "the pump did not pump: " + use);
        });
        h.runAfterDelay(7, () -> {
            ShipStatusPayload.Cell c = ShipStatusSync.build(level, f.ship()).cells().get(0);
            h.assertTrue(c.pumping(), "the pumped hold does not show the pump");
            h.assertTrue(c.water() < water[0], "the water did not go down: " + water[0] + " -> " + c.water());
        });
        // one use keeps a pump working for flooding.pump_use_ticks (8)
        h.runAfterDelay(5 + FloodingConfig.PUMP_USE_TICKS.get() + 4, () -> {
            h.assertFalse(ShipStatusSync.build(level, f.ship()).cells().get(0).pumping(), "the pump still shows after it stopped");
            h.succeed();
        });
    }

    /**
     * {@code ship_status_sync_interval_ticks} from config (7): with the water changing every tick, a status goes out at
     * every multiple of 7 and at no other tick, each fresh for {@link ShipStatusThrottle#freshTicks} of 7. Then, within
     * one tick (nothing changes): an unchanged status is held back, every fifth interval (35 ticks, about the 40-tick
     * keepalive) it goes out anyway, and a change goes out at once.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300, batch = "pirates_n_ships_config_ship_status")
    public static void intervalFromConfigAndChangeOnly(GameTestHelper h) {
        ConfigOverrides.during(h, ShipConfig.SHIP_STATUS_SYNC_INTERVAL_TICKS, 7);
        Fixture f = ship(h, false);
        ServerLevel level = h.getLevel();
        Player[] player = new Player[1];
        h.runAfterDelay(BOARD_AT, () -> player[0] = boardPlayer(h, f));
        ShipStatusSync sender = new ShipStatusSync();
        List<Sent> sent = new ArrayList<>();
        long[] start = {-1};
        int[] dueTicks = {0};
        h.onEachTick(() -> {
            if (player[0] == null || start[0] == -2) return;
            long now = level.getGameTime();
            if (start[0] < 0) {
                if (!aboard(player[0], f.ship())) return;
                start[0] = now;
            }
            if (now - start[0] < 3 * 7) {
                f.runtime().simulation().setVolume(0, 1 + (now % 5));
                if (now % 7 == 0) dueTicks[0]++;
                sender.tick(level, List.of(player[0]), (p, s) -> sent.add(new Sent(p, s, now)));
                return;
            }
            start[0] = -2;
            h.assertTrue(dueTicks[0] == 3, "expected three intervals in 21 ticks, got " + dueTicks[0]);
            h.assertTrue(sent.size() == dueTicks[0], "expected a status at each of " + dueTicks[0] + " intervals, got " + sent.size());
            for (Sent s : sent) h.assertTrue(s.time() % 7 == 0, "a status went out at tick " + s.time());
            int fresh = ShipStatusThrottle.freshTicks(7);
            for (Sent s : sent) h.assertTrue(s.payload().freshTicks() == fresh,
                    "the status says it is fresh for " + s.payload().freshTicks() + " ticks, not " + fresh);

            // change-only and keepalive, all within this tick
            ShipStatusSync again = new ShipStatusSync();
            int[] count = {0};
            List<Player> aboard = List.of(player[0]);
            again.sync(level, aboard, (p, s) -> count[0]++);
            h.assertTrue(count[0] == 1, "the first status was not sent");
            int keepalive = ShipStatusThrottle.keepaliveIntervals(7);
            h.assertTrue(keepalive == 5, "keepalive of " + keepalive + " intervals at 7 ticks, expected 5 (35 ticks)");
            for (int i = 1; i < keepalive; i++) {
                again.sync(level, aboard, (p, s) -> count[0]++);
                h.assertTrue(count[0] == 1, "an unchanged status was sent again at interval " + i);
            }
            again.sync(level, aboard, (p, s) -> count[0]++);
            h.assertTrue(count[0] == 2, "the fifth unchanged interval did not send the status");
            f.runtime().simulation().setVolume(0, 7.5);
            again.sync(level, aboard, (p, s) -> count[0]++);
            h.assertTrue(count[0] == 3, "a changed status was held back");
            discard(player);
            h.succeed();
        });
    }

    /**
     * HUD2's flicker on the server side: a ship at rest with a player standing aboard, the game's sync interval (20).
     * Nothing changes, so only the keepalive goes out: never more than {@link ShipStatusThrottle#KEEPALIVE_TICKS}
     * apart, and every status stays fresh on the client for well over the gap (HUD1 sent one every 100 ticks while
     * the client dropped it after 60).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 400)
    public static void aShipAtRestKeepsItsStatusFresh(GameTestHelper h) {
        Fixture f = ship(h, false);
        ServerLevel level = h.getLevel();
        Player[] player = new Player[1];
        h.runAfterDelay(BOARD_AT, () -> player[0] = boardPlayer(h, f));
        ShipStatusSync sender = new ShipStatusSync();
        List<Sent> sent = new ArrayList<>();
        long[] start = {-1};
        h.onEachTick(() -> {
            if (player[0] == null || start[0] == -2) return;
            long now = level.getGameTime();
            if (start[0] < 0) {
                if (!aboard(player[0], f.ship())) return;
                start[0] = now;
                // the settled ship at rest: one status first, so only keepalives follow
                f.runtime().simulation().setVolume(0, 2.0);
            }
            if (now - start[0] < 200) {
                h.assertTrue(aboard(player[0], f.ship()), "the player fell off the deck");
                sender.tick(level, List.of(player[0]), (p, s) -> sent.add(new Sent(p, s, now)));
                return;
            }
            start[0] = -2;
            int interval = ShipConfig.SHIP_STATUS_SYNC_INTERVAL_TICKS.get();
            h.assertTrue(sent.size() >= 200 / ShipStatusThrottle.KEEPALIVE_TICKS,
                    "only " + sent.size() + " statuses in 200 ticks aboard");
            h.assertTrue(sent.get(0).time() - start[0] < interval, "the first status took " + (sent.get(0).time() - start[0]));
            for (int i = 1; i < sent.size(); i++) {
                long gap = sent.get(i).time() - sent.get(i - 1).time();
                int fresh = sent.get(i - 1).payload().freshTicks();
                h.assertTrue(gap <= Math.max(interval, ShipStatusThrottle.KEEPALIVE_TICKS),
                        "a gap of " + gap + " ticks between two statuses");
                h.assertTrue(fresh >= 3 * gap, "a status fresh for " + fresh + " ticks with " + gap + " ticks to the next");
            }
            discard(player);
            h.succeed();
        });
    }

    /** {@code ship_status_hud = false}: no status for anyone, at any tick. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300, batch = "pirates_n_ships_config_ship_status_off")
    public static void switchedOffSendsNothing(GameTestHelper h) {
        ConfigOverrides.during(h, ShipConfig.SHIP_STATUS_HUD, false);
        Fixture f = ship(h, false);
        ServerLevel level = h.getLevel();
        Player[] player = new Player[1];
        h.runAfterDelay(BOARD_AT, () -> player[0] = boardPlayer(h, f));
        ShipStatusSync sender = new ShipStatusSync();
        long[] start = {-1};
        int[] sent = {0};
        h.onEachTick(() -> {
            if (player[0] == null || start[0] == -2) return;
            long now = level.getGameTime();
            if (start[0] < 0) {
                if (!aboard(player[0], f.ship())) return;
                start[0] = now;
            }
            if (now - start[0] <= 45) {
                f.runtime().simulation().setVolume(0, 1 + (now % 5));
                sender.tick(level, List.of(player[0]), (p, s) -> sent[0]++);
                return;
            }
            start[0] = -2;
            h.assertTrue(sent[0] == 0, sent[0] + " statuses were sent with the HUD switched off");
            discard(player);
            h.succeed();
        });
    }
}

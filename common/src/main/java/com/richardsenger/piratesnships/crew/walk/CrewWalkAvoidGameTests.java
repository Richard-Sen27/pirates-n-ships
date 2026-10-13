package com.richardsenger.piratesnships.crew.walk;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.crew.walk.CrewWalkGameTests.Ship;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import java.util.Collection;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * WALK2 (docs/design.md §6): walking crew steer round people. The WALK1 hull ({@link CrewWalkGameTests#ship}, 7×17,
 * helm at the stern, winch 12 blocks forward) gets a walkway walled two blocks high along the centre line, z 9..16,
 * with a second sail winch set into its west wall at z 13: a hand without AI ordered there sits on its seat at the
 * winch's first free side, the walkway cell (18, 9, 13). A walker starts at (18, 9, 10) and is sent to the winch
 * (its spot (18, 9, 17)), straight through that cell. Each test runs in a batch of its own (config overrides).
 */
public final class CrewWalkAvoidGameTests {

    private static final String BATCH = "pirates_n_ships_config_crew_walk_avoid_";

    /** The hand's winch, set into the walkway's west wall. */
    static final BlockPos HAND_WINCH = new BlockPos(17, 9, 13);
    /** The hand's seat: the winch's first free side (north is wall), in the walkway. */
    static final BlockPos HAND_SEAT = new BlockPos(18, 9, 13);
    static final BlockPos WALKER = new BlockPos(18, 9, 10);
    /** Tick the hand is ordered to its winch (it has landed on the deck by then). */
    static final int HAND_AT = 5;

    private CrewWalkAvoidGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CrewWalkAvoidGameTests.class);
    }

    // ------------------------------------------------------------------ fixture

    /** The walkway between walls at {@code westX} and {@code eastX} (two high, z 9..16), the hand's winch in the west wall. */
    static Consumer<GameTestHelper> walkway(int westX, int eastX, boolean handWinch) {
        return h -> {
            for (int z = 9; z <= 16; z++) {
                for (int y = 9; y <= 10; y++) {
                    h.setBlock(new BlockPos(westX, y, z), Blocks.OAK_PLANKS);
                    h.setBlock(new BlockPos(eastX, y, z), Blocks.OAK_PLANKS);
                }
            }
            if (handWinch) {
                h.setBlock(HAND_WINCH, SailingBlocks.SAIL_WINCH.get());
            }
        };
    }

    /**
     * Takes the walkway's walls off the deck before the ship goes: Sable hands the freed plot to the next test's ship,
     * and the WALK2 runs showed the pathfinder of later walk tests still seeing these walls there (the level's block
     * states read air there).
     */
    static void clearWalkway(GameTestHelper h, Ship s) {
        for (int z = 9; z <= 16; z++) {
            for (int y = 9; y <= 10; y++) {
                for (int x = 16; x <= 20; x++) {
                    BlockPos p = s.plot(new BlockPos(x, y, z));
                    if (!h.getLevel().getBlockState(p).isAir()) {
                        h.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    /** A hand without AI at its seat beside the walkway (ordered to the hand's winch, seated at once). */
    static CrewMember hand(GameTestHelper h, Ship s) {
        CrewMember hand = CrewWalkGameTests.crew(h, s, HAND_SEAT);
        hand.setNoAi(true);
        return hand;
    }

    static void seatHand(GameTestHelper h, Ship s, CrewMember hand) {
        CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), hand, s.plot(HAND_WINCH));
        h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "hand assign: " + r);
        h.assertTrue(hand.isAtStation() && hand.walk() == null, "the hand is not seated at once: walk " + hand.walk());
        BlockPos seat = hand.getVehicle() == null ? null : hand.getVehicle().blockPosition();
        h.assertTrue(s.plot(HAND_SEAT).equals(seat), "the hand's seat is not in the walkway: " + seat + ", expected " + s.plot(HAND_SEAT));
    }

    static void sendToWinch(GameTestHelper h, Ship s, CrewMember c) {
        CrewWalkGameTests.assertOnShip(h, s, c);
        CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), c, s.plot(CrewWalkGameTests.WINCH));
        h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "assign: " + r);
        h.assertTrue(c.walk() != null && !c.isAtStation(), "not walking to the winch");
    }

    /** Horizontal ship-space distance from the crew member to the centre of plot cell {@code cell}. */
    static double flat(Ship s, CrewMember c, BlockPos cell) {
        Vec3 p = s.body().toPlot(c.position());
        return Math.hypot(p.x - (cell.getX() + 0.5), p.z - (cell.getZ() + 0.5));
    }

    /** The walker's ship-space position and its path (plot nodes, relative to the fixture), for failure messages. */
    static String describe(Ship s, CrewMember c) {
        Vec3 p = s.body().toPlot(c.position()).subtract(Vec3.atLowerCornerOf(s.helm().subtract(CrewWalkGameTests.HELM)));
        StringBuilder b = new StringBuilder(String.format(java.util.Locale.ROOT, "at (%.2f, %.2f, %.2f)", p.x, p.y, p.z));
        net.minecraft.world.level.pathfinder.Path path = c.getNavigation().getPath();
        if (path == null) {
            b.append(", no path");
        } else {
            b.append(", path next ").append(path.getNextNodeIndex()).append(" of");
            for (int i = 0; i < path.getNodeCount(); i++) {
                b.append(' ').append(path.getNode(i).asBlockPos().subtract(s.helm().subtract(CrewWalkGameTests.HELM)).toShortString());
            }
        }
        WalkAvoidance.State st = CrewWalk.avoidance(c);
        b.append(", mode ").append(st == null ? "-" : st.mode()).append(", on ground ").append(c.onGround());
        b.append(", deck blocks:");
        for (int y = 9; y <= 10; y++) {
            for (int z = 8; z <= 19; z++) {
                for (int x = 16; x <= 21; x++) {
                    BlockPos rel = new BlockPos(x, y, z);
                    var st2 = c.level().getBlockState(s.plot(rel));
                    if (!st2.isAir()) b.append(' ').append(rel.toShortString()).append('=').append(st2.getBlock().getDescriptionId());
                }
            }
        }
        var es = c.level().getEntities(c, c.getBoundingBox().inflate(4));
        b.append(", entities near:");
        for (var e : es) b.append(' ').append(e.getType().getDescriptionId()).append('@').append(e.blockPosition().toShortString());
        return b.toString();
    }

    /** What a walker's steering did during its walk (the state goes with the walk, so it is sampled every tick). */
    static final class Seen {
        int sidesteps;
        int waits;
        int yields;
        int nudges;
        long seatedAt = -1;
        int still;
        Vec3 last;

        void sample(CrewMember c) {
            WalkAvoidance.State st = CrewWalk.avoidance(c);
            if (st != null) {
                sidesteps = Math.max(sidesteps, st.sidesteps());
                waits = Math.max(waits, st.waits());
                yields = Math.max(yields, st.yields());
                nudges = Math.max(nudges, st.nudges());
            }
        }

        @Override
        public String toString() {
            return "sidesteps " + sidesteps + ", waits " + waits + ", yields " + yields + ", nudges " + nudges + ", seated at " + seatedAt + ", still " + still;
        }
    }

    // ------------------------------------------------------------------ tests

    /**
     * A two-wide walkway with a hand seated in one lane: the walker sidesteps into the other lane and passes him without
     * waiting, never stepping into his cell, and is seated at the winch.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "two_wide")
    public static void passesASeatedHandInATwoWideWalkway(GameTestHelper h) {
        Ship s = CrewWalkGameTests.ship(h, false, walkway(17, 20, true));
        CrewMember hand = hand(h, s);
        CrewMember c = CrewWalkGameTests.crew(h, s, WALKER);
        Seen seen = new Seen();
        BlockPos seat = s.plot(HAND_SEAT);
        h.runAtTickTime(HAND_AT, () -> seatHand(h, s, hand));
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t == CrewWalkGameTests.SETTLE) {
                sendToWinch(h, s, c);
                seen.last = s.body().toPlot(c.position());
                return;
            }
            if (t < CrewWalkGameTests.SETTLE || seen.seatedAt >= 0) return;
            seen.sample(c);
            h.assertTrue(flat(s, c, seat) > 0.55, "the walker stepped into the hand's cell: " + flat(s, c, seat) + "; " + seen);
            if (c.isAtStation()) {
                seen.seatedAt = t - CrewWalkGameTests.SETTLE;
                return;
            }
            if ((t - CrewWalkGameTests.SETTLE) % CrewWalkGameTests.SAMPLE == 0) {
                Vec3 p = s.body().toPlot(c.position());
                if (p.distanceTo(seen.last) < 0.1) seen.still++;
                seen.last = p;
            }
            h.assertTrue(t - CrewWalkGameTests.SETTLE <= 120, "not seated within 120 ticks: " + seen + ", walk " + c.walk());
        });
        h.succeedWhen(() -> {
            h.assertTrue(seen.seatedAt >= 0, "still walking");
            h.assertTrue(hand.isAtStation(), "the hand left his seat");
            h.assertTrue(seen.sidesteps >= 1, "never sidestepped: " + seen);
            h.assertTrue(seen.waits == 0, "stopped to wait: " + seen);
            h.assertTrue(seen.still <= 1, "stood still on " + seen.still + " samples: " + seen);
            Constants.LOG.info("[WALK2] two-wide walkway past a seated hand: {}", seen);
            CrewStations.release(h.getLevel(), hand);
            CrewWalkGameTests.cleanup(hand);
            CrewWalkGameTests.cleanup(c);
            clearWalkway(h, s);
        });
    }

    /**
     * A one-wide corridor with a hand seated in the middle: there is no way round, so the walker waits short of him (it
     * never pushes through); once the hand gets up and goes, the walker goes on and is seated at the winch, well
     * inside the walk's timeout.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = BATCH + "one_wide_leaves")
    public static void waitsInAOneWideCorridorUntilTheHandLeaves(GameTestHelper h) {
        ConfigOverrides.during(h, WalkConfig.TIMEOUT_TICKS, 400);
        Ship s = CrewWalkGameTests.ship(h, false, walkway(17, 19, true));
        CrewMember hand = hand(h, s);
        CrewMember c = CrewWalkGameTests.crew(h, s, WALKER);
        Seen seen = new Seen();
        int leaves = CrewWalkGameTests.SETTLE + 80;
        h.runAtTickTime(HAND_AT, () -> seatHand(h, s, hand));
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t == CrewWalkGameTests.SETTLE) {
                sendToWinch(h, s, c);
                return;
            }
            if (t < CrewWalkGameTests.SETTLE || seen.seatedAt >= 0) return;
            seen.sample(c);
            if (t < leaves) {
                Vec3 p = s.body().toPlot(c.position());
                h.assertTrue(p.z < s.plot(HAND_SEAT).getZ(), "the walker got past the hand: z " + p.z + "; " + seen);
                h.assertTrue(!c.isAtStation() && c.walk() != null, "seated while the hand blocks the corridor; " + seen);
            }
            if (t == leaves) {
                WalkAvoidance.State st = CrewWalk.avoidance(c);
                h.assertTrue(seen.waits >= 1 && st != null && st.mode() == WalkAvoidance.Mode.WAIT,
                        "not waiting before the hand: " + seen + ", " + describe(s, c));
                CrewStations.release(h.getLevel(), hand); // he gets up and goes below
                CrewWalkGameTests.cleanup(hand);
            }
            if (c.isAtStation()) {
                seen.seatedAt = t - CrewWalkGameTests.SETTLE;
                return;
            }
            h.assertTrue(t <= leaves + 80, "not seated within 80 ticks after the hand left: " + seen + ", walk " + c.walk());
        });
        h.succeedWhen(() -> {
            h.assertTrue(seen.seatedAt >= 0, "still walking");
            h.assertTrue(seen.seatedAt < 400, "seated by the timeout");
            Constants.LOG.info("[WALK2] one-wide corridor, the hand leaves at {}: {}", leaves - CrewWalkGameTests.SETTLE, seen);
            CrewWalkGameTests.cleanup(c);
            clearWalkway(h, s);
        });
    }

    /**
     * The same corridor, the hand stays: the walker waits and is seated by WALK1's fallback at the timeout (waiting
     * counts towards it); it never walked through him.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "one_wide_stays")
    public static void aBlockedCorridorTimesOutCleanly(GameTestHelper h) {
        int timeout = 60;
        ConfigOverrides.during(h, WalkConfig.TIMEOUT_TICKS, timeout);
        Ship s = CrewWalkGameTests.ship(h, false, walkway(17, 19, true));
        CrewMember hand = hand(h, s);
        CrewMember c = CrewWalkGameTests.crew(h, s, WALKER);
        Seen seen = new Seen();
        h.runAtTickTime(HAND_AT, () -> seatHand(h, s, hand));
        h.runAtTickTime(CrewWalkGameTests.SETTLE, () -> sendToWinch(h, s, c));
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t <= CrewWalkGameTests.SETTLE || c.isAtStation() || c.isRemoved() || c.walk() == null) return;
            seen.sample(c);
            h.assertTrue(s.body().toPlot(c.position()).z < s.plot(HAND_SEAT).getZ(), "the walker got past the hand; " + seen + ", " + describe(s, c));
        });
        h.runAtTickTime(CrewWalkGameTests.SETTLE + timeout - 10, () -> {
            h.assertTrue(!c.isAtStation() && c.walk() != null, "seated before the timeout");
            h.assertTrue(seen.waits >= 1, "never waited: " + seen);
        });
        h.runAtTickTime(CrewWalkGameTests.SETTLE + timeout + 10, () -> {
            h.assertTrue(c.isAtStation() && c.walk() == null, "not seated by the fallback after the timeout: " + c.walk());
            h.assertTrue(hand.isAtStation(), "the hand was pushed off his seat");
            Constants.LOG.info("[WALK2] one-wide corridor, the hand stays: {}", seen);
            CrewStations.release(h.getLevel(), hand);
            CrewWalkGameTests.cleanup(hand);
            CrewWalkGameTests.cleanup(c);
            clearWalkway(h, s);
            h.succeed();
        });
    }

    /**
     * Two walkers meet head-on in a one-wide gangway: one goes to the winch forward, the other to the helm aft. The one
     * with the lower entity id yields (backs out of the gangway and steps off the other's way), the other holds and then
     * passes; both are seated well inside the timeout.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 400, batch = BATCH + "head_on")
    public static void twoWalkersMeetingHeadOnInAOneWideGangwayBothArrive(GameTestHelper h) {
        ConfigOverrides.during(h, WalkConfig.TIMEOUT_TICKS, 600);
        Ship s = CrewWalkGameTests.ship(h, false, walkway(17, 19, false));
        CrewMember fore = CrewWalkGameTests.crew(h, s, WALKER);
        CrewMember aft = CrewWalkGameTests.crew(h, s, new BlockPos(18, 9, 15));
        Seen foreSeen = new Seen();
        Seen aftSeen = new Seen();
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t == CrewWalkGameTests.SETTLE) {
                sendToWinch(h, s, fore);
                CrewWalkGameTests.assertOnShip(h, s, aft);
                CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), aft, s.helm());
                h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "helm assign: " + r);
                h.assertTrue(aft.walk() != null, "not walking to the helm");
                return;
            }
            if (t < CrewWalkGameTests.SETTLE) return;
            for (var e : java.util.List.of(java.util.Map.entry(fore, foreSeen), java.util.Map.entry(aft, aftSeen))) {
                if (e.getValue().seatedAt >= 0) continue;
                e.getValue().sample(e.getKey());
                if (e.getKey().isAtStation()) e.getValue().seatedAt = t - CrewWalkGameTests.SETTLE;
            }
            h.assertTrue(t - CrewWalkGameTests.SETTLE <= 300, "not both seated within 300 ticks: fore " + foreSeen + " " + describe(s, fore)
                    + " / aft " + aftSeen + " " + describe(s, aft));
        });
        h.succeedWhen(() -> {
            h.assertTrue(foreSeen.seatedAt >= 0 && aftSeen.seatedAt >= 0, "still walking");
            boolean foreLower = fore.getId() < aft.getId();
            Seen lower = foreLower ? foreSeen : aftSeen;
            Seen higher = foreLower ? aftSeen : foreSeen;
            h.assertTrue(lower.yields >= 1, "the lower id never yielded: " + lower);
            h.assertTrue(higher.yields == 0, "the higher id yielded: " + higher);
            Constants.LOG.info("[WALK2] head-on, ids fore {} aft {}: fore {}, aft {}", fore.getId(), aft.getId(), foreSeen, aftSeen);
            for (CrewMember c : java.util.List.of(fore, aft)) {
                CrewStations.release(h.getLevel(), c);
                CrewWalkGameTests.cleanup(c);
            }
            clearWalkway(h, s);
        });
    }

    /** {@code crew.walk.avoids_crew} off: WALK1's walk, no steering round the hand at all. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 120, batch = BATCH + "off")
    public static void avoidingOffSteersRoundNobody(GameTestHelper h) {
        ConfigOverrides.during(h, WalkConfig.AVOIDS_CREW, false);
        Ship s = CrewWalkGameTests.ship(h, false, walkway(17, 19, true));
        CrewMember hand = hand(h, s);
        CrewMember c = CrewWalkGameTests.crew(h, s, WALKER);
        h.runAtTickTime(HAND_AT, () -> seatHand(h, s, hand));
        h.runAtTickTime(CrewWalkGameTests.SETTLE, () -> sendToWinch(h, s, c));
        h.onEachTick(() -> {
            if (h.getTick() > CrewWalkGameTests.SETTLE) {
                h.assertTrue(CrewWalk.avoidance(c) == null, "steered with avoiding off: " + CrewWalk.avoidance(c));
            }
        });
        h.runAtTickTime(CrewWalkGameTests.SETTLE + 60, () -> {
            CrewStations.release(h.getLevel(), hand);
            CrewWalkGameTests.cleanup(hand);
            CrewStations.release(h.getLevel(), c);
            CrewWalkGameTests.cleanup(c);
            clearWalkway(h, s);
            h.succeed();
        });
    }
}

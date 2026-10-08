package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import com.richardsenger.piratesnships.combat.firearms.FirearmKind;
import com.richardsenger.piratesnships.combat.firearms.FirearmRules;
import com.richardsenger.piratesnships.combat.firearms.FirearmTrigger;
import com.richardsenger.piratesnships.combat.firearms.FirearmsConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/**
 * Sliding along a latched grappling rope (GR2, GR4, docs/design.md §8.3) in a real server. Ropes are grabbed
 * {@code grab_cooldown_ticks} after the throw ({@link #grabAt()}). Two closed 5×4×5 plank hulls of
 * {@link DryHullGameTests} float in one basin as in {@link GrappleGameTests}: ship A at x 2..6 (the thrower's), ship B
 * at x 16..20; the hook bites into B's west wall at deck height. For the crow's nest A carries a three-plank mast on
 * its helm, the thrower standing on top. GR5: a rope is a line to slide along only when its near end is tied off, so
 * the slide tests tie the thrower's rope to a cleat on A's deck ({@link #tieToCleat}). GR6: a thrower using their own
 * hand-held rope is pulled along it only as a climb; the crow's nest, own ship and musket tests have the hook level
 * with or below the thrower and expect the refusal ({@code NOT_A_CLIMB}). Hauling is switched off ({@code haul_force} 0, so every test that changes it
 * has its own batch) so the ships lie still while the riders slide. Players are mock players (not in the level):
 * riding, the level ticks them as passengers of the rider, which is all the slide needs. Ships are removed by
 * {@code ShipTestCleanup}; hooks are released at the end.
 */
public final class GrappleSlideGameTests {

    private static final String BATCH = "pirates_n_ships_config_grapple_slide_";

    private GrappleSlideGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(GrappleSlideGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private record Ships(Fixture a, Fixture b) {
    }

    /** The two ships; with {@code mast}, A has a three-plank mast on its helm (the crow's nest). */
    private static Ships twoShips(GameTestHelper h, boolean mast) {
        ConfigOverrides.during(h, GrappleConfig.HAUL_FORCE, 0.0);
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helmA = DryHullGameTests.hull(h, 2, false);
        BlockPos helmB = DryHullGameTests.hull(h, 16, false);
        if (mast) {
            for (int y = 1; y <= 3; y++) h.setBlock(helmA.above(y), Blocks.OAK_PLANKS);
        }
        h.setBlock(new BlockPos(5, 9, 12), SailingGameTestsShips.cleat(AttachFace.FLOOR, Direction.NORTH)); // A's cleat
        return new Ships(DryHullGameTests.assemble(h, helmA), DryHullGameTests.assemble(h, helmB));
    }

    /** Plot position of A's cleat (one east and one south of the helm). */
    private static BlockPos cleatA(Fixture a) {
        return a.helmPlot().offset(1, 0, 1);
    }

    /** Ties the thrower's rope to A's cleat (RP1): a line between two fixed ends to slide along (GR5). */
    private static void tieToCleat(GameTestHelper h, ServerLevel level, Player thrower, GrapplingHookEntity hook, Fixture a) {
        GrappleService.tieOff(level, thrower, cleatA(a));
        h.assertTrue(cleatA(a).equals(hook.tiedRing()) && hook.nearEndFixed(), "the rope is not tied to A's cleat: " + hook.tiedRing());
    }

    private static Player player(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.getInventory().clearContent();
        return p;
    }

    /** Puts {@code player} on plot block {@code feet} of {@code ship} every tick while {@code hold[0]} is true. */
    private static void keepOn(GameTestHelper h, Player player, ShipBody ship, BlockPos feet, boolean[] hold) {
        Runnable put = () -> {
            if (hold[0] && !ship.isRemoved()) {
                Vec3 at = ship.toWorld(Vec3.atBottomCenterOf(feet));
                player.setPos(at.x, at.y, at.z);
            }
        };
        put.run();
        h.onEachTick(put);
    }

    /** Plot block of B's west wall at deck height, the hook's target. */
    private static BlockPos westDeckEdge(Fixture f) {
        return f.helmPlot().offset(-2, -1, 0);
    }

    /** Throws a hook from two blocks west of B's west wall straight at it. */
    private static GrapplingHookEntity hookB(ServerLevel level, Player thrower, Fixture b) {
        Vec3 target = b.ship().toWorld(Vec3.atCenterOf(westDeckEdge(b)));
        Vec3 from = target.add(-2.0, 0, 0);
        return GrappleService.launch(level, thrower, from, new Vec3(GrappleConfig.THROW_VELOCITY.get(), 0, 0),
                new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
    }

    /** Turns {@code player} to look at {@code point}. */
    private static void lookAt(Player player, Vec3 point) {
        Vec3 d = point.subtract(player.getEyePosition());
        double len = d.length();
        player.setYRot((float) Math.toDegrees(-Math.atan2(d.x, d.z)));
        player.setXRot((float) Math.toDegrees(-Math.asin(d.y / len)));
    }

    /** The rope point at parameter {@code t} (server ends). */
    private static Vec3 ropeAt(ServerLevel level, GrapplingHookEntity hook, double t) {
        return RopeSlide.at(hook.ropeNearEnd(level), hook.ropeFarEnd(level), t);
    }

    /** Asserts that {@code player} stands on B: on top of a plank of B's plot, near the spot above the hook's wall. */
    private static void assertStandsOnB(GameTestHelper h, ServerLevel level, Player player, Fixture b) {
        Vec3 spot = b.ship().toWorld(Vec3.atBottomCenterOf(westDeckEdge(b).above()));
        double d = player.position().distanceTo(spot);
        h.assertTrue(d < 1.0, "the player landed " + d + " blocks from the deck spot above the hook: " + player.position() + " vs " + spot);
        BlockPos below = BlockPos.containing(b.ship().toPlot(player.position().subtract(0, 0.5, 0)));
        h.assertTrue(level.getBlockState(below).is(Blocks.OAK_PLANKS), "no plank of ship B below the player's feet (" + below + ")");
    }

    private static void assertNoRiders(GameTestHelper h) {
        h.assertTrue(h.getEntities(GrappleContent.ROPE_RIDER.get()).isEmpty(), "a rope rider is left");
    }

    /** The test tick at which a rope thrown at tick 20 can first be grabbed ({@code grab_cooldown_ticks}, GR4), plus slack. */
    private static int grabAt() {
        return 22 + GrappleConfig.GRAB_COOLDOWN_TICKS.get();
    }

    // ------------------------------------------------------------------ sliding

    /**
     * GR6 (was GR5's pull from the crow's nest): the thrower on the mast top uses their own hand-held rope to B's deck,
     * far below them. A hand-held rope is only climbed, so nothing happens but the tie-off hint: no rider, the player
     * stays where they are, the rope keeps running from the hook to their hand and its near end is not fixed.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = BATCH + "crows_nest")
    public static void fromTheCrowsNestTheThrowersOwnRopeIsNoClimb(GameTestHelper h) {
        Ships s = twoShips(h, true);
        ServerLevel level = h.getLevel();
        Player player = player(h);
        boolean[] hold = {true};
        keepOn(h, player, s.a().ship(), s.a().helmPlot().above(4), hold); // on top of the mast
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        Vec3[] at = new Vec3[1];
        h.runAfterDelay(20, () -> hook[0] = hookB(level, player, s.b()));
        h.runAfterDelay(grabAt(), () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + g.state());
            Vec3 near = g.ropeNearEnd(level);
            Vec3 far = g.ropeFarEnd(level);
            h.assertTrue(near.y > far.y + 4.0, "the crow's nest is not high above the hook: " + near + " / " + far);
            hold[0] = false;
            at[0] = player.position();
            lookAt(player, ropeAt(level, g, 0.1));
            RopeSlideService.Board b = RopeSlideService.tryBoard(level, player, g, 0.0);
            h.assertTrue(b == RopeSlideService.Board.NOT_A_CLIMB, "a rope down from the crow's nest was used: " + b);
            h.assertTrue(RopeSlideService.lastHint(player) == level.getGameTime(), "no tie-off hint");
            h.assertTrue(!player.isPassenger(), "the player mounts the rope");
            assertNoRiders(h);
            h.assertTrue(!g.nearEndFixed(), "the thrower's rope end was fixed in mid-air");
        });
        h.runAfterDelay(grabAt() + 10, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!player.isPassenger(), "the player rides the rope");
            assertNoRiders(h);
            h.assertTrue(player.position().distanceTo(at[0]) < 0.1, "the player moved: " + player.position() + " vs " + at[0]);
            Vec3 hand = player.getEyePosition().subtract(0, GrapplingHookEntity.HAND_BELOW_EYES, 0);
            h.assertTrue(g.ropeNearEnd(level).distanceTo(hand) < 1.0e-6, "the rope does not run to the player's hand: " + g.ropeNearEnd(level));
            h.assertTrue(!g.isRemoved() && g.state() == GrapplingHookEntity.State.LATCHED && !g.nearEndFixed(), "the rope changed");
            g.release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 180, batch = BATCH + "water")
    public static void aSwimmerGrabsTheRopeMidwayAndSlidesToTheLowerShip(GameTestHelper h) {
        Ships s = twoShips(h, false);
        ServerLevel level = h.getLevel();
        Player thrower = player(h);
        keepOn(h, thrower, s.a().ship(), s.a().helmPlot().east(), new boolean[]{true});
        Player swimmer = player(h);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> hook[0] = hookB(level, thrower, s.b()));
        h.runAfterDelay(grabAt(), () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + g.state());
            Vec3 loose = ropeAt(level, g, 0.5);
            swimmer.setPos(loose.x, h.absoluteVec(new Vec3(0, 6.4, 0)).y, loose.z);
            lookAt(swimmer, loose);
            RopeSlideService.Board refused = RopeSlideService.tryBoard(level, swimmer, g, 0.0);
            h.assertTrue(refused == RopeSlideService.Board.NOT_FIXED, "someone else mounted a hand-held rope: " + refused);
            tieToCleat(h, level, thrower, g, s.a());
            Vec3 mid = ropeAt(level, g, 0.5);
            Vec3 water = h.absoluteVec(new Vec3(0, 6.4, 0));
            swimmer.setPos(mid.x, water.y, mid.z); // treading water under the middle of the rope
            h.assertTrue(level.getBlockState(swimmer.blockPosition()).is(Blocks.WATER), "the swimmer is not in the water");
            lookAt(swimmer, mid);
            RopeSlideService.Board b = RopeSlideService.tryBoard(level, swimmer, g, 0.0);
            h.assertTrue(b == RopeSlideService.Board.OK, "grabbing the rope from the water was refused: " + b);
            RopeRiderEntity rider = (RopeRiderEntity) swimmer.getVehicle();
            h.assertTrue(Math.abs(rider.t() - 0.5) < 0.1, "grabbed the rope at t=" + rider.t() + " instead of the middle");
            h.assertTrue(!rider.pulling(), "the swimmer is pulled instead of sliding");
        });
        h.runAfterDelay(grabAt() + 1, () -> h.succeedWhen(() -> {
            h.assertTrue(!swimmer.isPassenger(), "still sliding");
            assertStandsOnB(h, level, swimmer, s.b());
            h.assertTrue(swimmer.getHealth() == swimmer.getMaxHealth(), "hurt by the slide");
            assertNoRiders(h);
            hook[0].release(GrappleRules.Release.NONE);
        }));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 140, batch = BATCH + "sneak")
    public static void sneakingLetsGoWhereThePlayerHangs(GameTestHelper h) {
        Ships s = twoShips(h, false);
        ServerLevel level = h.getLevel();
        Player thrower = player(h);
        keepOn(h, thrower, s.a().ship(), s.a().helmPlot().east(), new boolean[]{true});
        Player rider = player(h);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        Vec3[] hung = new Vec3[1];
        h.runAfterDelay(20, () -> hook[0] = hookB(level, thrower, s.b()));
        h.runAfterDelay(grabAt(), () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + g.state());
            tieToCleat(h, level, thrower, g, s.a());
            Vec3 at = ropeAt(level, g, 0.3);
            rider.setPos(at.x, at.y - 1.6, at.z - 0.5);
            lookAt(rider, at);
            h.assertTrue(RopeSlideService.tryBoard(level, rider, g, 0.0) == RopeSlideService.Board.OK, "grabbing the rope was refused");
        });
        h.runAfterDelay(grabAt() + 3, () -> {
            h.assertTrue(rider.getVehicle() instanceof RopeRiderEntity, "the player let go by itself");
            rider.fallDistance = 4.0f; // as if it had been falling: the next ride tick resets it
        });
        h.runAfterDelay(grabAt() + 4, () -> {
            h.assertTrue(rider.fallDistance == 0.0f, "the slide does not reset the fall distance");
            hung[0] = rider.position();
            rider.setShiftKeyDown(true);
        });
        h.runAfterDelay(grabAt() + 5, () -> h.succeedWhen(() -> {
            h.assertTrue(!rider.isPassenger(), "sneaking did not let go");
            double moved = rider.position().distanceTo(hung[0]);
            h.assertTrue(moved < 1.0, "the player was moved " + moved + " blocks on letting go, it should drop where it hung");
            h.assertTrue(rider.fallDistance == 0.0f, "the fall distance of the slide was kept: " + rider.fallDistance);
            assertNoRiders(h);
            hook[0].release(GrappleRules.Release.NONE);
        }));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 140, batch = BATCH + "release")
    public static void releasingTheHookDropsTheRider(GameTestHelper h) {
        Ships s = twoShips(h, false);
        ServerLevel level = h.getLevel();
        Player thrower = player(h);
        keepOn(h, thrower, s.a().ship(), s.a().helmPlot().east(), new boolean[]{true});
        Player rider = player(h);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        Vec3[] hung = new Vec3[1];
        h.runAfterDelay(20, () -> hook[0] = hookB(level, thrower, s.b()));
        h.runAfterDelay(grabAt(), () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + g.state());
            tieToCleat(h, level, thrower, g, s.a());
            Vec3 at = ropeAt(level, g, 0.3);
            rider.setPos(at.x, at.y - 1.6, at.z - 0.5);
            lookAt(rider, at);
            h.assertTrue(RopeSlideService.tryBoard(level, rider, g, 0.0) == RopeSlideService.Board.OK, "grabbing the rope was refused");
        });
        h.runAfterDelay(grabAt() + 3, () -> {
            h.assertTrue(rider.isPassenger(), "the player let go by itself");
            hung[0] = rider.position();
            h.assertTrue(GrappleService.release(thrower), "the thrower had no hook to release");
        });
        h.runAfterDelay(grabAt() + 4, () -> h.succeedWhen(() -> {
            h.assertTrue(!rider.isPassenger(), "the rider still hangs on a released rope");
            h.assertTrue(rider.position().distanceTo(hung[0]) < 1.0, "the player did not drop where it hung");
            assertNoRiders(h);
        }));
    }

    /** GR5: a tied rope does not snap when its thrower walks off; it lets go when its cleat breaks, dropping the rider. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 140, batch = BATCH + "snap")
    public static void aRopeLosingItsCleatDropsTheRider(GameTestHelper h) {
        Ships s = twoShips(h, false);
        ServerLevel level = h.getLevel();
        Player thrower = player(h);
        boolean[] hold = {true};
        keepOn(h, thrower, s.a().ship(), s.a().helmPlot().east(), hold);
        Player rider = player(h);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        Vec3[] hung = new Vec3[1];
        h.runAfterDelay(20, () -> hook[0] = hookB(level, thrower, s.b()));
        h.runAfterDelay(grabAt(), () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + g.state());
            tieToCleat(h, level, thrower, g, s.a());
            Vec3 at = ropeAt(level, g, 0.3);
            rider.setPos(at.x, at.y - 1.6, at.z - 0.5);
            lookAt(rider, at);
            h.assertTrue(RopeSlideService.tryBoard(level, rider, g, 0.0) == RopeSlideService.Board.OK, "grabbing the rope was refused");
        });
        h.runAfterDelay(grabAt() + 3, () -> {
            h.assertTrue(rider.isPassenger(), "the player let go by itself");
            hung[0] = rider.position();
            // the thrower walks away beyond the rope's length: a tied rope holds
            hold[0] = false;
            Vec3 far = hook[0].position().add(-(GrappleConfig.MAX_ROPE_LENGTH.get() + 2.0), 0, 0);
            thrower.setPos(far.x, far.y, far.z);
        });
        h.runAfterDelay(grabAt() + 4, () -> {
            h.assertTrue(!hook[0].isRemoved() && rider.isPassenger(), "the tied rope let go when the thrower walked off");
            hung[0] = rider.position();
            level.removeBlock(cleatA(s.a()), false); // the cleat breaks
        });
        h.runAfterDelay(grabAt() + 6, () -> h.succeedWhen(() -> {
            h.assertTrue(hook[0].isRemoved(), "the rope did not let go of its broken cleat");
            h.assertTrue(!rider.isPassenger(), "the rider still hangs on a rope without its cleat");
            h.assertTrue(rider.position().distanceTo(hung[0]) < 1.5, "the player did not drop where it hung");
            assertNoRiders(h);
        }));
    }

    /**
     * A level rope: only ship B floats in the basin, and the rope is tied to a wall cleat on a stone pillar at x 3..4 at
     * the hook's height (the block is chosen once the hook hangs, so the horn is within half a block of it).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 220, batch = BATCH + "level")
    public static void aLevelRopeCrawlsTowardTheHook(GameTestHelper h) {
        ConfigOverrides.during(h, GrappleConfig.HAUL_FORCE, 0.0);
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        Fixture shipB = DryHullGameTests.assemble(h, DryHullGameTests.hull(h, 16, false));
        Ships s = new Ships(null, shipB);
        ServerLevel level = h.getLevel();
        Player thrower = player(h);
        Vec3 stand = h.absoluteVec(new Vec3(6.5, 9, 11.5));
        thrower.setPos(stand.x, stand.y, stand.z);
        Player rider = player(h);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        double[] t0 = new double[1];
        h.runAfterDelay(20, () -> hook[0] = hookB(level, thrower, s.b()));
        h.runAfterDelay(grabAt(), () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + g.state());
            // tie the near end to a wall cleat on a pillar at the hook's height: a level rope
            Vec3 far = h.relativeVec(g.ropeFarEnd(level));
            int y = (int) Math.floor(far.y);
            for (int py = 2; py <= y; py++) h.setBlock(new BlockPos(3, py, 11), Blocks.STONE);
            BlockPos cleat = new BlockPos(4, y, 11);
            h.setBlock(cleat, SailingGameTestsShips.cleat(AttachFace.WALL, Direction.EAST));
            GrappleService.tieOff(level, thrower, h.absolutePos(cleat));
            h.assertTrue(h.absolutePos(cleat).equals(g.tiedRing()), "the rope is not tied to the pillar's cleat: " + g.tiedRing());
            Vec3 at = ropeAt(level, g, 0.3);
            rider.setPos(at.x, at.y - 1.6, at.z - 0.5);
            lookAt(rider, at);
            h.assertTrue(RopeSlideService.tryBoard(level, rider, g, 0.0) == RopeSlideService.Board.OK, "grabbing the rope was refused");
            t0[0] = ((RopeRiderEntity) rider.getVehicle()).t();
        });
        h.runAfterDelay(grabAt() + 10, () -> {
            h.assertTrue(rider.getVehicle() instanceof RopeRiderEntity, "the player let go on a level rope");
            RopeRiderEntity r = (RopeRiderEntity) rider.getVehicle();
            Vec3 a = hook[0].ropeNearEnd(level);
            Vec3 b = hook[0].ropeFarEnd(level);
            h.assertTrue(RopeSlide.level(RopeSlide.slope(a.y, b.y, a.distanceTo(b))), "the test rope is not level: " + a + " / " + b);
            h.assertTrue(r.t() > t0[0], "the rider did not move toward the hook: t " + t0[0] + " -> " + r.t());
            double min = GrappleConfig.SLIDE_MIN_SPEED.get();
            h.assertTrue(Math.abs(r.speed() - min) < 1.0e-9, "a level rope is not crawled at slide_min_speed: " + r.speed());
            // ten ticks at the crawling speed, with a little slack for the ships' bobbing
            double moved = (r.t() - t0[0]) * a.distanceTo(b);
            h.assertTrue(moved > 8 * min && moved < 12 * min, "crawled " + moved + " blocks in ten ticks");
        });
        h.runAfterDelay(grabAt() + 11, () -> h.succeedWhen(() -> {
            h.assertTrue(!rider.isPassenger(), "still crawling");
            assertStandsOnB(h, level, rider, s.b());
            assertNoRiders(h);
            hook[0].release(GrappleRules.Release.NONE);
        }));
    }

    /**
     * GR6 (was GR5's pull down the own ship): from the mast top the thrower hooks their own deck below. Their hand-held
     * rope is no climb, so using it does nothing but hint; a line down the own ship needs the rope tied off.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = BATCH + "own_ship")
    public static void aRopeFromTheMastTopToTheOwnDeckIsNoClimb(GameTestHelper h) {
        Ships s = twoShips(h, true);
        ServerLevel level = h.getLevel();
        Player player = player(h);
        boolean[] hold = {true};
        keepOn(h, player, s.a().ship(), s.a().helmPlot().above(4), hold); // on top of the mast
        BlockPos edge = s.a().helmPlot().offset(2, -1, 0); // A's own east wall at deck height
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        Vec3[] at = new Vec3[1];
        h.runAfterDelay(20, () -> {
            Vec3 target = s.a().ship().toWorld(Vec3.atCenterOf(edge));
            hook[0] = GrappleService.launch(level, player, target.add(2.0, 0, 0), new Vec3(-GrappleConfig.THROW_VELOCITY.get(), 0, 0),
                    new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
        });
        h.runAfterDelay(grabAt(), () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch on the own ship: " + g.state());
            h.assertTrue(s.a().ship().id().equals(g.shipId()) && edge.equals(g.latchedBlock()), "latched on " + g.shipId() + " " + g.latchedBlock());
            h.assertTrue(g.haulKind() == GrappleRules.Haul.NONE, "a rope within one ship pulls: " + g.haulKind());
            hold[0] = false;
            at[0] = player.position();
            lookAt(player, ropeAt(level, g, 0.1));
            RopeSlideService.Board b = RopeSlideService.tryBoard(level, player, g, 0.0);
            h.assertTrue(b == RopeSlideService.Board.NOT_A_CLIMB, "a rope down to the own deck was used: " + b);
            h.assertTrue(RopeSlideService.lastHint(player) == level.getGameTime(), "no tie-off hint");
            h.assertTrue(!player.isPassenger() && !g.nearEndFixed(), "the player mounts the rope or its end was fixed");
        });
        h.runAfterDelay(grabAt() + 10, () -> {
            h.assertTrue(!player.isPassenger(), "the player rides the rope");
            assertNoRiders(h);
            h.assertTrue(player.position().distanceTo(at[0]) < 0.1, "the player moved");
            h.assertTrue(!hook[0].isRemoved() && hook[0].state() == GrapplingHookEntity.State.LATCHED, "the rope let go");
            hook[0].release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 220, batch = BATCH + "zip_line")
    public static void aRopeBetweenTwoCliffsIsAZipLine(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // stone ground, a high cliff at x 0..3 (top at y 8) and a low one at x 18..23 (top at y 3)
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                boolean high = x <= 3 && z >= 8 && z <= 14;
                boolean low = x >= 18 && z >= 8 && z <= 14;
                for (int y = 1; y <= (high ? 8 : low ? 3 : 0); y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        BlockPos cleat = new BlockPos(1, 9, 11);
        h.setBlock(cleat, SailingGameTestsShips.cleat(AttachFace.FLOOR, Direction.EAST));
        Player player = player(h);
        Vec3 top = h.absoluteVec(new Vec3(2.5, 9, 11.5));
        player.setPos(top.x, top.y, top.z);
        BlockPos face = new BlockPos(18, 3, 11);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> hook[0] = GrappleService.launch(level, player, h.absoluteVec(new Vec3(16.5, 3.5, 11.5)),
                new Vec3(GrappleConfig.THROW_VELOCITY.get(), 0, 0), new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true));
        h.runAfterDelay(grabAt(), () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED && g.onWorldBlock(), "the hook did not latch on the cliff: " + g.state());
            h.assertTrue(h.absolutePos(face).equals(g.latchedBlock()), "latched on " + g.latchedBlock());
            h.assertTrue(g.haulKind() == GrappleRules.Haul.NONE && !g.taut(), "a rope between two cliffs pulls");
            GrappleService.tieOff(level, player, h.absolutePos(cleat)); // GR5: a zip line needs its near end tied off
            h.assertTrue(h.absolutePos(cleat).equals(g.tiedRing()), "the rope is not tied to the cliff's cleat: " + g.tiedRing());
            lookAt(player, ropeAt(level, g, 0.1));
            RopeSlideService.Board b = RopeSlideService.tryBoard(level, player, g, 0.0);
            h.assertTrue(b == RopeSlideService.Board.OK, "grabbing the zip line was refused: " + b);
            h.assertTrue(player.getVehicle() instanceof RopeRiderEntity r && !r.pulling(), "the player does not slide on the zip line");
        });
        h.runAfterDelay(grabAt() + 1, () -> h.succeedWhen(() -> {
            h.assertTrue(!player.isPassenger(), "still sliding");
            Vec3 spot = h.absoluteVec(new Vec3(18.5, 4, 11.5));
            double d = player.position().distanceTo(spot);
            h.assertTrue(d < 1.0, "the player landed " + d + " blocks from the low cliff's top: " + player.position());
            h.assertTrue(player.getHealth() == player.getMaxHealth(), "hurt by the zip line");
            assertNoRiders(h);
            h.assertTrue(!hook[0].isRemoved() && hook[0].state() == GrapplingHookEntity.State.LATCHED, "the zip line let go");
            hook[0].release(GrappleRules.Release.NONE);
        }));
    }

    // ------------------------------------------------------------------ the accidental grab (GR4)

    /**
     * Stone ground at y 0 and a stone wall at x 20 (y 1..6); a survival player at (3.5, 1, 11.5) looking along +x with
     * a musket in the main hand, {@code offHooks} hooks in the off hand and gunpowder in the pack.
     */
    private static Player musketeer(GameTestHelper h, int offHooks) {
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                if (x == 20) {
                    for (int y = 1; y <= 6; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        Player p = player(h);
        Vec3 at = h.absoluteVec(new Vec3(3.5, 1, 11.5));
        p.setPos(at.x, at.y, at.z);
        p.setYRot(-90.0f);
        p.setXRot(0.0f);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CombatContent.MUSKET.get()));
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(CombatContent.GRAPPLING_HOOK.get(), offHooks));
        p.getInventory().add(new ItemStack(Items.GUNPOWDER, 4));
        return p;
    }

    /** Loads the off-hand hook into the musket (a whole loading session) and fires it (an aim and release). */
    private static GrapplingHookEntity fireFromMusket(GameTestHelper h, Player p) {
        ServerLevel level = h.getLevel();
        ItemStack gun = p.getMainHandItem();
        int reload = FirearmsConfig.type(FirearmKind.MUSKET).reloadTicks();
        h.assertTrue(gun.use(level, p, InteractionHand.MAIN_HAND).getResult().consumesAction(), "no loading session");
        gun.getItem().onUseTick(level, p, gun, FirearmRules.LOAD_SESSION_TICKS - reload);
        gun.releaseUsing(level, p, FirearmRules.LOAD_SESSION_TICKS - reload - 5);
        p.stopUsingItem();
        h.assertTrue(GrappleContent.isHookLoaded(gun), "the musket holds no hook");
        h.assertTrue(gun.use(level, p, InteractionHand.MAIN_HAND).getResult().consumesAction(), "no aim");
        // the attack key fires (FA1); letting go only lowers the gun
        FirearmTrigger.pullAimedFor(level, p, FirearmsConfig.AIM_STEADY_TICKS.get() + 5);
        gun.releaseUsing(level, p, FirearmRules.AIM_SESSION_TICKS - (FirearmsConfig.AIM_STEADY_TICKS.get() + 5));
        p.stopUsingItem();
        GrapplingHookEntity hook = GrappleService.hookOf(p);
        h.assertTrue(hook != null, "the musket did not fire the hook");
        return hook;
    }

    /**
     * The human's playtest (GR4): fire the hook from the musket, then press use again. The thrower looks along their own
     * rope (it runs from the hand to the hook), so the client's pick finds it and asks to grab it; before GR4 the server
     * hung the player on the rope and pinned its near end where they stood. Now the musket's use wins, with the off hand
     * empty or holding a spare hook, and a fresh rope cannot be grabbed even with an empty hand.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH + "reaim")
    public static void reaimingTheMusketAfterAShotNeverGrabsTheRope(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player bare = musketeer(h, 1); // the only hook went into the musket: the off hand is empty after the shot
        Player spare = musketeer(h, 2); // a second hook stays in the off hand
        Player[] players = {bare, spare};
        GrapplingHookEntity[] hooks = new GrapplingHookEntity[2];
        h.runAfterDelay(1, () -> {
            hooks[0] = fireFromMusket(h, bare);
            hooks[1] = fireFromMusket(h, spare);
            h.assertTrue(bare.getOffhandItem().isEmpty() && spare.getOffhandItem().getCount() == 1, "the off hands are not as planned");
        });
        h.runAfterDelay(8, () -> {
            for (GrapplingHookEntity g : hooks) {
                h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED && g.onWorldBlock(), "the hook did not latch on the wall: " + g.state());
            }
            // right after the shot not even an empty hand grabs the rope
            ItemStack gun = bare.getMainHandItem();
            bare.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            RopeSlideService.Board early = RopeSlideService.tryBoard(level, bare, hooks[0], RopeSlideService.SERVER_TOLERANCE);
            h.assertTrue(early == RopeSlideService.Board.TOO_SOON, "a fresh rope could be grabbed: " + early);
            bare.setItemInHand(InteractionHand.MAIN_HAND, gun);
        });
        h.runAfterDelay(10 + GrappleConfig.GRAB_COOLDOWN_TICKS.get(), () -> {
            for (int i = 0; i < 2; i++) {
                Player p = players[i];
                GrapplingHookEntity g = hooks[i];
                Vec3 a = g.ropeNearEnd(level);
                Vec3 b = g.ropeFarEnd(level);
                // the client's pick finds the rope along the look ray: this is what sent the grab in the playtest
                h.assertTrue(RopeSlide.pick(p.getEyePosition(), p.getLookAngle(), GrappleConfig.BOARD_REACH.get(), a, b,
                        GrappleConfig.BOARD_PICK_RADIUS.get()) != null, "the test does not look along the rope");
                RopeSlideService.Board r = RopeSlideService.tryBoard(level, p, g, RopeSlideService.SERVER_TOLERANCE);
                h.assertTrue(r == RopeSlideService.Board.HAND_BUSY, "using the rope with the musket in hand was not refused: " + r);
                h.assertTrue(!p.isPassenger(), "the player mounts the rope");
                h.assertTrue(!g.nearEndFixed(), "the rope's near end was fixed where the player stands");
            }
            assertNoRiders(h);
            // the use goes to the musket: with a spare hook in the off hand it loads it again
            InteractionResult r = spare.getMainHandItem().use(level, spare, InteractionHand.MAIN_HAND).getResult();
            h.assertTrue(r.consumesAction() && spare.isUsingItem(), "the musket did not start loading the spare hook: " + r);
            spare.stopUsingItem();
            // an intentional use with an empty hand gets past the musket guard, but the hook sits at eye height, less
            // than climb_min_rise above the feet: no climb, only the tie-off hint (GR6)
            bare.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            RopeSlideService.Board ok = RopeSlideService.tryBoard(level, bare, hooks[0], RopeSlideService.SERVER_TOLERANCE);
            h.assertTrue(ok == RopeSlideService.Board.NOT_A_CLIMB && !bare.isPassenger(),
                    "an empty hand on a level rope was not refused as no climb: " + ok);
            h.assertTrue(!hooks[0].nearEndFixed(), "the use fixed the rope's near end");
            hooks[0].release(GrappleRules.Release.NONE);
            hooks[1].release(GrappleRules.Release.NONE);
        });
        h.runAfterDelay(13 + GrappleConfig.GRAB_COOLDOWN_TICKS.get(), () -> {
            h.assertTrue(!bare.isPassenger(), "the rider still hangs on a released rope");
            assertNoRiders(h);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ refusals

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = BATCH + "disabled")
    public static void disabledSlideRefusesTheRopeAndDropsRiders(GameTestHelper h) {
        Ships s = twoShips(h, false);
        ServerLevel level = h.getLevel();
        Player thrower = player(h);
        keepOn(h, thrower, s.a().ship(), s.a().helmPlot().east(), new boolean[]{true});
        Player rider = player(h);
        Player other = player(h);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> hook[0] = hookB(level, thrower, s.b()));
        h.runAfterDelay(grabAt(), () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + g.state());
            tieToCleat(h, level, thrower, g, s.a());
            Vec3 at = ropeAt(level, g, 0.5);
            for (Player p : new Player[]{rider, other}) {
                p.setPos(at.x, at.y - 1.6, at.z - 0.5);
                lookAt(p, at);
            }
            h.assertTrue(RopeSlideService.tryBoard(level, rider, g, 0.0) == RopeSlideService.Board.OK, "grabbing the rope was refused");
            ConfigOverrides.during(h, GrappleConfig.SLIDE_ENABLED, false);
            RopeSlideService.Board b = RopeSlideService.tryBoard(level, other, g, 0.0);
            h.assertTrue(b == RopeSlideService.Board.DISABLED, "a disabled slide let the player grab the rope: " + b);
            h.assertTrue(!other.isPassenger(), "the refused player rides");
        });
        h.runAfterDelay(grabAt() + 2, () -> {
            h.assertTrue(!rider.isPassenger(), "a rider kept sliding after the slide was disabled");
            assertNoRiders(h);
            h.assertTrue(!hook[0].isRemoved(), "disabling the slide released the hook");
            hook[0].release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void mobsNeverRideTheRope(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        GrapplingHookEntity hook = new GrapplingHookEntity(GrappleContent.HOOK.get(), level); // never added: only its id is used
        Vec3 at = h.absoluteVec(new Vec3(4.5, 4, 4.5));
        RopeRiderEntity rider = RopeRiderEntity.create(level, hook, 0.5, at);
        level.addFreshEntity(rider);
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, new Vec3(4.5, 1, 4.5));
        h.assertTrue(!pig.startRiding(rider), "a pig could get onto the rope");
        h.assertTrue(pig.startRiding(rider, true), "forcing the pig on failed (test setup)");
        h.succeedWhen(() -> {
            h.assertTrue(!pig.isPassenger(), "the pig still hangs on the rope");
            h.assertTrue(rider.isRemoved(), "the rider stayed after throwing the pig off");
        });
    }
}

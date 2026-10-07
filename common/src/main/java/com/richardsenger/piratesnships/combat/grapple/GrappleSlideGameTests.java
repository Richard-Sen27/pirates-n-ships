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
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/**
 * Sliding along a latched grappling rope (GR2, docs/design.md §8.3) in a real server. Two closed 5×4×5 plank hulls of
 * {@link DryHullGameTests} float in one basin as in {@link GrappleGameTests}: ship A at x 2..6 (the thrower's), ship B
 * at x 16..20; the hook bites into B's west wall at deck height. For the crow's nest A carries a three-plank mast on
 * its helm, the thrower standing on top. Hauling is switched off ({@code haul_force} 0, so every test that changes it
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
        return new Ships(DryHullGameTests.assemble(h, helmA), DryHullGameTests.assemble(h, helmB));
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

    // ------------------------------------------------------------------ sliding

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH + "crows_nest")
    public static void thrownFromTheCrowsNestTheThrowerSlidesDownAndLandsOnTheOtherShip(GameTestHelper h) {
        Ships s = twoShips(h, true);
        ServerLevel level = h.getLevel();
        Player player = player(h);
        boolean[] hold = {true};
        keepOn(h, player, s.a().ship(), s.a().helmPlot().above(4), hold); // on top of the mast
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        long[] boardedAt = {-1};
        h.runAfterDelay(20, () -> hook[0] = hookB(level, player, s.b()));
        h.runAfterDelay(26, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + g.state());
            Vec3 near = g.ropeNearEnd(level);
            Vec3 far = g.ropeFarEnd(level);
            h.assertTrue(near.y > far.y + 4.0, "the crow's nest is not high above the hook: " + near + " / " + far);
            hold[0] = false;
            player.fallDistance = 6.0f;
            lookAt(player, ropeAt(level, g, 0.1));
            RopeSlideService.Board b = RopeSlideService.tryBoard(level, player, g, 0.0);
            h.assertTrue(b == RopeSlideService.Board.OK, "grabbing the rope was refused: " + b);
            h.assertTrue(player.getVehicle() instanceof RopeRiderEntity, "the player does not hang on a rope rider");
            RopeRiderEntity rider = (RopeRiderEntity) player.getVehicle();
            h.assertTrue(rider.t() < 0.2, "grabbed the rope far from the crow's nest: t=" + rider.t());
            h.assertTrue(g.pinPos() != null && s.a().ship().id().equals(g.pinShip()), "the thrower's rope end was not pinned on ship A");
            h.assertTrue(player.fallDistance == 0.0f, "grabbing the rope did not reset the fall distance");
            boardedAt[0] = h.getTick();
        });
        h.runAfterDelay(30, () -> {
            h.assertTrue(player.isPassenger(), "the player let go after a few ticks");
            Vec3 grip = player.position().add(0, GrappleConfig.HANG_OFFSET.get(), 0);
            GrapplingHookEntity g = hook[0];
            Vec3 a = g.ropeNearEnd(level);
            Vec3 b = g.ropeFarEnd(level);
            double off = grip.distanceTo(RopeSlide.at(a, b, RopeSlide.parameter(a, b, grip)));
            h.assertTrue(off < 0.3, "the player does not hang hang_offset below the rope: " + off + " off");
            h.assertTrue(player.fallDistance == 0.0f, "falling while sliding");
        });
        h.runAfterDelay(31, () -> h.succeedWhen(() -> {
            h.assertTrue(!player.isPassenger(), "still sliding");
            long took = h.getTick() - boardedAt[0];
            // about 28 ticks for the 12.8 block rope (RopeSlideTest), a little slack for the ships' bobbing
            h.assertTrue(took <= 50, "the slide took " + took + " ticks");
            assertStandsOnB(h, level, player, s.b());
            h.assertTrue(player.fallDistance == 0.0f, "landed with fall distance " + player.fallDistance);
            h.assertTrue(player.getHealth() == player.getMaxHealth(), "hurt by the slide");
            assertNoRiders(h);
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!g.isRemoved() && g.state() == GrapplingHookEntity.State.LATCHED, "the pinned rope let go when the thrower left it");
            g.release(GrappleRules.Release.NONE);
        }));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 160, batch = BATCH + "water")
    public static void aSwimmerGrabsTheRopeMidwayAndSlidesToTheLowerShip(GameTestHelper h) {
        Ships s = twoShips(h, false);
        ServerLevel level = h.getLevel();
        Player thrower = player(h);
        keepOn(h, thrower, s.a().ship(), s.a().helmPlot().east(), new boolean[]{true});
        Player swimmer = player(h);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> hook[0] = hookB(level, thrower, s.b()));
        h.runAfterDelay(26, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + g.state());
            Vec3 mid = ropeAt(level, g, 0.5);
            Vec3 water = h.absoluteVec(new Vec3(0, 6.4, 0));
            swimmer.setPos(mid.x, water.y, mid.z); // treading water under the middle of the rope
            h.assertTrue(level.getBlockState(swimmer.blockPosition()).is(Blocks.WATER), "the swimmer is not in the water");
            lookAt(swimmer, mid);
            RopeSlideService.Board b = RopeSlideService.tryBoard(level, swimmer, g, 0.0);
            h.assertTrue(b == RopeSlideService.Board.OK, "grabbing the rope from the water was refused: " + b);
            RopeRiderEntity rider = (RopeRiderEntity) swimmer.getVehicle();
            h.assertTrue(Math.abs(rider.t() - 0.5) < 0.1, "grabbed the rope at t=" + rider.t() + " instead of the middle");
            h.assertTrue(g.pinPos() == null, "a rope grabbed by someone else was pinned");
        });
        h.runAfterDelay(27, () -> h.succeedWhen(() -> {
            h.assertTrue(!swimmer.isPassenger(), "still sliding");
            assertStandsOnB(h, level, swimmer, s.b());
            h.assertTrue(swimmer.getHealth() == swimmer.getMaxHealth(), "hurt by the slide");
            assertNoRiders(h);
            hook[0].release(GrappleRules.Release.NONE);
        }));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = BATCH + "sneak")
    public static void sneakingLetsGoWhereThePlayerHangs(GameTestHelper h) {
        Ships s = twoShips(h, false);
        ServerLevel level = h.getLevel();
        Player thrower = player(h);
        keepOn(h, thrower, s.a().ship(), s.a().helmPlot().east(), new boolean[]{true});
        Player rider = player(h);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        Vec3[] hung = new Vec3[1];
        h.runAfterDelay(20, () -> hook[0] = hookB(level, thrower, s.b()));
        h.runAfterDelay(26, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + g.state());
            Vec3 at = ropeAt(level, g, 0.3);
            rider.setPos(at.x, at.y - 1.6, at.z - 0.5);
            lookAt(rider, at);
            h.assertTrue(RopeSlideService.tryBoard(level, rider, g, 0.0) == RopeSlideService.Board.OK, "grabbing the rope was refused");
        });
        h.runAfterDelay(29, () -> {
            h.assertTrue(rider.getVehicle() instanceof RopeRiderEntity, "the player let go by itself");
            rider.fallDistance = 4.0f; // as if it had been falling: the next ride tick resets it
        });
        h.runAfterDelay(30, () -> {
            h.assertTrue(rider.fallDistance == 0.0f, "the slide does not reset the fall distance");
            hung[0] = rider.position();
            rider.setShiftKeyDown(true);
        });
        h.runAfterDelay(31, () -> h.succeedWhen(() -> {
            h.assertTrue(!rider.isPassenger(), "sneaking did not let go");
            double moved = rider.position().distanceTo(hung[0]);
            h.assertTrue(moved < 1.0, "the player was moved " + moved + " blocks on letting go, it should drop where it hung");
            h.assertTrue(rider.fallDistance == 0.0f, "the fall distance of the slide was kept: " + rider.fallDistance);
            assertNoRiders(h);
            hook[0].release(GrappleRules.Release.NONE);
        }));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = BATCH + "release")
    public static void releasingTheHookDropsTheRider(GameTestHelper h) {
        Ships s = twoShips(h, false);
        ServerLevel level = h.getLevel();
        Player thrower = player(h);
        keepOn(h, thrower, s.a().ship(), s.a().helmPlot().east(), new boolean[]{true});
        Player rider = player(h);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        Vec3[] hung = new Vec3[1];
        h.runAfterDelay(20, () -> hook[0] = hookB(level, thrower, s.b()));
        h.runAfterDelay(26, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + g.state());
            Vec3 at = ropeAt(level, g, 0.3);
            rider.setPos(at.x, at.y - 1.6, at.z - 0.5);
            lookAt(rider, at);
            h.assertTrue(RopeSlideService.tryBoard(level, rider, g, 0.0) == RopeSlideService.Board.OK, "grabbing the rope was refused");
        });
        h.runAfterDelay(29, () -> {
            h.assertTrue(rider.isPassenger(), "the player let go by itself");
            hung[0] = rider.position();
            h.assertTrue(GrappleService.release(thrower), "the thrower had no hook to release");
        });
        h.runAfterDelay(30, () -> h.succeedWhen(() -> {
            h.assertTrue(!rider.isPassenger(), "the rider still hangs on a released rope");
            h.assertTrue(rider.position().distanceTo(hung[0]) < 1.0, "the player did not drop where it hung");
            assertNoRiders(h);
        }));
    }

    // ------------------------------------------------------------------ refusals

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100, batch = BATCH + "disabled")
    public static void disabledSlideRefusesTheRopeAndDropsRiders(GameTestHelper h) {
        Ships s = twoShips(h, false);
        ServerLevel level = h.getLevel();
        Player thrower = player(h);
        keepOn(h, thrower, s.a().ship(), s.a().helmPlot().east(), new boolean[]{true});
        Player rider = player(h);
        Player other = player(h);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> hook[0] = hookB(level, thrower, s.b()));
        h.runAfterDelay(26, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + g.state());
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
        h.runAfterDelay(28, () -> {
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

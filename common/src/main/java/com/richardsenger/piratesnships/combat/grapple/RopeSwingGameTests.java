package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.decor.railing.RailingBlock;
import com.richardsenger.piratesnships.ship.decor.railing.RailingContent;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Locale;

/**
 * GR7 in a real server: the gentler hand throw, the pull's cooldown, and hanging and swinging on the own hand-held rope
 * (docs/design.md §8.3). The swinging players are mock players added to the level, so the server moves them like any
 * entity (vanilla's gravity and drag) and runs the swing at the end of their tick ({@link RopeSwingService#onPlayerTick}).
 *
 * <p>The land rig: stone floor at y 0, a stone platform x 0..4, z 2..6, y 1..5 (standing height 6), and a stone beam
 * x 4..12, z 4 at y 11. The hook is fired straight up into the beam's underside at x 8.5 and bites there, about 4.5
 * blocks above the platform's top.
 */
public final class RopeSwingGameTests {

    private static final double HOOK_X = 8.5;
    private static final double HOOK_Z = 4.5;

    private RopeSwingGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(RopeSwingGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** A survival mock player in the level at {@code world} facing {@code yRot}, discarded when the test ends. */
    static Player playerInLevel(GameTestHelper h, Vec3 world, float yRot) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.moveTo(world.x, world.y, world.z, yRot, 0f);
        h.getLevel().addFreshEntity(player);
        testInfo(h).addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo testInfo) { }
            @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { player.discard(); }
            @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { player.discard(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { player.discard(); }
        });
        return player;
    }

    /** {@code GameTestHelper#testInfo} is private and has no getter in 1.21.1. */
    private static GameTestInfo testInfo(GameTestHelper helper) {
        try {
            for (Field f : GameTestHelper.class.getDeclaredFields()) {
                if (f.getType() == GameTestInfo.class) {
                    f.setAccessible(true);
                    return (GameTestInfo) f.get(helper);
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("GameTestHelper has no GameTestInfo field");
    }

    /** The land rig of the class comment. */
    private static void landRig(GameTestHelper h) {
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        for (int x = 0; x <= 4; x++) {
            for (int z = 2; z <= 6; z++) {
                for (int y = 1; y <= 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        for (int x = 4; x <= 12; x++) {
            h.setBlock(new BlockPos(x, 11, 4), Blocks.STONE);
        }
        SailingGameTestsShips.openSky(h, 24); // room to stand on the beam
    }

    /** Fires {@code owner}'s hook straight up into the beam at x 8.5 (it bites into the underside). */
    private static GrapplingHookEntity hookBeam(GameTestHelper h, Player owner) {
        return GrappleService.launch(h.getLevel(), owner, h.absoluteVec(new Vec3(HOOK_X, 9.0, HOOK_Z)), new Vec3(0, 0.7, 0),
                new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
    }

    private static Vec3 hand(Player p) {
        return RopeSwingService.hand(p);
    }

    private static void assertLatchedOnBeam(GameTestHelper h, GrapplingHookEntity g) {
        h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED && g.onWorldBlock(), "the hook did not latch on the beam: " + g.state());
        Vec3 at = g.ropeFarEnd(h.getLevel());
        h.assertTrue(Math.abs(at.y - h.absoluteVec(new Vec3(0, 11, 0)).y) < 0.3, "the hook is not under the beam: " + at);
    }

    /** The player's hand's distance from the hook beyond the rope's length (negative: slack). */
    private static double beyond(GameTestHelper h, Player p, GrapplingHookEntity g) {
        return hand(p).distanceTo(g.ropeFarEnd(h.getLevel())) - g.swingLength();
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    // ------------------------------------------------------------------ the hand throw

    /**
     * A hook thrown by hand ({@link GrapplingHookItem#use}) at a railing two blocks up and four blocks away still gets
     * there with the gentler throw and latches on it (GR8: aimed a little above it, as its arc falls).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aHandThrowLatchesARailTwoUpAtFourBlocks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        h.setBlock(new BlockPos(5, 1, 4), Blocks.STONE);
        BlockPos rail = h.absolutePos(new BlockPos(5, 2, 4));
        BlockState s = RailingContent.RAILINGS.get(ShipWood.OAK).defaultBlockState().setValue(RailingBlock.WEST, true);
        level.setBlock(rail, RailingBlock.withPosts(s, level, rail), 3);
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.getInventory().clearContent();
        Vec3 feet = h.absoluteVec(new Vec3(1.5, 1, 4.5));
        p.setPos(feet.x, feet.y, feet.z);
        p.setOnGround(true);
        Vec3 target = Vec3.atLowerCornerOf(rail).add(0.1, 0.5, 0.5); // the west rail, about 1.5 above the feet
        Vec3 d = target.subtract(p.getEyePosition().subtract(0, 0.1, 0)); // from where a thrown item starts
        p.setYRot((float) Math.toDegrees(-Math.atan2(d.x, d.z)));
        // GR8: the throw falls on a real arc, so the thrower aims a little above the rail to drop onto it
        double pitch = HookFlight.aimPitch(GrappleConfig.HAND_THROW_VELOCITY.get(), d.horizontalDistance(), d.y,
                GrappleConfig.gravity(GrappleLaunch.Mode.THROW));
        h.assertTrue(!Double.isNaN(pitch), "no hand throw reaches the rail");
        p.setXRot((float) -pitch);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CombatContent.GRAPPLING_HOOK.get()));
        p.getMainHandItem().use(level, p, InteractionHand.MAIN_HAND);
        GrapplingHookEntity g = GrappleService.hookOf(p);
        h.assertTrue(g != null, "the hook was not thrown");
        h.assertTrue(Math.abs(g.getDeltaMovement().length() - GrappleConfig.HAND_THROW_VELOCITY.get()) < 0.05,
                "the hand throw flies at " + g.getDeltaMovement().length());
        h.succeedWhen(() -> {
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook is " + g.state() + " at " + g.position());
            h.assertTrue(rail.equals(g.latchedBlock()), "latched on " + g.latchedBlock() + ", not the railing " + rail);
            h.assertTrue(g.onStrongPoint(), "the railing does not hold the hook like a cleat");
            g.release(GrappleRules.Release.NONE);
        });
    }

    // ------------------------------------------------------------------ hanging and swinging

    /**
     * A player steps off the platform's edge with the hook out above the gap: the rope takes their weight at the
     * length it had, they swing down under the hook and past it, the hand never more than a hair beyond the rope's length
     * over 40 ticks, and no fall damage.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 140)
    public static void steppingOffAnEdgeTheRopeHoldsAndSwingsThePlayer(GameTestHelper h) {
        landRig(h);
        ServerLevel level = h.getLevel();
        Player[] player = {null};
        GrapplingHookEntity[] hook = {null};
        double[] worst = {Double.NEGATIVE_INFINITY};
        double[] farthestX = {Double.NEGATIVE_INFINITY};
        double[] length = {Double.NaN};
        int[] tick = {0};
        h.onEachTick(() -> {
            int t = tick[0]++;
            if (t == 10) {
                player[0] = playerInLevel(h, h.absoluteVec(new Vec3(4.2, 6, HOOK_Z)), -90f);
            }
            if (t == 12) {
                hook[0] = hookBeam(h, player[0]);
            }
            if (t == 20) {
                assertLatchedOnBeam(h, hook[0]);
                h.assertTrue(!RopeSwing.holding(hook[0].swingLength()), "the rope holds a player standing on the platform");
                Vec3 off = h.absoluteVec(new Vec3(5.45, 6, HOOK_Z)); // just past the edge
                player[0].setPos(off.x, off.y, off.z);
            }
            if (t > 22 && t <= 62) {
                GrapplingHookEntity g = hook[0];
                h.assertTrue(RopeSwing.holding(g.swingLength()), "tick " + t + ": the rope does not hold the player");
                if (Double.isNaN(length[0])) length[0] = g.swingLength();
                worst[0] = Math.max(worst[0], beyond(h, player[0], g));
                farthestX[0] = Math.max(farthestX[0], player[0].getX());
            }
        });
        h.runAfterDelay(64, () -> {
            Player p = player[0];
            GrapplingHookEntity g = hook[0];
            Vec3 hookAt = g.ropeFarEnd(level);
            Constants.LOG.info("[GR7] stepping off: rope {}, farthest beyond it {}, farthest x {} (hook at x {})",
                    f(length[0]), f(worst[0]), f(farthestX[0]), f(hookAt.x));
            h.assertTrue(worst[0] <= 0.1, "the hand got " + worst[0] + " blocks beyond the rope's length");
            h.assertTrue(Math.abs(g.swingLength() - length[0]) < 1.0e-6, "the rope's length changed without a pull");
            h.assertTrue(farthestX[0] > hookAt.x + 0.5, "the player did not swing past the hook: farthest x " + farthestX[0]);
            h.assertTrue(p.getY() > h.absoluteVec(new Vec3(0, 3, 0)).y, "the player fell: " + p.position());
            h.assertTrue(p.getHealth() == p.getMaxHealth(), "the player got hurt");
            g.release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }

    /**
     * GR8: hanging still under the hook, holding forward for three seconds only leans the player a little (gravity
     * dominates; GR7's push plus vanilla's air control flew them out), while pumping (forward as they swing forward,
     * back as they swing back) builds a swing of more than a block; the speed stays under {@code swing_max_speed}.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 380)
    public static void pumpingSwingsAHangingPlayerAndHoldingOnlyLeans(GameTestHelper h) {
        landRig(h);
        Player[] player = {null};
        GrapplingHookEntity[] hook = {null};
        double[] held = {0};
        double[] pumped = {0};
        double[] fastest = {0};
        Vec3[] last = {null};
        int[] tick = {0};
        h.onEachTick(() -> {
            int t = tick[0]++;
            if (t == 10) {
                // hanging straight under the hook, about 4 blocks of rope, facing +z (along the beam's side)
                player[0] = playerInLevel(h, h.absoluteVec(new Vec3(HOOK_X, 5.6, HOOK_Z)), 0f);
            }
            if (t == 12) {
                hook[0] = hookBeam(h, player[0]);
            }
            Player p = player[0];
            if (p == null || hook[0] == null) {
                return;
            }
            Vec3 at = hook[0].ropeFarEnd(h.getLevel());
            if (at == null) {
                return;
            }
            double out = p.getZ() - at.z;
            double vz = last[0] == null ? 0.0 : p.getZ() - last[0].z;
            if (last[0] != null && t >= 50) fastest[0] = Math.max(fastest[0], p.position().distanceTo(last[0]));
            last[0] = p.position();
            if (t >= 50 && t < 110) {
                p.zza = 1f; // held forward
                held[0] = Math.max(held[0], Math.abs(out));
            } else if (t >= 110 && t < 360) {
                p.zza = vz >= 0 ? 1f : -1f; // pumped in time with the swing
                pumped[0] = Math.max(pumped[0], Math.abs(out));
            } else {
                p.zza = 0f;
            }
        });
        h.runAfterDelay(49, () -> {
            GrapplingHookEntity g = hook[0];
            assertLatchedOnBeam(h, g);
            h.assertTrue(RopeSwing.holding(g.swingLength()), "the rope does not hold the hanging player");
            Vec3 at = g.ropeFarEnd(h.getLevel());
            double off = Math.hypot(player[0].getX() - at.x, player[0].getZ() - at.z);
            h.assertTrue(off < 0.3, "the player does not hang still under the hook: " + off);
        });
        h.runAfterDelay(362, () -> {
            Constants.LOG.info("[GR8] swing: held forward 3 s leans {} blocks out, pumping reaches {} blocks, fastest {} blocks/tick",
                    f(held[0]), f(pumped[0]), f(fastest[0]));
            h.assertTrue(held[0] < 0.8, "holding forward for three seconds swung the player " + held[0] + " blocks out");
            h.assertTrue(pumped[0] > 1.0, "pumping swung the player only " + pumped[0] + " blocks out");
            // the displacement per tick includes vanilla's own air control of the input (0.02)
            h.assertTrue(fastest[0] <= GrappleConfig.SWING_MAX_SPEED.get() + 0.03, "the swing ran at " + fastest[0] + " blocks per tick");
            h.assertTrue(beyond(h, player[0], hook[0]) <= 0.1, "the push stretched the rope");
            hook[0].release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }

    /**
     * Hanging under the hook: a pull shortens the rope by {@code rope_climb_step} and lifts the player; a second use in
     * the same tick does nothing (the pull's cooldown, shown on the hook item); after the cooldown a pull takes again.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 160)
    public static void pullsClimbTheRopeOncePerCooldown(GameTestHelper h) {
        landRig(h);
        ServerLevel level = h.getLevel();
        Player[] player = {null};
        GrapplingHookEntity[] hook = {null};
        double[] before = {0, 0};
        int cooldown = GrappleConfig.PULL_COOLDOWN_TICKS.get();
        double step = GrappleConfig.ROPE_CLIMB_STEP.get();
        h.runAfterDelay(10, () -> player[0] = playerInLevel(h, h.absoluteVec(new Vec3(HOOK_X, 4.0, HOOK_Z)), 0f));
        h.runAfterDelay(12, () -> hook[0] = hookBeam(h, player[0]));
        h.runAfterDelay(50, () -> {
            GrapplingHookEntity g = hook[0];
            Player p = player[0];
            assertLatchedOnBeam(h, g);
            h.assertTrue(RopeSwing.holding(g.swingLength()), "the rope does not hold the hanging player");
            before[0] = g.swingLength();
            before[1] = hand(p).y;
            // the rope is taken in from its length, or from the hand's distance when that is shorter (a slack rope)
            double from = Math.min(before[0], hand(p).distanceTo(g.ropeFarEnd(level)));
            RopeSlideService.Board first = RopeSlideService.tryBoard(level, p, g, 0.0);
            h.assertTrue(first == RopeSlideService.Board.OK, "the pull was refused: " + first);
            h.assertTrue(Math.abs(g.swingLength() - (from - step)) < 1.0e-6 && before[0] - from < 0.2,
                    "the pull took the rope from " + before[0] + " (hand at " + from + ") to " + g.swingLength());
            h.assertTrue(p.getCooldowns().isOnCooldown(CombatContent.GRAPPLING_HOOK.get()), "the hook item shows no cooldown");
            double after = g.swingLength();
            RopeSlideService.Board second = RopeSlideService.tryBoard(level, p, g, 0.0);
            h.assertTrue(second == RopeSlideService.Board.COOLDOWN, "a second use in the same tick: " + second);
            h.assertTrue(g.swingLength() == after, "the second use shortened the rope again");
            h.assertTrue(!p.isPassenger(), "the pull mounted the player on something");
        });
        h.runAfterDelay(50 + cooldown - 1, () -> {
            RopeSlideService.Board early = RopeSlideService.tryBoard(level, player[0], hook[0], 0.0);
            h.assertTrue(early == RopeSlideService.Board.COOLDOWN, "a use before the cooldown ended: " + early);
        });
        h.runAfterDelay(50 + cooldown + 1, () -> {
            Player p = player[0];
            GrapplingHookEntity g = hook[0];
            double rise = hand(p).y - before[1];
            Constants.LOG.info("[GR7] one pull lifted the hand {} blocks in {} ticks (rope {} -> {})", f(rise), cooldown + 1,
                    f(before[0]), f(g.swingLength()));
            h.assertTrue(rise > step * 0.7, "one pull lifted the player only " + rise + " blocks");
            h.assertTrue(beyond(h, p, g) <= 0.15, "the hand hangs " + beyond(h, p, g) + " beyond the shortened rope");
            double length = g.swingLength();
            RopeSlideService.Board again = RopeSlideService.tryBoard(level, p, g, 0.0);
            h.assertTrue(again == RopeSlideService.Board.OK, "the pull after the cooldown was refused: " + again);
            h.assertTrue(g.swingLength() < length - 0.5 || !RopeSwing.holding(g.swingLength()), "the second pull did not take");
            g.release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }

    /**
     * Climbing all the way: from hanging 4 blocks under the beam, pulls once per cooldown bring the player up and, within
     * reach of the hook, onto the standing spot on top of the beam.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void repeatedPullsClimbOntoTheBeam(GameTestHelper h) {
        landRig(h);
        ServerLevel level = h.getLevel();
        Player[] player = {null};
        GrapplingHookEntity[] hook = {null};
        int[] tick = {0};
        int cooldown = GrappleConfig.PULL_COOLDOWN_TICKS.get();
        h.onEachTick(() -> {
            int t = tick[0]++;
            if (t == 10) player[0] = playerInLevel(h, h.absoluteVec(new Vec3(HOOK_X, 4.0, HOOK_Z)), 0f);
            if (t == 12) hook[0] = hookBeam(h, player[0]);
            if (t >= 50 && (t - 50) % (cooldown + 1) == 0 && hook[0] != null && !hook[0].isRemoved()) {
                RopeSlideService.tryBoard(level, player[0], hook[0], 0.0);
            }
        });
        h.runAfterDelay(51, () -> h.succeedWhen(() -> {
            Vec3 top = h.absoluteVec(new Vec3(HOOK_X, 12, HOOK_Z));
            Player p = player[0];
            h.assertTrue(p.position().distanceTo(top) < 1.0, "the player is not on top of the beam: " + p.position() + " vs " + top);
            h.assertTrue(!RopeSwing.holding(hook[0].swingLength()), "the rope still holds the player at the top");
            h.assertTrue(p.getHealth() == p.getMaxHealth(), "hurt by the climb");
            hook[0].release(GrappleRules.Release.NONE);
        }));
    }

    /** Hanging on the rope, taking another item in hand lets go: the player drops, the rope pays out, the hook stays. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120)
    public static void holdingAnotherItemLetsGoOfTheRope(GameTestHelper h) {
        landRig(h);
        Player[] player = {null};
        GrapplingHookEntity[] hook = {null};
        double[] y = {0};
        h.runAfterDelay(10, () -> player[0] = playerInLevel(h, h.absoluteVec(new Vec3(HOOK_X, 4.0, HOOK_Z)), 0f));
        h.runAfterDelay(12, () -> hook[0] = hookBeam(h, player[0]));
        h.runAfterDelay(50, () -> {
            assertLatchedOnBeam(h, hook[0]);
            h.assertTrue(RopeSwing.holding(hook[0].swingLength()), "the rope does not hold the hanging player");
            y[0] = player[0].getY();
            player[0].setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        });
        h.runAfterDelay(60, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!RopeSwing.holding(g.swingLength()), "the rope still holds a player with a stick in hand");
            h.assertTrue(player[0].getY() < y[0] - 1.0, "the player did not drop: " + y[0] + " -> " + player[0].getY());
            h.assertTrue(!g.isRemoved() && g.state() == GrapplingHookEntity.State.LATCHED, "letting go released the hook");
            g.release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }

    /** {@code grapple.swing} off: the rope never holds the player in the air, and a use of it does not climb. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = "pirates_n_ships_config_grapple_swing_off")
    public static void withSwingOffTheRopeDoesNotHoldThePlayer(GameTestHelper h) {
        ConfigOverrides.during(h, GrappleConfig.SWING, false);
        landRig(h);
        Player[] player = {null};
        GrapplingHookEntity[] hook = {null};
        double[] y = {0};
        h.runAfterDelay(10, () -> player[0] = playerInLevel(h, h.absoluteVec(new Vec3(HOOK_X, 4.0, HOOK_Z)), 0f));
        h.runAfterDelay(12, () -> hook[0] = hookBeam(h, player[0]));
        h.runAfterDelay(50, () -> {
            GrapplingHookEntity g = hook[0];
            assertLatchedOnBeam(h, g);
            h.assertTrue(!RopeSwing.holding(g.swingLength()), "the rope holds the player with the swing off");
            h.assertTrue(player[0].onGround(), "the player did not fall to the floor: " + player[0].position());
            y[0] = player[0].getY();
            RopeSlideService.Board b = RopeSlideService.tryBoard(h.getLevel(), player[0], g, 0.0);
            h.assertTrue(b == RopeSlideService.Board.BUSY, "a use climbed the rope with the swing off: " + b);
            h.assertTrue(!RopeSwing.holding(g.swingLength()), "the use took the rope in");
        });
        h.runAfterDelay(60, () -> {
            h.assertTrue(Math.abs(player[0].getY() - y[0]) < 0.01, "the player moved: " + y[0] + " -> " + player[0].getY());
            hook[0].release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ on a ship under way

    /**
     * A hull afloat with a log mast at x 12, z 10 (deck y 9 to y 13) and an oak fence yard at y 13 reaching out over the
     * hull's north side to z 7; the hook bites under the yard's outer end, the player hangs from it 1.5 blocks outside
     * the hull, over the water. The ship is held at 4 m/s along x: the hook moves with it and the rope keeps the
     * player within its length, under the hook.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200)
    public static void aPlayerHangingOverAShipsSideIsCarriedUnderWay(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 39, true);
        SailingGameTestsShips.openSky(h, 40);
        BlockPos helmRel = DryHullGameTests.hull(h, 9, false);
        for (int y = 9; y <= 13; y++) h.setBlock(new BlockPos(12, y, 10), Blocks.OAK_LOG);
        for (int z = 7; z <= 9; z++) h.setBlock(new BlockPos(12, 13, z), Blocks.OAK_FENCE);
        Fixture fx = DryHullGameTests.assemble(h, helmRel);
        ShipBody ship = fx.ship();
        ServerLevel level = h.getLevel();
        BlockPos yardEnd = fx.helmPlot().offset(12 - helmRel.getX(), 13 - helmRel.getY(), 7 - helmRel.getZ());
        Vec3 linear = new Vec3(4, 0, 0);
        int start = 40;
        int span = 40;
        Player[] player = {null};
        GrapplingHookEntity[] hook = {null};
        double[] worst = {Double.NEGATIVE_INFINITY};
        double[] across = {0};
        Vec3[] hookStart = {null};
        int[] tick = {0};
        Vector3d lin = new Vector3d(), ang = new Vector3d();
        h.onEachTick(() -> {
            int t = tick[0]++;
            if (t >= 15 && t <= start + span) {
                ship.velocities(lin, ang);
                ship.addVelocity(new Vector3d(linear.x - lin.x, 0, linear.z - lin.z), new Vector3d(0, -ang.y, 0));
            }
            if (t == 20) {
                // the thrower hangs in the air 3 blocks (hand to hook) under the yard's end, moving with the ship, and the
                // hook goes up into the yard from 1.5 blocks below it
                Vec3 yard = ship.toWorld(Vec3.atCenterOf(yardEnd));
                Vec3 shipVelocity = ship.velocityAt(Vec3.atCenterOf(yardEnd)).scale(1.0 / 20.0);
                Player p = playerInLevel(h, yard.subtract(0, 0.5 + 3.0 + 1.17, 0), -90f);
                p.setDeltaMovement(shipVelocity);
                player[0] = p;
                hook[0] = GrappleService.launch(level, p, yard.subtract(0, 1.5, 0), new Vec3(0, 0.7, 0).add(shipVelocity),
                        new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
            }
            if (t == start - 6) {
                GrapplingHookEntity g = hook[0];
                h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED && ship.id().equals(g.shipId()),
                        "the hook is not on the ship: " + g.state() + " " + g.shipId());
            }
            if (t > start && t <= start + span) {
                GrapplingHookEntity g = hook[0];
                Player p = player[0];
                h.assertTrue(RopeSwing.holding(g.swingLength()), "tick " + t + ": the rope does not hold the player");
                Vec3 at = g.ropeFarEnd(level);
                if (hookStart[0] == null) hookStart[0] = at;
                worst[0] = Math.max(worst[0], hand(p).distanceTo(at) - g.swingLength());
                across[0] = Math.max(across[0], Math.hypot(p.getX() - at.x, p.getZ() - at.z));
            }
        });
        h.runAfterDelay(start + span + 1, () -> {
            GrapplingHookEntity g = hook[0];
            Player p = player[0];
            Vec3 at = g.ropeFarEnd(level);
            double moved = at.subtract(hookStart[0]).horizontalDistance();
            Constants.LOG.info("[GR7] under way: the hook moved {} blocks in {} ticks; hand beyond the rope at most {}, "
                    + "at most {} across from under the hook", f(moved), span, f(worst[0]), f(across[0]));
            h.assertTrue(moved > span * 0.2 * 0.75, "the ship did not sail: the hook moved " + moved);
            h.assertTrue(worst[0] <= 0.35, "the hand got " + worst[0] + " blocks beyond the rope on a moving ship");
            h.assertTrue(across[0] < 2.0, "the player trailed " + across[0] + " blocks from under the hook");
            h.assertTrue(p.getY() < at.y, "the player is not under the hook");
            g.release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }
    // ------------------------------------------------------------------ GR8: the rope poses

    /** The rope pose the hook's synced state gives {@code p} now ({@link RopePose}, as every client works it out). */
    private static RopePose pose(GameTestHelper h, Player p, GrapplingHookEntity g, RopePose previous) {
        Vec3 far = g.ropeFarEnd(h.getLevel());
        double speed = far == null ? 0.0 : RopePose.swingSpeed(hand(p).subtract(far), p.getDeltaMovement());
        return RopePose.select(RopePose.facts(p.isPassenger() && p.getVehicle() instanceof RopeRiderEntity, g, p,
                g.syncedSwingLength(), speed), previous);
    }

    /**
     * GR8: the hook's synced state drives the pose. Standing under the beam, a pull is the standing pull; hanging still,
     * the hang; a pull while hanging, the climb, for {@link RopePose#PULL_TICKS}, then the hang again; swinging fast,
     * the swing; and each pull moves the synced counter that restarts the climb on every client.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void theHookStateDrivesTheRopePose(GameTestHelper h) {
        landRig(h);
        ServerLevel level = h.getLevel();
        Player[] player = {null};
        GrapplingHookEntity[] hook = {null};
        int[] pulls = {0};
        h.runAfterDelay(10, () -> player[0] = playerInLevel(h, h.absoluteVec(new Vec3(HOOK_X, 1, HOOK_Z)), 0f));
        h.runAfterDelay(12, () -> hook[0] = hookBeam(h, player[0]));
        h.runAfterDelay(40, () -> {
            Player p = player[0];
            GrapplingHookEntity g = hook[0];
            assertLatchedOnBeam(h, g);
            h.assertTrue(p.onGround(), "the player is not standing under the beam");
            h.assertTrue(pose(h, p, g, RopePose.NONE) == RopePose.NONE, "standing with the rope: " + pose(h, p, g, RopePose.NONE));
            RopeSlideService.Board b = RopeSlideService.tryBoard(level, p, g, 0.0);
            h.assertTrue(b == RopeSlideService.Board.OK, "the standing pull was refused: " + b);
            h.assertTrue(g.pulls() == 1 && g.ticksSincePull() == 0, "the pull was not counted: " + g.pulls());
            RopePose standing = pose(h, p, g, RopePose.NONE);
            h.assertTrue(standing == RopePose.PULL, "a pull while standing shows " + standing);
            pulls[0] = g.pulls();
        });
        h.runAfterDelay(80, () -> {
            Player p = player[0];
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!p.onGround() && RopeSwing.holding(g.syncedSwingLength()), "the pull did not lift the player onto the rope");
            RopePose still = pose(h, p, g, RopePose.NONE);
            h.assertTrue(still == RopePose.HANG, "hanging still shows " + still);
            RopeSlideService.Board b = RopeSlideService.tryBoard(level, p, g, 0.0);
            h.assertTrue(b == RopeSlideService.Board.OK, "the pull while hanging was refused: " + b);
            h.assertTrue(g.pulls() == pulls[0] + 1, "the second pull was not counted");
            RopePose climbing = pose(h, p, g, RopePose.HANG);
            h.assertTrue(climbing == RopePose.CLIMB, "a pull while hanging shows " + climbing);
        });
        h.runAfterDelay(80 + RopePose.PULL_TICKS + 2, () -> {
            Player p = player[0];
            GrapplingHookEntity g = hook[0];
            RopePose after = pose(h, p, g, RopePose.CLIMB);
            h.assertTrue(after == RopePose.HANG || after == RopePose.SWING, "after the pull's hand over hand: " + after);
            p.setDeltaMovement(new Vec3(0.3, 0, 0)); // swinging across the rope
            RopePose swinging = pose(h, p, g, RopePose.HANG);
            h.assertTrue(swinging == RopePose.SWING, "swinging at 0.3 blocks per tick shows " + swinging);
            Constants.LOG.info("[GR8] rope poses: pull {}, then hang, climb, {}, swing; {} pulls synced", RopePose.PULL, after, g.pulls());
            g.release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }
}

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
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Collection;

/**
 * Hauling a hooked ship by hand and pulling oneself along the rope (GR5, docs/design.md §8.3 "Hauling" and "No floating
 * near end"; GR6 "Climb only": the pull is only a climb, the climbing tests build their own ground and wall) in a real server. A stone quay fills x 0..3 (top at y 8, standing height 9) west of a water basin
 * (x 4..23, walls at x 4 and 23); one closed 5×4×5 plank hull of {@link DryHullGameTests} floats free at x 10..14,
 * z 9..13. The mock player stands on the quay at (3.5, 9, 11.5) and its hook bites into the hull's west wall at deck
 * height, about 6.7 blocks from the hand. Mock players are not in the level, so the test sets their position, sneak
 * key and ground contact itself. Every test pins the hauling values and runs in its own batch.
 */
public final class GrappleHaulGameTests {

    private static final String BATCH = "pirates_n_ships_config_grapple_haul_";
    private static final double STIFFNESS = 60.0;
    private static final double DAMPING = 30.0;
    private static final double MAX_FORCE = 200.0;

    private GrappleHaulGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(GrappleHaulGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** Pins the hauling values ({@code maxForce} as the cap) and builds the quay, the basin and the free hull. */
    private static Fixture quay(GameTestHelper h, double maxForce) {
        ConfigOverrides.during(h, GrappleConfig.HAULING, true);
        ConfigOverrides.during(h, GrappleConfig.HAUL_STIFFNESS, STIFFNESS);
        ConfigOverrides.during(h, GrappleConfig.HAUL_DAMPING, DAMPING);
        ConfigOverrides.during(h, GrappleConfig.HAUL_MAX_FORCE, maxForce);
        DryHullGameTests.basin(h, 4, 23, true);
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 24; z++) {
                for (int y = 1; y <= 8; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        SailingGameTestsShips.openSky(h, 24);
        return DryHullGameTests.assemble(h, DryHullGameTests.hull(h, 10, false));
    }

    /** A survival mock player with an empty inventory, standing on the quay at relative x {@code x}. */
    private static Player onQuay(GameTestHelper h, double x) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.getInventory().clearContent();
        stand(h, p, x);
        return p;
    }

    /** Puts {@code p} on the quay at relative x {@code x}, feet planted. */
    private static void stand(GameTestHelper h, Player p, double x) {
        Vec3 at = h.absoluteVec(new Vec3(x, 9, 11.5));
        p.setPos(at.x, at.y, at.z);
        p.setOnGround(true);
    }

    /** Plot block of the hull's west wall at deck height, the hook's target. */
    private static BlockPos westDeckEdge(Fixture f) {
        return f.helmPlot().offset(-2, -1, 0);
    }

    /** Throws a hook from two blocks west of the hull's west wall straight at it. */
    private static GrapplingHookEntity hookShip(ServerLevel level, Player thrower, Fixture f) {
        Vec3 target = f.ship().toWorld(Vec3.atCenterOf(westDeckEdge(f)));
        return GrappleService.launch(level, thrower, target.add(-2.0, 0, 0), new Vec3(GrappleConfig.THROW_VELOCITY.get(), 0, 0),
                new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
    }

    private static Vec3 comWorld(ShipBody ship) {
        Vector3d c = new Vector3d();
        if (!ship.centerOfMass(c)) {
            throw new GameTestAssertException("no center of mass");
        }
        Vector3d w = ship.toWorld(c, new Vector3d());
        return new Vec3(w.x, w.y, w.z);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static Vec3 hand(Player p) {
        return p.getEyePosition().subtract(0, GrapplingHookEntity.HAND_BELOW_EYES, 0);
    }

    private static void assertLatched(GameTestHelper h, GrapplingHookEntity g, Fixture f) {
        h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + g.state());
        h.assertTrue(f.ship().id().equals(g.shipId()), "the hook is not on the hull");
        h.assertTrue(g.haulKind() == GrappleRules.Haul.SHORE, "a hull hooked from the quay is no shore rope: " + g.haulKind());
    }

    /** Releases the hook and stops the hull so it does not drift into the next test. */
    private static void finish(GrapplingHookEntity g, Fixture f) {
        g.release(GrappleRules.Release.NONE);
        f.ship().addVelocity(f.ship().linearVelocity().negate(), f.ship().angularVelocity().negate());
    }

    // ------------------------------------------------------------------ hauling

    /**
     * The human's case: the player sneaks (the rope freezes at about 6.7 blocks) and backs two blocks away; within 5 s
     * the hull has come at least one block toward the player, the rope looks taut while it pulls, and no substep pulled
     * harder than the cap.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 130, batch = BATCH + "pull")
    public static void sneakingFreezesTheRopeAndBackingAwayHaulsTheShip(GameTestHelper h) {
        Fixture f = quay(h, MAX_FORCE);
        ServerLevel level = h.getLevel();
        Player player = onQuay(h, 3.5);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        double[] startX = {0};
        double[] frozenAt = {0};
        h.runAfterDelay(20, () -> hook[0] = hookShip(level, player, f));
        h.runAfterDelay(25, () -> {
            GrapplingHookEntity g = hook[0];
            assertLatched(h, g, f);
            h.assertTrue(!g.haul().frozen() && !g.taut(), "the rope is frozen or taut before sneaking");
            startX[0] = comWorld(f.ship()).x;
            player.setShiftKeyDown(true);
        });
        h.runAfterDelay(26, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.haul().frozen(), "sneaking did not freeze the rope");
            double d = hand(player).distanceTo(g.ropeFarEnd(level));
            h.assertTrue(Math.abs(g.haul().frozenLength() - d) < 0.05, "frozen at " + g.haul().frozenLength() + ", the rope is " + d);
            h.assertTrue(d > 5.5 && d < 8.0, "the hook is not about six blocks out: " + d);
            frozenAt[0] = g.haul().frozenLength();
            stand(h, player, 1.5); // two blocks back
        });
        h.runAfterDelay(29, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.taut(), "the hauled rope does not look taut");
            h.assertTrue(g.haul().pullingSubsteps() > 0 && g.haul().tension() > 0, "the frozen rope does not pull");
        });
        h.runAfterDelay(27, () -> h.succeedWhen(() -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!g.isRemoved() && g.haul().frozen(), "the rope let go or unfroze");
            h.assertTrue(g.haul().peakTension() <= MAX_FORCE + 1.0e-9, "a substep pulled with " + g.haul().peakTension());
            h.assertTrue(g.haul().frozenLength() <= frozenAt[0] + 1.0e-9, "the rope slipped below the cap: " + g.haul().frozenLength());
            double moved = startX[0] - comWorld(f.ship()).x;
            h.assertTrue(moved >= 1.0, "the hull came only " + moved + " blocks toward the player");
            finish(g, f);
        }));
    }

    /** Without sneak the rope pays out: the player backs off and the hull stays (no passive drag from a hand on land). */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 160, batch = BATCH + "pay_out")
    public static void withoutSneakTheRopePaysOutAndTheShipStays(GameTestHelper h) {
        Fixture f = quay(h, MAX_FORCE);
        ServerLevel level = h.getLevel();
        Player player = onQuay(h, 3.5);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        Vec3[] start = new Vec3[1];
        h.runAfterDelay(20, () -> hook[0] = hookShip(level, player, f));
        h.runAfterDelay(25, () -> {
            assertLatched(h, hook[0], f);
            start[0] = comWorld(f.ship());
            stand(h, player, 1.5);
        });
        h.runAfterDelay(125, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!g.isRemoved(), "the rope let go");
            h.assertTrue(!g.haul().frozen() && g.haul().pullingSubsteps() == 0 && !g.taut(), "the rope pulled without sneak");
            double moved = horizontal(start[0], comWorld(f.ship()));
            h.assertTrue(moved < 0.2, "the hull moved " + moved + " blocks on a rope paying out");
            finish(g, f);
            h.succeed();
        });
    }

    /**
     * Above the cap the rope slips: backing off three blocks with a cap of 40 (spring 180) lets the frozen length grow
     * so the pull stays at the cap; the player is never stopped and the rope never snaps.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = BATCH + "slip")
    public static void aPullAboveTheCapSlipsTheRope(GameTestHelper h) {
        double cap = 40.0;
        Fixture f = quay(h, cap);
        ServerLevel level = h.getLevel();
        Player player = onQuay(h, 3.5);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        double[] frozenAt = {0};
        h.runAfterDelay(20, () -> hook[0] = hookShip(level, player, f));
        h.runAfterDelay(25, () -> {
            assertLatched(h, hook[0], f);
            player.setShiftKeyDown(true);
        });
        h.runAfterDelay(26, () -> {
            h.assertTrue(hook[0].haul().frozen(), "sneaking did not freeze the rope");
            frozenAt[0] = hook[0].haul().frozenLength();
            stand(h, player, 0.5); // three blocks back
        });
        h.runAfterDelay(32, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!g.isRemoved() && g.haul().frozen(), "the rope let go or unfroze");
            double d = hand(player).distanceTo(g.ropeFarEnd(level));
            h.assertTrue(g.haul().frozenLength() > frozenAt[0] + 1.5, "the rope did not slip: " + frozenAt[0] + " -> " + g.haul().frozenLength());
            h.assertTrue(Math.abs(d - g.haul().frozenLength() - cap / STIFFNESS) < 0.2,
                    "the slipped rope does not hold at the cap: rope " + d + ", frozen " + g.haul().frozenLength());
            h.assertTrue(g.haul().peakTension() <= cap + 1.0e-9, "a substep pulled with " + g.haul().peakTension() + " over the cap " + cap);
            h.assertTrue(g.haul().peakTension() > 0.5 * cap, "the rope hardly pulled: " + g.haul().peakTension());
            finish(g, f);
            h.succeed();
        });
    }

    /** A rope tied off on a cleat (RP1) cannot be hauled by hand: sneaking freezes nothing and the haul group stays empty. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100, batch = BATCH + "cleat")
    public static void aRopeTiedToACleatIgnoresSneak(GameTestHelper h) {
        Fixture f = quay(h, MAX_FORCE);
        BlockPos cleat = new BlockPos(2, 9, 13);
        h.setBlock(cleat, SailingGameTestsShips.cleat(AttachFace.FLOOR, Direction.NORTH));
        ServerLevel level = h.getLevel();
        Player player = onQuay(h, 3.5);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> hook[0] = hookShip(level, player, f));
        h.runAfterDelay(25, () -> {
            GrapplingHookEntity g = hook[0];
            assertLatched(h, g, f);
            GrappleService.tieOff(level, player, h.absolutePos(cleat));
            h.assertTrue(h.absolutePos(cleat).equals(g.tiedRing()), "the rope is not tied to the cleat: " + g.tiedRing());
            player.setShiftKeyDown(true);
            stand(h, player, 1.5);
        });
        h.runAfterDelay(35, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!g.isRemoved(), "the tied rope let go");
            h.assertTrue(!g.haul().frozen() && g.haul().pullingSubsteps() == 0, "sneaking hauled a rope tied to a cleat");
            finish(g, f);
            h.succeed();
        });
    }

    /** With {@code grapple.hauling} off sneaking freezes nothing and the haul group stays empty. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100, batch = BATCH + "off")
    public static void haulingOffDoesNothing(GameTestHelper h) {
        Fixture f = quay(h, MAX_FORCE);
        ConfigOverrides.during(h, GrappleConfig.HAULING, false);
        ServerLevel level = h.getLevel();
        Player player = onQuay(h, 3.5);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> hook[0] = hookShip(level, player, f));
        h.runAfterDelay(25, () -> {
            assertLatched(h, hook[0], f);
            player.setShiftKeyDown(true);
            stand(h, player, 1.5);
        });
        h.runAfterDelay(35, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!g.isRemoved(), "the rope let go");
            h.assertTrue(!g.haul().frozen() && g.haul().pullingSubsteps() == 0, "sneaking hauled with hauling off");
            finish(g, f);
            h.succeed();
        });
    }

    /** Airborne on a frozen rope, the player is pulled toward the hook (on the ground they feel nothing). */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100, batch = BATCH + "airborne")
    public static void anAirbornePlayerIsPulledTowardTheHook(GameTestHelper h) {
        Fixture f = quay(h, MAX_FORCE);
        ServerLevel level = h.getLevel();
        Player player = onQuay(h, 3.5);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> hook[0] = hookShip(level, player, f));
        h.runAfterDelay(25, () -> {
            assertLatched(h, hook[0], f);
            player.setShiftKeyDown(true);
        });
        h.runAfterDelay(26, () -> {
            stand(h, player, 1.5);
            player.setDeltaMovement(Vec3.ZERO);
        });
        h.runAfterDelay(28, () -> {
            h.assertTrue(player.getDeltaMovement().lengthSqr() < 1.0e-12, "a player on the ground was pulled: " + player.getDeltaMovement());
            player.setOnGround(false); // jumps
            player.setDeltaMovement(new Vec3(-0.2, 0, 0)); // still backing away
        });
        h.runAfterDelay(30, () -> {
            Vec3 v = player.getDeltaMovement();
            Vec3 toHook = hook[0].ropeFarEnd(level).subtract(hand(player)).normalize();
            h.assertTrue(v.dot(toHook) > 0.0, "the airborne player is not pulled toward the hook: " + v);
            finish(hook[0], f);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ pulling oneself along the rope (no floating near end)

    /** The test tick at which a rope thrown at tick 20 can first be used ({@code grab_cooldown_ticks}), plus slack. */
    private static int grabAt() {
        return 22 + GrappleConfig.GRAB_COOLDOWN_TICKS.get();
    }

    /** Turns {@code player} to look at {@code point}. */
    private static void lookAt(Player player, Vec3 point) {
        Vec3 d = point.subtract(player.getEyePosition());
        player.setYRot((float) Math.toDegrees(-Math.atan2(d.x, d.z)));
        player.setXRot((float) Math.toDegrees(-Math.asin(d.y / d.length())));
    }

    /**
     * GR5's playtest, now with {@code climb_min_rise} 0 (GR6: that restores GR5's pull): using the rope with its near end
     * in hand leaves no rope end floating where the player stood. The hook is about level with the quay, no climb, yet
     * the player is pulled hand over hand toward it, the rope running from the hook to their hand, and lands on the
     * hull's deck above the hook. Someone else cannot mount that rope.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH + "pull_along")
    public static void withNoClimbRiseAHandHeldRopePullsThePlayerToTheHook(GameTestHelper h) {
        Fixture f = quay(h, MAX_FORCE);
        ConfigOverrides.during(h, GrappleConfig.CLIMB_MIN_RISE, 0.0);
        ServerLevel level = h.getLevel();
        Player player = onQuay(h, 3.5);
        Player other = onQuay(h, 3.5);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        double[] start = {0};
        h.runAfterDelay(20, () -> hook[0] = hookShip(level, player, f));
        h.runAfterDelay(grabAt(), () -> {
            GrapplingHookEntity g = hook[0];
            assertLatched(h, g, f);
            h.assertTrue(g.ropeFarEnd(level).y - player.getY() < 2.0, "the hook is high enough to climb to anyway");
            Vec3 point = hand(player).lerp(g.ropeFarEnd(level), 0.25);
            lookAt(other, point);
            RopeSlideService.Board refused = RopeSlideService.tryBoard(level, other, g, 0.0);
            h.assertTrue(refused == RopeSlideService.Board.NOT_FIXED, "someone else mounted a hand-held rope: " + refused);
            lookAt(player, point);
            RopeSlideService.Board b = RopeSlideService.tryBoard(level, player, g, 0.0);
            h.assertTrue(b == RopeSlideService.Board.OK, "using the rope was refused: " + b);
            h.assertTrue(player.getVehicle() instanceof RopeRiderEntity r && r.pulling(), "the player is not pulled along the rope");
            h.assertTrue(!g.nearEndFixed() && g.tiedRing() == null, "the near end was fixed");
            start[0] = player.position().distanceTo(g.ropeFarEnd(level));
        });
        h.runAfterDelay(grabAt() + 10, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(player.isPassenger(), "the pull stopped early");
            h.assertTrue(g.ropeNearEnd(level).distanceTo(hand(player)) < 1.0e-6, "the rope does not run to the player's hand");
            h.assertTrue(!g.nearEndFixed(), "a rope end was left floating");
            double closer = start[0] - player.position().distanceTo(g.ropeFarEnd(level));
            double expected = 10 * GrappleConfig.SLIDE_MIN_SPEED.get();
            h.assertTrue(closer > 0.6 * expected, "the player came only " + closer + " blocks closer in ten ticks");
        });
        h.runAfterDelay(grabAt() + 11, () -> h.succeedWhen(() -> {
            h.assertTrue(!player.isPassenger(), "still pulling");
            Vec3 spot = f.ship().toWorld(Vec3.atBottomCenterOf(westDeckEdge(f).above()));
            double d = player.position().distanceTo(spot);
            h.assertTrue(d < 1.0, "the player landed " + d + " blocks from the deck spot above the hook: " + player.position());
            h.assertTrue(h.getEntities(GrappleContent.ROPE_RIDER.get()).isEmpty(), "a rope rider is left");
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!g.isRemoved() && !g.nearEndFixed(), "the rope let go or its end stayed behind");
            finish(g, f);
        }));
    }

    /**
     * Hauling (sneak) and pulling (use) do not interfere: while sneaking the use does not pull and the rope stays
     * frozen; a pull unfreezes it (the player rides the rope); sneaking during the pull lets go of it, and sneaking
     * again freezes the rope where the player is. GR6: the player swims in the basin below the hull (the hook well
     * above their feet), so the use is a climb.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = BATCH + "both")
    public static void haulingAndPullingDoNotInterfere(GameTestHelper h) {
        Fixture f = quay(h, MAX_FORCE);
        ServerLevel level = h.getLevel();
        Player player = swimming(h);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> hook[0] = hookShip(level, player, f));
        h.runAfterDelay(grabAt(), () -> {
            GrapplingHookEntity g = hook[0];
            assertLatched(h, g, f);
            player.setShiftKeyDown(true);
        });
        h.runAfterDelay(grabAt() + 1, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.haul().frozen(), "sneaking did not freeze the rope");
            lookAt(player, hand(player).lerp(g.ropeFarEnd(level), 0.25));
            RopeSlideService.Board b = RopeSlideService.tryBoard(level, player, g, 0.0);
            h.assertTrue(b == RopeSlideService.Board.BUSY, "a use while hauling pulled the player: " + b);
            h.assertTrue(!player.isPassenger(), "the hauling player mounts the rope");
            player.setShiftKeyDown(false);
        });
        h.runAfterDelay(grabAt() + 2, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!g.haul().frozen(), "releasing sneak did not unfreeze the rope");
            lookAt(player, hand(player).lerp(g.ropeFarEnd(level), 0.25));
            RopeSlideService.Board b = RopeSlideService.tryBoard(level, player, g, 0.0);
            h.assertTrue(b == RopeSlideService.Board.OK, "the pull was refused: " + b);
        });
        h.runAfterDelay(grabAt() + 5, () -> {
            h.assertTrue(player.isPassenger(), "the pull stopped early");
            h.assertTrue(!hook[0].haul().frozen(), "the rope froze during a pull");
            player.setShiftKeyDown(true); // let go of the pull
        });
        h.runAfterDelay(grabAt() + 8, () -> {
            h.assertTrue(!player.isPassenger(), "sneaking did not let go of the pull");
            player.setShiftKeyDown(true); // vanilla clears the sneak key on a dismount; the player still holds it
        });
        h.runAfterDelay(grabAt() + 10, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.haul().frozen(), "the rope did not freeze for the sneaking player after the pull");
            h.assertTrue(!g.isRemoved() && !g.nearEndFixed(), "the rope let go or its end was fixed");
            finish(g, f);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ climbing only (GR6)

    /** A survival mock player with an empty inventory, swimming in the basin west of the hull, feet at y 4 (the hull's keel is at y 5). */
    private static Player swimming(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.getInventory().clearContent();
        Vec3 at = h.absoluteVec(new Vec3(7.5, 4, 11.5));
        p.setPos(at.x, at.y, at.z);
        p.setOnGround(false);
        return p;
    }

    /** Looks at the rope 1.5 blocks out from the hand. */
    private static void lookAlongRope(Player player, Vec3 hook) {
        Vec3 hand = hand(player);
        lookAt(player, hand.add(hook.subtract(hand).normalize().scale(1.5)));
    }

    /**
     * Flat stone ground at y 0 (with {@code gap}, none at x 6..10) and a stone wall at x {@code wallX}, z 8..14,
     * y 1..{@code wallTop}. Returns a survival mock player standing at (3.5, 1, 11.5).
     */
    private static Player groundAndWall(GameTestHelper h, int wallX, int wallTop, boolean gap) {
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                if (!gap || x < 6 || x > 10) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                if (x == wallX && z >= 8 && z <= 14) {
                    for (int y = 1; y <= wallTop; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.getInventory().clearContent();
        Vec3 at = h.absoluteVec(new Vec3(3.5, 1, 11.5));
        p.setPos(at.x, at.y, at.z);
        p.setOnGround(true);
        return p;
    }

    /** Throws {@code thrower}'s hook along +x from relative {@code from}; it bites into the wall face ahead at that height. */
    private static GrapplingHookEntity hookWall(GameTestHelper h, Player thrower, Vec3 from) {
        return GrappleService.launch(h.getLevel(), thrower, h.absoluteVec(from), new Vec3(GrappleConfig.THROW_VELOCITY.get(), 0, 0),
                new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
    }

    /**
     * The human's report (GR6): across a gap, a hook only one block above the thrower's feet. Using the hand-held rope
     * does nothing to the player (no rider, they stay where they are, the rope's near end stays in hand); they get the
     * tie-off hint, at most once per second.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 140, batch = BATCH + "climb_gap")
    public static void aHookJustAboveTheFeetAcrossAGapIsNoClimb(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = groundAndWall(h, 14, 4, true);
        Vec3 start = player.position();
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        long[] hinted = {0};
        h.runAfterDelay(20, () -> hook[0] = hookWall(h, player, new Vec3(12.0, 2.0, 11.5)));
        h.runAfterDelay(grabAt(), () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED && g.onWorldBlock(), "the hook did not latch on the wall: " + g.state());
            double rise = g.ropeFarEnd(level).y - player.getY();
            h.assertTrue(rise > 0.8 && rise < 1.2, "the hook is not one block above the feet: " + rise);
            lookAlongRope(player, g.ropeFarEnd(level));
            RopeSlideService.Board b = RopeSlideService.tryBoard(level, player, g, 0.0);
            h.assertTrue(b == RopeSlideService.Board.NOT_A_CLIMB, "a rope across a gap was used: " + b);
            hinted[0] = level.getGameTime();
            h.assertTrue(RopeSlideService.lastHint(player) == hinted[0], "no tie-off hint");
            h.assertTrue(!player.isPassenger() && !g.nearEndFixed(), "the player mounts the rope or its end was fixed");
        });
        h.runAfterDelay(grabAt() + 5, () -> {
            RopeSlideService.Board b = RopeSlideService.tryBoard(level, player, hook[0], 0.0);
            h.assertTrue(b == RopeSlideService.Board.NOT_A_CLIMB, "the second use was not refused: " + b);
            h.assertTrue(RopeSlideService.lastHint(player) == hinted[0], "the hint was repeated within a second");
        });
        h.runAfterDelay(grabAt() + 25, () -> {
            RopeSlideService.tryBoard(level, player, hook[0], 0.0);
            h.assertTrue(RopeSlideService.lastHint(player) == level.getGameTime(), "the hint was not shown again after a second");
        });
        h.runAfterDelay(grabAt() + 30, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!player.isPassenger(), "the player rides the rope");
            h.assertTrue(h.getEntities(GrappleContent.ROPE_RIDER.get()).isEmpty(), "a rope rider is left");
            h.assertTrue(player.position().distanceTo(start) < 0.1, "the player moved: " + player.position() + " vs " + start);
            h.assertTrue(g.ropeNearEnd(level).distanceTo(hand(player)) < 1.0e-6, "the rope does not run to the player's hand");
            h.assertTrue(!g.isRemoved() && g.state() == GrapplingHookEntity.State.LATCHED && !g.nearEndFixed(), "the rope changed");
            g.release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }

    /**
     * GR6: a hook four blocks up a wall is a climb. The player is pulled hand over hand to it, up the face where the
     * straight way runs into the wall's lip, and lands on top of the wall; the rope runs to their hand all the way.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 220, batch = BATCH + "climb_wall")
    public static void aHookFourBlocksUpAWallIsClimbed(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = groundAndWall(h, 8, 6, false);
        Vec3 foot = h.absoluteVec(new Vec3(5.5, 1, 11.5));
        player.setPos(foot.x, foot.y, foot.z);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        double[] start = {0};
        h.runAfterDelay(20, () -> hook[0] = hookWall(h, player, new Vec3(6.0, 5.0, 11.5)));
        h.runAfterDelay(grabAt(), () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED && g.onWorldBlock(), "the hook did not latch on the wall: " + g.state());
            double rise = g.ropeFarEnd(level).y - player.getY();
            h.assertTrue(rise > 3.8 && rise < 4.2, "the hook is not four blocks above the feet: " + rise);
            lookAlongRope(player, g.ropeFarEnd(level));
            RopeSlideService.Board b = RopeSlideService.tryBoard(level, player, g, 0.0);
            h.assertTrue(b == RopeSlideService.Board.OK, "the climb was refused: " + b);
            h.assertTrue(player.getVehicle() instanceof RopeRiderEntity r && r.pulling(), "the player is not pulled up the rope");
            h.assertTrue(RopeSlideService.lastHint(player) == Long.MIN_VALUE, "a climb hinted to tie off");
            start[0] = player.position().distanceTo(g.ropeFarEnd(level));
        });
        h.runAfterDelay(grabAt() + 10, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(player.isPassenger(), "the climb stopped early");
            h.assertTrue(g.ropeNearEnd(level).distanceTo(hand(player)) < 1.0e-6, "the rope does not run to the player's hand");
            double closer = start[0] - player.position().distanceTo(g.ropeFarEnd(level));
            h.assertTrue(closer > 0.5, "the player came only " + closer + " blocks closer in ten ticks");
        });
        h.runAfterDelay(grabAt() + 11, () -> h.succeedWhen(() -> {
            h.assertTrue(!player.isPassenger(), "still climbing");
            Vec3 top = h.absoluteVec(new Vec3(8.5, 7, 11.5));
            h.assertTrue(player.position().distanceTo(top) < 1.0, "the player did not land on top of the wall: " + player.position() + " vs " + top);
            h.assertTrue(player.getY() >= top.y - 0.1, "the player is below the wall's top: " + player.getY());
            h.assertTrue(player.getHealth() == player.getMaxHealth(), "hurt by the climb");
            h.assertTrue(h.getEntities(GrappleContent.ROPE_RIDER.get()).isEmpty(), "a rope rider is left");
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!g.isRemoved() && g.state() == GrapplingHookEntity.State.LATCHED && !g.nearEndFixed(), "the rope let go or its end stayed behind");
            g.release(GrappleRules.Release.NONE);
        }));
    }

    /**
     * GR6: a player swimming below the hull hooks its deck edge, well above their feet: a climb. They are pulled out of
     * the water up the hull side and land on the deck above the hook.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 220, batch = BATCH + "climb_hull")
    public static void aSwimmerBelowTheHullClimbsToTheDeck(GameTestHelper h) {
        Fixture f = quay(h, MAX_FORCE);
        ServerLevel level = h.getLevel();
        Player player = swimming(h);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> hook[0] = hookShip(level, player, f));
        h.runAfterDelay(grabAt(), () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED && f.ship().id().equals(g.shipId()), "the hook is not on the hull: " + g.state());
            double rise = g.ropeFarEnd(level).y - player.getY();
            h.assertTrue(rise >= 2.0, "the deck is not high enough above the swimmer: " + rise);
            lookAlongRope(player, g.ropeFarEnd(level));
            RopeSlideService.Board b = RopeSlideService.tryBoard(level, player, g, 0.0);
            h.assertTrue(b == RopeSlideService.Board.OK, "the climb was refused: " + b);
            h.assertTrue(player.getVehicle() instanceof RopeRiderEntity r && r.pulling(), "the swimmer is not pulled up the rope");
        });
        h.runAfterDelay(grabAt() + 1, () -> h.succeedWhen(() -> {
            h.assertTrue(!player.isPassenger(), "still climbing");
            Vec3 spot = f.ship().toWorld(Vec3.atBottomCenterOf(westDeckEdge(f).above()));
            double d = player.position().distanceTo(spot);
            h.assertTrue(d < 1.0, "the player landed " + d + " blocks from the deck spot above the hook: " + player.position());
            h.assertTrue(h.getEntities(GrappleContent.ROPE_RIDER.get()).isEmpty(), "a rope rider is left");
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!g.isRemoved() && !g.nearEndFixed(), "the rope let go or its end stayed behind");
            finish(g, f);
        }));
    }
}

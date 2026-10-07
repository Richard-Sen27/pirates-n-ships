package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.combat.firearms.FirearmContent;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Collection;

/**
 * GR1 in a real server: launching the grappling hook from a crossbow or a musket in the other hand, and the mooring ring
 * (docs/design.md §8.3). The launch tests use a survival mock player looking up, drive the crossbow draw the way
 * {@code LivingEntity} does ({@code use}, then {@code releaseUsing} with the remaining use time) and measure the hook's
 * speed right after the launch. The ring tests use the two hulls of {@link GrappleGameTests} (ship A at x 2..6, ship B
 * at x 16..20, both z 9..13, deck top at y 9) with rings placed on the deck before assembly.
 */
public final class GrappleLaunchGameTests {

    private static final String LAUNCH_BATCH = "pirates_n_ships_grapple_launch";
    private static final String RING_BATCH = "pirates_n_ships_grapple_ring";
    private static final String CROSSBOW_DISABLED_BATCH = "pirates_n_ships_config_grapple_crossbow_disabled";
    /** Relative tolerance of a measured launch speed (the weapon's inaccuracy changes the length very slightly). */
    private static final double SPEED_TOLERANCE = 0.03;

    private GrappleLaunchGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(GrappleLaunchGameTests.class);
    }

    // ------------------------------------------------------------------ launch fixtures

    /** A survival mock player with an empty inventory, standing in the middle, looking up (the hook stays in the air). */
    private static Player shooter(GameTestHelper h, ItemStack offHand) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.getInventory().clearContent();
        Vec3 stand = h.absoluteVec(new Vec3(4.5, 1, 4.5));
        p.setPos(stand.x, stand.y, stand.z);
        p.setXRot(-60.0f);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CombatContent.GRAPPLING_HOOK.get()));
        p.setItemInHand(InteractionHand.OFF_HAND, offHand);
        return p;
    }

    private static int hooks(Player p) {
        return p.getInventory().countItem(CombatContent.GRAPPLING_HOOK.get());
    }

    private static void assertSpeed(GameTestHelper h, GrapplingHookEntity hook, double expected, String what) {
        double v = hook.getDeltaMovement().length();
        h.assertTrue(Math.abs(v - expected) <= SPEED_TOLERANCE * expected,
                what + " launched at " + v + " blocks/tick, expected " + expected);
    }

    // ------------------------------------------------------------------ crossbow

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = LAUNCH_BATCH)
    public static void crossbowShootsTheHookAfterTheDrawAtTheCrossbowSpeed(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = shooter(h, new ItemStack(Items.CROSSBOW));
        ItemStack hook = player.getMainHandItem();
        int draw = GrappleConfig.CROSSBOW_DRAW_TICKS.get();

        InteractionResult r = hook.use(level, player, InteractionHand.MAIN_HAND).getResult();
        h.assertTrue(r.consumesAction(), "the draw was refused: " + r);
        h.assertTrue(player.isUsingItem(), "using the hook with a crossbow did not start a draw");
        h.assertTrue(GrappleService.hookOf(player) == null, "the hook left before the draw");
        // let go too early: nothing happens
        hook.releaseUsing(level, player, GrapplingHookItem.DRAW_SESSION_TICKS - (draw - 1));
        player.stopUsingItem();
        h.assertTrue(GrappleService.hookOf(player) == null, "a hook was shot before the crossbow was drawn");
        h.assertTrue(hook.getCount() == 1, "an early release used up the hook");

        hook.use(level, player, InteractionHand.MAIN_HAND);
        hook.releaseUsing(level, player, GrapplingHookItem.DRAW_SESSION_TICKS - draw);
        player.stopUsingItem();
        GrapplingHookEntity out = GrappleService.hookOf(player);
        h.assertTrue(out != null, "no hook out after a full draw");
        assertSpeed(h, out, GrappleConfig.THROW_VELOCITY.get() * GrappleConfig.CROSSBOW_SPEED.get(), "the crossbow hook");
        h.assertTrue(out.ropeLength() == GrappleConfig.ropeLength(GrappleLaunch.Mode.CROSSBOW),
                "rope length " + out.ropeLength() + ", expected the crossbow's");
        h.assertTrue(out.ropeLength() > GrappleConfig.MAX_ROPE_LENGTH.get(), "the crossbow rope is not longer than the thrown one");
        h.assertTrue(player.getMainHandItem().isEmpty(), "the shot hook is still in the hand");
        h.assertTrue(player.getOffhandItem().getDamageValue() == 1, "the crossbow took no wear");
        h.assertTrue(GrappleService.release(player) && hooks(player) == 1, "the released hook did not come back");
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = CROSSBOW_DISABLED_BATCH)
    public static void crossbowLaunchSwitchedOffThrowsTheHook(GameTestHelper h) {
        ConfigOverrides.during(h, GrappleConfig.CROSSBOW_ENABLED, false);
        ServerLevel level = h.getLevel();
        Player player = shooter(h, new ItemStack(Items.CROSSBOW));
        InteractionResult r = player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND).getResult();
        h.assertTrue(r.consumesAction(), "the throw was refused: " + r);
        h.assertTrue(!player.isUsingItem(), "a switched-off crossbow launch still draws");
        GrapplingHookEntity out = GrappleService.hookOf(player);
        h.assertTrue(out != null, "the hook was not thrown");
        assertSpeed(h, out, GrappleConfig.THROW_VELOCITY.get(), "the thrown hook");
        h.assertTrue(out.ropeLength() == GrappleConfig.MAX_ROPE_LENGTH.get(), "a thrown hook has rope " + out.ropeLength());
        h.assertTrue(player.getOffhandItem().getDamageValue() == 0, "a throw wore the crossbow");
        h.assertTrue(GrappleService.release(player) && hooks(player) == 1, "the released hook did not come back");
        h.succeed();
    }

    // ------------------------------------------------------------------ musket

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = LAUNCH_BATCH)
    public static void musketFiresTheHookFastestForOneGunpowder(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = shooter(h, new ItemStack(CombatContent.MUSKET.get()));
        player.getInventory().add(new ItemStack(Items.GUNPOWDER, 3));
        InteractionResult r = player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND).getResult();
        h.assertTrue(r.consumesAction(), "the musket launch was refused: " + r);
        GrapplingHookEntity out = GrappleService.hookOf(player);
        h.assertTrue(out != null, "no hook out after firing the musket (a misfire? the test area has a roof)");
        double musket = GrappleConfig.THROW_VELOCITY.get() * GrappleConfig.MUSKET_SPEED.get();
        assertSpeed(h, out, musket, "the musket hook");
        h.assertTrue(musket > GrappleConfig.THROW_VELOCITY.get() * GrappleConfig.CROSSBOW_SPEED.get(), "the musket is not the fastest");
        h.assertTrue(out.ropeLength() == GrappleConfig.ropeLength(GrappleLaunch.Mode.MUSKET)
                && out.ropeLength() > GrappleConfig.ropeLength(GrappleLaunch.Mode.CROSSBOW), "rope length " + out.ropeLength());
        h.assertTrue(player.getInventory().countItem(Items.GUNPOWDER) == 2, "not exactly one gunpowder was burnt: "
                + player.getInventory().countItem(Items.GUNPOWDER) + " left of 3");
        h.assertTrue(player.getInventory().countItem(CombatContent.LEAD_SHOT.get()) == 0, "lead shot appeared");
        h.assertTrue(!FirearmContent.isLoaded(player.getOffhandItem()), "the musket became loaded");
        h.assertTrue(player.getCooldowns().isOnCooldown(CombatContent.MUSKET.get()), "the musket's cooldown did not start");
        h.assertTrue(player.getMainHandItem().isEmpty(), "the fired hook is still in the hand");
        h.assertTrue(GrappleService.release(player) && hooks(player) == 1, "the released hook did not come back");
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = LAUNCH_BATCH)
    public static void loadedMusketRefusesToFireTheHook(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ItemStack musket = new ItemStack(CombatContent.MUSKET.get());
        FirearmContent.setLoaded(musket, true);
        Player player = shooter(h, musket);
        player.getInventory().add(new ItemStack(Items.GUNPOWDER, 3));
        InteractionResult r = player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND).getResult();
        h.assertTrue(r == InteractionResult.FAIL, "a loaded musket fired the hook: " + r);
        h.assertTrue(GrappleService.hookOf(player) == null, "a hook is out");
        h.assertTrue(player.getMainHandItem().getCount() == 1, "the hook left the hand");
        h.assertTrue(player.getInventory().countItem(Items.GUNPOWDER) == 3, "powder was burnt");
        h.assertTrue(FirearmContent.isLoaded(player.getOffhandItem()), "the musket lost its ball");
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = LAUNCH_BATCH)
    public static void musketWithoutGunpowderClicksAndKeepsTheHook(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = shooter(h, new ItemStack(CombatContent.MUSKET.get()));
        InteractionResult r = player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND).getResult();
        h.assertTrue(r == InteractionResult.FAIL, "a musket without powder fired the hook: " + r);
        h.assertTrue(GrappleService.hookOf(player) == null, "a hook is out");
        h.assertTrue(player.getMainHandItem().getCount() == 1, "the hook left the hand");
        h.succeed();
    }

    // ------------------------------------------------------------------ ring fixtures

    private record Ships(Fixture a, Fixture b) {
    }

    /** Two hulls with a floor ring on each deck: A's at (5, 9, 12), B's at (17, 9, 10) (relative). */
    private static Ships twoShipsWithRings(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helmA = DryHullGameTests.hull(h, 2, false);
        BlockPos helmB = DryHullGameTests.hull(h, 16, false);
        BlockState ring = GrappleContent.MOORING_RING.get().defaultBlockState()
                .setValue(MooringRingBlock.FACE, AttachFace.FLOOR).setValue(MooringRingBlock.FACING, Direction.NORTH);
        h.setBlock(new BlockPos(5, 9, 12), ring);
        h.setBlock(new BlockPos(17, 9, 10), ring);
        return new Ships(DryHullGameTests.assemble(h, helmA), DryHullGameTests.assemble(h, helmB));
    }

    /** Plot position of A's ring (one east and one south of the helm). */
    private static BlockPos ringA(Fixture a) {
        return a.helmPlot().offset(1, 0, 1);
    }

    /** Plot position of B's ring (one west and one north of the helm). */
    private static BlockPos ringB(Fixture b) {
        return b.helmPlot().offset(-1, 0, -1);
    }

    private static Vec3 ringWorld(ServerLevel level, Fixture f, BlockPos ringPlot) {
        return f.ship().toWorld(MooringRingBlock.ringCenter(level, ringPlot));
    }

    private static Player thrower(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.getInventory().clearContent();
        return p;
    }

    /** Keeps {@code player} on A's deck next to the helm while {@code aboard[0]}. */
    private static void keepAboard(GameTestHelper h, Player player, Fixture a, boolean[] aboard) {
        Runnable put = () -> {
            if (aboard[0] && !a.ship().isRemoved()) {
                Vec3 at = a.ship().toWorld(Vec3.atBottomCenterOf(a.helmPlot().east()));
                player.setPos(at.x, at.y, at.z);
            }
        };
        put.run();
        h.onEachTick(put);
    }

    /** A hook flying along +z over B's ring, passing {@code above} blocks over its middle. */
    private static GrapplingHookEntity passOverRing(ServerLevel level, Player player, Fixture b, double above) {
        Vec3 ring = ringWorld(level, b, ringB(b));
        Vec3 from = ring.add(0, above, -4.5);
        return GrappleService.launch(level, player, from, new Vec3(0, 0, GrappleConfig.THROW_VELOCITY.get()),
                new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
    }

    /** A hook launched from 2 blocks west of B's west wall at deck height straight at it (a plain latch). */
    private static GrapplingHookEntity throwAtWestWall(ServerLevel level, Player player, Fixture b) {
        Vec3 target = b.ship().toWorld(Vec3.atCenterOf(b.helmPlot().offset(-2, -1, 0)));
        Vec3 from = target.add(-2.0, 0, 0);
        return GrappleService.launch(level, player, from, new Vec3(GrappleConfig.THROW_VELOCITY.get(), 0, 0),
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

    // ------------------------------------------------------------------ rings

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = RING_BATCH)
    public static void hookPassingCloseToARingLatchesOntoIt(GameTestHelper h) {
        Ships s = twoShipsWithRings(h);
        ServerLevel level = h.getLevel();
        Player player = thrower(h);
        keepAboard(h, player, s.a(), new boolean[]{true});
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> {
            h.assertTrue(MooringRingBlock.isRing(level, ringB(s.b())), "no ring on ship B at " + ringB(s.b()));
            hook[0] = passOverRing(level, player, s.b(), 0.8);
        });
        h.runAfterDelay(21, () -> h.succeedWhen(() -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook is " + g.state());
            h.assertTrue(s.b().ship().id().equals(g.shipId()), "latched onto the wrong ship");
            h.assertTrue(ringB(s.b()).equals(g.latchedBlock()), "latched on " + g.latchedBlock() + ", not the ring " + ringB(s.b()));
            h.assertTrue(g.onRing(), "the latch does not count as a ring");
            h.assertTrue(g.plotPos().distanceTo(MooringRingBlock.ringCenter(level, ringB(s.b()))) < 1.0e-6, "the hook is not on the ring");
            g.release(GrappleRules.Release.NONE);
        }));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = RING_BATCH)
    public static void ringLatchedHookHoldsWhereAPlainLatchSnaps(GameTestHelper h) {
        Ships s = twoShipsWithRings(h);
        ServerLevel level = h.getLevel();
        Player plain = thrower(h);
        Player ringed = thrower(h);
        boolean[] aboard = {true};
        keepAboard(h, plain, s.a(), aboard);
        keepAboard(h, ringed, s.a(), aboard);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[2];
        double rope = GrappleConfig.MAX_ROPE_LENGTH.get();
        double hold = GrappleConfig.RING_HOLD_MULTIPLIER.get() * rope;
        h.runAfterDelay(20, () -> {
            hook[0] = throwAtWestWall(level, plain, s.b());
            hook[1] = passOverRing(level, ringed, s.b(), 0.8);
        });
        h.runAfterDelay(30, () -> {
            h.assertTrue(hook[0].state() == GrapplingHookEntity.State.LATCHED && !hook[0].onRing(), "the plain hook did not latch");
            h.assertTrue(hook[1].state() == GrapplingHookEntity.State.LATCHED && hook[1].onRing(), "the ring hook did not latch on the ring");
            aboard[0] = false;
            // between the rope's length and the ring's hold
            double d = (rope + hold) / 2;
            Vec3 p0 = hook[0].position().add(-d, 0, 0);
            Vec3 p1 = hook[1].position().add(-d, 0, 0);
            plain.setPos(p0.x, p0.y, p0.z);
            ringed.setPos(p1.x, p1.y, p1.z);
        });
        h.runAfterDelay(32, () -> {
            h.assertTrue(hook[0].isRemoved(), "the plain latch did not snap beyond the rope's length");
            h.assertTrue(!hook[1].isRemoved() && hook[1].state() == GrapplingHookEntity.State.LATCHED,
                    "the ring latch snapped below ring_hold_multiplier times the rope");
            Vec3 far = hook[1].position().add(-(hold + 2.0), 0, 0);
            ringed.setPos(far.x, far.y, far.z);
        });
        h.runAfterDelay(34, () -> {
            h.assertTrue(hook[1].isRemoved(), "the ring latch did not snap beyond its hold");
            h.assertTrue(hooks(plain) == 1 && hooks(ringed) == 1, "the snapped hooks did not come back");
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 700, batch = RING_BATCH)
    public static void ropeTiedToARingKeepsHaulingAfterThePlayerLetsGo(GameTestHelper h) {
        Ships s = twoShipsWithRings(h);
        ServerLevel level = h.getLevel();
        Player player = thrower(h);
        boolean[] aboard = {true};
        keepAboard(h, player, s.a(), aboard);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        double[] start = {-1};
        BlockPos ring = ringA(s.a());
        h.runAfterDelay(20, () -> {
            h.assertTrue(MooringRingBlock.isRing(level, ring), "no ring on ship A at " + ring);
            start[0] = horizontal(comWorld(s.a().ship()), comWorld(s.b().ship()));
            hook[0] = throwAtWestWall(level, player, s.b());
        });
        h.runAfterDelay(27, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + g.state());
            player.setShiftKeyDown(true);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ring), Direction.UP, ring, false);
            InteractionResult r = level.getBlockState(ring).useWithoutItem(level, player, hit);
            h.assertTrue(r.consumesAction(), "using the ring did nothing: " + r);
            h.assertTrue(ring.equals(g.tiedRing()), "the rope is not tied to the ring: " + g.tiedRing());
            h.assertTrue(g.syncedTiedRing().filter(ring::equals).isPresent(), "the tie is not synched");
            // the player lets go and walks far away, beyond any rope length
            aboard[0] = false;
            player.setShiftKeyDown(false);
            Vec3 away = g.position().add(-2.5 * GrappleConfig.MAX_ROPE_LENGTH.get(), 0, 0);
            player.setPos(away.x, away.y, away.z);
        });
        h.runAfterDelay(40, () -> h.succeedWhen(() -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!g.isRemoved() && g.state() == GrapplingHookEntity.State.LATCHED, "the hook let go");
            h.assertTrue(s.a().ship().id().equals(g.throwerShipId()), "the ring's ship is not the hauling end");
            double d = horizontal(comWorld(s.a().ship()), comWorld(s.b().ship()));
            h.assertTrue(d < start[0] - 5.0, "the ships are not hauled together yet: " + start[0] + " -> " + d);
            Vec3 at = s.b().ship().toWorld(g.plotPos());
            double rope = horizontal(ringWorld(level, s.a(), ring), at);
            h.assertTrue(!g.taut(), "the rope is still hauling at " + rope + " blocks");
            h.assertTrue(rope > 0.2, "the hulls were pulled into each other: " + rope);
            h.assertTrue(d > 4.0, "the hulls overlap: centers " + d + " apart");
            g.release(GrappleRules.Release.NONE);
            h.assertTrue(hooks(player) == 1, "the released hook did not come back");
            s.a().ship().addVelocity(s.a().ship().linearVelocity().negate(), new Vector3d());
            s.b().ship().addVelocity(s.b().ship().linearVelocity().negate(), new Vector3d());
        }));
    }
}

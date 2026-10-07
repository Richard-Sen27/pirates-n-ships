package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.block.CleatBlock;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.rope.RopeAnchor;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.List;

/**
 * The grappling hook in a real server (docs/design.md §8.3, §8.4, G11). Two closed 5×4×5 plank hulls of
 * {@link DryHullGameTests} float in one basin: ship A at x 2..6 (the thrower's), ship B at x 16..20, both at z 9..13,
 * so their facing walls are 9 blocks apart. The thrower is a mock player (not in the level, so Sable does not carry
 * it): tests that need it aboard put it back onto A's deck every tick. Hooks are launched straight at their target
 * with {@link GrappleService#launch}, as the item does after aiming; ships are removed by {@code ShipTestCleanup} and
 * hooks are released at the end. The RP1 tests put a cleat on each deck, where the GR1 ring tests put their rings.
 */
public final class GrappleGameTests {

    private static final String DISABLED_BATCH = "pirates_n_ships_config_grapple_disabled";
    private static final String OWN_SHIP_OFF_BATCH = "pirates_n_ships_config_grapple_own_ship_off";
    private static final String WORLD_OFF_BATCH = "pirates_n_ships_config_grapple_world_off";

    private GrappleGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(GrappleGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private record Ships(Fixture a, Fixture b) {
    }

    private static Ships twoShips(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helmA = DryHullGameTests.hull(h, 2, false);
        BlockPos helmB = DryHullGameTests.hull(h, 16, false);
        return new Ships(DryHullGameTests.assemble(h, helmA), DryHullGameTests.assemble(h, helmB));
    }

    /** A survival mock player with an empty inventory. */
    private static Player thrower(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.getInventory().clearContent();
        return p;
    }

    /** Keeps {@code player} standing on A's deck, one block east of the helm. */
    private static void keepAboard(GameTestHelper h, Player player, Fixture a) {
        Runnable put = () -> {
            if (!a.ship().isRemoved()) {
                Vec3 at = a.ship().toWorld(Vec3.atBottomCenterOf(a.helmPlot().east()));
                player.setPos(at.x, at.y, at.z);
            }
        };
        put.run();
        h.onEachTick(put);
    }

    /** Plot block of B's west wall at deck height (above the waterline), the hook's target. */
    private static BlockPos westDeckEdge(Fixture f) {
        return f.helmPlot().offset(-2, -1, 0);
    }

    /** Plot block of A's east wall at deck height. */
    private static BlockPos eastDeckEdge(Fixture f) {
        return f.helmPlot().offset(2, -1, 0);
    }

    /** Launches a hook from {@code side} blocks off the target block's world center (along world x), straight at it. */
    private static GrapplingHookEntity throwAt(ServerLevel level, Player player, ShipBody ship, BlockPos plotBlock, double side) {
        Vec3 target = ship.toWorld(Vec3.atCenterOf(plotBlock));
        Vec3 from = target.add(side, 0, 0);
        Vec3 v = target.subtract(from).normalize().scale(GrappleConfig.THROW_VELOCITY.get());
        return GrappleService.launch(level, player, from, v, new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
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

    private static int hooksInInventory(Player p) {
        return p.getInventory().countItem(CombatContent.GRAPPLING_HOOK.get());
    }

    // ------------------------------------------------------------------ latching and hauling

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120)
    public static void hookLatchesOntoAnotherShipsHull(GameTestHelper h) {
        Ships s = twoShips(h);
        ServerLevel level = h.getLevel();
        Player player = thrower(h);
        keepAboard(h, player, s.a());
        BlockPos target = westDeckEdge(s.b());
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> {
            h.assertTrue(level.getBlockState(target).is(Blocks.OAK_PLANKS), "no plank at the target " + target);
            hook[0] = throwAt(level, player, s.b().ship(), target, -2.0);
        });
        h.runAfterDelay(21, () -> h.succeedWhen(() -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook is " + g.state());
            h.assertTrue(s.b().ship().id().equals(g.shipId()), "latched onto the wrong ship");
            h.assertTrue(target.equals(g.latchedBlock()), "latched block " + g.latchedBlock() + ", expected " + target);
            h.assertTrue(g.plotPos().distanceTo(Vec3.atCenterOf(target)) < 1.0, "the stored plot position is not at the block: " + g.plotPos());
            ShipBody owner = SableShips.containing(level, g.latchedBlock());
            h.assertTrue(owner != null && owner.id().equals(s.b().ship().id()), "the latched block is not in ship B's plot");
            h.assertTrue(g.syncedPlotPos().distanceTo(g.plotPos()) < 1.0e-3, "the synched plot position differs");
            h.assertTrue(g.taut(), "the rope between the two ships is not taut");
            h.assertTrue(g.position().distanceTo(s.b().ship().toWorld(g.plotPos())) < 0.5, "the hook does not sit on the hull");
            h.assertTrue(hooksInInventory(player) == 0, "the hook is back already");
            g.release(GrappleRules.Release.NONE);
            h.assertTrue(hooksInInventory(player) == 1, "a released hook did not come back");
        }));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 700)
    public static void haulingPullsTheShipsTogetherAndStopsAtTheHoldDistance(GameTestHelper h) {
        Ships s = twoShips(h);
        ServerLevel level = h.getLevel();
        Player player = thrower(h);
        keepAboard(h, player, s.a());
        BlockPos target = westDeckEdge(s.b());
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        double[] start = {-1};
        h.runAfterDelay(20, () -> {
            start[0] = horizontal(comWorld(s.a().ship()), comWorld(s.b().ship()));
            hook[0] = throwAt(level, player, s.b().ship(), target, -2.0);
        });
        h.runAfterDelay(30, () -> {
            h.assertTrue(hook[0].state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + hook[0].state());
            h.succeedWhen(() -> {
                GrapplingHookEntity g = hook[0];
                h.assertTrue(!g.isRemoved() && g.state() == GrapplingHookEntity.State.LATCHED, "the hook let go");
                double d = horizontal(comWorld(s.a().ship()), comWorld(s.b().ship()));
                h.assertTrue(d < start[0] - 5.0, "the ships are not hauled together yet: " + start[0] + " -> " + d);
                Vec3 anchor = s.a().ship().toWorld(Vec3.atCenterOf(eastDeckEdge(s.a())));
                Vec3 at = s.b().ship().toWorld(g.plotPos());
                double rope = horizontal(anchor, at);
                h.assertTrue(!g.taut(), "the rope is still hauling at " + rope + " blocks");
                h.assertTrue(rope > 0.2, "the hulls were pulled into each other: " + rope);
                h.assertTrue(d > 4.0, "the hulls overlap: centers " + d + " apart");
                g.release(GrappleRules.Release.NONE);
                s.a().ship().addVelocity(s.a().ship().linearVelocity().negate(), new Vector3d());
                s.b().ship().addVelocity(s.b().ship().linearVelocity().negate(), new Vector3d());
            });
        });
    }

    // ------------------------------------------------------------------ release

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120)
    public static void walkingBeyondTheRopeSnapsItAndReturnsTheHook(GameTestHelper h) {
        Ships s = twoShips(h);
        ServerLevel level = h.getLevel();
        Player player = thrower(h);
        boolean[] aboard = {true};
        Runnable put = () -> {
            if (aboard[0] && !s.a().ship().isRemoved()) {
                Vec3 at = s.a().ship().toWorld(Vec3.atBottomCenterOf(s.a().helmPlot().east()));
                player.setPos(at.x, at.y, at.z);
            }
        };
        put.run();
        h.onEachTick(put);
        BlockPos target = westDeckEdge(s.b());
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> hook[0] = throwAt(level, player, s.b().ship(), target, -2.0));
        h.runAfterDelay(26, () -> {
            h.assertTrue(hook[0].state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + hook[0].state());
            aboard[0] = false;
            Vec3 far = hook[0].position().add(-(GrappleConfig.MAX_ROPE_LENGTH.get() + 2.0), 0, 0);
            player.setPos(far.x, far.y, far.z);
        });
        h.runAfterDelay(27, () -> h.succeedWhen(() -> {
            h.assertTrue(hook[0].isRemoved(), "the rope did not snap");
            h.assertTrue(hooksInInventory(player) == 1, "the hook did not come back (rope_breaks_lose_hook is off)");
            h.assertTrue(GrappleService.hookOf(player) == null, "the player still has a hook out");
        }));
    }

    /** {@code grapple.latch_own_ship} off: a hook on the thrower's own ship misses and comes back (the pre-GR4 rule). */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = OWN_SHIP_OFF_BATCH)
    public static void hookOnTheThrowersOwnShipDoesNotLatchAndComesBack(GameTestHelper h) {
        ConfigOverrides.during(h, GrappleConfig.LATCH_OWN_SHIP, false);
        Ships s = twoShips(h);
        ServerLevel level = h.getLevel();
        Player player = thrower(h);
        keepAboard(h, player, s.a());
        BlockPos own = eastDeckEdge(s.a());
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        long[] thrownAt = {0};
        h.runAfterDelay(20, () -> {
            hook[0] = throwAt(level, player, s.a().ship(), own, 2.0);
            thrownAt[0] = h.getTick();
        });
        h.runAfterDelay(24, () -> {
            h.assertTrue(hook[0].state() == GrapplingHookEntity.State.RETRACTING,
                    "a hook on the thrower's own ship should miss, it is " + hook[0].state());
            h.assertTrue(hook[0].shipId() == null, "the hook latched onto its own ship");
        });
        h.runAfterDelay(24 + GrappleConfig.RETRACT_TICKS.get() / 2, () ->
                h.assertTrue(!hook[0].isRemoved() && hooksInInventory(player) == 0, "the hook came back before retract_ticks"));
        h.runAfterDelay(26, () -> h.succeedWhen(() -> {
            h.assertTrue(hook[0].isRemoved(), "the hook is still out");
            h.assertTrue(hooksInInventory(player) == 1, "the hook did not come back");
            h.assertTrue(h.getTick() - thrownAt[0] >= GrappleConfig.RETRACT_TICKS.get(), "retracted too early");
        }));
    }

    /** {@code grapple.latch_world_blocks} off: a hook on land misses and comes back (the pre-GR4 rule). */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 120, batch = WORLD_OFF_BATCH)
    public static void hookOnLandDoesNotLatchAndComesBackAfterTheRetractTime(GameTestHelper h) {
        ConfigOverrides.during(h, GrappleConfig.LATCH_WORLD_BLOCKS, false);
        ServerLevel level = h.getLevel();
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(7, y, 4), Blocks.STONE);
        Player player = thrower(h);
        Vec3 stand = h.absoluteVec(new Vec3(1.5, 1, 4.5));
        player.setPos(stand.x, stand.y, stand.z);
        Vec3 from = h.absoluteVec(new Vec3(3.5, 2.5, 4.5));
        GrapplingHookEntity hook = GrappleService.launch(level, player, from, new Vec3(GrappleConfig.THROW_VELOCITY.get(), 0, 0),
                new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
        h.runAfterDelay(5, () -> {
            h.assertTrue(hook.state() == GrapplingHookEntity.State.RETRACTING, "a hook on land should miss, it is " + hook.state());
            h.assertTrue(hook.shipId() == null, "the hook latched onto land");
        });
        h.runAfterDelay(GrappleConfig.RETRACT_TICKS.get() - 5, () ->
                h.assertTrue(!hook.isRemoved() && hooksInInventory(player) == 0, "the hook came back before retract_ticks"));
        h.runAfterDelay(GrappleConfig.RETRACT_TICKS.get() - 4, () -> h.succeedWhen(() -> {
            h.assertTrue(hook.isRemoved(), "the hook is still out");
            h.assertTrue(hooksInInventory(player) == 1, "the hook did not come back");
        }));
    }

    // ------------------------------------------------------------------ any surface (GR4)

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void hookOnTheThrowersOwnShipLatchesWithoutHauling(GameTestHelper h) {
        Ships s = twoShips(h);
        ServerLevel level = h.getLevel();
        Player player = thrower(h);
        keepAboard(h, player, s.a());
        BlockPos own = eastDeckEdge(s.a());
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        Vec3[] start = new Vec3[2];
        h.runAfterDelay(20, () -> hook[0] = throwAt(level, player, s.a().ship(), own, 2.0));
        h.runAfterDelay(25, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "a hook on the thrower's own ship should latch, it is " + g.state());
            h.assertTrue(s.a().ship().id().equals(g.shipId()), "latched onto the wrong body: " + g.shipId());
            h.assertTrue(own.equals(g.latchedBlock()), "latched block " + g.latchedBlock() + ", expected " + own);
            h.assertTrue(g.haulKind() == GrappleRules.Haul.NONE, "a rope within one ship pulls: " + g.haulKind());
            h.assertTrue(!g.taut(), "a rope within one ship is taut");
            start[0] = comWorld(s.a().ship());
            start[1] = comWorld(s.b().ship());
        });
        h.runAfterDelay(65, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!g.isRemoved() && g.state() == GrapplingHookEntity.State.LATCHED, "the hook let go");
            double movedA = horizontal(start[0], comWorld(s.a().ship()));
            double movedB = horizontal(start[1], comWorld(s.b().ship()));
            h.assertTrue(movedA < 0.5 && movedB < 0.5, "the ships moved: A " + movedA + ", B " + movedB);
            h.assertTrue(g.ropeFarEnd(level).distanceTo(s.a().ship().toWorld(g.plotPos())) < 1.0e-6, "the far end does not follow ship A");
            g.release(GrappleRules.Release.NONE);
            h.assertTrue(hooksInInventory(player) == 1, "the released hook did not come back");
            h.succeed();
        });
    }

    /**
     * A kedge line (GR4). Only one ship pulls (against the water alone, about 0.34 blocks per second for this hull with
     * the default haul_force, measured), so it takes about twice as long as two ships hauling each other.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 1200)
    public static void hookOnAWorldBlockFromAShipHaulsTheShipTowardIt(GameTestHelper h) {
        // only ship A in the basin; the hook bites into the basin's east wall (a world block), 16 blocks away
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        Fixture a = DryHullGameTests.assemble(h, DryHullGameTests.hull(h, 2, false));
        ServerLevel level = h.getLevel();
        Player player = thrower(h);
        keepAboard(h, player, a);
        BlockPos wall = new BlockPos(23, 8, 11);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        double[] startX = {0};
        h.runAfterDelay(20, () -> {
            h.assertTrue(h.getBlockState(wall).is(Blocks.STONE), "no basin wall at " + wall);
            startX[0] = comWorld(a.ship()).x;
            Vec3 target = h.absoluteVec(Vec3.atCenterOf(wall));
            hook[0] = GrappleService.launch(level, player, target.add(-2.0, 0, 0), new Vec3(GrappleConfig.THROW_VELOCITY.get(), 0, 0),
                    new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
        });
        h.runAfterDelay(24, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED && g.onWorldBlock(), "the hook did not latch on the wall: " + g.state());
            h.assertTrue(h.absolutePos(wall).equals(g.latchedBlock()), "latched on " + g.latchedBlock());
            h.assertTrue(g.haulKind() == GrappleRules.Haul.KEDGE, "a world block from a ship is no kedge: " + g.haulKind());
            h.assertTrue(a.ship().id().equals(g.throwerShipId()), "ship A is not the hauled end");
            h.assertTrue(g.taut(), "the kedge line is not taut");
        });
        h.runAfterDelay(30, () -> h.succeedWhen(() -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!g.isRemoved() && g.state() == GrapplingHookEntity.State.LATCHED, "the hook let go");
            double moved = comWorld(a.ship()).x - startX[0];
            Vec3 anchor = g.anchorPlot() == null ? null : a.ship().toWorld(g.anchorPlot());
            String state = "moved " + moved + ", rope " + (anchor == null ? "?" : horizontal(anchor, g.position()))
                    + ", holding " + g.holding().holding() + ", anchor " + anchor;
            h.assertTrue(moved > 8.0, "ship A is not hauled toward the wall yet: " + state);
            h.assertTrue(!g.taut(), "the kedge line is still hauling: " + state);
            h.assertTrue(g.position().distanceTo(h.absoluteVec(new Vec3(23.0, 8.5, 11.5))) < 0.6, "the hook left the wall: " + g.position());
            g.release(GrappleRules.Release.NONE);
            a.ship().addVelocity(a.ship().linearVelocity().negate(), new Vector3d());
        }));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 120)
    public static void hookOnLandLatchesAndHoldsTheWorldPoint(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(7, y, 4), Blocks.STONE);
        Player player = thrower(h);
        Vec3 stand = h.absoluteVec(new Vec3(1.5, 1, 4.5));
        player.setPos(stand.x, stand.y, stand.z);
        GrapplingHookEntity hook = GrappleService.launch(level, player, h.absoluteVec(new Vec3(3.5, 2.5, 4.5)),
                new Vec3(GrappleConfig.THROW_VELOCITY.get(), 0, 0), new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
        h.runAfterDelay(5, () -> {
            h.assertTrue(hook.state() == GrapplingHookEntity.State.LATCHED && hook.onWorldBlock(), "a hook on land should latch, it is " + hook.state());
            h.assertTrue(hook.shipId() == null, "latched onto a ship");
            h.assertTrue(h.absolutePos(new BlockPos(7, 2, 4)).equals(hook.latchedBlock()), "latched on " + hook.latchedBlock());
            h.assertTrue(hook.haulKind() == GrappleRules.Haul.NONE && !hook.taut(), "a rope between two land points pulls");
            Vec3 far = hook.ropeFarEnd(level);
            h.assertTrue(far != null && Math.abs(far.x - h.absoluteVec(new Vec3(7.0, 0, 0)).x) < 0.2, "the far end is not on the stone face: " + far);
        });
        h.runAfterDelay(GrappleConfig.RETRACT_TICKS.get() + 10, () -> {
            h.assertTrue(!hook.isRemoved() && hook.state() == GrapplingHookEntity.State.LATCHED, "the latched hook came back by itself");
            // breaking the block lets the hook go
            h.setBlock(new BlockPos(7, 2, 4), Blocks.AIR);
        });
        h.runAfterDelay(GrappleConfig.RETRACT_TICKS.get() + 12, () -> {
            h.assertTrue(hook.isRemoved(), "the hook still holds a broken block");
            h.assertTrue(hooksInInventory(player) == 1, "the hook did not come back");
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void hookSlipsOffLeaves(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        for (int y = 1; y <= 3; y++) {
            h.setBlock(new BlockPos(7, y, 4), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        }
        Player player = thrower(h);
        Vec3 stand = h.absoluteVec(new Vec3(1.5, 1, 4.5));
        player.setPos(stand.x, stand.y, stand.z);
        GrapplingHookEntity hook = GrappleService.launch(level, player, h.absoluteVec(new Vec3(3.5, 2.5, 4.5)),
                new Vec3(GrappleConfig.THROW_VELOCITY.get(), 0, 0), new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
        h.runAfterDelay(5, () -> {
            h.assertTrue(h.getBlockState(new BlockPos(7, 2, 4)).is(GrappleContent.HOOK_SLIPS), "leaves are not in the slip tag");
            h.assertTrue(hook.state() == GrapplingHookEntity.State.RETRACTING, "a hook on leaves should slip off, it is " + hook.state());
            hook.release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void hookHittingAnEntityHurtsItAndDoesNotLatch(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        IronGolem golem = h.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new Vec3(5.2, 1, 4.5));
        float full = golem.getHealth();
        Player player = thrower(h);
        Vec3 stand = h.absoluteVec(new Vec3(0.5, 1, 4.5));
        player.setPos(stand.x, stand.y, stand.z);
        GrapplingHookEntity hook = GrappleService.launch(level, player, h.absoluteVec(new Vec3(1.5, 2.0, 4.5)),
                new Vec3(GrappleConfig.THROW_VELOCITY.get(), 0, 0), new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
        h.succeedWhen(() -> {
            h.assertTrue(golem.getHealth() < full, "the golem has not been hit yet");
            double expected = full - GrappleConfig.ENTITY_DAMAGE.get();
            h.assertTrue(Math.abs(golem.getHealth() - expected) < 0.01, "expected health " + expected + ", got " + golem.getHealth());
            h.assertTrue(hook.state() == GrapplingHookEntity.State.RETRACTING, "the hook should drop off an entity: " + hook.state());
            hook.release(GrappleRules.Release.NONE);
        });
    }

    // ------------------------------------------------------------------ the item

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void throwingTakesTheItemAndASecondThrowReleasesTheFirst(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = thrower(h);
        Vec3 stand = h.absoluteVec(new Vec3(4.5, 1, 4.5));
        player.setPos(stand.x, stand.y, stand.z);
        player.setXRot(-60.0f); // up, so the hook stays above the test area until it is released
        ItemStack stack = new ItemStack(CombatContent.GRAPPLING_HOOK.get(), 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult r = stack.use(level, player, InteractionHand.MAIN_HAND).getResult();
        h.assertTrue(r.consumesAction(), "throwing was refused: " + r);
        h.assertTrue(player.getMainHandItem().isEmpty(), "the thrown hook is still in the hand");
        GrapplingHookEntity first = GrappleService.hookOf(player);
        h.assertTrue(first != null && first.getOwner() == player, "no hook out after the throw");
        h.assertTrue(first.getDeltaMovement().length() > 0.5 * GrappleConfig.THROW_VELOCITY.get(), "the hook was not thrown");

        GrapplingHookEntity second = GrappleService.launch(level, player, stand.add(0, 3, 0), new Vec3(0, 0.5, 0),
                new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
        h.assertTrue(first.isRemoved(), "the first hook is still out after a second throw");
        h.assertTrue(hooksInInventory(player) == 1, "the first hook did not come back");
        h.assertTrue(GrappleService.hookOf(player) == second, "the second hook is not the player's hook");
        h.assertTrue(GrappleService.release(player), "releasing found no hook");
        h.assertTrue(second.isRemoved() && hooksInInventory(player) == 2, "the released hook did not come back");
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = DISABLED_BATCH)
    public static void disabledHookIsInertAndHooksOutAreReleased(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player other = thrower(h);
        Vec3 stand = h.absoluteVec(new Vec3(4.5, 1, 4.5));
        other.setPos(stand.x, stand.y, stand.z);
        GrapplingHookEntity out = GrappleService.launch(level, other, stand.add(0, 2, 0), Vec3.ZERO,
                new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
        ConfigOverrides.during(h, GrappleConfig.ENABLED, false);

        Player player = thrower(h);
        player.setPos(stand.x, stand.y, stand.z);
        ItemStack stack = new ItemStack(CombatContent.GRAPPLING_HOOK.get(), 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult r = stack.use(level, player, InteractionHand.MAIN_HAND).getResult();
        h.assertTrue(r == InteractionResult.PASS, "a disabled hook did something: " + r);
        h.assertTrue(stack.getCount() == 1, "a disabled hook was used up");
        h.assertTrue(GrappleService.hookOf(player) == null, "a disabled hook was thrown");
        h.runAfterDelay(2, () -> {
            h.assertTrue(out.isRemoved(), "a hook out when the hook was disabled is still there");
            h.assertTrue(hooksInInventory(other) == 1, "the released hook did not come back");
            List<GrapplingHookEntity> left = h.getEntities(GrappleContent.HOOK.get());
            h.assertTrue(left.isEmpty(), "hooks left: " + left.size());
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ cleats as rope anchors (RP1)

    private static final String CLEAT_BATCH = "pirates_n_ships_grapple_cleat";
    private static final String CLEATS_OFF_BATCH = "pirates_n_ships_config_grapple_cleats_off";

    /** The two hulls with a floor cleat on each deck: A's at (5, 9, 12), B's at (17, 9, 10) (where the ring tests put rings). */
    private static Ships twoShipsWithCleats(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helmA = DryHullGameTests.hull(h, 2, false);
        BlockPos helmB = DryHullGameTests.hull(h, 16, false);
        h.setBlock(new BlockPos(5, 9, 12), SailingGameTestsShips.cleat(AttachFace.FLOOR, Direction.NORTH));
        h.setBlock(new BlockPos(17, 9, 10), SailingGameTestsShips.cleat(AttachFace.FLOOR, Direction.EAST));
        return new Ships(DryHullGameTests.assemble(h, helmA), DryHullGameTests.assemble(h, helmB));
    }

    /** Plot position of A's cleat (one east and one south of the helm). */
    private static BlockPos cleatA(Fixture a) {
        return a.helmPlot().offset(1, 0, 1);
    }

    /** Plot position of B's cleat (one west and one north of the helm). */
    private static BlockPos cleatB(Fixture b) {
        return b.helmPlot().offset(-1, 0, -1);
    }

    /** A hook flying along +z over B's cleat, passing {@code above} blocks over its horn. */
    private static GrapplingHookEntity passOverCleat(ServerLevel level, Player player, Fixture b, double above) {
        Vec3 horn = b.ship().toWorld(RopeAnchor.point(level, cleatB(b)));
        Vec3 from = horn.add(0, above, -4.5);
        return GrappleService.launch(level, player, from, new Vec3(0, 0, GrappleConfig.THROW_VELOCITY.get()),
                new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = CLEAT_BATCH)
    public static void hookPassingCloseToACleatLatchesOntoItsHorn(GameTestHelper h) {
        Ships s = twoShipsWithCleats(h);
        ServerLevel level = h.getLevel();
        Player player = thrower(h);
        keepAboard(h, player, s.a());
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> {
            h.assertTrue(level.getBlockState(cleatB(s.b())).getBlock() instanceof CleatBlock, "no cleat on ship B at " + cleatB(s.b()));
            hook[0] = passOverCleat(level, player, s.b(), 0.8);
        });
        h.runAfterDelay(21, () -> h.succeedWhen(() -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook is " + g.state());
            h.assertTrue(s.b().ship().id().equals(g.shipId()), "latched onto the wrong ship");
            h.assertTrue(cleatB(s.b()).equals(g.latchedBlock()), "latched on " + g.latchedBlock() + ", not the cleat " + cleatB(s.b()));
            h.assertTrue(g.onRing(), "the latch on a cleat does not hold like a ring");
            h.assertTrue(g.plotPos().distanceTo(RopeAnchor.point(level, cleatB(s.b()))) < 1.0e-6, "the hook is not on the horn: " + g.plotPos());
            g.release(GrappleRules.Release.NONE);
        }));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 700, batch = CLEAT_BATCH)
    public static void ropeTiedToACleatHoldsTheShipNotThePlayer(GameTestHelper h) {
        Ships s = twoShipsWithCleats(h);
        ServerLevel level = h.getLevel();
        Player player = thrower(h);
        boolean[] aboard = {true};
        Runnable put = () -> {
            if (aboard[0] && !s.a().ship().isRemoved()) {
                Vec3 at = s.a().ship().toWorld(Vec3.atBottomCenterOf(s.a().helmPlot().east()));
                player.setPos(at.x, at.y, at.z);
            }
        };
        put.run();
        h.onEachTick(put);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        double[] start = {-1};
        BlockPos cleat = cleatA(s.a());
        BlockPos target = westDeckEdge(s.b());
        h.runAfterDelay(20, () -> {
            h.assertTrue(level.getBlockState(cleat).getBlock() instanceof CleatBlock, "no cleat on ship A at " + cleat);
            start[0] = horizontal(comWorld(s.a().ship()), comWorld(s.b().ship()));
            hook[0] = throwAt(level, player, s.b().ship(), target, -2.0);
        });
        h.runAfterDelay(27, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + g.state());
            player.setShiftKeyDown(true);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(cleat), Direction.UP, cleat, false);
            InteractionResult r = level.getBlockState(cleat).useWithoutItem(level, player, hit);
            h.assertTrue(r.consumesAction(), "using the cleat did nothing: " + r);
            h.assertTrue(cleat.equals(g.tiedRing()), "the rope is not tied to the cleat: " + g.tiedRing());
            h.assertTrue(level.getBlockState(cleat).getValue(CleatBlock.TRIM) == SailTrim.FURLED, "tying off also cycled the trim");
            // the player lets go and walks far away, beyond any rope length
            aboard[0] = false;
            player.setShiftKeyDown(false);
            Vec3 away = g.position().add(-2.5 * GrappleConfig.MAX_ROPE_LENGTH.get(), 0, 0);
            player.setPos(away.x, away.y, away.z);
        });
        h.runAfterDelay(40, () -> h.succeedWhen(() -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!g.isRemoved() && g.state() == GrapplingHookEntity.State.LATCHED, "the hook let go");
            h.assertTrue(s.a().ship().id().equals(g.throwerShipId()), "the cleat's ship is not the hauling end");
            Vec3 near = g.ropeNearEnd(level);
            Vec3 horn = s.a().ship().toWorld(RopeAnchor.point(level, cleat));
            h.assertTrue(near != null && near.distanceTo(horn) < 1.0e-6, "the rope's near end is not the cleat's horn: " + near);
            double d = horizontal(comWorld(s.a().ship()), comWorld(s.b().ship()));
            h.assertTrue(d < start[0] - 5.0, "the ships are not hauled together yet: " + start[0] + " -> " + d);
            h.assertTrue(!g.taut(), "the rope is still hauling");
            h.assertTrue(d > 4.0, "the hulls overlap: centers " + d + " apart");
            g.release(GrappleRules.Release.NONE);
            h.assertTrue(hooksInInventory(player) == 1, "the released hook did not come back");
            s.a().ship().addVelocity(s.a().ship().linearVelocity().negate(), new Vector3d());
            s.b().ship().addVelocity(s.b().ship().linearVelocity().negate(), new Vector3d());
        }));
    }

    /** With {@code grapple.cleats_enabled} off a hook flies past a cleat, and using the cleat does not tie the rope. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = CLEATS_OFF_BATCH)
    public static void cleatsOffNeitherCatchNorTieOff(GameTestHelper h) {
        Ships s = twoShipsWithCleats(h);
        ConfigOverrides.during(h, GrappleConfig.CLEATS_ENABLED, false);
        ServerLevel level = h.getLevel();
        Player player = thrower(h);
        keepAboard(h, player, s.a());
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> hook[0] = passOverCleat(level, player, s.b(), 0.8));
        h.runAfterDelay(26, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!cleatB(s.b()).equals(g.latchedBlock()) && !g.onRing(), "a hook latched on a cleat while cleats are off");
            g.release(GrappleRules.Release.NONE);
            hook[0] = throwAt(level, player, s.b().ship(), westDeckEdge(s.b()), -2.0);
        });
        h.runAfterDelay(33, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + g.state());
            BlockPos cleat = cleatA(s.a());
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(cleat), Direction.UP, cleat, false);
            level.getBlockState(cleat).useWithoutItem(level, player, hit);
            h.assertTrue(g.tiedRing() == null, "the rope was tied to a cleat while cleats are off");
            g.release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }
}

package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
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
 * hooks are released at the end.
 */
public final class GrappleGameTests {

    private static final String DISABLED_BATCH = "pirates_n_ships_config_grapple_disabled";

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

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void hookOnTheThrowersOwnShipDoesNotLatchAndComesBack(GameTestHelper h) {
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

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 120)
    public static void hookOnLandDoesNotLatchAndComesBackAfterTheRetractTime(GameTestHelper h) {
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
}

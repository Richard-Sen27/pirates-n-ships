package com.richardsenger.piratesnships.ship.decor.railing;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.combat.grapple.GrappleConfig;
import com.richardsenger.piratesnships.combat.grapple.GrappleRules;
import com.richardsenger.piratesnships.combat.grapple.GrappleService;
import com.richardsenger.piratesnships.combat.grapple.GrapplingHookEntity;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Collection;

/**
 * The grappling hook on a railing (RAIL3, docs/design.md §4.8 "Railing extras (RAIL3–RAIL5)", §8.3): two closed hulls
 * of {@link DryHullGameTests} in one basin as in {@code GrappleGameTests} (ship A at x 2..6, the thrower's; ship B at
 * x 16..20, a railing run along B's west gunwale at x 16, z 10..12, y 9). A hook thrown at B's railing latches on it,
 * holds like a cleat (twice the rope's length) and hauls the ships together; with {@code grapple.railing_break_force}
 * below the haul's pull the railing is torn out and the hook comes back; with {@code latches_hold} off the railing is a
 * plain block for the hook.
 */
public final class RailingGrappleGameTests {

    private static final String BATCH = "pirates_n_ships_config_grapple_railing_";

    private RailingGrappleGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(RailingGrappleGameTests.class);
    }

    // ------------------------------------------------------------------ fixture

    private record Ships(Fixture a, Fixture b) {
    }

    /** Both hulls, B with a railing run on its west gunwale, assembled. */
    private static Ships ships(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helmA = DryHullGameTests.hull(h, 2, false);
        BlockPos helmB = DryHullGameTests.hull(h, 16, false);
        for (int z = 10; z <= 12; z++) {
            BlockPos abs = h.absolutePos(new BlockPos(16, 9, z));
            BlockState s = RailingContent.RAILINGS.get(ShipWood.OAK).defaultBlockState().setValue(RailingBlock.WEST, true);
            h.getLevel().setBlock(abs, RailingBlock.withPosts(s, h.getLevel(), abs), 3);
        }
        return new Ships(DryHullGameTests.assemble(h, helmA), DryHullGameTests.assemble(h, helmB));
    }

    /** Plot block of B's middle railing (helm at (18, 9, 11), the railing two west of it). */
    private static BlockPos railingOf(Fixture b) {
        return b.helmPlot().offset(-2, 0, 0);
    }

    private static Player thrower(GameTestHelper h, Fixture a) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.getInventory().clearContent();
        Runnable put = () -> {
            if (!a.ship().isRemoved()) {
                Vec3 at = a.ship().toWorld(Vec3.atBottomCenterOf(a.helmPlot().east()));
                p.setPos(at.x, at.y, at.z);
            }
        };
        put.run();
        h.onEachTick(put);
        return p;
    }

    /** A hook launched from 2 blocks west of the railing's west rail, straight east at it. */
    private static GrapplingHookEntity throwAtRailing(ServerLevel level, Player player, ShipBody ship, BlockPos railing) {
        Vec3 target = ship.toWorld(new Vec3(railing.getX() + 0.05, railing.getY() + 0.5, railing.getZ() + 0.5));
        Vec3 from = target.add(-2.0, 0, 0);
        Vec3 v = target.subtract(from).normalize().scale(GrappleConfig.THROW_VELOCITY.get());
        return GrappleService.launch(level, player, from, v, new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
    }

    private static Vec3 com(ShipBody ship) {
        Vector3d c = new Vector3d();
        if (!ship.centerOfMass(c)) throw new GameTestAssertException("no center of mass");
        Vector3d w = ship.toWorld(c, new Vector3d());
        return new Vec3(w.x, w.y, w.z);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static boolean isRailing(ServerLevel level, BlockPos plot) {
        return level.getBlockState(plot).getBlock() instanceof RailingBlock;
    }

    // ------------------------------------------------------------------ tests

    /** The hook latches on B's railing, holds like a cleat (twice the rope) and hauls the ships at least two blocks closer. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 400)
    public static void aHookOnARailingHoldsLikeACleatAndHauls(GameTestHelper h) {
        Ships s = ships(h);
        ServerLevel level = h.getLevel();
        Player player = thrower(h, s.a());
        BlockPos rail = railingOf(s.b());
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        double[] start = {-1};
        h.runAfterDelay(20, () -> {
            h.assertTrue(isRailing(level, rail), "no railing at " + rail + " in B's plot");
            start[0] = horizontal(com(s.a().ship()), com(s.b().ship()));
            hook[0] = throwAtRailing(level, player, s.b().ship(), rail);
        });
        h.runAfterDelay(24, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook is " + g.state());
            h.assertTrue(rail.equals(g.latchedBlock()) && s.b().ship().id().equals(g.shipId()), "latched on " + g.latchedBlock());
            h.assertTrue(g.onStrongPoint() && g.onRing(), "the railing does not hold like a cleat");
            h.assertTrue(Math.abs(g.breakLength() - g.ropeLength() * GrappleConfig.RING_HOLD_MULTIPLIER.get()) < 1e-9,
                    "break length " + g.breakLength());
            h.succeedWhen(() -> {
                h.assertTrue(!g.isRemoved() && g.state() == GrapplingHookEntity.State.LATCHED, "the hook let go");
                double d = horizontal(com(s.a().ship()), com(s.b().ship()));
                h.assertTrue(d < start[0] - 2.0, "not hauled yet: " + start[0] + " -> " + d);
                h.assertTrue(isRailing(level, rail), "the default haul tore the railing out");
                g.release(GrappleRules.Release.NONE);
                s.a().ship().addVelocity(s.a().ship().linearVelocity().negate(), new Vector3d());
                s.b().ship().addVelocity(s.b().ship().linearVelocity().negate(), new Vector3d());
            });
        });
    }

    /**
     * With {@code railing_break_force} 50, below the ship-to-ship haul's 120: the haul tears the railing out of B's
     * gunwale and the hook comes back to the thrower.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH + "break")
    public static void aHardHaulTearsTheRailingOut(GameTestHelper h) {
        ConfigOverrides.during(h, GrappleConfig.RAILING_BREAK_FORCE, 50.0);
        Ships s = ships(h);
        ServerLevel level = h.getLevel();
        Player player = thrower(h, s.a());
        BlockPos rail = railingOf(s.b());
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> hook[0] = throwAtRailing(level, player, s.b().ship(), rail));
        h.runAfterDelay(22, () -> h.succeedWhen(() -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!isRailing(level, rail), "the railing still stands (hook " + g.state() + ", latched on " + g.latchedBlock() + ")");
            h.assertTrue(g.isRemoved(), "the hook still holds");
            h.assertTrue(player.getInventory().countItem(CombatContent.GRAPPLING_HOOK.get()) == 1, "the hook did not come back");
            h.assertTrue(isRailing(level, rail.north()) && isRailing(level, rail.south()), "more than the hooked railing broke");
        }));
    }

    /** With {@code latches_hold} off the hook still bites the railing (any surface) but holds like any block. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = BATCH + "plain")
    public static void withLatchesHoldOffARailingIsAPlainBlock(GameTestHelper h) {
        ConfigOverrides.during(h, GrappleConfig.LATCHES_HOLD, false);
        Ships s = ships(h);
        ServerLevel level = h.getLevel();
        Player player = thrower(h, s.a());
        BlockPos rail = railingOf(s.b());
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> hook[0] = throwAtRailing(level, player, s.b().ship(), rail));
        h.runAfterDelay(24, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED && rail.equals(g.latchedBlock()), "not latched on the railing: " + g.state());
            h.assertTrue(!g.onStrongPoint() && !g.onRing(), "the railing holds like a cleat with latches_hold off");
            h.assertTrue(Math.abs(g.breakLength() - g.ropeLength()) < 1e-9, "break length " + g.breakLength());
            g.release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }
}

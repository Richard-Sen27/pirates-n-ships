package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.decor.railing.RailingBlock;
import com.richardsenger.piratesnships.ship.decor.railing.RailingContent;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.Locale;

/**
 * GR4: the grappling hook's pose in a real server (docs/design.md §8.3, {@link HookPose}). On a 9×9 stone floor: a hook
 * in flight points along its velocity every tick; latched on a railing it hangs, on the floor it lies flat, in a stone
 * wall it bites; slipping off leaves it falls point down. On a ship's deck the pose is kept in the plot's frame.
 */
public final class HookPoseGameTests {

    private HookPoseGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(HookPoseGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    /** A survival mock player with an empty inventory, standing at {@code at} (relative). */
    private static Player thrower(GameTestHelper h, Vec3 at) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.getInventory().clearContent();
        Vec3 abs = h.absoluteVec(at);
        p.setPos(abs.x, abs.y, abs.z);
        return p;
    }

    private static GrapplingHookEntity launch(GameTestHelper h, Player p, Vec3 from, Vec3 velocity) {
        return GrappleService.launch(h.getLevel(), p, h.absoluteVec(from), velocity, new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
    }

    private static String v(Vector3f v) {
        return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }

    private static void assertLatched(GameTestHelper h, GrapplingHookEntity g, HookPose.Kind kind) {
        h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook is " + g.state());
        h.assertTrue(g.pose() == kind, "the latched hook should be " + kind + ", it is " + g.pose());
    }

    // ------------------------------------------------------------------ tests

    /** A lobbed hook points along its velocity every tick of its flight, up on the way up, down on the way down. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 120)
    public static void inFlightTheHookPointsAlongItsVelocity(GameTestHelper h) {
        floor(h);
        Player p = thrower(h, new Vec3(1.5, 1, 1.5));
        // a steep lob that stays inside the test's barrier case (9 high): up 4 blocks, over the top and down again
        GrapplingHookEntity g = launch(h, p, new Vec3(1.5, 1.5, 4.5), new Vec3(0.1, 0.4, 0.0));
        Vector3f[] before = {null};
        int[] checked = {0};
        boolean[] sawDown = {false};
        String[] end = {"still flying"};
        h.onEachTick(() -> {
            if (g.isRemoved() || g.state() != GrapplingHookEntity.State.FLYING) {
                if (end[0].equals("still flying")) {
                    end[0] = (g.isRemoved() ? "removed" : g.state().name()) + " at " + h.relativeVec(g.position()) + " after " + g.tickCount + " ticks";
                }
                return;
            }
            Vec3 vel = g.getDeltaMovement();
            if (vel.lengthSqr() < 1.0e-6) {
                return;
            }
            Vector3f point = HookPose.point(g.poseRotation(1.0f));
            Vector3f dir = new Vector3f((float) vel.x, (float) vel.y, (float) vel.z).normalize();
            Vector3f prev = before[0];
            before[0] = dir;
            // the angles are set from the velocity at the start of the hook's tick, before its drag and gravity: the
            // hook points along this tick's velocity or the last one's (the test may run before or after the hook ticks)
            float off = Math.min(point.angle(dir), prev == null ? Float.MAX_VALUE : point.angle(prev));
            h.assertTrue(off < Math.toRadians(2.0), "tick " + g.tickCount + ": points " + v(point) + ", flies " + v(dir)
                    + (prev == null ? "" : ", flew " + v(prev)));
            h.assertTrue(g.pose() == HookPose.Kind.FLYING, "pose " + g.pose());
            sawDown[0] |= dir.y < -0.3f;
            checked[0]++;
        });
        h.succeedWhen(() -> {
            h.assertTrue(checked[0] >= 15 && sawDown[0], "checked " + checked[0] + " flying ticks, coming down: " + sawDown[0] + "; the hook: " + end[0]);
            g.release(GrappleRules.Release.NONE);
        });
    }

    /** Thrown down onto the stone floor, the hook lies flat, its point along the throw and dipped into the floor. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void onTheGroundTheHookLiesFlat(GameTestHelper h) {
        floor(h);
        Player p = thrower(h, new Vec3(1.5, 1, 4.5));
        GrapplingHookEntity g = launch(h, p, new Vec3(3.5, 3.0, 4.5), new Vec3(1.0, -1.0, 0.0));
        h.runAfterDelay(5, () -> {
            assertLatched(h, g, HookPose.Kind.FLAT);
            Vector3f point = HookPose.point(g.latchRotation());
            h.assertTrue(point.x > 0.95f && point.y < 0f && point.y > -0.3f, "point " + v(point));
            h.assertTrue(HookPose.eye(g.latchRotation()).y > 0f, "the eye is under the floor");
            g.release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }

    /** Thrown at a stone wall, the hook bites into it: point into the face, the shank out toward the thrower. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void inAWallTheHookBites(GameTestHelper h) {
        floor(h);
        for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(7, y, 4), Blocks.STONE);
        Player p = thrower(h, new Vec3(1.5, 1, 4.5));
        GrapplingHookEntity g = launch(h, p, new Vec3(3.5, 2.5, 4.5), new Vec3(GrappleConfig.THROW_VELOCITY.get(), 0.1, 0.2));
        h.runAfterDelay(5, () -> {
            assertLatched(h, g, HookPose.Kind.BITING);
            Vector3f point = HookPose.point(g.latchRotation());
            h.assertTrue(point.x > 0.999f, "the point not into the west face: " + v(point));
            Vector3f eye = HookPose.eye(g.latchRotation());
            h.assertTrue(eye.x < -0.99f * HookPose.EYE_FROM_CROWN, "the eye not out of the wall: " + v(eye));
            g.release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }

    /** Thrown at a railing's rail, the hook hangs from it: flukes up over the rail, the shank and eye below. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void onARailingTheHookHangs(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        BlockPos rail = h.absolutePos(new BlockPos(6, 1, 4));
        BlockState s = RailingContent.RAILINGS.get(ShipWood.OAK).defaultBlockState().setValue(RailingBlock.WEST, true);
        level.setBlock(rail, RailingBlock.withPosts(s, level, rail), 3);
        Player p = thrower(h, new Vec3(1.5, 1, 4.5));
        GrapplingHookEntity g = launch(h, p, new Vec3(4.05, 1.5, 4.5), new Vec3(GrappleConfig.THROW_VELOCITY.get(), 0, 0));
        h.runAfterDelay(5, () -> {
            assertLatched(h, g, HookPose.Kind.HANGING);
            h.assertTrue(rail.equals(g.latchedBlock()), "latched on " + g.latchedBlock() + ", not the railing " + rail);
            Vector3f point = HookPose.point(g.latchRotation());
            h.assertTrue(point.y > 0.999f, "the flukes not up over the rail: " + v(point));
            h.assertTrue(HookPose.eye(g.latchRotation()).y < -0.99f * HookPose.EYE_FROM_CROWN, "the eye not under the rail");
            g.release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }

    /** Slipping off leaves the hook falls free, point down. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void aHookThatSlipsOffFallsPointDown(GameTestHelper h) {
        floor(h);
        for (int y = 1; y <= 3; y++) {
            h.setBlock(new BlockPos(7, y, 4), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        }
        Player p = thrower(h, new Vec3(1.5, 1, 4.5));
        GrapplingHookEntity g = launch(h, p, new Vec3(3.5, 2.5, 4.5), new Vec3(GrappleConfig.THROW_VELOCITY.get(), 0, 0));
        h.runAfterDelay(5, () -> {
            h.assertTrue(g.state() == GrapplingHookEntity.State.RETRACTING && g.pose() == HookPose.Kind.FALLING, "pose " + g.pose());
            Vector3f point = HookPose.point(g.poseRotation(1.0f));
            h.assertTrue(point.y < -0.999f, "not point down: " + v(point));
            g.release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }

    /**
     * On a ship's deck the hook lies flat in the plot's frame: turned by the ship's rotation, its point runs along the
     * throw in the world and dips into the deck.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120)
    public static void onAShipsDeckTheHookLiesFlatInThePlotsFrame(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        DryHullGameTests.Fixture b = DryHullGameTests.assemble(h, DryHullGameTests.hull(h, 16, false));
        ServerLevel level = h.getLevel();
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> {
            BlockPos deck = b.helmPlot().offset(1, -1, 0);
            h.assertTrue(level.getBlockState(deck).is(Blocks.OAK_PLANKS), "no deck plank at " + deck);
            Vec3 target = b.ship().toWorld(new Vec3(deck.getX() + 0.5, deck.getY() + 1.0, deck.getZ() + 0.5));
            Vec3 from = target.add(-0.6, 2.5, 0.0);
            Player p = h.makeMockPlayer(GameType.SURVIVAL);
            p.getInventory().clearContent();
            p.setPos(target.x - 6.0, target.y, target.z);
            hook[0] = GrappleService.launch(level, p, from, target.subtract(from).normalize().scale(GrappleConfig.THROW_VELOCITY.get()),
                    new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
        });
        h.runAfterDelay(24, () -> {
            GrapplingHookEntity g = hook[0];
            assertLatched(h, g, HookPose.Kind.FLAT);
            h.assertTrue(b.ship().id().equals(g.shipId()), "not latched on the ship");
            Quaterniond ship = b.ship().orientation();
            Quaternionf world = new Quaternionf((float) ship.x, (float) ship.y, (float) ship.z, (float) ship.w).mul(g.latchRotation());
            Vector3f point = HookPose.point(world);
            Vector3f deckUp = new Quaternionf((float) ship.x, (float) ship.y, (float) ship.z, (float) ship.w).transform(new Vector3f(0f, 1f, 0f));
            h.assertTrue(point.x > 0.9f, "the point not along the throw in the world: " + v(point));
            h.assertTrue(point.dot(deckUp) < 0f && point.dot(deckUp) > -0.3f, "the point does not dip into the deck: " + v(point));
            g.release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }
}

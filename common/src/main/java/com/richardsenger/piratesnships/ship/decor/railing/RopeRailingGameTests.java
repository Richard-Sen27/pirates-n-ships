package com.richardsenger.piratesnships.ship.decor.railing;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.combat.grapple.GrappleConfig;
import com.richardsenger.piratesnships.combat.grapple.GrappleContent;
import com.richardsenger.piratesnships.combat.grapple.GrappleRules;
import com.richardsenger.piratesnships.combat.grapple.GrappleService;
import com.richardsenger.piratesnships.combat.grapple.GrapplingHookEntity;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.rigging.MastHoopBlock;
import com.richardsenger.piratesnships.ship.rigging.RailingLatchBlock;
import com.richardsenger.piratesnships.ship.rigging.RailingLatches;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetItem;
import com.richardsenger.piratesnships.ship.rigging.RiggingContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * The rope railing (RAIL6, docs/design.md §4.8 "Railing rework (RAIL6)") in a real server: placed at the edge like a
 * railing, its posts at the run's ends and corners, a run that ends in a post against a wooden railing (and the
 * railing in one against it), waterlogged, one drop per edge, the railing's collision; RL5's latch on its top line
 * with a net strung from it (and gone with the rope railing); the grappling hook holding on it like on a railing.
 */
public final class RopeRailingGameTests {

    private RopeRailingGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(RopeRailingGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    private static RopeRailingBlock rope() {
        return RailingContent.ROPE_RAILINGS.get(ShipWood.OAK);
    }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static Player holder(GameTestHelper h, Item item, int count, Direction look) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = h.absoluteVec(new Vec3(0.5, 1, 0.5));
        p.moveTo(at.x, at.y, at.z, look.toYRot(), 30f);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, count));
        return p;
    }

    /** Clicks the top of the floor under {@code cell} at {@code (fx, fz)} with the held item. */
    private static boolean clickFloor(GameTestHelper h, Player p, BlockPos cell, double fx, double fz) {
        BlockPos abs = h.absolutePos(cell.below());
        Vec3 hit = new Vec3(abs.getX() + fx, abs.getY() + 1.0, abs.getZ() + fz);
        return p.getMainHandItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, abs, false))).consumesAction();
    }

    private static Set<Direction> set(Direction... ds) {
        return ds.length == 0 ? EnumSet.noneOf(Direction.class) : EnumSet.of(ds[0], ds);
    }

    private static Set<Direction> posts(GameTestHelper h, BlockPos rel) {
        return RailingBlock.posts(h.getBlockState(rel));
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    private static int items(GameTestHelper h, Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds(), e -> e.isAlive() && e.getItem().is(item))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    // ------------------------------------------------------------------ placement, posts, drops

    /**
     * Two clicks lay a run of two along the north edge (posts only at its ends); a second edge in the east block makes a
     * corner; an oak railing beyond the run's west end does not continue it: both end in a post. The collision is the
     * railing's thin wall raised to 1.5 blocks.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aRopeRailingRunsTurnsAndEndsAgainstARailing(GameTestHelper h) {
        floor(h);
        Player p = holder(h, rope().asItem(), 8, Direction.NORTH);
        BlockPos a = new BlockPos(3, 1, 4), b = new BlockPos(4, 1, 4);
        h.assertTrue(clickFloor(h, p, a, 0.5, 0.1), "the rope railing was not placed");
        h.assertTrue(clickFloor(h, p, b, 0.5, 0.1), "the second rope railing was not placed");
        h.assertTrue(h.getBlockState(a).is(rope()), "placed " + h.getBlockState(a));
        h.assertTrue(posts(h, a).equals(set(Direction.NORTH)) && posts(h, b).equals(set(Direction.EAST)),
                "the run's posts: " + posts(h, a) + " / " + posts(h, b));
        h.assertTrue(clickFloor(h, p, b, 0.9, 0.5), "the second edge was refused");
        h.assertTrue(RailingBlock.edges(h.getBlockState(b)).equals(set(Direction.NORTH, Direction.EAST)), "b's edges");
        h.assertTrue(posts(h, b).equals(set(Direction.EAST, Direction.SOUTH)), "b: the corner and the east edge's end, got " + posts(h, b));
        // an oak railing west of the run, on the same edge: neither continues into the other
        Player q = holder(h, RailingContent.RAILINGS.get(ShipWood.OAK).asItem(), 1, Direction.NORTH);
        BlockPos w = new BlockPos(2, 1, 4);
        h.assertTrue(clickFloor(h, q, w, 0.5, 0.1), "the oak railing was not placed");
        h.assertTrue(posts(h, a).equals(set(Direction.NORTH)), "the rope run went on into the railing: " + posts(h, a));
        h.assertTrue(posts(h, w).equals(set(Direction.NORTH, Direction.EAST)), "the railing went on into the rope: " + posts(h, w));
        h.assertTrue(RailingBlock.kindOf(h.getBlockState(a)) == BulwarkRules.Kind.ROPE, "kind " + RailingBlock.kindOf(h.getBlockState(a)));
        BlockPos abs = h.absolutePos(a);
        double top = h.getBlockState(a).getCollisionShape(h.getLevel(), abs, CollisionContext.empty()).max(Direction.Axis.Y);
        h.assertTrue(Math.abs(top - 1.5) < 1e-6, "collision top " + top);
        h.assertTrue(h.getBlockState(a).is(GrappleContent.GRAPPLE_LATCHES), "not a grapple latch");
        h.succeed();
    }

    /** Placed into water it is waterlogged; broken with three edges it drops three rope railings. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aRopeRailingIsWaterloggedAndDropsOnePerEdge(GameTestHelper h) {
        floor(h);
        BlockPos wet = new BlockPos(2, 1, 2);
        h.setBlock(wet, Blocks.WATER);
        Player p = holder(h, rope().asItem(), 8, Direction.NORTH);
        h.assertTrue(clickFloor(h, p, wet, 0.5, 0.1), "not placed in water");
        h.assertTrue(Waterlogging.isWaterlogged(h.getBlockState(wet)), "not waterlogged");
        BlockPos dry = new BlockPos(5, 1, 5);
        clickFloor(h, p, dry, 0.5, 0.1);
        clickFloor(h, p, dry, 0.9, 0.5);
        clickFloor(h, p, dry, 0.1, 0.5);
        h.assertTrue(RailingBlock.edges(h.getBlockState(dry)).size() == 3, "edges " + RailingBlock.edges(h.getBlockState(dry)));
        h.getLevel().destroyBlock(h.absolutePos(dry), true);
        h.runAfterDelay(2, () -> {
            int n = items(h, rope().asItem());
            h.assertTrue(n == 3, "expected 3 rope railings, got " + n);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ RL5's latch on the top line

    /**
     * The net item on a rope railing's railed edge, then on a mast hoop: the latch is set on the top line (its block over
     * the rope railing) and the net strung from it; breaking the rope railing takes the latch and the net away.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 40)
    public static void aLatchStandsOnTheTopLine(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        for (int y = 1; y <= 6; y++) h.setBlock(new BlockPos(6, y, 4), Blocks.OAK_LOG);
        h.setBlock(new BlockPos(5, 6, 4), RiggingContent.MAST_HOOP.get().defaultBlockState().setValue(MastHoopBlock.FACING, Direction.WEST));
        BlockState s = rope().defaultBlockState().setValue(RailingBlock.WEST, true);
        BlockPos rail = h.absolutePos(new BlockPos(2, 1, 4));
        level.setBlock(rail, RailingBlock.withPosts(s, level, rail), 3);
        h.assertTrue(RailingLatches.refusal(level, rail, Direction.WEST) == RailingLatches.Refusal.NONE,
                "the rope railing refuses a latch: " + RailingLatches.refusal(level, rail, Direction.WEST));
        h.assertTrue(RailingLatches.refusal(level, rail, Direction.EAST) == RailingLatches.Refusal.NO_RAIL, "an unrailed edge took a latch");
        ItemStack stack = new ItemStack(RiggingContent.NET_ITEM.get(), 2);
        String first = key(RatlineNetItem.useOnRailing(level, stack, rail, Direction.WEST, null));
        h.assertTrue(first.equals(RatlineNetItem.KEY_MARKED), "the rope railing was not tied: " + first);
        String second = key(RatlineNetItem.use(level, stack, h.absolutePos(new BlockPos(5, 6, 4)), null));
        h.assertTrue(second.equals(RatlineNetItem.KEY_STRUNG), "not strung: " + second);
        BlockState latch = level.getBlockState(rail.above());
        h.assertTrue(latch.is(RiggingContent.RAILING_LATCH.get()) && latch.getValue(RailingLatchBlock.FACING) == Direction.WEST,
                "no latch on the top line: " + latch);
        h.assertTrue(latch.canSurvive(level, rail.above()), "the latch does not survive on the rope railing");
        level.destroyBlock(rail, false);
        h.assertTrue(level.getBlockState(rail.above()).isAir(), "the latch stayed without its rope railing");
        h.succeed();
    }

    // ------------------------------------------------------------------ the grappling hook

    /**
     * Two hulls as in {@code RailingGrappleGameTests}, B with a rope railing run on its west gunwale: a hook thrown at
     * it latches and holds like on a railing (a ring, twice the rope's length).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120)
    public static void aHookHoldsOnARopeRailing(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helmA = DryHullGameTests.hull(h, 2, false);
        BlockPos helmB = DryHullGameTests.hull(h, 16, false);
        for (int z = 10; z <= 12; z++) {
            BlockPos abs = h.absolutePos(new BlockPos(16, 9, z));
            BlockState s = rope().defaultBlockState().setValue(RailingBlock.WEST, true);
            h.getLevel().setBlock(abs, RailingBlock.withPosts(s, h.getLevel(), abs), 3);
        }
        Fixture a = DryHullGameTests.assemble(h, helmA);
        Fixture b = DryHullGameTests.assemble(h, helmB);
        ServerLevel level = h.getLevel();
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().clearContent();
        Vec3 stand = a.ship().toWorld(Vec3.atBottomCenterOf(a.helmPlot().east()));
        player.setPos(stand.x, stand.y, stand.z);
        BlockPos rail = b.helmPlot().offset(-2, 0, 0);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> {
            h.assertTrue(level.getBlockState(rail).getBlock() instanceof RopeRailingBlock, "no rope railing at " + rail);
            Vec3 target = b.ship().toWorld(new Vec3(rail.getX() + 0.05, rail.getY() + 0.5, rail.getZ() + 0.5));
            Vec3 from = target.add(-2.0, 0, 0);
            Vec3 v = target.subtract(from).normalize().scale(GrappleConfig.THROW_VELOCITY.get());
            hook[0] = GrappleService.launch(level, player, from, v, new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
        });
        h.runAfterDelay(24, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook is " + g.state());
            h.assertTrue(rail.equals(g.latchedBlock()), "latched on " + g.latchedBlock());
            h.assertTrue(g.onStrongPoint() && g.onRing(), "the rope railing does not hold like a railing");
            h.assertTrue(Math.abs(g.breakLength() - g.ropeLength() * GrappleConfig.RING_HOLD_MULTIPLIER.get()) < 1e-9,
                    "break length " + g.breakLength());
            g.release(GrappleRules.Release.NONE);
            h.succeed();
        });
    }
}

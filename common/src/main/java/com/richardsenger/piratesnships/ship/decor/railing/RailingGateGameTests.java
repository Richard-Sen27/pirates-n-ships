package com.richardsenger.piratesnships.ship.decor.railing;

import com.richardsenger.piratesnships.combat.boarding.BoardingContent;
import com.richardsenger.piratesnships.combat.boarding.BoardingPlanks;
import com.richardsenger.piratesnships.combat.boarding.PlankRun;
import com.richardsenger.piratesnships.combat.grapple.GrappleContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.decor.DecorConfig;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * The railing gate and hanging things from railing posts (RAIL3, docs/design.md §4.8 "Railing extras (RAIL3–RAIL5)")
 * in a real server: a gate placed in a railing run draws no double posts; use and redstone swing it with the fence-gate
 * sounds, its collision and pathing follow; a villager paths through the open gate and not the closed one; a plank laid
 * from the open gate's sill starts beyond the gate, from a closed gate it does not, and a plank in hand never swings
 * the gate; a lantern hangs below and stands on a railing post (and a gate's), a chain runs on from a post, and neither
 * is carried by a plain rail. The grappling hook on a railing is in {@code RailingGrappleGameTests}.
 */
public final class RailingGateGameTests {

    private static final String BATCH = "pirates_n_ships_config_ship_decor_railing_gate_";

    private RailingGateGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(RailingGateGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    private static RailingBlock railing() {
        return RailingContent.RAILINGS.get(ShipWood.OAK);
    }

    private static RailingGateBlock gate() {
        return RailingContent.GATES.get(ShipWood.OAK);
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

    private static InteractionResult use(GameTestHelper h, Player p, BlockPos clicked, Direction face, double fx, double fy, double fz) {
        BlockPos abs = h.absolutePos(clicked);
        Vec3 hit = new Vec3(abs.getX() + fx, abs.getY() + fy, abs.getZ() + fz);
        return p.getMainHandItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, abs, false)));
    }

    private static BlockPos setRailing(GameTestHelper h, BlockPos rel, Direction... edges) {
        BlockState s = railing().defaultBlockState();
        for (Direction d : edges) s = s.setValue(RailingBlock.edge(d), true);
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, RailingBlock.withPosts(s, h.getLevel(), abs), 3);
        return abs;
    }

    private static BlockState gateState(Direction facing, boolean open) {
        return gate().defaultBlockState().setValue(RailingGateBlock.FACING, facing).setValue(RailingGateBlock.OPEN, open);
    }

    private static Set<Direction> set(Direction... ds) {
        return ds.length == 0 ? EnumSet.noneOf(Direction.class) : EnumSet.of(ds[0], ds);
    }

    private static boolean open(GameTestHelper h, BlockPos rel) {
        return h.getBlockState(rel).getValue(RailingGateBlock.OPEN);
    }

    /** Whether the box {@code local} (block coordinates, 0..1 inside {@code rel}) hits the block's collision. */
    private static boolean collides(GameTestHelper h, BlockPos rel, AABB local) {
        BlockPos abs = h.absolutePos(rel);
        VoxelShape shape = h.getLevel().getBlockState(abs).getCollisionShape(h.getLevel(), abs, CollisionContext.empty());
        return shape.toAabbs().stream().anyMatch(b -> b.intersects(local));
    }

    // ------------------------------------------------------------------ the run

    /**
     * Railings on the north edge at x 2 and x 4 and a gate placed between them with the item (a click near the north
     * side, in the west half): the gate faces north, hangs on its left (west) post, and the railings either side keep
     * only their outer end posts.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aGateInARunDrawsNoDoublePosts(GameTestHelper h) {
        floor(h);
        setRailing(h, new BlockPos(2, 1, 4), Direction.NORTH);
        setRailing(h, new BlockPos(4, 1, 4), Direction.NORTH);
        Player p = holder(h, gate().asItem(), 1, Direction.NORTH);
        BlockPos at = new BlockPos(3, 1, 4);
        h.assertTrue(use(h, p, at.below(), Direction.UP, 0.3, 1.0, 0.1).consumesAction(), "the gate was not placed");
        BlockState g = h.getBlockState(at);
        h.assertTrue(g.getBlock() instanceof RailingGateBlock && g.getValue(RailingGateBlock.FACING) == Direction.NORTH
                && g.getValue(RailingGateBlock.HINGE) == DoorHingeSide.LEFT && !g.getValue(RailingGateBlock.OPEN), "gate state " + g);
        h.assertTrue(RailingBlock.posts(h.getBlockState(new BlockPos(2, 1, 4))).equals(set(Direction.NORTH)),
                "west railing posts " + RailingBlock.posts(h.getBlockState(new BlockPos(2, 1, 4))));
        h.assertTrue(RailingBlock.posts(h.getBlockState(new BlockPos(4, 1, 4))).equals(set(Direction.EAST)),
                "east railing posts " + RailingBlock.posts(h.getBlockState(new BlockPos(4, 1, 4))));
        // opened, the run still continues into it
        h.useBlock(at, h.makeMockPlayer(GameType.SURVIVAL));
        h.assertTrue(open(h, at), "the gate did not open");
        h.assertTrue(RailingBlock.posts(h.getBlockState(new BlockPos(2, 1, 4))).equals(set(Direction.NORTH)), "an open gate ends the run");
        h.assertTrue(h.getBlockState(at).is(BoardingContent.GANGWAY_OPENINGS) && h.getBlockState(at).is(GrappleContent.GRAPPLE_LATCHES),
                "the gate misses its tags");
        h.succeed();
    }

    /** With railings off the gate places nothing. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "disabled")
    public static void aDisabledGateDoesNotPlace(GameTestHelper h) {
        ConfigOverrides.during(h, DecorConfig.RAILINGS_ENABLED, false);
        floor(h);
        Player p = holder(h, gate().asItem(), 1, Direction.NORTH);
        h.assertFalse(use(h, p, new BlockPos(4, 0, 4), Direction.UP, 0.5, 1.0, 0.1).consumesAction(), "placed while disabled");
        h.succeed();
    }

    // ------------------------------------------------------------------ swinging

    /**
     * Use opens it (the oak fence gate's sounds), use again closes it. Closed, the leaf collides up to 1.5 blocks and
     * mobs do not path; open, only the port's posts and sill collide and mobs path through.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void useSwingsTheGateAndItsCollisionFollows(GameTestHelper h) {
        floor(h);
        BlockPos at = new BlockPos(4, 1, 4);
        h.setBlock(at, gateState(Direction.NORTH, false));
        h.assertTrue(gate().swingSound(true) == SoundEvents.FENCE_GATE_OPEN && gate().swingSound(false) == SoundEvents.FENCE_GATE_CLOSE,
                "not the oak fence gate's sounds");
        AABB leaf = new AABB(0.4, 0.5, 0.0, 0.6, 1.4, 0.1); // the middle of the leaf, above the sill, up to 1.4 blocks
        BlockPos abs = h.absolutePos(at);
        h.assertTrue(collides(h, at, leaf), "a closed gate does not block");
        double top = h.getBlockState(at).getCollisionShape(h.getLevel(), abs, CollisionContext.empty()).max(Direction.Axis.Y);
        h.assertTrue(Math.abs(top - 1.5) < 1e-6, "closed collision top " + top);
        h.assertFalse(h.getBlockState(at).isPathfindable(PathComputationType.LAND), "crew path through a closed gate");
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        h.useBlock(at, p);
        h.assertTrue(open(h, at), "use did not open the gate");
        h.assertFalse(collides(h, at, leaf), "an open gate still blocks the opening");
        h.assertTrue(h.getBlockState(at).isPathfindable(PathComputationType.LAND), "crew cannot path through an open gate");
        h.useBlock(at, p);
        h.assertFalse(open(h, at), "use did not close the gate");
        h.succeed();
    }

    /** A redstone block beside the gate opens it; removing it closes it again. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void redstoneOpensTheGate(GameTestHelper h) {
        floor(h);
        BlockPos at = new BlockPos(4, 1, 4);
        h.setBlock(at, gateState(Direction.NORTH, false));
        h.setBlock(at.east(), Blocks.REDSTONE_BLOCK);
        h.assertTrue(open(h, at) && h.getBlockState(at).getValue(RailingGateBlock.POWERED), "the signal did not open it");
        h.setBlock(at.east(), Blocks.AIR);
        h.assertFalse(open(h, at), "losing the signal did not close it");
        h.succeed();
    }

    /**
     * A railing line across the area (north edges of row z 5) with a gate in its middle: a villager south of it finds a
     * path to a point north of it through the open gate, and none through the closed one.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void crewPathThroughAnOpenGateOnly(GameTestHelper h) {
        floor(h);
        for (int x = 0; x < 9; x++) {
            if (x != 4) setRailing(h, new BlockPos(x, 1, 5), Direction.NORTH);
        }
        BlockPos gateAt = new BlockPos(4, 1, 5);
        h.setBlock(gateAt, gateState(Direction.NORTH, true));
        Villager v = h.spawn(EntityType.VILLAGER, new BlockPos(4, 1, 7));
        BlockPos target = h.absolutePos(new BlockPos(4, 1, 2));
        h.runAfterDelay(10, () -> {
            v.setOnGround(true);
            Path open = v.getNavigation().createPath(target, 0);
            h.assertTrue(open != null && open.canReach(), "no path through the open gate: " + open);
            boolean viaGate = false;
            for (int i = 0; i < open.getNodeCount(); i++) {
                if (open.getNode(i).asBlockPos().equals(h.absolutePos(gateAt))) viaGate = true;
            }
            h.assertTrue(viaGate, "the path does not run through the gate");
            v.getNavigation().stop();
            h.setBlock(gateAt, gateState(Direction.NORTH, false));
            Path shut = v.getNavigation().createPath(target, 0);
            h.assertTrue(shut == null || !shut.canReach(), "a path runs through the closed gate: " + shut);
            v.discard();
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ the gangway

    /**
     * On land: a gate facing east on a stone edge, a stone landing two blocks of air east of it. Open, a plank used on
     * its sill facing east starts in the block beyond the gate at the gate's height and lands level; closed, the gate
     * blocks that start like a railing.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aPlankStartsFromAnOpenGatesSillOnly(GameTestHelper h) {
        for (int x = 0; x <= 3; x++) for (int z = 0; z <= 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        for (int x = 6; x <= 8; x++) for (int z = 0; z <= 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        BlockPos shut = new BlockPos(3, 2, 2);
        BlockPos opened = new BlockPos(3, 2, 6);
        h.setBlock(shut, gateState(Direction.EAST, false));
        h.setBlock(opened, gateState(Direction.EAST, true));
        BoardingPlanks.Laid closedRun = BoardingPlanks.lay(h.getLevel(), h.absolutePos(shut), Direction.UP, Direction.EAST);
        h.assertTrue(closedRun.outcome() != BoardingPlanks.Outcome.OK || !closedRun.cells().get(0).equals(h.absolutePos(new BlockPos(4, 2, 2))),
                "a closed gate let the plank start beyond it at sill height");
        BoardingPlanks.Laid laid = BoardingPlanks.lay(h.getLevel(), h.absolutePos(opened), Direction.UP, Direction.EAST);
        h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.OK, "refused from the open gate: " + laid.outcome());
        h.assertTrue(laid.cells().get(0).equals(h.absolutePos(new BlockPos(4, 2, 6))), "the run starts at " + laid.cells().get(0));
        h.assertTrue(laid.landing() == PlankRun.Landing.LEVEL, "landing " + laid.landing());
        h.succeed();
    }

    /** A plank in hand clicking an open gate does not swing it shut (the plank's own use runs). */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aPlankInHandDoesNotSwingTheGate(GameTestHelper h) {
        floor(h);
        BlockPos at = new BlockPos(4, 1, 4);
        h.setBlock(at, gateState(Direction.NORTH, true));
        Player p = holder(h, BoardingContent.PLANK_ITEM.get(), 1, Direction.NORTH);
        BlockPos abs = h.absolutePos(at);
        ItemInteractionResult r = h.getBlockState(at).useItemOn(p.getMainHandItem(), h.getLevel(), p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
        h.assertTrue(r == ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION, "the gate takes the plank's click: " + r);
        h.assertTrue(open(h, at), "the gate swung");
        h.succeed();
    }

    // ------------------------------------------------------------------ hanging from a post

    /**
     * A lone railing edge hung in the air (an overhang) has two end posts: a lantern placed against its bottom hangs
     * below it, a lantern stands on its top, a chain runs on below it and carries a lantern; breaking the railing drops
     * the hanging lantern. A plain rail (the middle of a three-block run, no post) carries neither, and a gate's posts do.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void lanternsAndChainsHangFromPostsOnly(GameTestHelper h) {
        BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        BlockState standing = Blocks.LANTERN.defaultBlockState();
        // a post: a lone edge at y 4
        BlockPos post = new BlockPos(2, 4, 2);
        BlockPos postAbs = setRailing(h, post, Direction.NORTH);
        h.assertFalse(RailingBlock.posts(h.getBlockState(post)).isEmpty(), "the lone edge has no post");
        Player p = holder(h, Items.LANTERN, 2, Direction.NORTH);
        p.setXRot(-60f); // looking up: the lantern hangs
        h.assertTrue(use(h, p, post, Direction.DOWN, 0.5, 0.0, 0.5).consumesAction(), "no lantern placed under the post");
        h.assertTrue(h.getBlockState(post.below()).is(Blocks.LANTERN) && h.getBlockState(post.below()).getValue(LanternBlock.HANGING),
                "not a hanging lantern under the post: " + h.getBlockState(post.below()));
        h.assertTrue(standing.canSurvive(h.getLevel(), postAbs.above()), "a lantern cannot stand on the post");
        h.assertTrue(Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true).canSurvive(h.getLevel(), postAbs.below()),
                "a soul lantern cannot hang from the post");
        h.setBlock(post.above(), Blocks.CHAIN); // a chain above the post's cap
        h.assertTrue(h.getBlockState(post.above()).is(Blocks.CHAIN), "no chain over the post");
        // breaking the railing drops the hanging lantern
        h.getLevel().destroyBlock(postAbs, false);
        h.assertFalse(h.getBlockState(post.below()).is(Blocks.LANTERN), "the lantern hangs from nothing");
        // a chain below a post carries a lantern in turn
        BlockPos post2 = new BlockPos(6, 5, 2);
        setRailing(h, post2, Direction.NORTH);
        h.setBlock(post2.below(), Blocks.CHAIN);
        h.assertTrue(hanging.canSurvive(h.getLevel(), h.absolutePos(post2.below(2))), "a lantern cannot hang from the post's chain");
        // a plain rail: the middle of a run at y 4, no post
        for (int x = 1; x <= 3; x++) setRailing(h, new BlockPos(x, 4, 6), Direction.NORTH);
        BlockPos plain = new BlockPos(2, 4, 6);
        h.assertTrue(RailingBlock.posts(h.getBlockState(plain)).isEmpty(), "the middle of the run has a post");
        h.assertFalse(hanging.canSurvive(h.getLevel(), h.absolutePos(plain.below())), "a lantern hangs under a plain rail");
        h.assertFalse(standing.canSurvive(h.getLevel(), h.absolutePos(plain.above())), "a lantern stands on a plain rail");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LANTERN));
        h.assertFalse(use(h, p, plain, Direction.DOWN, 0.5, 0.0, 0.5).consumesAction(), "a lantern was placed under a plain rail");
        // a gate's posts carry one too
        BlockPos gateAt = new BlockPos(6, 4, 6);
        h.setBlock(gateAt, gateState(Direction.NORTH, false));
        h.assertTrue(hanging.canSurvive(h.getLevel(), h.absolutePos(gateAt.below())), "a lantern cannot hang under a gate");
        h.succeed();
    }
}

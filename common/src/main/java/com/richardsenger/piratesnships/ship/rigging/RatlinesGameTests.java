package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.rigging.RatlinesRules.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * Ratlines (RL1, docs/design.md §4.8 "Visual backlog 2", item 1) in a real server: placing with the item against a
 * mast of logs or fences and sloped from a deck, extending a run, support loss, waterlogging, the toggle, and a mock
 * player climbing a hung run and a sloped run on an assembled hull afloat in a basin (Sable's {@code climbing_sub_levels}
 * mixin finds the climbable block in the ship's plot).
 */
public final class RatlinesGameTests {

    private static final String BATCH = "pirates_n_ships_config_rigging_";
    /** Ticks after assembly before a player boards: Sable has the ship's bounds and the bobbing has died down. */
    private static final int SETTLE = 40;

    private RatlinesGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(RatlinesGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    private static BlockState ratlines(Kind kind, Direction facing) {
        return RiggingContent.RATLINES.get().defaultBlockState().setValue(RatlinesBlock.KIND, kind)
                .setValue(RatlinesBlock.FACING, facing);
    }

    /** A survival mock player (not in the level) holding {@code count} ratlines, looking {@code look}, slightly down. */
    private static Player holder(GameTestHelper h, Direction look, int count) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = h.absoluteVec(new Vec3(0.5, 1, 0.5));
        p.moveTo(at.x, at.y, at.z, look.toYRot(), 30f);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RiggingContent.RATLINES_ITEM.get(), count));
        return p;
    }

    /** Uses the held ratlines on {@code face} of the block at relative {@code pos}. */
    private static InteractionResult use(GameTestHelper h, Player p, BlockPos pos, Direction face) {
        BlockPos abs = h.absolutePos(pos);
        Vec3 hit = Vec3.atCenterOf(abs).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        return p.getMainHandItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, abs, false)));
    }

    private static void floor(GameTestHelper h, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static int droppedRatlines(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds(),
                e -> e.isAlive() && e.getItem().is(RiggingContent.RATLINES_ITEM.get()))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    /** A survival mock player in the level at the world position, discarded when the test ends. */
    private static Player playerInLevel(GameTestHelper h, Vec3 world, float yRot) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
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

    // ------------------------------------------------------------------ placement

    /** Clicking the west side of a log mast hangs the net facing west; the item is used up. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void hangsOnTheSideOfALogMast(GameTestHelper h) {
        floor(h, 9);
        for (int y = 1; y <= 4; y++) {
            h.setBlock(new BlockPos(4, y, 4), Blocks.OAK_LOG);
        }
        Player p = holder(h, Direction.EAST, 2);
        InteractionResult r = use(h, p, new BlockPos(4, 3, 4), Direction.WEST);
        h.assertTrue(r.consumesAction(), "placing did nothing: " + r);
        h.assertBlockState(new BlockPos(3, 3, 4), s -> s.equals(ratlines(Kind.WALL, Direction.WEST)), () -> "no hung net west of the mast");
        h.assertTrue(p.getMainHandItem().getCount() == 1, "the item was not used up");
        h.assertTrue(h.getBlockState(new BlockPos(3, 3, 4)).is(BlockTags.CLIMBABLE), "ratlines are not climbable");
        h.succeed();
    }

    /** A mast of fence posts carries a net on its side too (fences are in the anchor tag). */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void hangsOnTheSideOfAFenceMast(GameTestHelper h) {
        floor(h, 9);
        for (int y = 1; y <= 4; y++) {
            h.setBlock(new BlockPos(4, y, 4), Blocks.SPRUCE_FENCE);
        }
        Player p = holder(h, Direction.NORTH, 1);
        h.assertTrue(use(h, p, new BlockPos(4, 3, 4), Direction.SOUTH).consumesAction(), "placing on a fence did nothing");
        h.assertBlockState(new BlockPos(4, 3, 5), s -> s.equals(ratlines(Kind.WALL, Direction.SOUTH)), () -> "no hung net south of the fence");
        h.succeed();
    }

    /**
     * Clicking the top of the deck while looking east lays a sloped net rising east; clicking that net extends the run
     * one up and one east; clicking again extends it at the free end.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void laysASlopedRunFromTheDeck(GameTestHelper h) {
        floor(h, 9);
        for (int x = 0; x < 9; x++) {
            h.setBlock(new BlockPos(x, 1, 4), Blocks.OAK_PLANKS);
        }
        Player p = holder(h, Direction.EAST, 8);
        h.assertTrue(use(h, p, new BlockPos(1, 1, 4), Direction.UP).consumesAction(), "placing on the deck did nothing");
        h.assertBlockState(new BlockPos(1, 2, 4), s -> s.equals(ratlines(Kind.SLOPE, Direction.EAST)), () -> "no sloped net on the deck");
        // a click anywhere on the run extends it at its end
        h.assertTrue(use(h, p, new BlockPos(1, 2, 4), Direction.UP).consumesAction(), "extending did nothing");
        h.assertBlockState(new BlockPos(2, 3, 4), s -> s.equals(ratlines(Kind.SLOPE, Direction.EAST)), () -> "the run was not extended");
        h.assertTrue(use(h, p, new BlockPos(1, 2, 4), Direction.WEST).consumesAction(), "extending again did nothing");
        h.assertBlockState(new BlockPos(3, 4, 4), s -> s.equals(ratlines(Kind.SLOPE, Direction.EAST)), () -> "the run was not extended at its end");
        h.assertTrue(p.getMainHandItem().getCount() == 5, "three placed, " + p.getMainHandItem().getCount() + " left");
        // the treads are a stair: a tread top 2 px over the block's bottom on the low (west) side
        BlockPos low = h.absolutePos(new BlockPos(1, 2, 4));
        double top = h.getLevel().getBlockState(low).getCollisionShape(h.getLevel(), low).max(Direction.Axis.Y);
        h.assertTrue(top > 0.8 && top < 0.9, "the top tread should be at 14 px, is " + top * 16);
        h.succeed();
    }

    /** A hung net on a log extends straight up along the mast; the end of a mast stops it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void extendsAHungRunUpTheMast(GameTestHelper h) {
        floor(h, 9);
        for (int y = 1; y <= 3; y++) {
            h.setBlock(new BlockPos(4, y, 4), Blocks.OAK_LOG);
        }
        Player p = holder(h, Direction.EAST, 8);
        use(h, p, new BlockPos(4, 1, 4), Direction.WEST);
        use(h, p, new BlockPos(3, 1, 4), Direction.WEST);
        use(h, p, new BlockPos(3, 1, 4), Direction.WEST);
        for (int y = 1; y <= 3; y++) {
            h.assertBlockState(new BlockPos(3, y, 4), s -> s.equals(ratlines(Kind.WALL, Direction.WEST)), () -> "the run has a gap");
        }
        InteractionResult r = use(h, p, new BlockPos(3, 1, 4), Direction.WEST);
        h.assertTrue(!h.getBlockState(new BlockPos(3, 4, 4)).is(RiggingContent.RATLINES.get()),
                "a net was hung above the mast's top: " + r);
        h.succeed();
    }

    /** Placed into a water source, the net is waterlogged; breaking it leaves the water. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void placedInWaterIsWaterlogged(GameTestHelper h) {
        floor(h, 9);
        for (int y = 1; y <= 3; y++) {
            h.setBlock(new BlockPos(4, y, 4), Blocks.OAK_LOG);
            h.setBlock(new BlockPos(3, y, 4), Blocks.WATER);
            h.setBlock(new BlockPos(3, y, 3), Blocks.STONE);
            h.setBlock(new BlockPos(3, y, 5), Blocks.STONE);
            h.setBlock(new BlockPos(2, y, 4), Blocks.STONE);
        }
        Player p = holder(h, Direction.EAST, 1);
        h.assertTrue(use(h, p, new BlockPos(4, 2, 4), Direction.WEST).consumesAction(), "placing in water did nothing");
        BlockState s = h.getBlockState(new BlockPos(3, 2, 4));
        h.assertTrue(s.is(RiggingContent.RATLINES.get()) && s.getValue(RatlinesBlock.WATERLOGGED), "not waterlogged: " + s);
        h.assertTrue(s.getFluidState().is(Fluids.WATER), "no water in the net");
        h.getLevel().destroyBlock(h.absolutePos(new BlockPos(3, 2, 4)), true);
        h.assertTrue(h.getBlockState(new BlockPos(3, 2, 4)).getFluidState().is(Fluids.WATER), "the water went with the net");
        h.succeed();
    }

    /** Clicking the side of iron bars in mid-air: nothing can carry a net there (not sturdy, no anchor, no floor). */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void nothingToHoldItPlacesNothing(GameTestHelper h) {
        floor(h, 9);
        h.setBlock(new BlockPos(4, 4, 4), Blocks.IRON_BARS);
        Player p = holder(h, Direction.EAST, 1);
        InteractionResult r = use(h, p, new BlockPos(4, 4, 4), Direction.WEST);
        h.assertTrue(!h.getBlockState(new BlockPos(3, 4, 4)).is(RiggingContent.RATLINES.get()), "placed without support: " + r);
        h.assertTrue(p.getMainHandItem().getCount() == 1, "the item was used up");
        h.succeed();
    }

    // ------------------------------------------------------------------ support loss

    /** Breaking the log a net hangs on drops the net. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aHungNetFallsWithItsMast(GameTestHelper h) {
        floor(h, 9);
        h.setBlock(new BlockPos(4, 2, 4), Blocks.OAK_LOG);
        h.setBlock(new BlockPos(3, 2, 4), ratlines(Kind.WALL, Direction.WEST));
        h.getLevel().destroyBlock(h.absolutePos(new BlockPos(4, 2, 4)), false);
        h.assertTrue(h.getBlockState(new BlockPos(3, 2, 4)).isAir(), "the net stayed without its mast");
        h.runAfterDelay(2, () -> {
            h.assertTrue(droppedRatlines(h) == 1, "expected one dropped net, found " + droppedRatlines(h));
            h.succeed();
        });
    }

    /**
     * A sloped run of three links on a deck block: taking the deck block away drops the first link, then the links above
     * it one after the other (each stood only on the one below). A second run whose top leans on a mast keeps its top.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void aSlopedRunFallsLinkByLinkUnlessItLeansOnTheMast(GameTestHelper h) {
        floor(h, 9);
        // run A along z = 2, standing on one plank at (1, 1, 2)
        h.setBlock(new BlockPos(1, 1, 2), Blocks.OAK_PLANKS);
        for (int i = 0; i < 3; i++) {
            h.setBlock(new BlockPos(1 + i, 2 + i, 2), ratlines(Kind.SLOPE, Direction.EAST));
        }
        // run B along z = 6, the same, plus a log mast east of its top link at (4, 4, 6)
        h.setBlock(new BlockPos(1, 1, 6), Blocks.OAK_PLANKS);
        for (int y = 1; y <= 5; y++) {
            h.setBlock(new BlockPos(4, y, 6), Blocks.OAK_LOG);
        }
        for (int i = 0; i < 3; i++) {
            h.setBlock(new BlockPos(1 + i, 2 + i, 6), ratlines(Kind.SLOPE, Direction.EAST));
        }
        h.getLevel().destroyBlock(h.absolutePos(new BlockPos(1, 1, 2)), false);
        h.getLevel().destroyBlock(h.absolutePos(new BlockPos(1, 1, 6)), false);
        h.runAfterDelay(10, () -> {
            for (int i = 0; i < 3; i++) {
                h.assertTrue(!h.getBlockState(new BlockPos(1 + i, 2 + i, 2)).is(RiggingContent.RATLINES.get()), "run A link " + i + " stayed");
            }
            h.assertTrue(!h.getBlockState(new BlockPos(1, 2, 6)).is(RiggingContent.RATLINES.get()), "run B's first link stayed");
            h.assertTrue(!h.getBlockState(new BlockPos(2, 3, 6)).is(RiggingContent.RATLINES.get()), "run B's second link stayed");
            h.assertTrue(h.getBlockState(new BlockPos(3, 4, 6)).is(RiggingContent.RATLINES.get()), "run B's top link fell although it leans on the mast");
            h.assertTrue(droppedRatlines(h) == 5, "expected five dropped nets, found " + droppedRatlines(h));
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ climbing

    /**
     * At tick {@code start} spawns a player with {@code spawn}, then each tick holds jump (or walks forward) and tracks
     * the highest feet height; {@code forTicks} later it must have risen {@code rise} blocks. Everything is scheduled
     * up front: scheduling from inside a scheduled task changes the test's task map while it is iterated (a crash).
     */
    private static void climb(GameTestHelper h, int start, Supplier<Player> spawn, boolean jump, double rise,
                              int forTicks, String what) {
        Player[] player = {null};
        double[] y0 = {0};
        double[] max = {0};
        int[] tick = {0};
        h.onEachTick(() -> {
            int t = tick[0]++;
            if (t == start) {
                player[0] = spawn.get();
                y0[0] = player[0].getY();
                max[0] = y0[0];
            }
            Player p = player[0];
            if (p == null || p.isRemoved()) {
                return;
            }
            p.setJumping(jump);
            p.zza = jump ? 0f : 1f;
            max[0] = Math.max(max[0], p.getY());
        });
        h.runAfterDelay(start + forTicks, () -> {
            h.assertTrue(player[0] != null, what + ": no player was spawned");
            Constants.LOG.info("[RL1] {}: start y {}, highest {}, now {}", what, y0[0], max[0], player[0].getY());
            h.assertTrue(max[0] - y0[0] >= rise, what + ": rose only " + (max[0] - y0[0]) + " blocks (from " + y0[0] + ")");
            h.succeed();
        });
    }

    /** Control on land: a player holding jump climbs a hung run of three on a log mast. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void playerClimbsAHungRunOnLand(GameTestHelper h) {
        floor(h, 9);
        for (int y = 1; y <= 4; y++) {
            h.setBlock(new BlockPos(4, y, 4), Blocks.OAK_LOG);
            h.setBlock(new BlockPos(3, y, 4), ratlines(Kind.WALL, Direction.WEST));
        }
        climb(h, 0, () -> playerInLevel(h, h.absoluteVec(new Vec3(3.4, 1.0, 4.5)), Direction.EAST.toYRot()),
                true, 2.0, 30, "hung run on land");
    }

    /**
     * On an assembled hull afloat in a basin: a log mast of three west of the helm with a hung run on its west side; a
     * player standing in the bottom net and holding jump climbs it (Sable's climbing mixin reads the plot's block).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void playerClimbsAHungRunOnAShip(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        for (int y = 9; y <= 11; y++) {
            h.setBlock(new BlockPos(10, y, 11), Blocks.OAK_LOG);
            h.setBlock(new BlockPos(9, y, 11), ratlines(Kind.WALL, Direction.WEST));
        }
        Fixture f = DryHullGameTests.assemble(h, helm);
        ServerLevel level = h.getLevel();
        BlockPos bottom = f.helmPlot().offset(-2, 0, 0);
        h.assertTrue(level.getBlockState(bottom).equals(ratlines(Kind.WALL, Direction.WEST)),
                "the net did not come along into the plot: " + level.getBlockState(bottom));
        climb(h, SETTLE, () -> {
            for (int dy = 0; dy < 3; dy++) {
                h.assertTrue(level.getBlockState(bottom.above(dy)).is(RiggingContent.RATLINES.get()), "net " + dy + " fell off the ship");
            }
            Vec3 at = f.ship().toWorld(new Vec3(bottom.getX() + 0.4, bottom.getY() + 0.05, bottom.getZ() + 0.5));
            return playerInLevel(h, at, Direction.EAST.toYRot());
        }, true, 1.5, 40, "hung run on a ship");
    }

    /**
     * On an assembled hull afloat in a basin: a sloped run of three from the deck rising east towards a log mast on the
     * east gunwale; a player on the deck west of the run walking east (no jump) goes up the treads like a stair.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void playerClimbsASlopedRunOnAShip(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        for (int y = 9; y <= 11; y++) {
            h.setBlock(new BlockPos(13, y, 10), Blocks.OAK_LOG);
        }
        for (int i = 0; i < 3; i++) {
            h.setBlock(new BlockPos(10 + i, 9 + i, 10), ratlines(Kind.SLOPE, Direction.EAST));
        }
        Fixture f = DryHullGameTests.assemble(h, helm);
        ServerLevel level = h.getLevel();
        BlockPos low = f.helmPlot().offset(-1, 0, -1);
        h.assertTrue(level.getBlockState(low).equals(ratlines(Kind.SLOPE, Direction.EAST)),
                "the run did not come along into the plot: " + level.getBlockState(low));
        climb(h, SETTLE, () -> {
            for (int i = 0; i < 3; i++) {
                h.assertTrue(level.getBlockState(low.offset(i, i, 0)).is(RiggingContent.RATLINES.get()), "link " + i + " fell off the ship");
            }
            // on the deck one block west of the first link
            Vec3 at = f.ship().toWorld(new Vec3(low.getX() - 0.5, low.getY() + 0.02, low.getZ() + 0.5));
            return playerInLevel(h, at, Direction.EAST.toYRot());
        }, false, 1.5, 60, "sloped run on a ship");
    }

    // ------------------------------------------------------------------ toggle

    /** {@code rigging.ratlines_enabled = false}: the item places nothing and is not used up. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "disabled")
    public static void disabledRatlinesDoNotPlace(GameTestHelper h) {
        ConfigOverrides.during(h, RiggingConfig.RATLINES_ENABLED, false);
        floor(h, 9);
        h.setBlock(new BlockPos(4, 2, 4), Blocks.OAK_LOG);
        Player p = holder(h, Direction.EAST, 1);
        InteractionResult r = use(h, p, new BlockPos(4, 2, 4), Direction.WEST);
        h.assertTrue(!r.consumesAction(), "placing succeeded while disabled: " + r);
        h.assertTrue(h.getBlockState(new BlockPos(3, 2, 4)).isAir(), "a net was placed while disabled");
        h.assertTrue(p.getMainHandItem().getCount() == 1, "the item was used up while disabled");
        h.succeed();
    }
}

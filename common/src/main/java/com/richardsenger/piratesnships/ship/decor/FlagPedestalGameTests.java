package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.decor.flag.FlagConfig;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleBlockEntity;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFoot;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFooting;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpolePart;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleRun;
import com.richardsenger.piratesnships.ship.decor.flag.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/**
 * The flag pedestal (FLG3, docs/design.md §4.7 "Flag pedestal and the pole on the crow's nest"): it stands on a sturdy
 * top and pops off without one, a flagpole placed on it makes it the pole's foot (the socketed model with the cleat,
 * the lowest pole block bare), using it works the pole, breaking it brings the pole down, and it is waterloggable.
 * Batch {@code ..._flags_stacked} pins stacking on, a limit of 4 and a 20-tick delay like {@code FlagStackGameTests}.
 */
public final class FlagPedestalGameTests {

    private static final String BATCH = "pirates_n_ships_config_flags_stacked";

    private FlagPedestalGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(FlagPedestalGameTests.class);
    }

    private static void stackedConfig(GameTestHelper h) {
        ConfigOverrides.during(h, FlagConfig.STACKED_POLES, true);
        ConfigOverrides.during(h, FlagConfig.MAX_POLE_HEIGHT, 4);
        ConfigOverrides.during(h, FlagConfig.HOIST_DELAY_TICKS, 20);
    }

    /** A player places {@code block}'s item on the top face of the block at {@code onto}, as vanilla does. */
    static InteractionResult placeOnTop(GameTestHelper h, Player player, Block block, BlockPos onto) {
        ItemStack held = new ItemStack(block);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos abs = h.absolutePos(onto);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
        return held.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static FlagpoleFoot.Pole pole(GameTestHelper h, BlockPos rel) {
        return FlagpoleRun.pole(h.getLevel(), h.absolutePos(rel));
    }

    /**
     * A pedestal stands on a stone and a plank top, not on a bottom slab, a fence post or in mid-air, and pops off
     * (dropping itself) when its stone goes.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void pedestalNeedsASturdyTop(GameTestHelper h) {
        BlockState pedestal = ShipDecor.FLAG_PEDESTAL.get().defaultBlockState();
        h.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        h.setBlock(new BlockPos(3, 1, 1), Blocks.OAK_PLANKS);
        h.setBlock(new BlockPos(5, 1, 1), Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
        h.setBlock(new BlockPos(7, 1, 1), Blocks.OAK_FENCE);
        h.assertTrue(pedestal.canSurvive(h.getLevel(), h.absolutePos(new BlockPos(1, 2, 1))), "no pedestal on stone");
        h.assertTrue(pedestal.canSurvive(h.getLevel(), h.absolutePos(new BlockPos(3, 2, 1))), "no pedestal on planks");
        h.assertFalse(pedestal.canSurvive(h.getLevel(), h.absolutePos(new BlockPos(5, 2, 1))), "a pedestal on a bottom slab");
        h.assertFalse(pedestal.canSurvive(h.getLevel(), h.absolutePos(new BlockPos(7, 2, 1))), "a pedestal on a fence post");
        h.assertFalse(pedestal.canSurvive(h.getLevel(), h.absolutePos(new BlockPos(1, 4, 4))), "a pedestal in mid-air");
        h.setBlock(new BlockPos(1, 2, 1), pedestal);
        h.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
        h.runAtTickTime(2, () -> {
            h.assertBlockNotPresent(ShipDecor.FLAG_PEDESTAL.get(), new BlockPos(1, 2, 1));
            h.assertItemEntityPresent(ShipDecor.FLAG_PEDESTAL.get().asItem(), new BlockPos(1, 2, 1), 1.5);
            h.succeed();
        });
    }

    /**
     * A flagpole placed on a pedestal stands in its socket: the pedestal shows the pole and the cleat, the pole block
     * shows the part of a block standing on a pole (top: no cleat), and the pole is one block high with the cleat at
     * the pedestal. A second pole block makes the lowest one a bare middle; height and head count pole blocks only.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void poleOnAPedestalHasItsFootThere(GameTestHelper h) {
        stackedConfig(h);
        Player player = h.makeMockPlayer(GameType.CREATIVE);
        BlockPos base = new BlockPos(2, 1, 2), pedestal = base.above(), low = pedestal.above(), high = low.above();
        h.setBlock(base, Blocks.STONE);
        h.assertTrue(placeOnTop(h, player, ShipDecor.FLAG_PEDESTAL.get(), base).consumesAction(), "the pedestal was not placed");
        h.assertBlockProperty(pedestal, FlagpoleFooting.POLE, false);
        h.assertTrue(placeOnTop(h, player, ShipDecor.FLAGPOLE.get(), pedestal).consumesAction(), "the pole was not placed on the pedestal");
        h.assertBlockProperty(pedestal, FlagpoleFooting.POLE, true);
        h.assertBlockProperty(low, FlagpoleBlock.PART, FlagpolePart.TOP);
        FlagpoleFoot.Pole one = pole(h, low);
        h.assertTrue(one.height() == 1 && one.foot() == 0 && one.head() == 0 && one.cleat() == -1
                && one.base() == FlagpoleFoot.Support.PEDESTAL, "pole on the pedestal: " + one);
        h.assertTrue(FlagpoleRun.isHead(h.getLevel(), h.absolutePos(low)), "the pole block is not its own head");
        h.assertTrue(placeOnTop(h, player, ShipDecor.FLAGPOLE.get(), low).consumesAction(), "the second pole block was not placed");
        h.assertBlockProperty(low, FlagpoleBlock.PART, FlagpolePart.MIDDLE);
        h.assertBlockProperty(high, FlagpoleBlock.PART, FlagpolePart.TOP);
        h.assertBlockProperty(pedestal, FlagpoleFooting.POLE, true);
        FlagpoleFoot.Pole two = pole(h, high);
        h.assertTrue(two.height() == 2 && two.foot() == -1 && two.head() == 0 && two.cleat() == -2, "two blocks: " + two);
        h.assertTrue(FlagpoleRun.height(h.getLevel(), h.absolutePos(high)) == 2, "the run counts the pedestal");
        h.assertTrue(FlagpoleRun.head(h.getLevel(), h.absolutePos(low)).equals(h.absolutePos(high)), "head");
        // the same two blocks on plain stone: the lowest carries the cleat
        BlockPos plain = new BlockPos(5, 2, 2);
        h.setBlock(plain.below(), Blocks.STONE);
        h.assertTrue(placeOnTop(h, player, ShipDecor.FLAGPOLE.get(), plain.below()).consumesAction(), "plain pole");
        h.assertTrue(placeOnTop(h, player, ShipDecor.FLAGPOLE.get(), plain).consumesAction(), "plain pole, second block");
        h.assertBlockProperty(plain, FlagpoleBlock.PART, FlagpolePart.BOTTOM);
        h.assertTrue(pole(h, plain).cleat() == 0, "the plain pole's cleat is not on its foot: " + pole(h, plain));
        h.succeed();
    }

    /** Using the pedestal under a pole works the pole at its head: a flag item starts the hoist there. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void pedestalWorksThePole(GameTestHelper h) {
        stackedConfig(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pedestal = new BlockPos(2, 2, 2), head = pedestal.above(2);
        h.setBlock(pedestal.below(), Blocks.STONE);
        h.setBlock(pedestal, ShipDecor.FLAG_PEDESTAL.get());
        h.setBlock(pedestal.above(), ShipDecor.FLAGPOLE.get());
        h.setBlock(head, ShipDecor.FLAGPOLE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Flags.NAVY_FLAG.get()));
        BlockPos abs = h.absolutePos(pedestal);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
        ItemInteractionResult r = h.getLevel().getBlockState(abs).useItemOn(player.getMainHandItem(), h.getLevel(), player,
                InteractionHand.MAIN_HAND, hit);
        h.assertTrue(r.consumesAction(), "the pedestal did not take the flag: " + r);
        h.assertTrue(h.getBlockEntity(head) instanceof FlagpoleBlockEntity be
                && be.state().pending().map(p -> p.kind() == FlagKind.NAVY).orElse(false), "no hoist pending at the head");
        h.assertTrue(player.getMainHandItem().isEmpty(), "the flag was not taken");
        h.succeed();
    }

    /** Breaking the pedestal brings its two-block pole down: pedestal, poles and the flying flag drop as items. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void breakingThePedestalDropsThePole(GameTestHelper h) {
        stackedConfig(h);
        BlockPos pedestal = new BlockPos(2, 2, 2), low = pedestal.above(), head = low.above();
        h.setBlock(pedestal.below(), Blocks.STONE);
        h.setBlock(pedestal, ShipDecor.FLAG_PEDESTAL.get());
        h.setBlock(low, ShipDecor.FLAGPOLE.get());
        h.setBlock(head, ShipDecor.FLAGPOLE.get());
        if (!(h.getBlockEntity(head) instanceof FlagpoleBlockEntity be)) throw new AssertionError("no pole head");
        be.commandSet(FlagKind.JOLLY_ROGER, false, null);
        h.getLevel().destroyBlock(h.absolutePos(pedestal), true);
        h.runAtTickTime(2, () -> {
            h.assertBlockNotPresent(ShipDecor.FLAGPOLE.get(), low);
            h.assertBlockNotPresent(ShipDecor.FLAGPOLE.get(), head);
            h.assertItemEntityCountIs(ShipDecor.FLAGPOLE.get().asItem(), low, 3.0, 2);
            h.assertItemEntityPresent(ShipDecor.FLAG_PEDESTAL.get().asItem(), pedestal, 2.0);
            h.assertItemEntityPresent(Flags.JOLLY_ROGER_FLAG.get(), head, 2.0);
            h.succeed();
        });
    }

    /** A pedestal losing its support pops off and takes the pole with it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void pedestalWithoutSupportTakesThePole(GameTestHelper h) {
        stackedConfig(h);
        BlockPos pedestal = new BlockPos(2, 2, 2);
        h.setBlock(pedestal.below(), Blocks.STONE);
        h.setBlock(pedestal, ShipDecor.FLAG_PEDESTAL.get());
        h.setBlock(pedestal.above(), ShipDecor.FLAGPOLE.get());
        h.setBlock(pedestal.below(), Blocks.AIR);
        h.runAtTickTime(2, () -> {
            h.assertBlockNotPresent(ShipDecor.FLAG_PEDESTAL.get(), pedestal);
            h.assertBlockNotPresent(ShipDecor.FLAGPOLE.get(), pedestal.above());
            h.assertItemEntityPresent(ShipDecor.FLAGPOLE.get().asItem(), pedestal.above(), 2.0);
            h.succeed();
        });
    }

    /** A pedestal placed in water is waterlogged, keeps the water with a pole in it and leaves it when broken. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void pedestalIsWaterloggable(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.CREATIVE);
        BlockPos base = new BlockPos(2, 1, 2), pedestal = base.above();
        h.setBlock(base, Blocks.STONE);
        h.setBlock(pedestal, Blocks.WATER);
        h.assertTrue(placeOnTop(h, player, ShipDecor.FLAG_PEDESTAL.get(), base).consumesAction(), "not placed in water");
        h.assertBlockProperty(pedestal, Waterlogging.WATERLOGGED, true);
        h.setBlock(pedestal.above(), ShipDecor.FLAGPOLE.get());
        h.assertBlockProperty(pedestal, FlagpoleFooting.POLE, true);
        h.assertBlockProperty(pedestal, Waterlogging.WATERLOGGED, true);
        h.assertTrue(h.getLevel().getFluidState(h.absolutePos(pedestal)).is(Fluids.WATER), "no water in the pedestal");
        h.succeed();
    }
}

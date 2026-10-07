package com.richardsenger.piratesnships.law.content;

import com.richardsenger.piratesnships.combat.content.ContentTestSupport;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/** Registration, drops, bar connections, the door and recipes of the {@code law.content} module. */
public final class LawContentGameTests {

    public static final List<String> ITEM_IDS = List.of("shackles", "brig_key");
    public static final List<String> BLOCK_IDS = List.of("brig_bars", "brig_door");

    private LawContentGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(LawContentGameTests.class);
    }

    @ModGameTest
    public static void lawContentIsRegistered(GameTestHelper helper) {
        ContentTestSupport.assertRegistered(helper, ITEM_IDS, BLOCK_IDS);
        helper.succeed();
    }

    @ModGameTest
    public static void brigBarsPlaceAndDropThemselves(GameTestHelper helper) {
        ContentTestSupport.assertPlacesAndDropsSelf(helper, LawContent.BRIG_BARS.get(), new BlockPos(1, 1, 1));
        helper.succeed();
    }

    @ModGameTest
    public static void brigBarsConnectToNeighbors(GameTestHelper helper) {
        BlockPos a = new BlockPos(0, 1, 1);
        BlockPos b = new BlockPos(1, 1, 1);
        helper.setBlock(a, LawContent.BRIG_BARS.get());
        helper.setBlock(b, LawContent.BRIG_BARS.get());
        // Relative east/west depend on the test's rotation, so look at the absolute neighbor direction
        Direction toB = Direction.fromDelta(helper.absolutePos(b).getX() - helper.absolutePos(a).getX(), 0,
                helper.absolutePos(b).getZ() - helper.absolutePos(a).getZ());
        BlockState stateA = helper.getBlockState(a);
        BlockState stateB = helper.getBlockState(b);
        helper.assertTrue(stateA.getValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(toB)), "first bars should connect towards the second, " + stateA);
        helper.assertTrue(stateB.getValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(toB.getOpposite())), "second bars should connect back, " + stateB);
        helper.assertFalse(stateA.getValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(toB.getOpposite())), "nothing on the other side, " + stateA);
        helper.succeed();
    }

    /**
     * Bars connect to a brig door beside them (both halves): bars standing before the door is placed reach out to it,
     * bars placed by a player next to an existing door connect at once, and breaking the door takes the arms back.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void brigBarsConnectToTheBrigDoor(GameTestHelper helper) {
        BlockPos west = new BlockPos(3, 1, 4);
        helper.setBlock(west.below(), net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(west, LawContent.BRIG_BARS.get());
        helper.setBlock(west.above(), LawContent.BRIG_BARS.get());
        BlockPos lower = placeDoor(helper, new BlockPos(4, 1, 4));
        Direction toDoor = absolute(helper, west, lower);
        for (BlockPos bars : List.of(west, west.above())) {
            helper.assertTrue(arm(helper, bars, toDoor), "bars at " + bars + " should reach the door, " + helper.getBlockState(bars));
            helper.assertFalse(arm(helper, bars, toDoor.getOpposite()), "nothing on the far side, " + helper.getBlockState(bars));
        }
        // A player places bars on the other side of the door: connected at placement
        BlockPos east = new BlockPos(5, 1, 4);
        helper.setBlock(east.below(), net.minecraft.world.level.block.Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(LawContent.BRIG_BARS.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos floor = helper.absolutePos(east.below());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        helper.assertTrue(((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit))
                .consumesAction(), "placing the bars failed");
        helper.assertTrue(arm(helper, east, absolute(helper, east, lower)), "placed bars should reach the door, " + helper.getBlockState(east));
        // Breaking the door retracts the arms
        helper.getLevel().destroyBlock(helper.absolutePos(lower), false);
        helper.assertFalse(arm(helper, west, toDoor), "door gone: no arm, " + helper.getBlockState(west));
        helper.assertFalse(arm(helper, west.above(), toDoor), "door gone: no upper arm, " + helper.getBlockState(west.above()));
        helper.succeed();
    }

    private static Direction absolute(GameTestHelper helper, BlockPos from, BlockPos to) {
        BlockPos a = helper.absolutePos(from);
        BlockPos b = helper.absolutePos(to);
        return Direction.fromDelta(Integer.signum(b.getX() - a.getX()), 0, Integer.signum(b.getZ() - a.getZ()));
    }

    private static boolean arm(GameTestHelper helper, BlockPos bars, Direction absolute) {
        return helper.getBlockState(bars).getValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(absolute));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void brigDoorOpensAndCloses(GameTestHelper helper) {
        BlockPos lower = placeDoor(helper, new BlockPos(4, 1, 4));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(lower, player);
        helper.assertTrue(helper.getBlockState(lower).getValue(DoorBlock.OPEN), "door should open by hand");
        helper.assertTrue(helper.getBlockState(lower.above()).getValue(DoorBlock.OPEN), "upper half should open too");
        helper.useBlock(lower, player);
        helper.assertFalse(helper.getBlockState(lower).getValue(DoorBlock.OPEN), "door should close again");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void brigDoorDropsOnceFromEitherHalf(GameTestHelper helper) {
        // Break the lower half: one door
        BlockPos first = placeDoor(helper, new BlockPos(2, 1, 2));
        helper.getLevel().destroyBlock(helper.absolutePos(first), true);
        helper.assertBlockNotPresent(LawContent.BRIG_DOOR.get(), first.above());
        // Break the upper half: the lower half goes with it and drops the door, the upper half drops nothing
        BlockPos second = placeDoor(helper, new BlockPos(6, 1, 6));
        helper.getLevel().destroyBlock(helper.absolutePos(second.above()), true);
        helper.assertBlockNotPresent(LawContent.BRIG_DOOR.get(), second);
        helper.runAfterDelay(1, () -> {
            helper.assertItemEntityCountIs(LawContent.BRIG_DOOR.get().asItem(), first, 1.5, 1);
            helper.assertItemEntityCountIs(LawContent.BRIG_DOOR.get().asItem(), second, 1.5, 1);
            helper.succeed();
        });
    }

    @ModGameTest
    public static void lawRecipesAreLoaded(GameTestHelper helper) {
        ContentTestSupport.assertRecipe(helper, "shackles", LawContent.SHACKLES.get(), 1);
        ContentTestSupport.assertRecipe(helper, "brig_key", LawContent.BRIG_KEY.get(), 1);
        ContentTestSupport.assertRecipe(helper, "brig_bars", LawContent.BRIG_BARS.get().asItem(), 6);
        ContentTestSupport.assertRecipe(helper, "brig_door", LawContent.BRIG_DOOR.get().asItem(), 1);
        helper.succeed();
    }

    /** Places both halves of a closed brig door on a floor block and returns the lower half's position. */
    private static BlockPos placeDoor(GameTestHelper helper, BlockPos lower) {
        helper.setBlock(lower.below(), net.minecraft.world.level.block.Blocks.STONE);
        BlockState base = LawContent.BRIG_DOOR.get().defaultBlockState();
        helper.setBlock(lower, base.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(lower.above(), base.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        helper.assertBlockPresent(LawContent.BRIG_DOOR.get(), lower);
        helper.assertBlockPresent(LawContent.BRIG_DOOR.get(), lower.above());
        return lower;
    }
}

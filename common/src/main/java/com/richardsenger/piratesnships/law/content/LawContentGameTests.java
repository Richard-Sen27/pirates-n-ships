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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.Collection;
import java.util.List;

/** Registration, drops, bar connections, the door and recipes of the {@code law.content} module. */
public final class LawContentGameTests {

    public static final List<String> ITEM_IDS = List.of("shackles");
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

package com.richardsenger.piratesnships.seachest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

/**
 * GameTest fixtures of the sea chest: water basins, a filled chest item, item use on a block face, and survival mock
 * players that are in the level (so they tick and the player tick hook runs) and are discarded when the test ends.
 * The player helper repeats {@code mob.MobTestSupport} (package-private there); both are candidates for
 * {@code core/gametest}.
 */
final class SeaChestTestSupport {

    private static volatile Field testInfoField;

    private SeaChestTestSupport() {
    }

    /** Stone floor at y=1 and stone walls around a {@code size}×{@code size} area, water from y=2 up to {@code top}. */
    static void basin(GameTestHelper h, int size, int top) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || z == 0 || x == size - 1 || z == size - 1;
                for (int y = 2; y <= top; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : Blocks.WATER);
                }
            }
        }
    }

    /** A sea chest item holding 5 diamonds in slot 0 and 12 apples in slot 30, named "Loot". */
    static ItemStack filledChest() {
        java.util.List<ItemStack> slots = SeaChestContents.emptySlots();
        slots.set(0, new ItemStack(Items.DIAMOND, 5));
        slots.set(30, new ItemStack(Items.APPLE, 12));
        return SeaChestContents.toItem(SeaChestContent.ITEM.get(), slots, net.minecraft.network.chat.Component.literal("Loot"));
    }

    /** Whether the stack is a sea chest holding exactly the contents of {@link #filledChest()}. */
    static boolean holdsFilledContents(ItemStack stack) {
        if (!stack.is(SeaChestContent.ITEM.get())) return false;
        java.util.List<ItemStack> slots = SeaChestContents.fromItem(stack);
        return slots.get(0).is(Items.DIAMOND) && slots.get(0).getCount() == 5
                && slots.get(30).is(Items.APPLE) && slots.get(30).getCount() == 12
                && SeaChestContents.count(slots) == 17;
    }

    /** Uses the item in the player's main hand on the top face of the block at {@code relative}. */
    static InteractionResult useOnTop(GameTestHelper h, Player player, BlockPos relative) {
        BlockPos abs = h.absolutePos(relative);
        return useOnTopAbsolute(player, abs);
    }

    static InteractionResult useOnTopAbsolute(Player player, BlockPos abs) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
        return player.getItemInHand(InteractionHand.MAIN_HAND).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /** A survival mock player in the level at the test-relative position, discarded when the test ends. */
    static Player playerInLevel(GameTestHelper helper, Vec3 relative) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        helper.getLevel().addFreshEntity(player);
        testInfo(helper).addListener(new GameTestListener() {
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
            Field f = testInfoField;
            if (f == null) {
                for (Field candidate : GameTestHelper.class.getDeclaredFields()) {
                    if (candidate.getType() == GameTestInfo.class) {
                        candidate.setAccessible(true);
                        testInfoField = f = candidate;
                        break;
                    }
                }
            }
            if (f == null) throw new IllegalStateException("GameTestHelper has no GameTestInfo field");
            return (GameTestInfo) f.get(helper);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}

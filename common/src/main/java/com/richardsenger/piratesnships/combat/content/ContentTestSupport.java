package com.richardsenger.piratesnships.combat.content;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * GameTest helpers shared by the basic content modules (combat, trade, crew, law, ship decor). Lives here because
 * those modules may not add files to {@code core}; a candidate for {@code core/gametest} later.
 */
public final class ContentTestSupport {

    private ContentTestSupport() {
    }

    /** Every id in {@code itemIds} is a registered item, every id in {@code blockIds} a block with a block item. */
    public static void assertRegistered(GameTestHelper helper, List<String> itemIds, List<String> blockIds) {
        for (String name : itemIds) {
            ResourceLocation id = Constants.id(name);
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "item " + id + " is not registered");
        }
        for (String name : blockIds) {
            ResourceLocation id = Constants.id(name);
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "block " + id + " is not registered");
            Item item = BuiltInRegistries.ITEM.get(id);
            helper.assertTrue(item instanceof BlockItem bi && bi.getBlock() == BuiltInRegistries.BLOCK.get(id),
                    "block " + id + " has no matching block item");
        }
    }

    /** The tool a player would use: an iron pickaxe for pickaxe blocks, otherwise an iron axe. */
    public static ItemStack rightTool(BlockState state) {
        return new ItemStack(state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? Items.IRON_PICKAXE : Items.IRON_AXE);
    }

    /**
     * Places {@code block} at {@code relative}, checks it is there, then harvests it the way a survival player with
     * the right tool does ({@code Block.getDrops} with that player and tool, the same call vanilla's harvest makes)
     * and asserts the drops are exactly one of the block's item.
     */
    public static void assertPlacesAndDropsSelf(GameTestHelper helper, Block block, BlockPos relative) {
        helper.setBlock(relative, block);
        helper.assertBlockPresent(block, relative);
        BlockPos pos = helper.absolutePos(relative);
        BlockState state = helper.getLevel().getBlockState(pos);
        ItemStack tool = rightTool(state);
        helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_AXE),
                BuiltInRegistries.BLOCK.getKey(block) + " is in no mineable tag");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        helper.assertTrue(player.hasCorrectToolForDrops(state), BuiltInRegistries.BLOCK.getKey(block) + " can't be harvested with " + tool);
        List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), pos, helper.getLevel().getBlockEntity(pos), player, tool);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(block.asItem()) && drops.getFirst().getCount() == 1,
                BuiltInRegistries.BLOCK.getKey(block) + " should drop exactly itself, dropped " + drops);
        helper.destroyBlock(relative);
    }

    /** The recipe {@code pirates_n_ships:<name>} is loaded and produces {@code count} of {@code result}. */
    public static void assertRecipe(GameTestHelper helper, String name, Item result, int count) {
        ResourceLocation id = Constants.id(name);
        Optional<RecipeHolder<?>> recipe = helper.getLevel().getRecipeManager().byKey(id);
        helper.assertTrue(recipe.isPresent(), "recipe " + id + " is not loaded");
        ItemStack out = recipe.get().value().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(out.is(result) && out.getCount() == count, "recipe " + id + " makes " + out + ", expected " + count + " " + result);
    }
}

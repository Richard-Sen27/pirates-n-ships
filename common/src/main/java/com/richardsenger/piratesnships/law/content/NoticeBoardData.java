package com.richardsenger.piratesnships.law.content;

import com.richardsenger.piratesnships.core.datagen.DataContributions;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.Direction;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Datagen of the notice board (docs/design.md §13.2): the block state by facing (pointing at the hand-made model
 * {@code block/notice_board}, notices on the north side), loot, tags, recipe and lang.
 */
public final class NoticeBoardData {

    private NoticeBoardData() {
    }

    public static void gather(DataContributions data) {
        Block board = LawContent.NOTICE_BOARD.get();
        data.lang(lang -> lang.block(LawContent.NOTICE_BOARD, "Notice Board"));
        data.models(m -> {
            // Hand-made Blockbench model (art/models/notice_board.bbmodel, design.md §4.8): only the block state is
            // generated. The notices face north in the unrotated model; the block item delegates to the block model.
            ResourceLocation model = ModelLocationUtils.getModelLocation(board);
            m.blockStates().accept(MultiVariantGenerator.multiVariant(board,
                    Variant.variant().with(VariantProperties.MODEL, model)).with(
                    PropertyDispatch.property(NoticeBoardBlock.FACING)
                            .select(Direction.NORTH, Variant.variant())
                            .select(Direction.EAST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                            .select(Direction.SOUTH, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R180))
                            .select(Direction.WEST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R270))));
        });
        data.blockLoot(loot -> loot.dropSelf(board));
        data.blockTags(tags -> tags.tag(BlockTags.MINEABLE_WITH_AXE).add(board));
        // Planks with paper down the middle
        data.recipes(out -> ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, board)
                .pattern("WPW").pattern("WPW")
                .define('W', ItemTags.PLANKS).define('P', Items.PAPER)
                .unlockedBy("has_paper", InventoryChangeTrigger.TriggerInstance.hasItems(Items.PAPER))
                .save(out, LawContent.NOTICE_BOARD.id()));
    }
}

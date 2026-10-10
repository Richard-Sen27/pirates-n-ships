package com.richardsenger.piratesnships.law.content;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import com.richardsenger.piratesnships.ship.decor.wood.WoodRecipe;
import net.minecraft.core.Direction;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;

/**
 * Datagen of the notice board (docs/design.md §13.2) in every wood (WOOD3): the block state by facing (pointing at the
 * wood's child of the hand-made model {@code block/notice_board}, notices on the north side), loot, tags, recipe and
 * lang. The model's posts, feet, rails and roof follow the wood ({@code #log}, {@code #log_top}, {@code #planks}); the
 * spruce board behind the notices stays.
 */
public final class NoticeBoardData {

    static final ResourceLocation MODEL = Constants.id("block/notice_board");

    private NoticeBoardData() {
    }

    public static void gather(DataContributions data) {
        WoodFamily<NoticeBoardBlock> boards = LawContent.NOTICE_BOARDS;
        data.lang(lang -> boards.lang(lang, "Notice Board"));
        data.models(m -> {
            // Hand-made Blockbench model (art/models/notice_board.bbmodel, design.md §4.8): a child per wood, the block
            // states and the item models. The notices face north in the unrotated model.
            boards.childModels(m, MODEL);
            boards.itemModels(m, MODEL);
            boards.forEach((wood, board) -> m.blockStates().accept(MultiVariantGenerator.multiVariant(board,
                    Variant.variant().with(VariantProperties.MODEL, WoodFamily.child(wood, MODEL))).with(
                    PropertyDispatch.property(NoticeBoardBlock.FACING)
                            .select(Direction.NORTH, Variant.variant())
                            .select(Direction.EAST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                            .select(Direction.SOUTH, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R180))
                            .select(Direction.WEST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R270)))));
        });
        boards.dropSelf(data);
        data.blockTags(tags -> boards.tag(tags, BlockTags.MINEABLE_WITH_AXE));
        // The wood's planks with paper down the middle
        boards.recipe(data, WoodRecipe.shaped(RecipeCategory.DECORATIONS, 1, "WPW", "WPW")
                .define('W', WoodRecipe.planks()).define('P', WoodRecipe.item(Items.PAPER))
                .group(Constants.MOD_ID + ":notice_board").unlockedBy("has_paper", WoodRecipe.item(Items.PAPER)));
    }
}

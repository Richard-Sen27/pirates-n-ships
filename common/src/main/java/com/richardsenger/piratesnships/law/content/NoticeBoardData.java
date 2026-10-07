package com.richardsenger.piratesnships.law.content;

import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.ship.decor.flag.ElementModel;
import com.richardsenger.piratesnships.ship.decor.flag.ElementModel.Face;
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
 * Datagen of the notice board (docs/design.md §13.2): a placeholder model built from elements with vanilla textures
 * (spruce board on two dark oak legs under a little roof, four notices of white terracotta with red pins on the north
 * side) until a Blockbench model replaces it, the block state by facing, loot, tags, recipe and lang.
 */
public final class NoticeBoardData {

    private NoticeBoardData() {
    }

    public static void gather(DataContributions data) {
        Block board = LawContent.NOTICE_BOARD.get();
        data.lang(lang -> lang.block(LawContent.NOTICE_BOARD, "Notice Board"));
        data.models(m -> {
            ResourceLocation model = ModelLocationUtils.getModelLocation(board);
            m.models().accept(model, () -> model().build());
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

    /** The placeholder model, notices facing north (z small). */
    static ElementModel model() {
        ElementModel m = new ElementModel()
                .texture("particle", "minecraft:block/spruce_planks")
                .texture("board", "minecraft:block/spruce_planks")
                .texture("post", "minecraft:block/dark_oak_planks")
                .texture("paper", "minecraft:block/white_terracotta")
                .texture("pin", "minecraft:block/red_wool");
        box(m, 2, 0, 8, 4, 15, 10, "#post");
        box(m, 12, 0, 8, 14, 15, 10, "#post");
        box(m, 0, 4, 7, 16, 15, 8, "#board");
        box(m, 0, 15, 6, 16, 16, 10, "#post");
        paper(m, 2, 9, 7, 14);
        paper(m, 8.5f, 8, 14, 13.5f);
        paper(m, 3, 5, 7.5f, 8.5f);
        paper(m, 9, 4.5f, 12.5f, 7.5f);
        return m;
    }

    private static void paper(ElementModel m, float x0, float y0, float x1, float y1) {
        box(m, x0, y0, 6.75f, x1, y1, 7, "#paper");
        float cx = (x0 + x1) / 2;
        box(m, cx - 0.5f, y1 - 1.5f, 6.5f, cx + 0.5f, y1 - 0.5f, 6.75f, "#pin");
    }

    /** A cuboid with every face textured, UVs projected from the coordinates like vanilla's automatic UVs. */
    private static void box(ElementModel m, float x0, float y0, float z0, float x1, float y1, float z1, String tex) {
        m.element(x0, y0, z0, x1, y1, z1)
                .face(Face.NORTH, 16 - x1, 16 - y1, 16 - x0, 16 - y0, tex)
                .face(Face.SOUTH, x0, 16 - y1, x1, 16 - y0, tex)
                .face(Face.WEST, z0, 16 - y1, z1, 16 - y0, tex)
                .face(Face.EAST, 16 - z1, 16 - y1, 16 - z0, 16 - y0, tex)
                .face(Face.UP, x0, z0, x1, z1, tex)
                .face(Face.DOWN, x0, 16 - z1, x1, 16 - z0, tex)
                .end();
    }
}

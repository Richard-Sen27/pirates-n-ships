package com.richardsenger.piratesnships.trade.desk;

import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.ship.decor.flag.ElementModel;
import com.richardsenger.piratesnships.trade.client.MarketText;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.Direction;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Datagen of the harbor master's desk: a placeholder writing desk from elements with vanilla textures (a Blockbench
 * model replaces it in a later art batch), block state by facing, recipe, loot, tags and lang.
 */
public final class HarborDeskData {

    private static final String WOOD = "minecraft:block/dark_oak_planks";
    private static final String TOP = "minecraft:block/spruce_planks";
    private static final String DRAWER = "minecraft:block/stripped_dark_oak_log";
    private static final String KNOB = "minecraft:block/gold_block";
    private static final String COVER = "minecraft:block/red_wool";
    private static final String PAGES = "minecraft:block/white_wool";
    private static final String INK = "minecraft:block/black_concrete";

    private HarborDeskData() {
    }

    public static void gather(DataContributions data) {
        Block desk = HarborDesks.HARBOR_DESK.get();
        data.lang(lang -> lang.block(HarborDesks.HARBOR_DESK, "Harbor Master's Desk")
                .add(HarborDeskService.Use.UNBOUND.message(), "This desk does not belong to a port")
                .add(HarborDeskService.Use.NO_MARKET.message(), "This desk's port has no market"));
        data.lang(HarborDeskCommands::lang);
        data.lang(MarketText::lang);
        data.models(m -> {
            m.models().accept(ModelLocationUtils.getModelLocation(desk), HarborDeskData::model);
            m.blockStates().accept(MultiVariantGenerator.multiVariant(desk,
                    Variant.variant().with(VariantProperties.MODEL, ModelLocationUtils.getModelLocation(desk))).with(
                    PropertyDispatch.property(HarborDeskBlock.FACING)
                            .select(Direction.NORTH, Variant.variant())
                            .select(Direction.EAST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                            .select(Direction.SOUTH, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R180))
                            .select(Direction.WEST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R270))));
        });
        data.blockLoot(loot -> loot.dropSelf(desk));
        data.blockTags(tags -> tags.tag(BlockTags.MINEABLE_WITH_AXE).add(desk));
        data.recipes(out -> ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, desk)
                .pattern("BG ").pattern("PPP").pattern("P P")
                .define('B', Items.BOOK).define('G', Items.GOLD_NUGGET).define('P', ItemTags.PLANKS)
                .unlockedBy("has_book", InventoryChangeTrigger.TriggerInstance.hasItems(Items.BOOK))
                .save(out, HarborDesks.HARBOR_DESK.id()));
    }

    /**
     * A writing desk facing north (the customer's side): a top board over two drawer pedestals with drawer fronts
     * and gold knobs, a red ledger and an inkwell on top.
     */
    public static JsonObject model() {
        ElementModel m = new ElementModel()
                .texture("particle", WOOD).texture("wood", WOOD).texture("top", TOP).texture("drawer", DRAWER)
                .texture("knob", KNOB).texture("cover", COVER).texture("pages", PAGES).texture("ink", INK);
        box(m, 0, 12, 2, 16, 14, 14, "#top", "#wood");
        box(m, 1, 0, 3, 6, 12, 13, "#wood", "#wood");
        box(m, 10, 0, 3, 15, 12, 13, "#wood", "#wood");
        // back panel between the pedestals (the harbor master's knees stay hidden from the customer)
        box(m, 6, 4, 12, 10, 12, 13, "#wood", "#wood");
        for (float x0 : new float[]{1.5f, 10.5f}) {
            box(m, x0, 7, 2.5f, x0 + 4, 11, 3, "#drawer", "#drawer");
            box(m, x0, 1, 2.5f, x0 + 4, 6, 3, "#drawer", "#drawer");
            box(m, x0 + 1.5f, 8.5f, 2, x0 + 2.5f, 9.5f, 2.5f, "#knob", "#knob");
            box(m, x0 + 1.5f, 3, 2, x0 + 2.5f, 4, 2.5f, "#knob", "#knob");
        }
        // ledger: red cover with white page edges
        box(m, 3, 14, 5, 10, 14.5f, 10, "#pages", "#cover");
        box(m, 3, 14.5f, 5, 10, 15, 10, "#cover", "#pages");
        box(m, 2.8f, 14, 4.8f, 10.2f, 14.25f, 10.2f, "#cover", "#cover");
        // inkwell
        box(m, 12, 14, 7, 14, 15.5f, 9, "#ink", "#ink");
        JsonObject json = m.build();
        // display transforms of a normal block (GUI, hand, ground); our elements replace the parent's none
        json.addProperty("parent", "minecraft:block/block");
        return json;
    }

    /** A cuboid with auto UVs from its coordinates: {@code topTex} on up and down, {@code sideTex} on the sides. */
    private static void box(ElementModel m, float x0, float y0, float z0, float x1, float y1, float z1, String topTex, String sideTex) {
        m.element(x0, y0, z0, x1, y1, z1)
                .face(ElementModel.Face.UP, x0, z0, x1, z1, topTex)
                .face(ElementModel.Face.DOWN, x0, 16 - z1, x1, 16 - z0, topTex)
                .face(ElementModel.Face.NORTH, 16 - x1, 16 - y1, 16 - x0, 16 - y0, sideTex)
                .face(ElementModel.Face.SOUTH, x0, 16 - y1, x1, 16 - y0, sideTex)
                .face(ElementModel.Face.WEST, z0, 16 - y1, z1, 16 - y0, sideTex)
                .face(ElementModel.Face.EAST, 16 - z1, 16 - y1, 16 - z0, 16 - y0, sideTex)
                .end();
    }
}

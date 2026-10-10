package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.sailing.sail.TriangularSailContent;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import com.richardsenger.piratesnships.ship.decor.wood.WoodRecipe;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.Direction;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.DelegatedModel;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.List;

/**
 * Datagen of the ART2 decor blocks (design.md §4.8): block states that only point at the hand-made Blockbench models
 * (built from {@code tools/gen_decor_models.py}, projects in {@code art/models/}), item models, lang, loot, tags and
 * recipes. Every model faces north and is turned by {@code FACING} like a furnace.
 */
final class DecorData {

    private DecorData() {
    }

    static void gather(DataContributions data) {
        data.lang(lang -> lang
                .block(ShipDecor.SHIP_LANTERN, "Ship's Lantern")
                .block(ShipDecor.SHIPS_BELL, "Ship's Bell")
                .block(ShipDecor.ROPE_COIL, "Rope Coil")
                .add(SeaCotBlock.KEY_ON_SHIP, "Just for show while aboard a ship")
                .add(SeaCotBlock.KEY_NO_SLEEPING, "This cot is just for show"));
        // WOOD3: "Dark Oak Stern Window", "Spruce Chart Table", ...
        data.lang(lang -> {
            ShipDecor.STERN_WINDOWS.lang(lang, "Stern Window");
            ShipDecor.CHART_TABLES.lang(lang, "Chart Table");
            ShipDecor.SEA_COTS.lang(lang, "Sea Cot");
        });
        data.models(DecorData::models);
        data.models(DecorData::woodModels);
        ShipDecor.STERN_WINDOWS.dropSelf(data);
        ShipDecor.CHART_TABLES.dropSelf(data);
        // like the hammock: only the foot drops, so breaking either half yields one cot
        ShipDecor.SEA_COTS.loot(data, cot -> LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(cot).when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(cot)
                        .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(SeaCotBlock.PART, BedPart.FOOT))))
                .when(ExplosionCondition.survivesExplosion())));
        data.blockLoot(loot -> {
            loot.dropSelf(ShipDecor.SHIP_LANTERN.get());
            loot.dropSelf(ShipDecor.SHIPS_BELL.get());
            // one coil per layer, like snow layers
            RopeCoilBlock coil = ShipDecor.ROPE_COIL.get();
            LootItem.Builder<?> coils = LootItem.lootTableItem(coil);
            for (int n = 2; n <= RopeCoilBlock.MAX_LAYERS; n++) {
                coils.apply(SetItemCountFunction.setCount(ConstantValue.exactly(n))
                        .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(coil)
                                .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(RopeCoilBlock.LAYERS, n))));
            }
            loot.add(coil, LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                    .add(coils).when(ExplosionCondition.survivesExplosion())));
        });
        data.blockTags(tags -> {
            tags.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ShipDecor.SHIP_LANTERN.get(), ShipDecor.SHIPS_BELL.get());
            tags.tag(BlockTags.MINEABLE_WITH_AXE).add(ShipDecor.ROPE_COIL.get());
            // small and thin: like lanterns, chains and wool in Sable's own tags
            tags.tag(SableWeightTags.SUPER_LIGHT).add(ShipDecor.SHIP_LANTERN.get(), ShipDecor.ROPE_COIL.get());
            tags.tag(SableWeightTags.QUARTER_VOLUME).add(ShipDecor.SHIP_LANTERN.get(), ShipDecor.ROPE_COIL.get());
            // wooden furniture: like planks, chests and beds (every wood, WOOD3)
            for (WoodFamily<?> furniture : List.of(ShipDecor.STERN_WINDOWS, ShipDecor.CHART_TABLES, ShipDecor.SEA_COTS)) {
                furniture.tag(tags, BlockTags.MINEABLE_WITH_AXE, SableWeightTags.LIGHT);
            }
        });
        data.recipes(out -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, ShipDecor.SHIP_LANTERN.get())
                    .requires(Items.LANTERN).requires(Items.GOLD_NUGGET).requires(Items.GOLD_NUGGET)
                    .unlockedBy("has_lantern", InventoryChangeTrigger.TriggerInstance.hasItems(Items.LANTERN))
                    .save(out, ShipDecor.SHIP_LANTERN.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ShipDecor.SHIPS_BELL.get())
                    .pattern("SSS").pattern(" G ").pattern("GNG")
                    .define('S', Items.STICK).define('G', Items.GOLD_INGOT).define('N', Items.IRON_NUGGET)
                    .unlockedBy("has_gold_ingot", InventoryChangeTrigger.TriggerInstance.hasItems(Items.GOLD_INGOT))
                    .save(out, ShipDecor.SHIPS_BELL.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ShipDecor.ROPE_COIL.get())
                    .pattern("RR").pattern("RR")
                    .define('R', TriangularSailContent.ROPE.get())
                    .unlockedBy("has_rope", InventoryChangeTrigger.TriggerInstance.hasItems(TriangularSailContent.ROPE.get()))
                    .save(out, ShipDecor.ROPE_COIL.id());
        });
        // WOOD3: each wood's stern window from that wood's planks (dark oak's before), chart table and cot likewise
        ShipDecor.STERN_WINDOWS.recipe(data, WoodRecipe.shaped(RecipeCategory.DECORATIONS, 2, "DSD", "SSS", "DND")
                .define('D', WoodRecipe.planks()).define('S', WoodRecipe.item(Items.GLASS_PANE)).define('N', WoodRecipe.item(Items.GOLD_NUGGET))
                .group(Constants.MOD_ID + ":stern_window").unlockedBy("has_glass_pane", WoodRecipe.item(Items.GLASS_PANE)));
        ShipDecor.CHART_TABLES.recipe(data, WoodRecipe.shaped(RecipeCategory.DECORATIONS, 1, " M ", "PPP", "S S")
                .define('M', WoodRecipe.item(Items.MAP)).define('P', WoodRecipe.planks()).define('S', WoodRecipe.item(Items.STICK))
                .group(Constants.MOD_ID + ":chart_table").unlockedBy("has_map", WoodRecipe.item(Items.MAP)));
        ShipDecor.SEA_COTS.recipe(data, WoodRecipe.shaped(RecipeCategory.DECORATIONS, 1, "PWP", "PPP", "S S")
                .define('P', WoodRecipe.planks()).define('W', WoodRecipe.tag(ItemTags.WOOL)).define('S', WoodRecipe.item(Items.STICK))
                .group(Constants.MOD_ID + ":sea_cot").unlockedBy("has_wool", WoodRecipe.item(Items.WHITE_WOOL)));
    }

    private static void models(ModelContext m) {
        ShipLanternBlock lantern = ShipDecor.SHIP_LANTERN.get();
        m.blockStates().accept(MultiVariantGenerator.multiVariant(lantern).with(PropertyDispatch
                .properties(ShipLanternBlock.FACE, ShipLanternBlock.FACING).generate((face, facing) -> Variant.variant()
                        .with(VariantProperties.MODEL, switch (face) {
                            case FLOOR -> model(lantern, "");
                            case WALL -> model(lantern, "_wall");
                            case CEILING -> model(lantern, "_ceiling");
                        })
                        .with(VariantProperties.Y_ROT, yRot(facing)))));

        // BELL1: the block model is only the mount (post frame or wall bracket); ShipsBellRenderer draws the bell and
        // its clapper swinging. RINGING does not change the look. The item keeps the whole bell on its post
        // (block/ships_bell, which the default item model delegates to).
        ShipsBellBlock bell = ShipDecor.SHIPS_BELL.get();
        m.blockStates().accept(MultiVariantGenerator.multiVariant(bell).with(PropertyDispatch
                .properties(ShipsBellBlock.FACE, ShipsBellBlock.FACING).generate((face, facing) ->
                        Variant.variant()
                                .with(VariantProperties.MODEL, model(bell, face == AttachFace.WALL ? "_wall" : "_post"))
                                .with(VariantProperties.Y_ROT, yRot(facing)))));

        RopeCoilBlock coil = ShipDecor.ROPE_COIL.get();
        m.blockStates().accept(MultiVariantGenerator.multiVariant(coil).with(PropertyDispatch
                .properties(RopeCoilBlock.LAYERS, RopeCoilBlock.FACING).generate((layers, facing) -> Variant.variant()
                        .with(VariantProperties.MODEL, model(coil, "_layers" + layers))
                        .with(VariantProperties.Y_ROT, yRot(facing)))));
        // the item shows two coils (that model carries the item's display entries)
        m.models().accept(ModelLocationUtils.getModelLocation(coil.asItem()), new DelegatedModel(model(coil, "_layers2")));

    }

    static final ResourceLocation STERN_WINDOW = Constants.id("block/stern_window");
    static final ResourceLocation STERN_WINDOW_SHUTTERS = Constants.id("block/stern_window_shutters");
    static final ResourceLocation CHART_TABLE = Constants.id("block/chart_table");
    static final ResourceLocation SEA_COT_HEAD = Constants.id("block/sea_cot_head");
    static final ResourceLocation SEA_COT_FOOT = Constants.id("block/sea_cot_foot");
    static final ResourceLocation SEA_COT_ITEM = Constants.id("block/sea_cot_item");

    /**
     * WOOD3: the stern window, chart table and sea cot per wood: children of the hand-made parents
     * ({@code tools/gen_decor_models.py}; {@code #planks}, {@code #log}, accents fixed: the shutters' dark oak trapdoor,
     * the chart table's spruce drawer) per wood, block states pointing at the member's wood, item models as children.
     */
    private static void woodModels(ModelContext m) {
        WoodFamily<SternWindowBlock> windows = ShipDecor.STERN_WINDOWS;
        windows.childModels(m, STERN_WINDOW);
        windows.childModels(m, STERN_WINDOW_SHUTTERS);
        windows.itemModels(m, STERN_WINDOW);
        windows.forEach((wood, window) -> m.blockStates().accept(MultiVariantGenerator.multiVariant(window).with(PropertyDispatch
                .properties(SternWindowBlock.FACING, SternWindowBlock.SHUTTERS).generate((facing, shut) -> Variant.variant()
                        .with(VariantProperties.MODEL, WoodFamily.child(wood, shut ? STERN_WINDOW_SHUTTERS : STERN_WINDOW))
                        .with(VariantProperties.Y_ROT, yRot(facing))))));

        WoodFamily<ChartTableBlock> tables = ShipDecor.CHART_TABLES;
        tables.childModels(m, CHART_TABLE);
        tables.itemModels(m, CHART_TABLE);
        tables.forEach((wood, table) -> m.blockStates().accept(MultiVariantGenerator.multiVariant(table).with(PropertyDispatch
                .property(ChartTableBlock.FACING).generate(facing -> Variant.variant()
                        .with(VariantProperties.MODEL, WoodFamily.child(wood, CHART_TABLE))
                        .with(VariantProperties.Y_ROT, yRot(facing))))));

        WoodFamily<SeaCotBlock> cots = ShipDecor.SEA_COTS;
        for (ResourceLocation parent : List.of(SEA_COT_HEAD, SEA_COT_FOOT)) cots.childModels(m, parent);
        // the item is the whole cot (both halves, display scaled to the slot)
        cots.itemModels(m, SEA_COT_ITEM);
        cots.forEach((wood, cot) -> m.blockStates().accept(MultiVariantGenerator.multiVariant(cot).with(PropertyDispatch
                .properties(SeaCotBlock.FACING, SeaCotBlock.PART).generate((facing, part) -> Variant.variant()
                        .with(VariantProperties.MODEL, WoodFamily.child(wood, part == BedPart.HEAD ? SEA_COT_HEAD : SEA_COT_FOOT))
                        .with(VariantProperties.Y_ROT, yRot(facing))))));
    }

    private static ResourceLocation model(Block block, String suffix) {
        return ModelLocationUtils.getModelLocation(block, suffix);
    }

    private static VariantProperties.Rotation yRot(Direction facing) {
        return switch (facing) {
            case EAST -> VariantProperties.Rotation.R90;
            case SOUTH -> VariantProperties.Rotation.R180;
            case WEST -> VariantProperties.Rotation.R270;
            default -> VariantProperties.Rotation.R0;
        };
    }
}

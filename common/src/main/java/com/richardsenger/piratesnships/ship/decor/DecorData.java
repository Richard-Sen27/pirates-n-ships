package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.sailing.sail.TriangularSailContent;
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
                .block(ShipDecor.STERN_WINDOW, "Stern Window")
                .block(ShipDecor.CHART_TABLE, "Chart Table")
                .block(ShipDecor.SEA_COT, "Sea Cot")
                .add(SeaCotBlock.KEY_ON_SHIP, "This cot is just for show while it is aboard a ship")
                .add(SeaCotBlock.KEY_NO_SLEEPING, "This cot is just for show"));
        data.models(DecorData::models);
        data.blockLoot(loot -> {
            loot.dropSelf(ShipDecor.SHIP_LANTERN.get());
            loot.dropSelf(ShipDecor.SHIPS_BELL.get());
            loot.dropSelf(ShipDecor.STERN_WINDOW.get());
            loot.dropSelf(ShipDecor.CHART_TABLE.get());
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
            // like the hammock: only the foot drops, so breaking either half yields one cot
            SeaCotBlock cot = ShipDecor.SEA_COT.get();
            loot.add(cot, LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(cot).when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(cot)
                            .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(SeaCotBlock.PART, BedPart.FOOT))))
                    .when(ExplosionCondition.survivesExplosion())));
        });
        data.blockTags(tags -> {
            tags.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ShipDecor.SHIP_LANTERN.get(), ShipDecor.SHIPS_BELL.get());
            tags.tag(BlockTags.MINEABLE_WITH_AXE).add(ShipDecor.ROPE_COIL.get(), ShipDecor.STERN_WINDOW.get(),
                    ShipDecor.CHART_TABLE.get(), ShipDecor.SEA_COT.get());
            // small and thin: like lanterns, chains and wool in Sable's own tags
            tags.tag(SableWeightTags.SUPER_LIGHT).add(ShipDecor.SHIP_LANTERN.get(), ShipDecor.ROPE_COIL.get());
            tags.tag(SableWeightTags.QUARTER_VOLUME).add(ShipDecor.SHIP_LANTERN.get(), ShipDecor.ROPE_COIL.get());
            // wooden furniture: like planks, chests and beds
            tags.tag(SableWeightTags.LIGHT).add(ShipDecor.STERN_WINDOW.get(), ShipDecor.CHART_TABLE.get(), ShipDecor.SEA_COT.get());
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
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ShipDecor.STERN_WINDOW.get(), 2)
                    .pattern("DSD").pattern("SSS").pattern("DND")
                    .define('D', Items.DARK_OAK_PLANKS).define('S', Items.GLASS_PANE).define('N', Items.GOLD_NUGGET)
                    .unlockedBy("has_glass_pane", InventoryChangeTrigger.TriggerInstance.hasItems(Items.GLASS_PANE))
                    .save(out, ShipDecor.STERN_WINDOW.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ShipDecor.CHART_TABLE.get())
                    .pattern(" M ").pattern("PPP").pattern("S S")
                    .define('M', Items.MAP).define('P', ItemTags.PLANKS).define('S', Items.STICK)
                    .unlockedBy("has_map", InventoryChangeTrigger.TriggerInstance.hasItems(Items.MAP))
                    .save(out, ShipDecor.CHART_TABLE.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ShipDecor.SEA_COT.get())
                    .pattern("PWP").pattern("PPP").pattern("S S")
                    .define('P', ItemTags.PLANKS).define('W', ItemTags.WOOL).define('S', Items.STICK)
                    .unlockedBy("has_wool", InventoryChangeTrigger.TriggerInstance.hasItems(Items.WHITE_WOOL))
                    .save(out, ShipDecor.SEA_COT.id());
        });
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

        ShipsBellBlock bell = ShipDecor.SHIPS_BELL.get();
        m.blockStates().accept(MultiVariantGenerator.multiVariant(bell).with(PropertyDispatch
                .properties(ShipsBellBlock.FACE, ShipsBellBlock.FACING, ShipsBellBlock.RINGING).generate((face, facing, ringing) ->
                        Variant.variant()
                                .with(VariantProperties.MODEL, model(bell, (face == AttachFace.WALL ? "_wall" : "") + (ringing ? "_ringing" : "")))
                                .with(VariantProperties.Y_ROT, yRot(facing)))));

        RopeCoilBlock coil = ShipDecor.ROPE_COIL.get();
        m.blockStates().accept(MultiVariantGenerator.multiVariant(coil).with(PropertyDispatch
                .properties(RopeCoilBlock.LAYERS, RopeCoilBlock.FACING).generate((layers, facing) -> Variant.variant()
                        .with(VariantProperties.MODEL, model(coil, "_layers" + layers))
                        .with(VariantProperties.Y_ROT, yRot(facing)))));
        // the item shows two coils (that model carries the item's display entries)
        m.models().accept(ModelLocationUtils.getModelLocation(coil.asItem()), new DelegatedModel(model(coil, "_layers2")));

        SternWindowBlock window = ShipDecor.STERN_WINDOW.get();
        m.blockStates().accept(MultiVariantGenerator.multiVariant(window).with(PropertyDispatch
                .properties(SternWindowBlock.FACING, SternWindowBlock.SHUTTERS).generate((facing, shut) -> Variant.variant()
                        .with(VariantProperties.MODEL, model(window, shut ? "_shutters" : ""))
                        .with(VariantProperties.Y_ROT, yRot(facing)))));

        ChartTableBlock table = ShipDecor.CHART_TABLE.get();
        m.blockStates().accept(MultiVariantGenerator.multiVariant(table).with(PropertyDispatch
                .property(ChartTableBlock.FACING).generate(facing -> Variant.variant()
                        .with(VariantProperties.MODEL, model(table, ""))
                        .with(VariantProperties.Y_ROT, yRot(facing)))));

        SeaCotBlock cot = ShipDecor.SEA_COT.get();
        m.blockStates().accept(MultiVariantGenerator.multiVariant(cot).with(PropertyDispatch
                .properties(SeaCotBlock.FACING, SeaCotBlock.PART).generate((facing, part) -> Variant.variant()
                        .with(VariantProperties.MODEL, model(cot, part == BedPart.HEAD ? "_head" : "_foot"))
                        .with(VariantProperties.Y_ROT, yRot(facing)))));
        // the item is the whole cot (both halves, display scaled to the slot)
        m.models().accept(ModelLocationUtils.getModelLocation(cot.asItem()), new DelegatedModel(Constants.id("block/sea_cot_item")));
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

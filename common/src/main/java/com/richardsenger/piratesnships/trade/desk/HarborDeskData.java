package com.richardsenger.piratesnships.trade.desk;

import com.richardsenger.piratesnships.core.datagen.DataContributions;
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
 * Datagen of the harbor master's desk: block state by facing (the model is hand-made in Blockbench), recipe, loot,
 * tags and lang.
 */
public final class HarborDeskData {

    private HarborDeskData() {
    }

    public static void gather(DataContributions data) {
        Block desk = HarborDesks.HARBOR_DESK.get();
        data.lang(lang -> lang.block(HarborDesks.HARBOR_DESK, "Harbor Master's Desk")
                .add(HarborDeskService.Use.UNBOUND.message(), "This desk does not belong to a port")
                .add(HarborDeskService.Use.NO_MARKET.message(), "This desk's port has no market"));
        data.lang(HarborDeskCommands::lang);
        data.lang(MarketText::lang);
        // SW1: the shipwright's Orders tab and the ship receipt
        com.richardsenger.piratesnships.ship.template.ShipOrderData.gather(data);
        // hand-made Blockbench model (art/models/harbor_desk.bbmodel, design.md §4.8): only the block state is
        // generated. The model's north side (bell) is the customer's front, unrotated for FACING north; the ledger, the
        // inkwell and the coins face the harbor master on the south side.
        data.models(m -> {
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
}

package com.richardsenger.piratesnships.trade.desk;

import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.trade.client.MarketText;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import com.richardsenger.piratesnships.ship.decor.wood.WoodRecipe;
import net.minecraft.core.Direction;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;

/**
 * Datagen of the harbor master's desk in every wood (WOOD2): block state by facing (the model is hand-made in
 * Blockbench, a child per wood), recipe from that wood's planks, loot, tags and lang.
 */
public final class HarborDeskData {

    private HarborDeskData() {
    }

    /** The hand-made parent model ({@code art/models/harbor_desk.bbmodel}): dark oak's planks are {@code #planks}. */
    static final ResourceLocation DESK = Constants.id("block/harbor_desk");

    public static void gather(DataContributions data) {
        WoodFamily<HarborDeskBlock> desks = HarborDesks.HARBOR_DESKS;
        data.lang(lang -> desks.lang(lang, "Harbor Master's Desk"));
        data.lang(lang -> lang
                .add(HarborDeskService.Use.UNBOUND.message(), "This desk does not belong to a port")
                .add(HarborDeskService.Use.NO_MARKET.message(), "This desk's port has no market"));
        data.lang(HarborDeskCommands::lang);
        data.lang(MarketText::lang);
        // SW1: the shipwright's Orders tab and the ship receipt
        com.richardsenger.piratesnships.ship.template.ShipOrderData.gather(data);
        // hand-made Blockbench model (art/models/harbor_desk.bbmodel, design.md §4.8) with the wood family's texture
        // variables (WOOD2): a child per wood, the block state turns it. The model's north side (bell) is the
        // customer's front, unrotated for FACING north; the ledger, the inkwell and the coins face the harbor master on
        // the south side. The stripped spruce drawers stay spruce in every wood (an accent).
        data.models(m -> {
            desks.childModels(m, DESK);
            desks.itemModels(m, DESK);
            desks.forEach((wood, desk) -> m.blockStates().accept(MultiVariantGenerator.multiVariant(desk,
                    Variant.variant().with(VariantProperties.MODEL, WoodFamily.child(wood, DESK))).with(
                    PropertyDispatch.property(HarborDeskBlock.FACING)
                            .select(Direction.NORTH, Variant.variant())
                            .select(Direction.EAST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                            .select(Direction.SOUTH, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R180))
                            .select(Direction.WEST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R270)))));
        });
        desks.dropSelf(data);
        data.blockTags(tags -> desks.tag(tags, BlockTags.MINEABLE_WITH_AXE));
        // that wood's planks (WOOD2; before: any planks)
        desks.recipe(data, WoodRecipe.shaped(RecipeCategory.DECORATIONS, 1, "BG ", "PPP", "P P")
                .define('B', WoodRecipe.item(Items.BOOK)).define('G', WoodRecipe.item(Items.GOLD_NUGGET)).define('P', WoodRecipe.planks())
                .group(Constants.MOD_ID + ":harbor_desk").unlockedBy("has_book", WoodRecipe.item(Items.BOOK)));
    }
}

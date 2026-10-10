package com.richardsenger.piratesnships.sailing.block;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
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
 * Datagen of the sail winch and the capstan families (WOOD4, docs/woods.md): lang, child models of the hand-made parents
 * per wood, block states, item models, loot, tags and recipes from that wood's planks (and stripped log for the
 * capstan). Called from {@code SailingModule.gatherData}; the blocks' other lines (trim, anchor messages) stay there.
 */
public final class WinchAndCapstanData {

    /** The sail winch's hand-made model ({@code art/models/sail_winch.bbmodel}, crank east), block and item. */
    public static final ResourceLocation WINCH_MODEL = Constants.id("block/sail_winch");
    /** CAP2: the capstan's base and drum (the block model), with the wood variables. */
    public static final ResourceLocation CAPSTAN_MODEL = Constants.id("block/capstan");
    /** CAP2: the bars and drumhead, a stand-alone model drawn turning by {@code CapstanBarsRenderer} (one child per wood). */
    public static final ResourceLocation CAPSTAN_BARS_MODEL = Constants.id("block/capstan_bars");
    /** CAP2: bars and drum together, for the items. */
    public static final ResourceLocation CAPSTAN_ITEM_MODEL = Constants.id("block/capstan_item");

    private WinchAndCapstanData() {
    }

    public static void gather(DataContributions data) {
        WoodFamily<SailWinchBlock> winches = SailingBlocks.SAIL_WINCHES;
        WoodFamily<CapstanBlock> capstans = SailingBlocks.CAPSTANS;
        data.lang(lang -> {
            winches.lang(lang, "Sail Winch");
            capstans.lang(lang, "Capstan");
        });
        data.models(WinchAndCapstanData::models);
        winches.dropSelf(data);
        capstans.dropSelf(data);
        data.blockTags(tags -> {
            winches.tag(tags, BlockTags.MINEABLE_WITH_AXE, SableWeightTags.LIGHT);
            capstans.tag(tags, BlockTags.MINEABLE_WITH_AXE);
        });
        // WOOD4: that wood's planks (and stripped log) instead of any planks and #logs; the legacy members keep the
        // recipe ids sail_winch and capstan
        winches.recipe(data, WoodRecipe.shaped(RecipeCategory.TRANSPORTATION, 1, "TIT", "PPP")
                .define('T', WoodRecipe.item(Items.STRING)).define('I', WoodRecipe.item(Items.IRON_INGOT))
                .define('P', WoodRecipe.planks())
                .group(Constants.MOD_ID + ":sail_winch").unlockedBy("has_iron_ingot", WoodRecipe.item(Items.IRON_INGOT)));
        capstans.recipe(data, WoodRecipe.shaped(RecipeCategory.TRANSPORTATION, 1, "LSL", "CIC", "PPP")
                .define('L', WoodRecipe.strippedLog()).define('S', WoodRecipe.item(Items.STICK))
                .define('C', WoodRecipe.item(Items.CHAIN)).define('I', WoodRecipe.item(Items.IRON_BLOCK))
                .define('P', WoodRecipe.planks())
                .group(Constants.MOD_ID + ":capstan").unlockedBy("has_chain", WoodRecipe.item(Items.CHAIN)));
    }

    private static void models(ModelContext m) {
        WoodFamily<SailWinchBlock> winches = SailingBlocks.SAIL_WINCHES;
        WoodFamily<CapstanBlock> capstans = SailingBlocks.CAPSTANS;
        // The sail winch: facing is the crank's side; east is the unrotated model, and each further quarter turn
        // clockwise seen from above (vanilla's y rotation, as for the helm) adds 90°. The item shows the block model.
        winches.childModels(m, WINCH_MODEL);
        winches.itemModels(m, WINCH_MODEL);
        winches.forEach((wood, block) -> m.blockStates().accept(MultiVariantGenerator.multiVariant(block,
                        Variant.variant().with(VariantProperties.MODEL, WoodFamily.child(wood, WINCH_MODEL)))
                .with(PropertyDispatch.property(SailWinchBlock.FACING)
                        .select(Direction.EAST, Variant.variant())
                        .select(Direction.SOUTH, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                        .select(Direction.WEST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R180))
                        .select(Direction.NORTH, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R270)))));
        // The capstan (CAP2): block/<wood>_capstan is the base and drum (no facing, every anchor phase the same),
        // block/<wood>_capstan_bars the bars drawn turning by station.capstan.client.CapstanBarsRenderer, the item both
        capstans.childModels(m, CAPSTAN_MODEL);
        capstans.childModels(m, CAPSTAN_BARS_MODEL);
        capstans.itemModels(m, CAPSTAN_ITEM_MODEL);
        capstans.forEach((wood, block) -> m.blockStates().accept(MultiVariantGenerator.multiVariant(block,
                Variant.variant().with(VariantProperties.MODEL, WoodFamily.child(wood, CAPSTAN_MODEL)))));
    }
}

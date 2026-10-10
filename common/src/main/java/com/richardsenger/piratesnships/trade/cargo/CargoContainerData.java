package com.richardsenger.piratesnships.trade.cargo;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import com.richardsenger.piratesnships.ship.decor.wood.WoodRecipe;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;

/**
 * Datagen of the cargo crate and cargo barrel families (WOOD2, docs/woods.md; the blocks are registered in
 * {@code ship.decor.ShipDecor}): lang ("Birch Cargo Crate"), a child of the hand-made parent per wood (one model for
 * every {@code load}: the load is physics only, CW1), loot with the cargo ({@link CargoContainers#lootTable}), tags, and
 * the recipes from that wood's planks.
 *
 * <p>Models ({@code art/models/cargo_crate.bbmodel}, {@code cargo_barrel.bbmodel}): the crate's oak slats and lid are
 * {@code #planks}, its stripped spruce posts {@code #log} with their ends {@code #log_top}; the spruce rails and the dark
 * oak inside stay. The barrel's spruce staves are {@code #planks}; the oak heads and the dark oak bung stay.
 */
public final class CargoContainerData {

    static final ResourceLocation CRATE = Constants.id("block/cargo_crate");
    static final ResourceLocation BARREL = Constants.id("block/cargo_barrel");

    private CargoContainerData() {
    }

    public static void gather(DataContributions data) {
        WoodFamily<CargoContainerBlock> crates = ShipDecor.CARGO_CRATES;
        WoodFamily<CargoContainerBlock> barrels = ShipDecor.CARGO_BARRELS;
        data.lang(lang -> {
            crates.lang(lang, "Cargo Crate");
            barrels.lang(lang, "Cargo Barrel");
        });
        data.models(m -> {
            models(m, crates, CRATE);
            models(m, barrels, BARREL);
        });
        crates.loot(data, CargoContainers::lootTable);
        barrels.loot(data, CargoContainers::lootTable);
        // Wooden containers: like vanilla barrels and chests
        data.blockTags(tags -> {
            crates.tag(tags, BlockTags.MINEABLE_WITH_AXE, SableWeightTags.LIGHT);
            barrels.tag(tags, BlockTags.MINEABLE_WITH_AXE, SableWeightTags.LIGHT);
        });
        crates.recipe(data, WoodRecipe.shaped(RecipeCategory.DECORATIONS, 1, "SPS", "P P", "SPS")
                .define('S', WoodRecipe.item(Items.STICK)).define('P', WoodRecipe.planks())
                .group(Constants.MOD_ID + ":cargo_crate").unlockedBy("has_stick", WoodRecipe.item(Items.STICK)));
        barrels.recipe(data, WoodRecipe.shaped(RecipeCategory.DECORATIONS, 1, "PIP", "P P", "PIP")
                .define('P', WoodRecipe.planks()).define('I', WoodRecipe.item(Items.IRON_NUGGET))
                .group(Constants.MOD_ID + ":cargo_barrel").unlockedBy("has_iron_nugget", WoodRecipe.item(Items.IRON_NUGGET)));
    }

    private static void models(ModelContext m, WoodFamily<CargoContainerBlock> family, ResourceLocation parent) {
        family.childModels(m, parent);
        family.itemModels(m, parent);
        family.forEach((wood, block) -> m.blockStates().accept(MultiVariantGenerator.multiVariant(block, Variant.variant()
                .with(VariantProperties.MODEL, WoodFamily.child(wood, parent)))));
    }
}

package com.richardsenger.piratesnships.apparel;

import com.richardsenger.piratesnships.core.CoreContent;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The {@code apparel} module: wearable hats and the officer's coat for players (design.md §9, §15). Each hat is the hat
 * of a seafarer mob as a hand-made item model ({@code tools/gen_hat_items.py}; the captain's hat from Blockbench, ART6),
 * worn in the head slot with a small armour bonus ({@code apparel.hat_armor}). The officer's coat (ART6) is worn in the
 * chest slot ({@code apparel.officers_coat_armor}) and drawn by vanilla's armour layer.
 */
public final class ApparelModule implements ModModule {

    @Override
    public String id() {
        return "apparel";
    }

    @Override
    public void registerConfig() {
        ApparelConfig.init();
    }

    @Override
    public void registerContent() {
        ApparelContent.init();
        CoreContent.setTabIcon(() -> new ItemStack(ApparelContent.OFFICER_HAT.get()));
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .item(ApparelContent.PIRATE_HAT, "Pirate Hat")
                .item(ApparelContent.BANDANA, "Bandana")
                .item(ApparelContent.NAVY_HAT, "Navy Tricorn")
                .item(ApparelContent.OFFICER_HAT, "Officer's Bicorne")
                .item(ApparelContent.CAPTAINS_HAT, "Captain's Hat")
                .item(ApparelContent.OFFICERS_COAT, "Officer's Coat"));
        // Every apparel item has a hand-made item model (tools/gen_hat_items.py for the four crafted hats, Blockbench
        // projects art/models/captains_hat.bbmodel and officers_coat.bbmodel), so datagen writes none
        data.recipes(out -> {
            // A tricorn: a row of black wool over leather with a bone (the skull) in the middle
            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ApparelContent.PIRATE_HAT.get())
                    .pattern("KKK").pattern("LBL")
                    .define('K', Items.BLACK_WOOL).define('L', Items.LEATHER).define('B', Items.BONE)
                    .unlockedBy("has_leather", InventoryChangeTrigger.TriggerInstance.hasItems(Items.LEATHER))
                    .save(out, ApparelContent.PIRATE_HAT.id());
            // The same tricorn edged with white wool
            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ApparelContent.NAVY_HAT.get())
                    .pattern("KKK").pattern("LWL")
                    .define('K', Items.BLACK_WOOL).define('L', Items.LEATHER).define('W', Items.WHITE_WOOL)
                    .unlockedBy("has_leather", InventoryChangeTrigger.TriggerInstance.hasItems(Items.LEATHER))
                    .save(out, ApparelContent.NAVY_HAT.id());
            // The bicorne: the gold nugget is the loop and the edge
            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ApparelContent.OFFICER_HAT.get())
                    .pattern("KKK").pattern("LGL")
                    .define('K', Items.BLACK_WOOL).define('L', Items.LEATHER).define('G', Items.GOLD_NUGGET)
                    .unlockedBy("has_leather", InventoryChangeTrigger.TriggerInstance.hasItems(Items.LEATHER))
                    .save(out, ApparelContent.OFFICER_HAT.id());
            // Red cloth tied with string
            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ApparelContent.BANDANA.get())
                    .pattern("RSR")
                    .define('R', Items.RED_WOOL).define('S', Items.STRING)
                    .unlockedBy("has_red_wool", InventoryChangeTrigger.TriggerInstance.hasItems(Items.RED_WOOL))
                    .save(out, ApparelContent.BANDANA.id());
            // The officer's coat: blue wool with white facings and a gold epaulette
            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ApparelContent.OFFICERS_COAT.get())
                    .pattern("BGB").pattern("BWB").pattern("BBB")
                    .define('B', Items.BLUE_WOOL).define('W', Items.WHITE_WOOL).define('G', Items.GOLD_INGOT)
                    .unlockedBy("has_blue_wool", InventoryChangeTrigger.TriggerInstance.hasItems(Items.BLUE_WOOL))
                    .save(out, ApparelContent.OFFICERS_COAT.id());
            // The captain's hat has no recipe: the named pirate captain drops it
        });
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(ApparelGameTests.class);
    }
}

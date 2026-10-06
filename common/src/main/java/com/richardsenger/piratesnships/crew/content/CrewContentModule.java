package com.richardsenger.piratesnships.crew.content;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.crew.provisions.ProvisionTags;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import com.richardsenger.piratesnships.trade.content.TradeContent;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.models.model.TexturedModel;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * The {@code crew.content} module: provision items and the pantry and water barrel blocks (design.md §7.4), plus
 * their {@code pirates_n_ships:provisions/*} tags (including rum from {@code trade.content}). The lime has no recipe:
 * it is a fruit that comes from loot and trade (apples and berries already prevent scurvy).
 */
public final class CrewContentModule implements ModModule {

    static final TagKey<Item> C_FOODS = cTag("foods");
    static final TagKey<Item> C_FRUITS = cTag("foods/fruit");
    static final TagKey<Item> C_BREADS = cTag("foods/bread");

    @Override
    public String id() {
        return "crew.content";
    }

    @Override
    public void registerContent() {
        CrewContent.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .item(CrewContent.HARDTACK, "Hardtack")
                .item(CrewContent.SALTED_FISH, "Salted Fish")
                .item(CrewContent.SALT_PORK, "Salt Pork")
                .item(CrewContent.LIME, "Lime")
                .block(CrewContent.PANTRY, "Pantry")
                .block(CrewContent.WATER_BARREL, "Water Barrel"));
        data.models(m -> {
            m.flatItem(CrewContent.HARDTACK.get());
            m.flatItem(CrewContent.SALTED_FISH.get());
            m.flatItem(CrewContent.SALT_PORK.get());
            m.flatItem(CrewContent.LIME.get());
            // cube_column: <name>_side around, <name>_top on top and bottom
            m.blocks().createTrivialBlock(CrewContent.PANTRY.get(), TexturedModel.COLUMN);
            m.blocks().createTrivialBlock(CrewContent.WATER_BARREL.get(), TexturedModel.COLUMN);
        });
        data.blockLoot(loot -> {
            loot.dropSelf(CrewContent.PANTRY.get());
            loot.dropSelf(CrewContent.WATER_BARREL.get());
        });
        data.blockTags(tags -> {
            tags.tag(BlockTags.MINEABLE_WITH_AXE).add(CrewContent.PANTRY.get(), CrewContent.WATER_BARREL.get());
            tags.tag(SableWeightTags.LIGHT).add(CrewContent.PANTRY.get());
            // Full of water: heavier than a wooden block until containers get load-dependent mass (§4.9)
            tags.tag(SableWeightTags.HEAVY).add(CrewContent.WATER_BARREL.get());
        });
        data.itemTags(tags -> {
            tags.tag(ProvisionTags.PRESERVED).add(CrewContent.HARDTACK.get(), CrewContent.SALTED_FISH.get(), CrewContent.SALT_PORK.get());
            tags.tag(ProvisionTags.ANTI_SCURVY).add(CrewContent.LIME.get());
            tags.tag(ProvisionTags.RUM).add(TradeContent.RUM.get());
            tags.tag(ProvisionTags.WATER_BARREL).add(CrewContent.WATER_BARREL.get().asItem());
            tags.tag(C_FOODS).add(CrewContent.HARDTACK.get(), CrewContent.SALTED_FISH.get(), CrewContent.SALT_PORK.get(), CrewContent.LIME.get());
            tags.tag(C_FRUITS).add(CrewContent.LIME.get());
            tags.tag(C_BREADS).add(CrewContent.HARDTACK.get());
        });
        data.recipes(out -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, CrewContent.HARDTACK.get(), 2)
                    .requires(Items.WHEAT, 3).requires(Items.WATER_BUCKET)
                    .unlockedBy("has_wheat", InventoryChangeTrigger.TriggerInstance.hasItems(Items.WHEAT))
                    .save(out, CrewContent.HARDTACK.id());
            // Dried kelp stands in for salt, which vanilla lacks
            ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, CrewContent.SALTED_FISH.get())
                    .requires(Ingredient.of(Items.COD, Items.SALMON)).requires(Items.DRIED_KELP)
                    .unlockedBy("has_dried_kelp", InventoryChangeTrigger.TriggerInstance.hasItems(Items.DRIED_KELP))
                    .save(out, CrewContent.SALTED_FISH.id());
            ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, CrewContent.SALT_PORK.get())
                    .requires(Items.PORKCHOP).requires(Items.DRIED_KELP)
                    .unlockedBy("has_dried_kelp", InventoryChangeTrigger.TriggerInstance.hasItems(Items.DRIED_KELP))
                    .save(out, CrewContent.SALT_PORK.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, CrewContent.PANTRY.get())
                    .pattern("PPP").pattern("PWP").pattern("PPP")
                    .define('P', ItemTags.PLANKS).define('W', Items.WHEAT)
                    .unlockedBy("has_wheat", InventoryChangeTrigger.TriggerInstance.hasItems(Items.WHEAT))
                    .save(out, CrewContent.PANTRY.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, CrewContent.WATER_BARREL.get())
                    .pattern("PIP").pattern("PWP").pattern("PIP")
                    .define('P', ItemTags.PLANKS).define('I', Items.IRON_NUGGET).define('W', Items.WATER_BUCKET)
                    .unlockedBy("has_water_bucket", InventoryChangeTrigger.TriggerInstance.hasItems(Items.WATER_BUCKET))
                    .save(out, CrewContent.WATER_BARREL.id());
        });
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(CrewContentGameTests.class);
    }

    private static TagKey<Item> cTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }
}

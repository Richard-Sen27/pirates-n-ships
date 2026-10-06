package com.richardsenger.piratesnships.trade.content;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The {@code trade.content} module: the doubloon and the trade goods tobacco, spices, cloth and rum. Doubloon,
 * tobacco and spices have no recipe on purpose: they enter the game through loot and markets (§10.2, §10.3).
 * Rum's provisions tag is added by {@code crew.content}.
 */
public final class TradeContentModule implements ModModule {

    static final TagKey<Item> C_DRINKS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "drinks"));

    @Override
    public String id() {
        return "trade.content";
    }

    @Override
    public void registerContent() {
        TradeContent.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .item(TradeContent.DOUBLOON, "Doubloon")
                .item(TradeContent.TOBACCO, "Tobacco")
                .item(TradeContent.SPICES, "Spices")
                .item(TradeContent.CLOTH, "Cloth")
                .item(TradeContent.RUM, "Rum"));
        data.models(m -> {
            m.flatItem(TradeContent.DOUBLOON.get());
            m.flatItem(TradeContent.TOBACCO.get());
            m.flatItem(TradeContent.SPICES.get());
            m.flatItem(TradeContent.CLOTH.get());
            m.flatItem(TradeContent.RUM.get());
        });
        data.itemTags(tags -> tags.tag(C_DRINKS).add(TradeContent.RUM.get()));
        data.recipes(out -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, TradeContent.CLOTH.get())
                    .pattern("SSS")
                    .define('S', Items.STRING)
                    .unlockedBy("has_string", InventoryChangeTrigger.TriggerInstance.hasItems(Items.STRING))
                    .save(out, TradeContent.CLOTH.id());
            ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, TradeContent.RUM.get())
                    .requires(Items.SUGAR_CANE).requires(Items.SUGAR, 2).requires(Items.GLASS_BOTTLE)
                    .unlockedBy("has_sugar_cane", InventoryChangeTrigger.TriggerInstance.hasItems(Items.SUGAR_CANE))
                    .save(out, TradeContent.RUM.id());
        });
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(TradeContentGameTests.class);
    }
}

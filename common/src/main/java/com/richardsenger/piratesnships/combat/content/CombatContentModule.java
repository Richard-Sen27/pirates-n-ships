package com.richardsenger.piratesnships.combat.content;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/** The {@code combat.content} module: swords, firearms, ammunition, cannonball, grappling hook (design.md §8.1). */
public final class CombatContentModule implements ModModule {

    static final TagKey<Item> C_MELEE_WEAPONS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools/melee_weapon"));
    static final TagKey<Item> C_RANGED_WEAPONS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools/ranged_weapon"));

    @Override
    public String id() {
        return "combat.content";
    }

    @Override
    public void registerContent() {
        CombatContent.init();
        CombatSounds.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        CombatSounds.gather(data);
        data.lang(lang -> lang
                .item(CombatContent.RAPIER, "Rapier")
                .item(CombatContent.CUTLASS, "Cutlass")
                .item(CombatContent.SABER, "Saber")
                .item(CombatContent.PISTOL, "Pistol")
                .item(CombatContent.MUSKET, "Musket")
                .item(CombatContent.LEAD_SHOT, "Lead Shot")
                .item(CombatContent.CANNONBALL, "Cannonball")
                .item(CombatContent.GRAPPLING_HOOK, "Grappling Hook"));
        data.models(m -> {
            m.handheldItem(CombatContent.PISTOL.get());
            m.handheldItem(CombatContent.MUSKET.get());
            m.flatItem(CombatContent.LEAD_SHOT.get());
            m.flatItem(CombatContent.CANNONBALL.get());
            m.flatItem(CombatContent.GRAPPLING_HOOK.get());
        });
        data.itemTags(tags -> {
            tags.tag(ItemTags.SWORDS).add(CombatContent.RAPIER.get(), CombatContent.CUTLASS.get(), CombatContent.SABER.get());
            tags.tag(C_MELEE_WEAPONS).add(CombatContent.RAPIER.get(), CombatContent.CUTLASS.get(), CombatContent.SABER.get());
            tags.tag(C_RANGED_WEAPONS).add(CombatContent.PISTOL.get(), CombatContent.MUSKET.get());
        });
        data.recipes(out -> {
            Item rapier = CombatContent.RAPIER.get();
            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, rapier)
                    .pattern("  I").pattern(" I ").pattern("S  ")
                    .define('I', Items.IRON_INGOT).define('S', Items.STICK)
                    .unlockedBy("has_iron_ingot", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT))
                    .save(out, CombatContent.RAPIER.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, CombatContent.CUTLASS.get())
                    .pattern(" II").pattern(" I ").pattern("S  ")
                    .define('I', Items.IRON_INGOT).define('S', Items.STICK)
                    .unlockedBy("has_iron_ingot", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT))
                    .save(out, CombatContent.CUTLASS.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, CombatContent.SABER.get())
                    .pattern("  I").pattern("GI ").pattern("S  ")
                    .define('I', Items.IRON_INGOT).define('G', Items.GOLD_INGOT).define('S', Items.STICK)
                    .unlockedBy("has_iron_ingot", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT))
                    .save(out, CombatContent.SABER.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, CombatContent.PISTOL.get())
                    .pattern("II").pattern("FP")
                    .define('I', Items.IRON_INGOT).define('F', Items.FLINT).define('P', ItemTags.PLANKS)
                    .unlockedBy("has_flint", InventoryChangeTrigger.TriggerInstance.hasItems(Items.FLINT))
                    .save(out, CombatContent.PISTOL.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, CombatContent.MUSKET.get())
                    .pattern("I  ").pattern(" IF").pattern("  P")
                    .define('I', Items.IRON_INGOT).define('F', Items.FLINT).define('P', ItemTags.PLANKS)
                    .unlockedBy("has_flint", InventoryChangeTrigger.TriggerInstance.hasItems(Items.FLINT))
                    .save(out, CombatContent.MUSKET.id());
            ShapelessRecipeBuilder.shapeless(RecipeCategory.COMBAT, CombatContent.LEAD_SHOT.get(), 4)
                    .requires(Items.IRON_NUGGET, 2)
                    .unlockedBy("has_iron_nugget", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_NUGGET))
                    .save(out, CombatContent.LEAD_SHOT.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, CombatContent.CANNONBALL.get(), 2)
                    .pattern("II").pattern("II")
                    .define('I', Items.IRON_INGOT)
                    .unlockedBy("has_iron_ingot", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT))
                    .save(out, CombatContent.CANNONBALL.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, CombatContent.GRAPPLING_HOOK.get())
                    .pattern("I I").pattern(" I ").pattern(" S ")
                    .define('I', Items.IRON_INGOT).define('S', Items.STRING)
                    .unlockedBy("has_string", InventoryChangeTrigger.TriggerInstance.hasItems(Items.STRING))
                    .save(out, CombatContent.GRAPPLING_HOOK.id());
        });
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(CombatContentGameTests.class);
    }
}

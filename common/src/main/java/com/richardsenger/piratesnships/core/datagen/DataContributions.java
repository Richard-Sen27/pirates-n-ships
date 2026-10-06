package com.richardsenger.piratesnships.core.datagen;

import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * What a module contributes to datagen. Passed to {@code ModModule.gatherData}; each method takes a callback that
 * runs when the matching provider runs. All providers use vanilla datagen only, so this works on every loader.
 *
 * <pre>{@code
 * data.lang(lang -> lang.block(CoreContent.TEST_BLOCK, "Test Block"));
 * data.models(m -> m.blocks().createTrivialCube(CoreContent.TEST_BLOCK.get()));
 * data.blockLoot(loot -> loot.dropSelf(CoreContent.TEST_BLOCK.get()));
 * data.blockTags(tags -> tags.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(CoreContent.TEST_BLOCK.get()));
 * data.recipes(out -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ...).save(out));
 * }</pre>
 */
public final class DataContributions {

    final List<Consumer<LangBuilder>> lang = new ArrayList<>();
    final List<Consumer<ModelContext>> models = new ArrayList<>();
    final List<Consumer<RecipeOutput>> recipes = new ArrayList<>();
    final List<Consumer<ModBlockLoot>> blockLoot = new ArrayList<>();
    final List<Consumer<ModTagsProvider<Block>>> blockTags = new ArrayList<>();
    final List<Consumer<ModTagsProvider<Item>>> itemTags = new ArrayList<>();

    /** English lang entries ({@code en_us.json}). */
    public void lang(Consumer<LangBuilder> c) { lang.add(c); }

    /** Block states, block models and item models. */
    public void models(Consumer<ModelContext> c) { models.add(c); }

    /** Recipes (and their unlock advancements). */
    public void recipes(Consumer<RecipeOutput> c) { recipes.add(c); }

    /** Block loot tables. Every block of the mod that drops something needs one. */
    public void blockLoot(Consumer<ModBlockLoot> c) { blockLoot.add(c); }

    /** Block tags. */
    public void blockTags(Consumer<ModTagsProvider<Block>> c) { blockTags.add(c); }

    /** Item tags. */
    public void itemTags(Consumer<ModTagsProvider<Item>> c) { itemTags.add(c); }
}

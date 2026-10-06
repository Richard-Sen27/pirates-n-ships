package com.richardsenger.piratesnships.core.datagen;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.core.data.DefinitionType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * What a module contributes to datagen. Passed to {@code ModModule.gatherData}; each method takes a callback that
 * runs when the matching provider runs. All providers use vanilla datagen only, so this works on every loader.
 *
 * <pre>{@code
 * data.lang(lang -> lang.block(CoreContent.TEST_BLOCK, "Test Block"));
 * data.models(m -> m.blocks().createTrivialCube(CoreContent.TEST_BLOCK.get()));
 * data.blockLoot(loot -> loot.dropSelf(CoreContent.TEST_BLOCK.get()));
 * data.blockTags(tags -> tags.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(CoreContent.TEST_BLOCK.get()));
 * data.entityTypeTags(tags -> tags.tag(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("sable", "retain_in_sub_level"))).add(MY_ENTITY.get()));
 * data.recipes(out -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ...).save(out));
 * data.json(PackOutput.Target.DATA_PACK, "physics_block_properties", Constants.id("plank"), () -> json);
 * data.definitions(CoreDefinitions.TEST_MARKER, Map.of(Constants.id("example"), new TestMarker("example", 3)));
 * }</pre>
 */
public final class DataContributions {

    final List<Consumer<LangBuilder>> lang = new ArrayList<>();
    final List<Consumer<ModelContext>> models = new ArrayList<>();
    final List<Consumer<RecipeOutput>> recipes = new ArrayList<>();
    final List<Consumer<ModBlockLoot>> blockLoot = new ArrayList<>();
    /** Tag contributors per built-in registry, in first-use order (block and item always exist). */
    final Map<ResourceKey<? extends Registry<?>>, List<Consumer<?>>> tags = new LinkedHashMap<>();
    final JsonOutputs json = new JsonOutputs();

    public DataContributions() {
        tags.put(Registries.BLOCK, new ArrayList<>());
        tags.put(Registries.ITEM, new ArrayList<>());
    }

    /** English lang entries ({@code en_us.json}). */
    public void lang(Consumer<LangBuilder> c) { lang.add(c); }

    /** Block states, block models and item models. */
    public void models(Consumer<ModelContext> c) { models.add(c); }

    /** Recipes (and their unlock advancements). */
    public void recipes(Consumer<RecipeOutput> c) { recipes.add(c); }

    /** Block loot tables. Every block of the mod that drops something needs one. */
    public void blockLoot(Consumer<ModBlockLoot> c) { blockLoot.add(c); }

    /** Block tags. */
    public void blockTags(Consumer<ModTagsProvider<Block>> c) { tags(Registries.BLOCK, c); }

    /** Item tags. */
    public void itemTags(Consumer<ModTagsProvider<Item>> c) { tags(Registries.ITEM, c); }

    /** Entity type tags (e.g. {@code #sable:retain_in_sub_level}). */
    public void entityTypeTags(Consumer<ModTagsProvider<EntityType<?>>> c) { tags(Registries.ENTITY_TYPE, c); }

    /**
     * Tags for any <b>built-in</b> registry (blocks, items, entity types, block entity types, fluids, sounds, ...).
     * Tag ids may use any namespace ({@code c:}, {@code sable:}, {@code minecraft:}). Datapack registries
     * (biomes, enchantments, ...) are not supported here.
     */
    public <T> void tags(ResourceKey<? extends Registry<T>> registry, Consumer<ModTagsProvider<T>> c) {
        tags.computeIfAbsent(registry, k -> new ArrayList<>()).add(c);
    }

    /**
     * An arbitrary JSON file at {@code <pack>/<id namespace>/<directory>/<id path>.json}, where pack is
     * {@code data} or {@code assets}. Use for formats without a vanilla provider (e.g. Sable's
     * {@code physics_block_properties}). Two contributions with the same output path fail the data run.
     */
    public void json(PackOutput.Target target, String directory, ResourceLocation id, Supplier<JsonElement> json) {
        this.json.add(target, directory, id, registries -> json.get());
    }

    /** Like {@link #json}, but encodes {@code value} with {@code codec} (registry-aware ops). */
    public <T> void encoded(PackOutput.Target target, String directory, ResourceLocation id, Codec<T> codec, T value) {
        this.json.add(target, directory, id, registries -> JsonOutputs.encode(codec, value, registries));
    }

    /** Default entries of a datapack definition type, written to {@code data/<ns>/pirates_n_ships/<type>/<path>.json}. */
    public <T> void definitions(DefinitionType<T> type, Map<ResourceLocation, ? extends T> entries) {
        entries.forEach((id, value) -> definition(type, id, value));
    }

    /** One default entry of a datapack definition type (see {@link #definitions}). */
    public <T> void definition(DefinitionType<T> type, ResourceLocation id, T value) {
        encoded(PackOutput.Target.DATA_PACK, type.directory(), id, type.codec(), value);
    }
}

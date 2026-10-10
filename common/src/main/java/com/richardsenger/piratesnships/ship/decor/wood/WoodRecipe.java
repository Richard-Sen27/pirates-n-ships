package com.richardsenger.piratesnships.ship.decor.wood;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * One recipe of a wood family, written once for every wood (WOOD1, docs/woods.md): a shaped or shapeless recipe whose
 * ingredients may be "this wood's planks / log / stripped log" or "this wood's member of another family".
 *
 * <p>A vanilla wood's recipe goes through vanilla's {@link ShapedRecipeBuilder}/{@link ShapelessRecipeBuilder}, as
 * before. A mod's wood cannot use the builders (its planks are not registered in a data run), so its recipe and unlock
 * advancement are built from the same vanilla classes with a stand-in item per wood ingredient ({@link #STAND_INS}),
 * encoded with the vanilla codecs, the stand-in ids replaced by the mod's ids, and both loaders' conditions added
 * ({@link WoodConditions}): the files load only with the mod.
 */
public final class WoodRecipe {

    /** Which item of a wood an ingredient is. */
    public enum WoodItem { PLANKS, LOG, STRIPPED_LOG }

    /** An ingredient: a fixed item, an item of the recipe's wood, or the wood's member of a family. */
    public sealed interface Part permits Fixed, OfWood, Member, Tagged {
    }

    record Fixed(Supplier<? extends ItemLike> item) implements Part {
    }

    record OfWood(WoodItem item) implements Part {
    }

    record Member(WoodFamily<?> family) implements Part {
    }

    /** Any item of a tag (WOOD3: the sea cot's wool); never an unlock condition. */
    record Tagged(TagKey<Item> tag) implements Part {
    }

    /** Stand-ins of a mod's wood items while its recipe is encoded (never ingredients of a family recipe themselves). */
    static final Map<WoodItem, Item> STAND_INS = Map.of(WoodItem.PLANKS, Items.BARRIER, WoodItem.LOG, Items.STRUCTURE_VOID,
            WoodItem.STRIPPED_LOG, Items.DEBUG_STICK);

    private final RecipeCategory category;
    private final int count;
    private final List<String> pattern;
    private final Map<Character, Part> key = new LinkedHashMap<>();
    private final List<Part> ingredients = new ArrayList<>();
    private String group;
    private String unlockName;
    private Part unlock;

    private WoodRecipe(RecipeCategory category, int count, List<String> pattern) {
        this.category = category;
        this.count = count;
        this.pattern = pattern;
    }

    public static Part planks() {
        return new OfWood(WoodItem.PLANKS);
    }

    public static Part log() {
        return new OfWood(WoodItem.LOG);
    }

    public static Part strippedLog() {
        return new OfWood(WoodItem.STRIPPED_LOG);
    }

    public static Part item(ItemLike item) {
        return new Fixed(() -> item);
    }

    public static Part member(WoodFamily<?> family) {
        return new Member(family);
    }

    /** Any item of {@code tag} (WOOD3), the same in every wood's recipe. */
    public static Part tag(TagKey<Item> tag) {
        return new Tagged(tag);
    }

    /** A shaped recipe giving {@code count} with the rows of {@code pattern}. */
    public static WoodRecipe shaped(RecipeCategory category, int count, String... pattern) {
        return new WoodRecipe(category, count, List.of(pattern));
    }

    /** A shapeless recipe giving {@code count}. */
    public static WoodRecipe shapeless(RecipeCategory category, int count) {
        return new WoodRecipe(category, count, List.of());
    }

    public WoodRecipe define(char symbol, Part part) {
        key.put(symbol, part);
        return this;
    }

    public WoodRecipe requires(Part part) {
        ingredients.add(part);
        return this;
    }

    public WoodRecipe group(String group) {
        this.group = group;
        return this;
    }

    public WoodRecipe unlockedBy(String name, Part part) {
        this.unlockName = name;
        this.unlock = part;
        return this;
    }

    boolean shapeless() {
        return pattern.isEmpty();
    }

    /** The item of {@code part} for {@code wood}: a stand-in for a mod wood's own items when {@code standIns}. */
    private static ItemLike resolve(Part part, ShipWood wood, boolean standIns) {
        return switch (part) {
            case Fixed f -> f.item().get();
            case Member m -> m.family().get(wood);
            case Tagged t -> throw new IllegalArgumentException("a tag is no single item: " + t.tag().location());
            case OfWood o -> standIns ? STAND_INS.get(o.item()) : switch (o.item()) {
                case PLANKS -> wood.planksItem();
                case LOG -> wood.log();
                case STRIPPED_LOG -> wood.strippedLog();
            };
        };
    }

    /** The ingredient of {@code part} for {@code wood}: a tag's items, or {@link #resolve the one item}. */
    private static Ingredient ingredient(Part part, ShipWood wood, boolean standIns) {
        return part instanceof Tagged t ? Ingredient.of(t.tag()) : Ingredient.of(resolve(part, wood, standIns));
    }

    /** The mod ids a stand-in turns into for {@code wood}. */
    static Map<String, String> replacements(ShipWood wood) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put(id(STAND_INS.get(WoodItem.PLANKS)), wood.planksId().toString());
        out.put(id(STAND_INS.get(WoodItem.LOG)), wood.logId().toString());
        out.put(id(STAND_INS.get(WoodItem.STRIPPED_LOG)), wood.strippedLogId().toString());
        return out;
    }

    /** The wood items the recipe of a mod's wood needs (its item-exists conditions). */
    Set<ResourceLocation> woodItems(ShipWood wood) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        List<Part> parts = new ArrayList<>(key.values());
        parts.addAll(ingredients);
        for (Part p : parts) {
            if (p instanceof OfWood o) out.add(switch (o.item()) {
                case PLANKS -> wood.planksId();
                case LOG -> wood.logId();
                case STRIPPED_LOG -> wood.strippedLogId();
            });
        }
        return out;
    }

    private static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** A vanilla wood's recipe through vanilla's builders, saved to {@code out} as {@code id}. */
    void saveVanilla(RecipeOutput out, ShipWood wood, Block result, ResourceLocation id) {
        if (shapeless()) {
            ShapelessRecipeBuilder b = ShapelessRecipeBuilder.shapeless(category, result, count);
            for (Part p : ingredients) b.requires(ingredient(p, wood, false));
            b.group(group).unlockedBy(unlockName, InventoryChangeTrigger.TriggerInstance.hasItems(resolve(unlock, wood, false))).save(out, id);
        } else {
            ShapedRecipeBuilder b = ShapedRecipeBuilder.shaped(category, result, count);
            for (String row : pattern) b.pattern(row);
            key.forEach((c, p) -> b.define(c, ingredient(p, wood, false)));
            b.group(group).unlockedBy(unlockName, InventoryChangeTrigger.TriggerInstance.hasItems(resolve(unlock, wood, false))).save(out, id);
        }
    }

    /** The recipe of a mod's wood with stand-ins (what the builders would make). */
    Recipe<?> standInRecipe(ShipWood wood, Block result) {
        ItemStack stack = new ItemStack(result, count);
        String g = group == null ? "" : group;
        if (shapeless()) {
            NonNullList<Ingredient> list = NonNullList.create();
            for (Part p : ingredients) list.add(ingredient(p, wood, true));
            return new ShapelessRecipe(g, RecipeBuilder.determineBookCategory(category), stack, list);
        }
        Map<Character, Ingredient> k = new LinkedHashMap<>();
        key.forEach((c, p) -> k.put(c, ingredient(p, wood, true)));
        return new ShapedRecipe(g, RecipeBuilder.determineBookCategory(category), ShapedRecipePattern.of(k, pattern), stack, true);
    }

    /** The unlock advancement of the recipe {@code id} of a mod's wood with stand-ins (what the builders would make). */
    AdvancementHolder standInAdvancement(ShipWood wood, ResourceLocation id) {
        return Advancement.Builder.recipeAdvancement().parent(RecipeBuilder.ROOT_RECIPE_ADVANCEMENT)
                .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(id))
                .rewards(AdvancementRewards.Builder.recipe(id))
                .requirements(AdvancementRequirements.Strategy.OR)
                .addCriterion(unlockName, InventoryChangeTrigger.TriggerInstance.hasItems(resolve(unlock, wood, true)))
                .build(advancementId(id));
    }

    /** Where vanilla's builders put the unlock advancement of {@code id}: {@code recipes/<category folder>/<path>}. */
    ResourceLocation advancementId(ResourceLocation id) {
        return id.withPrefix("recipes/" + category.getFolderName() + "/");
    }

    private static HolderLookup.Provider lookup;

    /**
     * The built-in registries for encoding (item predicates need registry access, and only the built-in item
     * registry's own holders pass the owner check; the family files reference no data-pack registry).
     */
    static synchronized HolderLookup.Provider lookup() {
        if (lookup == null) lookup = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        return lookup;
    }

    /** {@code value} encoded with {@code codec} and registry access, stand-ins replaced, both loaders' conditions added. */
    static <T> JsonObject encodeForMod(com.mojang.serialization.Codec<T> codec, T value, ShipWood wood, Set<ResourceLocation> items) {
        JsonElement json = codec.encodeStart(RegistryOps.create(JsonOps.INSTANCE, lookup()), value)
                .getOrThrow(msg -> new IllegalStateException("Failed to encode " + value + " for " + wood + ": " + msg));
        JsonObject out = replace(json, replacements(wood)).getAsJsonObject();
        WoodConditions.add(out, wood.modId(), items);
        return out;
    }

    /** {@code json} with every string equal to a key of {@code ids} replaced by its value. */
    static JsonElement replace(JsonElement json, Map<String, String> ids) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            String s = json.getAsString();
            return ids.containsKey(s) ? new JsonPrimitive(ids.get(s)) : json;
        }
        if (json.isJsonArray()) {
            JsonArray a = new JsonArray();
            for (JsonElement e : json.getAsJsonArray()) a.add(replace(e, ids));
            return a;
        }
        if (json.isJsonObject()) {
            JsonObject o = new JsonObject();
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) o.add(e.getKey(), replace(e.getValue(), ids));
            return o;
        }
        return json;
    }
}

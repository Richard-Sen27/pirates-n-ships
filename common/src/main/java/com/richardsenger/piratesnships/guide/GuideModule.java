package com.richardsenger.piratesnships.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The {@code guide} module: docs/guide.md as an in-game book through GuideME, an optional NeoForge-only dependency.
 *
 * <ul>
 *   <li>The guide is resources only: {@code tools/gen_guideme.py} writes the guide definition
 *       ({@code assets/pirates_n_ships/guideme_guides/guide.json}) and the pages
 *       ({@code assets/pirates_n_ships/guides/pirates_n_ships/guide/*.md}); this module writes nothing of it.</li>
 *   <li>Datagen: a shapeless recipe (book + feather) for GuideME's guide item carrying our guide id in its result
 *       components, its recipe-book unlock, and the lang of the book's name and tooltip. Recipe and advancement load
 *       only while GuideME is present ({@code neoforge:conditions} / {@code fabric:load_conditions}, plain JSON keys:
 *       nothing here imports a loader or GuideME).</li>
 *   <li>A new player gets the book once ({@link GuideGift}, server config {@code guide.give_on_first_join}).</li>
 * </ul>
 */
public final class GuideModule implements ModModule {

    /** The book recipe and its unlock advancement. */
    public static final ResourceLocation RECIPE_ID = Constants.id("guide_book");

    @Override
    public String id() {
        return "guide";
    }

    @Override
    public void registerConfig() {
        GuideConfig.init();
    }

    @Override
    public void registerContent() {
        GuideGift.init();
    }

    @Override
    public void registerEvents() {
        CommonEvents.PLAYER_LOGIN.register(GuideGift::onLogin);
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .add(GuideBook.NAME_KEY, "Pirates 'n' Ships Guide")
                .add(GuideBook.TOOLTIP_KEY, "Ships, crew, trade and the law, explained"));
        data.json(PackOutput.Target.DATA_PACK, "recipe", RECIPE_ID, GuideModule::recipe);
        data.json(PackOutput.Target.DATA_PACK, "advancement/recipes/misc", RECIPE_ID, GuideModule::advancement);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(GuideGameTests.class);
    }

    /** Book + feather (shapeless) = one guide book. */
    static JsonObject recipe() {
        JsonObject json = new JsonObject();
        conditions(json);
        json.addProperty("type", "minecraft:crafting_shapeless");
        json.addProperty("category", "misc");
        JsonArray ingredients = new JsonArray();
        ingredients.add(item("minecraft:book"));
        ingredients.add(item("minecraft:feather"));
        json.add("ingredients", ingredients);
        json.add("result", GuideBook.stackJson());
        return json;
    }

    /** Unlocks the recipe in the recipe book once the player holds a book. */
    static JsonObject advancement() {
        JsonObject json = new JsonObject();
        conditions(json);
        json.addProperty("parent", "minecraft:recipes/root");
        JsonObject hasBook = new JsonObject();
        JsonObject hasBookConditions = new JsonObject();
        JsonArray items = new JsonArray();
        JsonObject book = new JsonObject();
        book.addProperty("items", "minecraft:book");
        items.add(book);
        hasBookConditions.add("items", items);
        hasBook.add("conditions", hasBookConditions);
        hasBook.addProperty("trigger", "minecraft:inventory_changed");
        JsonObject hasRecipe = new JsonObject();
        JsonObject hasRecipeConditions = new JsonObject();
        hasRecipeConditions.addProperty("recipe", RECIPE_ID.toString());
        hasRecipe.add("conditions", hasRecipeConditions);
        hasRecipe.addProperty("trigger", "minecraft:recipe_unlocked");
        JsonObject criteria = new JsonObject();
        criteria.add("has_book", hasBook);
        criteria.add("has_the_recipe", hasRecipe);
        json.add("criteria", criteria);
        JsonArray requirement = new JsonArray();
        requirement.add("has_the_recipe");
        requirement.add("has_book");
        JsonArray requirements = new JsonArray();
        requirements.add(requirement);
        json.add("requirements", requirements);
        JsonObject rewards = new JsonObject();
        JsonArray recipes = new JsonArray();
        recipes.add(RECIPE_ID.toString());
        rewards.add("recipes", recipes);
        json.add("rewards", rewards);
        return json;
    }

    /**
     * Loads the file only while GuideME is installed, on either loader: NeoForge's {@code neoforge:mod_loaded}
     * condition (ConditionalOps.DEFAULT_CONDITIONS_KEY, ModLoadedCondition "modid") and Fabric API's
     * {@code fabric:all_mods_loaded} resource condition. Each loader ignores the other's key.
     */
    private static void conditions(JsonObject json) {
        JsonObject neo = new JsonObject();
        neo.addProperty("type", "neoforge:mod_loaded");
        neo.addProperty("modid", GuideBook.GUIDEME);
        JsonArray neoList = new JsonArray();
        neoList.add(neo);
        json.add("neoforge:conditions", neoList);
        JsonObject fabric = new JsonObject();
        fabric.addProperty("condition", "fabric:all_mods_loaded");
        JsonArray mods = new JsonArray();
        mods.add(GuideBook.GUIDEME);
        fabric.add("values", mods);
        JsonArray fabricList = new JsonArray();
        fabricList.add(fabric);
        json.add("fabric:load_conditions", fabricList);
    }

    private static JsonElement item(String id) {
        JsonObject json = new JsonObject();
        json.addProperty("item", id);
        return json;
    }
}

package com.richardsenger.piratesnships.almanac.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * What a recipe panel (ALM8) shows of one recipe, read from a level's recipe manager (on the client: the recipes the
 * server synced, so a datapack's change shows). Shaped and shapeless crafting fill the 3x3 grid ({@link RecipeGrid});
 * smelting, blasting, smoking and campfire cooking show their one input over a flame; the stonecutter its one input;
 * any other type only its result. Each input is the list of stacks its ingredient accepts (the panel cycles them).
 *
 * @param kind   how the panel draws it
 * @param inputs nine slots for crafting, one for cooking and the stonecutter, none otherwise
 * @param result the result with its count, or empty when there is no such recipe
 */
public record RecipeView(RecipeGrid.Kind kind, List<List<ItemStack>> inputs, ItemStack result) {

    /** No recipe of that id. */
    public static final RecipeView NONE = new RecipeView(RecipeGrid.Kind.OTHER, List.of(), ItemStack.EMPTY);

    public RecipeView {
        inputs = List.copyOf(inputs);
    }

    /**
     * WOOD1: the recipes a panel of {@code id} shows in turn: every member of {@code id}'s wood family whose recipe is
     * in the world (in {@code ShipWood.shipped()} order), or {@code id} alone when it is no family member.
     */
    public static List<ResourceLocation> family(Level level, ResourceLocation id) {
        if (!id.getNamespace().equals(Constants.MOD_ID)) return List.of(id);
        WoodFamily<?> family = WoodFamily.ofPath(id.getPath());
        if (family == null) return List.of(id);
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation member : family.ids()) {
            if (level.getRecipeManager().byKey(member).isPresent()) out.add(member);
        }
        return out.isEmpty() ? List.of(id) : out;
    }

    public static RecipeView of(Level level, ResourceLocation id) {
        Optional<RecipeHolder<?>> holder = level.getRecipeManager().byKey(id);
        if (holder.isEmpty()) return NONE;
        Recipe<?> recipe = holder.get().value();
        ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
        List<Ingredient> ingredients = recipe.getIngredients();
        if (recipe instanceof ShapedRecipe shaped && shaped.getWidth() <= 3 && shaped.getHeight() <= 3
                && ingredients.size() == shaped.getWidth() * shaped.getHeight()) {
            List<List<ItemStack>> grid = emptyGrid();
            for (int i = 0; i < ingredients.size(); i++) {
                grid.set(RecipeGrid.shapedSlot(i, shaped.getWidth(), shaped.getHeight()), stacks(ingredients.get(i)));
            }
            return new RecipeView(RecipeGrid.Kind.SHAPED, grid, result);
        }
        if (recipe instanceof ShapelessRecipe && !ingredients.isEmpty() && ingredients.size() <= 9) {
            List<List<ItemStack>> grid = emptyGrid();
            for (int i = 0; i < ingredients.size(); i++) {
                grid.set(RecipeGrid.shapelessSlot(i, ingredients.size()), stacks(ingredients.get(i)));
            }
            return new RecipeView(RecipeGrid.Kind.SHAPELESS, grid, result);
        }
        if (recipe instanceof AbstractCookingRecipe && !ingredients.isEmpty()) {
            return new RecipeView(RecipeGrid.Kind.COOKING, List.of(stacks(ingredients.getFirst())), result);
        }
        if (recipe instanceof SingleItemRecipe && !ingredients.isEmpty()) {
            return new RecipeView(RecipeGrid.Kind.SINGLE, List.of(stacks(ingredients.getFirst())), result);
        }
        return new RecipeView(RecipeGrid.Kind.OTHER, List.of(), result);
    }

    private static List<List<ItemStack>> emptyGrid() {
        List<List<ItemStack>> grid = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) grid.add(List.of());
        return grid;
    }

    private static List<ItemStack> stacks(Ingredient ingredient) {
        return List.copyOf(Arrays.asList(ingredient.getItems()));
    }
}

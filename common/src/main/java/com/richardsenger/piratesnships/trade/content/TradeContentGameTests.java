package com.richardsenger.piratesnships.trade.content;

import com.richardsenger.piratesnships.combat.content.ContentTestSupport;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;

/** Registration and recipes of the {@code trade.content} module. */
public final class TradeContentGameTests {

    public static final List<String> BLOCK_IDS = List.of();
    public static final List<String> ITEM_IDS = List.of("doubloon", "tobacco", "spices", "cloth", "rum");

    private TradeContentGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(TradeContentGameTests.class);
    }

    @ModGameTest
    public static void tradeItemsAreRegistered(GameTestHelper helper) {
        ContentTestSupport.assertRegistered(helper, ITEM_IDS, List.of());
        helper.succeed();
    }

    @ModGameTest
    public static void doubloonHasNoRecipe(GameTestHelper helper) {
        boolean crafted = helper.getLevel().getRecipeManager().getRecipes().stream()
                .anyMatch(r -> r.value().getResultItem(helper.getLevel().registryAccess()).is(TradeContent.DOUBLOON.get()));
        helper.assertFalse(crafted, "the doubloon must not be craftable");
        helper.succeed();
    }

    @ModGameTest
    public static void tradeRecipesAreLoaded(GameTestHelper helper) {
        ContentTestSupport.assertRecipe(helper, "cloth", TradeContent.CLOTH.get(), 1);
        ContentTestSupport.assertRecipe(helper, "rum", TradeContent.RUM.get(), 1);
        helper.succeed();
    }

    @ModGameTest
    public static void rumIsADrinkThatReturnsTheBottle(GameTestHelper helper) {
        ItemStack rum = new ItemStack(TradeContent.RUM.get());
        var food = rum.get(DataComponents.FOOD);
        helper.assertTrue(food != null && food.canAlwaysEat(), "rum should be always drinkable");
        helper.assertTrue(food.usingConvertsTo().map(s -> s.is(net.minecraft.world.item.Items.GLASS_BOTTLE)).orElse(false),
                "rum should leave a glass bottle, got " + food.usingConvertsTo());
        helper.assertTrue(rum.is(TradeContentModule.C_DRINKS), BuiltInRegistries.ITEM.getKey(rum.getItem()) + " not in #c:drinks");
        helper.succeed();
    }
}

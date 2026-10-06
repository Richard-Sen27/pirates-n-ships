package com.richardsenger.piratesnships.crew.content;

import com.richardsenger.piratesnships.combat.content.ContentTestSupport;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.provisions.ProvisionClassifier;
import com.richardsenger.piratesnships.crew.provisions.ProvisionKind;
import com.richardsenger.piratesnships.crew.provisions.ProvisionSettings;
import com.richardsenger.piratesnships.crew.provisions.ProvisionTags;
import com.richardsenger.piratesnships.crew.provisions.ProvisionType;
import com.richardsenger.piratesnships.trade.content.TradeContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Registration, food, provisions tags and classification, blocks and recipes of the {@code crew.content} module. */
public final class CrewContentGameTests {

    public static final List<String> ITEM_IDS = List.of("hardtack", "salted_fish", "salt_pork", "lime");
    public static final List<String> BLOCK_IDS = List.of("pantry", "water_barrel");

    private CrewContentGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CrewContentGameTests.class);
    }

    @ModGameTest
    public static void crewContentIsRegistered(GameTestHelper helper) {
        ContentTestSupport.assertRegistered(helper, ITEM_IDS, BLOCK_IDS);
        helper.succeed();
    }

    @ModGameTest
    public static void foodsHaveFoodProperties(GameTestHelper helper) {
        assertFood(helper, CrewContent.HARDTACK.get(), CrewContent.HARDTACK_FOOD);
        assertFood(helper, CrewContent.SALTED_FISH.get(), CrewContent.SALTED_FISH_FOOD);
        assertFood(helper, CrewContent.SALT_PORK.get(), CrewContent.SALT_PORK_FOOD);
        assertFood(helper, CrewContent.LIME.get(), CrewContent.LIME_FOOD);
        assertFood(helper, TradeContent.RUM.get(), TradeContent.RUM_FOOD);
        helper.succeed();
    }

    @ModGameTest
    public static void provisionsAreTaggedAndClassified(GameTestHelper helper) {
        ProvisionSettings s = ProvisionSettings.DEFAULTS;
        for (Item preserved : List.of(CrewContent.HARDTACK.get(), CrewContent.SALTED_FISH.get(), CrewContent.SALT_PORK.get())) {
            ItemStack stack = new ItemStack(preserved);
            helper.assertTrue(stack.is(ProvisionTags.PRESERVED), preserved + " is not in provisions/preserved");
            ProvisionType type = classify(helper, stack, s);
            helper.assertTrue(type.kind() == ProvisionKind.FOOD && type.preserved() && !type.perishable(), preserved + " should be preserved food, got " + type);
        }
        ItemStack lime = new ItemStack(CrewContent.LIME.get());
        helper.assertTrue(lime.is(ProvisionTags.ANTI_SCURVY), "lime is not in provisions/anti_scurvy");
        ProvisionType limeType = classify(helper, lime, s);
        helper.assertTrue(limeType.kind() == ProvisionKind.FOOD && limeType.antiScurvy(), "lime should be anti-scurvy food, got " + limeType);

        ItemStack rum = new ItemStack(TradeContent.RUM.get());
        helper.assertTrue(rum.is(ProvisionTags.RUM), "rum is not in provisions/rum");
        helper.assertTrue(classify(helper, rum, s).kind() == ProvisionKind.RUM, "rum should classify as rum");
        helper.assertTrue(ProvisionClassifier.leftover(rum).is(net.minecraft.world.item.Items.GLASS_BOTTLE), "rum should leave a bottle");

        ItemStack barrel = new ItemStack(CrewContent.WATER_BARREL.get());
        helper.assertTrue(barrel.is(ProvisionTags.WATER_BARREL), "water barrel is not in provisions/water_barrel");
        ProvisionType water = classify(helper, barrel, s);
        helper.assertTrue(water.kind() == ProvisionKind.WATER && water.valuePerUnit() == s.waterBarrelRations(),
                "water barrel should be " + s.waterBarrelRations() + " water rations, got " + water);
        helper.succeed();
    }

    @ModGameTest
    public static void crewBlocksPlaceAndDropThemselves(GameTestHelper helper) {
        ContentTestSupport.assertPlacesAndDropsSelf(helper, CrewContent.PANTRY.get(), new BlockPos(1, 1, 1));
        ContentTestSupport.assertPlacesAndDropsSelf(helper, CrewContent.WATER_BARREL.get(), new BlockPos(1, 1, 1));
        helper.succeed();
    }

    @ModGameTest
    public static void crewRecipesAreLoaded(GameTestHelper helper) {
        ContentTestSupport.assertRecipe(helper, "hardtack", CrewContent.HARDTACK.get(), 2);
        ContentTestSupport.assertRecipe(helper, "salted_fish", CrewContent.SALTED_FISH.get(), 1);
        ContentTestSupport.assertRecipe(helper, "salt_pork", CrewContent.SALT_PORK.get(), 1);
        ContentTestSupport.assertRecipe(helper, "pantry", CrewContent.PANTRY.get().asItem(), 1);
        ContentTestSupport.assertRecipe(helper, "water_barrel", CrewContent.WATER_BARREL.get().asItem(), 1);
        helper.succeed();
    }

    private static void assertFood(GameTestHelper helper, Item item, FoodProperties expected) {
        FoodProperties food = new ItemStack(item).get(DataComponents.FOOD);
        helper.assertTrue(food != null, item + " has no food component");
        helper.assertValueEqual(food.nutrition(), expected.nutrition(), item + " nutrition");
        helper.assertValueEqual(food.saturation(), expected.saturation(), item + " saturation");
    }

    private static ProvisionType classify(GameTestHelper helper, ItemStack stack, ProvisionSettings s) {
        Optional<ProvisionType> type = ProvisionClassifier.classify(stack, s);
        helper.assertTrue(type.isPresent(), stack + " is not a provision");
        return type.get();
    }
}

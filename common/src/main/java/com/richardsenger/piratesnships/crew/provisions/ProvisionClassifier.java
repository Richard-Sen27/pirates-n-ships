package com.richardsenger.piratesnships.crew.provisions;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.Optional;

/**
 * Decides whether a real item is a provision, and which. Reads only the item's data components and item tags, so it
 * works for every mod's food. Tags must be loaded (a running server), except in tests that only use components.
 *
 * <p>Order: {@link ProvisionTags#EXCLUDED} = nothing; {@link ProvisionTags#RUM} = rum (one ration per item);
 * {@link ProvisionTags#WATER_BARREL} and {@link ProvisionTags#FRESH_WATER} = water; a water bottle (potion with
 * water contents) = one water ration; otherwise any item with a food component and nutrition above 0 = food, valued
 * by its nutrition, preserved if tagged {@link ProvisionTags#PRESERVED}, citrus if tagged
 * {@link ProvisionTags#ANTI_SCURVY}.
 */
public final class ProvisionClassifier {

    private ProvisionClassifier() {
    }

    public static Optional<ProvisionType> classify(ItemStack stack, ProvisionSettings s) {
        if (stack.isEmpty() || stack.is(ProvisionTags.EXCLUDED)) {
            return Optional.empty();
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (stack.is(ProvisionTags.RUM)) {
            return Optional.of(ProvisionType.rum(id, 1, s.rumWeightPerUnit()));
        }
        if (stack.is(ProvisionTags.WATER_BARREL)) {
            return Optional.of(water(id, s.waterBarrelRations(), s));
        }
        if (stack.is(ProvisionTags.FRESH_WATER)) {
            return Optional.of(water(id, stack.is(Items.WATER_BUCKET) ? s.waterBucketRations() : 1, s));
        }
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (stack.is(Items.POTION) && potion != null && potion.is(Potions.WATER)) {
            return Optional.of(water(id, 1, s));
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null && food.nutrition() > 0) {
            boolean preserved = stack.is(ProvisionTags.PRESERVED);
            return Optional.of(ProvisionType.food(id, food.nutrition(), preserved, stack.is(ProvisionTags.ANTI_SCURVY),
                    s.foodWeightPerUnit(), preserved ? 0 : s.freshShelfLifeTicks()));
        }
        return Optional.empty();
    }

    private static ProvisionType water(String id, int rations, ProvisionSettings s) {
        return ProvisionType.water(id, rations, rations * s.waterWeightPerRation());
    }

    /**
     * What is left in the crew's hands after one unit is used: a bowl for stews, a glass bottle for water bottles,
     * a bucket for water buckets, otherwise nothing.
     */
    public static ItemStack leftover(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null && food.usingConvertsTo().isPresent()) {
            return food.usingConvertsTo().get().copy();
        }
        if (stack.is(Items.POTION)) {
            return new ItemStack(Items.GLASS_BOTTLE);
        }
        Item remainder = stack.getItem().getCraftingRemainingItem();
        return remainder == null ? ItemStack.EMPTY : new ItemStack(remainder);
    }
}

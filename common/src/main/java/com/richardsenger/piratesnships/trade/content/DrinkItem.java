package com.richardsenger.piratesnships.trade.content;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/**
 * A food item that is drunk instead of eaten (drinking animation and sound). Nothing else: what the drink does
 * comes from its food component, and later from the crew/morale systems.
 */
public class DrinkItem extends Item {

    public DrinkItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public SoundEvent getEatingSound() {
        return getDrinkingSound();
    }
}

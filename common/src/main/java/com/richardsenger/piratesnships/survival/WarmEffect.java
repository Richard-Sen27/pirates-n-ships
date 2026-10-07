package com.richardsenger.piratesnships.survival;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The {@code pirates_n_ships:warm} effect: no effect of its own; while it lasts, cold water does not fill the freezing
 * meter ({@link com.richardsenger.piratesnships.survival.cold.ColdWater}). Granted by drinking an item in
 * {@code #pirates_n_ships:warming} (rum).
 */
public class WarmEffect extends MobEffect {

    /** Amber, like rum. */
    public static final int COLOR = 0xD9822B;

    public WarmEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }
}

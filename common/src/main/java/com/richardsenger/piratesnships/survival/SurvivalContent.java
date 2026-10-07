package com.richardsenger.piratesnships.survival;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;

/** Registry entries and tags of the {@code survival} module. */
public final class SurvivalContent {

    /** While it lasts, cold water does not freeze. */
    public static final RegistryEntry<MobEffect, WarmEffect> WARM =
            Services.REGISTRY.register(Registries.MOB_EFFECT, "warm", WarmEffect::new);

    /** Biomes whose water freezes ({@code data/pirates_n_ships/tags/worldgen/biome/cold_water.json}). */
    public static final TagKey<Biome> COLD_WATER = TagKey.create(Registries.BIOME, Constants.id("cold_water"));
    /** Drinks that grant {@link #WARM} when finished (rum). */
    public static final TagKey<Item> WARMING = TagKey.create(Registries.ITEM, Constants.id("warming"));

    private SurvivalContent() {
    }

    /** Loads the class so the entries above are registered in time. Called from {@code registerContent()}. */
    public static void init() {
    }
}

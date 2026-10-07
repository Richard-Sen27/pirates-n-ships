package com.richardsenger.piratesnships.platform.registry;

import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A loader-neutral natural spawn: {@code type} spawns in {@code category} in every biome that is in at least one of
 * {@code biomes} (once per biome, however many tags match). Weight and group size are suppliers because they come from
 * the server config: the loader reads them once when it builds the biomes at server start (NeoForge: after the server
 * config loaded, through the {@code pirates_n_ships:natural_spawns} biome modifier; Fabric port:
 * {@code BiomeModifications}, whose modifiers also run at server start). A weight of 0 adds nothing.
 */
public record NaturalSpawn(Supplier<? extends EntityType<?>> type, MobCategory category, List<TagKey<Biome>> biomes,
                           IntSupplier weight, IntSupplier minGroup, IntSupplier maxGroup) {

    /** Whether {@code biome} is in any of the tags. */
    public boolean matches(Holder<Biome> biome) {
        for (TagKey<Biome> tag : biomes) if (biome.is(tag)) return true;
        return false;
    }

    /** {@code [min, max]} with {@code max >= min >= 1}, whatever the config says. */
    public int[] group() {
        int min = Math.max(1, minGroup.getAsInt());
        return new int[]{min, Math.max(min, maxGroup.getAsInt())};
    }
}

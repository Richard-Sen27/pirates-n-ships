package com.richardsenger.piratesnships.platform.services;

import com.richardsenger.piratesnships.platform.registry.NaturalSpawn;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.function.Supplier;

/**
 * Registers content into vanilla registries at the right time for the loader. Feature code uses the typed helpers in
 * {@code core.registry.ModRegistry}; call this directly only for registries that have no helper.
 */
public interface IRegistryHelper {

    /**
     * Declares an entry {@code pirates_n_ships:<name>} in {@code registry}. Must be called during mod
     * initialization (i.e. from a module's {@code registerContent()} or a static initializer it triggers).
     */
    <R, T extends R> RegistryEntry<R, T> register(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory);

    /** Registers default attributes for a living entity type (required for every custom {@code LivingEntity}). */
    <E extends LivingEntity> void registerEntityAttributes(Supplier<EntityType<E>> type, Supplier<AttributeSupplier.Builder> attributes);

    /**
     * Sets the spawn placement of a mob type (where natural spawning, spawners and the chunk generator may place it):
     * vanilla's {@code SpawnPlacements.register}, which is private. Replaces any earlier placement of the type. The
     * predicate lives in common. NeoForge: {@code RegisterSpawnPlacementsEvent}; Fabric port: the access-widened
     * {@code SpawnPlacements.register} at init.
     */
    <E extends Mob> void registerSpawnPlacement(Supplier<EntityType<E>> type, SpawnPlacementType placement,
                                                Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<E> predicate);

    /**
     * Adds a natural spawn to biomes (see {@link NaturalSpawn}). NeoForge: the {@code pirates_n_ships:natural_spawns}
     * biome modifier (its JSON comes from datagen); Fabric port: {@code BiomeModifications.addSpawn} with a
     * {@code BiomeSelectors.tag} selector per tag.
     */
    void registerNaturalSpawn(NaturalSpawn spawn);
}

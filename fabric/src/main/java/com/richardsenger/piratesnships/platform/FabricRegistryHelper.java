package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.platform.registry.NaturalSpawn;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.platform.services.IRegistryHelper;
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

/** TODO Fabric port (milestone 22). */
public class FabricRegistryHelper implements IRegistryHelper {

    @Override
    public <R, T extends R> RegistryEntry<R, T> register(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory) {
        throw new UnsupportedOperationException("Fabric port: milestone 22");
    }

    @Override
    public <E extends LivingEntity> void registerEntityAttributes(Supplier<EntityType<E>> type, Supplier<AttributeSupplier.Builder> attributes) {
        throw new UnsupportedOperationException("Fabric port: milestone 22");
    }

    /** Fabric port: {@code SpawnPlacements.register} (access-widened by Fabric API) once the type is registered. */
    @Override
    public <E extends Mob> void registerSpawnPlacement(Supplier<EntityType<E>> type, SpawnPlacementType placement,
                                                       Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<E> predicate) {
        throw new UnsupportedOperationException("Fabric port: milestone 22");
    }

    /**
     * Fabric port: {@code BiomeModifications.create(id).add(ModificationPhase.ADDITIONS, BiomeSelectors.tag(..) per
     * tag, ctx -> ctx.getSpawnSettings().addSpawn(category, new SpawnerData(type, weight, min, max)))}; the lambda runs
     * at server start, so it can read the config suppliers like the NeoForge modifier does.
     */
    @Override
    public void registerNaturalSpawn(NaturalSpawn spawn) {
        throw new UnsupportedOperationException("Fabric port: milestone 22");
    }
}

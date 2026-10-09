package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.registry.NaturalSpawn;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.platform.services.IRegistryHelper;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Registers straight into the vanilla registries, which Fabric leaves open during mod initialization: each entry is
 * created and registered when common code declares it, in declaration order (the usual Fabric way). This differs from
 * NeoForge's {@code DeferredRegister}, which creates entries later, registry by registry; a factory must therefore only
 * use entries declared before it (true for all of ours: block items after their blocks, armour items after their
 * materials, block entity types after their blocks). Any registry works, including Sable's force groups and
 * {@code Registries.ARMOR_MATERIAL} (ART6), as long as it exists in {@link BuiltInRegistries#REGISTRY} when the entry
 * is declared.
 *
 * <p>Entity attributes, spawn placements and natural spawns are collected and applied by {@link #finish()}, which the
 * entry point calls after common init, when every entity type exists.
 */
public final class FabricRegistryHelper implements IRegistryHelper {

    private final List<AttributeEntry<?>> attributes = new ArrayList<>();
    private final List<PlacementEntry<?>> placements = new ArrayList<>();
    private final List<NaturalSpawn> naturalSpawns = new ArrayList<>();
    private boolean finished;

    private record AttributeEntry<E extends LivingEntity>(Supplier<EntityType<E>> type, Supplier<AttributeSupplier.Builder> builder) {
        void register() {
            FabricDefaultAttributeRegistry.register(type.get(), builder.get());
        }
    }

    private record PlacementEntry<E extends Mob>(Supplier<EntityType<E>> type, SpawnPlacementType placement,
                                                 Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<E> predicate) {
        void register() {
            // access-widened (pirates_n_ships.accesswidener); throws on a duplicate, our types have no vanilla entry
            SpawnPlacements.register(type.get(), placement, heightmap, predicate);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized <R, T extends R> RegistryEntry<R, T> register(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory) {
        Registry<R> target = (Registry<R>) BuiltInRegistries.REGISTRY.get(registry.location());
        if (target == null) {
            throw new IllegalStateException("No registry " + registry.location() + " to register " + name + " in");
        }
        T value = factory.get();
        Holder.Reference<R> holder = Registry.registerForHolder(target, Constants.id(name), value);
        return new RegistryEntry<>(holder.key(), () -> value, holder);
    }

    @Override
    public synchronized <E extends LivingEntity> void registerEntityAttributes(Supplier<EntityType<E>> type, Supplier<AttributeSupplier.Builder> attributes) {
        AttributeEntry<E> entry = new AttributeEntry<>(type, attributes);
        if (finished) entry.register();
        else this.attributes.add(entry);
    }

    @Override
    public synchronized <E extends Mob> void registerSpawnPlacement(Supplier<EntityType<E>> type, SpawnPlacementType placement,
                                                                    Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<E> predicate) {
        PlacementEntry<E> entry = new PlacementEntry<>(type, placement, heightmap, predicate);
        if (finished) entry.register();
        else placements.add(entry);
    }

    /**
     * Fabric API's biome modifications run once per server start, before the world loads. Forge Config API Port loads
     * the server config only at {@code SERVER_STARTING}, later, so the weight and group size read here are those of the
     * config as it was when the server was created: the code defaults on a dedicated server's first start (NeoForge
     * reads the loaded config). See docs/fabric.md.
     */
    @Override
    public synchronized void registerNaturalSpawn(NaturalSpawn spawn) {
        naturalSpawns.add(spawn);
        int index = naturalSpawns.size() - 1;
        BiomeModifications.create(Constants.id("natural_spawn_" + index))
                .add(ModificationPhase.ADDITIONS, ctx -> spawn.matches(ctx.getBiomeRegistryEntry()), ctx -> {
                    int weight = spawn.weight().getAsInt();
                    if (weight <= 0) return;
                    int[] group = spawn.group();
                    ctx.getSpawnSettings().addSpawn(spawn.category(),
                            new MobSpawnSettings.SpawnerData(spawn.type().get(), weight, group[0], group[1]));
                });
    }

    /** Called once by the entry point after common init: registers what needs every entity type to exist. */
    public synchronized void finish() {
        if (finished) throw new IllegalStateException("FabricRegistryHelper finished twice");
        finished = true;
        attributes.forEach(AttributeEntry::register);
        placements.forEach(PlacementEntry::register);
    }
}

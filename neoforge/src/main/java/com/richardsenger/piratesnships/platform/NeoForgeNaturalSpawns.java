package com.richardsenger.piratesnships.platform;

import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.registry.NaturalSpawn;
import net.minecraft.core.Holder;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NeoForge side of {@code IRegistryHelper#registerNaturalSpawn}: one biome modifier type,
 * {@code pirates_n_ships:natural_spawns}, adds every registered {@link NaturalSpawn} to the biomes in its tags. Its
 * single JSON file ({@code data/pirates_n_ships/neoforge/biome_modifier/natural_spawns.json}) comes from datagen.
 *
 * <p>Why not one {@code neoforge:add_spawns} file per mob: those carry a fixed weight and group size, while ours come
 * from the server config ({@code mobs.shark.spawn_weight}, ...). NeoForge loads the server config before it runs the
 * biome modifiers ({@code ServerLifecycleHooks.handleServerAboutToStart}), so this modifier reads them at every
 * server start. A datapack can still switch it off by replacing the file with {@code {"type": "neoforge:none"}}.
 */
final class NeoForgeNaturalSpawns {

    static final ResourceLocation ID = Constants.id("natural_spawns");

    private static final List<NaturalSpawn> SPAWNS = new CopyOnWriteArrayList<>();

    void add(NaturalSpawn spawn) {
        SPAWNS.add(spawn);
    }

    /** Declares the modifier type and the datagen provider of its JSON. */
    void attach(NeoForgeRegistryHelper registry, IEventBus modBus) {
        registry.register(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, ID.getPath(), () -> Modifier.CODEC);
        modBus.addListener(GatherDataEvent.class, event -> event.getGenerator().addProvider(event.includeServer(),
                new JsonProvider(event.getGenerator().getPackOutput())));
    }

    /** The modifier: no fields, it reads the registered spawns and their config when it runs. */
    record Modifier() implements BiomeModifier {

        static final MapCodec<Modifier> CODEC = MapCodec.unit(Modifier::new);

        @Override
        public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
            if (phase != Phase.ADD) return;
            for (NaturalSpawn spawn : SPAWNS) {
                int weight = spawn.weight().getAsInt();
                if (weight <= 0 || !spawn.matches(biome)) continue;
                int[] group = spawn.group();
                builder.getMobSpawnSettings().addSpawn(spawn.category(),
                        new MobSpawnSettings.SpawnerData(spawn.type().get(), weight, group[0], group[1]));
            }
        }

        @Override
        public MapCodec<? extends BiomeModifier> codec() {
            return CODEC;
        }
    }

    /** Writes the one modifier file (vanilla datagen; output root as for every other provider). */
    private record JsonProvider(PackOutput output) implements DataProvider {

        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            JsonObject json = new JsonObject();
            json.addProperty("type", ID.toString());
            return DataProvider.saveStable(cache, json,
                    output.createRegistryElementsPathProvider(NeoForgeRegistries.Keys.BIOME_MODIFIERS).json(ID));
        }

        @Override
        public String getName() {
            return "Pirates 'n' Ships natural spawn biome modifier";
        }
    }
}

package com.richardsenger.piratesnships.core.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.ModModules;
import com.richardsenger.piratesnships.core.config.ConfigSchema;
import com.richardsenger.piratesnships.core.config.ConfigType;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.blockstates.BlockStateGenerator;
import net.minecraft.data.models.model.DelegatedModel;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Collects every module's {@link DataContributions} and turns them into vanilla data providers. The loader's data
 * event calls {@link #gather}; output goes to {@code common/src/generated/resources}.
 */
public final class ModDataGenerator {

    /** Adds a provider; {@code client} = assets (lang, models), otherwise server data. */
    @FunctionalInterface
    public interface ProviderSink {
        void add(boolean client, DataProvider provider);
    }

    private ModDataGenerator() {
    }

    public static void gather(ProviderSink sink, PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        DataContributions data = new DataContributions();
        for (ModModule m : ModModules.ALL) m.gatherData(data);
        data.lang(ModDataGenerator::configLang);

        sink.add(true, new LangProvider(output, data.lang));
        sink.add(true, new ModelProvider(output, data.models));
        sink.add(false, new RecipeProvider(output, lookup) {
            // public: Fabric API widens the vanilla method, and the fabric module compiles this file too (FAB1)
            @Override
            public void buildRecipes(RecipeOutput out) {
                data.recipes.forEach(c -> c.accept(out));
            }
        });
        sink.add(false, new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(reg -> new ModBlockLoot(reg, data.blockLoot), LootContextParamSets.BLOCK)), lookup));
        data.tags.forEach((registry, contributors) -> sink.add(false, tagsProvider(output, registry, lookup, contributors)));
        sink.add(true, new SoundsProvider(output, data.sounds));
        sink.add(true, new JsonOutputs.Provider(output, PackOutput.Target.RESOURCE_PACK, data.json, lookup));
        sink.add(false, new JsonOutputs.Provider(output, PackOutput.Target.DATA_PACK, data.json, lookup));
        sink.add(false, new GameTestStructureProvider(output));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ModTagsProvider<?> tagsProvider(PackOutput output, ResourceKey<? extends Registry<?>> registry,
                                                   CompletableFuture<HolderLookup.Provider> lookup, List<Consumer<?>> contributors) {
        return ModTagsProvider.forBuiltIn(output, (ResourceKey) registry, lookup, (List) contributors);
    }

    /** Config screen titles (from value names) and tooltips (from comments) for every declared config entry. */
    private static void configLang(LangBuilder lang) {
        for (ConfigType type : ConfigType.values()) {
            ConfigSchema schema = ModConfigs.schema(type);
            schema.sections().forEach((path, comment) -> lang.add(ConfigValue.translationKey(path), title(path.getLast())));
            for (ConfigValue<?> v : schema.values()) {
                lang.add(v.translationKey(), title(v.path().getLast()));
                lang.add(v.translationKey() + ".tooltip", v.comment());
            }
        }
    }

    private static String title(String snake) {
        StringBuilder sb = new StringBuilder();
        for (String w : snake.split("_")) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
        }
        return sb.toString();
    }

    private record LangProvider(PackOutput output, List<Consumer<LangBuilder>> contributors) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            LangBuilder lang = new LangBuilder();
            contributors.forEach(c -> c.accept(lang));
            JsonObject json = new JsonObject();
            lang.entries().forEach(json::addProperty);
            return DataProvider.saveStable(cache, json, output.getOutputFolder(PackOutput.Target.RESOURCE_PACK)
                    .resolve(Constants.MOD_ID).resolve("lang").resolve("en_us.json"));
        }

        @Override
        public String getName() {
            return "Lang: " + Constants.MOD_ID;
        }
    }

    /** Writes {@code assets/pirates_n_ships/sounds.json} from every module's {@link DataContributions#sounds} entries. */
    private record SoundsProvider(PackOutput output, List<Consumer<SoundEntries>> contributors) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            SoundEntries entries = new SoundEntries();
            contributors.forEach(c -> c.accept(entries));
            if (entries.isEmpty()) return CompletableFuture.completedFuture(null);
            return DataProvider.saveStable(cache, entries.toJson(), output.getOutputFolder(PackOutput.Target.RESOURCE_PACK)
                    .resolve(Constants.MOD_ID).resolve("sounds.json"));
        }

        @Override
        public String getName() {
            return "Sounds: " + Constants.MOD_ID;
        }
    }

    /** Like vanilla's ModelProvider, but limited to our namespace and fed by module contributors. */
    private record ModelProvider(PackOutput output, List<Consumer<ModelContext>> contributors) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            Map<Block, BlockStateGenerator> states = new HashMap<>();
            Map<ResourceLocation, Supplier<JsonElement>> models = new HashMap<>();
            Set<Item> skipped = new HashSet<>();
            Consumer<BlockStateGenerator> stateOut = g -> {
                if (states.put(g.getBlock(), g) != null) throw new IllegalStateException("Duplicate blockstate for " + g.getBlock());
            };
            BiConsumer<ResourceLocation, Supplier<JsonElement>> modelOut = (id, json) -> {
                if (models.put(id, json) != null) throw new IllegalStateException("Duplicate model " + id);
            };
            ModelContext ctx = new ModelContext(new BlockModelGenerators(stateOut, modelOut, skipped::add),
                    new ItemModelGenerators(modelOut), stateOut, modelOut, skipped::add);
            contributors.forEach(c -> c.accept(ctx));

            List<String> missing = new ArrayList<>();
            for (var entry : ModRegistry.blocks()) {
                if (!states.containsKey(entry.get())) missing.add(entry.id().toString());
            }
            if (!missing.isEmpty()) throw new IllegalStateException("Missing blockstate definitions for: " + missing);
            // Block items without their own model delegate to the block model
            for (var entry : ModRegistry.items()) {
                if (entry.get() instanceof BlockItem bi && !skipped.contains(bi)) {
                    ResourceLocation itemModel = ModelLocationUtils.getModelLocation(bi);
                    if (!models.containsKey(itemModel)) {
                        models.put(itemModel, new DelegatedModel(ModelLocationUtils.getModelLocation(bi.getBlock())));
                    }
                }
            }

            PackOutput.PathProvider statePaths = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "blockstates");
            PackOutput.PathProvider modelPaths = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models");
            List<CompletableFuture<?>> futures = new ArrayList<>();
            states.forEach((block, gen) -> futures.add(DataProvider.saveStable(cache, gen.get(), statePaths.json(block.builtInRegistryHolder().key().location()))));
            models.forEach((id, json) -> futures.add(DataProvider.saveStable(cache, json.get(), modelPaths.json(id))));
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        }

        @Override
        public String getName() {
            return "Block states and models: " + Constants.MOD_ID;
        }
    }

    /** Writes the empty structure templates from {@link GameTestTemplates} as {@code data/<ns>/structure/*.nbt}. */
    private record GameTestStructureProvider(PackOutput output) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            PackOutput.PathProvider paths = output.createPathProvider(PackOutput.Target.DATA_PACK, "structure");
            return CompletableFuture.runAsync(() -> {
                for (GameTestTemplates.Template t : GameTestTemplates.ALL) {
                    CompoundTag tag = new CompoundTag();
                    ListTag size = new ListTag();
                    size.add(IntTag.valueOf(t.x()));
                    size.add(IntTag.valueOf(t.y()));
                    size.add(IntTag.valueOf(t.z()));
                    tag.put("size", size);
                    tag.put("palette", new ListTag());
                    tag.put("blocks", new ListTag());
                    tag.put("entities", new ListTag());
                    NbtUtils.addDataVersion(tag, SharedConstants.getCurrentVersion().getDataVersion().getVersion());
                    try {
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        NbtIo.writeCompressed(tag, bytes);
                        byte[] data = bytes.toByteArray();
                        cache.writeIfNeeded(paths.file(ResourceLocation.parse(t.id()), "nbt"), data, Hashing.sha1().hashBytes(data));
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to write structure " + t.id(), e);
                    }
                }
            });
        }

        @Override
        public String getName() {
            return "GameTest structures: " + Constants.MOD_ID;
        }
    }
}

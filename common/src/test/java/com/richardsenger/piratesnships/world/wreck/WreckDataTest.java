package com.richardsenger.piratesnships.world.wreck;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated worldgen files of the wrecks parse with vanilla's codecs (our structure through its own codec, since
 * our structure type is not in the JUnit registry), and every piece names a committed NBT whose height its water cover
 * allows. Holders resolve as unbound stand-alone references, so this checks the format; the GameTests load the files
 * in a real server.
 */
class WreckDataTest {

    private static Path root;
    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("tools/schem_to_structure.py"))) root = root.getParent();
        assertNotNull(root, "repository root");
        ops = RegistryOps.create(JsonOps.INSTANCE, new RegistryOps.RegistryInfoLookup() {
            private final Map<ResourceKey<?>, RegistryOps.RegistryInfo<?>> infos = new HashMap<>();

            @Override
            @SuppressWarnings("unchecked")
            public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
                return Optional.of((RegistryOps.RegistryInfo<T>) infos.computeIfAbsent(key, k -> standAlone()));
            }

            private <T> RegistryOps.RegistryInfo<T> standAlone() {
                HolderOwner<T> owner = new HolderOwner<>() { };
                HolderGetter<T> getter = new HolderGetter<>() {
                    @Override
                    public Optional<Holder.Reference<T>> get(ResourceKey<T> element) {
                        return Optional.of(Holder.Reference.createStandAlone(owner, element));
                    }

                    @Override
                    public Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
                        return Optional.of(HolderSet.emptyNamed(owner, tag));
                    }
                };
                return new RegistryOps.RegistryInfo<>(owner, getter, Lifecycle.stable());
            }
        });
    }

    private static JsonElement generated(String path) throws IOException {
        Path file = root.resolve("common/src/generated/resources/data/pirates_n_ships/" + path + ".json");
        assertTrue(Files.exists(file), "missing " + file);
        try (Reader r = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(r);
        }
    }

    @Test
    void structureParses() throws IOException {
        JsonObject json = generated("worldgen/structure/wreck").getAsJsonObject();
        assertEquals("pirates_n_ships:wreck", json.get("type").getAsString());
        WreckStructure s = WreckStructure.CODEC.codec().parse(ops, json).getOrThrow();
        assertEquals(GenerationStep.Decoration.SURFACE_STRUCTURES, s.step());
        assertEquals(TerrainAdjustment.NONE, s.terrainAdaptation());
        assertTrue(s.spawnOverrides().isEmpty(), "no spawn overrides");
        assertEquals(Optional.of(WreckKeys.HAS_WRECK), s.biomes().unwrapKey());
        assertEquals(WreckData.PIECES, s.pieces());
        assertEquals(1, s.depthBelowFloor());
        assertTrue(WreckStructure.CODEC.codec().encodeStart(ops, s).isSuccess(), "encodes again");
    }

    @Test
    void structureNeedsPieces() {
        JsonObject json = WreckData.structure();
        json.add("pieces", new JsonArray());
        assertTrue(WreckStructure.CODEC.codec().parse(ops, json).isError(), "an empty piece list is rejected");
    }

    @Test
    void structureSetParses() throws IOException {
        StructureSet set = StructureSet.DIRECT_CODEC.parse(ops, generated("worldgen/structure_set/wrecks")).getOrThrow();
        assertEquals(1, set.structures().size());
        assertEquals(Optional.of(WreckKeys.WRECK), set.structures().get(0).structure().unwrapKey());
        RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) set.placement();
        assertEquals(24, placement.spacing());
        assertEquals(8, placement.separation());
        assertEquals(WreckData.SALT, generated("worldgen/structure_set/wrecks").getAsJsonObject()
                .getAsJsonObject("placement").get("salt").getAsInt());
    }

    @Test
    void biomeTagHasTheOceans() throws IOException {
        JsonObject json = generated("tags/worldgen/biome/has_structure/wreck").getAsJsonObject();
        List<String> values = json.getAsJsonArray("values").asList().stream().map(JsonElement::getAsString).toList();
        assertEquals(List.of("#minecraft:is_ocean", "#minecraft:is_deep_ocean"), values);
    }

    @Test
    void everyPieceIsACommittedNbtThatFitsItsWaterCover() throws IOException {
        for (WreckPieceEntry piece : WreckData.PIECES) {
            Path nbt = root.resolve("common/src/main/resources/data/" + piece.template().getNamespace() + "/structure/"
                    + piece.template().getPath() + ".nbt");
            assertTrue(Files.exists(nbt), piece.template() + " has no committed NBT");
            CompoundTag tag = NbtIo.readCompressed(nbt, NbtAccounter.unlimitedHeap());
            ListTag size = tag.getList("size", Tag.TAG_INT);
            // the top row (y = height - 1) must have at least one block of water above it
            assertEquals(size.getInt(1), piece.waterAbove(), piece.template() + ": water cover equals the piece's height");
        }
    }
}

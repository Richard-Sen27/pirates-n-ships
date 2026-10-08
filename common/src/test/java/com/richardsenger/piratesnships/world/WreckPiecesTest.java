package com.richardsenger.piratesnships.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Keeps the committed wreck pieces ({@code data/pirates_n_ships/structure/wreck/*.nbt}, ST5, art/README.md "Wrecks
 * (ST5)") honest: they load as vanilla structure templates within their size bounds, use only existing blocks and
 * states (mod blocks against the datagen block states), are waterlogged wherever the block allows it, have no jigsaw
 * blocks, carry the wreck loot table in every chest, and are exactly what the converter makes from the committed
 * schematics. Also checks the generated loot table {@code pirates_n_ships:chests/wreck}.
 */
class WreckPiecesTest {

    private static final String MOD = "pirates_n_ships";
    private static final String LOOT_TABLE = MOD + ":chests/wreck";
    /** Piece -> the largest size it may have (x, y, z). */
    private static final Map<String, int[]> PIECES = Map.of(
            "sunken_sloop", new int[] {16, 12, 28},
            "cargo_field", new int[] {15, 4, 15},
            "mast_stump", new int[] {7, 12, 7},
            "stern", new int[] {9, 8, 11});

    private static Path root;

    @TempDir
    Path tmp;

    @BeforeAll
    static void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("tools/schem_to_structure.py"))) {
            root = root.getParent();
        }
        assertNotNull(root, "repository root (tools/schem_to_structure.py) not found above the working directory");
    }

    private static Path dir() {
        return root.resolve("common/src/main/resources/data/" + MOD + "/structure/wreck");
    }

    private static CompoundTag piece(String name) throws IOException {
        return NbtIo.readCompressed(dir().resolve(name + ".nbt"), NbtAccounter.unlimitedHeap());
    }

    private static int[] size(CompoundTag structure) {
        ListTag size = structure.getList("size", Tag.TAG_INT);
        return new int[] {size.getInt(0), size.getInt(1), size.getInt(2)};
    }

    /** A placed block: its position, palette entry and block entity data (or null). */
    private record Placed(int x, int y, int z, CompoundTag state, CompoundTag nbt) {
        String name() {
            return state.getString("Name");
        }
    }

    private static List<Placed> blocks(CompoundTag structure) {
        ListTag palette = structure.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = structure.getList("blocks", Tag.TAG_COMPOUND);
        List<Placed> out = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            out.add(new Placed(pos.getInt(0), pos.getInt(1), pos.getInt(2), palette.getCompound(b.getInt("state")),
                    b.contains("nbt") ? b.getCompound("nbt") : null));
        }
        return out;
    }

    @Test
    void exactlyTheExpectedPiecesAreCommitted() throws IOException {
        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.list(dir())) {
            files.forEach(f -> found.add(f.getFileName().toString().replace(".nbt", "")));
        }
        assertEquals(new TreeSet<>(PIECES.keySet()), found);
    }

    @Test
    void piecesLoadAndStayWithinBounds() throws IOException {
        for (Map.Entry<String, int[]> e : PIECES.entrySet()) {
            String name = e.getKey();
            CompoundTag structure = piece(name);
            int[] size = size(structure);
            for (int axis = 0; axis < 3; axis++) {
                assertTrue(size[axis] > 0 && size[axis] <= e.getValue()[axis],
                        name + ": size " + java.util.Arrays.toString(size) + " exceeds " + java.util.Arrays.toString(e.getValue()));
            }
            assertTrue(structure.getList("entities", Tag.TAG_COMPOUND).isEmpty(), name + " has entities");
            StructureTemplate template = new StructureTemplate();
            template.load(BuiltInRegistries.BLOCK.asLookup(), structure);
            assertEquals(size[0], template.getSize().getX(), name);
            assertEquals(size[1], template.getSize().getY(), name);
            assertEquals(size[2], template.getSize().getZ(), name);
            // y 0 is the seabed row: the piece brings blocks for it, and every row of the box is used
            Set<Integer> rows = new HashSet<>();
            for (Placed b : blocks(structure)) {
                rows.add(b.y());
            }
            for (int y = 0; y < size[1]; y++) {
                assertTrue(rows.contains(y), name + ": row y " + y + " is empty");
            }
        }
    }

    @Test
    void everyBlockAndStateExistsAndIsWaterlogged() throws IOException {
        Map<String, Map<String, Set<String>>> modStates = new HashMap<>();
        for (String name : PIECES.keySet()) {
            ListTag palette = piece(name).getList("palette", Tag.TAG_COMPOUND);
            for (int i = 0; i < palette.size(); i++) {
                CompoundTag entry = palette.getCompound(i);
                String id = entry.getString("Name");
                CompoundTag props = entry.getCompound("Properties");
                ResourceLocation location = ResourceLocation.parse(id);
                if (location.getNamespace().equals("minecraft")) {
                    assertTrue(BuiltInRegistries.BLOCK.containsKey(location), name + ": unknown block " + id);
                    Block block = BuiltInRegistries.BLOCK.get(location);
                    for (String key : props.getAllKeys()) {
                        Property<?> property = block.getStateDefinition().getProperty(key);
                        assertNotNull(property, name + ": " + id + " has no property " + key);
                        assertTrue(property.getValue(props.getString(key)).isPresent(),
                                name + ": " + id + "[" + key + "=" + props.getString(key) + "] is not a value");
                    }
                    // under water: every block that can hold water does
                    if (block.getStateDefinition().getProperties().contains(BlockStateProperties.WATERLOGGED)) {
                        assertEquals("true", props.getString("waterlogged"), name + ": " + id + " is not waterlogged");
                    }
                } else if (location.getNamespace().equals(MOD)) {
                    Map<String, Set<String>> known = modStates.computeIfAbsent(location.getPath(), WreckPiecesTest::modBlockStates);
                    for (String key : props.getAllKeys()) {
                        Set<String> values = known.get(key);
                        if (values != null) {
                            assertTrue(values.contains(props.getString(key)),
                                    name + ": " + id + "[" + key + "=" + props.getString(key) + "] is not a value " + values);
                        }
                    }
                } else {
                    fail(name + ": block from another mod: " + id);
                }
            }
        }
    }

    /** Property -> values named in the mod block's datagen block state file; fails when the block has none. */
    private static Map<String, Set<String>> modBlockStates(String path) {
        Path file = root.resolve("common/src/generated/resources/assets/" + MOD + "/blockstates/" + path + ".json");
        assertTrue(Files.exists(file), "no mod block " + MOD + ":" + path + " (no generated block state " + file + ")");
        Map<String, Set<String>> out = new HashMap<>();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("variants")) {
                for (String variant : json.getAsJsonObject("variants").keySet()) {
                    for (String pair : variant.split(",")) {
                        if (pair.contains("=")) {
                            String[] kv = pair.split("=", 2);
                            out.computeIfAbsent(kv[0], k -> new HashSet<>()).add(kv[1]);
                        }
                    }
                }
            }
            if (json.has("multipart")) {
                for (JsonElement part : json.getAsJsonArray("multipart")) {
                    JsonObject when = part.getAsJsonObject().getAsJsonObject("when");
                    if (when != null) {
                        for (String key : when.keySet()) {
                            if (when.get(key).isJsonPrimitive()) {
                                for (String v : when.get(key).getAsString().split("\\|")) {
                                    out.computeIfAbsent(key, k -> new HashSet<>()).add(v);
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    @Test
    void everyChestCarriesTheWreckLootTable() throws IOException {
        for (String name : PIECES.keySet()) {
            int chests = 0;
            for (Placed b : blocks(piece(name))) {
                assertFalse(b.name().equals("minecraft:jigsaw"), name + ": wrecks are single pieces, no jigsaws");
                if (b.name().equals("minecraft:chest") || b.name().equals("minecraft:trapped_chest")
                        || b.name().equals("minecraft:barrel") && b.nbt() != null) {
                    String where = name + ": " + b.name() + " at " + b.x() + "," + b.y() + "," + b.z();
                    assertNotNull(b.nbt(), where + " has no block entity data");
                    assertEquals(LOOT_TABLE, b.nbt().getString("LootTable"), where);
                    assertFalse(b.nbt().contains("LootTableSeed"), where + ": the seed is rolled when it is placed");
                    assertEquals(b.name(), b.nbt().getString("id"), where + ": block entity id");
                    chests++;
                }
            }
            assertTrue(chests >= 1, name + " has no loot chest");
        }
        assertTrue(blocks(piece("sunken_sloop")).stream().anyMatch(b -> b.name().equals(MOD + ":sea_chest")),
                "the sunken sloop's hold has the sea chest");
    }

    @Test
    void lootTableIsGenerated() throws IOException {
        Path file = root.resolve("common/src/generated/resources/data/" + MOD + "/loot_table/chests/wreck.json");
        assertTrue(Files.exists(file), "run ./gradlew :neoforge:runData: " + file);
        JsonObject json;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        }
        assertEquals("minecraft:chest", json.get("type").getAsString());
        Set<String> items = new TreeSet<>();
        JsonArray pools = json.getAsJsonArray("pools");
        for (JsonElement pool : pools) {
            for (JsonElement entry : pool.getAsJsonObject().getAsJsonArray("entries")) {
                items.add(entry.getAsJsonObject().get("name").getAsString());
            }
        }
        assertEquals(new TreeSet<>(List.of(MOD + ":doubloon", MOD + ":rum", MOD + ":salted_fish", MOD + ":rope",
                MOD + ":nails", MOD + ":lead_shot", MOD + ":cutlass", MOD + ":treasure_map", MOD + ":kraken_ink")), items);
        // TM1: one blank treasure map among the ship's stores at weight 2
        boolean map = false;
        for (JsonElement entry : pools.get(1).getAsJsonObject().getAsJsonArray("entries")) {
            JsonObject e = entry.getAsJsonObject();
            if (!e.get("name").getAsString().equals(MOD + ":treasure_map")) continue;
            assertEquals(2, e.get("weight").getAsInt(), "treasure map weight");
            assertFalse(e.has("functions"), "one map at a time");
            map = true;
        }
        assertTrue(map, "the treasure map is among the ship's stores");
        // the first pool: always 3-12 doubloons
        JsonObject doubloons = pools.get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals(MOD + ":doubloon", doubloons.get("name").getAsString());
        JsonObject count = doubloons.getAsJsonArray("functions").get(0).getAsJsonObject().getAsJsonObject("count");
        assertEquals(3, count.get("min").getAsInt());
        assertEquals(12, count.get("max").getAsInt());
    }

    /** The committed NBT is exactly what the converter makes from the committed schematics. */
    @Test
    void committedPiecesAreUpToDate() throws Exception {
        Assumptions.assumeTrue(pythonWorks(), "python3 is not available");
        Path out = Files.createDirectories(tmp.resolve("wreck"));
        List<String> cmd = new ArrayList<>(List.of("python3", "-I", root.resolve("tools/schem_to_structure.py").toString(),
                "--out", out.toString()));
        for (String name : new TreeSet<>(PIECES.keySet())) {
            cmd.add(root.resolve("art/schematics/structures/wreck/" + name + ".schem").toString());
        }
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "converter timed out");
        assertEquals(0, p.exitValue(), log);
        for (String name : PIECES.keySet()) {
            assertArrayEquals(Files.readAllBytes(dir().resolve(name + ".nbt")), Files.readAllBytes(out.resolve(name + ".nbt")),
                    "wreck/" + name + ": rerun python3 tools/build_structures.py (or tools/schem_to_structure.py) and commit the result");
        }
        assertFalse(log.contains("jigsaw at"), "wrecks have no jigsaws: " + log);
    }

    private static boolean pythonWorks() {
        try {
            Process p = new ProcessBuilder("python3", "--version").redirectErrorStream(true).start();
            return p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}

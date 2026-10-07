package com.richardsenger.piratesnships.world;

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
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
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
 * Keeps the committed seafarer village pieces ({@code data/pirates_n_ships/structure/village/*.nbt}, ST1) honest:
 * they load as vanilla structure templates, stay within the size bounds, use only existing blocks and states, follow
 * the jigsaw convention of art/README.md ("Structures (ST1)") and are exactly what the converter makes from the
 * committed schematics. Mod blocks are checked against the datagen block states (every mod block has one).
 */
class StructurePiecesTest {

    private static final String MOD = "pirates_n_ships";
    private static final String EMPTY = "minecraft:empty";
    private static final List<String> PIECES = List.of("dock_head", "house_small", "pier", "shipwright", "street", "street_end", "tavern");
    private static final Set<String> BUILDINGS = Set.of("house_small", "shipwright", "tavern");
    private static final int MAX_SIZE = 32;

    /** Jigsaw name -> {target, pool, joint, final state or null for any}. */
    private static final Map<String, List<String>> CONVENTION = Map.of(
            MOD + ":street_out", List.of(MOD + ":street_in", MOD + ":village/streets", "aligned"),
            MOD + ":street_in", List.of(MOD + ":street_out", EMPTY, "aligned"),
            MOD + ":building_out", List.of(MOD + ":building_in", MOD + ":village/buildings", "rollable"),
            MOD + ":building_in", List.of(MOD + ":building_out", EMPTY, "rollable"),
            MOD + ":pier_out", List.of(MOD + ":pier_in", MOD + ":village/pier", "aligned"),
            MOD + ":pier_in", List.of(MOD + ":pier_out", EMPTY, "aligned"),
            MOD + ":berth", List.of(EMPTY, EMPTY, "aligned"));

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

    private static Path villageDir() {
        return root.resolve("common/src/main/resources/data/" + MOD + "/structure/village");
    }

    private static CompoundTag piece(String name) throws IOException {
        return NbtIo.readCompressed(villageDir().resolve(name + ".nbt"), NbtAccounter.unlimitedHeap());
    }

    private static int[] size(CompoundTag structure) {
        ListTag size = structure.getList("size", Tag.TAG_INT);
        return new int[] {size.getInt(0), size.getInt(1), size.getInt(2)};
    }

    /** A jigsaw block of a piece: its position, orientation (front) and data. */
    private record Jigsaw(BlockPos pos, String front, CompoundTag nbt) {
        String name() {
            return nbt.getString("name");
        }
    }

    private static List<Jigsaw> jigsaws(CompoundTag structure) {
        ListTag palette = structure.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = structure.getList("blocks", Tag.TAG_COMPOUND);
        List<Jigsaw> out = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            CompoundTag state = palette.getCompound(b.getInt("state"));
            if (!state.getString("Name").equals("minecraft:jigsaw")) {
                continue;
            }
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            String orientation = state.getCompound("Properties").getString("orientation");
            assertTrue(orientation.endsWith("_up"), "horizontal jigsaws only: " + orientation);
            assertTrue(b.contains("nbt"), "jigsaw without data at " + pos);
            out.add(new Jigsaw(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)),
                    orientation.substring(0, orientation.length() - 3), b.getCompound("nbt")));
        }
        return out;
    }

    private static Map<String, Integer> countNames(List<Jigsaw> jigsaws) {
        Map<String, Integer> out = new TreeMap<>();
        for (Jigsaw j : jigsaws) {
            out.merge(j.name().substring(MOD.length() + 1), 1, Integer::sum);
        }
        return out;
    }

    @Test
    void exactlyTheVillagePiecesAreCommitted() throws IOException {
        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.list(villageDir())) {
            files.forEach(f -> found.add(f.getFileName().toString().replace(".nbt", "")));
        }
        assertEquals(new TreeSet<>(PIECES), found);
    }

    @Test
    void piecesLoadAndStayWithinBounds() throws IOException {
        for (String name : PIECES) {
            CompoundTag structure = piece(name);
            int[] size = size(structure);
            for (int s : size) {
                assertTrue(s > 0 && s <= MAX_SIZE, name + ": size " + java.util.Arrays.toString(size));
            }
            assertTrue(structure.getList("entities", Tag.TAG_COMPOUND).isEmpty(), name + " has entities");
            StructureTemplate template = new StructureTemplate();
            template.load(BuiltInRegistries.BLOCK.asLookup(), structure);
            assertEquals(size[0], template.getSize().getX(), name);
            assertEquals(size[1], template.getSize().getY(), name);
            assertEquals(size[2], template.getSize().getZ(), name);
        }
    }

    @Test
    void everyBlockAndStateExists() throws IOException {
        Map<String, Map<String, Set<String>>> modStates = new HashMap<>();
        for (String name : PIECES) {
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
                } else if (location.getNamespace().equals(MOD)) {
                    Map<String, Set<String>> known = modStates.computeIfAbsent(location.getPath(), StructurePiecesTest::modBlockStates);
                    for (String key : props.getAllKeys()) {
                        Set<String> values = known.get(key);
                        // properties the block state file does not vary by (trim, waterlogged) are not checked
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
    void jigsawsFollowTheConvention() throws IOException {
        for (String name : PIECES) {
            CompoundTag structure = piece(name);
            int[] size = size(structure);
            for (Jigsaw j : jigsaws(structure)) {
                String where = name + " jigsaw " + j.name() + " at " + j.pos().toShortString();
                List<String> rule = CONVENTION.get(j.name());
                assertNotNull(rule, where + ": name not in the convention");
                assertEquals(rule.get(0), j.nbt().getString("target"), where + ": target");
                assertEquals(rule.get(1), j.nbt().getString("pool"), where + ": pool");
                assertEquals(rule.get(2), j.nbt().getString("joint"), where + ": joint");
                assertEquals("minecraft:jigsaw", j.nbt().getString("id"), where + ": block entity id");
                String finalState = j.nbt().getString("final_state");
                String finalId = finalState.contains("[") ? finalState.substring(0, finalState.indexOf('[')) : finalState;
                assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(finalId)), where + ": final_state " + finalState);
                if (j.name().equals(MOD + ":berth")) {
                    assertEquals("minecraft:water", finalState, where + ": a berth marker becomes water");
                    continue;
                }
                // a connector sits on the face of the piece's box it points out of
                BlockPos p = j.pos();
                boolean onFace = switch (j.front()) {
                    case "north" -> p.getZ() == 0;
                    case "south" -> p.getZ() == size[2] - 1;
                    case "west" -> p.getX() == 0;
                    case "east" -> p.getX() == size[0] - 1;
                    default -> false;
                };
                assertTrue(onFace, where + ": points " + j.front() + " but is not on that face of " + java.util.Arrays.toString(size));
            }
        }
    }

    @Test
    void connectorsPerPiece() throws IOException {
        assertEquals(Map.of("pier_out", 1, "street_out", 1), countNames(jigsaws(piece("dock_head"))));
        assertEquals(Map.of("berth", 2, "pier_in", 1), countNames(jigsaws(piece("pier"))));
        assertEquals(Map.of("building_out", 2, "street_in", 1, "street_out", 1), countNames(jigsaws(piece("street"))));
        assertEquals(Map.of("street_in", 1), countNames(jigsaws(piece("street_end"))), "the terminator spawns nothing");
        for (String building : BUILDINGS) {
            List<Jigsaw> jigsaws = jigsaws(piece(building));
            assertEquals(Map.of("building_in", 1), countNames(jigsaws), building);
            assertEquals("north", jigsaws.get(0).front(), building + " faces north");
            assertEquals(0, jigsaws.get(0).pos().getY(), building + ": the connector is on the foundation row");
        }
        // the dock head's connectors: pier seaward (north), street inland (south), both on the quay row
        for (Jigsaw j : jigsaws(piece("dock_head"))) {
            assertEquals(j.name().endsWith("pier_out") ? "north" : "south", j.front(), j.name());
            assertEquals(0, j.pos().getY(), j.name());
        }
    }

    @Test
    void pierBerthsSitAtSeaLevelBesideTheDeck() throws IOException {
        CompoundTag pier = piece("pier");
        int[] size = size(pier);
        List<Jigsaw> jigsaws = jigsaws(pier);
        Jigsaw in = jigsaws.stream().filter(j -> j.name().endsWith("pier_in")).findFirst().orElseThrow();
        assertEquals(5, in.pos().getY(), "the deck (and pier_in) is at y 5");
        assertEquals("south", in.front());
        Set<Integer> sides = new HashSet<>();
        for (Jigsaw j : jigsaws) {
            if (!j.name().endsWith("berth")) {
                continue;
            }
            assertEquals(in.pos().getY() - 1, j.pos().getY(), "berths at sea level, one below the deck");
            assertEquals("north", j.front(), "bow toward the sea");
            assertTrue(j.pos().getX() == 0 || j.pos().getX() == size[0] - 1, "berths beside the deck: " + j.pos());
            sides.add(j.pos().getX());
        }
        assertEquals(2, sides.size(), "one berth on each side");
    }

    /** The committed NBT is exactly what the converter makes from the committed schematics. */
    @Test
    void committedPiecesAreUpToDate() throws Exception {
        Assumptions.assumeTrue(pythonWorks(), "python3 is not available");
        List<String> cmd = new ArrayList<>(List.of("python3", "-I", root.resolve("tools/schem_to_structure.py").toString(),
                "--out", tmp.toString()));
        for (String name : PIECES) {
            cmd.add(root.resolve("art/schematics/structures/village/" + name + ".schem").toString());
        }
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "converter timed out");
        assertEquals(0, p.exitValue(), log);
        for (String name : PIECES) {
            assertArrayEquals(Files.readAllBytes(villageDir().resolve(name + ".nbt")),
                    Files.readAllBytes(tmp.resolve(name + ".nbt")),
                    name + ": rerun python3 tools/build_structures.py (or tools/schem_to_structure.py) and commit the result");
        }
        assertTrue(log.contains("jigsaw at"), log);
        assertFalse(log.contains("helm"), "pieces are converted as structures, not ships: " + log);
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

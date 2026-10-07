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
 * Keeps the committed structure pieces ({@code data/pirates_n_ships/structure/<group>/*.nbt}: the seafarer village of
 * ST1 and the pirate island of ST2) honest:
 * they load as vanilla structure templates, stay within the size bounds, use only existing blocks and states, follow
 * the jigsaw convention of art/README.md ("Structures (ST1)", "Pirate island (ST2)") and are exactly what the converter makes from the
 * committed schematics. Mod blocks are checked against the datagen block states (every mod block has one).
 */
class StructurePiecesTest {

    private static final String MOD = "pirates_n_ships";
    private static final String EMPTY = "minecraft:empty";
    /** Group -> its committed pieces. */
    private static final Map<String, List<String>> GROUPS = Map.of(
            "village", List.of("dock_head", "house_small", "pier", "shipwright", "street", "tavern"),
            "pirate_island", List.of("camp_start", "captains_hut", "jetty", "path", "path_end", "tavern_hut", "tent",
                    "treasure_spot"));
    private static final Set<String> BUILDINGS = Set.of("house_small", "shipwright", "tavern");
    private static final Set<String> HUTS = Set.of("captains_hut", "tavern_hut", "tent");
    private static final int MAX_SIZE = 32;

    /** Group -> jigsaw name -> {target, pool, joint}. Markers ({@link #MARKERS}) are valid in every group. */
    private static final Map<String, Map<String, List<String>>> CONVENTION = Map.of(
            "village", Map.of(
                    MOD + ":street_out", List.of(MOD + ":street_in", MOD + ":village/streets", "aligned"),
                    MOD + ":street_in", List.of(MOD + ":street_out", EMPTY, "aligned"),
                    MOD + ":building_out", List.of(MOD + ":building_in", MOD + ":village/buildings", "rollable"),
                    MOD + ":building_in", List.of(MOD + ":building_out", EMPTY, "rollable"),
                    MOD + ":pier_out", List.of(MOD + ":pier_in", MOD + ":village/pier", "aligned"),
                    MOD + ":pier_in", List.of(MOD + ":pier_out", EMPTY, "aligned")),
            "pirate_island", Map.of(
                    MOD + ":path_out", List.of(MOD + ":path_in", MOD + ":pirate_island/paths", "aligned"),
                    MOD + ":path_in", List.of(MOD + ":path_out", EMPTY, "aligned"),
                    MOD + ":hut_out", List.of(MOD + ":hut_in", MOD + ":pirate_island/huts", "rollable"),
                    MOD + ":hut_in", List.of(MOD + ":hut_out", EMPTY, "rollable"),
                    MOD + ":jetty_out", List.of(MOD + ":jetty_in", MOD + ":pirate_island/jetty", "aligned"),
                    MOD + ":jetty_in", List.of(MOD + ":jetty_out", EMPTY, "aligned")));

    /** Markers (not connectors): name -> {target, pool, joint, final state}. */
    private static final Map<String, List<String>> MARKERS = Map.of(
            MOD + ":berth", List.of(EMPTY, EMPTY, "aligned", "minecraft:water"),
            MOD + ":treasure", List.of(EMPTY, EMPTY, "aligned", "minecraft:sand"));

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

    private static Path groupDir(String group) {
        return root.resolve("common/src/main/resources/data/" + MOD + "/structure/" + group);
    }

    private static CompoundTag piece(String group, String name) throws IOException {
        return NbtIo.readCompressed(groupDir(group).resolve(name + ".nbt"), NbtAccounter.unlimitedHeap());
    }

    private static CompoundTag village(String name) throws IOException {
        return piece("village", name);
    }

    private static CompoundTag island(String name) throws IOException {
        return piece("pirate_island", name);
    }

    /** {group, piece} for every committed piece. */
    private static List<String[]> allPieces() {
        List<String[]> out = new ArrayList<>();
        GROUPS.forEach((group, pieces) -> pieces.forEach(name -> out.add(new String[] {group, name})));
        return out;
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
    void exactlyTheExpectedPiecesAreCommitted() throws IOException {
        for (Map.Entry<String, List<String>> group : GROUPS.entrySet()) {
            Set<String> found = new TreeSet<>();
            try (Stream<Path> files = Files.list(groupDir(group.getKey()))) {
                files.forEach(f -> found.add(f.getFileName().toString().replace(".nbt", "")));
            }
            assertEquals(new TreeSet<>(group.getValue()), found, group.getKey());
        }
    }

    @Test
    void piecesLoadAndStayWithinBounds() throws IOException {
        for (String[] gp : allPieces()) {
            String name = gp[0] + "/" + gp[1];
            CompoundTag structure = piece(gp[0], gp[1]);
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
        for (String[] gp : allPieces()) {
            String name = gp[0] + "/" + gp[1];
            ListTag palette = piece(gp[0], gp[1]).getList("palette", Tag.TAG_COMPOUND);
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
        for (String[] gp : allPieces()) {
            String name = gp[0] + "/" + gp[1];
            CompoundTag structure = piece(gp[0], gp[1]);
            int[] size = size(structure);
            for (Jigsaw j : jigsaws(structure)) {
                String where = name + " jigsaw " + j.name() + " at " + j.pos().toShortString();
                List<String> marker = MARKERS.get(j.name());
                List<String> rule = marker != null ? marker : CONVENTION.get(gp[0]).get(j.name());
                assertNotNull(rule, where + ": name not in the " + gp[0] + " convention");
                assertEquals(rule.get(0), j.nbt().getString("target"), where + ": target");
                assertEquals(rule.get(1), j.nbt().getString("pool"), where + ": pool");
                assertEquals(rule.get(2), j.nbt().getString("joint"), where + ": joint");
                assertEquals("minecraft:jigsaw", j.nbt().getString("id"), where + ": block entity id");
                String finalState = j.nbt().getString("final_state");
                String finalId = finalState.contains("[") ? finalState.substring(0, finalState.indexOf('[')) : finalState;
                assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(finalId)), where + ": final_state " + finalState);
                if (marker != null) {
                    assertEquals(marker.get(3), finalState, where + ": what the marker becomes");
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
        assertEquals(Map.of("pier_out", 1, "street_out", 1), countNames(jigsaws(village("dock_head"))));
        assertEquals(Map.of("berth", 2, "pier_in", 1), countNames(jigsaws(village("pier"))));
        assertEquals(Map.of("building_out", 2, "street_in", 1, "street_out", 1), countNames(jigsaws(village("street"))));
        for (String building : BUILDINGS) {
            List<Jigsaw> jigsaws = jigsaws(village(building));
            assertEquals(Map.of("building_in", 1), countNames(jigsaws), building);
            assertEquals("north", jigsaws.get(0).front(), building + " faces north");
            assertEquals(0, jigsaws.get(0).pos().getY(), building + ": the connector is on the foundation row");
        }
        // the dock head's connectors: pier seaward (north), street inland (south), both on the quay row
        for (Jigsaw j : jigsaws(village("dock_head"))) {
            assertEquals(j.name().endsWith("pier_out") ? "north" : "south", j.front(), j.name());
            assertEquals(0, j.pos().getY(), j.name());
        }
    }

    @Test
    void pierBerthsSitAtSeaLevelBesideTheDeck() throws IOException {
        assertBerthsBesideTheDeck(village("pier"), "pier_in");
    }

    @Test
    void jettyBerthsSitAtSeaLevelBesideTheDeck() throws IOException {
        assertBerthsBesideTheDeck(island("jetty"), "jetty_in");
    }

    private static void assertBerthsBesideTheDeck(CompoundTag pier, String inName) {
        int[] size = size(pier);
        List<Jigsaw> jigsaws = jigsaws(pier);
        Jigsaw in = jigsaws.stream().filter(j -> j.name().endsWith(inName)).findFirst().orElseThrow();
        assertEquals(5, in.pos().getY(), "the deck (and " + inName + ") is at y 5");
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

    @Test
    void islandConnectorsPerPiece() throws IOException {
        assertEquals(Map.of("jetty_out", 1, "path_out", 2, "hut_out", 1), countNames(jigsaws(island("camp_start"))));
        assertEquals(Map.of("berth", 2, "jetty_in", 1), countNames(jigsaws(island("jetty"))));
        assertEquals(Map.of("hut_out", 2, "path_in", 1, "path_out", 1), countNames(jigsaws(island("path"))));
        assertEquals(Map.of("path_in", 1), countNames(jigsaws(island("path_end"))));
        assertEquals(Map.of("hut_in", 1, "treasure", 1), countNames(jigsaws(island("treasure_spot"))));
        for (String hut : HUTS) {
            List<Jigsaw> jigsaws = jigsaws(island(hut));
            assertEquals(Map.of("hut_in", 1), countNames(jigsaws), hut);
            assertEquals("north", jigsaws.get(0).front(), hut + " faces north");
            assertEquals(0, jigsaws.get(0).pos().getY(), hut + ": the connector is on the foundation row");
        }
        // the camp's connectors: jetty seaward (north), the paths east and west, the huts inland (south), all on the sand
        Set<String> pathSides = new TreeSet<>();
        for (Jigsaw j : jigsaws(island("camp_start"))) {
            assertEquals(0, j.pos().getY(), j.name());
            switch (j.name().substring(MOD.length() + 1)) {
                case "jetty_out" -> assertEquals("north", j.front());
                case "hut_out" -> assertEquals("south", j.front());
                case "path_out" -> pathSides.add(j.front());
                default -> fail("unexpected " + j.name());
            }
        }
        assertEquals(Set.of("east", "west"), pathSides);
    }

    @Test
    void treasureIsBuriedTwoBlocksUnderTheSurface() throws IOException {
        CompoundTag spot = island("treasure_spot");
        int[] size = size(spot);
        List<Jigsaw> jigsaws = jigsaws(spot);
        Jigsaw in = jigsaws.stream().filter(j -> j.name().endsWith("hut_in")).findFirst().orElseThrow();
        Jigsaw treasure = jigsaws.stream().filter(j -> j.name().equals(MOD + ":treasure")).findFirst().orElseThrow();
        // hut_in sits on the surface row, so the piece's surface lines up with the trail it hangs from
        assertEquals(in.pos().getY() - 2, treasure.pos().getY(), "two blocks under the surface");
        assertEquals(size[0] / 2, treasure.pos().getX(), "at the piece's centre");
        assertEquals(size[2] / 2, treasure.pos().getZ(), "at the piece's centre");
        // buried: sand above the marker up to the surface
        ListTag palette = spot.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = spot.getList("blocks", Tag.TAG_COMPOUND);
        Set<Integer> sandAbove = new HashSet<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            if (pos.getInt(0) == treasure.pos().getX() && pos.getInt(2) == treasure.pos().getZ()
                    && pos.getInt(1) > treasure.pos().getY() && pos.getInt(1) <= in.pos().getY()
                    && palette.getCompound(b.getInt("state")).getString("Name").equals("minecraft:sand")) {
                sandAbove.add(pos.getInt(1));
            }
        }
        assertEquals(2, sandAbove.size(), "sand covers the treasure");
        // no other piece buries treasure
        for (String[] gp : allPieces()) {
            if (!gp[1].equals("treasure_spot")) {
                assertFalse(countNames(jigsaws(piece(gp[0], gp[1]))).containsKey("treasure"), gp[0] + "/" + gp[1]);
            }
        }
    }

    /** The committed NBT is exactly what the converter makes from the committed schematics. */
    @Test
    void committedPiecesAreUpToDate() throws Exception {
        Assumptions.assumeTrue(pythonWorks(), "python3 is not available");
        for (Map.Entry<String, List<String>> group : GROUPS.entrySet()) {
            Path out = Files.createDirectories(tmp.resolve(group.getKey()));
            List<String> cmd = new ArrayList<>(List.of("python3", "-I", root.resolve("tools/schem_to_structure.py").toString(),
                    "--out", out.toString()));
            for (String name : group.getValue()) {
                cmd.add(root.resolve("art/schematics/structures/" + group.getKey() + "/" + name + ".schem").toString());
            }
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(p.waitFor(60, TimeUnit.SECONDS), "converter timed out");
            assertEquals(0, p.exitValue(), log);
            for (String name : group.getValue()) {
                assertArrayEquals(Files.readAllBytes(groupDir(group.getKey()).resolve(name + ".nbt")),
                        Files.readAllBytes(out.resolve(name + ".nbt")),
                        group.getKey() + "/" + name + ": rerun python3 tools/build_structures.py (or tools/schem_to_structure.py) and commit the result");
            }
            assertTrue(log.contains("jigsaw at"), log);
            assertFalse(log.contains("helm"), "pieces are converted as structures, not ships: " + log);
        }
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

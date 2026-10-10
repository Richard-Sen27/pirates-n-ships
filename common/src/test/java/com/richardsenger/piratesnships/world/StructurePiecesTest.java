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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Keeps the committed structure pieces ({@code data/pirates_n_ships/structure/<group>/*.nbt}: the seafarer village,
 * ST1, the pirate island, ST2, and the navy outpost, ST3) honest: they load as vanilla structure templates, stay within
 * the size bounds, use only existing blocks and states, follow their group's jigsaw convention of art/README.md
 * ("Structures (ST1)", "Pirate island (ST2)", "Navy outpost (ST3)") and are exactly what the converter makes from the
 * committed schematics. Mod blocks are checked against the datagen block states (every mod block has one).
 */
class StructurePiecesTest {

    private static final String MOD = "pirates_n_ships";
    private static final String EMPTY = "minecraft:empty";
    private static final int MAX_SIZE = 32;

    /**
     * A piece set: its folder under {@code structure/}, its pieces and its jigsaw convention (name -> target, pool,
     * joint). The shared {@link #MARKERS} are valid in every group and are not part of a convention.
     */
    private record Group(String name, List<String> pieces, Map<String, List<String>> convention) {
    }

    private static final Group VILLAGE = new Group("village",
            List.of("dock_head", "house_small", "pier", "shipwright", "street", "street_end", "tavern"),
            Map.of(
                    MOD + ":street_out", List.of(MOD + ":street_in", MOD + ":village/streets", "aligned"),
                    MOD + ":street_in", List.of(MOD + ":street_out", EMPTY, "aligned"),
                    MOD + ":building_out", List.of(MOD + ":building_in", MOD + ":village/buildings", "rollable"),
                    MOD + ":building_in", List.of(MOD + ":building_out", EMPTY, "rollable"),
                    MOD + ":pier_out", List.of(MOD + ":pier_in", MOD + ":village/pier", "aligned"),
                    MOD + ":pier_in", List.of(MOD + ":pier_out", EMPTY, "aligned")));
    private static final Set<String> BUILDINGS = Set.of("house_small", "shipwright", "tavern");

    /** The pirate island (ST2; STR2's path network, building looks and decorations). */
    private static final Group ISLAND = new Group("pirate_island",
            List.of("camp_start", "captains_cabin", "captains_hut", "captains_lookout",
                    "deco_campfire", "deco_crates", "deco_fish_rack", "deco_hammock", "deco_jolly_roger", "deco_palm",
                    "deco_rocks", "deco_rum_cask", "deco_skull_post", "deco_washing_line",
                    "hut_porch", "hut_stilts", "hut_thatch", "jetty", "longboat", "lookout_tower",
                    "path", "path_bend_left", "path_bend_right", "path_clearing", "path_end", "path_end_palm",
                    "path_end_rocks", "path_fork_left", "path_fork_right", "path_junction", "path_long",
                    "powder_store", "smokehouse", "tavern_deck", "tavern_hut", "tavern_long",
                    "tent", "tent_large", "tent_lean", "tent_small", "treasure_cairn", "treasure_spot", "treasure_wreck"),
            Map.of(
                    MOD + ":path_out", List.of(MOD + ":path_in", MOD + ":pirate_island/paths", "aligned"),
                    MOD + ":path_in", List.of(MOD + ":path_out", EMPTY, "aligned"),
                    MOD + ":hut_out", List.of(MOD + ":hut_in", MOD + ":pirate_island/huts", "rollable"),
                    MOD + ":hut_in", List.of(MOD + ":hut_out", EMPTY, "rollable"),
                    MOD + ":jetty_out", List.of(MOD + ":jetty_in", MOD + ":pirate_island/jetty", "aligned"),
                    MOD + ":jetty_in", List.of(MOD + ":jetty_out", EMPTY, "aligned"),
                    MOD + ":trailhead_out", List.of(MOD + ":path_in", MOD + ":pirate_island/trailheads", "aligned"),
                    MOD + ":deco_out", List.of(MOD + ":deco_in", MOD + ":pirate_island/decorations", "rollable"),
                    MOD + ":deco_in", List.of(MOD + ":deco_out", EMPTY, "rollable")));
    /** The buildings of the huts pool (the treasure looks are checked on their own). */
    private static final Set<String> HUTS = Set.of("captains_cabin", "captains_hut", "captains_lookout", "hut_porch",
            "hut_stilts", "hut_thatch", "longboat", "lookout_tower", "powder_store", "smokehouse", "tavern_deck",
            "tavern_hut", "tavern_long", "tent", "tent_large", "tent_lean", "tent_small");
    private static final Set<String> TREASURES = Set.of("treasure_cairn", "treasure_spot", "treasure_wreck");

    /**
     * The navy outpost (ST3; STR4's wall trace, court, buildings and decorations): each run of the curtain wall has its
     * own jigsaw pair, each state of the trace its own out connectors (art/README.md).
     */
    private static final Group NAVY = new Group("navy_outpost",
            List.of("armoury", "barracks", "barracks_long",
                    "barracks_mess", "battery", "brig", "brig_large", "chapel",
                    "deco_bell_post", "deco_benches", "deco_cannon_balls", "deco_cart", "deco_crates", "deco_drill_target",
                    "deco_lantern_post", "deco_notice_board", "deco_tree", "deco_water_trough",
                    "flank_angle_east", "flank_angle_west", "flank_bastion", "flank_long", "flank_sally_port", "flank_stair",
                    "flank_step_east", "flank_step_west", "flank_wall", "fort_gate", "gun_battery",
                    "lane", "lane_bend_left", "lane_bend_right", "lane_end_privy", "lane_end_store", "lane_end_woodpile",
                    "lane_fork_left", "lane_fork_right", "lane_junction", "lane_long", "officers_quarters", "parade_ground",
                    "powder_magazine", "quay", "round_tower", "signal_tower", "store_house",
                    "wall", "wall_angle_east", "wall_angle_west", "wall_bastion", "wall_long", "wall_sally_port", "wall_stair",
                    "wall_tower", "watchtower"),
            Map.ofEntries(
                    Map.entry(MOD + ":wall_east_out", List.of(MOD + ":wall_east_in", MOD + ":navy_outpost/walls", "aligned")),
                    Map.entry(MOD + ":wall_east_in", List.of(MOD + ":wall_east_out", EMPTY, "aligned")),
                    Map.entry(MOD + ":wall_west_out", List.of(MOD + ":wall_west_in", MOD + ":navy_outpost/walls", "aligned")),
                    Map.entry(MOD + ":wall_west_in", List.of(MOD + ":wall_west_out", EMPTY, "aligned")),
                    Map.entry(MOD + ":gate_east_out", List.of(MOD + ":wall_east_in", MOD + ":navy_outpost/walls_gate", "aligned")),
                    Map.entry(MOD + ":gate_west_out", List.of(MOD + ":wall_west_in", MOD + ":navy_outpost/walls_gate", "aligned")),
                    Map.entry(MOD + ":flank_east_out", List.of(MOD + ":wall_east_in", MOD + ":navy_outpost/walls_flank", "aligned")),
                    Map.entry(MOD + ":flank_west_out", List.of(MOD + ":wall_west_in", MOD + ":navy_outpost/walls_flank", "aligned")),
                    Map.entry(MOD + ":flankhead_east_out", List.of(MOD + ":wall_east_in", MOD + ":navy_outpost/walls_flank_head", "aligned")),
                    Map.entry(MOD + ":flankhead_west_out", List.of(MOD + ":wall_west_in", MOD + ":navy_outpost/walls_flank_head", "aligned")),
                    Map.entry(MOD + ":end_east_out", List.of(MOD + ":wall_east_in", MOD + ":navy_outpost/terminators", "aligned")),
                    Map.entry(MOD + ":end_west_out", List.of(MOD + ":wall_west_in", MOD + ":navy_outpost/terminators", "aligned")),
                    Map.entry(MOD + ":building_out", List.of(MOD + ":building_in", MOD + ":navy_outpost/buildings", "rollable")),
                    Map.entry(MOD + ":building_in", List.of(MOD + ":building_out", EMPTY, "rollable")),
                    Map.entry(MOD + ":quay_out", List.of(MOD + ":quay_in", MOD + ":navy_outpost/quay", "aligned")),
                    Map.entry(MOD + ":quay_in", List.of(MOD + ":quay_out", EMPTY, "aligned")),
                    Map.entry(MOD + ":lanehead_out", List.of(MOD + ":lane_in", MOD + ":navy_outpost/laneheads", "aligned")),
                    Map.entry(MOD + ":lane_out", List.of(MOD + ":lane_in", MOD + ":navy_outpost/lanes", "aligned")),
                    Map.entry(MOD + ":lane_in", List.of(MOD + ":lane_out", EMPTY, "aligned")),
                    Map.entry(MOD + ":deco_out", List.of(MOD + ":deco_in", MOD + ":navy_outpost/decorations", "rollable")),
                    Map.entry(MOD + ":deco_in", List.of(MOD + ":deco_out", EMPTY, "rollable"))));
    /** The buildings of the outpost's buildings pool (ST3's three and STR4's). */
    private static final Set<String> NAVY_BUILDINGS = Set.of("barracks", "brig", "watchtower", "barracks_long", "barracks_mess",
            "brig_large", "signal_tower", "officers_quarters", "powder_magazine", "armoury", "gun_battery", "store_house", "chapel");
    /** The symmetric wall pieces of every state (both runs' pairs) and the run-ending towers. */
    private static final List<String> NAVY_STRAIGHTS = List.of("wall", "wall_long", "wall_bastion", "wall_sally_port", "wall_stair",
            "flank_wall", "flank_long", "flank_bastion", "flank_sally_port", "flank_stair");
    private static final List<String> NAVY_TOWERS = List.of("wall_tower", "round_tower", "battery");

    private static final List<Group> GROUPS = List.of(VILLAGE, ISLAND, NAVY);

    /** Markers (not connectors), valid in every group: name -> {target, pool, joint, final state}. */
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

    private static Path dir(Group group) {
        return root.resolve("common/src/main/resources/data/" + MOD + "/structure/" + group.name());
    }

    private static CompoundTag piece(Group group, String name) throws IOException {
        return NbtIo.readCompressed(dir(group).resolve(name + ".nbt"), NbtAccounter.unlimitedHeap());
    }

    private static CompoundTag village(String name) throws IOException {
        return piece(VILLAGE, name);
    }

    private static CompoundTag island(String name) throws IOException {
        return piece(ISLAND, name);
    }

    /** A block of a piece: position, properties and block entity data (empty when it has none). */
    private record PieceBlock(BlockPos pos, CompoundTag props, CompoundTag nbt) {
    }

    /** Every block of a piece with the given id ({@code null}: every block that is not air). */
    private static List<PieceBlock> blocks(CompoundTag structure, String id) {
        ListTag palette = structure.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = structure.getList("blocks", Tag.TAG_COMPOUND);
        List<PieceBlock> out = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            CompoundTag state = palette.getCompound(b.getInt("state"));
            String name = state.getString("Name");
            if (id == null ? !name.equals("minecraft:air") : name.equals(id)) {
                ListTag pos = b.getList("pos", Tag.TAG_INT);
                out.add(new PieceBlock(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)),
                        state.getCompound("Properties"), b.getCompound("nbt")));
            }
        }
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
        for (Group group : GROUPS) {
            Set<String> found = new TreeSet<>();
            try (Stream<Path> files = Files.list(dir(group))) {
                files.forEach(f -> found.add(f.getFileName().toString().replace(".nbt", "")));
            }
            assertEquals(new TreeSet<>(group.pieces()), found, group.name());
        }
    }

    @Test
    void piecesLoadAndStayWithinBounds() throws IOException {
        for (Group group : GROUPS) for (String name : group.pieces()) {
            CompoundTag structure = piece(group, name);
            int[] size = size(structure);
            for (int s : size) {
                assertTrue(s > 0 && s <= MAX_SIZE, group.name() + "/" + name + ": size " + java.util.Arrays.toString(size));
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
        for (Group group : GROUPS) for (String name : group.pieces()) {
            ListTag palette = piece(group, name).getList("palette", Tag.TAG_COMPOUND);
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
                                    // a part shown "when side=true" means the side is a boolean that can be false
                                    if (v.equals("true") || v.equals("false")) {
                                        out.get(key).addAll(Set.of("true", "false"));
                                    }
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
        for (Group group : GROUPS) for (String name : group.pieces()) {
            CompoundTag structure = piece(group, name);
            int[] size = size(structure);
            for (Jigsaw j : jigsaws(structure)) {
                String where = group.name() + "/" + name + " jigsaw " + j.name() + " at " + j.pos().toShortString();
                List<String> marker = MARKERS.get(j.name());
                List<String> rule = marker != null ? marker : group.convention().get(j.name());
                assertNotNull(rule, where + ": name not in the " + group.name() + " convention");
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
        assertEquals(Map.of("street_in", 1), countNames(jigsaws(village("street_end"))), "the terminator spawns nothing");
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

    @Test
    void quayBerthsSitAtSeaLevelBesideTheDeck() throws IOException {
        assertBerthsBesideTheDeck(piece(NAVY, "quay"), "quay_in");
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

    // ------------------------------------------------------------------ pirate island (ST2)

    @Test
    void islandConnectorsPerPiece() throws IOException {
        assertEquals(Map.of("jetty_out", 1, "path_out", 2, "trailhead_out", 1), countNames(jigsaws(island("camp_start"))));
        assertEquals(Map.of("berth", 2, "jetty_in", 1), countNames(jigsaws(island("jetty"))));
        // STR2's path network: every trail piece has one path_in; straights and bends one path_out, forks and the
        // junction two, the clearing none (a cul-de-sac of buildings); huts and decorations hang on the sides
        assertEquals(Map.of("hut_out", 2, "path_in", 1, "path_out", 1), countNames(jigsaws(island("path"))));
        assertEquals(Map.of("hut_out", 2, "deco_out", 2, "path_in", 1, "path_out", 1), countNames(jigsaws(island("path_long"))));
        for (String bend : List.of("path_bend_left", "path_bend_right")) {
            assertEquals(Map.of("hut_out", 2, "path_in", 1, "path_out", 1), countNames(jigsaws(island(bend))), bend);
        }
        for (String fork : List.of("path_fork_left", "path_fork_right")) {
            assertEquals(Map.of("deco_out", 1, "hut_out", 1, "path_in", 1, "path_out", 2), countNames(jigsaws(island(fork))), fork);
        }
        assertEquals(Map.of("hut_out", 1, "path_in", 1, "path_out", 2), countNames(jigsaws(island("path_junction"))));
        assertEquals(Map.of("hut_out", 2, "deco_out", 1, "path_in", 1), countNames(jigsaws(island("path_clearing"))));
        for (String end : List.of("path_end", "path_end_rocks", "path_end_palm")) {
            assertEquals(Map.of("path_in", 1), countNames(jigsaws(island(end))), end);
        }
        // a bend turns: its path_out points sideways, not on along the path_in's line
        for (String bend : List.of("path_bend_left", "path_bend_right")) {
            Jigsaw out = jigsaws(island(bend)).stream().filter(j -> j.name().endsWith("path_out")).findFirst().orElseThrow();
            assertEquals(bend.endsWith("left") ? "east" : "west", out.front(), bend);
        }
        for (String treasure : TREASURES) {
            assertEquals(Map.of("hut_in", 1, "treasure", 1), countNames(jigsaws(island(treasure))), treasure);
        }
        for (String name : ISLAND.pieces()) {
            if (!name.startsWith("deco_")) continue;
            List<Jigsaw> jigsaws = jigsaws(island(name));
            assertEquals(Map.of("deco_in", 1), countNames(jigsaws), name);
            assertEquals("north", jigsaws.get(0).front(), name + " faces north");
            assertEquals(0, jigsaws.get(0).pos().getY(), name + ": the connector is on the ground row");
        }
        for (String hut : HUTS) {
            List<Jigsaw> jigsaws = jigsaws(island(hut));
            assertEquals(Map.of("hut_in", 1), countNames(jigsaws), hut);
            assertEquals("north", jigsaws.get(0).front(), hut + " faces north");
            assertEquals(0, jigsaws.get(0).pos().getY(), hut + ": the connector is on the foundation row");
        }
        // the camp's connectors: jetty seaward (north), the trails east and west, the trailhead inland (south), all on the sand
        Set<String> pathSides = new TreeSet<>();
        for (Jigsaw j : jigsaws(island("camp_start"))) {
            assertEquals(0, j.pos().getY(), j.name());
            switch (j.name().substring(MOD.length() + 1)) {
                case "jetty_out" -> assertEquals("north", j.front());
                case "path_out" -> pathSides.add(j.front());
                case "trailhead_out" -> assertEquals("south", j.front(), "the trailhead leads inland");
                default -> fail("unexpected " + j.name());
            }
        }
        assertEquals(Set.of("east", "west"), pathSides);
    }

    @Test
    void treasureIsBuriedTwoBlocksUnderTheSurface() throws IOException {
        for (String look : TREASURES) {
            assertTreasureBuried(look);
        }
        // no other piece buries treasure
        for (Group group : GROUPS) for (String name : group.pieces()) {
            if (group != ISLAND || !TREASURES.contains(name)) {
                assertFalse(countNames(jigsaws(piece(group, name))).containsKey("treasure"), group.name() + "/" + name);
            }
        }
    }

    private static void assertTreasureBuried(String look) throws IOException {
        CompoundTag spot = island(look);
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
        assertEquals(2, sandAbove.size(), look + ": sand covers the treasure");
    }

    /** The committed NBT is exactly what the converter makes from the committed schematics. */
    @Test
    void committedPiecesAreUpToDate() throws Exception {
        Assumptions.assumeTrue(pythonWorks(), "python3 is not available");
        for (Group group : GROUPS) {
            Path out = Files.createDirectories(tmp.resolve(group.name()));
            List<String> cmd = new ArrayList<>(List.of("python3", "-I", root.resolve("tools/schem_to_structure.py").toString(),
                    "--out", out.toString()));
            for (String name : group.pieces()) {
                cmd.add(root.resolve("art/schematics/structures/" + group.name() + "/" + name + ".schem").toString());
            }
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(p.waitFor(60, TimeUnit.SECONDS), "converter timed out");
            assertEquals(0, p.exitValue(), log);
            for (String name : group.pieces()) {
                assertArrayEquals(Files.readAllBytes(dir(group).resolve(name + ".nbt")),
                        Files.readAllBytes(out.resolve(name + ".nbt")),
                        group.name() + "/" + name + ": rerun python3 tools/build_structures.py (or tools/schem_to_structure.py) and commit the result");
            }
            assertTrue(log.contains("jigsaw at"), log);
            assertFalse(log.contains("helm"), "pieces are converted as structures, not ships: " + log);
        }
    }

    // ------------------------------------------------------------------ navy outpost (ST3)

    @Test
    void navyConnectorsPerPiece() throws IOException {
        assertEquals(Map.of("lanehead_out", 1, "quay_out", 1, "gate_east_out", 1, "gate_west_out", 1),
                countNames(jigsaws(piece(NAVY, "fort_gate"))));
        assertEquals(Map.of("berth", 2, "quay_in", 1), countNames(jigsaws(piece(NAVY, "quay"))));
        for (String name : NAVY_STRAIGHTS) {
            String state = name.startsWith("flank") ? "flank" : "wall";
            assertEquals(Map.of("wall_east_in", 1, state + "_east_out", 1, "wall_west_in", 1, state + "_west_out", 1),
                    countNames(jigsaws(piece(NAVY, name))), name);
        }
        for (String tower : NAVY_TOWERS) {
            assertEquals(Map.of("wall_east_in", 1, "wall_west_in", 1), countNames(jigsaws(piece(NAVY, tower))), tower);
        }
        // the angles turn one run inland into the next state, the flank's steps back out into the shore state
        assertEquals(Map.of("wall_east_in", 1, "flankhead_east_out", 1), countNames(jigsaws(piece(NAVY, "wall_angle_east"))));
        assertEquals(Map.of("wall_west_in", 1, "flankhead_west_out", 1), countNames(jigsaws(piece(NAVY, "wall_angle_west"))));
        assertEquals(Map.of("wall_east_in", 1, "end_east_out", 1), countNames(jigsaws(piece(NAVY, "flank_angle_east"))));
        assertEquals(Map.of("wall_west_in", 1, "end_west_out", 1), countNames(jigsaws(piece(NAVY, "flank_angle_west"))));
        assertEquals(Map.of("wall_east_in", 1, "wall_east_out", 1), countNames(jigsaws(piece(NAVY, "flank_step_east"))));
        assertEquals(Map.of("wall_west_in", 1, "wall_west_out", 1), countNames(jigsaws(piece(NAVY, "flank_step_west"))));
        for (String building : NAVY_BUILDINGS) {
            List<Jigsaw> jigsaws = jigsaws(piece(NAVY, building));
            assertEquals(Map.of("building_in", 1), countNames(jigsaws), building);
            assertEquals("north", jigsaws.get(0).front(), building + " faces north");
            assertEquals(0, jigsaws.get(0).pos().getY(), building + ": the connector is on the foundation row");
        }
        // the gate: quay seaward (north), the wall runs east and west, the court's lanes landward (south); all on y 0
        Map<String, String> fronts = Map.of("quay_out", "north", "gate_east_out", "east", "gate_west_out", "west",
                "lanehead_out", "south");
        for (Jigsaw j : jigsaws(piece(NAVY, "fort_gate"))) {
            String name = j.name().substring(MOD.length() + 1);
            assertEquals(fronts.get(name), j.front(), name);
            assertEquals(0, j.pos().getY(), name);
        }
        // the wall connectors are laid out first (placement priority), the lanes place buildings before decorations
        for (Group g : List.of(NAVY)) for (String name : g.pieces()) {
            for (Jigsaw j : jigsaws(piece(NAVY, name))) {
                String n = j.name().substring(MOD.length() + 1);
                int placement = n.endsWith("east_out") || n.endsWith("west_out") ? 2 : 0;
                assertEquals(placement, j.nbt().getInt("placement_priority"), name + " " + n + ": placement priority");
                int selection = n.equals("building_out") ? 2 : n.equals("deco_out") || n.equals("lanehead_out") ? 1 : 0;
                assertEquals(selection, j.nbt().getInt("selection_priority"), name + " " + n + ": selection priority");
            }
        }
    }

    /**
     * A jigsaw only turns a piece, it never mirrors one, so each run of the curtain wall has its own pair: the east pair
     * (out on the east face, in on the west face) and the west pair (the other way round), each on its own row, the
     * same in the gate, every symmetric wall piece and every tower. A piece attached either way lines up with the gate's
     * wall (same z and y; the bastion's line runs five rows back and three up on its footings) and keeps its outside
     * north.
     */
    @Test
    void wallRunsLineUpWithTheGate() throws IOException {
        Map<String, BlockPos> gate = byName(jigsaws(piece(NAVY, "fort_gate")));
        List<String> pieces = new ArrayList<>(NAVY_STRAIGHTS);
        pieces.addAll(NAVY_TOWERS);
        for (String name : pieces) {
            Map<String, BlockPos> wall = byName(jigsaws(piece(NAVY, name)));
            BlockPos offset = name.endsWith("bastion") ? new BlockPos(0, 3, 5) : BlockPos.ZERO;
            for (String run : List.of("east", "west")) {
                BlockPos out = gate.get("gate_" + run + "_out");
                BlockPos in = wall.get("wall_" + run + "_in");
                assertEquals(out.getY() + offset.getY(), in.getY(), name + " " + run + " run: row");
                assertEquals(out.getZ() + offset.getZ(), in.getZ(), name + " " + run + " run: z");
            }
            for (Jigsaw j : jigsaws(piece(NAVY, name))) {
                boolean eastFace = j.name().endsWith("east_out") || j.name().endsWith("west_in");
                assertEquals(eastFace ? "east" : "west", j.front(), name + " " + j.name());
                BlockPos in = wall.get(j.name().contains("east") ? "wall_east_in" : "wall_west_in");
                assertEquals(in.getZ(), j.pos().getZ(), name + " " + j.name() + ": a run's pair on one row");
            }
        }
        Map<String, BlockPos> wall = byName(jigsaws(piece(NAVY, "wall")));
        assertNotEquals(wall.get("wall_east_in").getZ(), wall.get("wall_west_in").getZ(), "the runs need their own rows");
    }

    /**
     * The angles: the next piece's section continues the turned stretch. The east run's salient angle leaves through
     * its south face at x 4 (the next piece, turned clockwise, has its outer skin two columns east of its in connector,
     * on the angle's x 6); the west run's at x 3 (turned anticlockwise, the skin three columns west, on x 0). The steps
     * leave through the north face, the next piece's skin on the step's x 2 (east) or x 6 (west).
     */
    @Test
    void anglesHandTheirSectionOn() throws IOException {
        Map<String, List<Object>> expected = Map.of(
                "wall_angle_east", List.of("flankhead_east_out", new BlockPos(4, 0, 6), "south"),
                "wall_angle_west", List.of("flankhead_west_out", new BlockPos(3, 0, 6), "south"),
                "flank_angle_east", List.of("end_east_out", new BlockPos(4, 0, 6), "south"),
                "flank_angle_west", List.of("end_west_out", new BlockPos(3, 0, 6), "south"),
                "flank_step_east", List.of("wall_east_out", new BlockPos(4, 0, 0), "north"),
                "flank_step_west", List.of("wall_west_out", new BlockPos(3, 0, 0), "north"));
        for (Map.Entry<String, List<Object>> e : expected.entrySet()) {
            Jigsaw out = jigsaws(piece(NAVY, e.getKey())).stream().filter(j -> j.name().endsWith((String) e.getValue().get(0)))
                    .findFirst().orElseThrow();
            assertEquals(e.getValue().get(1), out.pos(), e.getKey());
            assertEquals(e.getValue().get(2), out.front(), e.getKey());
        }
    }

    private static Map<String, BlockPos> byName(List<Jigsaw> jigsaws) {
        Map<String, BlockPos> out = new HashMap<>();
        for (Jigsaw j : jigsaws) {
            assertNull(out.put(j.name().substring(MOD.length() + 1), j.pos()), "one " + j.name() + " per piece");
        }
        return out;
    }

    /** Every piece of the wall trace mounts at least one whole cannon over its outer face, muzzle out (north). */
    @Test
    void everyWallPieceMountsAGun() throws IOException {
        List<String> pieces = new ArrayList<>(NAVY_STRAIGHTS);
        pieces.addAll(List.of("wall_angle_east", "wall_angle_west", "flank_angle_east", "flank_angle_west", "battery"));
        for (String name : pieces) {
            List<PieceBlock> cannon = blocks(piece(NAVY, name), MOD + ":cannon");
            List<PieceBlock> fronts = cannon.stream().filter(b -> b.props().getString("part").equals("front")).toList();
            assertTrue(fronts.size() >= 1, name + " mounts a gun");
            assertEquals(2 * fronts.size(), cannon.size(), name + ": whole guns");
            for (PieceBlock front : fronts) {
                assertEquals("north", front.props().getString("facing"), name + ": the muzzle points out");
                assertTrue(cannon.stream().anyMatch(b -> b.pos().equals(front.pos().south()) && b.props().getString("part").equals("rear")),
                        name + ": the rear sits behind the master at " + front.pos().toShortString());
            }
        }
    }

    /** The wall carries one whole cannon facing the sea: the master (front) with the rear one block behind it. */
    @Test
    void wallHasOneCannonFacingTheSea() throws IOException {
        CompoundTag wall = piece(NAVY, "wall");
        List<PieceBlock> cannon = blocks(wall, MOD + ":cannon");
        assertEquals(2, cannon.size(), "one two-block cannon");
        PieceBlock front = cannon.stream().filter(b -> b.props().getString("part").equals("front")).findFirst().orElseThrow();
        PieceBlock rear = cannon.stream().filter(b -> b.props().getString("part").equals("rear")).findFirst().orElseThrow();
        assertEquals("north", front.props().getString("facing"), "the muzzle points out to sea");
        assertEquals("north", rear.props().getString("facing"), "both halves share the facing");
        assertEquals(front.pos().south(), rear.pos(), "the rear sits behind the master");
        assertEquals("empty", front.props().getString("load"));
        assertEquals("empty", rear.props().getString("load"));
        assertEquals(1, blocks(wall, MOD + ":cargo_barrel").size(), "a powder barrel beside the gun");
        // both halves stand on the walkway, and the embrasure in front of the muzzle is open to the box face
        Set<BlockPos> solid = new HashSet<>();
        for (PieceBlock b : blocks(wall, null)) {
            solid.add(b.pos());
        }
        assertTrue(solid.contains(front.pos().below()) && solid.contains(rear.pos().below()), "the cannon stands on the walkway");
        for (BlockPos p = front.pos().north(); p.getZ() >= 0; p = p.north()) {
            assertFalse(solid.contains(p), "embrasure blocked at " + p.toShortString());
        }
    }

    /** The gate, the tower and the parade ground (STR4) fly the navy flag: one pole each, its top holding the flag. */
    @Test
    void navyFlagsAreHoisted() throws IOException {
        for (String name : List.of("fort_gate", "wall_tower", "parade_ground")) {
            List<PieceBlock> poles = blocks(piece(NAVY, name), MOD + ":flagpole");
            List<PieceBlock> flying = poles.stream().filter(b -> b.props().getString("flag").equals("navy")).toList();
            assertEquals(1, flying.size(), name + ": one navy flag");
            CompoundTag flag = flying.get(0).nbt().getCompound("flagpole");
            assertEquals("navy", flag.getString("kind"), name);
            assertEquals(MOD + ":navy_flag", flag.getCompound("item").getString("id"), name);
            assertEquals(1, flag.getCompound("item").getInt("count"), name);
            int top = poles.stream().mapToInt(b -> b.pos().getY()).max().orElseThrow();
            assertEquals(top, flying.get(0).pos().getY(), name + ": the flag flies from the top of the pole");
            for (PieceBlock pole : poles) {
                assertEquals(flying.get(0).pos().getX(), pole.pos().getX(), name + ": one pole");
                assertEquals(flying.get(0).pos().getZ(), pole.pos().getZ(), name + ": one pole");
                if (pole != flying.get(0)) {
                    assertEquals("none", pole.props().getString("flag"), name + ": the lower poles fly nothing");
                    assertFalse(pole.nbt().contains("flagpole"), name + ": the lower poles hold no flag");
                }
            }
        }
    }

    /** The brigs' cells: whole brig doors (both halves agree), closed and unlocked, in fronts of brig bars. */
    @Test
    void brigCellsHaveWholeDoors() throws IOException {
        assertBrigCells("brig", 2);
        assertBrigCells("brig_large", 4);
    }

    private static void assertBrigCells(String name, int cells) throws IOException {
        CompoundTag brig = piece(NAVY, name);
        List<PieceBlock> doors = blocks(brig, MOD + ":brig_door");
        assertEquals(2 * cells, doors.size(), name + ": a door per cell, two halves each");
        int lowers = 0;
        for (PieceBlock lower : doors) {
            if (!lower.props().getString("half").equals("lower")) {
                continue;
            }
            lowers++;
            PieceBlock upper = doors.stream().filter(d -> d.pos().equals(lower.pos().above())).findFirst().orElseThrow();
            assertEquals("upper", upper.props().getString("half"));
            for (String key : List.of("facing", "hinge", "locked", "open")) {
                assertEquals(lower.props().getString(key), upper.props().getString(key), key);
            }
            assertEquals("false", lower.props().getString("locked"), "a generated door has no owner");
            assertEquals("false", lower.props().getString("open"));
        }
        assertEquals(cells, lowers, name);
        assertTrue(blocks(brig, MOD + ":brig_bars").size() >= 4 * cells, name + ": cell fronts of brig bars");
    }

    @Test
    void fortGateHasTheOffice() throws IOException {
        CompoundTag gate = piece(NAVY, "fort_gate");
        assertEquals(1, blocks(gate, MOD + ":harbor_desk").size(), "the harbor master's desk");
        assertEquals(1, blocks(gate, MOD + ":notice_board").size(), "a notice board");
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

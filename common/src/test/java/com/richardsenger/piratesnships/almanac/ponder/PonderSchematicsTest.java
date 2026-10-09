package com.richardsenger.piratesnships.almanac.ponder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the Ponder scene schematics ({@code assets/pirates_n_ships/ponder/*.nbt}, ALM4) honest: one per scene of
 * {@link AlmanacScenes#ALL} and nothing else, each loads the way Ponder loads it (a gzipped vanilla structure template),
 * the cannon scene has its blocks where {@link CannonLoadingScene} expects them, and every file is exactly what the
 * converter makes from the committed schematic ({@code art/schematics/structures/ponder/}).
 */
class PonderSchematicsTest {

    private static final String MOD = "pirates_n_ships";

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
        return root.resolve("common/src/main/resources/assets/" + MOD + "/ponder");
    }

    private static CompoundTag schematic(String name) throws IOException {
        // Ponder reads the file gzipped (PonderSceneRegistry.loadSchematic)
        return NbtIo.readCompressed(dir().resolve(name + ".nbt"), NbtAccounter.unlimitedHeap());
    }

    @Test
    void oneSchematicPerScene() throws IOException {
        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.list(dir())) {
            files.forEach(f -> found.add(f.getFileName().toString().replace(".nbt", "")));
        }
        Set<String> expected = new TreeSet<>();
        for (AlmanacScenes.Scene scene : AlmanacScenes.ALL) {
            assertEquals(MOD, scene.schematic().getNamespace());
            expected.add(scene.schematic().getPath());
        }
        assertEquals(expected, found);
    }

    @Test
    void cannonSceneHasItsBlocksWhereTheSceneExpectsThem() throws IOException {
        CompoundTag nbt = schematic(CannonLoadingText.SCENE);
        StructureTemplate template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK.asLookup(), nbt);
        assertEquals(7, template.getSize().getX());
        assertEquals(3, template.getSize().getY());
        assertEquals(7, template.getSize().getZ());

        assertEquals(MOD + ":cannon[facing=north,load=empty,part=front,shot=ball,waterlogged=false]", stateAt(nbt, 3, 1, 1));
        assertEquals(MOD + ":cannon[facing=north,load=empty,part=rear,shot=ball,waterlogged=false]", stateAt(nbt, 3, 1, 2));
        assertEquals(MOD + ":cargo_barrel", stateAt(nbt, 1, 1, 2));
        assertTrue(stateAt(nbt, 5, 1, 2).startsWith("minecraft:chest["), "the shot chest");
        // the gun port: open in front of the muzzle, a lintel above
        assertEquals(null, stateAt(nbt, 3, 1, 0));
        assertTrue(stateAt(nbt, 3, 2, 0).startsWith("minecraft:spruce_slab[type=top"));
        // the base plate (Ponder's showBasePlate: the 7x7 layer y 0) is whole
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                assertEquals("minecraft:spruce_planks", stateAt(nbt, x, 0, z), "deck at " + x + "," + z);
            }
        }
    }

    /** PND1: the blocks each ship scene's storyboard works on stand where it expects them (sizes, then positions). */
    @Test
    void shipScenesHaveTheirBlocksWhereTheScenesExpectThem() throws IOException {
        size(ShipSceneText.SHIP_ASSEMBLY, 9, 5, 9);
        assertTrue(stateAt(schematic(ShipSceneText.SHIP_ASSEMBLY), 4, 4, 6).startsWith(MOD + ":helm[facing=south"));
        assertEquals(MOD + ":cargo_barrel", stateAt(schematic(ShipSceneText.SHIP_ASSEMBLY), 4, 4, 3));
        assertEquals(null, stateAt(schematic(ShipSceneText.SHIP_ASSEMBLY), 4, 2, 4), "the hold is dry");
        assertTrue(stateAt(schematic(ShipSceneText.SHIP_ASSEMBLY), 1, 2, 4).startsWith("minecraft:water"), "the sea");

        size(ShipSceneText.SAILS_AND_WINCH, 7, 8, 7);
        CompoundTag sails = schematic(ShipSceneText.SAILS_AND_WINCH);
        for (int x = 1; x <= 5; x++) {
            assertTrue(stateAt(sails, x, 6, 3).startsWith(MOD + ":yard[axis=x,trim=furled"), "upper yard " + x);
            assertTrue(stateAt(sails, x, 2, 3).startsWith(MOD + ":yard[axis=x"), "lower yard " + x);
        }
        for (int y : new int[] {1, 3, 4, 5, 7}) {
            assertEquals("minecraft:oak_log[axis=y]", stateAt(sails, 3, y, 3), "mast " + y);
        }
        assertEquals(MOD + ":sail_winch[facing=south]", stateAt(sails, 5, 1, 5));

        size(ShipSceneText.STEERING, 11, 5, 11);
        assertTrue(stateAt(schematic(ShipSceneText.STEERING), 5, 4, 8).startsWith(MOD + ":helm[facing=south"));
        assertEquals("minecraft:spruce_planks", stateAt(schematic(ShipSceneText.STEERING), 5, 2, 9), "the stern");

        size(ShipSceneText.ANCHOR_AND_CAPSTAN, 9, 7, 9);
        CompoundTag anchor = schematic(ShipSceneText.ANCHOR_AND_CAPSTAN);
        assertTrue(stateAt(anchor, 3, 6, 4).startsWith(MOD + ":capstan"));
        // the hull AnchorAndCapstanScene.HAWSE assumes: x 1..5 on the deck row, nothing beyond it to starboard
        assertEquals("minecraft:spruce_planks", stateAt(anchor, 5, 5, 4));
        assertEquals(null, stateAt(anchor, 6, 5, 4), "the chain's top cell is free");
        for (int y = 1; y <= 4; y++) {
            assertTrue(stateAt(anchor, 6, y, 4).startsWith("minecraft:water"), "the chain's column is sea at " + y);
        }

        size(ShipSceneText.PUMPS_AND_FLOODING, 9, 5, 9);
        CompoundTag pumps = schematic(ShipSceneText.PUMPS_AND_FLOODING);
        assertTrue(stateAt(pumps, 3, 2, 3).startsWith(MOD + ":bilge_pump[facing=south"));
        assertEquals("minecraft:spruce_planks", stateAt(pumps, 6, 2, 4), "the wall block the breach removes");
        assertEquals(null, stateAt(pumps, 4, 3, 4), "the hold is dry");

        size(ShipSceneText.HULL_REPAIR, 7, 4, 7);
        assertTrue(stateAt(schematic(ShipSceneText.HULL_REPAIR), 5, 2, 3).startsWith("minecraft:water"), "the breach");
        assertEquals("minecraft:spruce_planks", stateAt(schematic(ShipSceneText.HULL_REPAIR), 5, 2, 2), "its edge");

        size(ShipSceneText.MOORING, 9, 5, 9);
        CompoundTag mooring = schematic(ShipSceneText.MOORING);
        assertTrue(stateAt(mooring, 2, 4, 4).startsWith(MOD + ":mooring_ring[face=floor"), "the quay's ring");
        assertTrue(stateAt(mooring, 6, 4, 4).startsWith(MOD + ":mooring_ring[face=floor"), "the ship's ring");
        assertEquals("minecraft:spruce_planks", stateAt(mooring, 8, 1, 4), "the ship lies at the east edge");

        size(ShipSceneText.CARGO_AND_WEIGHT, 9, 6, 11);
        CompoundTag cargo = schematic(ShipSceneText.CARGO_AND_WEIGHT);
        for (int[] c : new int[][] {{3, 5, 8}, {5, 5, 8}, {3, 5, 5}, {5, 5, 5}}) {
            assertEquals(MOD + ":cargo_crate[load=0]", stateAt(cargo, c[0], c[1], c[2]));
        }
    }

    /** PND2: the blocks each crew scene's storyboard works on stand where it expects them. */
    @Test
    void crewScenesHaveTheirBlocksWhereTheScenesExpectThem() throws IOException {
        size(CrewSceneText.CREW_ORDERS, 11, 5, 11);
        CompoundTag orders = schematic(CrewSceneText.CREW_ORDERS);
        assertEquals(MOD + ":harbor_desk[facing=east,waterlogged=false]", stateAt(orders, CrewOrdersScene.DESK));
        assertEquals(MOD + ":sail_winch[facing=south]", stateAt(orders, CrewOrdersScene.WINCH));
        assertTrue(stateAt(orders, CrewOrdersScene.CANNON).startsWith(MOD + ":cannon[facing=east,load=empty,part=front"));
        assertTrue(stateAt(orders, CrewOrdersScene.CANNON.west()).startsWith(MOD + ":cannon[facing=east,load=empty,part=rear"));
        assertEquals(MOD + ":cargo_barrel", stateAt(orders, CrewOrdersScene.SUPPLY), "the gun's supply");
        assertTrue(stateAt(orders, CrewOrdersScene.HELM).startsWith(MOD + ":helm[facing=south"));
        assertEquals("minecraft:stone_bricks", stateAt(orders, 0, 3, 5), "the quay, level with the deck");
        assertEquals(null, stateAt(orders, 0, 4, 5), "the harbor master's spot behind the desk");
        for (int[] c : new int[][] {{4, 4, 5}, {4, 4, 4}, {5, 4, 6}, {7, 4, 5}, {6, 4, 5}}) {
            assertEquals(null, stateAt(orders, c[0], c[1], c[2]), "deck spot " + c[0] + "," + c[2] + " is free to stand on");
            assertEquals("minecraft:spruce_planks", stateAt(orders, c[0], c[1] - 1, c[2]), "deck under " + c[0] + "," + c[2]);
        }

        size(CrewSceneText.BOARDING, 13, 5, 13);
        CompoundTag boarding = schematic(CrewSceneText.BOARDING);
        assertEquals("minecraft:spruce_planks", stateAt(boarding, BoardingScene.GUNWALE), "our gunwale");
        for (BlockPos cell : BoardingScene.RUN) {
            assertEquals(null, stateAt(boarding, cell), "the plank's cell " + cell + " is free");
            assertEquals(null, stateAt(boarding, cell.below()), "nothing under " + cell + " until she comes alongside");
        }
        // her west gunwale, hauled three blocks west, ends under the run's tip
        BlockPos tipRail = BoardingScene.RUN.get(1).below().east(BoardingScene.HAUL);
        assertEquals("minecraft:spruce_planks", stateAt(boarding, tipRail), "her gunwale before the haul");
        assertEquals(null, stateAt(boarding, tipRail.west()), "the sea west of her");
        assertTrue(stateAt(boarding, BoardingScene.THEIR_POLE).startsWith(MOD + ":flagpole[facing=north,flag=jolly_roger"));
        assertTrue(stateAt(boarding, 0, 0, 0).startsWith("minecraft:water"), "open sea as the base plate");

        size(CrewSceneText.FLAGS_AND_ALLEGIANCE, 13, 6, 13);
        CompoundTag flags = schematic(CrewSceneText.FLAGS_AND_ALLEGIANCE);
        assertTrue(stateAt(flags, FlagsAndAllegianceScene.OUR_HEAD).startsWith(MOD + ":flagpole[facing=north,flag=none,part=top"));
        // FLG3: on a pedestal the lowest pole block shows the middle part (the pedestal carries the cleat)
        assertTrue(stateAt(flags, FlagsAndAllegianceScene.OUR_HEAD.below()).startsWith(MOD + ":flagpole[facing=north,flag=none,part=middle"));
        assertEquals(MOD + ":flag_pedestal[pole=true,waterlogged=false]", stateAt(flags, FlagsAndAllegianceScene.PEDESTAL));
        assertEquals(FlagsAndAllegianceScene.PEDESTAL, FlagsAndAllegianceScene.OUR_HEAD.below(2));
        assertTrue(stateAt(flags, FlagsAndAllegianceScene.NAVY_HEAD).startsWith(MOD + ":flagpole[facing=north,flag=navy,part=top"));
        assertEquals("minecraft:spruce_planks", stateAt(flags, 9, 2, 5), "the patrol's deck under her soldier");

        size(CrewSceneText.CROWS_NEST, 7, 9, 7);
        CompoundTag nest = schematic(CrewSceneText.CROWS_NEST);
        assertEquals(MOD + ":crows_nest[pole=false,waterlogged=false]", stateAt(nest, CrowsNestScene.NEST));
        assertTrue(stateAt(nest, CrowsNestScene.POLE).startsWith(MOD + ":flagpole[facing=north,flag=none,part=top"),
                "FLG3: a one-block pole on a footing shows the top part");
        assertEquals(CrowsNestScene.NEST.above(), CrowsNestScene.POLE);
        for (int y = 1; y <= 6; y++) {
            assertEquals("minecraft:oak_log[axis=y]", stateAt(nest, 3, y, 3), "mast " + y);
        }
        assertEquals(MOD + ":ratlines[facing=north,kind=slope,waterlogged=false]", stateAt(nest, 3, 1, 6));
        assertEquals(MOD + ":ratlines[facing=south,kind=wall,waterlogged=false]", stateAt(nest, 3, 7, 4), "the net on the nest");

        size(CrewSceneText.SHIPS_BELL, 5, 4, 5);
        CompoundTag bell = schematic(CrewSceneText.SHIPS_BELL);
        assertEquals(MOD + ":ships_bell[face=wall,facing=south,ringing=false,waterlogged=false]", stateAt(bell, ShipsBellScene.WALL_BELL));
        assertEquals(MOD + ":ships_bell[face=floor,facing=south,ringing=false,waterlogged=false]", stateAt(bell, ShipsBellScene.POST_BELL));
        assertTrue(stateAt(bell, ShipsBellScene.POST_BELL.below()).startsWith("minecraft:spruce_fence"), "the post");
        assertEquals("minecraft:spruce_planks", stateAt(bell, ShipsBellScene.WALL_BELL.north()), "the wall behind the bracket");

        size(CrewSceneText.GALLEY_AND_PROVISIONS, 7, 3, 7);
        CompoundTag galley = schematic(CrewSceneText.GALLEY_AND_PROVISIONS);
        assertEquals(MOD + ":pantry[facing=east,load=0]", stateAt(galley, GalleyAndProvisionsScene.PANTRY));
        assertEquals(MOD + ":water_barrel[fill=0,load=0]", stateAt(galley, GalleyAndProvisionsScene.BARREL));
        for (BlockPos spot : GalleyAndProvisionsScene.mealSpots()) {
            assertEquals(null, stateAt(galley, spot), "the meal seat " + spot + " is free");
        }

        size(CrewSceneText.HAMMOCKS_AND_REST, 7, 3, 7);
        CompoundTag rest = schematic(CrewSceneText.HAMMOCKS_AND_REST);
        for (BlockPos foot : HammocksAndRestScene.FEET) {
            assertEquals(MOD + ":hammock[facing=east,part=foot,waterlogged=false]", stateAt(rest, foot));
            assertEquals(MOD + ":hammock[facing=east,part=head,waterlogged=false]", stateAt(rest, foot.east()));
            assertTrue(stateAt(rest, foot.west()).startsWith("minecraft:spruce_fence"), "the support west of " + foot);
            assertTrue(stateAt(rest, foot.east(2)).startsWith("minecraft:spruce_fence"), "the support east of " + foot);
        }
        assertTrue(stateAt(rest, HammocksAndRestScene.COT).startsWith(MOD + ":sea_cot[facing=east,occupied=false,part=foot"));
        assertTrue(stateAt(rest, HammocksAndRestScene.COT.east()).startsWith(MOD + ":sea_cot[facing=east,occupied=false,part=head"));

        size(CrewSceneText.SWIVEL_GUN, 7, 3, 7);
        CompoundTag swivel = schematic(CrewSceneText.SWIVEL_GUN);
        assertEquals(MOD + ":swivel_gun[load=empty,piece=yoke,waterlogged=false]", stateAt(swivel, SwivelGunScene.GUN));
        assertTrue(stateAt(swivel, SwivelGunScene.GUN.below()).startsWith("minecraft:spruce_fence"), "the mount");
    }

    private static String stateAt(CompoundTag structure, BlockPos pos) {
        return stateAt(structure, pos.getX(), pos.getY(), pos.getZ());
    }

    private static void size(String scene, int x, int y, int z) throws IOException {
        StructureTemplate template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK.asLookup(), schematic(scene));
        assertEquals(x, template.getSize().getX(), scene);
        assertEquals(y, template.getSize().getY(), scene);
        assertEquals(z, template.getSize().getZ(), scene);
        // the base plate (y 0) is whole: Ponder fades it in first
        CompoundTag nbt = schematic(scene);
        for (int i = 0; i < x; i++) {
            for (int k = 0; k < z; k++) {
                assertNotNull(stateAt(nbt, i, 0, k), scene + " base plate at " + i + "," + k);
            }
        }
    }

    /** The block state at a template position as {@code name[key=value,...]}, or null for nothing (air). */
    private static String stateAt(CompoundTag structure, int x, int y, int z) {
        ListTag palette = structure.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = structure.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            if (pos.getInt(0) == x && pos.getInt(1) == y && pos.getInt(2) == z) {
                CompoundTag state = palette.getCompound(b.getInt("state"));
                CompoundTag props = state.getCompound("Properties");
                if (props.isEmpty()) return state.getString("Name");
                List<String> pairs = new ArrayList<>();
                for (String key : new TreeSet<>(props.getAllKeys())) pairs.add(key + "=" + props.getString(key));
                return state.getString("Name") + "[" + String.join(",", pairs) + "]";
            }
        }
        return null;
    }

    /** The committed NBT is exactly what the converter makes from the committed schematics. */
    @Test
    void committedSchematicsAreUpToDate() throws Exception {
        Assumptions.assumeTrue(pythonWorks(), "python3 is not available");
        Path out = Files.createDirectories(tmp.resolve("ponder"));
        List<String> cmd = new ArrayList<>(List.of("python3", "-I", root.resolve("tools/schem_to_structure.py").toString(),
                "--out", out.toString()));
        for (AlmanacScenes.Scene scene : AlmanacScenes.ALL) {
            cmd.add(root.resolve("art/schematics/structures/ponder/" + scene.schematic().getPath() + ".schem").toString());
        }
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "converter timed out");
        assertEquals(0, p.exitValue(), log);
        for (AlmanacScenes.Scene scene : AlmanacScenes.ALL) {
            String name = scene.schematic().getPath() + ".nbt";
            assertArrayEquals(Files.readAllBytes(dir().resolve(name)), Files.readAllBytes(out.resolve(name)),
                    "ponder/" + name + ": rerun python3 tools/build_structures.py ponder/" + scene.schematic().getPath()
                            + " and commit the result");
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

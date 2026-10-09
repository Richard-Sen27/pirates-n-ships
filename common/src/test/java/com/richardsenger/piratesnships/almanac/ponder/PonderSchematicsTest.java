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

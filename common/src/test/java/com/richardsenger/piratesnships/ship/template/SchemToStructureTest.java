package com.richardsenger.piratesnships.ship.template;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code tools/schem_to_structure.py} on tiny hand-made Sponge schematics (v2 and v3), read back with vanilla's NBT
 * and structure template code. Skipped when no {@code python3} is on the PATH.
 */
class SchemToStructureTest {

    /** 3 wide (x), 2 high (y), 2 long (z): 12 cells, index x + z*3 + y*6. */
    private static final int W = 3, H = 2, L = 2;
    /** Palette ids above 127 need two varint bytes. */
    private static final int STONE = 1, OAK = 150, CHEST = 200, AIR = 0;

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
        Assumptions.assumeTrue(root != null, "tools/schem_to_structure.py not found above the working directory");
        Assumptions.assumeTrue(pythonWorks(), "python3 is not available");
    }

    private static boolean pythonWorks() {
        try {
            Process p = new ProcessBuilder("python3", "--version").redirectErrorStream(true).start();
            return p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static String convert(Path out, Path... inputs) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("python3", "-I", root.resolve("tools/schem_to_structure.py").toString(),
                "--out", out.toString()));
        for (Path in : inputs) {
            cmd.add(in.toString());
        }
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "converter timed out");
        assertEquals(0, p.exitValue(), log);
        return log;
    }

    /** The cell ids, x fastest, then z, then y. */
    private static int[] cells() {
        int[] ids = new int[W * H * L];
        java.util.Arrays.fill(ids, AIR);
        ids[index(0, 0, 0)] = STONE;
        ids[index(2, 0, 1)] = OAK;
        ids[index(1, 1, 0)] = CHEST;
        ids[index(2, 1, 1)] = STONE;
        return ids;
    }

    private static int index(int x, int y, int z) {
        return x + z * W + y * W * L;
    }

    private static byte[] varints(int[] ids) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int v : ids) {
            while ((v & ~0x7F) != 0) {
                out.write((v & 0x7F) | 0x80);
                v >>>= 7;
            }
            out.write(v);
        }
        return out.toByteArray();
    }

    private static CompoundTag palette() {
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:air", AIR);
        palette.putInt("minecraft:stone", STONE);
        // properties out of order: the converter sorts them
        palette.putInt("minecraft:oak_stairs[half=bottom,facing=east]", OAK);
        palette.putInt("minecraft:chest[facing=south]", CHEST);
        // filler entries so the used ids need two varint bytes
        for (int i = 2; i < 210; i++) {
            if (i != OAK && i != CHEST) {
                palette.putInt("minecraft:filler_" + i, i);
            }
        }
        return palette;
    }

    private static void writeSchem(Path file, CompoundTag root) throws IOException {
        NbtIo.writeCompressed(root, file);
    }

    private static Path v2(Path dir) throws IOException {
        CompoundTag s = new CompoundTag();
        s.putInt("Version", 2);
        s.putInt("DataVersion", 3953);
        s.putShort("Width", (short) W);
        s.putShort("Height", (short) H);
        s.putShort("Length", (short) L);
        s.put("Offset", new IntArrayTag(new int[] {-7, 3, 12}));
        s.put("Palette", palette());
        s.putInt("PaletteMax", 210);
        s.putByteArray("BlockData", varints(cells()));
        ListTag bes = new ListTag();
        CompoundTag chest = new CompoundTag();
        chest.put("Pos", new IntArrayTag(new int[] {1, 1, 0}));
        chest.putString("Id", "minecraft:chest");
        chest.putString("CustomName", "{\"text\":\"Loot\"}");
        bes.add(chest);
        s.put("BlockEntities", bes);
        Path file = dir.resolve("tiny_v2.schem");
        writeSchem(file, s);
        return file;
    }

    private static Path v3(Path dir) throws IOException {
        CompoundTag s = new CompoundTag();
        s.putInt("Version", 3);
        s.putInt("DataVersion", 4000); // newer than 1.21.1: written as 3955
        s.putShort("Width", (short) W);
        s.putShort("Height", (short) H);
        s.putShort("Length", (short) L);
        s.put("Offset", new IntArrayTag(new int[] {0, 0, 0}));
        CompoundTag blocks = new CompoundTag();
        blocks.put("Palette", palette());
        blocks.putByteArray("Data", varints(cells()));
        ListTag bes = new ListTag();
        CompoundTag chest = new CompoundTag();
        chest.put("Pos", new IntArrayTag(new int[] {1, 1, 0}));
        chest.putString("Id", "minecraft:chest");
        CompoundTag data = new CompoundTag();
        data.putString("CustomName", "{\"text\":\"Loot\"}");
        chest.put("Data", data);
        bes.add(chest);
        blocks.put("BlockEntities", bes);
        s.put("Blocks", blocks);
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("Schematic", s);
        Path file = dir.resolve("tiny_v3.schem");
        writeSchem(file, wrapper);
        return file;
    }

    private static CompoundTag read(Path file) throws IOException {
        return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
    }

    /** pos -> "name[props]" (+ " nbt" when the block has data) of a structure file. */
    private static Map<BlockPos, String> blocksOf(CompoundTag structure) {
        ListTag palette = structure.getList("palette", Tag.TAG_COMPOUND);
        Map<BlockPos, String> out = new HashMap<>();
        ListTag blocks = structure.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            CompoundTag entry = palette.getCompound(b.getInt("state"));
            StringBuilder s = new StringBuilder(entry.getString("Name"));
            CompoundTag props = entry.getCompound("Properties");
            if (!props.isEmpty()) {
                s.append('[');
                s.append(String.join(",", props.getAllKeys().stream().sorted().map(k -> k + "=" + props.getString(k)).toList()));
                s.append(']');
            }
            out.put(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), s.toString());
        }
        return out;
    }

    private void assertTiny(CompoundTag structure, int dataVersion) {
        assertEquals(dataVersion, structure.getInt("DataVersion"));
        ListTag size = structure.getList("size", Tag.TAG_INT);
        assertEquals(List.of(W, H, L), List.of(size.getInt(0), size.getInt(1), size.getInt(2)));
        assertTrue(structure.getList("entities", Tag.TAG_COMPOUND).isEmpty());
        Map<BlockPos, String> blocks = blocksOf(structure);
        assertEquals(Map.of(
                new BlockPos(0, 0, 0), "minecraft:stone",
                new BlockPos(2, 0, 1), "minecraft:oak_stairs[facing=east,half=bottom]",
                new BlockPos(1, 1, 0), "minecraft:chest[facing=south]",
                new BlockPos(2, 1, 1), "minecraft:stone"), blocks);
        // the palette holds only used states (no air, no unused filler), sorted
        ListTag palette = structure.getList("palette", Tag.TAG_COMPOUND);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            names.add(palette.getCompound(i).getString("Name"));
        }
        assertEquals(List.of("minecraft:chest", "minecraft:oak_stairs", "minecraft:stone"), names);
        // the chest keeps its data with its id, without the schematic's position keys
        ListTag list = structure.getList("blocks", Tag.TAG_COMPOUND);
        CompoundTag chestNbt = null;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            if (b.contains("nbt")) {
                assertEquals(null, chestNbt, "only the chest has block entity data");
                chestNbt = b.getCompound("nbt");
                assertEquals(List.of(1, 1, 0), List.of(b.getList("pos", Tag.TAG_INT).getInt(0),
                        b.getList("pos", Tag.TAG_INT).getInt(1), b.getList("pos", Tag.TAG_INT).getInt(2)));
            }
        }
        assertTrue(chestNbt != null, "chest data lost");
        assertEquals("minecraft:chest", chestNbt.getString("id"));
        assertEquals("{\"text\":\"Loot\"}", chestNbt.getString("CustomName"));
        assertFalse(chestNbt.contains("Pos") || chestNbt.contains("Id") || chestNbt.contains("Data"));
        // vanilla loads it
        StructureTemplate template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK.asLookup(), structure);
        assertEquals(W, template.getSize().getX());
    }

    @Test
    void version2() throws Exception {
        Path out = tmp.resolve("out");
        String log = convert(out, v2(tmp));
        assertTiny(read(out.resolve("tiny_v2.nbt")), 3953);
        assertTrue(log.contains("Sponge v2"), log);
        assertTrue(log.contains("no helm"), log);
    }

    @Test
    void version3() throws Exception {
        Path out = tmp.resolve("out");
        String log = convert(out, v3(tmp));
        assertTiny(read(out.resolve("tiny_v3.nbt")), 3955);
        assertTrue(log.contains("Sponge v3") && log.contains("newer than 1.21.1"), log);
    }

    @Test
    void outputIsDeterministic() throws Exception {
        Path in = v2(tmp);
        convert(tmp.resolve("a"), in);
        convert(tmp.resolve("b"), in);
        byte[] a = Files.readAllBytes(tmp.resolve("a/tiny_v2.nbt"));
        byte[] b = Files.readAllBytes(tmp.resolve("b/tiny_v2.nbt"));
        assertArrayEquals(a, b);
        // gzip header: no modification time
        assertArrayEquals(new byte[] {0, 0, 0, 0}, java.util.Arrays.copyOfRange(a, 4, 8));
    }

    /** The committed starter sloop structure is exactly what the converter makes from the committed schematic. */
    @Test
    void committedStarterSloopIsUpToDate() throws Exception {
        Path schem = root.resolve("art/schematics/starter_sloop.schem");
        Path committed = root.resolve("common/src/main/resources/data/pirates_n_ships/structure/ships/starter_sloop.nbt");
        Path out = tmp.resolve("sloop");
        String log = convert(out, schem);
        assertArrayEquals(Files.readAllBytes(committed), Files.readAllBytes(out.resolve("starter_sloop.nbt")),
                "rerun python3 tools/schem_to_structure.py and commit the result");
        assertTrue(log.contains("helm at [4, 8, 22]"), log);
    }
}

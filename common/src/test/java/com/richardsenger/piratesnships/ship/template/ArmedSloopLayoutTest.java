package com.richardsenger.piratesnships.ship.template;

import com.richardsenger.piratesnships.worldsim.materialize.GunStocking;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The armed sloops (WS4c) against the committed starter sloop structure: the only differences are the four guns in
 * the waist (master outboard facing out, rear inboard), the bulwark cut out in front of each muzzle (the gun port), and
 * the shot locker in the hold on its stand, within the gun crews' supply range (4) of every gun. Reads the committed
 * {@code .nbt} files ({@code SchemToStructureTest} pins them to their schematics).
 */
class ArmedSloopLayoutTest {

    private static final int SUPPLY_RANGE = 4;
    private static final String CANNON = "pirates_n_ships:cannon";
    private static final BlockPos LOCKER = new BlockPos(4, 3, 14);

    private static Path root;

    @BeforeAll
    static void findRoot() {
        root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("common/src/main/resources/data/pirates_n_ships/structure/ships"))) {
            root = root.getParent();
        }
        Assumptions.assumeTrue(root != null, "ship structures not found above the working directory");
    }

    /** pos -> "name[props]" (props sorted) of a committed ship structure. */
    private static Map<BlockPos, String> blocks(String name) throws IOException {
        CompoundTag s = NbtIo.readCompressed(root.resolve("common/src/main/resources/data/pirates_n_ships/structure/ships/" + name + ".nbt"),
                NbtAccounter.unlimitedHeap());
        ListTag palette = s.getList("palette", Tag.TAG_COMPOUND);
        Map<BlockPos, String> out = new HashMap<>();
        ListTag list = s.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            CompoundTag entry = palette.getCompound(b.getInt("state"));
            StringBuilder sb = new StringBuilder(entry.getString("Name"));
            CompoundTag props = entry.getCompound("Properties");
            if (!props.isEmpty()) {
                sb.append('[').append(String.join(",", props.getAllKeys().stream().sorted().map(k -> k + "=" + props.getString(k)).toList())).append(']');
            }
            out.put(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), sb.toString());
        }
        return out;
    }

    private static String cannon(String facing, String part) {
        return CANNON + "[facing=" + facing + ",load=empty,part=" + part + "]";
    }

    /** The expected changes from the starter sloop: new cell -> state, or null for a removed cell. */
    private static Map<BlockPos, String> expectedChanges() {
        Map<BlockPos, String> out = new TreeMap<>();
        for (int z : new int[] {13, 15}) {
            out.put(new BlockPos(0, 5, z), null);
            out.put(new BlockPos(1, 5, z), cannon("west", "front"));
            out.put(new BlockPos(2, 5, z), cannon("west", "rear"));
            out.put(new BlockPos(8, 5, z), null);
            out.put(new BlockPos(7, 5, z), cannon("east", "front"));
            out.put(new BlockPos(6, 5, z), cannon("east", "rear"));
        }
        out.put(LOCKER, "minecraft:barrel[facing=up,open=false]");
        out.put(LOCKER.below(), "minecraft:spruce_planks");
        return out;
    }

    @ParameterizedTest
    @ValueSource(strings = {"navy_sloop_armed", "pirate_sloop_armed"})
    void onlyTheGunsPortsAndLockerDiffer(String name) throws IOException {
        Map<BlockPos, String> base = blocks("starter_sloop");
        Map<BlockPos, String> armed = blocks(name);
        Map<BlockPos, String> expected = expectedChanges();
        Set<BlockPos> all = new HashSet<>(base.keySet());
        all.addAll(armed.keySet());
        List<String> unexpected = new ArrayList<>();
        for (BlockPos p : all) {
            String was = base.get(p);
            String now = armed.get(p);
            if (expected.containsKey(p)) {
                if (!java.util.Objects.equals(now, expected.get(p))) unexpected.add(p.toShortString() + ": " + now + ", expected " + expected.get(p));
            } else if (!java.util.Objects.equals(was, now)) {
                unexpected.add(p.toShortString() + ": " + was + " -> " + now);
            }
        }
        assertTrue(unexpected.isEmpty(), String.join("\n", unexpected));
        // the gun ports were bulwark, the guns and locker stand on free cells
        for (Map.Entry<BlockPos, String> e : expected.entrySet()) {
            if (e.getValue() == null) {
                assertEquals("minecraft:dark_oak_planks", base.get(e.getKey()), "gun port at " + e.getKey() + " was no bulwark");
            } else {
                assertEquals(null, base.get(e.getKey()), "cell " + e.getKey() + " was not free");
            }
        }
    }

    /** Every gun stands on the deck, its muzzle at a gun port, and the locker feeds all of them. */
    @Test
    void everyGunIsSuppliedAndFiresThroughAPort() throws IOException {
        Map<BlockPos, String> armed = blocks("navy_sloop_armed");
        List<BlockPos> guns = new ArrayList<>();
        armed.forEach((p, s) -> {
            if (s.startsWith(CANNON) && s.contains("part=front")) guns.add(p);
        });
        guns.sort(BlockPos::compareTo);
        assertEquals(4, guns.size(), "guns " + guns);
        for (BlockPos g : guns) {
            boolean west = armed.get(g).contains("facing=west");
            BlockPos ahead = west ? g.west() : g.east();
            BlockPos rear = west ? g.east() : g.west();
            assertEquals(null, armed.get(ahead), "no gun port ahead of " + g);
            assertTrue(armed.get(rear).startsWith(CANNON) && armed.get(rear).contains("part=rear"), "no rear behind " + g);
            assertNotEquals(null, armed.get(g.below()), "gun " + g + " stands on nothing");
            assertNotEquals(null, armed.get(rear.below()), "rear of " + g + " stands on nothing");
            assertNotEquals(null, armed.get(ahead.below()), "the port of " + g + " is not over the hull side");
            assertEquals(null, armed.get(g.above()), "the barrel of " + g + " runs into a block");
        }
        int[] lockerOf = GunStocking.lockerOf(guns, List.of(LOCKER), SUPPLY_RANGE);
        for (int i = 0; i < guns.size(); i++) {
            assertEquals(0, lockerOf[i], "gun " + guns.get(i) + " is out of reach of the locker");
        }
        // the deck above the locker: a barrel opens under it (a chest would not)
        assertTrue(armed.get(LOCKER.above()) != null && armed.get(LOCKER).startsWith("minecraft:barrel"));
        // symmetric: two guns a side at the same z
        assertEquals(List.of(new BlockPos(1, 5, 13), new BlockPos(1, 5, 15), new BlockPos(7, 5, 13), new BlockPos(7, 5, 15)),
                guns.stream().sorted(java.util.Comparator.<BlockPos>comparingInt(p -> p.getX()).thenComparingInt(p -> p.getZ())).toList());
    }
}

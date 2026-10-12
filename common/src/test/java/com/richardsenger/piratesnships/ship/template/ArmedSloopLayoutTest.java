package com.richardsenger.piratesnships.ship.template;

import com.richardsenger.piratesnships.sailing.sail.BlockPoint;
import com.richardsenger.piratesnships.sailing.sail.CleatLookup;
import com.richardsenger.piratesnships.sailing.sail.ClothGeometry;
import com.richardsenger.piratesnships.sailing.sail.SailShape;
import com.richardsenger.piratesnships.sailing.sail.SquareSail;
import com.richardsenger.piratesnships.sailing.sail.StayLinker;
import com.richardsenger.piratesnships.sailing.sail.StayRules;
import com.richardsenger.piratesnships.sailing.sail.TriangularSail;
import com.richardsenger.piratesnships.sailing.sail.YardLinker;
import com.richardsenger.piratesnships.sailing.sail.YardLookup;
import com.richardsenger.piratesnships.sailing.sail.YardRules;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The armed sloops (WS4c, TPL2) against the committed starter sloop structure: the only differences are the four guns
 * in the waist (master outboard facing out, rear inboard), the bulwark cut out in front of each muzzle (the gun port),
 * the shot locker in the hold on its stand, within the gun crews' supply range (4) of every gun, and (TPL2) the crow's
 * nest on the mast top where the flag was, the flag on an ensign staff on the taffrail, and a run of ratlines on each
 * side of the mast from the quarterdeck to the masthead that is climbable link by link without a jump (the last
 * sloped link beside the mast, right above and abaft the upper yard's end) and keeps clear of the yards,
 * the square sail's cloth and the jib. TPL3: all three sloops follow the yard rule (the yards one block forward of the
 * mast) and keep a working jib whose head and clew cleats sit above the upper and below the lower yard. Reads the
 * committed {@code .nbt} files ({@code SchemToStructureTest} pins them to their schematics).
 */
class ArmedSloopLayoutTest {

    private static final int SUPPLY_RANGE = 4;
    private static final String CANNON = "pirates_n_ships:cannon";
    private static final BlockPos LOCKER = new BlockPos(4, 3, 14);
    private static final String RATLINES = "pirates_n_ships:ratlines";
    private static final String SLOPE = RATLINES + "[facing=north,kind=slope,waterlogged=false]";
    /** The mast column (x, z) and its top block; the nest sits on it. */
    private static final int MAST_X = 4, MAST_Z = 13, MAST_TOP = 19;
    private static final BlockPos NEST = new BlockPos(MAST_X, MAST_TOP + 1, MAST_Z);
    /** The ensign staff on the taffrail rail (4, 8, 27). */
    private static final BlockPos ENSIGN = new BlockPos(4, 9, 27);
    /**
     * Each run's foot on the quarterdeck, its last sloped link (beside the mast, right above and abaft the upper yard's
     * end) and the hung links' first height (right above the last sloped link).
     */
    private static final int FOOT_Z = 22, FOOT_Y = 8, TOP_Z = 13, TOP_Y = 17, HUNG_FROM = 18;
    /**
     * The square sail: yards along x at z 12, one block forward of the mast (the yard rule, TPL3), the lower at y 10
     * (x 0..8), the upper at y 16 (x 1..7).
     */
    private static final int YARD_Z = MAST_Z - 1, LOWER_YARD = 10, UPPER_YARD = 16;
    /** The jib (TPL3): head above the upper yard, clew below the lower yard, both in the yards' column; tack on the bowsprit. */
    private static final BlockPoint JIB_HEAD = new BlockPoint(MAST_X, UPPER_YARD + 1, YARD_Z),
            JIB_CLEW = new BlockPoint(MAST_X, LOWER_YARD - 1, YARD_Z), JIB_TACK = new BlockPoint(MAST_X, 7, 1);
    /** The jib's area: 0.5 x 8 (head to clew) x 11 (clew to tack); 42 before TPL3 (0.5 x 7 x 12). */
    private static final double JIB_AREA = 44.0;
    /** sail_visuals.max_belly and flutter_amplitude at their defaults (SailVisualsConfig). */
    private static final float MAX_BELLY = 0.6f, FLUTTER = 0.15f;

    private static Path root;

    @BeforeAll
    static void findRoot() {
        root = shipsRoot();
        Assumptions.assumeTrue(root != null, "ship structures not found above the working directory");
    }

    /** The checkout above the working directory that holds the ship structures, or null (shared with BrigLayoutTest). */
    static Path shipsRoot() {
        Path r = Path.of("").toAbsolutePath();
        while (r != null && !Files.exists(r.resolve("common/src/main/resources/data/pirates_n_ships/structure/ships"))) {
            r = r.getParent();
        }
        return r;
    }

    /** pos -> "name[props]" (props sorted) of a committed ship structure (shared with BrigLayoutTest). */
    static Map<BlockPos, String> blocks(String name) throws IOException {
        Path root = shipsRoot();
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
        // TPL2: the nest where the flag was, the flag on the taffrail, the ratlines
        out.put(NEST, "pirates_n_ships:crows_nest");
        out.put(ENSIGN, "pirates_n_ships:flagpole[facing=north,flag=none,part=bottom]");
        out.put(ENSIGN.above(), "pirates_n_ships:flagpole[facing=north,flag=none,part=top]");
        out.putAll(ratlines());
        return out;
    }

    /** Both runs: sloped from the quarterdeck toward the bow, then hung on the mast's west / east face. */
    private static Map<BlockPos, String> ratlines() {
        Map<BlockPos, String> out = new TreeMap<>();
        for (int x : new int[] {MAST_X - 1, MAST_X + 1}) {
            for (int i = 0; i <= FOOT_Z - TOP_Z; i++) out.put(new BlockPos(x, FOOT_Y + i, FOOT_Z - i), SLOPE);
            String face = x < MAST_X ? "west" : "east";
            for (int y = HUNG_FROM; y <= MAST_TOP; y++) {
                out.put(new BlockPos(x, y, MAST_Z), RATLINES + "[facing=" + face + ",kind=wall,waterlogged=false]");
            }
        }
        return out;
    }

    @ParameterizedTest
    @ValueSource(strings = {"navy_sloop_armed", "pirate_sloop_armed"})
    void onlyTheGunsPortsLockerNestAndRatlinesDiffer(String name) throws IOException {
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
            } else if (e.getKey().equals(NEST)) {
                assertTrue(base.get(NEST).startsWith("pirates_n_ships:flagpole"), "the starter sloop's flag is not at " + NEST);
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

    /** TPL2: the nest stands on the mast top with nothing above it; the flag flies from the taffrail. */
    @ParameterizedTest
    @ValueSource(strings = {"navy_sloop_armed", "pirate_sloop_armed"})
    void theNestCrownsTheMastAndTheFlagFliesAft(String name) throws IOException {
        Map<BlockPos, String> armed = blocks(name);
        assertEquals("pirates_n_ships:crows_nest", armed.get(NEST));
        assertEquals("minecraft:spruce_log[axis=y]", armed.get(NEST.below()), "the nest does not stand on the mast");
        for (int y = NEST.getY() + 1; y < NEST.getY() + 4; y++) {
            assertEquals(null, armed.get(new BlockPos(NEST.getX(), y, NEST.getZ())), "something above the nest at y " + y);
        }
        assertEquals("minecraft:spruce_fence", armed.get(ENSIGN.below()), "the ensign staff does not stand on the taffrail");
        assertEquals(1, armed.values().stream().filter(s -> s.startsWith("pirates_n_ships:flagpole") && s.contains("part=top")).count(),
                "one flag head");
        assertEquals(2, armed.values().stream().filter(s -> s.startsWith("pirates_n_ships:flagpole")).count(), "one two-block staff");
        // the flag's cloth (one block high, 1.5 blocks out from the pole) has open air round the head
        BlockPos head = ENSIGN.above();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) assertEquals(null, armed.get(head.offset(dx, 0, dz)), "the flag's cloth runs into a block");
            }
        }
    }

    /**
     * TPL2, RL1b: each run is one climb from the quarterdeck to the nest without a jump: the foot stands on the deck,
     * every sloped link is one up and one toward the bow from the last, the last sloped link lies on the upper yard's
     * end (a ratlines anchor) right beside the mast, so a climber in it pushes against the mast and rises, the hung
     * links start straight above it, every hung link hangs on a mast log, and the cell above the top link is free beside
     * the nest.
     */
    @ParameterizedTest
    @ValueSource(strings = {"navy_sloop_armed", "pirate_sloop_armed"})
    void eachRunClimbsFromTheQuarterdeckToTheNest(String name) throws IOException {
        Map<BlockPos, String> armed = blocks(name);
        for (int x : new int[] {MAST_X - 1, MAST_X + 1}) {
            BlockPos foot = new BlockPos(x, FOOT_Y, FOOT_Z);
            assertEquals(SLOPE, armed.get(foot), "no foot at " + foot);
            assertEquals("minecraft:spruce_planks", armed.get(foot.below()), "the run at x " + x + " has no deck under its foot");
            assertEquals(null, armed.get(foot.south()), "no room to step onto the run at x " + x);
            BlockPos p = foot;
            while (SLOPE.equals(armed.get(p.above().north()))) p = p.above().north();
            assertEquals(new BlockPos(x, TOP_Y, TOP_Z), p, "the sloped run at x " + x + " breaks off");
            // TPL3: the upper yard moved one block forward; the top link stands on the link before it, right above and
            // abaft the yard's end
            assertEquals(SLOPE, armed.get(p.below().south()), "the top sloped link has no link under it");
            assertEquals(null, armed.get(p.below()), "something under the top sloped link at x " + x);
            assertTrue(String.valueOf(armed.get(p.below().north())).startsWith("pirates_n_ships:yard"),
                    "the upper yard's end is not right in front of and below the top sloped link at x " + x);
            assertEquals(UPPER_YARD, p.getY() - 1, "the top sloped link is not right above the upper yard");
            assertEquals("minecraft:spruce_log[axis=y]", armed.get(new BlockPos(MAST_X, p.getY(), MAST_Z)),
                    "no mast beside the top sloped link to push against");
            assertEquals(null, armed.get(p.above().north()), "a block over the top of the slope at x " + x);
            BlockPos hung = p.above();
            assertEquals(HUNG_FROM, hung.getY());
            String face = x < MAST_X ? "west" : "east";
            for (int y = hung.getY(); y <= MAST_TOP; y++) {
                BlockPos h = new BlockPos(x, y, MAST_Z);
                assertEquals(RATLINES + "[facing=" + face + ",kind=wall,waterlogged=false]", armed.get(h), "hung link at " + h);
                assertEquals("minecraft:spruce_log[axis=y]", armed.get(new BlockPos(MAST_X, y, MAST_Z)), "no mast behind " + h);
            }
            BlockPos above = new BlockPos(x, MAST_TOP + 1, MAST_Z);
            assertEquals(null, armed.get(above), "the way into the nest at x " + x + " is blocked");
            assertEquals(1, Math.abs(above.getX() - NEST.getX()) + Math.abs(above.getZ() - NEST.getZ()), "the run does not end beside the nest");
        }
    }

    /**
     * TPL2: no ratlines block stands in the jib's plane (x 4: the stay and its cleats) or in the square sail's cloth.
     * The cloth hangs from the upper yard to the lower one in the yards' plane (z 12, TPL3) and stands off it to either
     * side by
     * {@link ClothGeometry#clearance} plus the deepest belly and flutter VIS1b draws at the default
     * {@code sail_visuals} values (breathing included); the sloped nets' rope plane (the diagonal of their cell) must
     * stay outside that envelope wherever the wind pushes the cloth, no net stands in the yards' plane, and the hung
     * nets on the mast lie above the upper yard.
     */
    @Test
    void ratlinesKeepClearOfTheClothAndTheJib() throws IOException {
        Map<BlockPos, String> armed = blocks("navy_sloop_armed");
        int drop = UPPER_YARD - LOWER_YARD;
        ClothGeometry cloth = new ClothGeometry(true, 3.5f, 3.5f, 4.5f, 4.5f, drop);
        float reach = SailShape.cap(drop, MAX_BELLY) * (1f + SailShape.BREATH) + FLUTTER * 1.5f;
        double axisZ = YARD_Z + 0.5;
        List<BlockPos> nets = new ArrayList<>();
        armed.forEach((p, s) -> {
            if (s.startsWith(RATLINES)) nets.add(p);
        });
        assertEquals(24, nets.size(), "two runs of twelve");
        for (BlockPos p : nets) {
            assertNotEquals(MAST_X, p.getX(), "a net in the jib's plane at " + p);
            assertNotEquals(YARD_Z, p.getZ(), "a net in the yards' plane at " + p);
            if (armed.get(p).contains("kind=wall")) {
                // the hung links on the mast: all above the upper yard
                assertEquals(MAST_Z, p.getZ(), "a hung net off the mast at " + p);
                assertTrue(p.getY() > UPPER_YARD, "a hung net below the upper yard at " + p);
                continue;
            }
            // the slope rises north: at height t (0..1) in its cell the net is at z = p.z + 1 - t
            for (int k = 0; k <= 20; k++) {
                double t = k / 20.0;
                double y = p.getY() + t;
                double z = p.getZ() + 1 - t;
                double v = (UPPER_YARD + 0.5) - y; // depth below the upper yard's axis
                if (v < 0 || v > drop) continue;   // above the upper yard or below the lower one: no cloth
                double standoff = cloth.clearance((float) v, drop) + reach * Math.sin(Math.PI * v / drop);
                assertTrue(Math.abs(z - axisZ) > standoff, "the net at " + p + " meets the cloth at y " + y + " (z " + z
                        + ", the cloth reaches " + (axisZ + standoff) + ")");
            }
        }
    }

    /**
     * TPL3, the yard rule: on all three sloops both yards stand in the column one block forward of the mast and make
     * one square sail (F5a: only air between their middles, the jib's cleats outside), and the jib's head and clew
     * cleats sit in that column right above the upper and below the lower yard, so the clew search from the head
     * passes both yards (F5b) and a rope from the head to the bowsprit cleat makes a jib of {@link #JIB_AREA}.
     */
    @ParameterizedTest
    @ValueSource(strings = {"starter_sloop", "navy_sloop_armed", "pirate_sloop_armed"})
    void theYardsStandForwardOfTheMastAndTheJibStillSets(String name) throws IOException {
        Map<BlockPos, String> ship = blocks(name);
        for (int y = 1; y <= MAST_TOP; y++) {
            assertEquals("minecraft:spruce_log[axis=y]", ship.get(new BlockPos(MAST_X, y, MAST_Z)), "the mast is broken at y " + y);
        }
        List<int[]> yards = new ArrayList<>();
        ship.forEach((p, s) -> {
            if (s.startsWith("pirates_n_ships:yard")) {
                assertEquals(YARD_Z, p.getZ(), "a yard off the column forward of the mast at " + p);
                yards.add(new int[] {p.getX(), p.getY(), p.getZ()});
            }
        });
        assertEquals(9 + 7, yards.size(), "yard blocks");
        YardLookup yardCells = (x, y, z) -> {
            String s = ship.get(new BlockPos(x, y, z));
            if (s == null) return YardLookup.Cell.AIR;
            if (s.startsWith("pirates_n_ships:yard")) return s.contains("axis=x") ? YardLookup.Cell.YARD_X : YardLookup.Cell.YARD_Z;
            return s.equals("minecraft:spruce_log[axis=y]") ? YardLookup.Cell.MAST : YardLookup.Cell.OTHER;
        };
        List<SquareSail> sails = YardLinker.link(yardCells, yards, YardRules.DEFAULTS).sails();
        assertEquals(1, sails.size(), "square sails " + sails);
        SquareSail sail = sails.get(0);
        assertEquals(List.of(UPPER_YARD, LOWER_YARD, MAST_X, YARD_Z),
                List.of(sail.upper().y(), sail.lower().y(), sail.upper().middleX(), sail.upper().middleZ()));

        CleatLookup cleatCells = (x, y, z) -> {
            String s = ship.get(new BlockPos(x, y, z));
            if (s == null) return CleatLookup.Cell.AIR;
            if (s.startsWith("pirates_n_ships:cleat")) return CleatLookup.Cell.CLEAT;
            if (s.startsWith("pirates_n_ships:yard")) return CleatLookup.Cell.YARD;
            return s.equals("minecraft:spruce_log[axis=y]") ? CleatLookup.Cell.MAST : CleatLookup.Cell.OTHER;
        };
        assertEquals(3, ship.values().stream().filter(s -> s.startsWith("pirates_n_ships:cleat")).count(), "three cleats");
        for (BlockPoint c : List.of(JIB_HEAD, JIB_CLEW)) {
            BlockPos at = new BlockPos(c.x(), c.y(), c.z());
            assertEquals("pirates_n_ships:cleat[face=wall,facing=north]", ship.get(at), "no wall cleat at " + at);
            assertEquals("minecraft:spruce_log[axis=y]", ship.get(at.south()), "the cleat at " + at + " is not on the mast");
        }
        BlockPos tack = new BlockPos(JIB_TACK.x(), JIB_TACK.y(), JIB_TACK.z());
        assertEquals("pirates_n_ships:cleat[face=floor,facing=north]", ship.get(tack), "no tack cleat at " + tack);
        assertEquals("minecraft:spruce_log[axis=z]", ship.get(tack.below()), "the tack is not on the bowsprit");
        assertEquals(JIB_CLEW, StayLinker.findClew(cleatCells, JIB_HEAD, JIB_TACK.y()), "the clew search does not pass the yards");
        TriangularSail jib = StayLinker.sail(cleatCells, JIB_HEAD, JIB_TACK, StayRules.DEFAULTS);
        assertNotNull(jib, "no jib between the head and the bowsprit cleat");
        assertEquals(JIB_AREA, jib.area(), 1e-9);
    }
}

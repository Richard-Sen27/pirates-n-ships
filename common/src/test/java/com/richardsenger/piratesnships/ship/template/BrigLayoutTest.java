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
import com.richardsenger.piratesnships.ship.rigging.NetFitting;
import com.richardsenger.piratesnships.ship.rigging.RailingLatchBlock;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Cell;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import com.richardsenger.piratesnships.worldsim.materialize.GunStocking;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
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
 * The brig (SHIP1, reworked in SHIP2, art/schematics/brig.py) against the rules that hold for the sloops
 * ({@link ArmedSloopLayoutTest}), read from the committed structures ({@code SchemToStructureTest} pins them to their
 * schematics): the armed brigs differ from the orderable brig only by the crow's nest, the ratline fittings and the
 * swivel guns; every brig's guns are supplied and fire through a port; the nest crowns the main mast and the flag flies
 * aft; each ratline net (the template's {@code nets}, strung on placement) climbs from the quarterdeck's rail to a hoop
 * right under the nest and keeps clear of the cloth and the jib; the yards stand forward of the masts, the four square
 * sails and the jib set; the hold is closed below the gun deck; the ballast lies low and even on both sides, and the
 * block count the price is made from is the structure's. Walking to the stations is checked in a world
 * ({@code BrigGameTests}), floating upright in {@code BrigTrimGameTests}.
 */
class BrigLayoutTest {

    private static final String CANNON = "pirates_n_ships:cannon";
    private static final String MAST = "minecraft:spruce_log[axis=y]";
    private static final String DECK = "minecraft:spruce_planks";
    private static final int SUPPLY_RANGE = 4;
    private static final int FORE_Z = 11, MAIN_Z = 19, FORE_TOP = 23, MAIN_TOP = 25, MAST_X = 4;
    /** The yards of each mast (one block forward of it), lowest first. */
    private static final int[] FORE_YARDS = {12, 17, 21}, MAIN_YARDS = {12, 18, 23};
    private static final int[] FORE_HALF = {4, 3, 2}, MAIN_HALF = {4, 3, 2};
    private static final BlockPos NEST = new BlockPos(MAST_X, MAIN_TOP + 1, MAIN_Z);
    private static final int[] GUN_ZS = {13, 17, 21, 25};
    private static final List<BlockPos> LOCKERS = List.of(new BlockPos(1, 6, 15), new BlockPos(1, 6, 23),
            new BlockPos(7, 6, 15), new BlockPos(7, 6, 23));
    private static final List<BlockPos> SWIVELS = List.of(new BlockPos(1, 11, 29), new BlockPos(7, 11, 29));
    private static final BlockPos FLAG_FOOT = new BlockPos(MAST_X, 10, 29);
    private static final String BALLAST = "minecraft:cobbled_deepslate";
    private static final BlockPoint JIB_HEAD = new BlockPoint(MAST_X, 22, FORE_Z - 1), JIB_CLEW = new BlockPoint(MAST_X, 11, FORE_Z - 1),
            JIB_TACK = new BlockPoint(MAST_X, 10, 1);
    /** The jib's area: 0.5 x 11 (head to clew) x 9 (clew to tack). */
    private static final double JIB_AREA = 49.5;
    /** sail_visuals.max_belly and flutter_amplitude at their defaults (SailVisualsConfig), as in ArmedSloopLayoutTest. */
    private static final float MAX_BELLY = 0.6f, FLUTTER = 0.15f;
    /** rigging.net_max_length, net_min_pitch and net_max_spread at their defaults. */
    private static final double NET_MAX_LENGTH = 32.0, NET_MIN_PITCH = 30.0, NET_MAX_SPREAD = 6.0;

    @BeforeAll
    static void findRoot() {
        Assumptions.assumeTrue(ArmedSloopLayoutTest.shipsRoot() != null, "ship structures not found above the working directory");
    }

    private static Map<BlockPos, String> blocks(String name) throws IOException {
        return ArmedSloopLayoutTest.blocks(name);
    }

    private static String cannon(String facing, String part) {
        return CANNON + "[facing=" + facing + ",load=empty,part=" + part + "]";
    }

    private static String port(String facing, boolean open) {
        return "pirates_n_ships:dark_oak_bulwark_port[facing=" + facing + ",open=" + open + ",propped=false,waterlogged=false]";
    }

    /** The armed brig's changes against the orderable brig: new cell -> state. */
    private static Map<BlockPos, String> expectedChanges() {
        Map<BlockPos, String> out = new TreeMap<>();
        out.put(NEST, "pirates_n_ships:crows_nest");
        out.put(new BlockPos(MAST_X - 1, MAIN_TOP, MAIN_Z), "pirates_n_ships:mast_hoop[facing=west,waterlogged=false]");
        out.put(new BlockPos(MAST_X + 1, MAIN_TOP, MAIN_Z), "pirates_n_ships:mast_hoop[facing=east,waterlogged=false]");
        for (int z = 26; z <= 28; z++) {
            out.put(new BlockPos(1, 11, z), "pirates_n_ships:railing_latch[facing=west,waterlogged=false]");
            out.put(new BlockPos(7, 11, z), "pirates_n_ships:railing_latch[facing=east,waterlogged=false]");
        }
        for (BlockPos s : SWIVELS) out.put(s, "pirates_n_ships:swivel_gun");
        return out;
    }

    @ParameterizedTest
    @ValueSource(strings = {"navy_brig_armed", "pirate_brig_armed"})
    void onlyTheNestRatlineFittingsAndSwivelsDiffer(String name) throws IOException {
        Map<BlockPos, String> base = blocks("brig");
        Map<BlockPos, String> armed = blocks(name);
        Map<BlockPos, String> expected = expectedChanges();
        Set<BlockPos> all = new HashSet<>(base.keySet());
        all.addAll(armed.keySet());
        List<String> unexpected = new ArrayList<>();
        for (BlockPos p : all) {
            String was = base.get(p);
            String now = armed.get(p);
            if (expected.containsKey(p)) {
                if (!Objects.equals(now, expected.get(p))) unexpected.add(p.toShortString() + ": " + now + ", expected " + expected.get(p));
            } else if (!Objects.equals(was, now)) {
                unexpected.add(p.toShortString() + ": " + was + " -> " + now);
            }
        }
        assertTrue(unexpected.isEmpty(), String.join("\n", unexpected));
        for (BlockPos p : expected.keySet()) assertEquals(null, base.get(p), "cell " + p + " was not free in the orderable brig");
    }

    /**
     * Four guns a side on the lower gun deck: each muzzle (master) right inboard of an open gun port on the hull side
     * with its rear inboard of it, both on the deck, the barrel clear above, its crew's place behind the rear free; every
     * gun within the gun crews' supply range (4) of a shot locker, two guns per locker; a barrel, which opens under the
     * weather deck. SHIP2: the orderable brig carries them as the armed ones do.
     */
    @ParameterizedTest
    @ValueSource(strings = {"brig", "navy_brig_armed", "pirate_brig_armed"})
    void everyGunIsSuppliedAndFiresThroughAPort(String name) throws IOException {
        Map<BlockPos, String> armed = blocks(name);
        List<BlockPos> guns = new ArrayList<>();
        armed.forEach((p, s) -> {
            if (s.startsWith(CANNON) && s.contains("part=front")) guns.add(p);
        });
        guns.sort(Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        List<BlockPos> expected = new ArrayList<>();
        for (int x : new int[] {1, 7}) for (int z : GUN_ZS) expected.add(new BlockPos(x, 6, z));
        assertEquals(expected, guns);
        for (BlockPos g : guns) {
            boolean west = armed.get(g).contains("facing=west");
            BlockPos ahead = west ? g.west() : g.east();
            BlockPos rear = west ? g.east() : g.west();
            BlockPos crew = west ? rear.east() : rear.west();
            assertEquals(port(west ? "west" : "east", true), armed.get(ahead), "no open gun port ahead of " + g);
            assertTrue(armed.get(rear).startsWith(CANNON) && armed.get(rear).contains("part=rear"), "no rear behind " + g);
            assertEquals(DECK, armed.get(g.below()), "gun " + g + " is not on the gun deck");
            assertEquals(DECK, armed.get(rear.below()), "rear of " + g + " is not on the gun deck");
            assertNotEquals(null, armed.get(ahead.below()), "the port of " + g + " is not over the hull side");
            assertEquals(null, armed.get(g.above()), "the barrel of " + g + " runs into a block");
            assertEquals(null, armed.get(crew), "no room for the crew of " + g + " at " + crew);
            assertEquals(null, armed.get(crew.above()), "no head room for the crew of " + g);
        }
        int[] lockerOf = GunStocking.lockerOf(guns, LOCKERS, SUPPLY_RANGE);
        int[] fed = new int[LOCKERS.size()];
        for (int i = 0; i < guns.size(); i++) {
            assertTrue(lockerOf[i] >= 0, "gun " + guns.get(i) + " is out of reach of every locker");
            fed[lockerOf[i]]++;
        }
        for (int l = 0; l < LOCKERS.size(); l++) {
            assertEquals("minecraft:barrel[facing=up,open=false]", armed.get(LOCKERS.get(l)));
            assertEquals(2, fed[l], "locker " + LOCKERS.get(l) + " feeds " + fed[l] + " guns");
            assertEquals(null, armed.get(LOCKERS.get(l).above()), "the locker " + LOCKERS.get(l) + " cannot open");
        }
    }

    /** The nest stands on the main mast top with nothing over it; the flag flies from a staff on the quarterdeck, aft. */
    @ParameterizedTest
    @ValueSource(strings = {"brig", "navy_brig_armed", "pirate_brig_armed"})
    void theNestCrownsTheMastAndTheFlagFliesAft(String name) throws IOException {
        Map<BlockPos, String> ship = blocks(name);
        if (!name.equals("brig")) {
            assertEquals("pirates_n_ships:crows_nest", ship.get(NEST));
            assertEquals(MAST, ship.get(NEST.below()), "the nest does not stand on the main mast");
            for (int y = NEST.getY() + 1; y < NEST.getY() + 4; y++) {
                assertEquals(null, ship.get(new BlockPos(NEST.getX(), y, NEST.getZ())), "something above the nest at y " + y);
            }
        } else {
            assertEquals(null, ship.get(NEST), "the merchant brig has no nest");
        }
        assertTrue(ship.get(FLAG_FOOT).startsWith("pirates_n_ships:flag_pedestal"), "no flag pedestal at " + FLAG_FOOT);
        assertEquals(DECK, ship.get(FLAG_FOOT.below()), "the pedestal is not on the quarterdeck");
        String[] parts = {"bottom", "middle", "top"};
        for (int i = 0; i < 3; i++) {
            assertEquals("pirates_n_ships:flagpole[facing=north,flag=none,part=" + parts[i] + "]", ship.get(FLAG_FOOT.above(i + 1)));
        }
        assertEquals(3, ship.values().stream().filter(s -> s.startsWith("pirates_n_ships:flagpole")).count(), "one three-block staff");
        assertTrue(FLAG_FOOT.getZ() > MAIN_Z, "the flag is not aft of the main mast");
        BlockPos head = FLAG_FOOT.above(3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    if (dx != 0 || dz != 0 || dy != 0) assertEquals(null, ship.get(head.offset(dx, dy, dz)), "the flag's cloth runs into a block");
                }
            }
        }
    }

    // ------------------------------------------------------------------ the ratline nets

    /** The ring of a railing latch at {@code pos} on its edge {@code facing} ({@link RailingLatchBlock#ring}). */
    private static Point latchRing(BlockPos pos, String facing) {
        int sx = facing.equals("west") ? -1 : facing.equals("east") ? 1 : 0;
        int sz = facing.equals("north") ? -1 : facing.equals("south") ? 1 : 0;
        double out = 0.5 - RailingLatchBlock.RING_INSET;
        return new Point(pos.getX() + 0.5 + sx * out, pos.getY() + RailingLatchBlock.RING_HEIGHT, pos.getZ() + 0.5 + sz * out);
    }

    /** The ring of a mast hoop at {@code pos} facing {@code facing} (away from the mast; {@code MastHoopBlock#ring}). */
    private static Point hoopRing(BlockPos pos, String facing) {
        int sx = facing.equals("west") ? -1 : facing.equals("east") ? 1 : 0;
        int sz = facing.equals("north") ? -1 : facing.equals("south") ? 1 : 0;
        double back = 0.5 - NetFitting.RING_FROM_SUPPORT;
        return new Point(pos.getX() + 0.5 - sx * back, pos.getY() + 0.5, pos.getZ() + 0.5 - sz * back);
    }

    private static String facingOf(String state) {
        int i = state.indexOf("facing=") + 7;
        int end = i;
        while (end < state.length() && Character.isLetter(state.charAt(end))) end++;
        return state.substring(i, end);
    }

    /** One side's strands: foot rings, hub rings and their cells. */
    private record Side(List<Point> feet, List<Point> hubs, List<List<Cell>> cells, Set<Cell> fittings) {
    }

    private static Map<Integer, Side> sides(Map<BlockPos, String> ship) {
        Map<Integer, Side> out = new TreeMap<>();
        for (ShipTemplate.Net n : ShipTemplates.BRIG_NETS) {
            String latch = ship.get(n.a()), hoop = ship.get(n.b());
            assertNotNull(latch, "no latch at " + n.a());
            assertNotNull(hoop, "no hoop at " + n.b());
            Point ra = latchRing(n.a(), facingOf(latch)), rb = hoopRing(n.b(), facingOf(hoop));
            Side s = out.computeIfAbsent(n.a().getX(), k -> new Side(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new HashSet<>()));
            s.feet().add(ra);
            s.hubs().add(rb);
            s.cells().add(RatlineNetRules.netCells(ra, rb));
            s.fittings().add(new Cell(n.a().getX(), n.a().getY(), n.a().getZ()));
            s.fittings().add(new Cell(n.b().getX(), n.b().getY(), n.b().getZ()));
        }
        return out;
    }

    /**
     * Each side's nets: three latches in a row on the quarterdeck's side rail (each on a railing railed on the latch's
     * edge, the deck inboard of it free to stand on), all to one mast hoop on the main mast's side right
     * under the nest; every strand within the default length and pitch; the three join pairwise into one sheet (RL4's
     * fan) whose cells are all free in the structure; and the cell over the hoop is free and right beside the nest, so a
     * climber leaves the net at the top (RL3) straight into the nest.
     */
    @ParameterizedTest
    @ValueSource(strings = {"navy_brig_armed", "pirate_brig_armed"})
    void eachNetClimbsFromTheQuarterdeckToTheNest(String name) throws IOException {
        Map<BlockPos, String> ship = blocks(name);
        assertEquals(6, ShipTemplates.BRIG_NETS.size());
        for (ShipTemplate.Net n : ShipTemplates.BRIG_NETS) {
            String latch = ship.get(n.a());
            assertTrue(latch.startsWith("pirates_n_ships:railing_latch"), "no latch at " + n.a() + ": " + latch);
            String rail = ship.get(n.a().below());
            assertTrue(rail.startsWith("pirates_n_ships:dark_oak_railing[") && rail.contains(facingOf(latch) + "=true"),
                    "the latch at " + n.a() + " is not on its railing's edge: " + rail);
            BlockPos inboard = facingOf(latch).equals("west") ? n.a().below().east() : n.a().below().west();
            assertEquals(null, ship.get(inboard), "no deck to stand on beside the net's foot at " + inboard);
            assertEquals(DECK, ship.get(inboard.below()), "no quarterdeck under " + inboard);
            String hoop = ship.get(n.b());
            assertTrue(hoop.startsWith("pirates_n_ships:mast_hoop"), "no hoop at " + n.b() + ": " + hoop);
            assertEquals(new BlockPos(MAST_X, MAIN_TOP, MAIN_Z), facingOf(hoop).equals("west") ? n.b().east() : n.b().west(),
                    "the hoop at " + n.b() + " is not on the main mast's top block");
            BlockPos over = n.b().above();
            assertEquals(null, ship.get(over), "the top exit over the hoop is blocked");
            assertEquals(null, ship.get(over.above()), "no head room at the top exit");
            assertEquals(1, over.distManhattan(NEST), "the top exit is not beside the nest");
            Point ra = latchRing(n.a(), facingOf(latch)), rb = hoopRing(n.b(), facingOf(hoop));
            assertEquals(RatlineNetRules.Verdict.OK, RatlineNetRules.check(ra, rb, NET_MAX_LENGTH, NET_MIN_PITCH), "net " + n);
        }
        Map<Integer, Side> sides = sides(ship);
        assertEquals(2, sides.size(), "nets on both sides");
        for (Side s : sides.values()) {
            boolean[] joinable = new boolean[s.feet().size()];
            java.util.Arrays.fill(joinable, true);
            int[] partner = RatlineNetRules.sheetPartners(s.feet(), s.hubs(), joinable, NET_MAX_SPREAD);
            assertEquals(2, java.util.Arrays.stream(partner).filter(p -> p >= 0).count(), "the three strands do not make one sheet: "
                    + java.util.Arrays.toString(partner));
            List<RatlineNetRules.SheetCell> cells = RatlineNetRules.sheetCells(s.feet(), s.hubs(), s.cells(), partner, s.fittings());
            assertTrue(cells.size() > 30, "a thin sheet: " + cells.size());
            for (RatlineNetRules.SheetCell c : cells) {
                BlockPos p = new BlockPos(c.cell().x(), c.cell().y(), c.cell().z());
                assertEquals(null, ship.get(p), "the net's cell " + p + (c.fill() ? " (fill)" : "") + " is taken by " + ship.get(p));
            }
        }
    }

    /**
     * The nets keep clear of the main mast's square sails and the jib: no net cell in a yards' plane (z 18, z 10) or the
     * jib's plane (x 4) forward of the main mast, and every point of each side's sheet (its strands and the triangle
     * between them, sampled) lies outside the cloth's envelope, as {@link ArmedSloopLayoutTest#ratlinesKeepClearOfTheClothAndTheJib}
     * measures it: the cloth stands off the yards' plane by {@link ClothGeometry#clearance} plus the deepest belly and
     * flutter at the default {@code sail_visuals}, between each pair of yards.
     */
    @Test
    void netsKeepClearOfTheClothAndTheJib() throws IOException {
        Map<BlockPos, String> ship = blocks("navy_brig_armed");
        double axisZ = MAIN_Z - 1 + 0.5;
        for (Side s : sides(ship).values()) {
            for (List<Cell> strand : s.cells()) {
                for (Cell c : strand) {
                    assertNotEquals(MAIN_Z - 1, c.z(), "a net cell in the main yards' plane at " + c);
                    assertTrue(c.z() > FORE_Z, "a net cell forward at " + c);
                }
            }
            Point hub = s.hubs().get(0);
            for (int k = 0; k + 1 < s.feet().size(); k++) {
                for (int i = 0; i <= 20; i++) {
                    for (int j = 0; j <= 20 - i; j++) {
                        // a point of the triangle (hub, foot k, foot k+1)
                        double u = i / 20.0, v = j / 20.0, w = 1 - u - v;
                        Point a = s.feet().get(k), b = s.feet().get(k + 1);
                        double y = w * hub.y() + u * a.y() + v * b.y();
                        double z = w * hub.z() + u * a.z() + v * b.z();
                        for (int sail = 0; sail + 1 < MAIN_YARDS.length; sail++) {
                            int upper = MAIN_YARDS[sail + 1], lower = MAIN_YARDS[sail];
                            int drop = upper - lower;
                            ClothGeometry cloth = new ClothGeometry(true, MAIN_HALF[sail + 1] + 0.5f, MAIN_HALF[sail + 1] + 0.5f,
                                    MAIN_HALF[sail] + 0.5f, MAIN_HALF[sail] + 0.5f, drop);
                            float reach = SailShape.cap(drop, MAX_BELLY) * (1f + SailShape.BREATH) + FLUTTER * 1.5f;
                            double depth = (upper + 0.5) - y;
                            if (depth < 0 || depth > drop) continue;
                            double standoff = cloth.clearance((float) depth, drop) + reach * Math.sin(Math.PI * depth / drop);
                            assertTrue(Math.abs(z - axisZ) > standoff, "the net meets the cloth between the yards at y " + lower
                                    + " and " + upper + ": at y " + y + ", z " + z + " (the cloth reaches " + (axisZ + standoff) + ")");
                        }
                    }
                }
            }
        }
    }

    /**
     * The yard rule on the brig: both masts run unbroken from the keelson to their heads, every yard stands in the column
     * one block forward of its mast, the three yards of each mast make two square sails (four in all, F5a), and the jib's
     * head and clew cleats sit on the fore mast right above the upper and below the lower fore yard, so the clew search
     * passes the yards (F5b) and a rope to the bowsprit cleat makes a jib of {@link #JIB_AREA}.
     */
    @ParameterizedTest
    @ValueSource(strings = {"brig", "navy_brig_armed", "pirate_brig_armed"})
    void theYardsStandForwardOfTheMastsAndTheSailsSet(String name) throws IOException {
        Map<BlockPos, String> ship = blocks(name);
        for (int[] m : new int[][] {{FORE_Z, FORE_TOP}, {MAIN_Z, MAIN_TOP}}) {
            for (int y = 1; y <= m[1]; y++) {
                assertEquals(MAST, ship.get(new BlockPos(MAST_X, y, m[0])), "the mast at z " + m[0] + " is broken at y " + y);
            }
        }
        List<int[]> yards = new ArrayList<>();
        Map<Integer, Integer> perColumn = new HashMap<>();
        ship.forEach((p, s) -> {
            if (s.startsWith("pirates_n_ships:yard")) {
                assertTrue(p.getZ() == FORE_Z - 1 || p.getZ() == MAIN_Z - 1, "a yard off the columns forward of the masts at " + p);
                yards.add(new int[] {p.getX(), p.getY(), p.getZ()});
                perColumn.merge(p.getZ(), 1, Integer::sum);
            }
        });
        assertEquals(Map.of(FORE_Z - 1, 9 + 7 + 5, MAIN_Z - 1, 9 + 7 + 5), perColumn, "yard blocks per mast");
        YardLookup yardCells = (x, y, z) -> {
            String s = ship.get(new BlockPos(x, y, z));
            if (s == null) return YardLookup.Cell.AIR;
            if (s.startsWith("pirates_n_ships:yard")) return s.contains("axis=x") ? YardLookup.Cell.YARD_X : YardLookup.Cell.YARD_Z;
            return s.equals(MAST) ? YardLookup.Cell.MAST : YardLookup.Cell.OTHER;
        };
        List<SquareSail> sails = new ArrayList<>(YardLinker.link(yardCells, yards, YardRules.DEFAULTS).sails());
        sails.sort(Comparator.<SquareSail>comparingInt(s -> s.upper().middleZ()).thenComparingInt(s -> s.upper().y()));
        List<List<Integer>> got = sails.stream().map(s -> List.of(s.upper().middleZ(), s.upper().y(), s.lower().y(), s.upper().middleX())).toList();
        assertEquals(List.of(List.of(FORE_Z - 1, FORE_YARDS[1], FORE_YARDS[0], MAST_X), List.of(FORE_Z - 1, FORE_YARDS[2], FORE_YARDS[1], MAST_X),
                List.of(MAIN_Z - 1, MAIN_YARDS[1], MAIN_YARDS[0], MAST_X), List.of(MAIN_Z - 1, MAIN_YARDS[2], MAIN_YARDS[1], MAST_X)), got,
                "square sails (column, upper, lower, middle)");

        CleatLookup cleatCells = (x, y, z) -> {
            String s = ship.get(new BlockPos(x, y, z));
            if (s == null) return CleatLookup.Cell.AIR;
            if (s.startsWith("pirates_n_ships:cleat")) return CleatLookup.Cell.CLEAT;
            if (s.startsWith("pirates_n_ships:yard")) return CleatLookup.Cell.YARD;
            return s.equals(MAST) ? CleatLookup.Cell.MAST : CleatLookup.Cell.OTHER;
        };
        assertEquals(3, ship.values().stream().filter(s -> s.startsWith("pirates_n_ships:cleat")).count(), "three cleats");
        for (BlockPoint c : List.of(JIB_HEAD, JIB_CLEW)) {
            BlockPos at = new BlockPos(c.x(), c.y(), c.z());
            assertEquals("pirates_n_ships:cleat[face=wall,facing=north]", ship.get(at), "no wall cleat at " + at);
            assertEquals(MAST, ship.get(at.south()), "the cleat at " + at + " is not on the fore mast");
        }
        BlockPos tack = new BlockPos(JIB_TACK.x(), JIB_TACK.y(), JIB_TACK.z());
        assertEquals("pirates_n_ships:cleat[face=floor,facing=north]", ship.get(tack), "no tack cleat at " + tack);
        assertEquals("minecraft:spruce_log[axis=z]", ship.get(tack.below()), "the tack is not on the bowsprit");
        assertEquals(JIB_CLEW, StayLinker.findClew(cleatCells, JIB_HEAD, JIB_TACK.y()), "the clew search does not pass the yards");
        TriangularSail jib = StayLinker.sail(cleatCells, JIB_HEAD, JIB_TACK, StayRules.DEFAULTS);
        assertNotNull(jib, "no jib between the head and the bowsprit cleat");
        assertEquals(JIB_AREA, jib.area(), 1e-9);
    }

    /**
     * SH1b on the brig: the hold (y 2..4) is closed below the gun deck: no air cell of the hull below the gun deck floor is
     * reached from outside through air (ladders count as air), and the two bulkheads cut it into three rooms (a fore, a
     * main and an aft hold), each with its own hatch and ladder from the gun deck. The brig cell's bars and door in the
     * aft hold (SHIP2) let water through, so they are open here.
     */
    @Test
    void theHoldIsClosedAndHasThreeCompartments() throws IOException {
        Map<BlockPos, String> ship = blocks("brig");
        java.util.function.Predicate<BlockPos> open = p -> {
            String s = ship.get(p);
            return s == null || s.startsWith("minecraft:ladder") || s.startsWith("pirates_n_ships:brig_");
        };
        // flood the air outside the hull below the gun deck floor (y 0..4), from beyond the structure's box
        Set<BlockPos> outside = new HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        BlockPos start = new BlockPos(-1, 0, -1);
        outside.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos n = p.relative(d);
                if (n.getX() < -1 || n.getX() > 9 || n.getY() < 0 || n.getY() > 4 || n.getZ() < -1 || n.getZ() > 32) continue;
                if (open.test(n) && outside.add(n)) queue.add(n);
            }
        }
        // the hold's air: below the gun deck floor, inside the hull (between hull blocks along x)
        List<Set<BlockPos>> rooms = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (int y = 2; y <= 4; y++) {
            for (int z = 0; z < 32; z++) {
                for (int x = 1; x < 8; x++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!open.test(p) || outside.contains(p) || !seen.add(p)) continue;
                    Set<BlockPos> room = new HashSet<>();
                    java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>(List.of(p));
                    room.add(p);
                    while (!q.isEmpty()) {
                        BlockPos c = q.poll();
                        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                            BlockPos n = c.relative(d);
                            if (n.getY() < 1 || n.getY() > 4) continue;
                            assertTrue(!outside.contains(n), "the hold is open to the sea at " + n);
                            if (open.test(n) && room.add(n)) {
                                seen.add(n);
                                q.add(n);
                            }
                        }
                    }
                    rooms.add(room);
                }
            }
        }
        rooms.sort(Comparator.comparingInt(r -> r.stream().mapToInt(BlockPos::getZ).min().orElse(0)));
        assertEquals(3, rooms.size(), "hold rooms " + rooms.stream().map(Set::size).toList());
        int[][] spans = {{0, 13}, {15, 22}, {24, 31}};
        for (int i = 0; i < 3; i++) {
            Set<BlockPos> room = rooms.get(i);
            int lo = spans[i][0], hi = spans[i][1];
            assertTrue(room.stream().allMatch(p -> p.getZ() >= lo && p.getZ() <= hi), "hold room " + i + " crosses a bulkhead");
            assertTrue(room.stream().anyMatch(p -> ship.getOrDefault(p, "").startsWith("minecraft:ladder")), "no ladder in hold room " + i);
            long hatches = room.stream().filter(p -> p.getY() == 4 && ship.getOrDefault(p.above(), "").startsWith("minecraft:spruce_trapdoor")).count();
            assertEquals(1, hatches, "hatches over hold room " + i);
        }
    }

    /**
     * SHIP2: the ballast stones lie in the bottom and beside the keel (y 0 and 1), at least 60 of them, mirrored across
     * the centre line; every block's mass sits evenly on both sides (the same block at x and 8 − x, ignoring facing and
     * a few light fittings), so she has no list of her own.
     */
    @ParameterizedTest
    @ValueSource(strings = {"brig", "navy_brig_armed", "pirate_brig_armed"})
    void theBallastLiesLowAndEven(String name) throws IOException {
        Map<BlockPos, String> ship = blocks(name);
        List<BlockPos> ballast = ship.entrySet().stream().filter(e -> e.getValue().equals(BALLAST)).map(Map.Entry::getKey).toList();
        assertTrue(ballast.size() >= 60, "ballast stones " + ballast.size());
        for (BlockPos b : ballast) {
            assertTrue(b.getY() <= 1, "ballast above the bottom at " + b);
            assertEquals(BALLAST, ship.get(new BlockPos(8 - b.getX(), b.getY(), b.getZ())), "the ballast at " + b + " has no twin");
        }
        Set<String> light = Set.of("pirates_n_ships:bilge_pump", "pirates_n_ships:chart_table", "pirates_n_ships:cargo_barrel",
                "pirates_n_ships:ship_lantern", "pirates_n_ships:pantry", "pirates_n_ships:water_barrel");
        List<String> uneven = new ArrayList<>();
        ship.forEach((p, s) -> {
            if (p.getX() == MAST_X) return;
            String id = s.contains("[") ? s.substring(0, s.indexOf('[')) : s;
            String twin = ship.get(new BlockPos(8 - p.getX(), p.getY(), p.getZ()));
            String twinId = twin == null ? null : twin.contains("[") ? twin.substring(0, twin.indexOf('[')) : twin;
            if (!id.equals(twinId) && !light.contains(id) && (twinId == null || !light.contains(twinId))) uneven.add(p.toShortString() + " " + id + " | " + twinId);
        });
        assertTrue(uneven.isEmpty(), "uneven across the centre line:\n" + String.join("\n", uneven));
    }

    /** The block count the brig's price is made from ({@link ShipOrderMath#brigPrice}) is the structure's. */
    @Test
    void thePriceCountsTheStructuresBlocksAndGuns() throws IOException {
        Map<BlockPos, String> ship = blocks("brig");
        assertEquals(ShipOrderMath.BRIG_BLOCKS, ship.size(), "blocks");
        assertEquals(ShipOrderMath.BRIG_GUNS, ship.values().stream().filter(s -> s.startsWith(CANNON) && s.contains("part=front")).count());
        assertEquals(1250, ShipOrderMath.brigPrice());
    }
}

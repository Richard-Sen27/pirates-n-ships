package com.richardsenger.piratesnships.ship.hull.runtime;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.hazards.waves.SeaState;
import com.richardsenger.piratesnships.hazards.waves.SeaStates;
import com.richardsenger.piratesnships.hazards.waves.WaveField;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.DisassemblyMath;
import com.richardsenger.piratesnships.ship.hull.Compartment;
import com.richardsenger.piratesnships.ship.hull.FloodingConfig;
import com.richardsenger.piratesnships.ship.hull.HullAnalysis;
import com.richardsenger.piratesnships.ship.hull.net.ShipStatusPayload;
import com.richardsenger.piratesnships.ship.hull.net.ShipStatusSync;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

/**
 * CMP1 (docs/design.md §4.2): a ship's compartments do not change with its tilt. The ship: a 7×17 plank hull (x 16..22,
 * z 10..26, floor y 5, deck y 10, bow +Z) with a bulkhead across the hold at z 18 one block lower than the deck, so the
 * layer y 9 runs over it, and a hole in the west wall at that layer (x 16, y 9, z 13). Upright the hole is the pour point
 * of the layer y 9, which is outside air, and the hold is two rooms (5×3×7 each). Before CMP1 any heel that lifted the
 * hole side put the rest of that layer below the hole: it became basin and joined both rooms into one compartment, and
 * the other way they split again, so a rolling ship's count flickered between one and two and its water was remapped.
 */
public final class CompartmentStabilityGameTests {

    private static final int SETTLE = 60;
    /** Thirty seconds of rolling. */
    private static final int WINDOW = 600;
    private static final int ROOM = 5 * 3 * 7;

    private CompartmentStabilityGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CompartmentStabilityGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** The two-room hull described above; returns the helm's relative position. */
    static BlockPos twoRoomHull(GameTestHelper h) {
        for (int x = 16; x <= 22; x++) {
            for (int z = 10; z <= 26; z++) {
                for (int y = 5; y <= 10; y++) {
                    boolean shell = y == 5 || y == 10 || x == 16 || x == 22 || z == 10 || z == 26;
                    h.setBlock(new BlockPos(x, y, z), shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
        for (int x = 17; x <= 21; x++) {
            for (int y = 6; y <= 8; y++) {
                h.setBlock(new BlockPos(x, y, 18), Blocks.OAK_PLANKS); // bulkhead, one block lower than the deck
            }
        }
        h.setBlock(new BlockPos(16, 9, 13), Blocks.AIR); // the hole at the layer over the bulkhead
        BlockPos helm = new BlockPos(19, 11, 22);
        h.setBlock(helm, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        return helm;
    }

    /** Holds the sea at {@code state} toward +X (abeam of the +Z bow), phases pinned to the test, as the wave tests do. */
    private static void hold(GameTestHelper h, SeaState state, long ticks) {
        ServerLevel level = h.getLevel();
        Vec3 mid = h.absoluteVec(new Vec3(19.5, 0.0, 18.5));
        SeaStates.set(level, state, 90.0, level.getGameTime() + ticks,
                new WaveField.Origin(level.getGameTime() - h.getTick(), mid.x, mid.z));
    }

    private static HullRuntime hull(GameTestHelper h, SailingGameTestsShips.Fixture f) {
        HullRuntime rt = HullRuntimes.get(h.getLevel(), f.ship().id());
        if (rt == null) {
            throw new AssertionError("no hull runtime");
        }
        return rt;
    }

    /** The compartments' cells by id. */
    private static List<BitSet> layout(HullAnalysis a) {
        List<BitSet> out = new ArrayList<>();
        for (Compartment c : a.compartments()) {
            out.add(c.cells());
        }
        return out;
    }

    private static double[] volumes(HullRuntime rt) {
        double[] v = new double[rt.simulation().analysis().compartments().size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = rt.simulation().volume(i);
        }
        return v;
    }

    /** The strip's layout: id and volume of every cell, bow to stern. */
    private static List<String> strip(ShipStatusPayload p) {
        return p.cells().stream().map(c -> c.id() + ":" + c.volume()).toList();
    }

    /** Checks the upright partition: two rooms of {@link #ROOM} cells. */
    private static void assertTwoRooms(GameTestHelper h, HullRuntime rt) {
        List<Compartment> comps = rt.simulation().analysis().compartments();
        h.assertTrue(comps.size() == 2 && comps.get(0).volume() == ROOM && comps.get(1).volume() == ROOM,
                "expected two rooms of " + ROOM + " cells, got " + comps.stream().map(Compartment::volume).toList());
    }

    // ------------------------------------------------------------------ tests

    /**
     * Thirty seconds on a rough sea, with the heights refreshed at every half degree of tilt: the compartment count, the
     * ids and their cells never change, and the water in both rooms stays to 1e-6 (no sea comes in: flooding is off, so
     * only a remap could change it).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = SETTLE + WINDOW + 200,
            batch = "pirates_n_ships_config_hull_cmp1_rough")
    public static void roughSeaKeepsTheCompartmentsOfARollingShip(GameTestHelper h) {
        ConfigOverrides.during(h, FloodingConfig.REANALYSIS_TILT_DEGREES, 0.5);
        ConfigOverrides.during(h, FloodingConfig.ENABLED, false);
        hold(h, SeaState.CALM, SETTLE + WINDOW + 300);
        SailingGameTestsShips.basin(h, true);
        SailingGameTestsShips.Fixture f = SailingGameTestsShips.assemble(h, twoRoomHull(h));
        HullRuntime rt = hull(h, f);
        Object[] base = new Object[3]; // layout, volumes, refreshes at the start
        double[] tilt = {0, 0}; // largest tilt [degrees], ticks measured
        h.runAfterDelay(SETTLE, () -> {
            hold(h, SeaState.ROUGH, WINDOW + 300);
            assertTwoRooms(h, rt);
            rt.simulation().setVolume(0, 12.345678);
            rt.simulation().setVolume(1, 4.25);
            base[0] = layout(rt.simulation().analysis());
            base[1] = volumes(rt);
            base[2] = rt.tiltRefreshes();
        });
        h.onEachTick(() -> {
            long t = h.getTick();
            if (base[0] == null || t <= SETTLE || t > SETTLE + WINDOW || f.ship().isRemoved()) {
                return;
            }
            tilt[0] = Math.max(tilt[0], DisassemblyMath.tiltDegrees(f.ship().orientation()));
            tilt[1]++;
            List<BitSet> now = layout(rt.simulation().analysis());
            h.assertTrue(now.equals(base[0]), "tick " + t + ": the compartments changed to "
                    + now.stream().map(BitSet::cardinality).toList());
            double[] v = volumes(rt), v0 = (double[]) base[1];
            for (int i = 0; i < v.length; i++) {
                h.assertTrue(Math.abs(v[i] - v0[i]) <= 1e-6, "tick " + t + ": compartment " + i + " holds " + v[i]
                        + " instead of " + v0[i]);
            }
        });
        h.runAfterDelay(SETTLE + WINDOW + 1, () -> {
            int refreshes = rt.tiltRefreshes() - (int) base[2];
            Constants.LOG.info("[cmp1 test] rough sea: {} tilt refreshes in {} ticks, largest tilt {} deg, compartments {}",
                    refreshes, (long) tilt[1], String.format(Locale.ROOT, "%.2f", tilt[0]),
                    layout(rt.simulation().analysis()).stream().map(BitSet::cardinality).toList());
            SeaStates.clear(h.getLevel());
            h.assertTrue(tilt[1] >= WINDOW - 1, "measured only " + tilt[1] + " ticks");
            h.assertTrue(tilt[0] > 1.0, "the ship did not roll: largest tilt " + tilt[0] + " deg");
            h.assertTrue(refreshes >= 5, "only " + refreshes + " tilt refreshes in the window");
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    /**
     * Heeled 20 degrees by hand with the hole side up (before CMP1 that joined the two rooms): the tilt refresh keeps
     * both rooms, their ids, their water and the HUD strip's layout.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200)
    public static void floodedRoomKeepsItsWaterAcrossATiltRefresh(GameTestHelper h) {
        SailingGameTestsShips.basin(h, false); // on land: no sea comes in at 20 degrees of heel
        SailingGameTestsShips.Fixture f = SailingGameTestsShips.assemble(h, twoRoomHull(h));
        HullRuntime rt = hull(h, f);
        ServerLevel level = h.getLevel();
        Object[] base = new Object[3]; // layout, strip, refreshes before the heel
        h.runAfterDelay(20, () -> {
            assertTwoRooms(h, rt);
            rt.simulation().setVolume(0, 30.0);
            rt.simulation().setVolume(1, 0.0);
            base[0] = layout(rt.simulation().analysis());
            base[1] = strip(ShipStatusSync.build(level, f.ship()));
            base[2] = rt.tiltRefreshes();
            h.assertTrue(((List<?>) base[1]).size() == 2, "the strip shows " + base[1]);
            // lift the west (hole) side: a negative turn about +Z lowers +X
            f.ship().setOrientation(new Quaterniond().rotateAxis(Math.toRadians(-20), 0, 0, 1));
        });
        boolean[] logged = {false};
        h.runAfterDelay(21, () -> h.succeedWhen(() -> {
            h.assertTrue(rt.tiltRefreshes() > (int) base[2], "no tilt refresh yet (tilt "
                    + DisassemblyMath.tiltDegrees(f.ship().orientation()) + " deg)");
            HullAnalysis a = rt.simulation().analysis();
            if (!logged[0]) {
                logged[0] = true;
                Constants.LOG.info("[cmp1 test] heeled {} deg, refreshes {}, up {}", String.format(Locale.ROOT, "%.1f",
                        DisassemblyMath.tiltDegrees(f.ship().orientation())), rt.tiltRefreshes(), a.up());
            }
            h.assertTrue(layout(a).equals(base[0]), "the heel changed the compartments to "
                    + a.compartments().stream().map(Compartment::volume).toList());
            h.assertTrue(Math.abs(rt.simulation().volume(0) - 30.0) <= 1e-6 && rt.simulation().volume(1) <= 1e-6,
                    "the water moved: " + rt.simulation().volume(0) + ", " + rt.simulation().volume(1));
            ShipStatusPayload status = ShipStatusSync.build(level, f.ship());
            List<String> s = strip(status);
            h.assertTrue(s.equals(base[1]), "the strip changed from " + base[1] + " to " + s);
            ShipStatusPayload.Cell wet = status.cells().stream().filter(c -> c.id() == 0).findFirst().orElseThrow();
            h.assertTrue(Math.abs(wet.water() - 30.0f) <= 0.05f, "the strip shows " + wet.water() + " blocks in room 0");
            SableShips.remove(f.ship());
        }));
    }
}

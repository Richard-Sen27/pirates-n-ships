package com.richardsenger.piratesnships.hazards.waves;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.hazard.HazardConfig;
import com.richardsenger.piratesnships.sailing.force.ShipFrame;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.waves.WaveForces;
import com.richardsenger.piratesnships.sailing.waves.WaveTorqueRule;
import com.richardsenger.piratesnships.ship.hull.FloodingConfig;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import io.netty.buffer.Unpooled;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * GameTests of the waves (WV1, docs/design.md §5.4). The sea state is held with {@link SeaStates#set} (what
 * {@code /pirates waves set} does), which is level-wide, so every test has its own batch, holds the sea with an expiry
 * as a safety net and clears it when it ends. The waves run abeam (toward +X, from 270°) of hulls whose bow is +Z.
 *
 * <p>Roll is measured as the half range (max − min) / 2 of the roll angle over a window, which ignores a constant
 * list.
 */
public final class WaveGameTests {

    /** Waves come from the west and run toward +X: abeam of a +Z bow. */
    private static final double FROM_WEST = 270.0;
    private static final int SETTLE = 60;

    private WaveGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(WaveGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private static void hold(GameTestHelper h, SeaState state, long ticks) {
        ServerLevel level = h.getLevel();
        SeaStates.set(level, state, FROM_WEST + 180.0, level.getGameTime() + ticks);
    }

    private static void release(GameTestHelper h) {
        SeaStates.clear(h.getLevel());
    }

    /** Signed roll angle [degrees] (+ = port side up). */
    private static double rollDegrees(SailingGameTestsShips.Fixture f) {
        Quaterniond q = f.runtime().bow().shipToWorld(f.ship().orientation(new Quaterniond()), new Quaterniond());
        Vector3d port = q.transform(new Vector3d(ShipFrame.PORT));
        return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, port.y))));
    }

    /** Roll statistics over ticks [from, to): {min, max}. */
    private static double[] watchRoll(GameTestHelper h, SailingGameTestsShips.Fixture f, long from, long to) {
        double[] mm = {Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t >= from && t < to && !f.ship().isRemoved()) {
                double r = rollDegrees(f);
                mm[0] = Math.min(mm[0], r);
                mm[1] = Math.max(mm[1], r);
            }
        });
        return mm;
    }

    private static double half(double[] mm) {
        return Double.isFinite(mm[0]) ? (mm[1] - mm[0]) * 0.5 : Double.NaN;
    }

    /** Logs the roll every 10 ticks between {@code from} and {@code to}. */
    private static void trace(GameTestHelper h, String label, SailingGameTestsShips.Fixture f, long from, long to) {
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t >= from && t < to && t % 10 == 0 && !f.ship().isRemoved()) {
                WaveTorqueRule.Torque q = WaveForces.torque(h.getLevel(), f.ship().id());
                double[] s = WaveForces.slopes(h.getLevel(), f.ship().id());
                Constants.LOG.info("[wave test] {} t={} roll {} deg, slope roll {} pitch {}, torque roll {} pitch {}, sea {}",
                        label, t, String.format("%.2f", rollDegrees(f)), String.format("%.3f", s[0]), String.format("%.3f", s[1]),
                        String.format("%.1f", q.roll()), String.format("%.1f", q.pitch()), SeaStates.current(h.getLevel()).id());
            }
        });
    }

    /** The 7×17 test hull of the damping tests (180 kpg, bow +Z), assembled in an open 40×40 basin. */
    private static SailingGameTestsShips.Fixture longHull(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        return SailingGameTestsShips.assemble(h, SailingGameTestsShips.longHull(h));
    }

    /**
     * A 12-wide, 32-long plank hull, 4 high (x 14..25, z 4..35, floor y=5, deck y=8), bow +Z, helm on deck. The design
     * asks for 40×12; 32 is the longest that floats free in the 40×40 basin (inner 38) with room to drift.
     */
    private static SailingGameTestsShips.Fixture bigHull(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        for (int x = 14; x <= 25; x++) {
            for (int z = 4; z <= 35; z++) {
                for (int y = 5; y <= 8; y++) {
                    boolean shell = y == 5 || y == 8 || x == 14 || x == 25 || z == 4 || z == 35;
                    h.setBlock(new BlockPos(x, y, z), shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
        BlockPos helm = new BlockPos(19, 9, 6);
        h.setBlock(helm, com.richardsenger.piratesnships.ship.assembly.AssemblyContent.HELM.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        return SailingGameTestsShips.assemble(h, helm);
    }

    // ------------------------------------------------------------------ roll

    /**
     * The 7×17 hull at storm amplitude rolls 3 to 15 degrees (half range over 10 s at steady state), and once the sea
     * goes calm it settles within 10 s.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 700, batch = "pirates_n_ships_waves_storm_roll")
    public static void stormRollsTheTestHullAndCalmSettlesIt(GameTestHelper h) {
        hold(h, SeaState.CALM, 800);
        SailingGameTestsShips.Fixture f = longHull(h);
        long stormAt = SETTLE, calmAt = SETTLE + 360;
        h.runAfterDelay(stormAt, () -> hold(h, SeaState.STORM, 800));
        h.runAfterDelay(calmAt, () -> hold(h, SeaState.CALM, 800));
        trace(h, "storm 7x17", f, stormAt, calmAt + 220);
        double[] storm = watchRoll(h, f, stormAt + 160, calmAt);
        double[] settled = watchRoll(h, f, calmAt + 200, calmAt + 220);
        h.runAfterDelay(calmAt + 221, () -> {
            double s = half(storm), c = half(settled);
            Constants.LOG.info("[wave test] 7x17 storm roll {} deg (range {}..{}), 10 s after calm {} deg; mass {}, torque per mass cap {}",
                    String.format("%.2f", s), String.format("%.1f", storm[0]), String.format("%.1f", storm[1]),
                    String.format("%.2f", c), String.format("%.1f", f.ship().mass()), HazardConfig.MAX_TORQUE_PER_MASS.get());
            release(h);
            h.assertTrue(s >= 3.0 && s <= 15.0, "storm roll of the 7x17 hull outside 3..15 deg: " + s);
            h.assertTrue(c < 0.5, "the 7x17 hull still rolls " + c + " deg 10 s after the sea went calm");
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    /** A calm sea leaves the 7×17 hull still: under half a degree of roll. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 400, batch = "pirates_n_ships_waves_calm_roll")
    public static void calmSeaLeavesTheTestHullStill(GameTestHelper h) {
        hold(h, SeaState.CALM, 500);
        SailingGameTestsShips.Fixture f = longHull(h);
        trace(h, "calm 7x17", f, SETTLE, 360);
        double[] calm = watchRoll(h, f, 160, 360);
        h.runAfterDelay(361, () -> {
            double c = half(calm);
            Constants.LOG.info("[wave test] 7x17 calm roll {} deg", String.format("%.3f", c));
            release(h);
            h.assertTrue(c < 0.5, "a calm sea rolls the 7x17 hull " + c + " deg");
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    /** A big hull (32×12) lies steady in a storm: under 3 degrees of roll. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 500, batch = "pirates_n_ships_waves_big_hull")
    public static void stormBarelyRollsABigHull(GameTestHelper h) {
        hold(h, SeaState.CALM, 600);
        SailingGameTestsShips.Fixture f = bigHull(h);
        h.runAfterDelay(SETTLE, () -> hold(h, SeaState.STORM, 600));
        trace(h, "storm 32x12", f, SETTLE, 420);
        double[] storm = watchRoll(h, f, SETTLE + 160, 420);
        h.runAfterDelay(421, () -> {
            double s = half(storm);
            Constants.LOG.info("[wave test] 32x12 storm roll {} deg (range {}..{}); mass {}", String.format("%.2f", s),
                    String.format("%.1f", storm[0]), String.format("%.1f", storm[1]), String.format("%.1f", f.ship().mass()));
            release(h);
            h.assertTrue(s < 3.0, "a storm rolls the 32x12 hull " + s + " deg");
            h.assertTrue(s > 0.05, "the storm did not move the 32x12 hull at all: " + s + " deg");
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    /** With {@code waves.enabled} off a storm applies no torque and no spill height. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_config_waves_disabled")
    public static void disabledWavesApplyNothing(GameTestHelper h) {
        ConfigOverrides.during(h, HazardConfig.WAVES_ENABLED, false);
        hold(h, SeaState.STORM, 400);
        SailingGameTestsShips.Fixture f = longHull(h);
        UUID id = f.ship().id();
        boolean[] seen = {false};
        h.onEachTick(() -> {
            if (h.getTick() > 20 && !f.ship().isRemoved()) {
                if (WaveForces.torque(h.getLevel(), id).magnitude() > 0.0 || WaveForces.spillHeight(h.getLevel(), id) > 0.0) {
                    seen[0] = true;
                }
            }
        });
        double[] roll = watchRoll(h, f, 120, 260);
        h.runAfterDelay(261, () -> {
            release(h);
            h.assertTrue(SeaStates.field(h.getLevel()).isFlat(), "the sea is not flat with waves off");
            h.assertFalse(seen[0], "waves acted on the ship while disabled");
            h.assertTrue(half(roll) < 0.5, "the hull rolls " + half(roll) + " deg with waves off");
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ spilling

    /** The 5×4×5 hull of the dry hull tests with an open trapdoor low in its west wall (plot cell y=7, deck y=8). */
    private static DryHullGameTests.Fixture hatchHull(GameTestHelper h) {
        return hatchHull(h, HATCH_BALLAST);
    }

    /** Cobblestone ballast blocks in the floor: the middle {@code ballast} cells (1, 5, 9, 13, ...) by distance. */
    private static DryHullGameTests.Fixture hatchHull(GameTestHelper h, int ballast) {
        DryHullGameTests.basin(h, 0, 23, true);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        java.util.List<BlockPos> floor = new java.util.ArrayList<>();
        for (int x = 9; x <= 13; x++) {
            for (int z = 9; z <= 13; z++) {
                floor.add(new BlockPos(x, 5, z));
            }
        }
        floor.sort(java.util.Comparator.comparingInt(p -> Math.abs(p.getX() - 11) + Math.abs(p.getZ() - 11)));
        for (int i = 0; i < ballast && i < floor.size(); i++) {
            h.setBlock(floor.get(i), Blocks.COBBLESTONE); // ballast: the hull floats deeper
        }
        h.setBlock(new BlockPos(9, 7, 11), Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.WEST));
        DryHullGameTests.Fixture f = DryHullGameTests.assemble(h, helm);
        SailingRuntimes.getOrCreate(f.ship());
        return f;
    }

    /** Height of the hatch's sill above the still sea, in the hull's frame [blocks], or NaN. */
    private static double freeboard(DryHullGameTests.Fixture f) {
        var comps = f.runtime().simulation().analysis().compartments();
        double sill = Double.POSITIVE_INFINITY;
        for (var c : comps) {
            for (var p : c.ports()) {
                if (!p.isPourPoint()) sill = Math.min(sill, p.sill());
            }
        }
        return Double.isFinite(sill) ? sill - f.runtime().seaShipFrame() : Double.NaN;
    }

    private static final int OPEN_AT = 60;
    /**
     * No ballast: the plain hull floats with the hatch's sill about 0.4 blocks above the still sea (measured: 0.38;
     * 5 cobblestone blocks bring it to 0.03, 9 put it under water), above a calm crest (0.1) and well below a storm's
     * (up to 1.2).
     */
    private static final int HATCH_BALLAST = 0;


    private static void spill(GameTestHelper h, SeaState state, boolean floods) {
        ConfigOverrides.during(h, FloodingConfig.INFLOW_RATE, 10.0);
        hold(h, state, 500);
        DryHullGameTests.Fixture f = hatchHull(h);
        BlockPos hatch = f.hold(-2, -2, 0);
        ServerLevel level = h.getLevel();
        // opened once the hull floats: during the first ticks after assembly it still sits a block deeper
        h.runAfterDelay(OPEN_AT, () -> {
            h.assertTrue(level.getBlockState(hatch).is(Blocks.OAK_TRAPDOOR), "no hatch in the plot at " + hatch);
            level.setBlock(hatch, level.getBlockState(hatch).setValue(TrapDoorBlock.OPEN, true), net.minecraft.world.level.block.Block.UPDATE_ALL);
        });
        double[] maxSpill = {0};
        double[] before = {0};
        h.runAfterDelay(OPEN_AT, () -> before[0] = f.runtime().simulation().totalVolume());
        h.onEachTick(() -> {
            if (!f.ship().isRemoved()) {
                maxSpill[0] = Math.max(maxSpill[0], WaveForces.spillHeight(h.getLevel(), f.ship().id()));
                if (h.getTick() % 20 == 0) {
                    Constants.LOG.info("[wave test] spill {} t={} hatch sill above sea {} spill {} water {}", state.id(), h.getTick(),
                            String.format("%.2f", freeboard(f)),
                            String.format("%.2f", WaveForces.spillHeight(h.getLevel(), f.ship().id())),
                            String.format("%.2f", f.runtime().simulation().totalVolume()));
                }
            }
        });
        h.runAfterDelay(OPEN_AT + 300, () -> {
            double water = f.runtime().simulation().totalVolume() - before[0];
            Constants.LOG.info("[wave test] spill {}: water {} in 300 ticks with the hatch open ({} before), highest crest feed {}, sill above sea {}",
                    state.id(), String.format("%.2f", water), String.format("%.2f", before[0]), String.format("%.2f", maxSpill[0]),
                    String.format("%.2f", freeboard(f)));
            release(h);
            h.assertTrue(f.runtime().simulation().isOpen(f.runtime().simulation().analysis().grid().index(
                    hatch.getX() - f.runtime().simulation().analysis().grid().originX(),
                    hatch.getY() - f.runtime().simulation().analysis().grid().originY(),
                    hatch.getZ() - f.runtime().simulation().analysis().grid().originZ())), "the open hatch did not reach the simulation");
            if (floods) {
                h.assertTrue(water > 0.5, "a storm put only " + water + " blocks of water through the low hatch");
            } else {
                h.assertTrue(water < 0.01, "a calm sea put " + water + " blocks of water through the low hatch");
            }
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    /** In a storm the crests spill water through an open hatch about 0.4 blocks above the still sea. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 460, batch = "pirates_n_ships_config_waves_spill_storm")
    public static void stormSpillsThroughALowHatch(GameTestHelper h) {
        spill(h, SeaState.STORM, true);
    }

    /** In a calm sea the same hatch stays dry. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 460, batch = "pirates_n_ships_config_waves_spill_calm")
    public static void calmSeaKeepsALowHatchDry(GameTestHelper h) {
        spill(h, SeaState.CALM, false);
    }

    // ------------------------------------------------------------------ sync

    /**
     * The sync payload carries the held storm to a player: encoded and decoded with its stream codec, it gives the
     * client holder the storm's state and wave height. (A mock player has no connection; {@link WaveSync#syncTo} takes
     * the path of the per-player sync and records it.)
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 40, batch = "pirates_n_ships_waves_sync")
    public static void syncPayloadReachesAPlayer(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        hold(h, SeaState.STORM, 100);
        h.runAfterDelay(2, () -> {
            UUID player = UUID.randomUUID();
            List<WaveSyncPayload> sent = WaveSync.record(player);
            WaveSyncPayload[] received = new WaveSyncPayload[1];
            WaveSync.syncTo(level, player, p -> {
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
                WaveSyncPayload.CODEC.encode(buf, p);
                received[0] = WaveSyncPayload.CODEC.decode(buf);
                buf.release();
            });
            WaveSync.stopRecording(player);
            release(h);
            h.assertTrue(sent.size() == 1, "expected one recorded payload, got " + sent);
            h.assertValueEqual(received[0], sent.get(0), "decoded payload");
            h.assertValueEqual(received[0].seaState(), SeaState.STORM, "state");
            double expected = SeaState.STORM.amplitude() * HazardConfig.WAVE_AMPLITUDE.get();
            h.assertTrue(Math.abs(received[0].amplitude() - expected) < 1.0e-4, "amplitude " + received[0].amplitude() + " != " + expected);
            ClientWaves.accept(received[0], level.getGameTime());
            h.assertTrue(ClientWaves.hasData() && ClientWaves.state() == SeaState.STORM
                    && Math.abs(ClientWaves.field(level.getGameTime()).amplitude() - expected) < 1.0e-4, "client holder did not take the storm");
            ClientWaves.reset();
            h.succeed();
        });
    }

    /** The waves force group is registered and a ship in a storm gets a torque from it (and none on land). */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = "pirates_n_ships_waves_dry_land")
    public static void shipOnLandGetsNoWaves(GameTestHelper h) {
        hold(h, SeaState.STORM, 300);
        SailingGameTestsShips.basin(h, false);
        SailingGameTestsShips.Fixture f = SailingGameTestsShips.assemble(h, SailingGameTestsShips.longHull(h));
        ShipBody ship = f.ship();
        boolean[] seen = {false};
        h.onEachTick(() -> {
            if (!ship.isRemoved() && WaveForces.torque(h.getLevel(), ship.id()).magnitude() > 0.0) {
                seen[0] = true;
            }
        });
        h.runAfterDelay(120, () -> {
            release(h);
            h.assertFalse(seen[0], "a ship on land got wave torque");
            SableShips.remove(ship);
            h.succeed();
        });
    }
}

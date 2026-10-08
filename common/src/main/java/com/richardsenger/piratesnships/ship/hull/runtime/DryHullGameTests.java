package com.richardsenger.piratesnships.ship.hull.runtime;

import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodReport;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.WaterRegions;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Spike 2 GameTests: a closed 5×4×5 plank hull (floor y=5, hold y=6..7, deck y=8, helm on the deck) floating in a
 * stone basin filled with water up to y=7 (relative, floor y=1). The hold (3×2×3 = 18 cells) is one compartment.
 */
public final class DryHullGameTests {

    private static final int HOLD = 18;

    private DryHullGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(DryHullGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** Stone basin over x in [x0, x1], all z, floor y=1, walls y=2..8, water y=2..7 (a dry basin is solid up to y=4). */
    public static void basin(GameTestHelper h, int x0, int x1, boolean water) {
        for (int x = x0; x <= x1; x++) {
            for (int z = 0; z < 24; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == x0 || x == x1 || z == 0 || z == 23;
                for (int y = 2; y <= 8; y++) {
                    // a dry basin gets a raised floor up to y=4 so the hull (bottom y=5) rests on it from the start
                    boolean solid = wall || !water && y <= 4;
                    h.setBlock(new BlockPos(x, y, z), solid ? Blocks.STONE : y <= 7 && water ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
    }

    /** The hull at x in [a, a+4], z in [9, 13]; returns the helm's relative position. */
    public static BlockPos hull(GameTestHelper h, int a, boolean hatch) {
        for (int x = a; x <= a + 4; x++) {
            for (int z = 9; z <= 13; z++) {
                for (int y = 5; y <= 8; y++) {
                    boolean shell = y == 5 || y == 8 || x == a || x == a + 4 || z == 9 || z == 13;
                    h.setBlock(new BlockPos(x, y, z), shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
        if (hatch) {
            h.setBlock(new BlockPos(a + 1, 8, 10), Blocks.OAK_TRAPDOOR);
        }
        BlockPos helm = new BlockPos(a + 2, 9, 11);
        h.setBlock(helm, AssemblyContent.HELM.get());
        return helm;
    }

    public record Fixture(ShipBody ship, HullRuntime runtime, BlockPos helmPlot) {
        /** Plot position of a hold cell, relative to the helm (hold spans dx/dz −1..1, dy −3..−2). */
        public BlockPos hold(int dx, int dy, int dz) {
            return helmPlot.offset(dx, dy, dz);
        }
    }

    public static Fixture assemble(GameTestHelper h, BlockPos helm) {
        ServerLevel level = h.getLevel();
        AssemblyResult r = com.richardsenger.piratesnships.ship.ShipTestCleanup.assemble(h, helm);
        if (r.shipId() == null) {
            throw new AssertionError("assembly failed: " + r);
        }
        ShipBody ship = SableShips.byId(level, r.shipId());
        HullRuntime rt = HullRuntimes.get(level, r.shipId());
        if (ship == null || rt == null) {
            throw new AssertionError("no ship or no hull runtime after assembly");
        }
        BlockPos helmPlot = ship.plotBlocks().stream()
                .filter(p -> level.getBlockState(p).is(AssemblyContent.HELM.get())).findFirst().orElseThrow();
        return new Fixture(ship, rt, helmPlot);
    }

    private static Vec3 world(Fixture f, BlockPos plot) {
        return f.ship().toWorld(Vec3.atCenterOf(plot));
    }

    // ------------------------------------------------------------------ tests

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void closedHullIsOccludedInsideButNotOutside(GameTestHelper h) {
        basin(h, 0, 23, true);
        Fixture f = assemble(h, hull(h, 9, false));
        h.assertTrue(f.runtime().simulation().analysis().compartments().size() == 1
                && f.runtime().simulation().analysis().compartments().get(0).volume() == HOLD,
                "expected one 18-cell compartment, got " + f.runtime().simulation().analysis().compartments());
        ServerLevel level = h.getLevel();
        h.succeedWhen(() -> {
            h.assertTrue(f.runtime().regionCount() == 1 && f.runtime().regionCells().get(0).count() == HOLD, "no dry region yet");
            h.assertTrue(WaterRegions.isOccluded(level, world(f, f.hold(0, -3, 0))), "lowest hold cell is not occluded");
            h.assertTrue(WaterRegions.isOccluded(level, world(f, f.hold(1, -2, -1))), "upper hold cell is not occluded");
            h.assertFalse(WaterRegions.isOccluded(level, Vec3.atCenterOf(h.absolutePos(new BlockPos(3, 4, 3)))), "basin water is occluded");
            h.assertTrue(Double.isFinite(f.runtime().seaShipFrame()) && f.runtime().seaShipFrame() > f.hold(0, -3, 0).getY(),
                    "sea level not found above the hold floor: " + f.runtime().seaShipFrame());
            SableShips.remove(f.ship());
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void entityInDryHullIsNotInWater(GameTestHelper h) {
        basin(h, 0, 23, true);
        Fixture f = assemble(h, hull(h, 9, false));
        ServerLevel level = h.getLevel();
        Pig inside = EntityType.PIG.create(level);
        Pig outside = EntityType.PIG.create(level);
        h.runAfterDelay(20, () -> {
            Vec3 p = world(f, f.hold(0, -3, 0));
            inside.moveTo(p.x, p.y - 0.4, p.z);
            Vec3 o = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 4, 3)));
            outside.moveTo(o.x, o.y, o.z);
            level.addFreshEntity(inside);
            level.addFreshEntity(outside);
        });
        h.runAfterDelay(30, () -> h.succeedWhen(() -> {
            h.assertTrue(outside.isInWater(), "the pig in the basin is not in water");
            h.assertFalse(inside.isInWater(), "the pig in the dry hold is in water at " + inside.position());
            h.assertFalse(inside.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), "the pig in the hold has its eyes in water");
            inside.discard();
            outside.discard();
            SableShips.remove(f.ship());
        }));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void openHatchAboveWaterlineFloodsNothing(GameTestHelper h) {
        basin(h, 0, 23, true);
        Fixture f = assemble(h, hull(h, 9, true));
        ServerLevel level = h.getLevel();
        BlockPos hatch = f.hold(-1, -1, -1);
        h.assertTrue(level.getBlockState(hatch).is(Blocks.OAK_TRAPDOOR), "no hatch in the plot at " + hatch);
        h.runAfterDelay(10, () -> level.setBlock(hatch, level.getBlockState(hatch).setValue(TrapDoorBlock.OPEN, true), Block.UPDATE_ALL));
        h.runAfterDelay(60, () -> {
            h.assertTrue(f.runtime().simulation().isOpen(gridIndex(f, hatch)), "the open hatch did not reach the simulation");
            h.assertTrue(f.runtime().simulation().totalVolume() == 0, "water came in through a hatch above the waterline: "
                    + f.runtime().simulation().totalVolume());
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300, batch = "pirates_n_ships_config_dry_hull_inflow_breach")
    public static void breachBelowWaterlineFloodsTheHold(GameTestHelper h) {
        // At the default rate one breached block needs well over the timeout to submerge a whole hold layer; ten times
        // faster keeps the test short while the flow law stays the same.
        com.richardsenger.piratesnships.core.gametest.ConfigOverrides.during(h,
                com.richardsenger.piratesnships.ship.hull.FloodingConfig.INFLOW_RATE, 10.0);
        basin(h, 0, 23, true);
        Fixture f = assemble(h, hull(h, 9, false));
        ServerLevel level = h.getLevel();
        BlockPos wall = f.hold(-2, -3, 0);
        h.runAfterDelay(20, () -> {
            h.assertTrue(level.getBlockState(wall).is(Blocks.OAK_PLANKS), "no wall at " + wall);
            level.setBlock(wall, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            h.assertTrue(f.runtime().breaches().contains(wall), "the removed hull block is not a breach");
        });
        // The re-analysis that turns the breach into an opening is debounced (and may run off-thread), so wait for the
        // first water, then require it to keep rising while the dry region shrinks.
        double[] seen = {0};
        h.runAfterDelay(21, () -> h.succeedWhen(() -> {
            double v = f.runtime().simulation().totalVolume();
            if (seen[0] == 0) {
                seen[0] = v;
                var a = f.runtime().simulation().analysis();
                throw new net.minecraft.gametest.framework.GameTestAssertException("no water in the hold yet: sea "
                        + f.runtime().seaShipFrame() + ", hold floor " + f.hold(0, -3, 0).getY() + ", breaches "
                        + f.runtime().breaches().size() + ", compartments " + a.compartments().size() + ", ports "
                        + (a.compartments().isEmpty() ? "-" : a.compartments().get(0).ports()) + ", analysing "
                        + f.runtime().isAnalysing() + ", helm world " + world(f, f.helmPlot()));
            }
            h.assertTrue(v > seen[0] && v < HOLD, "hold volume is not growing at a rate: " + seen[0] + " -> " + v);
            int dry = f.runtime().regionCells().stream().mapToInt(CellSet::count).sum();
            h.assertTrue(dry < HOLD, "the occluded cell set did not shrink: " + dry);
            SableShips.remove(f.ship());
        }));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 260)
    public static void floodedShipFloatsLowerThanDryShip(GameTestHelper h) {
        basin(h, 0, 11, true);
        basin(h, 12, 23, true);
        Fixture dry = assemble(h, hull(h, 3, false));
        Fixture wet = assemble(h, hull(h, 15, false));
        // Both ships bob after assembly, so compare the mean helm height over ticks 80..200 instead of one sample.
        double[] sum = {0, 0, 0};
        h.onEachTick(() -> {
            if (!wet.ship().isRemoved()) {
                wet.runtime().simulation().setVolume(0, HOLD);
                if (h.getTick() >= 80) {
                    sum[0] += world(dry, dry.helmPlot()).y;
                    sum[1] += world(wet, wet.helmPlot()).y;
                    sum[2]++;
                }
            }
        });
        h.runAfterDelay(200, () -> {
            double yDry = sum[0] / sum[2], yWet = sum[1] / sum[2];
            FloodReport rd = dry.runtime().lastReport(), rw = wet.runtime().lastReport();
            SableShips.remove(dry.ship());
            SableShips.remove(wet.ship());
            // Expected gap ≈ 1.4 blocks: the 18 dry cells lift 189 and the flood water weighs 189, and the hull's own
            // waterplane (25 blocks × 10.5) answers about 262 per block of draft. 0.3 leaves room for heel and bobbing.
            h.assertTrue(yWet < yDry - 0.3, "flooded ship is not lower: dry " + yDry + ", flooded " + yWet
                    + "; dry submerged " + (rd == null ? "-" : rd.submergedDryVolume()) + ", wet flood "
                    + (rw == null ? "-" : rw.floodVolume()));
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void removalLeavesNothingBehind(GameTestHelper h) {
        basin(h, 0, 23, true);
        Fixture afloat = assemble(h, hull(h, 9, false));
        ServerLevel level = h.getLevel();
        h.runAfterDelay(30, () -> {
            List<WaterRegions.Handle> a = List.copyOf(afloat.runtime().regionHandles());
            h.assertTrue(!a.isEmpty(), "the floating ship has no regions");
            SableShips.remove(afloat.ship());
            h.assertTrue(HullRuntimes.get(level, afloat.ship().id()) == null, "runtime survived removal");
            h.assertTrue(a.stream().noneMatch(x -> WaterRegions.isLive(level, x)), "regions survived removal");
            h.succeed();

        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void disassemblyLeavesNothingBehind(GameTestHelper h) {
        // The hull stands on stone in a dry dock, next to a water basin.
        dryDock(h);
        Fixture onStone = assemble(h, hull(h, 9, false));
        ServerLevel level = h.getLevel();
        // Disassembly is refused while the ship still settles on the stone (MOVING), so retry each tick like spike 1.
        h.runAfterDelay(10, () -> {
            List<WaterRegions.Handle> s = List.copyOf(onStone.runtime().regionHandles());
            h.succeedWhen(() -> {
                if (!onStone.ship().isRemoved()) {
                    AssemblyResult r = ShipAssembler.disassemble(onStone.ship(), onStone.helmPlot(), null);
                    h.assertTrue(r.success(), "not yet disassembled: " + r);
                }
                h.assertTrue(HullRuntimes.get(level, onStone.ship().id()) == null, "runtime survived disassembly");
                h.assertTrue(s.stream().noneMatch(x -> WaterRegions.isLive(level, x)), "regions survived disassembly");
            });
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void floodStateSurvivesSaveAndReload(GameTestHelper h) {
        basin(h, 0, 23, true);
        Fixture f = assemble(h, hull(h, 9, false));
        ServerLevel level = h.getLevel();
        BlockPos wall = f.hold(0, -2, 2);
        level.setBlock(wall, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        f.runtime().reanalyseNow(f.ship());
        f.runtime().simulation().setVolume(0, 4.5);
        f.runtime().save(f.ship());
        HullRuntime reloaded = HullRuntimes.onAssembled(f.ship());
        h.assertTrue(reloaded != f.runtime() && reloaded.breaches().contains(wall), "breach lost on reload");
        h.assertTrue(Math.abs(reloaded.simulation().totalVolume() - 4.5) < 1e-9, "water lost on reload: " + reloaded.simulation().totalVolume());
        SableShips.remove(f.ship());
        h.succeed();
    }

    /** Stone up to y=4 everywhere, with a water basin (x 15..22, z 1..22, water y=2..7) right next to the hull. */
    private static void dryDock(GameTestHelper h) {
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                boolean pool = x >= 15 && x <= 22 && z >= 1 && z <= 22;
                for (int y = 1; y <= 8; y++) {
                    boolean water = pool && y >= 2 && y <= 7;
                    boolean stone = y == 1 || (!pool && y <= 4) || (x == 14 || x == 23 || z == 0 || z == 23) && y <= 8 && x >= 14;
                    h.setBlock(new BlockPos(x, y, z), water ? Blocks.WATER : stone ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void hullInDryDockBesideWaterStaysPut(GameTestHelper h) {
        // The hull (x 9..13, bottom y=5) stands on stone; the basin wall at x=14 is the only thing between it and water
        // that reaches up to y=7, above the hull's floor. The old "nine columns around the ship" rule saw that as sea.
        dryDock(h);
        Fixture f = assemble(h, hull(h, 9, false));
        double y0 = world(f, f.helmPlot()).y;
        double[] maxSpeed = {0};
        h.onEachTick(() -> {
            if (!f.ship().isRemoved() && h.getTick() >= 10) {
                maxSpeed[0] = Math.max(maxSpeed[0], f.ship().linearVelocity().length());
                h.assertFalse(f.runtime().seesSea(), "the ship in the dry dock sees a sea");
            }
        });
        h.runAfterDelay(120, () -> {
            double dy = world(f, f.helmPlot()).y - y0;
            h.assertTrue(Math.abs(dy) < 0.1, "the ship in the dry dock moved by " + dy + " blocks");
            h.assertTrue(maxSpeed[0] < 0.2, "the ship in the dry dock moved at up to " + maxSpeed[0] + " m/s");
            h.assertTrue(f.runtime().simulation().totalVolume() == 0, "the dry-docked hull took on water");
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 400)
    public static void hullAfloatInBasinStaysCalm(GameTestHelper h) {
        basin(h, 0, 23, true);
        Fixture f = assemble(h, hull(h, 9, false));
        double[] max = {0, 0};
        h.onEachTick(() -> {
            if (!f.ship().isRemoved() && h.getTick() >= 40) {
                max[0] = Math.max(max[0], f.ship().linearVelocity().length());
                max[1] = Math.max(max[1], com.richardsenger.piratesnships.ship.assembly.DisassemblyMath.tiltDegrees(f.ship().orientation()));
            }
        });
        h.runAfterDelay(340, () -> {
            // Limits: after 40 ticks of settling, a 5x4x5 hull in still water must not move faster than 1 m/s (a bob,
            // not a launch: the bad runs reached 15 and 72 m/s) nor heel past 5 degrees (it is symmetric; the bad
            // run reached 16); and it must still float: its sea is found and its helm is above the water.
            h.assertTrue(max[0] < 1.0, "the floating ship moved at up to " + max[0] + " m/s");
            h.assertTrue(max[1] < 5.0, "the floating ship tilted up to " + max[1] + " degrees");
            h.assertTrue(f.runtime().seesSea(), "the floating ship sees no sea");
            double helmY = world(f, f.helmPlot()).y, water = h.absolutePos(new BlockPos(0, 7, 0)).getY() + 0.9;
            h.assertTrue(helmY > water, "the ship sank: helm at " + helmY + ", water at " + water);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ partial blocks (design.md §4.3)

    /**
     * {@link #hull} with a deck hatch, a floor of bottom slabs under the hold except one top slab at hold
     * (1, −4, 1) (empty half towards the sea), and a bottom stair facing south at hold (−1, −3, 1), its back to the
     * south wall. The hold has 17 cells.
     */
    private static BlockPos partialHull(GameTestHelper h, int a) {
        BlockPos helm = hull(h, a, true);
        for (int x = a + 1; x <= a + 3; x++) {
            for (int z = 10; z <= 12; z++) {
                SlabType type = x == a + 3 && z == 12 ? SlabType.TOP : SlabType.BOTTOM;
                h.setBlock(new BlockPos(x, 5, z), Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, type));
            }
        }
        h.setBlock(new BlockPos(a + 1, 6, 12), Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.BOTTOM));
        return helm;
    }

    private static Vec3 worldAt(Fixture f, BlockPos plot, double ox, double oy, double oz) {
        return f.ship().toWorld(new Vec3(plot.getX() + ox, plot.getY() + oy, plot.getZ() + oz));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void partialBlocksInsideAreOccludedButNotTowardsTheSea(GameTestHelper h) {
        basin(h, 0, 23, true);
        Fixture f = assemble(h, partialHull(h, 9));
        ServerLevel level = h.getLevel();
        BlockPos slab = f.hold(0, -4, 0), topSlab = f.hold(1, -4, 1), stair = f.hold(-1, -3, 1), hatch = f.hold(-1, -1, -1);
        h.assertTrue(level.getBlockState(slab).is(Blocks.OAK_SLAB) && level.getBlockState(stair).is(Blocks.OAK_STAIRS)
                && level.getBlockState(hatch).is(Blocks.OAK_TRAPDOOR), "partial blocks missing in the plot");
        h.assertTrue(f.runtime().simulation().analysis().compartments().size() == 1
                && f.runtime().simulation().analysis().compartments().get(0).volume() == HOLD - 1,
                "expected one 17-cell compartment, got " + f.runtime().simulation().analysis().compartments());
        h.succeedWhen(() -> {
            h.assertTrue(f.runtime().regionCount() == 1, "no dry region yet");
            // 17 hold cells + the stair + 7 bottom slabs (the one under the stair touches no dry cell) + the deck hatch
            // (its sky is above the sea, HV1)
            int count = f.runtime().regionCells().get(0).count();
            h.assertTrue(count == HOLD - 1 + 1 + 7 + 1, "expected 26 occluded cells, got " + count);
            h.assertFalse(f.runtime().inRegion(f.hold(-1, -4, 1)), "the slab under the stair is in the region");
            h.assertTrue(WaterRegions.isOccluded(level, worldAt(f, slab, 0.5, 0.75, 0.5)), "empty half of the floor slab shows water");
            h.assertTrue(WaterRegions.isOccluded(level, worldAt(f, stair, 0.5, 0.75, 0.25)), "empty part of the stair shows water");
            h.assertFalse(f.runtime().inRegion(topSlab), "the top slab over the sea is in the region");
            h.assertFalse(WaterRegions.isOccluded(level, worldAt(f, topSlab, 0.5, 0.25, 0.5)), "the sea in the top slab's lower half is hidden");
            h.assertFalse(WaterRegions.isOccluded(level, worldAt(f, topSlab, 0.5, -0.5, 0.5)), "the sea under the hull is hidden");
            h.assertTrue(f.runtime().inRegion(hatch), "a deck hatch above the sea is not in the region (HV1)");
            SableShips.remove(f.ship());
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300, batch = "pirates_n_ships_config_dry_hull_inflow_floor_slab")
    public static void floorSlabLeavesTheRegionWhenTheHoldFloods(GameTestHelper h) {
        com.richardsenger.piratesnships.core.gametest.ConfigOverrides.during(h,
                com.richardsenger.piratesnships.ship.hull.FloodingConfig.INFLOW_RATE, 10.0);
        basin(h, 0, 23, true);
        Fixture f = assemble(h, partialHull(h, 9));
        ServerLevel level = h.getLevel();
        BlockPos slab = f.hold(0, -4, 0), wall = f.hold(-2, -3, 0), above = f.hold(0, -3, 0);
        boolean[] seen = {false};
        h.runAfterDelay(20, () -> {
            h.assertTrue(f.runtime().inRegion(slab), "the floor slab is not in the dry region before the breach");
            seen[0] = true;
            level.setBlock(wall, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        });
        h.runAfterDelay(21, () -> h.succeedWhen(() -> {
            h.assertTrue(seen[0], "breach not made");
            h.assertFalse(f.runtime().inRegion(above), "the hold's bottom layer is still dry");
            h.assertFalse(f.runtime().inRegion(slab), "the floor slab stayed in the region under flood water");
            h.assertFalse(WaterRegions.isOccluded(level, worldAt(f, slab, 0.5, 0.75, 0.5)), "the flooded slab half is still occluded");
            SableShips.remove(f.ship());
        }));
    }

    private static int gridIndex(Fixture f, BlockPos plot) {
        var g = f.runtime().simulation().analysis().grid();
        return g.index(plot.getX() - g.originX(), plot.getY() - g.originY(), plot.getZ() - g.originZ());
    }
}

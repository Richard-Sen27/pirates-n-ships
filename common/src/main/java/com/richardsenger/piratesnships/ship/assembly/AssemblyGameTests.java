package com.richardsenger.piratesnships.ship.assembly;

import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult.Outcome;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Spike 1 GameTests: gather, assembly into a real Sable sub-level, disassembly back to the grid, refusals.
 * Physics runs in the test server and there is no sea, so test hulls stand on stone (terrain, never gathered).
 */
public final class AssemblyGameTests {

    /** Relative positions of the standard test hull: a 3×3 plank floor, the helm and a chest on top. */
    private static final BlockPos HELM = new BlockPos(4, 3, 4);
    private static final BlockPos CHEST = new BlockPos(3, 3, 3);
    private static final int HULL_SIZE = 11;

    private AssemblyGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(AssemblyGameTests.class);
    }

    // ------------------------------------------------------------------ gather

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void gatherTakesHullButNotTerrainOrWater(GameTestHelper helper) {
        Set<BlockPos> hull = buildHull(helper);
        helper.setBlock(new BlockPos(2, 2, 4), Blocks.WATER);
        helper.setBlock(new BlockPos(6, 2, 4), Blocks.WATER);
        helper.setBlock(new BlockPos(4, 2, 6), Blocks.KELP);
        SableShips.Gathered g = ShipAssembler.gather(helper.getLevel(), helper.absolutePos(HELM));
        helper.assertTrue(g.state() == SableShips.GatherState.SUCCESS, "gather failed: " + g.state());
        helper.assertTrue(g.blocks().equals(absolute(helper, hull)), "gathered " + g.blocks().size() + " blocks, expected the " + hull.size() + " hull blocks");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void isolatedHelmGathersItselfButIsNoShip(GameTestHelper helper) {
        helper.setBlock(HELM, AssemblyContent.HELM.get());
        SableShips.Gathered g = ShipAssembler.gather(helper.getLevel(), helper.absolutePos(HELM));
        helper.assertTrue(g.state() == SableShips.GatherState.SUCCESS && g.blocks().equals(Set.of(helper.absolutePos(HELM))),
                "an isolated helm should gather exactly itself");
        AssemblyResult r = ShipTestCleanup.assemble(helper, HELM);
        helper.assertTrue(r.outcome() == Outcome.NOTHING_TO_ASSEMBLE, "expected NOTHING_TO_ASSEMBLE, got " + r.outcome());
        helper.assertBlockPresent(AssemblyContent.HELM.get(), HELM);
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_assembly_limit")
    public static void hullAboveLimitIsRefused(GameTestHelper helper) {
        buildHull(helper);
        // ConfigValue.reset() only clears a local override; with a loaded loader config set() writes the real value,
        // so restore the previous value explicitly.
        int previous = AssemblyConfig.MAX_BLOCKS.get();
        AssemblyConfig.MAX_BLOCKS.set(5);
        try {
            AssemblyResult r = ShipTestCleanup.assemble(helper, HELM);
            helper.assertTrue(r.outcome() == Outcome.TOO_MANY_BLOCKS && r.count() == 5, "expected TOO_MANY_BLOCKS(5), got " + r);
            helper.assertBlockPresent(AssemblyContent.HELM.get(), HELM);
        } finally {
            AssemblyConfig.MAX_BLOCKS.set(previous);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ assembly

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void assemblyCreatesSubLevelAndShipData(GameTestHelper helper) {
        Set<BlockPos> hull = buildHull(helper);
        ServerLevel level = helper.getLevel();
        AssemblyResult r = ShipTestCleanup.assemble(helper, HELM);
        helper.assertTrue(r.outcome() == Outcome.ASSEMBLED && r.count() == HULL_SIZE, "expected ASSEMBLED(" + HULL_SIZE + "), got " + r);
        ShipBody ship = SableShips.byId(level, r.shipId());
        helper.assertTrue(ship != null, "no sub-level for " + r.shipId());
        try {
            List<BlockPos> plot = ship.plotBlocks();
            helper.assertTrue(plot.size() == HULL_SIZE, "plot holds " + plot.size() + " blocks");
            for (BlockPos p : hull) {
                helper.assertBlockPresent(Blocks.AIR, p);
            }
            BlockPos chest = find(ship, Blocks.CHEST);
            helper.assertTrue(chest != null && level.getBlockEntity(chest) instanceof ChestBlockEntity be
                    && be.getItem(0).is(Items.DIAMOND) && be.getItem(0).getCount() == 7, "chest content lost on assembly");
            BlockPos helmPlot = find(ship, AssemblyContent.HELM.get());
            helper.assertTrue(helmPlot != null && SableShips.containing(level, helmPlot) != null, "helm not found in the plot");
            ShipRegistry registry = ShipRegistry.get(level.getServer());
            helper.assertTrue(registry.find(ship.id()).isPresent(), "no ShipData for the ship");
            helper.assertTrue(ship.userData(ShipAssembler.USER_DATA_KEY).getUUID("ship").equals(ship.id()), "no back-pointer in user data");
            helper.assertTrue(ShipAssembler.name(ship, "Black Pearl").success()
                    && registry.find(ship.id()).map(ShipData::name).orElse("").equals("Black Pearl"), "naming failed");
        } finally {
            SableShips.remove(ship);
        }
        helper.assertTrue(ShipRegistry.get(level.getServer()).find(r.shipId()).isEmpty(), "ShipData survived sub-level removal");
        helper.succeed();
    }

    /**
     * Regression (D2c): a ship removed and another assembled in the same tick share the plot (Sable reuses the first
     * free one), and the server chunk cache's memo still held the removed ship's chunk, so the new ship's chest arrived
     * empty. Read through the chunk map ({@code getChunkNow}, which Sable redirects to the live plot) to see the real
     * block entity, not the memo.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void plotFreedThisTickKeepsChestContent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildHull(helper);
        AssemblyResult first = ShipTestCleanup.assemble(helper, HELM);
        helper.assertTrue(first.success(), "first assembly failed: " + first);
        ShipBody old = SableShips.byId(level, first.shipId());
        helper.assertTrue(find(old, Blocks.CHEST) != null, "first ship has no chest"); // memoises the plot's chunk
        SableShips.remove(old);
        buildHull(helper);
        AssemblyResult second = ShipTestCleanup.assemble(helper, HELM);
        helper.assertTrue(second.success(), "second assembly failed: " + second);
        ShipBody ship = SableShips.byId(level, second.shipId());
        BlockPos chest = find(ship, Blocks.CHEST);
        helper.assertTrue(chest != null, "second ship has no chest in its plot");
        net.minecraft.world.level.chunk.LevelChunk live = level.getChunkSource().getChunkNow(chest.getX() >> 4, chest.getZ() >> 4);
        helper.assertTrue(live != null && live.getBlockEntity(chest) instanceof ChestBlockEntity be
                && be.getItem(0).is(Items.DIAMOND) && be.getItem(0).getCount() == 7, "chest content lost when assembling into a plot freed this tick");
        helper.assertTrue(level.getBlockEntity(chest) == live.getBlockEntity(chest), "Level#getBlockEntity sees a stale chunk");
        helper.succeed();
    }

    /**
     * Regression (CW1b, docs/sable-notes.md §9.0m): a ship removed and another assembled in the same tick share the plot,
     * and Sable kept the removed ship's physics sections and tickets, so the new ship carried the old one's blocks as
     * phantom voxels wherever it has air. The old ship here is a helm with a plank column and two long plank beams (the
     * shape that showed it in the CW1b experiments); the new one is the 5×4×5 test hull, which floats level in a fresh
     * plot. Without the fix its -z end sat 0.24 blocks above its +z end (corner heights at the bottom, mean of 60
     * ticks); with it both differences are 0.0000.
     */
    // Own batch: Sable hands out the lowest free plot, so a plot freed by another test of the batch between the ghost's
    // assembly and its removal would be taken instead of the ghost's.
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 160, batch = "pirates_n_ships_assembly_plot_reuse")
    public static void plotFreedThisTickHasNoPhantomBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ghostHelm = new BlockPos(5, 10, 11);
        helper.setBlock(ghostHelm, AssemblyContent.HELM.get());
        for (int y = 6; y <= 9; y++) {
            helper.setBlock(new BlockPos(4, y, 11), Blocks.OAK_PLANKS);
        }
        for (int z = 0; z <= 23; z++) {
            helper.setBlock(new BlockPos(4, 6, z), Blocks.OAK_PLANKS);
        }
        for (int x = 0; x <= 11; x++) {
            helper.setBlock(new BlockPos(x, 6, 2), Blocks.OAK_PLANKS);
        }
        AssemblyResult ghost = ShipTestCleanup.assemble(helper, ghostHelm);
        helper.assertTrue(ghost.success(), "ghost assembly failed: " + ghost);
        ShipBody ghostShip = SableShips.byId(level, ghost.shipId());
        BlockPos ghostPlotHelm = find(ghostShip, AssemblyContent.HELM.get());
        AtomicReference<ShipBody> fresh = new AtomicReference<>();
        AtomicReference<BlockPos> helmPlot = new AtomicReference<>();
        double[] tilt = new double[3];
        helper.onEachTick(() -> {
            long t = helper.getTick();
            if (t == 20) {
                // the ghost has lived 20 ticks (falling in the empty template); remove it and assemble the new hull
                SableShips.remove(ghostShip);
                com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.basin(helper, 0, 23, true);
                BlockPos helm = com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.hull(helper, 9, false);
                AssemblyResult r = ShipTestCleanup.assemble(helper, helm);
                helper.assertTrue(r.success(), "assembly failed: " + r);
                ShipBody ship = SableShips.byId(level, r.shipId());
                BlockPos plotHelm = find(ship, AssemblyContent.HELM.get());
                helper.assertTrue(plotHelm.equals(ghostPlotHelm), "the new ship did not get the freed plot: " + plotHelm + " vs " + ghostPlotHelm);
                fresh.set(ship);
                helmPlot.set(plotHelm);
            } else if (t > 60 && t <= 120 && fresh.get() != null) {
                ShipBody ship = fresh.get();
                BlockPos h = helmPlot.get();
                tilt[0] += ship.toWorld(Vec3.atCenterOf(h.offset(-2, -4, 0))).y - ship.toWorld(Vec3.atCenterOf(h.offset(2, -4, 0))).y;
                tilt[1] += ship.toWorld(Vec3.atCenterOf(h.offset(0, -4, -2))).y - ship.toWorld(Vec3.atCenterOf(h.offset(0, -4, 2))).y;
                tilt[2]++;
            } else if (t == 121) {
                double roll = tilt[0] / tilt[2];
                double pitch = tilt[1] / tilt[2];
                String info = String.format(java.util.Locale.ROOT, "-x side %.4f above +x, -z side %.4f above +z (mean of 60 ticks)", roll, pitch);
                com.richardsenger.piratesnships.Constants.LOG.info("CW1b plotFreedThisTickHasNoPhantomBlocks: {}", info);
                SableShips.remove(fresh.get());
                helper.assertTrue(Math.abs(roll) < 0.05 && Math.abs(pitch) < 0.05,
                        "the hull in a plot freed this tick is not level (phantom blocks of the removed ship): " + info);
                helper.succeed();
            }
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void assemblyInWaterRestoresTheSea(GameTestHelper helper) {
        // Stone basin (x/z 0..8, floor y=1, walls y=2..3) full of water around a 5×5 open boat at x/z 2..6.
        helper.forEveryBlockInStructure(p -> {
            boolean wall = p.getX() == 0 || p.getX() == 8 || p.getZ() == 0 || p.getZ() == 8;
            if (p.getY() == 1 || wall && (p.getY() == 2 || p.getY() == 3)) {
                helper.setBlock(p, Blocks.STONE);
            } else if (p.getY() == 2 || p.getY() == 3) {
                boolean inBoat = p.getX() >= 2 && p.getX() <= 6 && p.getZ() >= 2 && p.getZ() <= 6;
                boolean boatWall = p.getX() == 2 || p.getX() == 6 || p.getZ() == 2 || p.getZ() == 6;
                if (!inBoat) {
                    helper.setBlock(p, Blocks.WATER);
                } else if (p.getY() == 2 || boatWall) {
                    helper.setBlock(p, Blocks.OAK_PLANKS);
                }
            }
        });
        helper.setBlock(new BlockPos(4, 3, 4), AssemblyContent.HELM.get());
        AssemblyResult r = ShipTestCleanup.assemble(helper, new BlockPos(4, 3, 4));
        helper.assertTrue(r.outcome() == Outcome.ASSEMBLED && r.count() == 25 + 16 + 1, "expected 42 blocks, got " + r);
        try {
            for (int x = 2; x <= 6; x++) {
                for (int z = 2; z <= 6; z++) {
                    helper.assertBlockPresent(Blocks.WATER, new BlockPos(x, 2, z));
                    helper.assertBlockPresent(Blocks.WATER, new BlockPos(x, 3, z));
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, 4, z));
                }
            }
        } finally {
            SableShips.remove(SableShips.byId(helper.getLevel(), r.shipId()));
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ disassembly

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 300)
    public static void disassemblyPutsBlocksBackOnTheGrid(GameTestHelper helper) {
        Set<BlockPos> hull = buildHull(helper);
        ServerLevel level = helper.getLevel();
        // A mob standing on the deck plank at (5, 2, 5): it must end up standing on the placed plank again.
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(5.5, 3.0, 5.5));
        AssemblyResult assembled = ShipTestCleanup.assemble(helper, HELM);
        helper.assertTrue(assembled.success(), "assembly failed: " + assembled);
        AtomicReference<AssemblyResult> done = new AtomicReference<>();
        helper.runAfterDelay(10, () -> helper.succeedWhen(() -> {
            if (done.get() == null) {
                ShipBody ship = SableShips.byId(level, assembled.shipId());
                helper.assertTrue(ship != null, "ship vanished before disassembly");
                AssemblyResult r = ShipAssembler.disassemble(ship, find(ship, AssemblyContent.HELM.get()), null);
                if (!r.success()) {
                    throw new GameTestAssertException("not yet disassembled: " + r.outcome() + " " + r.value());
                }
                done.set(r);
                com.richardsenger.piratesnships.Constants.LOG.info("[gametest] disassembled after {} ticks", helper.getTick());
            }
            helper.assertTrue(done.get().count() == HULL_SIZE, "moved " + done.get().count() + " blocks");
            // Tolerance: the ship rests on the stone it was built on, but contact physics may settle it a fraction of a
            // block. The helm must land within one block of its old spot; every other block must keep its exact offset.
            BlockPos offset = null;
            for (BlockPos d : BlockPos.betweenClosed(-1, -1, -1, 1, 1, 1)) {
                if (helper.getBlockState(HELM.offset(d)).is(AssemblyContent.HELM.get())) {
                    offset = d.immutable();
                }
            }
            helper.assertTrue(offset != null, "helm not back near " + HELM);
            com.richardsenger.piratesnships.Constants.LOG.info("[gametest] helm landed at offset {} from its old spot", offset);
            for (BlockPos p : hull) {
                helper.assertTrue(!helper.getBlockState(p.offset(offset)).isAir(), "missing block at " + p.offset(offset));
            }
            helper.assertTrue(helper.getBlockEntity(CHEST.offset(offset)) instanceof ChestBlockEntity be
                    && be.getItem(0).is(Items.DIAMOND) && be.getItem(0).getCount() == 7, "chest content lost on disassembly");
            helper.assertTrue(SableShips.byId(level, assembled.shipId()) == null, "sub-level still exists");
            // Tolerance: half a block horizontally (it may have shuffled while the ship settled), 0.1 vertically.
            Vec3 deckTop = Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 2, 5).offset(offset))).add(0, 0.5, 0);
            helper.assertTrue(Math.abs(pig.getY() - deckTop.y) < 0.1 && Math.abs(pig.getX() - deckTop.x) < 0.5
                    && Math.abs(pig.getZ() - deckTop.z) < 0.5, "pig not on deck: " + pig.position() + " vs " + deckTop);
            helper.assertTrue(level.noCollision(pig), "pig stuck in a block");
            helper.assertTrue(ShipRegistry.get(level.getServer()).find(assembled.shipId()).isEmpty(), "ShipData not removed");
        }));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void yawedShipSnapsToQuarterTurn(GameTestHelper helper) {
        ShipBody ship = assembleHull(helper);
        // Yawed 88° (CCW from above): snaps to one quarter turn. Checks that Sable's moveBlocks lands every block where
        // our obstruction check looked (DisassemblyMath.target) and rotates block states.
        ship.setOrientation(new Quaterniond().rotateAxis(Math.toRadians(88), 0, 1, 0));
        BlockPos helmPlot = find(ship, AssemblyContent.HELM.get());
        BlockPos goal = BlockPos.containing(ship.toWorld(Vec3.atCenterOf(helmPlot)));
        int turns = DisassemblyMath.quarterTurns(ship.orientation());
        helper.assertTrue(turns == 1, "expected one quarter turn, got " + turns);
        List<BlockPos> targets = ship.plotBlocks().stream().map(b -> DisassemblyMath.target(b, helmPlot, goal, turns)).toList();
        AssemblyResult r = ShipAssembler.disassemble(ship, helmPlot, null);
        helper.assertTrue(r.outcome() == Outcome.DISASSEMBLED, "expected DISASSEMBLED, got " + r);
        for (BlockPos t : targets) {
            helper.assertTrue(!helper.getLevel().getBlockState(t).isAir(), "nothing placed at predicted " + t);
        }
        helper.assertTrue(helper.getLevel().getBlockState(goal).getValue(HelmBlock.FACING) == net.minecraft.core.Direction.WEST,
                "helm facing not rotated with the ship");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void disassemblyRefusedWhileMoving(GameTestHelper helper) {
        ShipBody ship = assembleHull(helper);
        ship.addVelocity(new Vector3d(0, 6, 0), new Vector3d());
        helper.runAfterDelay(1, () -> {
            ShipBody s = SableShips.byId(helper.getLevel(), ship.id());
            AssemblyResult r = ShipAssembler.disassemble(s, find(s, AssemblyContent.HELM.get()), null);
            SableShips.remove(s);
            helper.assertTrue(r.outcome() == Outcome.MOVING, "expected MOVING, got " + r.outcome());
            helper.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void disassemblyRefusedWhileTilted(GameTestHelper helper) {
        ShipBody ship = assembleHull(helper);
        ship.setOrientation(new Quaterniond().rotateAxis(Math.toRadians(20), 1, 0, 0));
        AssemblyResult r = ShipAssembler.disassemble(ship, find(ship, AssemblyContent.HELM.get()), null);
        SableShips.remove(ship);
        helper.assertTrue(r.outcome() == Outcome.NOT_LEVEL && Math.abs(r.value() - 20) < 0.5, "expected NOT_LEVEL(20), got " + r);
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void disassemblyRefusedWhenObstructed(GameTestHelper helper) {
        ShipBody ship = assembleHull(helper);
        BlockPos blocker = new BlockPos(3, 2, 3);
        helper.setBlock(blocker, Blocks.COBBLESTONE);
        AssemblyResult r = ShipAssembler.disassemble(ship, find(ship, AssemblyContent.HELM.get()), null);
        boolean stillThere = SableShips.byId(helper.getLevel(), ship.id()) != null;
        SableShips.remove(ship);
        helper.assertTrue(r.outcome() == Outcome.OBSTRUCTED && helper.absolutePos(blocker).equals(r.where()),
                "expected OBSTRUCTED at " + helper.absolutePos(blocker) + ", got " + r);
        helper.assertTrue(stillThere, "a refused disassembly must keep the ship");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** Stone floor (terrain) at y=1, plank floor at y=2 (x/z 3..5), helm and chest with 7 diamonds at y=3. */
    private static Set<BlockPos> buildHull(GameTestHelper helper) {
        Set<BlockPos> hull = new HashSet<>();
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                BlockPos p = new BlockPos(x, 2, z);
                helper.setBlock(p, Blocks.OAK_PLANKS);
                hull.add(p);
            }
        }
        helper.setBlock(HELM, AssemblyContent.HELM.get());
        helper.setBlock(CHEST, Blocks.CHEST);
        if (helper.getBlockEntity(CHEST) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
        }
        hull.add(HELM);
        hull.add(CHEST);
        return hull;
    }

    private static ShipBody assembleHull(GameTestHelper helper) {
        buildHull(helper);
        AssemblyResult r = ShipTestCleanup.assemble(helper, HELM);
        helper.assertTrue(r.success(), "assembly failed: " + r);
        ShipBody ship = SableShips.byId(helper.getLevel(), r.shipId());
        helper.assertTrue(ship != null, "no sub-level");
        return ship;
    }

    private static BlockPos find(ShipBody ship, Block block) {
        for (BlockPos p : ship.plotBlocks()) {
            if (ship.level().getBlockState(p).is(block)) {
                return p;
            }
        }
        return null;
    }

    private static Set<BlockPos> absolute(GameTestHelper helper, Set<BlockPos> relative) {
        Set<BlockPos> out = new HashSet<>();
        relative.forEach(p -> out.add(helper.absolutePos(p)));
        return out;
    }
}

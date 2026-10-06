package com.richardsenger.piratesnships.ship.assembly;

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
        AssemblyResult r = ShipAssembler.assemble(helper.getLevel(), helper.absolutePos(HELM), null);
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
            AssemblyResult r = ShipAssembler.assemble(helper.getLevel(), helper.absolutePos(HELM), null);
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
        AssemblyResult r = ShipAssembler.assemble(level, helper.absolutePos(HELM), null);
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
        AssemblyResult r = ShipAssembler.assemble(helper.getLevel(), helper.absolutePos(new BlockPos(4, 3, 4)), null);
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
        AssemblyResult assembled = ShipAssembler.assemble(level, helper.absolutePos(HELM), null);
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
        AssemblyResult r = ShipAssembler.assemble(helper.getLevel(), helper.absolutePos(HELM), null);
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

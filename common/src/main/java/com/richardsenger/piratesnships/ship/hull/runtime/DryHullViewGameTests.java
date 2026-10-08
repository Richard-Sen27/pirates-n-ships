package com.richardsenger.piratesnships.ship.hull.runtime;

import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.hull.HullTags;
import com.richardsenger.piratesnships.ship.hull.client.PlantFootprint;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.WaterRegions;
import io.netty.buffer.Unpooled;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dry hull view (HV1, docs/design.md §4.4): a camera in a deck opening counts as inside the ship, and the water plants
 * that the client hides inside dry regions are tagged. Fixtures from {@link DryHullGameTests} (5×4×5 plank hull, hold
 * y 6..7, deck y 8, in a basin with water up to y 7).
 */
public final class DryHullViewGameTests {

    private DryHullViewGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(DryHullViewGameTests.class);
    }

    private static Vec3 worldAt(DryHullGameTests.Fixture f, BlockPos plot, double ox, double oy, double oz) {
        return f.ship().toWorld(new Vec3(plot.getX() + ox, plot.getY() + oy, plot.getZ() + oz));
    }

    private static String where(DryHullGameTests.Fixture f, ServerLevel level, BlockPos plot) {
        Vec3 w = worldAt(f, plot, 0.5, 0.5, 0.5);
        BlockPos b = BlockPos.containing(w);
        return plot + " (world " + w + ", fluid " + level.getFluidState(b).getType() + " h" + level.getFluidState(b).getHeight(level, b)
                + ", in region " + f.runtime().inRegion(plot) + ", sea " + f.runtime().seaShipFrame() + ")";
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void openDeckHatchIsInsideTheShip(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        DryHullGameTests.Fixture f = DryHullGameTests.assemble(h, DryHullGameTests.hull(h, 9, true));
        ServerLevel level = h.getLevel();
        BlockPos hatch = f.hold(-1, -1, -1), sky = hatch.above(), below = hatch.below();
        h.assertTrue(level.getBlockState(hatch).is(Blocks.OAK_TRAPDOOR), "no hatch in the plot at " + hatch);
        h.runAfterDelay(5, () -> level.setBlock(hatch, level.getBlockState(hatch).setValue(TrapDoorBlock.OPEN, true), Block.UPDATE_ALL));
        h.runAfterDelay(10, () -> h.succeedWhen(() -> {
            h.assertTrue(f.runtime().regionCount() == 1, "no dry region yet");
            h.assertTrue(f.runtime().inRegion(below), "the hold under the hatch is not dry: " + where(f, level, below));
            h.assertTrue(f.runtime().inRegion(hatch), "the open deck hatch is not in the dry region: " + where(f, level, hatch));
            for (double y : new double[] {0.05, 0.5, 0.95}) {
                h.assertTrue(WaterRegions.isOccluded(level, worldAt(f, hatch, 0.5, y, 0.5)),
                        "a camera at height " + y + " in the hatch is not occluded: " + where(f, level, hatch));
            }
            h.assertFalse(f.runtime().inRegion(sky), "the air above the deck is in the region");
            // outside the hull: the basin water beside the west wall at hold height stays water
            BlockPos besideWall = f.hold(-3, -2, 0);
            h.assertFalse(WaterRegions.isOccluded(level, worldAt(f, besideWall, 0.5, 0.5, 0.5)),
                    "the sea beside the hull is occluded: " + where(f, level, besideWall));
            SableShips.remove(f.ship());
        }));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void trapdoorUnderADeckOpeningIsInsideTheShip(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        // a deck opening at (12, 8, 12) closed from below by an open trapdoor hung under the deck at (12, 7, 12)
        h.setBlock(new BlockPos(12, 8, 12), Blocks.AIR);
        h.setBlock(new BlockPos(12, 7, 12), Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.HALF, Half.TOP).setValue(TrapDoorBlock.OPEN, true));
        DryHullGameTests.Fixture f = DryHullGameTests.assemble(h, helm);
        ServerLevel level = h.getLevel();
        BlockPos trapdoor = f.hold(1, -2, 1), opening = trapdoor.above();
        h.assertTrue(level.getBlockState(trapdoor).is(Blocks.SPRUCE_TRAPDOOR), "no trapdoor in the plot at " + trapdoor);
        h.succeedWhen(() -> {
            h.assertTrue(f.runtime().regionCount() >= 1, "no dry region yet");
            h.assertTrue(f.runtime().inRegion(trapdoor), "the open trapdoor is not in the dry region: " + where(f, level, trapdoor));
            h.assertTrue(WaterRegions.isOccluded(level, worldAt(f, trapdoor, 0.5, 0.5, 0.5)), "a camera in the trapdoor cell is not occluded");
            h.assertTrue(f.runtime().inRegion(opening), "the deck opening above the trapdoor is not in the dry region: " + where(f, level, opening));
            h.assertFalse(f.runtime().inRegion(opening.above()), "the air above the deck is in the region");
            SableShips.remove(f.ship());
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_3)
    public static void waterPlantsAreHiddenInDryHulls(GameTestHelper h) {
        for (Block b : new Block[] {Blocks.SEAGRASS, Blocks.TALL_SEAGRASS, Blocks.KELP, Blocks.KELP_PLANT, Blocks.SEA_PICKLE,
                Blocks.BUBBLE_COLUMN}) {
            h.assertTrue(b.defaultBlockState().is(HullTags.HIDDEN_IN_DRY_HULL), b + " is not in #hidden_in_dry_hull");
        }
        for (Block b : new Block[] {Blocks.WATER, Blocks.OAK_PLANKS, Blocks.OAK_TRAPDOOR, Blocks.TUBE_CORAL}) {
            h.assertFalse(b.defaultBlockState().is(HullTags.HIDDEN_IN_DRY_HULL), b + " is in #hidden_in_dry_hull");
        }
        h.succeed();
    }

    /**
     * HV1c: a water plant whose cell a dry region cuts only in part (the region's boundary runs through the cell, here the
     * hold floor or the deck underside of a floating hull at a fractional height) is hidden, as is a tall seagrass whose
     * upper half alone is cut; a seagrass a full block away from every region stays. Ground truth is Sable's own point
     * test ({@code isOccluded}) on sample points, independent of the footprint's box test; the client builds the same
     * {@link PlantFootprint} from its own regions.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300)
    public static void plantsInPartlyCoveredCellsAreHidden(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        DryHullGameTests.Fixture f = DryHullGameTests.assemble(h, DryHullGameTests.hull(h, 9, false));
        ServerLevel level = h.getLevel();
        BlockState grass = Blocks.SEAGRASS.defaultBlockState();
        BlockState tallLower = Blocks.TALL_SEAGRASS.defaultBlockState().setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState tallUpper = tallLower.setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.UPPER);
        h.runAfterDelay(10, () -> h.succeedWhen(() -> {
            h.assertTrue(f.runtime().regionCount() >= 1, "no dry region yet");
            PlantFootprint footprint = PlantFootprint.of(WaterRegions.views(level), new long[1]);
            AABB box = f.ship().worldBounds().inflate(1);
            BlockPos cut = null, far = null, tallRoot = null;
            for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
                boolean center = WaterRegions.isOccluded(level, Vec3.atCenterOf(p));
                if (cut == null && !center && occludedSample(level, p, Vec3.ZERO, 0, 6)) {
                    cut = p.immutable();
                }
                if (tallRoot == null && !center) {
                    Vec3 o = tallLower.getOffset(level, p);
                    if (!occludedSample(level, p, o, 0, 6) && occludedSample(level, p.above(), o, 0, 6)) {
                        tallRoot = p.immutable();
                    }
                }
            }
            if (cut == null || tallRoot == null) {
                throw new GameTestAssertException("the ship's pose cuts no cell in part yet (cut " + cut + ", tall " + tallRoot + ")");
            }
            // two or three blocks away from the cut cell: a block whose cell grown by a full block reaches no region
            for (int d = 2; d <= 3 && far == null; d++) {
                for (Direction dir : Direction.values()) {
                    BlockPos p = cut.relative(dir, d);
                    if (far == null && !occludedSample(level, p, Vec3.ZERO, 1.0, 12)) {
                        far = p;
                    }
                }
            }
            h.assertTrue(footprint.hides(grass, cut), "seagrass at " + cut + ", whose cell the region cuts but whose center "
                    + "is outside, is not hidden");
            h.assertTrue(footprint.hides(tallLower, tallRoot) && footprint.hides(tallUpper, tallRoot.above()),
                    "tall seagrass at " + tallRoot + " with only its upper half in the region is not hidden as a whole");
            h.assertTrue(far != null, "no block a full block outside the region found near the ship");
            h.assertFalse(footprint.hides(grass, far), "seagrass at " + far + ", a full block outside the region, is hidden");
            h.assertFalse(footprint.hides(Blocks.OAK_PLANKS.defaultBlockState(), cut), "an untagged block is hidden");
            SableShips.remove(f.ship());
        }));
    }

    /** Whether one of n³ sample points of the block at p, shifted and grown by {@code grow}, is in a region (Sable's test). */
    private static boolean occludedSample(ServerLevel level, BlockPos p, Vec3 shift, double grow, int n) {
        double size = 1 + 2 * grow;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    Vec3 q = new Vec3(p.getX() + shift.x - grow + size * (i + 0.5) / n,
                            p.getY() + shift.y - grow + size * (j + 0.5) / n, p.getZ() + shift.z - grow + size * (k + 0.5) / n);
                    if (WaterRegions.isOccluded(level, q)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * FLD1: a partly flooded hold is synced as one water surface carrying the hold's 18 cells and its level (0.7 blocks
     * above the hold floor for 6.3 blocks of water in the 3×3 floor layer), through the payload codec; drained, the
     * next payload clears it.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void floodSurfaceSyncCarriesTheCellsAndTheLevel(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        DryHullGameTests.Fixture f = DryHullGameTests.assemble(h, DryHullGameTests.hull(h, 9, false));
        f.runtime().simulation().setVolume(0, 6.3);
        BlockPos floor = f.hold(0, -3, 0);
        boolean[] drained = {false};
        h.succeedWhen(() -> {
            FloodSurfacePayload p = f.runtime().floodSurfaces();
            h.assertTrue(p != null && p.ship().equals(f.ship().id()), "no flood surface payload for the ship yet");
            if (!drained[0]) {
                h.assertTrue(p.surfaces().size() == 1, "expected one flooded compartment, got " + p.surfaces());
                FloodSurfacePayload.Surface s = p.surfaces().get(0);
                h.assertTrue(s.cells().count() == 18 && s.cells().contains(floor.getX(), floor.getY(), floor.getZ())
                                && s.cells().contains(floor.getX() + 1, floor.getY() + 1, floor.getZ() + 1),
                        "the surface does not carry the hold's cells: " + s.cells().count() + " cells");
                h.assertTrue(s.cells().minY() == floor.getY(), "the cell set does not start at the hold floor");
                h.assertTrue(Math.abs(s.level() - 0.7f) < 0.05f, "level " + s.level() + " is not 0.7 blocks above the floor");
                h.assertTrue(p.upY() > 0.99f, "up vector " + p.upX() + ", " + p.upY() + ", " + p.upZ());
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
                FloodSurfacePayload.CODEC.encode(buf, p);
                h.assertTrue(FloodSurfacePayload.CODEC.decode(buf).equals(p), "the payload does not survive its codec");
                h.assertFalse(f.runtime().inRegion(floor), "the flooded floor cell is still in the dry region");
                drained[0] = true;
                f.runtime().simulation().setVolume(0, 0);
                throw new GameTestAssertException("waiting for the drained payload");
            }
            h.assertTrue(p.surfaces().isEmpty(), "the drained hold still has a surface: " + p.surfaces());
            SableShips.remove(f.ship());
        });
    }
}

package com.richardsenger.piratesnships.ship.hull.runtime;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.hull.HullTags;
import com.richardsenger.piratesnships.ship.hull.client.PlantFootprint;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.WaterRegions;
import com.richardsenger.piratesnships.ship.template.ShipTemplatePlacer;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import io.netty.buffer.Unpooled;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.joml.Quaterniond;
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
            Constants.LOG.info("HV1c GameTest: partly cut cell {}, tall seagrass root {}, far cell {}, footprint {} + {} "
                    + "offset blocks", cut, tallRoot, far, footprint.cells().size(), footprint.offsetCells().size());
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


    // ------------------------------------------------------------------ HV2: the view down an open deck hatch

    /** What fills the space between the deck hatch and the hold in {@link #waterlineHull}. */
    enum Shaft {
        /** Two ladder cells (the human's screenshot): hatch, ladder, ladder, then the hold. */
        TWO_LADDERS,
        /** One ladder cell, the hold one layer taller. */
        ONE_LADDER,
        /**
         * A bottom slab under the hatch, its south side open to an air pocket that leads down into the hold: the hatch
         * touches no compartment, only the slab's empty half (joins transitively, {@code PartialCellRule}).
         */
        SLAB
    }

    /**
     * A heavy 5×6×5 hull resting on four stone pillars in {@link DryHullGameTests#basin} (water y 2..7, surface 7.89):
     * iron floor y=3, hold y=4..5 (3×3), iron layers y=6..7 with the shaft at (11, 6..7, 11), plank deck y=8 with a
     * spruce trapdoor at (11, 8, 11), helm at (10, 9, 10). The sea surface lies 0.11 under the deck, in the upper shaft
     * cell, like the starter sloop's in the human's screenshot. Returns the helm.
     */
    private static BlockPos waterlineHull(GameTestHelper h, Shaft shaft, boolean open) {
        DryHullGameTests.basin(h, 0, 23, true);
        int a = 9;
        for (int x : new int[] {a, a + 4}) {
            for (int z : new int[] {9, 13}) {
                h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
            }
        }
        int holdTop = shaft == Shaft.ONE_LADDER ? 6 : 5;
        for (int x = a; x <= a + 4; x++) {
            for (int z = 9; z <= 13; z++) {
                for (int y = 3; y <= 8; y++) {
                    boolean hold = y >= 4 && y <= holdTop && x > a && x < a + 4 && z > 9 && z < 13;
                    h.setBlock(new BlockPos(x, y, z), hold ? Blocks.AIR : y == 8 ? Blocks.OAK_PLANKS : Blocks.IRON_BLOCK);
                }
            }
        }
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        switch (shaft) {
            case TWO_LADDERS -> {
                h.setBlock(new BlockPos(a + 2, 6, 11), ladder);
                h.setBlock(new BlockPos(a + 2, 7, 11), ladder);
            }
            case ONE_LADDER -> h.setBlock(new BlockPos(a + 2, 7, 11), ladder);
            case SLAB -> {
                h.setBlock(new BlockPos(a + 2, 7, 11), Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                h.setBlock(new BlockPos(a + 2, 7, 12), Blocks.AIR);
                h.setBlock(new BlockPos(a + 2, 6, 12), Blocks.AIR);
            }
        }
        h.setBlock(new BlockPos(a + 2, 8, 11), Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)
                .setValue(TrapDoorBlock.FACING, Direction.NORTH).setValue(TrapDoorBlock.OPEN, open));
        BlockPos helm = new BlockPos(a + 1, 9, 10);
        h.setBlock(helm, AssemblyContent.HELM.get());
        return helm;
    }

    /**
     * Asserts that the whole column under {@code hatch} down to {@code bottom} (plot cells) is in the ship's one dry
     * region, and that every sample point along it, the sea surface included, is occluded by one and the same Sable
     * region (the depth mask ends at the first back face of any region, so a second region along the line would let the
     * sea show behind the boundary).
     */
    private static void assertDryColumn(GameTestHelper h, ServerLevel level, ShipBody ship, HullRuntime rt, BlockPos top,
                                        BlockPos bottom, String what) {
        h.assertTrue(rt.regionCount() == 1, what + ": expected one region for the ship, got " + rt.regionCount());
        for (BlockPos p = top; p.getY() >= bottom.getY(); p = p.below()) {
            h.assertTrue(rt.inRegion(p), what + ": " + level.getBlockState(p).getBlock() + " at " + p + " is not in the dry region"
                    + " (world y " + ship.toWorld(Vec3.atCenterOf(p)).y + ", sea " + rt.seaWorldY() + ")");
        }
        List<WaterRegions.View> views = WaterRegions.views(level);
        Set<WaterRegions.View> hit = new HashSet<>();
        int samples = 0;
        for (double y = top.getY() + 0.95; y >= bottom.getY() + 0.05; y -= 0.1) {
            Vec3 w = ship.toWorld(new Vec3(top.getX() + 0.5, y, top.getZ() + 0.5));
            h.assertTrue(WaterRegions.isOccluded(level, w), what + ": the column is not occluded at plot y " + y);
            hit.add(owner(views, w));
            samples++;
        }
        Vec3 sea = ship.toWorld(new Vec3(top.getX() + 0.5, top.getY() + 0.5, top.getZ() + 0.5));
        sea = new Vec3(sea.x, rt.seaWorldY() - 0.01, sea.z);
        h.assertTrue(WaterRegions.isOccluded(level, sea), what + ": the sea surface in the shaft is not occluded at " + sea);
        hit.add(owner(views, sea));
        h.assertTrue(hit.size() == 1 && !hit.contains(null), what + ": " + samples + " points along the hatch column lie in "
                + hit.size() + " regions");
    }

    private static WaterRegions.View owner(List<WaterRegions.View> views, Vec3 world) {
        for (WaterRegions.View v : views) {
            Vec3 p = v.toPlot(world);
            if (v.occupied(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z))) {
                return v;
            }
        }
        return null;
    }

    /**
     * Runs one shaft variant: closed hatch first, then open, both must give the dry column from the hatch down into the
     * hold. The sea surface must lie in the shaft (the human's case), or the test proves nothing.
     */
    private static void shaftCase(GameTestHelper h, Shaft shaft, int shaftCells, boolean yaw) {
        DryHullGameTests.Fixture f = DryHullGameTests.assemble(h, waterlineHull(h, shaft, false));
        ServerLevel level = h.getLevel();
        BlockPos hatch = f.hold(1, -1, 1);
        BlockPos bottom = hatch.below(shaftCells + 1); // the first hold cell under the shaft
        h.assertTrue(level.getBlockState(hatch).is(Blocks.SPRUCE_TRAPDOOR), "no hatch in the plot at " + hatch);
        int[] phase = {0};
        h.runAfterDelay(20, () -> h.succeedWhen(() -> {
            HullRuntime rt = f.runtime();
            double deck = f.ship().toWorld(Vec3.atLowerCornerOf(hatch)).y;
            h.assertTrue(rt.seesSea() && rt.seaWorldY() < deck && rt.seaWorldY() > deck - 1,
                    "the sea (" + rt.seaWorldY() + ") is not in the cell under the deck (" + deck + ")");
            BlockPos top = hatch;
            if (phase[0] == 0) {
                assertDryColumn(h, level, f.ship(), rt, top, shaft == Shaft.SLAB ? hatch.below() : bottom, shaft + " closed");
                phase[0] = 1;
                level.setBlock(hatch, level.getBlockState(hatch).setValue(TrapDoorBlock.OPEN, true), Block.UPDATE_ALL);
                throw new GameTestAssertException("waiting for the open hatch");
            }
            h.assertTrue(level.getBlockState(hatch).getValue(TrapDoorBlock.OPEN), "the hatch did not open");
            assertDryColumn(h, level, f.ship(), rt, top, shaft == Shaft.SLAB ? hatch.below() : bottom, shaft + " open");
            if (shaft == Shaft.SLAB) {
                BlockPos pocket = hatch.below().south();
                h.assertTrue(rt.inRegion(pocket) && rt.inRegion(pocket.below()), "the air pocket beside the slab is not dry");
            }
            if (yaw && phase[0] == 1) {
                // a yawed ship: the region turns with the plot, the column stays occluded in world space
                phase[0] = 2;
                f.ship().setOrientation(new Quaterniond().rotateAxis(Math.toRadians(30), 0, 1, 0));
                assertDryColumn(h, level, f.ship(), rt, top, bottom, shaft + " open, yawed 30°");
            }
            SableShips.remove(f.ship());
        }));
    }

    /** HV2, the human's case: a two-ladder shaft under the deck hatch with the sea surface in the upper ladder cell. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300)
    public static void ladderShaftUnderADeckHatchIsDry(GameTestHelper h) {
        shaftCase(h, Shaft.TWO_LADDERS, 2, true);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300)
    public static void oneLadderShaftUnderADeckHatchIsDry(GameTestHelper h) {
        shaftCase(h, Shaft.ONE_LADDER, 1, false);
    }

    /** HV2: the hatch over a slab touches no compartment; it joins through the slab's empty half. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300)
    public static void hatchOverASlabStepIsDry(GameTestHelper h) {
        shaftCase(h, Shaft.SLAB, 1, false);
    }

    /**
     * HV2, the ship of the screenshot: the starter sloop's deck well (the air between its bulwarks, a compartment of its
     * own), its hatch, the two ladder cells on the mast and the hold must be one region. Before HV2 each compartment was
     * its own Sable region; looking down the hatch from the deck, the face between the deck well's region and the
     * hold's region ended the dry volume and the sea showed in the hold behind it.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 300)
    public static void starterSloopHatchViewIsOneDryRegion(GameTestHelper h) {
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 48; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 47 || z == 0 || z == 47;
                for (int y = 2; y <= 8; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : y <= 7 ? Blocks.WATER : Blocks.AIR);
                }
                for (int y = 12; y <= 15; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (h.getBlockState(p).is(Blocks.BARRIER)) {
                        h.setBlock(p, Blocks.AIR);
                    }
                }
            }
        }
        ServerLevel level = h.getLevel();
        ShipTemplatePlacer.Result r = ShipTemplatePlacer.place(level, ShipTemplates.STARTER_SLOOP_ID,
                h.absolutePos(new BlockPos(24, 8, 47)), Direction.NORTH, true, true, null);
        if (r.assembly() == null || r.assembly().shipId() == null) {
            throw new GameTestAssertException("sloop not placed and assembled: " + r.outcome());
        }
        UUID id = r.assembly().shipId();
        ShipTestCleanup.track(h, id);
        ShipBody ship = SableShips.byId(level, id);
        h.assertTrue(ship != null, "no sloop body");
        BlockPos hatch = ship.plotBlocks().stream().filter(p -> level.getBlockState(p).is(Blocks.SPRUCE_TRAPDOOR)).findFirst()
                .orElseThrow(() -> new GameTestAssertException("the sloop has no hatch"));
        boolean[] opened = {false};
        h.runAfterDelay(20, () -> h.succeedWhen(() -> {
            HullRuntime rt = HullRuntimes.get(level, id);
            h.assertTrue(rt != null && rt.seesSea(), "the sloop is not afloat yet");
            var an = rt.simulation().analysis();
            var g = an.grid();
            BlockPos well = hatch.above();
            h.assertTrue(an.compartments().size() >= 2 && an.compartmentAt(well.getX() - g.originX(), well.getY() - g.originY(),
                    well.getZ() - g.originZ()) >= 0, "the deck above the hatch is no compartment of its own any more ("
                    + an.compartments().size() + " compartments): the test no longer covers two compartments");
            if (!opened[0]) {
                assertDryColumn(h, level, ship, rt, well, hatch.below(2), "sloop, hatch closed");
                opened[0] = true;
                level.setBlock(hatch, level.getBlockState(hatch).setValue(TrapDoorBlock.OPEN, true), Block.UPDATE_ALL);
                throw new GameTestAssertException("waiting for the open hatch");
            }
            assertDryColumn(h, level, ship, rt, well, hatch.below(2), "sloop, hatch open");
            SableShips.remove(ship);
        }));
    }
}

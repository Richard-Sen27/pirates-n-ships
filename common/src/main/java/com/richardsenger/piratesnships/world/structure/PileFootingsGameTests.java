package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * PIER1 on a built deep sea (floor {@link #WATER} blocks under the surface): every pier, jetty and quay piece
 * (and each extension section) is placed with its footing row {@link #GAP} or fewer blocks above the floor.
 *
 * <ul>
 *     <li>{@link #pilesReachTheFloor}: after {@link PileFootings}, every footing column stands on the floor with no
 *     water in the column below the template's lowest row, the footing is the bottom block and every block above it is
 *     solid up to the template's old row.</li>
 *     <li>{@link #quayStandsOnPillars}: the navy quay and its extension have open water under the deck, between the
 *     pillars and under the middle, down to the floor.</li>
 *     <li>{@link #shallowSeaLeavesTheTemplates}: where the floor is the row under the footings the pass changes
 *     nothing.</li>
 * </ul>
 */
public final class PileFootingsGameTests {

    static final int FLOOR_Y = 1;
    /** Water blocks over the floor. */
    static final int WATER = 8;
    static final int SURFACE_Y = FLOOR_Y + WATER;
    static final int SIZE = 40;
    static final List<String> TEMPLATES = List.of("village/pier", "village/pier_extension", "pirate_island/jetty",
            "pirate_island/jetty_extension", "navy_outpost/quay", "navy_outpost/quay_extension");

    private PileFootingsGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(PileFootingsGameTests.class);
    }

    /** The absolute y of the test-relative row {@code y}. */
    static int floorY(GameTestHelper helper, int y) {
        return helper.absolutePos(new BlockPos(0, y, 0)).getY();
    }

    /** The template's waterline row (deck row minus one): 4 for the pier, jetty and quay, 6 for the extensions. */
    static int seaRow(String name) {
        return name.endsWith("_extension") ? 6 : 4;
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 400, batch = "pirates_n_ships_pile_footings")
    public static void pilesReachTheFloor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureTemplateManager manager = level.getStructureManager();
        int floorY = floorY(helper, FLOOR_Y);
        int piles = 0;
        for (String name : TEMPLATES) {
            buildSea(helper, FLOOR_Y);
            Placed placed = place(helper, manager, name);
            List<BlockPos> footings = footings(level, placed.box);
            helper.assertTrue(footings.size() >= 6, name + ": footings in the template (" + footings.size() + ")");
            int before = waterBelow(level, footings, floorY);
            helper.assertTrue(before > 0, name + ": the footings hang in the water before the pass (" + before + ")");
            int changed = PileFootings.extend(level, wholeColumn(level, placed.box), List.of(placed.piece));
            helper.assertTrue(changed > 0, name + ": the pass placed blocks");
            for (BlockPos foot : footings) {
                piles++;
                BlockState pile = level.getBlockState(foot);
                helper.assertFalse(pile.is(Blocks.WATER) || pile.isAir() || pile.is(Blocks.COBBLESTONE) || pile.is(Blocks.MOSSY_COBBLESTONE),
                        name + ": " + foot + " is a pile block, not " + pile);
                for (int y = foot.getY(); y > floorY; y--) {
                    BlockState state = level.getBlockState(new BlockPos(foot.getX(), y, foot.getZ()));
                    helper.assertTrue(!state.getFluidState().is(net.minecraft.tags.FluidTags.WATER) && !state.isAir(),
                            name + ": water or air at " + foot.getX() + " " + y + " " + foot.getZ() + " in a pile column: " + state);
                }
                BlockState bottom = level.getBlockState(new BlockPos(foot.getX(), floorY + 1, foot.getZ()));
                helper.assertTrue(bottom.is(Blocks.COBBLESTONE) || bottom.is(Blocks.MOSSY_COBBLESTONE),
                        name + ": the footing rests on the floor at " + foot.getX() + " " + foot.getZ() + ": " + bottom);
            }
        }
        Constants.LOG.info("PIER1: {} pile columns of {} pieces reach the floor", piles, TEMPLATES.size());
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 400, batch = "pirates_n_ships_pile_footings")
    public static void quayStandsOnPillars(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureTemplateManager manager = level.getStructureManager();
        int floorY = floorY(helper, FLOOR_Y);
        for (String name : List.of("navy_outpost/quay", "navy_outpost/quay_extension")) {
            buildSea(helper, FLOOR_Y);
            Placed placed = place(helper, manager, name);
            PileFootings.extend(level, wholeColumn(level, placed.box), List.of(placed.piece));
            BoundingBox box = placed.box;
            int sea = box.minY() + seaRow(name);
            // the middle column: water from the floor to the waterline, the whole length
            int openMiddle = 0;
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = floorY + 1; y <= sea; y++) {
                    BlockPos p = new BlockPos(box.minX() + 3, y, z);
                    helper.assertTrue(level.getBlockState(p).is(Blocks.WATER), name + ": open water under the deck's middle at " + p
                            + ": " + level.getBlockState(p));
                    openMiddle++;
                }
            }
            // an edge column between two pillars: water from the floor to just under the edge course
            int openEdge = 0;
            for (int x : new int[] {box.minX() + 1, box.minX() + 5}) {
                for (int z : new int[] {box.minZ() + 3, box.minZ() + 7}) {
                    for (int y = floorY + 1; y < sea; y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        helper.assertTrue(level.getBlockState(p).is(Blocks.WATER), name + ": open water between the pillars at " + p
                                + ": " + level.getBlockState(p));
                        openEdge++;
                    }
                }
            }
            // and the pillars are there: stone from the floor to the edge course at both edges
            for (int x : new int[] {box.minX() + 1, box.minX() + 5}) {
                for (int y = floorY + 1; y <= sea; y++) {
                    BlockPos p = new BlockPos(x, y, box.minZ() + 1);
                    helper.assertFalse(level.getBlockState(p).is(Blocks.WATER) || level.getBlockState(p).isAir(),
                            name + ": a pillar at " + p + ": " + level.getBlockState(p));
                }
            }
            helper.assertTrue(openMiddle > 50 && openEdge > 20, name + ": cells checked " + openMiddle + " " + openEdge);
            Constants.LOG.info("PIER1: {} open water cells under the middle, {} between the pillars of {}", openMiddle, openEdge, name);
            // one block of deck: nothing solid right under it in the middle
            BlockPos deck = new BlockPos(box.minX() + 3, box.minY() + seaRow(name) + 1, box.minZ() + 3);
            helper.assertFalse(level.getBlockState(deck).isAir(), name + ": the deck row at " + deck);
        }
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 400, batch = "pirates_n_ships_pile_footings")
    public static void shallowSeaLeavesTheTemplates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureTemplateManager manager = level.getStructureManager();
        for (String name : TEMPLATES) {
            // the floor is the row under the footing row (the template's own seabed)
            buildSea(helper, FLOOR_Y + 4);
            Placed placed = place(helper, manager, name);
            BoundingBox box = placed.box;
            List<BlockState> before = new ArrayList<>();
            BlockPos.betweenClosed(box.minX(), box.minY() - 2, box.minZ(), box.maxX(), box.maxY(), box.maxZ())
                    .forEach(p -> before.add(level.getBlockState(p)));
            int changed = PileFootings.extend(level, wholeColumn(level, box), List.of(placed.piece));
            helper.assertValueEqual(changed, 0, name + ": blocks changed on a shallow sea");
            List<BlockState> after = new ArrayList<>();
            BlockPos.betweenClosed(box.minX(), box.minY() - 2, box.minZ(), box.maxX(), box.maxY(), box.maxZ())
                    .forEach(p -> after.add(level.getBlockState(p)));
            helper.assertTrue(before.equals(after), name + ": the template stands as it was");
        }
        helper.succeed();
    }

    // --- fixture ------------------------------------------------------------------------------------------------

    record Placed(PoolElementStructurePiece piece, BoundingBox box) {
    }

    /** Rock below {@code floorY}, one sand row at it, water up to {@link #SURFACE_Y}, air above, over the test area. */
    static void buildSea(GameTestHelper helper, int floorY) {
        ServerLevel level = helper.getLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -2; x < SIZE; x++) {
            for (int z = -2; z < SIZE; z++) {
                for (int y = 1; y <= SURFACE_Y + 14; y++) {
                    BlockState state = y <= floorY ? stone : y <= SURFACE_Y ? water : air;
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), state, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }
    }

    /** The template {@code name} (group/piece) placed with its waterline row at the surface. */
    static Placed place(GameTestHelper helper, StructureTemplateManager manager, String name) {
        ServerLevel level = helper.getLevel();
        ResourceLocation id = Constants.id(name);
        StructureTemplate template = manager.get(id).orElseThrow(() -> new AssertionError("no template " + id));
        BlockPos origin = helper.absolutePos(new BlockPos(4, SURFACE_Y - seaRow(name), 4));
        StructurePlaceSettings settings = new StructurePlaceSettings();
        BoundingBox box = template.getBoundingBox(settings, origin);
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        PoolElementStructurePiece piece = new PoolElementStructurePiece(manager,
                StructurePoolElement.single(id.toString()).apply(StructureTemplatePool.Projection.RIGID), origin, 0, Rotation.NONE, box,
                LiquidSettings.APPLY_WATERLOGGING);
        return new Placed(piece, box);
    }

    /** The footing columns of the placed piece: cobblestone on its lowest row. */
    static List<BlockPos> footings(ServerLevel level, BoundingBox box) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                BlockPos p = new BlockPos(x, box.minY(), z);
                if (PileFootings.isFooting(level.getBlockState(p))) out.add(p);
            }
        }
        return out;
    }

    /** How many water cells lie between the footings and the floor. */
    static int waterBelow(ServerLevel level, List<BlockPos> footings, int floorY) {
        int n = 0;
        for (BlockPos foot : footings) {
            for (int y = foot.getY() - 1; y > floorY; y--) {
                if (level.getBlockState(new BlockPos(foot.getX(), y, foot.getZ())).is(Blocks.WATER)) n++;
            }
        }
        return n;
    }

    private static BoundingBox wholeColumn(ServerLevel level, BoundingBox box) {
        return new BoundingBox(box.minX() - 1, level.getMinBuildHeight(), box.minZ() - 1, box.maxX() + 1, level.getMaxBuildHeight(), box.maxZ() + 1);
    }
}

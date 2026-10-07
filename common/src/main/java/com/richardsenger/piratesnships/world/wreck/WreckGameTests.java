package com.richardsenger.piratesnships.world.wreck;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.world.WorldConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Wrecks on a hand-built ocean floor (WK1): stone with a sand top inside a 48×48 test area, water above it up to a
 * chosen sea surface. The tests run the structure's own planning (or its stub for a chosen piece) at the area's centre
 * with the floor height and sea surface of the hand-built sea, then place the pieces through
 * {@link StructureStart#placeInChunk}, as world generation and {@code /place structure} do.
 */
public final class WreckGameTests {

    private static final int SIZE = 48;
    /** Top row of the floor (sand) in the deep fixture; the seabed row of a wreck replaces it. */
    private static final int FLOOR_TOP = 2;
    /** Sea surface (topmost water block) of the deep fixture: 13 blocks of water above the floor, enough for every piece. */
    private static final int DEEP_SEA = 15;

    private WreckGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(WreckGameTests.class);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48)
    public static void cargoFieldSitsOnTheFloor(GameTestHelper helper) {
        pieceSitsOnTheFloorAndSucceeds(helper, WreckKeys.CARGO_FIELD, Rotation.NONE);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48)
    public static void mastStumpSitsOnTheFloor(GameTestHelper helper) {
        pieceSitsOnTheFloorAndSucceeds(helper, WreckKeys.MAST_STUMP, Rotation.CLOCKWISE_90);
    }

    /** Also: the stern's fences and window panes are connected (the templates store them as posts). */
    @ModGameTest(template = GameTestTemplates.EMPTY_48)
    public static void sternSitsOnTheFloor(GameTestHelper helper) {
        WreckPiece piece = pieceSitsOnTheFloor(helper, WreckKeys.STERN, Rotation.CLOCKWISE_180);
        ServerLevel level = helper.getLevel();
        BoundingBox box = piece.getBoundingBox();
        int connected = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof CrossCollisionBlock)) continue;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir))) connected++;
            }
        }
        helper.assertTrue(connected > 0, "the stern's fences and panes connect to something");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48)
    public static void sunkenSloopSitsOnTheFloor(GameTestHelper helper) {
        pieceSitsOnTheFloorAndSucceeds(helper, WreckKeys.SUNKEN_SLOOP, Rotation.COUNTERCLOCKWISE_90);
    }

    /** The structure's own planning in deep water: some piece, on the floor, fully under water. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48)
    public static void plannedWreckSitsOnTheFloor(GameTestHelper helper) {
        buildSea(helper, FLOOR_TOP, DEEP_SEA);
        ServerLevel level = helper.getLevel();
        WreckStructure structure = structure(level);
        BlockPos centre = centre(helper);
        Optional<Structure.GenerationStub> stub = structure.plan(level.getStructureManager(), centre.getX(), centre.getZ(),
                floorHeight(helper, FLOOR_TOP), seaSurface(helper, DEEP_SEA), RandomSource.create(42L));
        helper.assertTrue(stub.isPresent(), "deep water gives a generation point");
        WreckPiece piece = placed(helper, stub.get());
        checkPlaced(helper, piece, DEEP_SEA);
        helper.succeed();
    }

    /**
     * Five blocks of water above the seabed row: only the cargo field (4) fits, whatever the random; three blocks:
     * nothing fits and there is no wreck.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48)
    public static void shallowWaterOnlyFitsTheCargoField(GameTestHelper helper) {
        int sea = FLOOR_TOP + 5;
        buildSea(helper, FLOOR_TOP, sea);
        ServerLevel level = helper.getLevel();
        WreckStructure structure = structure(level);
        BlockPos centre = centre(helper);
        int floor = floorHeight(helper, FLOOR_TOP);
        for (long seed = 0; seed < 32; seed++) {
            Optional<Structure.GenerationStub> stub = structure.plan(level.getStructureManager(), centre.getX(), centre.getZ(),
                    floor, seaSurface(helper, sea), RandomSource.create(seed));
            helper.assertTrue(stub.isPresent(), "five blocks of water fit the cargo field (seed " + seed + ")");
            List<StructurePiece> pieces = stub.get().getPiecesBuilder().build().pieces();
            helper.assertValueEqual(((WreckPiece) pieces.get(0)).templateId(), WreckKeys.CARGO_FIELD, "piece (seed " + seed + ")");
        }
        WreckPiece piece = placed(helper, structure.plan(level.getStructureManager(), centre.getX(), centre.getZ(), floor,
                seaSurface(helper, sea), RandomSource.create(7L)).orElseThrow());
        checkPlaced(helper, piece, sea);

        for (long seed = 0; seed < 8; seed++) {
            helper.assertTrue(structure.plan(level.getStructureManager(), centre.getX(), centre.getZ(), floor,
                    seaSurface(helper, FLOOR_TOP + 3), RandomSource.create(seed)).isEmpty(), "three blocks of water fit nothing");
        }
        helper.succeed();
    }

    /** With wrecks toggled off, deep water gives no generation point. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, batch = "pirates_n_ships_config_world_wreck")
    public static void disabledWrecksHaveNoGenerationPoint(GameTestHelper helper) {
        ConfigOverrides.during(helper, WorldConfig.WRECK_ENABLED, false);
        buildSea(helper, FLOOR_TOP, DEEP_SEA);
        ServerLevel level = helper.getLevel();
        WreckStructure structure = structure(level);
        BlockPos centre = centre(helper);
        for (long seed = 0; seed < 8; seed++) {
            helper.assertTrue(structure.plan(level.getStructureManager(), centre.getX(), centre.getZ(), floorHeight(helper, FLOOR_TOP),
                    seaSurface(helper, DEEP_SEA), RandomSource.create(seed)).isEmpty(), "plan while disabled");
        }
        helper.assertTrue(structure.findValidGenerationPoint(context(level, centre)).isEmpty(), "generation point while disabled");
        helper.succeed();
    }

    /** The loaded structure has the four pieces, each a committed, non-empty template. */
    @ModGameTest
    public static void everyPieceResolvesToATemplate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WreckStructure structure = structure(level);
        helper.assertValueEqual(structure.pieces().stream().map(WreckPieceEntry::template).toList(),
                List.of(WreckKeys.CARGO_FIELD, WreckKeys.MAST_STUMP, WreckKeys.STERN, WreckKeys.SUNKEN_SLOOP), "pieces");
        for (WreckPieceEntry piece : structure.pieces()) {
            Optional<StructureTemplate> template = level.getStructureManager().get(piece.template());
            helper.assertTrue(template.isPresent(), piece.template() + " is a committed template");
            var size = template.get().getSize();
            helper.assertTrue(size.getX() > 0 && size.getY() > 0 && size.getZ() > 0, piece.template() + " is not empty");
            helper.assertTrue(size.getY() <= piece.waterAbove(), piece.template() + ": water cover " + piece.waterAbove()
                    + " keeps its top row (height " + size.getY() + ") under the surface");
        }
        helper.succeed();
    }

    // --- Placement and checks ----------------------------------------------------------------------------------

    private static void pieceSitsOnTheFloorAndSucceeds(GameTestHelper helper, ResourceLocation template, Rotation rotation) {
        pieceSitsOnTheFloor(helper, template, rotation);
        helper.succeed();
    }

    private static WreckPiece pieceSitsOnTheFloor(GameTestHelper helper, ResourceLocation template, Rotation rotation) {
        buildSea(helper, FLOOR_TOP, DEEP_SEA);
        ServerLevel level = helper.getLevel();
        WreckStructure structure = structure(level);
        BlockPos centre = centre(helper);
        int seabed = WreckPlan.seabedY(floorHeight(helper, FLOOR_TOP), structure.depthBelowFloor());
        WreckPiece piece = placed(helper, WreckStructure.stub(level.getStructureManager(), template, rotation,
                new BlockPos(centre.getX(), seabed, centre.getZ())));
        helper.assertValueEqual(piece.templateId(), template, "template");
        helper.assertValueEqual(piece.getRotation(), rotation, "rotation");
        checkPlaced(helper, piece, DEEP_SEA);
        return piece;
    }

    /**
     * The seabed row replaces the floor's top block; the piece lies inside the test area and under the sea surface;
     * there is no air in its box; every block with a {@code waterlogged} property is waterlogged and there are as many
     * as the template holds; every chest the template holds carries the wreck loot table.
     */
    private static void checkPlaced(GameTestHelper helper, WreckPiece piece, int sea) {
        ServerLevel level = helper.getLevel();
        BoundingBox box = piece.getBoundingBox();
        helper.assertValueEqual(box.minY(), helper.absolutePos(new BlockPos(0, FLOOR_TOP, 0)).getY(), "seabed row on the floor's top row");
        BlockPos a = helper.absolutePos(BlockPos.ZERO);
        BlockPos b = helper.absolutePos(new BlockPos(SIZE - 1, 0, SIZE - 1));
        helper.assertTrue(box.minX() >= Math.min(a.getX(), b.getX()) && box.maxX() <= Math.max(a.getX(), b.getX())
                && box.minZ() >= Math.min(a.getZ(), b.getZ()) && box.maxZ() <= Math.max(a.getZ(), b.getZ()), "piece inside the test area: " + box);
        int seaY = seaSurface(helper, sea);
        helper.assertTrue(box.maxY() < seaY, "top row " + box.maxY() + " below the sea surface " + seaY);

        int waterlogged = 0;
        int chests = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState state = level.getBlockState(pos);
            helper.assertTrue(!state.isAir(), "no air pocket at " + pos.toShortString());
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                helper.assertTrue(state.getValue(BlockStateProperties.WATERLOGGED), state + " at " + pos.toShortString() + " is waterlogged");
                waterlogged++;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RandomizableContainer container && container.getLootTable() != null) {
                helper.assertValueEqual(container.getLootTable(), WreckLoot.WRECK, "loot table at " + pos.toShortString());
                chests++;
            }
        }
        CompoundTag nbt = piece.template().save(new CompoundTag());
        ListTag palette = nbt.getList("palette", Tag.TAG_COMPOUND);
        int expectedWaterlogged = 0;
        int expectedChests = 0;
        List<String> lost = new ArrayList<>();
        for (Tag t : nbt.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) t;
            CompoundTag state = palette.getCompound(block.getInt("state"));
            if (block.getCompound("nbt").contains("LootTable")) expectedChests++;
            boolean wet = "true".equals(state.getCompound("Properties").getString("waterlogged"));
            if (wet) expectedWaterlogged++;
            ListTag p = block.getList("pos", Tag.TAG_INT);
            BlockPos world = piece.templatePosition().offset(StructureTemplate.calculateRelativePosition(piece.placeSettings(),
                    new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2))));
            BlockState placed = level.getBlockState(world);
            boolean same = BuiltInRegistries.BLOCK.getKey(placed.getBlock()).toString().equals(state.getString("Name"));
            if (!same || wet && !(placed.hasProperty(BlockStateProperties.WATERLOGGED) && placed.getValue(BlockStateProperties.WATERLOGGED))) {
                lost.add(state.getString("Name") + (wet ? "[waterlogged]" : "") + " at " + world.toShortString() + " is " + placed);
            }
        }
        // every block of the template is in place (none dropped at a chunk border), waterlogged where the template says so
        helper.assertTrue(lost.isEmpty(), piece.templateId() + ": template blocks missing or dry in place: " + lost);
        helper.assertTrue(expectedWaterlogged > 0 && expectedChests > 0, piece.templateId() + " has waterlogged blocks and a chest");
        helper.assertTrue(lost.isEmpty(), piece.templateId() + ": waterlogged template blocks not waterlogged in place: " + lost);
        // blocks the template stores without the property (some mod blocks) take the sea's water as well
        helper.assertTrue(waterlogged >= expectedWaterlogged, "waterlogged blocks of " + piece.templateId() + ": " + waterlogged
                + " < " + expectedWaterlogged);
        helper.assertValueEqual(chests, expectedChests, "chests with the wreck loot table in " + piece.templateId());
        helper.assertTrue(level.getBlockState(new BlockPos(box.getCenter().getX(), box.maxY() + 1, box.getCenter().getZ())).is(Blocks.WATER),
                "water above the piece");
    }

    /** Places every chunk of the stub's structure start, as {@code /place structure} does; returns its one piece. */
    private static WreckPiece placed(GameTestHelper helper, Structure.GenerationStub stub) {
        ServerLevel level = helper.getLevel();
        List<StructurePiece> pieces = stub.getPiecesBuilder().build().pieces();
        helper.assertValueEqual(pieces.size(), 1, "a wreck is one piece");
        StructureStart start = new StructureStart(structure(level), new ChunkPos(stub.position()), 0,
                new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(pieces));
        BoundingBox box = start.getBoundingBox();
        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
        ChunkPos.rangeClosed(min, max).forEach(chunk -> start.placeInChunk(level, level.structureManager(),
                level.getChunkSource().getGenerator(), level.getRandom(),
                new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(), level.getMaxBuildHeight(), chunk.getMaxBlockZ()), chunk));
        return (WreckPiece) pieces.get(0);
    }

    // --- Fixture ------------------------------------------------------------------------------------------------

    /** Stone up to {@code floorTop - 1}, sand at {@code floorTop}, water up to {@code sea}, air above. */
    private static void buildSea(GameTestHelper helper, int floorTop, int sea) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = 0; y < 16; y++) {
                    BlockState state = y < floorTop ? Blocks.STONE.defaultBlockState()
                            : y == floorTop ? Blocks.SAND.defaultBlockState()
                            : y <= sea ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    helper.setBlock(new BlockPos(x, y, z), state);
                }
            }
        }
    }

    /**
     * The area's centre moved to the nearest chunk corner, so every piece spans four chunks and is placed one chunk at
     * a time across both borders, as in world generation.
     */
    private static BlockPos centre(GameTestHelper helper) {
        BlockPos middle = helper.absolutePos(new BlockPos(SIZE / 2, 0, SIZE / 2));
        return new BlockPos(Math.round(middle.getX() / 16f) * 16, middle.getY(), Math.round(middle.getZ() / 16f) * 16);
    }

    /** The {@code OCEAN_FLOOR_WG} height of the fixture: the first free block above the floor's top row. */
    private static int floorHeight(GameTestHelper helper, int floorTop) {
        return helper.absolutePos(new BlockPos(0, floorTop + 1, 0)).getY();
    }

    private static int seaSurface(GameTestHelper helper, int sea) {
        return helper.absolutePos(new BlockPos(0, sea, 0)).getY();
    }

    private static WreckStructure structure(ServerLevel level) {
        Structure s = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(WreckKeys.WRECK);
        if (!(s instanceof WreckStructure wreck)) throw new IllegalStateException("wreck is not loaded: " + s);
        return wreck;
    }

    private static Structure.GenerationContext context(ServerLevel level, BlockPos site) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        return new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), new ChunkPos(site), level, b -> true);
    }
}

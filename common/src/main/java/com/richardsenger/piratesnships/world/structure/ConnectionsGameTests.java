package com.richardsenger.piratesnships.world.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Fences, panes, iron bars and walls of the port pieces connect to their neighbours in the piece (WG4). The schematic
 * lab's templates store them without side properties, and jigsaw placement ({@code knownShape}) never runs the
 * neighbour-shape pass, so without {@link ConnectionsProcessor} they would stand as unconnected posts.
 *
 * <p>Each test takes a piece from its real template pool, places it chunk by chunk (as world generation does) into a
 * cleared 48×48 area, and compares every connecting block's sides with what vanilla's own {@code updateShape} computes
 * from the placed neighbours. Sides whose neighbour lies outside the piece are not compared (they keep the template's
 * value).
 */
public final class ConnectionsGameTests {

    private ConnectionsGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(ConnectionsGameTests.class);
    }

    /** The village pier's rail, turned a quarter (the processor works in the template's frame, placement turns it). */
    @ModGameTest(template = GameTestTemplates.EMPTY_48)
    public static void villagePierRailConnects(GameTestHelper helper) {
        assertConnected(helper, "village/pier", Rotation.CLOCKWISE_90, 4);
    }

    /** The tavern's windows (glass panes) and fences. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48)
    public static void villageTavernPanesConnect(GameTestHelper helper) {
        assertConnected(helper, "village/tavern", Rotation.NONE, 10);
    }

    /** The pirate island's tavern hut, the island piece with the most fences. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48)
    public static void islandTavernHutFencesConnect(GameTestHelper helper) {
        assertConnected(helper, "pirate_island/tavern_hut", Rotation.COUNTERCLOCKWISE_90, 4);
    }

    /** The fort's brig: iron bars and brig bars. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48)
    public static void fortBrigBarsConnect(GameTestHelper helper) {
        assertConnected(helper, "navy_outpost/brig", Rotation.CLOCKWISE_180, 4);
    }

    /** The fort's quay: stone brick walls, fences and iron bars. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48)
    public static void fortQuayWallsConnect(GameTestHelper helper) {
        assertConnected(helper, "navy_outpost/quay", Rotation.NONE, 2);
    }

    /** Every single-pool element of our pools carries the connections processor list. */
    @ModGameTest
    public static void everyPortPoolElementConnects(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int elements = 0;
        for (var entry : level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).entrySet()) {
            if (!entry.getKey().location().getNamespace().equals(Constants.MOD_ID)) continue;
            for (StructurePoolElement element : entry.getValue().getShuffledTemplates(RandomSource.create(0L))) {
                JsonObject json = encode(level, element);
                if (json == null || !json.has("location")) continue;
                String processors = json.get("processors").getAsString();
                helper.assertValueEqual(processors, ConnectionsProcessor.LIST.location().toString(),
                        entry.getKey().location() + ": " + json.get("location").getAsString() + " processors");
                elements++;
            }
        }
        helper.assertTrue(elements > 0, "port pools loaded");
        helper.succeed();
    }

    // --- Helpers ----------------------------------------------------------------------------------------------

    private static void assertConnected(GameTestHelper helper, String template, Rotation rotation, int minConnected) {
        ServerLevel level = helper.getLevel();
        StructurePoolElement element = element(level, Constants.id(template).toString());
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        BoundingBox piece = element.getBoundingBox(level.getStructureManager(), origin, rotation);
        // the piece's box may lie anywhere around origin after rotation: move it so its min corner is at origin
        BlockPos offset = origin.offset(origin.getX() - piece.minX(), 0, origin.getZ() - piece.minZ());
        piece = element.getBoundingBox(level.getStructureManager(), offset, rotation);

        BoundingBox area = piece;
        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(area.minX()), SectionPos.blockToSectionCoord(area.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(area.maxX()), SectionPos.blockToSectionCoord(area.maxZ()));
        BlockPos at = offset;
        ChunkPos.rangeClosed(min, max).forEach(chunk -> element.place(level.getStructureManager(), level, level.structureManager(),
                level.getChunkSource().getGenerator(), at, at, rotation,
                new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(), level.getMaxBuildHeight(), chunk.getMaxBlockZ()),
                RandomSource.create(0L), LiquidSettings.APPLY_WATERLOGGING, false));

        List<String> wrong = new ArrayList<>();
        int connecting = 0;
        int connected = 0;
        for (BlockPos pos : BlockPos.betweenClosed(piece.minX(), piece.minY(), piece.minZ(), piece.maxX(), piece.maxY(), piece.maxZ())) {
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            if (!(block instanceof FenceBlock) && !(block instanceof IronBarsBlock) && !(block instanceof WallBlock)) continue;
            connecting++;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                Property<?> side = side(block, dir);
                if (isConnected(state, side)) connected++;
                BlockPos neighbour = pos.relative(dir);
                if (!piece.isInside(neighbour)) continue;
                BlockState expected = state.updateShape(dir, level.getBlockState(neighbour), level, pos.immutable(), neighbour);
                if (!expected.getValue(side).equals(state.getValue(side))) {
                    wrong.add(helper.relativePos(pos) + " " + state + " " + dir + " expected " + expected.getValue(side));
                }
            }
            if (block instanceof WallBlock && piece.isInside(pos.above())) {
                BlockState expected = state.updateShape(Direction.UP, level.getBlockState(pos.above()), level, pos.immutable(), pos.above());
                if (!expected.getValue(WallBlock.UP).equals(state.getValue(WallBlock.UP))) {
                    wrong.add(helper.relativePos(pos) + " " + state + " up expected " + expected.getValue(WallBlock.UP));
                }
            }
        }
        helper.assertTrue(connecting > 0, template + " has fences, panes, bars or walls");
        helper.assertTrue(wrong.isEmpty(), template + ": " + wrong.size() + " sides differ from vanilla's, first " + wrong.stream().limit(4).toList());
        helper.assertTrue(connected >= minConnected, template + ": only " + connected + " connected sides");
        helper.succeed();
    }

    private static Property<?> side(Block block, Direction dir) {
        if (block instanceof WallBlock) {
            return switch (dir) {
                case NORTH -> WallBlock.NORTH_WALL;
                case EAST -> WallBlock.EAST_WALL;
                case SOUTH -> WallBlock.SOUTH_WALL;
                default -> WallBlock.WEST_WALL;
            };
        }
        return PipeBlock.PROPERTY_BY_DIRECTION.get(dir);
    }

    private static boolean isConnected(BlockState state, Property<?> side) {
        Object value = state.getValue(side);
        return value instanceof Boolean b ? b : !value.toString().equals("none");
    }

    /**
     * The element of our pools whose template is {@code location}. A template whose pool is not registered yet (the
     * fort's pieces before WG3 lands) is wrapped in a rigid element with the connections list, as the pool will be.
     */
    private static StructurePoolElement element(ServerLevel level, String location) {
        for (var entry : level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).entrySet()) {
            if (!entry.getKey().location().getNamespace().equals(Constants.MOD_ID)) continue;
            for (StructurePoolElement element : entry.getValue().getShuffledTemplates(RandomSource.create(0L))) {
                JsonObject json = encode(level, element);
                if (json != null && json.has("location") && json.get("location").getAsString().equals(location)) return element;
            }
        }
        Optional<Holder.Reference<StructureProcessorList>> list = level.registryAccess()
                .registryOrThrow(Registries.PROCESSOR_LIST).getHolder(ConnectionsProcessor.LIST);
        if (list.isEmpty()) throw new IllegalStateException("processor list " + ConnectionsProcessor.LIST.location() + " is not loaded");
        return StructurePoolElement.single(location, list.get()).apply(StructureTemplatePool.Projection.RIGID);
    }

    private static JsonObject encode(ServerLevel level, StructurePoolElement element) {
        Optional<JsonElement> json = StructurePoolElement.CODEC
                .encodeStart(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), element).result();
        return json.filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).orElse(null);
    }
}

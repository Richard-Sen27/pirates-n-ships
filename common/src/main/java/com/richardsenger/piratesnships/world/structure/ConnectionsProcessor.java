package com.richardsenger.piratesnships.world.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.law.content.BrigBarsBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Connects the fences, panes, iron bars and walls of a template to their neighbours inside the template (WK1, WG4);
 * processor type {@code pirates_n_ships:connections}, processor list {@link #LIST}.
 *
 * <p>The schematic lab's templates store these blocks without their side properties (posts). Vanilla connects them
 * only through the neighbour-shape pass after placement, which jigsaw pieces ({@code SinglePoolElement} sets
 * {@code knownShape}) and wrecks ({@code WreckPiece}) skip: world generation places a piece one chunk at a time, so
 * that pass would see the neighbour chunk's half of the piece still missing. This processor computes the connections
 * once from the whole template instead ({@link StructureProcessor#finalizeProcessing} gets every block of the template
 * on every chunk's call), in the template's own frame: the states it sees are not rotated yet (placement mirrors and
 * rotates them afterwards), so it pairs each processed state with its template position. The neighbours are the
 * processed states (a jigsaw block counts as its final state). A side whose neighbour is not in the template (outside
 * it, a structure void or a block an earlier processor dropped) keeps the template's value: the processor never
 * connects to the terrain or to other pieces.
 */
public final class ConnectionsProcessor extends StructureProcessor {

    public static final ConnectionsProcessor INSTANCE = new ConnectionsProcessor();
    public static final MapCodec<ConnectionsProcessor> CODEC = MapCodec.unit(() -> INSTANCE);
    /** The processor list {@code pirates_n_ships:connections} that every port pool element names (datagen). */
    public static final ResourceKey<StructureProcessorList> LIST = ResourceKey.create(Registries.PROCESSOR_LIST, Constants.id("connections"));

    private static final VoxelShape POST_TEST = Block.box(7.0, 0.0, 7.0, 9.0, 16.0, 9.0);
    private static final VoxelShape NORTH_TEST = Block.box(7.0, 0.0, 0.0, 9.0, 16.0, 9.0);
    private static final VoxelShape SOUTH_TEST = Block.box(7.0, 0.0, 7.0, 9.0, 16.0, 16.0);
    private static final VoxelShape WEST_TEST = Block.box(0.0, 0.0, 7.0, 9.0, 16.0, 9.0);
    private static final VoxelShape EAST_TEST = Block.box(7.0, 0.0, 7.0, 16.0, 16.0, 9.0);

    private ConnectionsProcessor() {
    }

    @Override
    public List<StructureBlockInfo> finalizeProcessing(ServerLevelAccessor level, BlockPos offset, BlockPos pos,
                                                       List<StructureBlockInfo> originalBlockInfos,
                                                       List<StructureBlockInfo> processedBlockInfos, StructurePlaceSettings settings) {
        // vanilla keeps both lists aligned: entry i of the processed list is entry i of the template, moved into the world
        if (originalBlockInfos.size() != processedBlockInfos.size()) return processedBlockInfos;
        Map<BlockPos, BlockState> template = new HashMap<>();
        for (int i = 0; i < originalBlockInfos.size(); i++) template.put(originalBlockInfos.get(i).pos(), processedBlockInfos.get(i).state());
        TemplateGetter getter = new TemplateGetter(template);
        List<StructureBlockInfo> out = new ArrayList<>(processedBlockInfos.size());
        for (int i = 0; i < processedBlockInfos.size(); i++) {
            StructureBlockInfo info = processedBlockInfos.get(i);
            BlockState connected = connect(getter, originalBlockInfos.get(i).pos(), info.state());
            out.add(connected == info.state() ? info : new StructureBlockInfo(info.pos(), connected, info.nbt()));
        }
        return out;
    }

    /**
     * {@code state} at {@code pos} with its horizontal sides (and a wall's post) connected as vanilla would connect
     * them to the template's blocks; sides facing a position outside the template keep their value, other blocks are
     * returned unchanged.
     */
    public static BlockState connect(TemplateGetter template, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof WallBlock) return connectWall(template, pos, state);
        if (!(block instanceof FenceBlock) && !(block instanceof IronBarsBlock)) return state;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbourPos = pos.relative(dir);
            if (!template.contains(neighbourPos)) continue;
            BlockState neighbour = template.getBlockState(neighbourPos);
            boolean sturdy = neighbour.isFaceSturdy(template, neighbourPos, dir.getOpposite());
            boolean connects = block instanceof FenceBlock fence
                    ? fence.connectsTo(neighbour, sturdy, dir.getOpposite())
                    : ((IronBarsBlock) block).attachsTo(neighbour, sturdy)
                    // brig bars add their door rule in updateShape, which needs a level; mirror it here
                    || block instanceof BrigBarsBlock && BrigBarsBlock.connectsToDoor(neighbour);
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), connects);
        }
        return state;
    }

    /**
     * A wall as vanilla's {@code WallBlock} shapes it: a side connects to walls, iron bars and panes, fence gates in
     * line and sturdy faces; a connected side is tall when the block above covers it; the post rises unless the wall
     * runs straight through (or under a wall post or a block that covers it).
     */
    private static BlockState connectWall(TemplateGetter template, BlockPos pos, BlockState state) {
        BlockPos abovePos = pos.above();
        BlockState above = template.getBlockState(abovePos);
        VoxelShape aboveFace = above.getCollisionShape(template, abovePos).getFaceShape(Direction.DOWN);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbourPos = pos.relative(dir);
            if (!template.contains(neighbourPos)) continue;
            BlockState neighbour = template.getBlockState(neighbourPos);
            boolean sturdy = neighbour.isFaceSturdy(template, neighbourPos, dir.getOpposite());
            WallSide side = wallConnectsTo(neighbour, sturdy, dir.getOpposite())
                    ? isCovered(aboveFace, wallTest(dir)) ? WallSide.TALL : WallSide.LOW
                    : WallSide.NONE;
            state = state.setValue(wallSide(dir), side);
        }
        return state.setValue(WallBlock.UP, raisesPost(state, above, aboveFace));
    }

    private static boolean wallConnectsTo(BlockState neighbour, boolean sturdy, Direction towardsWall) {
        Block block = neighbour.getBlock();
        boolean gate = block instanceof FenceGateBlock && FenceGateBlock.connectsToDirection(neighbour, towardsWall);
        return neighbour.is(BlockTags.WALLS) || !Block.isExceptionForConnection(neighbour) && sturdy || block instanceof IronBarsBlock || gate;
    }

    private static boolean raisesPost(BlockState wall, BlockState above, VoxelShape aboveFace) {
        if (above.getBlock() instanceof WallBlock && above.getValue(WallBlock.UP)) return true;
        WallSide north = wall.getValue(WallBlock.NORTH_WALL);
        WallSide south = wall.getValue(WallBlock.SOUTH_WALL);
        WallSide east = wall.getValue(WallBlock.EAST_WALL);
        WallSide west = wall.getValue(WallBlock.WEST_WALL);
        boolean noNorth = north == WallSide.NONE, noSouth = south == WallSide.NONE;
        boolean noEast = east == WallSide.NONE, noWest = west == WallSide.NONE;
        // alone, an end or a corner: always a post
        if (noNorth && noSouth && noEast && noWest || noNorth != noSouth || noEast != noWest) return true;
        boolean tallLine = north == WallSide.TALL && south == WallSide.TALL || east == WallSide.TALL && west == WallSide.TALL;
        if (tallLine) return false;
        return above.is(BlockTags.WALL_POST_OVERRIDE) || isCovered(aboveFace, POST_TEST);
    }

    private static boolean isCovered(VoxelShape face, VoxelShape test) {
        return !Shapes.joinIsNotEmpty(test, face, BooleanOp.ONLY_FIRST);
    }

    private static VoxelShape wallTest(Direction dir) {
        return switch (dir) {
            case NORTH -> NORTH_TEST;
            case SOUTH -> SOUTH_TEST;
            case WEST -> WEST_TEST;
            default -> EAST_TEST;
        };
    }

    private static EnumProperty<WallSide> wallSide(Direction dir) {
        return switch (dir) {
            case NORTH -> WallBlock.NORTH_WALL;
            case SOUTH -> WallBlock.SOUTH_WALL;
            case WEST -> WallBlock.WEST_WALL;
            default -> WallBlock.EAST_WALL;
        };
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return PortStructures.CONNECTIONS.get();
    }

    /** Datagen: the processor list {@link #LIST} holding only this processor. */
    public static void gather(DataContributions data) {
        data.json(PackOutput.Target.DATA_PACK, "worldgen/processor_list", LIST.location(), () -> {
            JsonObject processor = new JsonObject();
            processor.addProperty("processor_type", Constants.id("connections").toString());
            JsonArray processors = new JsonArray();
            processors.add(processor);
            JsonObject json = new JsonObject();
            json.add("processors", processors);
            return json;
        });
    }

    /** The template's blocks (template frame) as a block getter; everything else is air and not {@link #contains}ed. */
    public record TemplateGetter(Map<BlockPos, BlockState> blocks) implements BlockGetter {

        public boolean contains(BlockPos pos) {
            return blocks.containsKey(pos);
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }
}

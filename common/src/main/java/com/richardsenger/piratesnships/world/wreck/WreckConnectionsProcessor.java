package com.richardsenger.piratesnships.world.wreck;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Connects the fences, panes and iron bars of a wreck piece to their neighbours inside the template (WK1).
 *
 * <p>The ST5 templates store these blocks without their side properties (the schematic lab writes posts). Vanilla
 * connects them only through the neighbour-shape pass after placement, which a wreck turns off
 * ({@code knownShape = true}, see {@link WreckPiece}): world generation places a piece one chunk at a time, so that
 * pass sees the neighbour chunk's half of the piece still missing and drops blocks attached across the border (a cleat
 * whose mast stands in the next chunk). This processor computes the connections once from the whole template instead,
 * in the template's own frame (placement rotates the result), so they are the same whatever the chunk borders.
 * Neighbours outside the template count as water (no connection to the terrain).
 */
public final class WreckConnectionsProcessor extends StructureProcessor {

    public static final WreckConnectionsProcessor INSTANCE = new WreckConnectionsProcessor();
    public static final MapCodec<WreckConnectionsProcessor> CODEC = MapCodec.unit(() -> INSTANCE);

    private WreckConnectionsProcessor() {
    }

    @Override
    public List<StructureBlockInfo> finalizeProcessing(ServerLevelAccessor level, BlockPos offset, BlockPos pos,
                                                       List<StructureBlockInfo> originalBlockInfos,
                                                       List<StructureBlockInfo> processedBlockInfos, StructurePlaceSettings settings) {
        // vanilla keeps both lists aligned: entry i of the processed list is entry i of the template, transformed
        if (originalBlockInfos.size() != processedBlockInfos.size()) return processedBlockInfos;
        Map<BlockPos, BlockState> template = new HashMap<>();
        for (StructureBlockInfo info : originalBlockInfos) template.put(info.pos(), info.state());
        BlockGetter getter = new TemplateGetter(template);
        List<StructureBlockInfo> out = new ArrayList<>(processedBlockInfos.size());
        for (int i = 0; i < processedBlockInfos.size(); i++) {
            StructureBlockInfo info = processedBlockInfos.get(i);
            BlockState connected = connect(getter, originalBlockInfos.get(i).pos(), info.state());
            out.add(connected == info.state() ? info : new StructureBlockInfo(info.pos(), connected, info.nbt()));
        }
        return out;
    }

    /** {@code state} at {@code pos} with its horizontal sides connected as vanilla would on placement; others unchanged. */
    public static BlockState connect(BlockGetter level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (!(block instanceof FenceBlock) && !(block instanceof IronBarsBlock)) return state;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbourPos = pos.relative(dir);
            BlockState neighbour = level.getBlockState(neighbourPos);
            boolean sturdy = neighbour.isFaceSturdy(level, neighbourPos, dir.getOpposite());
            boolean connects = block instanceof FenceBlock fence
                    ? fence.connectsTo(neighbour, sturdy, dir.getOpposite())
                    : ((IronBarsBlock) block).attachsTo(neighbour, sturdy);
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), connects);
        }
        return state;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return WreckStructures.CONNECTIONS.get();
    }

    /** The template's blocks as a block getter; everything else is water. */
    public record TemplateGetter(Map<BlockPos, BlockState> blocks) implements BlockGetter {

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.WATER.defaultBlockState());
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

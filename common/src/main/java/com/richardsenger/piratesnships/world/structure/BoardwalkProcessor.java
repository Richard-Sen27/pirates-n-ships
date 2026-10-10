package com.richardsenger.piratesnships.world.structure;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Turns a trail that runs out over water into a boardwalk (STR2, the pirate island's trails; processor type
 * {@code pirates_n_ships:boardwalk}): a trail-bed block (sand, gravel, coarse dirt, dirt path, a driftwood edging log)
 * whose final position is water becomes spruce planks, so the trail crosses an inlet or runs along the shore on boards
 * instead of leaving loose sand floating on the sea.
 *
 * <p>It works in {@link #finalizeProcessing}, after vanilla's gravity processor of a terrain-matching piece has moved
 * every block onto the surface (a {@code minecraft:rule} processor sees the block before that move, where a trail over
 * the sea is still in the air). Only positions inside the placement's bounding box (the chunk being placed) are
 * looked at; the rest are placed with another chunk.
 */
public final class BoardwalkProcessor extends StructureProcessor {

    public static final BoardwalkProcessor INSTANCE = new BoardwalkProcessor();
    public static final MapCodec<BoardwalkProcessor> CODEC = MapCodec.unit(() -> INSTANCE);

    /** The trail-bed blocks that become boards over water. */
    public static final Set<Block> BED = Set.of(Blocks.SAND, Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.DIRT_PATH,
            Blocks.STRIPPED_JUNGLE_LOG);

    private BoardwalkProcessor() {
    }

    /** What a trail block {@code state} becomes over water: boards for the bed, everything else unchanged. */
    public static BlockState overWater(BlockState state) {
        return BED.contains(state.getBlock()) ? Blocks.SPRUCE_PLANKS.defaultBlockState() : state;
    }

    @Override
    public List<StructureBlockInfo> finalizeProcessing(ServerLevelAccessor level, BlockPos offset, BlockPos pos,
                                                       List<StructureBlockInfo> originalBlockInfos,
                                                       List<StructureBlockInfo> processedBlockInfos, StructurePlaceSettings settings) {
        BoundingBox box = settings.getBoundingBox();
        List<StructureBlockInfo> out = new ArrayList<>(processedBlockInfos.size());
        for (StructureBlockInfo info : processedBlockInfos) {
            BlockState boards = overWater(info.state());
            if (boards != info.state() && (box == null || box.isInside(info.pos()))
                    && level.getFluidState(info.pos()).is(FluidTags.WATER)) {
                out.add(new StructureBlockInfo(info.pos(), boards, info.nbt()));
            } else {
                out.add(info);
            }
        }
        return out;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return PortStructures.BOARDWALK.get();
    }
}

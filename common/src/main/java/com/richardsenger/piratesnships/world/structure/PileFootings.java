package com.richardsenger.piratesnships.world.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

import java.util.List;
import java.util.Set;

/**
 * PIER1 (the human: "the poles at other piers could go completely down to the ground"): the piles of a pier, a jetty
 * and the pillars of a quay (and of their extension sections) end in a cobblestone footing at the template's lowest
 * row, which hangs in open water wherever the sea floor lies deeper than the template (the village pier's footings sat
 * in mid water with fish below). Run by {@link PortStructure#afterPlace} per chunk, after the berths' basins are carved
 * (the floor is the final one): for every footing column (a cobblestone or mossy cobblestone block on the piece's
 * lowest row with a pile or pillar block on top) over water, the pile carries on straight down through the water to the
 * first solid block, copying the block above the footing, and the footing moves to rest on the floor. Where the floor is
 * already the row below the footing (or the footing stands in the ground) nothing changes. Only columns inside the
 * chunk being placed are touched. {@code world.structures.piles_to_floor} switches it off.
 */
public final class PileFootings {

    /** The templates it applies to (their last path segment, {@link StructureLayout#templateName}). */
    public static final Set<String> PIECES = Set.of("pier", "pier_extension", "jetty", "jetty_extension", "quay", "quay_extension");
    /** The most blocks a pile is carried down (a guard against bottomless columns). */
    public static final int MAX_DEPTH = 64;

    private PileFootings() {
    }

    /** Whether {@code piece} is a pier, jetty or quay piece or an extension section of one. */
    public static boolean applies(StructurePiece piece) {
        return piece instanceof PoolElementStructurePiece p && PIECES.contains(StructureLayout.templateName(p.getElement().toString()));
    }

    /** Whether {@code state} is a footing block. */
    static boolean isFooting(BlockState state) {
        return state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE);
    }

    /** Extends the piles of {@code pieces} inside {@code chunkBox}; returns how many blocks were placed. */
    public static int extend(WorldGenLevel level, BoundingBox chunkBox, List<StructurePiece> pieces) {
        int placed = 0;
        for (StructurePiece piece : pieces) {
            if (!applies(piece) || !piece.getBoundingBox().intersects(chunkBox)) continue;
            BoundingBox box = piece.getBoundingBox();
            int y0 = box.minY();
            for (int x = Math.max(box.minX(), chunkBox.minX()); x <= Math.min(box.maxX(), chunkBox.maxX()); x++) {
                for (int z = Math.max(box.minZ(), chunkBox.minZ()); z <= Math.min(box.maxZ(), chunkBox.maxZ()); z++) {
                    placed += column(level, new BlockPos(x, y0, z));
                }
            }
        }
        return placed;
    }

    /** One column; 0 when it holds no footing over water. */
    static int column(WorldGenLevel level, BlockPos footing) {
        BlockState foot = level.getBlockState(footing);
        if (!isFooting(foot)) return 0;
        BlockState pile = level.getBlockState(footing.above());
        if (pile.isAir() || pile.is(Blocks.WATER) || isFooting(pile)) return 0;
        int floor = footing.getY() - 1;
        int min = Math.max(level.getMinBuildHeight(), footing.getY() - MAX_DEPTH);
        while (floor >= min && level.getBlockState(new BlockPos(footing.getX(), floor, footing.getZ())).getFluidState().is(FluidTags.WATER)) {
            floor--;
        }
        if (floor < min || floor == footing.getY() - 1) return 0;     // bottomless, or the floor is right under the footing
        BlockPos top = new BlockPos(footing.getX(), floor + 1, footing.getZ());
        level.setBlock(footing, pile, Block.UPDATE_CLIENTS);
        int n = 1;
        for (int y = footing.getY() - 1; y > top.getY(); y--) {
            level.setBlock(new BlockPos(footing.getX(), y, footing.getZ()), pile, Block.UPDATE_CLIENTS);
            n++;
        }
        level.setBlock(top, foot, Block.UPDATE_CLIENTS);
        return n + 1;
    }
}

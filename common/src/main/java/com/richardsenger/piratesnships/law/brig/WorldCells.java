package com.richardsenger.piratesnships.law.brig;

import com.richardsenger.piratesnships.law.content.BrigBarsBlock;
import com.richardsenger.piratesnships.law.content.BrigDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/** World adapter of {@link CellCheck}: classifies real blocks. */
public final class WorldCells {

    private WorldCells() {
    }

    /**
     * Brig door: a wall only while closed and locked. Brig bars: bars. Other doors, gates and trapdoors: passable
     * (they can be opened). Anything else: a wall if it has a collision shape. Unloaded blocks count as passable,
     * so a cell at the edge of loaded chunks is never trusted.
     */
    public static CellCheck.Cell classify(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return CellCheck.Cell.PASSABLE;
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof BrigDoorBlock) {
            return state.getValue(BrigDoorBlock.LOCKED) && !state.getValue(DoorBlock.OPEN) ? CellCheck.Cell.LOCKED_DOOR : CellCheck.Cell.PASSABLE;
        }
        if (block instanceof BrigBarsBlock) return CellCheck.Cell.BARS;
        if (block instanceof DoorBlock || block instanceof FenceGateBlock || block instanceof TrapDoorBlock) return CellCheck.Cell.PASSABLE;
        return state.getCollisionShape(level, pos).isEmpty() ? CellCheck.Cell.PASSABLE : CellCheck.Cell.WALL;
    }

    /** Checks from an entity's feet; if the feet block is solid (standing on a slab or carpet) from the block above. */
    public static CellCheck.Result check(Level level, BlockPos feet, int maxCells) {
        BlockPos start = classify(level, feet).passable() ? feet : feet.above();
        return CellCheck.check((x, y, z) -> classify(level, new BlockPos(x, y, z)), start.getX(), start.getY(), start.getZ(), maxCells);
    }
}

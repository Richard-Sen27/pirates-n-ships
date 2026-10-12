package com.richardsenger.piratesnships.world.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * STR6 (the human, 2026-10-12: "remove blocks in front of doors at generated structures, EVERY single structure has
 * that problem"): every door of a placed structure can be walked through. On both sides of a door (one step along its
 * {@code facing} and one step against it) the door's two rows must be free of collision boxes; a step (a block whose
 * collision box reaches at most half a block, a bottom slab) on the lower row moves the two rows one up; a flat box (a
 * carpet) counts as free. A
 * cobweb has no collision box but holds a walker fast, so it counts as blocking. The same rule as
 * {@code tools/check_structures.py}'s door check on the templates, here on the placed blocks, so a neighbour piece, a
 * decoration or a processor in front of a door is caught as well. Used by the layout GameTests' placed tests
 * ({@code StructureLayoutGameTests}, {@code VillageLayoutGameTests}, {@code OutpostLayoutGameTests}).
 */
public final class DoorAccess {

    private DoorAccess() {
    }

    /** One line per blocked side of every door (lower half) in {@code box}; empty when every door can be walked through. */
    public static List<String> blockedDoors(BlockGetter level, BoundingBox box) {
        return blockedDoors(level, box, false);
    }

    /**
     * As {@link #blockedDoors(BlockGetter, BoundingBox)}; with {@code sidesInBox} a side outside {@code box} is not judged
     * (one template placed alone: the cell beyond its box belongs to the neighbouring piece).
     */
    public static List<String> blockedDoors(BlockGetter level, BoundingBox box, boolean sidesInBox) {
        List<String> out = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState door = level.getBlockState(pos);
            if (!(door.getBlock() instanceof DoorBlock) || door.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
            Direction facing = door.getValue(DoorBlock.FACING);
            for (Direction side : new Direction[] {facing, facing.getOpposite()}) {
                BlockPos front = pos.relative(side);
                if (sidesInBox && !box.isInside(front)) continue;
                String blocked = blocked(level, front);
                if (blocked != null) {
                    out.add(door.getBlock().getName().getString() + " at " + pos.toShortString() + " (facing " + facing.getName()
                            + "): the " + side.getName() + " side " + front.toShortString() + " is blocked by " + blocked);
                }
            }
        }
        return out;
    }

    /** What blocks a walker who steps from a door into {@code front}, or null when the way is free. */
    static String blocked(BlockGetter level, BlockPos front) {
        BlockPos low = front;
        if (isStep(level, front)) low = front.above();
        for (BlockPos pos : new BlockPos[] {low, low.above()}) {
            BlockState state = level.getBlockState(pos);
            if (!passable(level, pos, state)) return state.getBlock().getName().getString() + " at " + pos.toShortString();
        }
        return null;
    }

    /** A collision box at most this high (a carpet, 1/16) is walked over as if it were not there. */
    static final double FLAT = 0.125;

    /** Whether a walker passes through {@code state}: no collision box (or a flat one, a carpet), and not a cobweb. */
    public static boolean passable(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.COBWEB)) return false;
        VoxelShape shape = state.getCollisionShape(level, pos);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) <= FLAT;
    }

    /** A half-block step (a bottom slab): a collision box whose top is above {@link #FLAT} and at most half a block high. */
    static boolean isStep(BlockGetter level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        return !shape.isEmpty() && shape.max(Direction.Axis.Y) > FLAT && shape.max(Direction.Axis.Y) <= 0.5;
    }
}

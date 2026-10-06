package com.richardsenger.piratesnships.sailing.sail;

import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.block.YardBlock;
import com.richardsenger.piratesnships.sailing.block.YardBlockEntity;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The square sail rule ({@link YardLinker}) applied to a level: the block lookup, the cloth geometry of the yard block
 * entities, and setting a sail's trim. Works the same on land and in a ship's plot (server side).
 */
public final class YardSails {

    private YardSails() {
    }

    /** The rule's view of {@code level}: yards by axis, air, mast blocks ({@link SailingBlocks#MASTS}), anything else. */
    public static YardLookup lookup(BlockGetter level) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        return (x, y, z) -> cell(level.getBlockState(m.set(x, y, z)));
    }

    public static YardLookup.Cell cell(BlockState s) {
        if (s.getBlock() instanceof YardBlock) {
            return s.getValue(YardBlock.AXIS) == Direction.Axis.X ? YardLookup.Cell.YARD_X : YardLookup.Cell.YARD_Z;
        }
        if (s.isAir()) {
            return YardLookup.Cell.AIR;
        }
        return s.is(SailingBlocks.MASTS) ? YardLookup.Cell.MAST : YardLookup.Cell.OTHER;
    }

    /** The yard containing {@code pos}, or null. */
    public static @Nullable YardRow row(BlockGetter level, BlockPos pos, YardRules rules) {
        return YardLinker.row(lookup(level), pos.getX(), pos.getY(), pos.getZ(), rules);
    }

    /** The cloth that the yard block at {@code pos} heads (only a head has one), or null. */
    public static @Nullable ClothGeometry geometryAt(BlockGetter level, BlockPos pos, YardRules rules) {
        SquareSail s = YardLinker.sailHeadedAt(lookup(level), pos.getX(), pos.getY(), pos.getZ(), rules);
        return s == null ? null : s.geometry();
    }

    /** The sail headed by the yard that contains {@code pos} (any block of the upper yard), or null. */
    public static @Nullable SquareSail sailHeadedByYardAt(BlockGetter level, BlockPos pos, YardRules rules) {
        YardLookup lookup = lookup(level);
        YardRow row = YardLinker.row(lookup, pos.getX(), pos.getY(), pos.getZ(), rules);
        return row == null ? null : YardLinker.sailHeadedBy(lookup, row, rules);
    }

    public static BlockPos headOf(YardRow row) {
        return new BlockPos(row.middleX(), row.y(), row.middleZ());
    }

    /**
     * Sets {@code trim} on every block of the yard containing {@code pos} (the head's block state is the sail's trim).
     * Returns false when there is no yard at {@code pos}.
     */
    public static boolean setTrim(Level level, BlockPos pos, SailTrim trim) {
        YardRow row = row(level, pos, SailingConfig.yardRules());
        if (row == null) {
            return false;
        }
        for (int a = row.min(); a <= row.max(); a++) {
            BlockPos p = new BlockPos(row.xAt(a), row.y(), row.zAt(a));
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof YardBlock && s.getValue(YardBlock.TRIM) != trim) {
                level.setBlock(p, s.setValue(YardBlock.TRIM, trim), Block.UPDATE_ALL);
            }
        }
        return true;
    }

    /** Cycles the trim of the sail headed by the yard at {@code pos}; returns the new trim, or null when it heads none. */
    public static @Nullable SailTrim cycle(ServerLevel level, BlockPos pos) {
        SquareSail sail = sailHeadedByYardAt(level, pos, SailingConfig.yardRules());
        if (sail == null) {
            return null;
        }
        BlockState head = level.getBlockState(headOf(sail.upper()));
        SailTrim next = head.getValue(YardBlock.TRIM).next();
        setTrim(level, pos, next);
        return next;
    }

    /**
     * After a yard block at {@code pos} was placed or removed: recomputes the cloth of every yard that could be affected,
     * i.e. the yards through {@code pos} and its two neighbors along each axis, and every yard within the largest gap
     * above or below any of their blocks.
     */
    public static void refreshAround(ServerLevel level, BlockPos pos) {
        YardRules rules = SailingConfig.yardRules();
        YardLookup lookup = lookup(level);
        Set<YardRow> near = new LinkedHashSet<>();
        Set<Long> columns = new LinkedHashSet<>();
        columns.add(BlockPos.asLong(pos.getX(), 0, pos.getZ()));
        for (BlockPos p : new BlockPos[] {pos, pos.east(), pos.west(), pos.north(), pos.south()}) {
            YardRow r = YardLinker.row(lookup, p.getX(), p.getY(), p.getZ(), rules);
            if (r != null && near.add(r)) {
                for (int a = r.min(); a <= r.max(); a++) columns.add(BlockPos.asLong(r.xAt(a), 0, r.zAt(a)));
            }
        }
        Set<YardRow> affected = new LinkedHashSet<>(near);
        for (long c : columns) {
            int x = BlockPos.getX(c);
            int z = BlockPos.getZ(c);
            for (int dy = -rules.maxGap(); dy <= rules.maxGap(); dy++) {
                int y = pos.getY() + dy;
                if (lookup.at(x, y, z).isYard()) {
                    YardRow r = YardLinker.row(lookup, x, y, z, rules);
                    if (r != null) affected.add(r);
                }
            }
        }
        for (YardRow r : affected) {
            refreshRow(level, lookup, r, rules);
        }
    }

    /** Writes the cloth of {@code row} to its blocks' entities: the sail's on the middle block, none on the others. */
    public static void refreshRow(Level level, YardLookup lookup, YardRow row, YardRules rules) {
        SquareSail sail = YardLinker.sailHeadedBy(lookup, row, rules);
        ClothGeometry g = sail == null ? null : sail.geometry();
        for (int a = row.min(); a <= row.max(); a++) {
            if (level.getBlockEntity(new BlockPos(row.xAt(a), row.y(), row.zAt(a))) instanceof YardBlockEntity be) {
                be.setGeometry(a == row.middle() ? g : null);
            }
        }
    }
}

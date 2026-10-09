package com.richardsenger.piratesnships.ship.hull.client;

import com.richardsenger.piratesnships.ship.hull.HullTags;
import com.richardsenger.piratesnships.ship.sable.WaterRegions;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The world blocks in which a water plant is not drawn because a dry region cuts its cell (HV1, HV1c, docs/design.md
 * §4.4 "Partly covered cells"). Two sets, both immutable once built (the section compiler reads them on worker threads):
 * {@link #cells} for plants without a model offset, {@link #offsetCells} for plants with vanilla's random XZ offset
 * (their cell shifted by the offset at that column, the same for every such block because vanilla clamps it to
 * ±0.25 blocks and seeds it with the column only). A two-block plant (a {@code half} property, e.g. tall seagrass) is
 * hidden as a whole: either half's cell counts for both.
 *
 * <p>No client classes: the GameTest server builds it from its own regions to check the rule.
 */
public record PlantFootprint(LongSet cells, LongSet offsetCells) {

    public static final PlantFootprint EMPTY = new PlantFootprint(LongSets.EMPTY_SET, LongSets.EMPTY_SET);

    /** A block with vanilla's XZ offset ({@code OffsetType.XZ}); its offset at a column is every such block's offset. */
    private static final class Reference {
        static final BlockState OFFSET = Blocks.TALL_SEAGRASS.defaultBlockState();
    }

    public boolean isEmpty() {
        return cells.isEmpty() && offsetCells.isEmpty();
    }

    /** Builds the footprint of all given regions now ({@code visited} gets the number of world blocks looked at). */
    public static PlantFootprint of(List<WaterRegions.View> views, long[] visited) {
        LongOpenHashSet cells = new LongOpenHashSet(), offset = new LongOpenHashSet();
        BlockPos.MutableBlockPos column = new BlockPos.MutableBlockPos();
        RegionFootprint.ModelOffset modelOffset = (x, z) -> Reference.OFFSET.getOffset(EmptyBlockGetter.INSTANCE, column.set(x, 0, z));
        for (WaterRegions.View v : views) {
            visited[0] += RegionFootprint.collect(v.minX, v.minY, v.minZ, v.maxX, v.maxY, v.maxZ, v::occupied,
                    v::toWorld, v::toPlot, modelOffset, cells, offset);
        }
        return cells.isEmpty() && offset.isEmpty() ? EMPTY : new PlantFootprint(cells, offset);
    }

    /**
     * Whether a block of {@code #pirates_n_ships:hidden_in_dry_hull} at {@code pos} is left out: its cell (shifted by
     * its model offset when it has one) or, for a two-block plant, its other half's cell is cut by a region.
     */
    public boolean hides(BlockState state, BlockPos pos) {
        if (isEmpty() || !state.is(HullTags.HIDDEN_IN_DRY_HULL)) {
            return false;
        }
        int otherHalf = 0;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            otherHalf = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? 1 : -1;
        }
        return hides(pos.asLong(), state.hasOffsetFunction(), otherHalf);
    }

    /**
     * The rule without block states: {@code offset} picks the shifted set, {@code otherHalfDy} is the other half's y
     * step (+1 for a lower half, −1 for an upper half, 0 for a one-block plant).
     */
    public boolean hides(long pos, boolean offset, int otherHalfDy) {
        LongSet set = offset ? offsetCells : cells;
        return set.contains(pos) || otherHalfDy != 0 && set.contains(BlockPos.offset(pos, 0, otherHalfDy, 0));
    }

    /**
     * The world blocks whose {@link #hides} answer may differ between the two footprints, grouped by chunk section
     * ({@link SectionPos#asLong}): every block in exactly one of the two sets of either kind, and the blocks right above
     * and below it (the other half of a two-block plant). These are the sections whose compiled mesh may now be wrong.
     */
    public static Long2ObjectMap<LongList> changedBySection(PlantFootprint before, PlantFootprint after) {
        LongOpenHashSet changed = new LongOpenHashSet();
        addMissing(before.cells, after.cells, changed);
        addMissing(after.cells, before.cells, changed);
        addMissing(before.offsetCells, after.offsetCells, changed);
        addMissing(after.offsetCells, before.offsetCells, changed);
        Long2ObjectMap<LongList> out = new Long2ObjectOpenHashMap<>();
        for (LongIterator it = changed.iterator(); it.hasNext(); ) {
            long pos = it.nextLong();
            long section = SectionPos.asLong(SectionPos.blockToSectionCoord(BlockPos.getX(pos)),
                    SectionPos.blockToSectionCoord(BlockPos.getY(pos)), SectionPos.blockToSectionCoord(BlockPos.getZ(pos)));
            out.computeIfAbsent(section, k -> new LongArrayList()).add(pos);
        }
        return out;
    }

    private static void addMissing(LongSet from, LongSet notIn, LongSet out) {
        for (LongIterator it = from.iterator(); it.hasNext(); ) {
            long pos = it.nextLong();
            if (!notIn.contains(pos)) {
                out.add(pos);
                out.add(BlockPos.offset(pos, 0, 1, 0));
                out.add(BlockPos.offset(pos, 0, -1, 0));
            }
        }
    }
}

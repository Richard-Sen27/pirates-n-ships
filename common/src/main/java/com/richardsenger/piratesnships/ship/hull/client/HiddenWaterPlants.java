package com.richardsenger.piratesnships.ship.hull.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.hull.HullTags;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullConfig;
import com.richardsenger.piratesnships.ship.sable.WaterRegions;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

/**
 * Client: world water plants inside dry hulls are not drawn (HV1, docs/design.md §4.4). Sable's water occlusion hides
 * the water inside a hull with a depth mask, but cutout blocks of the world (seagrass, kelp, sea pickles, bubble columns)
 * still show through the dry hold.
 *
 * <p>How: every {@code dry_hull_view.hidden_plants_refresh_ticks} ticks the client thread computes the world blocks
 * whose cell any water occlusion region cuts right now, also only in part (HV1c; {@link PlantFootprint},
 * {@link RegionFootprint}), and publishes them as an immutable footprint. The section compiler
 * ({@code mixin.MixinSectionCompiler}, on worker threads) asks {@link #filter} for every block and gets the bare fluid
 * instead of a block in {@code #pirates_n_ships:hidden_in_dry_hull} that the footprint hides (its cell, shifted by its
 * model offset, or its other half's cell is cut). When the footprint changes (the ship moved, a region changed), the
 * sections that hold a tagged block at a newly covered or left position, or right above or below one, are re-marked for
 * compilation; {@link LevelChunkSection#maybeHas} rules out most sections without looking at blocks.
 */
public final class HiddenWaterPlants {

    /** World blocks whose cell a region cuts; replaced, never mutated, so worker threads may read it. */
    private static volatile PlantFootprint hidden = PlantFootprint.EMPTY;

    private static final RefreshThrottle THROTTLE = new RefreshThrottle();
    private static @Nullable ClientLevel lastLevel;
    private static long ticks;

    // cost counters (logged at debug level every STATS_TICKS ticks)
    private static final int STATS_TICKS = 200;
    private static long statRefreshes, statVisited, statChangedBlocks, statSections, statMarked, statNanos;

    private HiddenWaterPlants() {
    }

    /**
     * The state the section compiler draws at {@code pos}: {@code state} itself, or its bare fluid when it is a tagged
     * water plant inside a dry region. Runs on chunk compile threads, for every block, so the common case is one
     * volatile read and an empty check.
     */
    public static BlockState filter(BlockState state, BlockPos pos) {
        PlantFootprint h = hidden;
        if (h.isEmpty() || !h.hides(state, pos)) {
            return state;
        }
        return state.getFluidState().createLegacyBlock();
    }

    /** Client tick end. */
    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            // a new level compiles all its sections from scratch
            lastLevel = level;
            hidden = PlantFootprint.EMPTY;
            THROTTLE.reset();
        }
        if (level == null || !THROTTLE.ready(ticks++, DryHullConfig.HIDDEN_PLANTS_REFRESH_TICKS.get())) {
            return;
        }
        long t0 = System.nanoTime();
        PlantFootprint next = PlantFootprint.EMPTY;
        if (DryHullConfig.HIDE_WATER_PLANTS.get()) {
            long[] visited = {0};
            next = PlantFootprint.of(WaterRegions.views(level), visited);
            statVisited += visited[0];
        }
        PlantFootprint before = hidden;
        if (!next.equals(before)) {
            hidden = next;
            remark(mc, level, before, next);
        }
        statRefreshes++;
        statNanos += System.nanoTime() - t0;
        if (ticks % STATS_TICKS == 0) {
            logStats();
        }
    }

    public static void reset() {
        hidden = PlantFootprint.EMPTY;
        lastLevel = null;
        THROTTLE.reset();
    }

    private static void remark(Minecraft mc, ClientLevel level, PlantFootprint before, PlantFootprint after) {
        Long2ObjectMap<LongList> changed = PlantFootprint.changedBySection(before, after);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Long2ObjectMap.Entry<LongList> e : changed.long2ObjectEntrySet()) {
            statSections++;
            statChangedBlocks += e.getValue().size();
            long s = e.getLongKey();
            int sx = SectionPos.x(s), sy = SectionPos.y(s), sz = SectionPos.z(s);
            if (sy < level.getMinSection() || sy >= level.getMaxSection() || !level.hasChunk(sx, sz)) {
                continue;
            }
            LevelChunk chunk = level.getChunk(sx, sz);
            LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(sy));
            if (section.hasOnlyAir() || !section.maybeHas(st -> st.is(HullTags.HIDDEN_IN_DRY_HULL))) {
                continue;
            }
            boolean any = false;
            for (int i = 0; i < e.getValue().size() && !any; i++) {
                pos.set(e.getValue().getLong(i));
                any = section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15).is(HullTags.HIDDEN_IN_DRY_HULL);
            }
            if (any) {
                mc.levelRenderer.setSectionDirty(sx, sy, sz);
                statMarked++;
            }
        }
    }

    private static void logStats() {
        if (statRefreshes > 0 && statChangedBlocks > 0 && Constants.LOG.isDebugEnabled()) {
            Constants.LOG.debug("Hidden water plants, last {} ticks: {} refreshes ({} µs each), {} blocks visited, "
                            + "{} footprint blocks changed in {} sections, {} sections re-marked",
                    STATS_TICKS, statRefreshes, statNanos / 1000 / statRefreshes, statVisited, statChangedBlocks,
                    statSections, statMarked);
        }
        statRefreshes = statVisited = statChangedBlocks = statSections = statMarked = statNanos = 0;
    }
}

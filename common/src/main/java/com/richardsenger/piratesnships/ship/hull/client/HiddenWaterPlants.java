package com.richardsenger.piratesnships.ship.hull.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.hull.HullTags;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullConfig;
import com.richardsenger.piratesnships.ship.sable.WaterRegions;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
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
 * <p>How: every {@code dry_hull_view.hidden_plants_refresh_ticks} ticks the client thread computes the world blocks that
 * all water occlusion regions cover right now ({@link RegionFootprint}) and publishes them as an immutable set. The
 * section compiler ({@code mixin.MixinSectionCompiler}, on worker threads) asks {@link #filter} for every block and
 * gets the bare fluid instead of a block in {@code #pirates_n_ships:hidden_in_dry_hull} that stands in that set. When
 * the footprint changes (the ship moved by a block, a region changed), the sections that hold a tagged block at a
 * newly covered or left position are re-marked for compilation; {@link LevelChunkSection#maybeHas} rules out most
 * sections without looking at blocks.
 */
public final class HiddenWaterPlants {

    /** World blocks ({@link BlockPos#asLong}) inside a region; replaced, never mutated, so worker threads may read it. */
    private static volatile LongSet hidden = LongSets.EMPTY_SET;

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
        LongSet h = hidden;
        if (h.isEmpty() || !state.is(HullTags.HIDDEN_IN_DRY_HULL) || !h.contains(pos.asLong())) {
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
            hidden = LongSets.EMPTY_SET;
            THROTTLE.reset();
        }
        if (level == null || !THROTTLE.ready(ticks++, DryHullConfig.HIDDEN_PLANTS_REFRESH_TICKS.get())) {
            return;
        }
        long t0 = System.nanoTime();
        LongSet next = LongSets.EMPTY_SET;
        if (DryHullConfig.HIDE_WATER_PLANTS.get()) {
            LongOpenHashSet set = new LongOpenHashSet();
            for (WaterRegions.View v : WaterRegions.views(level)) {
                statVisited += RegionFootprint.collect(v.minX, v.minY, v.minZ, v.maxX, v.maxY, v.maxZ, v::occupied,
                        v::toWorld, v::toPlot, set);
            }
            if (!set.isEmpty()) {
                next = set;
            }
        }
        LongSet before = hidden;
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
        hidden = LongSets.EMPTY_SET;
        lastLevel = null;
        THROTTLE.reset();
    }

    private static void remark(Minecraft mc, ClientLevel level, LongSet before, LongSet after) {
        Long2ObjectMap<LongList> changed = RegionFootprint.changedBySection(before, after);
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

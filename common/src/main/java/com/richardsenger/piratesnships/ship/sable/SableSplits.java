package com.richardsenger.piratesnships.ship.sable;

import com.richardsenger.piratesnships.Constants;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Sable's own splitting of a sub-level (docs/sable-notes.md §9.0j), seen from our side. Sable cuts a part that lost its
 * connection off into a new sub-level by itself; this class reports each split as one batch per parent, after Sable is
 * done with it, so the rules (ship/assembly {@code ShipSplits}) see every piece at once.
 *
 * <p>How a split is seen (Sable 2.0.6, paths relative to {@code refs/sable/common/src/main/java/dev/ryanhcode/sable}):
 * <ol>
 *   <li>{@code sublevel/plot/heat/SubLevelHeatMapManager.java#split} (l.200-251) runs inside the parent's
 *       {@code ServerSubLevel#tick} (l.234-236, only while {@code SableConfig.SUB_LEVEL_SPLITTING} is on). For each
 *       cut-off group it first calls every {@link SubLevelHeatMapManager.SplitListener} with the group's blocks, still
 *       in the parent's plot (l.240-242, registered with {@code addSplitListener}, l.382), then
 *       {@code SubLevelAssemblyHelper.assembleBlocks(level, blocks.get(0), blocks, bounds)} (l.244), which allocates the
 *       new sub-level ({@code SubLevelContainer#allocateSubLevel} notifies {@code SubLevelObserver#onSubLevelAdded},
 *       l.271) and moves the blocks with a pure translation {@code blocks.get(0) → plot.getCenterBlock()}
 *       ({@code api/SubLevelAssemblyHelper.java} l.103-107, {@code AssemblyTransform#apply} l.538-547). A piece
 *       without mass is removed again at once (l.246-250).</li>
 *   <li>The parent keeps the part still connected to its heat-map root, the first block ever added to it
 *       ({@code onSolidAdded} l.277-287; assembly adds blocks in the order given to {@code moveBlocks}, l.364-410), or
 *       the largest part when every block was cut off (l.221-228). When its block count is off, a cut that removes the
 *       root moves every block into a new body and the emptied parent is removed before we deliver
 *       ({@link #isSplitPending}).</li>
 *   <li>{@code SubLevelContainer#tick} ticks every sub-level, then calls each observer's {@code tick} (l.143-149): that
 *       is where a batch is complete and delivered.</li>
 * </ol>
 * Sable has no merge and no per-sub-level switch; the only switch is the global {@code sub_level_splitting} config.
 */
public final class SableSplits {

    /** One cut-off piece: the new ship, its blocks' positions in the parent's plot before the move, and the move. */
    public record Piece(UUID id, List<BlockPos> oldPlotBlocks, BlockPos offset) {
        /** Where a block of the parent's plot that moved with this piece is now (plot of {@link #id}). */
        public BlockPos moved(BlockPos oldPlotPos) {
            return oldPlotPos.offset(offset);
        }
    }

    /**
     * Pieces Sable cut off one parent during one container tick. {@code pieces} may be empty when Sable dropped every
     * announced piece again. {@code parentUserData} is a copy of the parent's user data taken when the first piece was
     * announced: the parent itself may be gone by now, see {@link #isSplitPending}.
     */
    @FunctionalInterface
    public interface SplitListener {
        void onSplit(ServerLevel level, UUID parent, List<Piece> pieces, CompoundTag parentUserData);
    }

    /** A group announced by the split listener; {@link #child} is filled in when Sable allocates its sub-level. */
    private static final class Pending {
        final UUID parent;
        final BlockPos anchor;
        final List<BlockPos> blocks;
        final CompoundTag parentUserData;
        @Nullable ServerSubLevel child;

        Pending(UUID parent, List<BlockPos> blocks, CompoundTag parentUserData) {
            this.parent = parent;
            this.anchor = blocks.get(0);
            this.blocks = blocks;
            this.parentUserData = parentUserData;
        }
    }

    private static final List<SplitListener> LISTENERS = new ArrayList<>();
    /** Per level: groups announced by the split listener, in order; the child is filled in by the observer. */
    private static final Map<Level, List<Pending>> PENDING = new IdentityHashMap<>();
    private static boolean hooked;

    private SableSplits() {
    }

    /** Registers a listener for completed splits (server thread). Call during mod construction. */
    public static synchronized void onSplit(SplitListener listener) {
        LISTENERS.add(listener);
        hook();
    }

    private static void hook() {
        if (hooked) {
            return;
        }
        hooked = true;
        SubLevelHeatMapManager.addSplitListener(SableSplits::announce);
        SableEventPlatform.INSTANCE.onSubLevelContainerReady((level, container) -> {
            if (container instanceof ServerSubLevelContainer server && level instanceof ServerLevel serverLevel) {
                server.addObserver(new SubLevelObserver() {
                    @Override
                    public void onSubLevelAdded(SubLevel subLevel) {
                        adopt(serverLevel, subLevel);
                    }

                    @Override
                    public void tick(SubLevelContainer subLevels) {
                        deliver(serverLevel);
                    }
                });
            }
        });
    }

    /** {@code SplitListener#addBlocks}: the blocks are still in the parent's plot; {@code blocks.get(0)} is the anchor. */
    private static void announce(Level level, dev.ryanhcode.sable.companion.math.BoundingBox3ic bounds, Collection<BlockPos> blocks) {
        if (!(level instanceof ServerLevel) || blocks.isEmpty()) {
            return;
        }
        List<BlockPos> copy = new ArrayList<>(blocks.size());
        for (BlockPos p : blocks) {
            copy.add(p.immutable());
        }
        SubLevel parent = Sable.HELPER.getContaining(level, copy.get(0));
        if (parent == null) {
            return;
        }
        // ServerSubLevel#getUserDataTag (l.548): copied now, while the parent surely exists
        CompoundTag data = parent instanceof ServerSubLevel s && s.getUserDataTag() != null ? s.getUserDataTag().copy() : new CompoundTag();
        PENDING.computeIfAbsent(level, l -> new ArrayList<>())
                .add(new Pending(parent.getUniqueId(), List.copyOf(copy), data));
    }

    /**
     * Whether Sable announced a split of {@code parent} this tick that is not delivered yet. Sable removes a parent that
     * a split emptied completely (every block moved into new bodies) in {@code SubLevelContainer#processSubLevelRemovals}
     * (l.153-168: its mass tracker is invalid), which {@code SubLevelContainer#tick} (l.141-147) runs after the
     * sub-levels ticked (where the split happens) and before the observers' {@code tick} (where we deliver). A removal
     * listener that sees {@code destroyed} for such a parent must leave the ship's identity to the split handling.
     * This happens when Sable's heat map has lost count of the parent's blocks: after a split that cut every block off
     * its root ({@code SubLevelHeatMapManager#split} l.224-227 rebuilds the count from the kept group with
     * {@code rebuildHeatmapFrom}, l.254-266, before the other groups leave, whose removal, {@code onSolidRemoved}
     * l.328-331, lowers it again), the next loss of the root sees one group that is not the "whole" sub-level
     * (l.204, l.224) and moves all of it into a new body.
     */
    public static boolean isSplitPending(Level level, UUID parent) {
        List<Pending> list = PENDING.get(level);
        if (list == null) {
            return false;
        }
        for (Pending p : list) {
            if (p.parent.equals(parent)) {
                return true;
            }
        }
        return false;
    }

    /** The sub-level allocated right after an announcement is the announced group's new body. */
    private static void adopt(ServerLevel level, SubLevel subLevel) {
        List<Pending> list = PENDING.get(level);
        if (list == null || list.isEmpty() || !(subLevel instanceof ServerSubLevel s)) {
            return;
        }
        Pending last = list.get(list.size() - 1);
        if (last.child == null) {
            last.child = s;
        }
    }

    private static void deliver(ServerLevel level) {
        List<Pending> list = PENDING.remove(level);
        if (list == null || list.isEmpty()) {
            return;
        }
        Map<UUID, List<Piece>> byParent = new LinkedHashMap<>();
        Map<UUID, CompoundTag> parentData = new LinkedHashMap<>();
        for (Pending p : list) {
            // every announced parent is delivered, also with no surviving piece (its listeners may have held state back)
            byParent.computeIfAbsent(p.parent, k -> new ArrayList<>());
            parentData.putIfAbsent(p.parent, p.parentUserData);
            ServerSubLevel child = p.child;
            if (child == null || child.isRemoved()) {
                continue; // Sable dropped a massless piece (l.246-250)
            }
            BlockPos center = child.getPlot().getCenterBlock();
            if (level.getBlockState(center).isAir()) {
                continue; // not the announced group's body (its anchor block lands on the plot center, l.103-107)
            }
            BlockPos offset = center.subtract(p.anchor);
            byParent.computeIfAbsent(p.parent, k -> new ArrayList<>()).add(new Piece(child.getUniqueId(), p.blocks, offset));
        }
        for (Map.Entry<UUID, List<Piece>> e : byParent.entrySet()) {
            for (SplitListener l : List.copyOf(LISTENERS)) {
                try {
                    l.onSplit(level, e.getKey(), List.copyOf(e.getValue()), parentData.get(e.getKey()).copy());
                } catch (RuntimeException ex) {
                    Constants.LOG.error("Ship split handling failed for {}", e.getKey(), ex);
                }
            }
        }
    }

    // ---------------------------------------------------------------- Sable's global switch

    private static @Nullable BooleanSupplier splittingValue;
    private static @Nullable Method splittingSetter;
    private static boolean splittingLookupFailed;

    /**
     * Sable's {@code sub_level_splitting} ({@code SableConfig.java} l.9, l.27-29, read in {@code ServerSubLevel#tick}
     * l.234), or true if it cannot be read. The value is a NeoForge {@code ModConfigSpec.BooleanValue} (also on Fabric,
     * through Forge Config API Port), a type the common module cannot see, so it is reached through
     * {@link BooleanSupplier}, which it implements, and reflection for the setter.
     */
    public static boolean sableSplitting() {
        BooleanSupplier v = splittingValue();
        return v == null || v.getAsBoolean();
    }

    /**
     * Sets Sable's {@code sub_level_splitting} in memory ({@code ModConfigSpec.ConfigValue#set}; the file is not
     * rewritten). Returns false if Sable's config could not be reached.
     */
    public static boolean setSableSplitting(boolean on) {
        BooleanSupplier v = splittingValue();
        if (v == null || splittingSetter == null) {
            return false;
        }
        try {
            splittingSetter.invoke(v, on);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Constants.LOG.warn("Could not set Sable's sub_level_splitting", e);
            return false;
        }
    }

    private static @Nullable BooleanSupplier splittingValue() {
        if (splittingValue != null || splittingLookupFailed) {
            return splittingValue;
        }
        try {
            Object value = SableConfig.class.getField("SUB_LEVEL_SPLITTING").get(null);
            Method set = null;
            for (Method m : value.getClass().getMethods()) {
                if (m.getName().equals("set") && m.getParameterCount() == 1) {
                    set = m;
                    break;
                }
            }
            if (value instanceof BooleanSupplier b && set != null) {
                splittingSetter = set;
                splittingValue = b;
            } else {
                splittingLookupFailed = true;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Constants.LOG.warn("Sable's sub_level_splitting config is not reachable", e);
            splittingLookupFailed = true;
        }
        return splittingValue;
    }
}

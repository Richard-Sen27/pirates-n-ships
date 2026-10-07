package com.richardsenger.piratesnships.ship.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The single entry point from Pirates 'n' Ships into Sable (docs/sable-notes.md §7). Every Sable import of the mod
 * lives in this package, so a Sable update touches only here. Methods are thin and factual; policy (what is a ship
 * block, when disassembly is allowed, water rules) lives in the feature packages.
 *
 * <p>Sable sources (2.0.6) behind each call are named in the method docs, relative to
 * {@code refs/sable/common/src/main/java/dev/ryanhcode/sable}.
 */
public final class SableShips {

    private SableShips() {
    }

    // ---------------------------------------------------------------- gather and assemble

    /** Outcome of a connected-block gather. */
    public enum GatherState { SUCCESS, NO_BLOCKS, TOO_MANY_BLOCKS }

    /**
     * @param blocks  gathered world positions (empty unless {@code SUCCESS})
     * @param checked how many blocks the flood fill visited before it stopped
     * @param min     lower corner of the gathered blocks (null unless {@code SUCCESS})
     * @param max     upper corner, inclusive (null unless {@code SUCCESS})
     */
    public record Gathered(GatherState state, Set<BlockPos> blocks, int checked, @Nullable BlockPos min, @Nullable BlockPos max) { }

    /**
     * Flood fill over non-air blocks from {@code origin} (faces and edges, not corners), accepting a neighbour only when
     * {@code accept} says so. The origin itself is always taken if it is not air.
     * Backed by {@code api/SubLevelAssemblyHelper.java#gatherConnectedBlocks} (l.191-277).
     */
    public static Gathered gather(ServerLevel level, BlockPos origin, int limit, BiPredicate<BlockPos, BlockState> accept) {
        SubLevelAssemblyHelper.GatherResult r = SubLevelAssemblyHelper.gatherConnectedBlocks(origin, level, limit,
                (fromPos, fromState, pos, state, dir) -> accept.test(pos, state));
        return switch (r.assemblyState()) {
            case SUCCESS -> {
                BoundingBox3i b = r.boundingBox();
                yield new Gathered(GatherState.SUCCESS, Set.copyOf(r.blocks()), r.checkedBlocks(),
                        new BlockPos(b.minX(), b.minY(), b.minZ()), new BlockPos(b.maxX(), b.maxY(), b.maxZ()));
            }
            case TOO_MANY_BLOCKS -> new Gathered(GatherState.TOO_MANY_BLOCKS, Set.of(), r.checkedBlocks(), null, null);
            case NO_BLOCKS -> new Gathered(GatherState.NO_BLOCKS, Set.of(), r.checkedBlocks(), null, null);
        };
    }

    /**
     * Moves the world blocks into a new sub-level that appears exactly where they were (identity orientation).
     * Vacated positions become air. Backed by {@code api/SubLevelAssemblyHelper.java#assembleBlocks} (l.69-140).
     *
     * @return the new ship, or null if Sable is not ready for this level
     */
    public static @Nullable ShipBody assemble(ServerLevel level, BlockPos anchor, Collection<BlockPos> blocks, BlockPos min, BlockPos max) {
        if (SubLevelContainer.getContainer(level) == null) {
            return null;
        }
        ServerSubLevel sub = SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, new BoundingBox3i(min, max));
        return sub == null ? null : new ShipBody(sub);
    }

    /**
     * Moves every block of {@code ship} back into the world: {@code plotAnchor} lands on {@code worldGoal}, rotated by
     * {@code quarterTurns} counter-clockwise 90° steps (seen from above). Overwrites whatever is at the targets, so the
     * caller must check for obstructions first. Plot-resident entities are kicked to world space; log-out tracking points
     * are moved along. The sub-level is removed afterwards.
     * Backed by {@code api/SubLevelAssemblyHelper.java} {@code AssemblyTransform} (l.513-570), {@code moveBlocks}
     * (l.327-463), {@code moveTrackingPoints} (l.279-295), {@code sublevel/plot/ServerLevelPlot.java#kickAllEntities}
     * (l.266) and {@code api/sublevel/SubLevelContainer.java#removeSubLevel} (l.517).
     */
    public static void disassemble(ShipBody ship, BlockPos plotAnchor, BlockPos worldGoal, int quarterTurns, List<BlockPos> plotBlocks) {
        ServerSubLevel sub = ship.raw();
        ServerLevel level = sub.getLevel();
        int turns = Math.floorMod(quarterTurns, 4);
        SubLevelAssemblyHelper.AssemblyTransform transform =
                new SubLevelAssemblyHelper.AssemblyTransform(plotAnchor, worldGoal, turns, rotationFor(turns), level);
        BoundingBox3i plotBounds = new BoundingBox3i(sub.getPlot().getBoundingBox());
        if (!plotBlocks.isEmpty()) {
            sub.getPlot().kickAllEntities();
            SubLevelAssemblyHelper.moveBlocks(level, transform, plotBlocks);
        }
        SubLevelAssemblyHelper.moveTrackingPoints(level, plotBounds, null, transform);
        remove(ship);
    }

    /**
     * Moves blocks from one ship's plot into another ship's plot (rejoining, RS2): {@code anchor} (plot of {@code from})
     * lands on {@code target} (plot of {@code to}), rotated by {@code quarterTurns} counter-clockwise 90° steps; block
     * states are rotated and block entities keep their data. Overwrites whatever is at the targets, so the caller must
     * check for overlaps first. Log-out tracking points on {@code from} move to {@code to}. {@code from} is left empty,
     * the caller removes it.
     * Backed by {@code api/SubLevelAssemblyHelper.java} {@code AssemblyTransform} (l.513-560), {@code moveBlocks}
     * (l.327-463: creates missing chunks in the target plot, l.354-365, then for each block saves the block entity with
     * {@code saveWithFullMetadata}, writes the rotated state, loads the data with {@code loadWithComponents} and clears
     * the old cell) and {@code moveTrackingPoints} (l.279-295). The same calls that assembly (world to plot) and our
     * disassembly (plot to world) use; plot to plot is the third case of the same transform.
     */
    public static void moveBlocksBetween(ShipBody from, ShipBody to, BlockPos anchor, BlockPos target, int quarterTurns,
                                         List<BlockPos> plotBlocks) {
        ServerSubLevel sub = from.raw();
        ServerLevel level = sub.getLevel();
        int turns = Math.floorMod(quarterTurns, 4);
        SubLevelAssemblyHelper.AssemblyTransform transform =
                new SubLevelAssemblyHelper.AssemblyTransform(anchor, target, turns, rotationFor(turns), level);
        BoundingBox3i plotBounds = new BoundingBox3i(sub.getPlot().getBoundingBox());
        if (!plotBlocks.isEmpty()) {
            SubLevelAssemblyHelper.moveBlocks(level, transform, plotBlocks);
        }
        SubLevelAssemblyHelper.moveTrackingPoints(level, plotBounds, to.raw(), transform);
    }

    /** The block-state rotation matching {@code quarterTurns} counter-clockwise steps (the inverse of Aeronautics' mapping). */
    static Rotation rotationFor(int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> Rotation.COUNTERCLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.CLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    // ---------------------------------------------------------------- lookup

    /**
     * The ship whose plot contains this position (plot coordinates only, not a world position near a ship).
     * Backed by {@code ActiveSableCompanion.java#getContaining(Level, Vec3i)} (l.92).
     */
    public static @Nullable ShipBody containing(Level level, BlockPos plotPos) {
        return wrap(Sable.HELPER.getContaining(level, plotPos));
    }

    /** The ship containing this block entity. Backed by {@code ActiveSableCompanion.java#getContaining(BlockEntity)} (l.118). */
    public static @Nullable ShipBody containing(BlockEntity blockEntity) {
        return wrap(Sable.HELPER.getContaining(blockEntity));
    }

    /** A loaded ship by its sub-level UUID. Backed by {@code api/sublevel/SubLevelContainer.java#getSubLevel(UUID)} (l.528). */
    public static @Nullable ShipBody byId(ServerLevel level, UUID id) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        return container == null ? null : wrap(container.getSubLevel(id));
    }

    /** All loaded ships of a level. Backed by {@code ServerSubLevelContainer#getAllSubLevels} (l.177). */
    public static List<ShipBody> all(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        List<ShipBody> out = new ArrayList<>();
        if (container != null) {
            for (ServerSubLevel s : container.getAllSubLevels()) {
                out.add(new ShipBody(s));
            }
        }
        return out;
    }

    /** Removes a ship for good (its plot is freed). Backed by {@code SubLevelContainer#removeSubLevel} (l.517). */
    public static void remove(ShipBody ship) {
        ServerSubLevel sub = ship.raw();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(sub.getLevel());
        if (container != null && container.getSubLevel(sub.getUniqueId()) != null) {
            container.removeSubLevel(sub, SubLevelRemovalReason.REMOVED);
        }
    }

    private static @Nullable ShipBody wrap(@Nullable SubLevel sub) {
        return sub instanceof ServerSubLevel s && !s.isRemoved() ? new ShipBody(s) : null;
    }

    // ---------------------------------------------------------------- lifecycle

    /** Called on the server thread when a ship leaves the level. {@code destroyed} is false for a plain unload. */
    @FunctionalInterface
    public interface RemovalListener {
        void onShipRemoved(ServerLevel level, UUID shipId, boolean destroyed);
    }

    /** Called on the client thread when a ship leaves a client level (unloaded, out of range or destroyed). */
    @FunctionalInterface
    public interface ClientRemovalListener {
        void onClientShipRemoved(Level level, UUID shipId);
    }

    /** Called once per physics substep on the server thread, before Sable applies queued forces. */
    @FunctionalInterface
    public interface PhysicsTickListener {
        void onPhysicsTick(ServerLevel level, double timeStep);
    }

    private static final List<RemovalListener> REMOVAL_LISTENERS = new ArrayList<>();
    private static final List<ClientRemovalListener> CLIENT_REMOVAL_LISTENERS = new ArrayList<>();
    private static boolean hooked;

    /**
     * Registers a listener for server ship removal. Call during mod construction. Installs one Sable observer per server
     * container through {@code platform/SableEventPlatform.java#onSubLevelContainerReady} (l.15) and
     * {@code api/sublevel/SubLevelObserver.java#onSubLevelRemoved}; {@code SubLevelRemovalReason.REMOVED} means destroyed,
     * {@code UNLOADED} means unloaded ({@code sublevel/storage/SubLevelRemovalReason.java}).
     */
    public static synchronized void onShipRemoved(RemovalListener listener) {
        REMOVAL_LISTENERS.add(listener);
        hook();
    }

    /**
     * Registers a listener for client ship removal: the same observer mechanism on the client container
     * ({@code api/sublevel/SubLevelContainer.java#addObserver} l.173, observers notified on removal at l.487). Safe on a
     * dedicated server (it never fires there).
     */
    public static synchronized void onClientShipRemoved(ClientRemovalListener listener) {
        CLIENT_REMOVAL_LISTENERS.add(listener);
        hook();
    }

    /**
     * Registers a per-substep physics callback ({@code SableEventPlatform#onPhysicsTick} l.17,
     * {@code api/event/SablePrePhysicsTickEvent.java#prePhysicsTick}, fired in
     * {@code sublevel/system/SubLevelPhysicsSystem.java} l.271 right before queued forces are applied). Call during mod
     * construction. Queued forces recorded here are applied this substep.
     */
    public static void onPhysicsTick(PhysicsTickListener listener) {
        SableEventPlatform.INSTANCE.onPhysicsTick((system, timeStep) -> listener.onPhysicsTick(system.getLevel(), timeStep));
    }

    private static void hook() {
        if (hooked) {
            return;
        }
        hooked = true;
        SableEventPlatform.INSTANCE.onSubLevelContainerReady((level, container) -> {
            if (container instanceof ServerSubLevelContainer server && level instanceof ServerLevel serverLevel) {
                server.addObserver(new SubLevelObserver() {
                    @Override
                    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
                        boolean destroyed = reason == SubLevelRemovalReason.REMOVED;
                        for (RemovalListener l : List.copyOf(REMOVAL_LISTENERS)) {
                            l.onShipRemoved(serverLevel, subLevel.getUniqueId(), destroyed);
                        }
                    }
                });
            } else if (level.isClientSide()) {
                container.addObserver(new SubLevelObserver() {
                    @Override
                    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
                        for (ClientRemovalListener l : List.copyOf(CLIENT_REMOVAL_LISTENERS)) {
                            l.onClientShipRemoved(level, subLevel.getUniqueId());
                        }
                    }
                });
            }
        });
    }
}

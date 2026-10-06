package com.richardsenger.piratesnships.ship;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block-change listeners for ship runtimes. Sable publishes no "block changed in a plot" event
 * (docs/sable-notes.md §9.0b), so {@code mixin/MixinLevelChunk} calls {@link #fire} for every block state change in a
 * server level on the server thread, and the hull and sailing runtimes subscribe here instead of adding more mixins.
 * Listeners must return at once when their level has no ship runtime: this runs for every block change in the world.
 */
public final class ShipBlockChanges {

    /** A block state changed at {@code pos} (any chunk, world or plot) from {@code oldState} to {@code newState}. */
    @FunctionalInterface
    public interface Listener {
        void onBlockChanged(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private ShipBlockChanges() {
    }

    /** Registers a listener; call during mod construction. */
    public static void register(Listener listener) {
        LISTENERS.add(listener);
    }

    /** Called by {@code MixinLevelChunk}. */
    public static void fire(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        for (Listener l : LISTENERS) {
            l.onBlockChanged(level, pos, oldState, newState);
        }
    }
}

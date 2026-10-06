package com.richardsenger.piratesnships.ship.decor.flag;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-side notification when what a flagpole shows changes: hoisted, struck, raised, taken down, broken, or set by
 * command. Later systems (ship allegiance, NPC AI that stops firing at a ship that struck its colors, crimes for
 * attacking it) register a listener in their {@code registerEvents()}. Listeners run on the server thread.
 */
public final class FlagpoleEvents {

    /**
     * @param before what the pole showed before
     * @param after  what it shows now
     * @param actor  the player who caused it, or null (pole broken without a player, command from the console)
     */
    public record FlagChange(ServerLevel level, BlockPos pos, FlagReading before, FlagReading after,
                             FlagpoleMachine.Cause cause, @Nullable UUID actor) {
    }

    @FunctionalInterface
    public interface Listener {
        void onChange(FlagChange change);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private FlagpoleEvents() {
    }

    public static void register(Listener listener) {
        LISTENERS.add(listener);
    }

    public static void unregister(Listener listener) {
        LISTENERS.remove(listener);
    }

    static void fire(FlagChange change) {
        for (Listener l : LISTENERS) l.onChange(change);
    }
}

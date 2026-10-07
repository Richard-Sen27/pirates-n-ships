package com.richardsenger.piratesnships.mob.ai;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Counters and the state trace of the fighting mobs' AI, server thread only.
 *
 * <ul>
 *   <li>{@link #stats}: per-mob counters (path recalculations, attacks started, targets acquired and lost), always
 *       kept; GameTests read them.</li>
 *   <li>The trace ({@code /pirates mob debug on}): every change of a mob's state on a channel ({@code duelist},
 *       {@code target}, {@code brain}) within {@link #RANGE} blocks of a listener is logged to the server log, one
 *       line per change, and sent to the listening player's chat at most {@link #CHAT_LINES_PER_SECOND} lines per
 *       second (the rest only go to the log). Off by default; with no listener a report costs one map check.</li>
 * </ul>
 */
public final class DuelistDebug {

    /** Blocks around a listener in which mobs are traced. */
    public static final double RANGE = 24.0;
    public static final int CHAT_LINES_PER_SECOND = 4;

    /** Counters of one mob. */
    public static final class Stats {
        public int repaths;
        public int attacksStarted;
        public int targetsAcquired;
        public int targetsLost;
    }

    /** One listener: a player (followed as it moves) or a fixed point (console, command block). */
    private static final class Listener {
        final @Nullable UUID player;
        final ResourceKey<Level> dimension;
        final Vec3 at;
        long second = -1;
        int sent;

        Listener(@Nullable UUID player, ResourceKey<Level> dimension, Vec3 at) {
            this.player = player;
            this.dimension = dimension;
            this.at = at;
        }
    }

    private static final Map<Mob, Stats> STATS = new WeakHashMap<>();
    private static final Map<Mob, Map<String, String>> LAST = new WeakHashMap<>();
    private static final Map<Object, Listener> LISTENERS = new HashMap<>();
    private static int linesLogged;

    private DuelistDebug() {
    }

    public static Stats stats(Mob mob) {
        return STATS.computeIfAbsent(mob, m -> new Stats());
    }

    // --- listeners ----------------------------------------------------------------------------------------------

    /** Starts tracing mobs near {@code player} (followed as it moves). */
    public static void listen(ServerPlayer player) {
        LISTENERS.put(player.getUUID(), new Listener(player.getUUID(), player.level().dimension(), player.position()));
    }

    /** Starts tracing mobs near a fixed point; {@code key} identifies the source (to turn it off again). */
    public static void listen(Object key, ResourceKey<Level> dimension, Vec3 at) {
        LISTENERS.put(key, new Listener(null, dimension, at));
    }

    /** Stops tracing for a player's UUID or a fixed-point key; returns whether it was on. */
    public static boolean unlisten(Object key) {
        boolean was = LISTENERS.remove(key) != null;
        if (LISTENERS.isEmpty()) LAST.clear();
        return was;
    }

    public static boolean active() {
        return !LISTENERS.isEmpty();
    }

    public static void clear() {
        LISTENERS.clear();
        LAST.clear();
    }

    // --- reports ------------------------------------------------------------------------------------------------

    /**
     * Reports {@code mob}'s state on {@code channel}. Only a change is printed: {@code state} is compared with the last
     * one of that channel, {@code detail} (distances and the like) is printed with it but not compared.
     */
    public static void report(Mob mob, String channel, String state, @Nullable LivingEntity target, String detail) {
        if (LISTENERS.isEmpty() || mob.level().isClientSide()) return;
        Map<String, String> last = LAST.computeIfAbsent(mob, m -> new HashMap<>());
        if (state.equals(last.get(channel))) return;
        last.put(channel, state);
        MinecraftServer server = mob.level().getServer();
        if (server == null) return;
        String dist = target == null ? "-" : String.format(Locale.ROOT, "%.2f", mob.distanceTo(target));
        String line = String.format(Locale.ROOT, "[mob debug] t=%d %s#%d %s: %s (target %s, dist %s%s%s)",
                mob.level().getGameTime(), mob.getType().toShortString(), mob.getId(), channel, state,
                target == null ? "none" : target.getName().getString() + "#" + target.getId(), dist,
                detail.isEmpty() ? "" : ", ", detail);
        boolean near = false;
        long second = mob.level().getGameTime() / 20;
        for (Listener l : LISTENERS.values()) {
            ServerPlayer p = l.player == null ? null : server.getPlayerList().getPlayer(l.player);
            ResourceKey<Level> dim = p != null ? p.level().dimension() : l.dimension;
            Vec3 at = p != null ? p.position() : l.at;
            if (!dim.equals(mob.level().dimension()) || at.distanceToSqr(mob.position()) > RANGE * RANGE) continue;
            near = true;
            if (p == null) continue;
            if (l.second != second) {
                l.second = second;
                l.sent = 0;
            }
            if (l.sent++ < CHAT_LINES_PER_SECOND) p.sendSystemMessage(Component.literal(line));
        }
        if (near) {
            Constants.LOG.info(line);
            linesLogged++;
        }
    }

    /** Trace lines written to the log since the server started (for tests). */
    public static int linesLogged() {
        return linesLogged;
    }
}

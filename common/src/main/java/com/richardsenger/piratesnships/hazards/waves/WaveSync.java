package com.richardsenger.piratesnships.hazards.waves;

import com.richardsenger.piratesnships.hazard.HazardConfig;
import com.richardsenger.piratesnships.platform.Services;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends the sea of each player's level to the player (WV1): on login, every {@code waves.sync_interval_ticks}, and at
 * once to every player of a level whose sea changed its heading or origin (WAV7: a direction step starts its crossfade
 * {@link SeaStateModel#DIRECTION_LEAD_TICKS} later, so the clients draw it from its first tick). Tests observe what is
 * sent through {@link #record} (a mock player has no connection).
 */
public final class WaveSync {

    private static final Map<UUID, List<WaveSyncPayload>> RECORDINGS = new HashMap<>();
    /** Heading and origin last sent per level, to send a change at once. */
    private static final Map<ServerLevel, Object> SENT = new java.util.IdentityHashMap<>();

    private WaveSync() {
    }

    public static void onServerTick(MinecraftServer server) {
        int interval = HazardConfig.SYNC_INTERVAL_TICKS.get();
        boolean due = server.getTickCount() % interval == 0;
        java.util.Set<ServerLevel> changed = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (ServerLevel level : server.getAllLevels()) {
            if (changed(level)) {
                changed.add(level);
            }
        }
        if (!due && changed.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (due || changed.contains(player.serverLevel())) {
                send(player, interval);
            }
        }
    }

    /** Whether the heading or origin of {@code level}'s sea differs from what was last sent (and remembers it). */
    private static synchronized boolean changed(ServerLevel level) {
        WaveField f = SeaStates.field(level);
        Object key = java.util.List.of(f.heading(), f.origin());
        Object last = SENT.put(level, key);
        return last != null && !last.equals(key);
    }

    /** Forgets the levels of a stopped server. */
    public static synchronized void clear() {
        SENT.clear();
    }

    public static void onLogin(ServerPlayer player) {
        send(player, HazardConfig.SYNC_INTERVAL_TICKS.get());
    }

    private static void send(ServerPlayer player, int interval) {
        WaveSyncPayload p = payload(player.serverLevel(), interval);
        recorded(player.getUUID(), p);
        Services.NETWORK.sendToPlayer(player, p);
    }

    /** The payload for players in {@code level}. */
    public static WaveSyncPayload payload(ServerLevel level, int interval) {
        return WaveSyncPayload.of(SeaStates.current(level), SeaStates.field(level), SeaStates.peakWavelength(level), interval);
    }

    /**
     * Sends {@code level}'s sea to the player with {@code id} through {@code sink} and records it, the path
     * {@link #onServerTick} takes per player; for tests with a mock player.
     */
    public static WaveSyncPayload syncTo(ServerLevel level, UUID id, java.util.function.Consumer<WaveSyncPayload> sink) {
        WaveSyncPayload p = payload(level, HazardConfig.SYNC_INTERVAL_TICKS.get());
        recorded(id, p);
        sink.accept(p);
        return p;
    }

    private static synchronized void recorded(UUID id, WaveSyncPayload p) {
        List<WaveSyncPayload> rec = RECORDINGS.get(id);
        if (rec != null) {
            rec.add(p);
        }
    }

    /** Starts recording the payloads sent to {@code player}; the returned list fills as they are sent. */
    public static synchronized List<WaveSyncPayload> record(UUID player) {
        return RECORDINGS.computeIfAbsent(player, k -> java.util.Collections.synchronizedList(new ArrayList<>()));
    }

    public static synchronized void stopRecording(UUID player) {
        RECORDINGS.remove(player);
    }
}

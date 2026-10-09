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
 * Sends the sea of each player's level to the player (WV1): on login and every {@code waves.sync_interval_ticks}.
 * Tests observe what is sent through {@link #record} (a mock player has no connection).
 */
public final class WaveSync {

    private static final Map<UUID, List<WaveSyncPayload>> RECORDINGS = new HashMap<>();

    private WaveSync() {
    }

    public static void onServerTick(MinecraftServer server) {
        int interval = HazardConfig.SYNC_INTERVAL_TICKS.get();
        if (server.getTickCount() % interval != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, interval);
        }
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

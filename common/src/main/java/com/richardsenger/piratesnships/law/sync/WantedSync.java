package com.richardsenger.piratesnships.law.sync;

import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends each online player their own {@link WantedSyncPayload}: at login, then every
 * {@code wanted_sync_interval_ticks} only if the level or the displayed score changed. The score decays
 * continuously, but the displayed (rounded up) score changes rarely, so this sends little.
 */
public final class WantedSync {

    private static final Map<UUID, WantedSyncPayload> LAST_SENT = new ConcurrentHashMap<>();

    private WantedSync() {
    }

    /** The payload {@code player} should have now. */
    public static WantedSyncPayload snapshot(LivingEntity player) {
        return new WantedSyncPayload(LawService.wantedLevel(player), LawService.displayScore(player));
    }

    /** Sends unconditionally (login). */
    public static void sendNow(ServerPlayer player) {
        WantedSyncPayload p = snapshot(player);
        LAST_SENT.put(player.getUUID(), p);
        Services.NETWORK.sendToPlayer(player, p);
    }

    /** Sends if the value differs from the last one sent. Returns whether it sent. */
    public static boolean sendIfChanged(ServerPlayer player) {
        WantedSyncPayload p = snapshot(player);
        if (Objects.equals(LAST_SENT.get(player.getUUID()), p)) return false;
        LAST_SENT.put(player.getUUID(), p);
        Services.NETWORK.sendToPlayer(player, p);
        return true;
    }

    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % LawConfig.WANTED_SYNC_INTERVAL_TICKS.get() != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendIfChanged(player);
    }

    public static void onLogout(ServerPlayer player) {
        LAST_SENT.remove(player.getUUID());
    }

    public static void onServerStopped(MinecraftServer server) {
        LAST_SENT.clear();
    }

    /** The last payload sent to a player (tests, debug). */
    public static WantedSyncPayload lastSent(UUID player) {
        return LAST_SENT.get(player);
    }
}

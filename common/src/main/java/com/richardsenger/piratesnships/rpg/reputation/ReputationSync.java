package com.richardsenger.piratesnships.rpg.reputation;

import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends each online player their own {@link ReputationSyncPayload} (the {@code law.sync.WantedSync} pattern): at
 * login, then every {@code reputation.sync_interval_ticks} only if a shown score changed. Only players in the server's
 * player list are synced, so GameTest players without a negotiated connection never receive anything.
 */
public final class ReputationSync {

    private static final Map<UUID, ReputationSyncPayload> LAST_SENT = new ConcurrentHashMap<>();

    private ReputationSync() {
    }

    /** The payload {@code player} should have now. */
    public static ReputationSyncPayload snapshot(Player player) {
        ReputationRecord r = Reputation.record(player);
        return new ReputationSyncPayload(r.display(Faction.NAVY), r.display(Faction.PIRATES), r.display(Faction.VILLAGERS));
    }

    /** Sends unconditionally (login). */
    public static void sendNow(ServerPlayer player) {
        ReputationSyncPayload p = snapshot(player);
        LAST_SENT.put(player.getUUID(), p);
        Services.NETWORK.sendToPlayer(player, p);
    }

    /** Sends if the shown scores differ from the last ones sent. Returns whether it sent. */
    public static boolean sendIfChanged(ServerPlayer player) {
        ReputationSyncPayload p = snapshot(player);
        if (Objects.equals(LAST_SENT.get(player.getUUID()), p)) return false;
        LAST_SENT.put(player.getUUID(), p);
        Services.NETWORK.sendToPlayer(player, p);
        return true;
    }

    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % ReputationConfig.SYNC_INTERVAL_TICKS.get() != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendIfChanged(player);
    }

    public static void onLogout(ServerPlayer player) {
        LAST_SENT.remove(player.getUUID());
    }

    public static void onServerStopped(MinecraftServer server) {
        LAST_SENT.clear();
    }
}

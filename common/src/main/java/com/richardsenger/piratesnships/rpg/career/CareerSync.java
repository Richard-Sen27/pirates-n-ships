package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends each online player their own {@link CareerSyncPayload} (the {@code ReputationSync} pattern): at login and
 * whenever {@link Careers#store} changes what the client sees. Only players in the server's player list are synced,
 * so GameTest players without a negotiated connection never receive anything.
 */
public final class CareerSync {

    private static final Map<UUID, CareerSyncPayload> LAST_SENT = new ConcurrentHashMap<>();

    private CareerSync() {
    }

    static boolean online(ServerPlayer player) {
        return player.server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    public static void sendNow(ServerPlayer player) {
        CareerSyncPayload p = CareerSyncPayload.of(Careers.record(player));
        LAST_SENT.put(player.getUUID(), p);
        if (online(player)) Services.NETWORK.sendToPlayer(player, p);
    }

    /** Sends if what the client sees differs from the last payload sent. */
    public static boolean sendIfChanged(ServerPlayer player, CareerRecord record) {
        if (!online(player)) return false;
        CareerSyncPayload p = CareerSyncPayload.of(record);
        if (Objects.equals(LAST_SENT.get(player.getUUID()), p)) return false;
        LAST_SENT.put(player.getUUID(), p);
        Services.NETWORK.sendToPlayer(player, p);
        return true;
    }

    public static void onLogout(ServerPlayer player) {
        LAST_SENT.remove(player.getUUID());
    }

    public static void clear() {
        LAST_SENT.clear();
    }
}

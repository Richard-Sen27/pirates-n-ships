package com.richardsenger.piratesnships.sailing.wind;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Sends the wind to players: on login and every {@code wind.sync_interval_ticks}. */
public final class WindSync {

    private WindSync() {
    }

    public static void registerPayloads() {
        Services.NETWORK.registerToClient(WindSyncPayload.TYPE, WindSyncPayload.CODEC,
                (payload, player) -> ClientWind.accept(payload, player.level().getGameTime()));
    }

    public static void onServerTick(MinecraftServer server) {
        int interval = SailingConfig.SYNC_INTERVAL.get();
        if (server.getTickCount() % interval != 0) {
            return;
        }
        WindParams params = SailingConfig.windParams();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, params, interval);
        }
    }

    public static void onLogin(ServerPlayer player) {
        send(player, SailingConfig.windParams(), SailingConfig.SYNC_INTERVAL.get());
    }

    private static void send(ServerPlayer player, WindParams params, int interval) {
        WindSample s = WindService.sample(player.serverLevel(), player.position(), params);
        Services.NETWORK.sendToPlayer(player, WindSyncPayload.of(s, interval));
    }
}

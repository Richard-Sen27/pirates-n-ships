package com.richardsenger.piratesnships.core.data;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;

/** Wires {@link DefinitionType} loading and client sync into the game. Called by {@code CoreModule} only. */
public final class DefinitionLoading {

    private DefinitionLoading() {
    }

    public static void registerPayloads() {
        Services.NETWORK.registerToClient(DefinitionSyncPayload.TYPE, DefinitionSyncPayload.STREAM_CODEC,
                (payload, player) -> payload.applyOnClient());
    }

    public static void registerEvents() {
        CommonEvents.ADD_RELOAD_LISTENERS.register((sink, registries) -> sink.accept(new DefinitionReloadListener(registries)));
        CommonEvents.DATAPACK_SYNC.register((player, joined) -> {
            for (DefinitionType<?> type : DefinitionType.all()) {
                if (type.isSynced()) Services.NETWORK.sendToPlayer(player, DefinitionSyncPayload.of(type));
            }
        });
        CommonEvents.SERVER_STOPPED.register(server -> DefinitionType.all().forEach(DefinitionType::clearServer));
    }

    /**
     * Empties the client store of every definition type. {@code core.client.CoreClient} calls this on
     * {@code ClientEvents.CLIENT_DISCONNECT}, so a stale server's entries never leak into the next world. Listeners are not called.
     */
    public static void clearClientStores() {
        DefinitionType.all().forEach(DefinitionType::clearClient);
    }
}

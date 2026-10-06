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
}

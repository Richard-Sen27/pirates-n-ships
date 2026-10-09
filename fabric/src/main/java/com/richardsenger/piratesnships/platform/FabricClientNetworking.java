package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.platform.network.PayloadHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The physical client's half of {@link FabricNetworkHelper}. Only reached on the physical client: the classes of
 * {@link ClientPlayNetworking} are missing on a dedicated server. Client receivers run on the client main thread
 * (Fabric API's contract for play payloads).
 */
final class FabricClientNetworking {

    private FabricClientNetworking() {
    }

    static <T extends CustomPacketPayload> void registerReceiver(CustomPacketPayload.Type<T> type, PayloadHandler<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.handle(payload, context.player()));
    }

    static void send(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}

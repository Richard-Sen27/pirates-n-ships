package com.richardsenger.piratesnships.combat.melee.net;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.network.PayloadHandler;

/**
 * Payload registration of the melee module. The client handler of {@link MeleeStatePayload} is installed by the
 * client side ({@code combat.melee.client.MeleeClient.init()}) through {@link #setClientReceiver}, so this class
 * never touches client classes and is safe on a dedicated server.
 */
public final class MeleeNet {

    private static volatile PayloadHandler<MeleeStatePayload> clientReceiver = (payload, player) -> { };

    private MeleeNet() {
    }

    /** Called from {@code MeleeModule.registerPayloads()}. */
    public static void registerPayloads() {
        Services.NETWORK.registerToServer(MeleeActionPayload.TYPE, MeleeActionPayload.CODEC, MeleeActions::handle);
        Services.NETWORK.registerToClient(MeleeStatePayload.TYPE, MeleeStatePayload.CODEC, (p, player) -> clientReceiver.handle(p, player));
    }

    /** Physical client only: who handles received state payloads. */
    public static void setClientReceiver(PayloadHandler<MeleeStatePayload> receiver) {
        clientReceiver = receiver;
    }
}

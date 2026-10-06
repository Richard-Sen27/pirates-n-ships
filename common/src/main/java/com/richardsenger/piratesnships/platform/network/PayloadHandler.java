package com.richardsenger.piratesnships.platform.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/** Handles a received payload on the main thread of the receiving side. */
@FunctionalInterface
public interface PayloadHandler<T extends CustomPacketPayload> {

    /**
     * @param payload the decoded payload
     * @param player  on the server: the sending {@code ServerPlayer}; on the client: the local player
     */
    void handle(T payload, Player player);
}

package com.richardsenger.piratesnships.platform.services;

import com.richardsenger.piratesnships.platform.network.PayloadHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

/**
 * Payload registration and sending. Payload records, their {@link StreamCodec}s and handlers live in common.
 * Register from a module's {@code registerPayloads()}; handlers run on the main thread of the receiving side.
 */
public interface INetworkHelper {

    /** Registers a server → client payload. The handler runs on the client main thread. */
    <T extends CustomPacketPayload> void registerToClient(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadHandler<T> handler);

    /** Registers a client → server payload. The handler runs on the server main thread. */
    <T extends CustomPacketPayload> void registerToServer(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadHandler<T> handler);

    /** Client only: sends a payload to the server. */
    void sendToServer(CustomPacketPayload payload);

    /** Sends a payload to one player. */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Sends a payload to every player tracking the entity (and the entity itself, if it is a player). */
    void sendToTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload);

    /** Sends a payload to every player that has the chunk loaded. */
    void sendToTrackingChunk(ServerLevel level, ChunkPos chunk, CustomPacketPayload payload);

    /** Sends a payload to every player on the server. */
    void sendToAll(CustomPacketPayload payload);
}

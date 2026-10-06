package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.platform.network.PayloadHandler;
import com.richardsenger.piratesnships.platform.services.INetworkHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

/** TODO Fabric port (milestone 22): PayloadTypeRegistry + ServerPlayNetworking / ClientPlayNetworking. */
public class FabricNetworkHelper implements INetworkHelper {

    private static UnsupportedOperationException todo() {
        return new UnsupportedOperationException("Fabric port: milestone 22");
    }

    @Override public <T extends CustomPacketPayload> void registerToClient(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadHandler<T> handler) { throw todo(); }
    @Override public <T extends CustomPacketPayload> void registerToServer(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadHandler<T> handler) { throw todo(); }
    @Override public void sendToServer(CustomPacketPayload payload) { throw todo(); }
    @Override public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) { throw todo(); }
    @Override public void sendToTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) { throw todo(); }
    @Override public void sendToTrackingChunk(ServerLevel level, ChunkPos chunk, CustomPacketPayload payload) { throw todo(); }
    @Override public void sendToAll(CustomPacketPayload payload) { throw todo(); }
}

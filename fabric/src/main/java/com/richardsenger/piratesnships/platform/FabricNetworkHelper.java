package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.platform.network.PayloadHandler;
import com.richardsenger.piratesnships.platform.services.INetworkHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Fabric API networking for our vanilla {@link CustomPacketPayload}s. Both directions register the codec in
 * {@link PayloadTypeRegistry} on both sides (the sender encodes, the receiver decodes); the server registers its
 * receivers with {@link ServerPlayNetworking}, the physical client its own with {@code ClientPlayNetworking} through
 * {@link FabricClientNetworking} (a separate class: the client networking classes do not exist on a dedicated server).
 * Fabric runs play receivers on the main thread of the receiving side, as {@link PayloadHandler} requires.
 *
 * <p>Unlike NeoForge, Fabric negotiates no protocol version: a client and server with different payload formats fail
 * when decoding, not at login.
 */
public final class FabricNetworkHelper implements INetworkHelper {

    private volatile @Nullable MinecraftServer server;

    @Override
    public synchronized <T extends CustomPacketPayload> void registerToClient(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadHandler<T> handler) {
        PayloadTypeRegistry.playS2C().register(type, codec);
        if (Services.PLATFORM.isPhysicalClient()) FabricClientNetworking.registerReceiver(type, handler);
    }

    @Override
    public synchronized <T extends CustomPacketPayload> void registerToServer(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadHandler<T> handler) {
        PayloadTypeRegistry.playC2S().register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.handle(payload, context.player()));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        FabricClientNetworking.send(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) {
        Packet<?> packet = ServerPlayNetworking.createS2CPacket(payload);
        Collection<ServerPlayer> tracking = PlayerLookup.tracking(entity);
        for (ServerPlayer p : tracking) p.connection.send(packet);
        if (entity instanceof ServerPlayer self && !tracking.contains(self)) self.connection.send(packet);
    }

    @Override
    public void sendToTrackingChunk(ServerLevel level, ChunkPos chunk, CustomPacketPayload payload) {
        send(PlayerLookup.tracking(level, chunk), payload);
    }

    @Override
    public void sendToAll(CustomPacketPayload payload) {
        MinecraftServer s = server;
        if (s != null) send(PlayerLookup.all(s), payload);
    }

    private static void send(Collection<ServerPlayer> players, CustomPacketPayload payload) {
        if (players.isEmpty()) return;
        Packet<?> packet = ServerPlayNetworking.createS2CPacket(payload);
        for (ServerPlayer p : players) p.connection.send(packet);
    }

    /** Called once by the entry point: remembers the running server for {@link #sendToAll}. */
    public void attach() {
        ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            if (server == s) server = null;
        });
    }
}

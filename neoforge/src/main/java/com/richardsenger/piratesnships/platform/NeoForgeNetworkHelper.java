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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Buffers common payload registrations and replays them in {@link RegisterPayloadHandlersEvent}. */
public final class NeoForgeNetworkHelper implements INetworkHelper {

    /** Bump when a payload's wire format changes. */
    private static final String PROTOCOL_VERSION = "1";

    private final List<Consumer<PayloadRegistrar>> registrations = new ArrayList<>();

    @Override
    public synchronized <T extends CustomPacketPayload> void registerToClient(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadHandler<T> handler) {
        registrations.add(r -> r.playToClient(type, codec, (payload, ctx) -> handler.handle(payload, ctx.player())));
    }

    @Override
    public synchronized <T extends CustomPacketPayload> void registerToServer(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadHandler<T> handler) {
        registrations.add(r -> r.playToServer(type, codec, (payload, ctx) -> handler.handle(payload, ctx.player())));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }

    @Override
    public void sendToTrackingChunk(ServerLevel level, ChunkPos chunk, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingChunk(level, chunk, payload);
    }

    @Override
    public void sendToAll(CustomPacketPayload payload) {
        PacketDistributor.sendToAllPlayers(payload);
    }

    /** Called once by the entry point. Handlers run on the main thread (NeoForge default). */
    public void attach(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
            synchronized (this) {
                registrations.forEach(r -> r.accept(registrar));
            }
        });
    }
}

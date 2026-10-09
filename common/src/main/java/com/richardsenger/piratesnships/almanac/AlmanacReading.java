package com.richardsenger.piratesnships.almanac;

import com.richardsenger.piratesnships.almanac.client.AlmanacClient;
import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of reading the Captain's Almanac (ALM1, docs/design.md §3.4). Reading the placed tome
 * ({@link #readTome}) or the book in hand ({@link #readInHand}) sends {@link AlmanacPayloads.Open} to the reader, whose
 * client opens the almanac from {@link AlmanacClient#open}; the tome also lies open while read. When the reader is
 * done the client sends {@link AlmanacPayloads.Close} ({@link #close}). With {@code almanac.enabled} off nothing is
 * read. A GameTest player has a mock connection, so tests record its payloads instead ({@link #record}).
 */
public final class AlmanacReading {

    private static final Map<UUID, List<CustomPacketPayload>> RECORDINGS = new ConcurrentHashMap<>();

    private AlmanacReading() {
    }

    public static void registerPayloads() {
        Services.NETWORK.registerToClient(AlmanacPayloads.Open.TYPE, AlmanacPayloads.Open.CODEC,
                (p, player) -> AlmanacClient.open(p));
        Services.NETWORK.registerToServer(AlmanacPayloads.Close.TYPE, AlmanacPayloads.Close.CODEC,
                (p, player) -> close((ServerPlayer) player, p));
    }

    /** {@code player} right-clicked the tome at {@code pos}: it opens and the client opens the almanac. */
    public static boolean readTome(ServerPlayer player, BlockPos pos) {
        if (!AlmanacConfig.ENABLED.get()) return false;
        if (!(player.level().getBlockEntity(pos) instanceof AlmanacBlockEntity tome)) return false;
        tome.startReading(player, player.level().getGameTime());
        deliver(player, new AlmanacPayloads.Open(Optional.of(pos.immutable())));
        return true;
    }

    /** {@code player} used the book in hand: the client opens the almanac. */
    public static boolean readInHand(ServerPlayer player) {
        if (!AlmanacConfig.ENABLED.get()) return false;
        deliver(player, new AlmanacPayloads.Open(Optional.empty()));
        return true;
    }

    /** {@code player} closed the almanac; a placed tome they read closes once nobody reads it. */
    public static void close(ServerPlayer player, AlmanacPayloads.Close close) {
        close.tome().ifPresent(pos -> {
            if (player.level().isLoaded(pos) && player.level().getBlockEntity(pos) instanceof AlmanacBlockEntity tome) {
                tome.stopReading(player.getUUID());
            }
        });
    }

    public static void onServerStopped(MinecraftServer server) {
        RECORDINGS.clear();
    }

    private static void deliver(ServerPlayer player, CustomPacketPayload payload) {
        List<CustomPacketPayload> rec = RECORDINGS.get(player.getUUID());
        if (rec != null) {
            rec.add(payload);
            return;
        }
        Services.NETWORK.sendToPlayer(player, payload);
    }

    // --- Test support -------------------------------------------------------------------------------------------

    /** GameTests: record the payloads for {@code player} instead of sending them (a mock connection has no channels). */
    public static List<CustomPacketPayload> record(UUID player) {
        return RECORDINGS.computeIfAbsent(player, id -> Collections.synchronizedList(new ArrayList<>()));
    }

    public static void stopRecording(UUID player) {
        RECORDINGS.remove(player);
    }
}

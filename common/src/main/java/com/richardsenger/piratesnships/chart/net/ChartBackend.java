package com.richardsenger.piratesnships.chart.net;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.chart.ChartConfig;
import com.richardsenger.piratesnships.chart.ChartContent;
import com.richardsenger.piratesnships.chart.ChartService;
import com.richardsenger.piratesnships.chart.client.ClientChart;
import com.richardsenger.piratesnships.chart.data.ChartData;
import com.richardsenger.piratesnships.chart.data.ChartRegion;
import com.richardsenger.piratesnships.chart.data.MarkerRules;
import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Network side of the chart (work package MAP1). A player who opens the chart (the item, or the key with
 * {@code chart.open_without_item}) gets a session: the client sends its viewport, and every tick the server sends
 * up to {@link #REGIONS_PER_TICK} regions of the viewport the client has not seen in their current version (so a
 * chart that fills while it is open updates live). What was sent is remembered per player until logout, matching the
 * client's cache, which lives until it leaves the server. Once a second, with {@code chart.show_other_players}, the
 * open chart gets the other players in the overworld within the server's view distance. Marker edits are checked by
 * {@link MarkerRules} and answered with the new marker list.
 */
public final class ChartBackend {

    public static final String MSG = "message." + Constants.MOD_ID + ".chart.";
    public static final int REGIONS_PER_TICK = 4;
    private static final int OTHERS_INTERVAL = 20;

    private record View(int cx, int cz, int radius) {
    }

    private static final class Session {
        final ServerPlayer player;
        volatile View view;

        Session(ServerPlayer player) {
            this.player = player;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<Long, Long>> SENT = new ConcurrentHashMap<>();
    private static final Map<UUID, List<CustomPacketPayload>> RECORDINGS = new ConcurrentHashMap<>();

    private ChartBackend() {
    }

    public static void registerPayloads() {
        Services.NETWORK.registerToServer(ChartViewPayload.TYPE, ChartViewPayload.CODEC, (p, player) -> handleView((ServerPlayer) player, p));
        Services.NETWORK.registerToServer(ChartMarkerPayload.TYPE, ChartMarkerPayload.CODEC, (p, player) -> handleMarker((ServerPlayer) player, p));
        Services.NETWORK.registerToServer(ChartSimplePayloads.RequestOpen.TYPE, ChartSimplePayloads.RequestOpen.CODEC, (p, player) -> handleRequestOpen((ServerPlayer) player));
        Services.NETWORK.registerToServer(ChartSimplePayloads.Close.TYPE, ChartSimplePayloads.Close.CODEC, (p, player) -> close((ServerPlayer) player));
        Services.NETWORK.registerToClient(ChartSettingsPayload.TYPE, ChartSettingsPayload.CODEC, (p, player) -> ClientChart.acceptSettings(p.settings()));
        Services.NETWORK.registerToClient(ChartOpenPayload.TYPE, ChartOpenPayload.CODEC, (p, player) -> ClientChart.open(p));
        Services.NETWORK.registerToClient(ChartRegionPayload.TYPE, ChartRegionPayload.CODEC, (p, player) -> ClientChart.acceptRegion(p));
        Services.NETWORK.registerToClient(ChartStatePayload.TYPE, ChartStatePayload.CODEC, (p, player) -> ClientChart.acceptState(p));
    }

    /** The settings the client is told about. */
    public static ChartSettings settings() {
        return new ChartSettings(ChartConfig.ENABLED.get(), ChartConfig.OPEN_WITHOUT_ITEM.get(), ChartConfig.SHOW_OTHER_PLAYERS.get(),
                ChartConfig.CELL_BLOCKS.get(), ChartConfig.MAX_MARKERS.get());
    }

    // --- lifecycle -----------------------------------------------------------------------------------------------

    /** {@code DATAPACK_SYNC}: tell the client the settings; a new login starts with an empty client cache. */
    public static void onDatapackSync(ServerPlayer player, boolean joined) {
        if (joined) SENT.remove(player.getUUID());
        deliver(player, new ChartSettingsPayload(settings()));
    }

    public static void onLogout(Player player) {
        SESSIONS.remove(player.getUUID());
        SENT.remove(player.getUUID());
    }

    public static void onServerStopped(MinecraftServer server) {
        SESSIONS.clear();
        SENT.clear();
        RECORDINGS.clear();
    }

    // --- opening -------------------------------------------------------------------------------------------------

    /** Opens the chart screen of {@code player} (the item was used, or the key was allowed). */
    public static boolean open(ServerPlayer player) {
        if (!ChartConfig.ENABLED.get()) {
            player.displayClientMessage(Component.translatable(MSG + "disabled"), true);
            return false;
        }
        Session s = new Session(player);
        Session old = SESSIONS.put(player.getUUID(), s);
        if (old != null) s.view = old.view;
        ChartData data = ChartService.data(player);
        deliver(player, new ChartOpenPayload(settings(), data.markers()));
        deliver(player, new ChartStatePayload(data.markers(), others(player), Optional.empty()));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 0.9f);
        return true;
    }

    /** Whether the player may open the chart without using the item: the option, or a chart in either hand. */
    public static boolean mayOpenWithKey(Player player) {
        return ChartConfig.OPEN_WITHOUT_ITEM.get()
                || player.getMainHandItem().is(ChartContent.CHART.get()) || player.getOffhandItem().is(ChartContent.CHART.get());
    }

    public static boolean handleRequestOpen(ServerPlayer player) {
        if (!mayOpenWithKey(player)) {
            player.displayClientMessage(Component.translatable(MSG + "needs_item"), true);
            return false;
        }
        return open(player);
    }

    public static void close(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    public static boolean isOpen(UUID player) {
        return SESSIONS.containsKey(player);
    }

    // --- streaming -----------------------------------------------------------------------------------------------

    public static void handleView(ServerPlayer player, ChartViewPayload view) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        s.view = new View(view.centerCx(), view.centerCz(), view.clampedRadius());
        sendPending(s, REGIONS_PER_TICK);
    }

    public static void onServerTick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        boolean others = ChartConfig.SHOW_OTHER_PLAYERS.get() && server.getTickCount() % OTHERS_INTERVAL == 0;
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            Session s = SESSIONS.get(id);
            if (s == null) continue;
            if (s.player.isRemoved() || s.player.hasDisconnected() || !ChartConfig.ENABLED.get()) {
                SESSIONS.remove(id);
                continue;
            }
            sendPending(s, REGIONS_PER_TICK);
            if (others) {
                deliver(s.player, new ChartStatePayload(ChartService.data(s.player).markers(), others(s.player), Optional.empty()));
            }
        }
    }

    /** Sends up to {@code limit} regions of the session's viewport the client has not seen; returns how many. */
    public static int sendPending(UUID player, int limit) {
        Session s = SESSIONS.get(player);
        return s == null ? 0 : sendPending(s, limit);
    }

    private static int sendPending(Session s, int limit) {
        View v = s.view;
        if (v == null) return 0;
        ChartData data = ChartService.data(s.player);
        Map<Long, Long> sent = SENT.computeIfAbsent(s.player.getUUID(), k -> Collections.synchronizedMap(new HashMap<>()));
        List<ChartRegion> pending = ChartStreaming.pending(data.regions(), sent, v.cx(), v.cz(), v.radius(), limit);
        for (ChartRegion r : pending) {
            deliver(s.player, ChartRegionPayload.of(r));
            sent.put(r.key(), r.version());
        }
        return pending.size();
    }

    /** Other players on the chart of {@code viewer}: in the overworld, within view distance, not spectators. */
    public static List<ChartStatePayload.OtherPlayer> others(ServerPlayer viewer) {
        if (!ChartConfig.SHOW_OTHER_PLAYERS.get() || !ChartService.charted(viewer.level())) return List.of();
        double range = viewer.server.getPlayerList().getViewDistance() * 16.0;
        List<ChartStatePayload.OtherPlayer> out = new ArrayList<>();
        for (ServerPlayer p : viewer.server.getPlayerList().getPlayers()) {
            if (p == viewer || p.isSpectator() || p.level() != viewer.level()) continue;
            double dx = p.getX() - viewer.getX();
            double dz = p.getZ() - viewer.getZ();
            if (dx * dx + dz * dz > range * range) continue;
            out.add(new ChartStatePayload.OtherPlayer(p.getGameProfile().getName(), p.getBlockX(), p.getBlockZ(), p.getYRot()));
            if (out.size() >= ChartStatePayload.MAX_OTHERS) break;
        }
        return out;
    }

    // --- markers -------------------------------------------------------------------------------------------------

    /** Applies a marker request; returns the outcome (also sent back as a state payload). */
    public static MarkerRules.Outcome handleMarker(ServerPlayer player, ChartMarkerPayload request) {
        ChartData data = ChartService.data(player);
        MarkerRules.Outcome outcome;
        if (!ChartConfig.ENABLED.get()) {
            outcome = new MarkerRules.Outcome(data, MarkerRules.Refusal.DISABLED, -1);
        } else {
            outcome = switch (request.action()) {
                case ADD -> MarkerRules.add(data, request.x(), request.z(), request.icon(), request.name(), ChartConfig.MAX_MARKERS.get());
                case EDIT -> MarkerRules.edit(data, request.id(), request.x(), request.z(), request.icon(), request.name());
                case REMOVE -> MarkerRules.remove(data, request.id());
            };
        }
        if (outcome.ok()) ChartService.setData(player, outcome.data());
        Optional<String> refusal = outcome.ok() ? Optional.empty() : Optional.of(MSG + "refused." + outcome.refusal().key());
        deliver(player, new ChartStatePayload(outcome.data().markers(), others(player), refusal));
        return outcome;
    }

    // --- delivery and test support -------------------------------------------------------------------------------

    private static void deliver(ServerPlayer player, CustomPacketPayload payload) {
        List<CustomPacketPayload> rec = RECORDINGS.get(player.getUUID());
        if (rec != null) {
            // A GameTest player has a mock connection without negotiated channels: record instead of sending
            rec.add(payload);
            return;
        }
        Services.NETWORK.sendToPlayer(player, payload);
    }

    /** GameTests: every chart payload for {@code player} goes into the returned list instead of the network. */
    public static List<CustomPacketPayload> record(UUID player) {
        List<CustomPacketPayload> list = Collections.synchronizedList(new ArrayList<>());
        RECORDINGS.put(player, list);
        return list;
    }

    public static void stopRecording(UUID player) {
        RECORDINGS.remove(player);
        SESSIONS.remove(player);
        SENT.remove(player);
    }
}

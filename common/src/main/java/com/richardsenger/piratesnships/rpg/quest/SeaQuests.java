package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.rpg.career.ShipPrizes;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.lane.Lanes;
import com.richardsenger.piratesnships.worldsim.materialize.Materializer;
import com.richardsenger.piratesnships.worldsim.materialize.RouteMath;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageEndings;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the sea quests' progress comes from (QST2, docs/design.md §15):
 * <ul>
 *   <li>{@link #onEnding} ({@code VoyageEndings.onEnding}, WS3b): a SUNK ending credits the last player shooter within
 *       {@code shooter_memory_ticks} (from {@code CannonShipHits}), CAPTURED the capturing player, PLUNDERED the player
 *       aboard who took the cargo; the player's quests hear {@link QuestEvent.ShipDefeated}, and a pirate ship sunk or
 *       captured pays letter-of-marque prize money ({@link ShipPrizes}). The voyage's own deeds still fire in WS3b.</li>
 *   <li>{@link #onVoyageEnd} ({@code Voyages.onEnd}): an escorted convoy that arrived or was lost
 *       ({@link QuestEvent.VoyageEnded}); remembered for {@link #pollEscort}, so a player offline at the time still
 *       hears of it.</li>
 *   <li>{@link #pollEscort} (from {@link QuestTracker#pollAt}, every {@code quests.poll_ticks}): the player within
 *       {@code quests.escort_radius} of the convoy (the real ship's centre, else the record's position) counts the leg
 *       it sails ({@link QuestEvent.EscortSeen}).</li>
 *   <li>{@link #pollCharting} (same poll, QST2b): an escort accepted before its lane was charted sails once the lane
 *       is ready, or is called off when the lane fails or {@code escort_chart_timeout_ticks} pass.</li>
 * </ul>
 * The endings carry players by id; {@link #player} finds them online (or, in GameTests, among the stand-ins).
 */
public final class SeaQuests {

    private static volatile MinecraftServer server;
    /** GameTests: server players that are not in the player list, by id. */
    private static final Map<UUID, ServerPlayer> STAND_INS = new ConcurrentHashMap<>();
    /** How recently ended convoys ended, for escorts whose player was offline then. */
    private static final Map<UUID, VoyageEnd> ENDED = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, VoyageEnd> eldest) {
            return size() > 512;
        }
    });

    private SeaQuests() {
    }

    public static void onServerStarted(MinecraftServer s) {
        server = s;
    }

    public static void onServerStopped() {
        server = null;
        STAND_INS.clear();
        ENDED.clear();
        ShipPrizes.clear();
    }

    /** GameTests: lets the endings find {@code player} by id although it is not in the player list. */
    public static void standIn(ServerPlayer player) {
        STAND_INS.put(player.getUUID(), player);
    }

    public static void dropStandIn(UUID id) {
        STAND_INS.remove(id);
    }

    /** The online player {@code id} (or a GameTest stand-in). */
    public static Optional<ServerPlayer> player(MinecraftServer s, UUID id) {
        ServerPlayer p = s.getPlayerList().getPlayer(id);
        return Optional.ofNullable(p != null ? p : STAND_INS.get(id));
    }

    private static List<ServerPlayer> players(MinecraftServer s) {
        List<ServerPlayer> out = new ArrayList<>(s.getPlayerList().getPlayers());
        for (ServerPlayer p : STAND_INS.values()) if (!out.contains(p)) out.add(p);
        return out;
    }

    /** How an ending counts for the quests. */
    static QuestEvent.How how(VoyageEndings.Outcome outcome) {
        return switch (outcome) {
            case SUNK -> QuestEvent.How.SUNK;
            case CAPTURED -> QuestEvent.How.CAPTURED;
            case PLUNDERED -> QuestEvent.How.PLUNDERED;
        };
    }

    /** {@code VoyageEndings.onEnding}: credits the player behind a sinking, a capture or a plunder. */
    public static void onEnding(VoyageEndings.Ending e) {
        MinecraftServer s = server;
        if (s == null || e.player().isEmpty()) return;
        Optional<ServerPlayer> p = player(s, e.player().get());
        if (p.isEmpty()) return;
        Voyage v = e.voyage();
        QuestEvent.How how = how(e.outcome());
        ShipPrizes.award(p.get(), v.id(), v.faction(), how != QuestEvent.How.PLUNDERED);
        Quests.apply(p.get(), new QuestEvent.ShipDefeated(v.id(), v.kind(), v.faction(), how));
    }

    /** {@code Voyages.onEnd}: an escorted convoy arrived or was lost. */
    public static void onVoyageEnd(MinecraftServer s, Voyage v, VoyageEnd reason) {
        if (v.kind() != VoyageKind.CONVOY) return;
        ENDED.put(v.id(), reason);
        for (ServerPlayer p : players(s)) {
            for (Quest q : Quests.log(p).active()) {
                if (q.target() instanceof QuestTarget.Escort t && t.follows(v.id())) {
                    ended(p, t, reason);
                    break;
                }
            }
        }
    }

    private static List<Quest> ended(ServerPlayer p, QuestTarget.Escort t, VoyageEnd reason) {
        List<Quest> changed = Quests.apply(p, new QuestEvent.VoyageEnded(t.voyage().orElseThrow(), reason));
        for (Quest q : changed) {
            if (q.state() != QuestState.FAILED) continue;
            p.displayClientMessage(Component.translatable(reason == VoyageEnd.ARRIVED ? QuestText.ESCORT_ALONE : QuestText.ESCORT_LOST,
                    t.name()), false);
        }
        return changed;
    }

    /**
     * Checks the player's charting escort {@code q} now (QST2b): its lane ready sails the convoy
     * ({@link Quests#sail}, the quest becomes active); the lane failed, or not ready {@code escort_chart_timeout_ticks}
     * after accepting, calls the escort off: it leaves the log without counting as failed (escorts take no deposit, so
     * nothing is owed) and the player is told. A pair lost from the queue (a restart) is queued again. Returns the
     * quests that changed, as they are now (a cancelled one as FAILED).
     */
    public static List<Quest> pollCharting(ServerPlayer player, Quest q) {
        if (!QuestRules.charting(q)) return List.of();
        QuestTarget.Escort t = (QuestTarget.Escort) q.target();
        MinecraftServer s = player.server;
        long now = s.overworld().getGameTime();
        boolean ready = Lanes.between(s, q.port(), t.destination()).isPresent();
        Lanes.Status status = ready ? Lanes.Status.READY : Lanes.status(s, q.port(), t.destination());
        boolean failed = status == Lanes.Status.FAILED || status == Lanes.Status.UNKNOWN_PORT;
        QuestRules.ChartStep step = QuestRules.chartStep(ready, failed, t.chartingSince().orElse(now), now,
                QuestConfig.ESCORT_CHART_TIMEOUT_TICKS.get());
        switch (step) {
            case WAIT -> {
                return List.of();
            }
            case SAIL -> {
                Optional<Quest> sailed = Quests.sail(player, q);
                if (sailed.isPresent()) {
                    Quests.store(player, Quests.log(player).with(sailed.get()));
                    return List.of(sailed.get());
                }
                failed = true;
            }
            case CANCEL -> {
            }
        }
        Quests.store(player, Quests.log(player).without(q.id()));
        player.displayClientMessage(Component.translatable(failed ? QuestText.ESCORT_NO_ROUTE : QuestText.ESCORT_CHART_TIMEOUT,
                QuestText.portName(t.destination())), false);
        return List.of(q.withState(QuestState.FAILED));
    }

    /**
     * Checks the player's escort {@code t} now: the convoy gone ends the quest by how it ended (lost if unknown), else
     * the player within {@code escort_radius} of it counts its current leg. Returns the quests that changed.
     */
    public static List<Quest> pollEscort(ServerPlayer player, QuestTarget.Escort t) {
        if (t.voyage().isEmpty()) return List.of();
        MinecraftServer s = player.server;
        UUID id = t.voyage().get();
        Optional<Voyage> voyage = Voyages.get(s, id);
        if (voyage.isEmpty()) return ended(player, t, ENDED.getOrDefault(id, VoyageEnd.LOST));
        Voyage v = voyage.get();
        ServerLevel level = Materializer.levelOf(s, v);
        if (player.level() != level || !player.isAlive() || player.isSpectator()) return List.of();
        double x, z;
        int leg;
        ShipBody ship = v.state() == Voyage.State.MATERIALISED && v.shipId().isPresent() ? SableShips.byId(level, v.shipId().get()) : null;
        if (ship != null) {
            Vec3 c = Materializer.centre(ship);
            x = c.x;
            z = c.z;
            leg = Lane.positionAlong(v.waypoints(), RouteMath.project(v.waypoints(), x, z, v.progress())).leg();
        } else {
            Lane.Position pos = v.position();
            x = pos.x();
            z = pos.z();
            leg = pos.leg();
        }
        if (Math.hypot(player.getX() - x, player.getZ() - z) > QuestConfig.ESCORT_RADIUS.get()) return List.of();
        return Quests.apply(player, new QuestEvent.EscortSeen(id, leg));
    }
}

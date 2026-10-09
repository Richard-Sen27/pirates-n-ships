package com.richardsenger.piratesnships.combat.boarding.order;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tells each player where his whistle reads "Back aboard" (BRD3, {@link BoardingStatePayload}): for every party a
 * player sent out (not an NPC captain's), its captain, the owner of its ship and the players standing on that ship see
 * "Back aboard" on the party's ship and on the ship it boards. Looked at every {@link #INTERVAL} ticks; a player is
 * sent the set only when it changed.
 */
public final class BoardingSync {

    static final int INTERVAL = 10;

    /** What each player was sent last. */
    private static final Map<UUID, Set<UUID>> SENT = new HashMap<>();

    private BoardingSync() {
    }

    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL != 0) return;
        if (SENT.isEmpty() && !BoardingParties.any()) return;
        Map<UUID, Set<UUID>> want = new HashMap<>();
        ShipRegistry registry = ShipRegistry.get(server);
        for (BoardingParties.Party p : BoardingParties.parties()) {
            if (p.npc()) continue;
            List<UUID> who = new ArrayList<>();
            if (p.captain() != null) who.add(p.captain());
            registry.find(p.home()).flatMap(ShipData::owner).ifPresent(who::add);
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                if (BoardingFight.aboard(sp, p.home())) who.add(sp.getUUID());
            }
            for (UUID id : who) {
                Set<UUID> ships = want.computeIfAbsent(id, k -> new LinkedHashSet<>());
                ships.add(p.home());
                ships.add(p.target());
            }
        }
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            Set<UUID> now = want.getOrDefault(sp.getUUID(), Set.of());
            Set<UUID> before = Optional.ofNullable(SENT.get(sp.getUUID())).orElse(Set.of());
            if (now.equals(before)) continue;
            Services.NETWORK.sendToPlayer(sp, new BoardingStatePayload(new ArrayList<>(now)));
            if (now.isEmpty()) SENT.remove(sp.getUUID());
            else SENT.put(sp.getUUID(), now);
        }
        SENT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    public static void clear() {
        SENT.clear();
    }
}

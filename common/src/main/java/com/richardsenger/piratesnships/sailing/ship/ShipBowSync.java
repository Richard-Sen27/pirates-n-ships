package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends each ship's bow to every client that renders the ship (VIS1c): the sails need it to read the apparent wind off
 * the real bow (docs/design.md §4.8, §5.2). The bow is the sailing runtime's ({@link SailingRuntime#bow()}, derived from
 * the helm once and kept in the ship's user data), so it is sent for ships with a runtime, which every assembled or
 * loaded ship of ours gets within {@code scan_interval_ticks}.
 *
 * <p>Each level tick, per ship, the players Sable tracks it for ({@link ShipBody#trackingPlayers}) are compared with
 * those that already have its current bow: a new tracker (a fresh assembly, a player coming into range, logging in or
 * changing dimension) gets one {@link ShipBowPayload}, a changed bow goes to every tracker again, and a player who
 * stops tracking is forgotten, so he gets it again when he comes back. Nothing is sent while nothing changes.
 *
 * <p>Why a payload of its own: the ship status ({@code ShipStatusPayload}) reaches only players aboard, the wind sync
 * is per player, and the yard and cleat block entities are per sail and written from places that do not know the
 * runtime; Sable's own tracking set is the exact "who renders this ship".
 *
 * <p>An instance owns its bookkeeping, so GameTests run their own sender with a chosen tracker set and a recording
 * sink; the game uses {@link #INSTANCE}.
 */
public final class ShipBowSync {

    /** The game's sender, ticked from the sailing module after the sailing runtimes. */
    public static final ShipBowSync INSTANCE = new ShipBowSync();

    private final Map<UUID, Sent> sent = new HashMap<>();

    /** The bow last sent for one ship and the players that have it. */
    private static final class Sent {
        private BowFrame bow;
        private final Set<UUID> players = new HashSet<>();
    }

    public static void registerPayloads() {
        Services.NETWORK.registerToClient(ShipBowPayload.TYPE, ShipBowPayload.CODEC,
                (payload, player) -> ClientShipBows.INSTANCE.accept(payload));
    }

    /** Level tick end: the game's sender, to the real players Sable tracks each ship for. */
    public static void onLevelTick(ServerLevel level) {
        INSTANCE.tick(level, ShipBody::trackingPlayers, (uuid, payload) -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                Services.NETWORK.sendToPlayer(player, payload);
            }
        });
    }

    public static void onShipRemoved(ServerLevel level, UUID ship, boolean destroyed) {
        INSTANCE.forget(ship);
    }

    public static void onServerStopped() {
        INSTANCE.sent.clear();
    }

    /**
     * One tick of this sender for {@code level}: hands {@code sink} a payload for every (player, ship) pair whose player
     * tracks the ship ({@code trackers}) and does not have its current bow yet. Public for GameTests.
     */
    public void tick(ServerLevel level, Function<ShipBody, Collection<UUID>> trackers, BiConsumer<UUID, ShipBowPayload> sink) {
        for (SailingRuntime rt : SailingRuntimes.runtimes(level)) {
            ShipBody ship = SableShips.byId(level, rt.id());
            if (ship == null || ship.isRemoved()) {
                continue;
            }
            Sent s = sent.computeIfAbsent(rt.id(), id -> new Sent());
            if (!rt.bow().equals(s.bow)) {
                s.bow = rt.bow();
                s.players.clear(); // a changed bow goes to every tracker again
            }
            Collection<UUID> tracking = trackers.apply(ship);
            s.players.retainAll(tracking); // out of range: sent again on return
            ShipBowPayload payload = null;
            for (UUID player : tracking) {
                if (s.players.add(player)) {
                    if (payload == null) {
                        payload = ShipBowPayload.of(rt.id(), s.bow);
                    }
                    sink.accept(player, payload);
                }
            }
        }
    }

    /** Drops what was sent for {@code ship} (it is gone). */
    public void forget(UUID ship) {
        sent.remove(ship);
    }
}

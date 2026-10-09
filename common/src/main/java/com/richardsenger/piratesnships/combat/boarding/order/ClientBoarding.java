package com.richardsenger.piratesnships.combat.boarding.order;

import com.richardsenger.piratesnships.ship.hull.client.ClientShipStatus;
import com.richardsenger.piratesnships.ship.hull.net.ShipStatusPayload;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Client store of the last {@link BoardingStatePayload} (BRD3): on which ships the whistle's boarding entry and the ship
 * screen's button read "Back aboard". No client classes, so common code may hold it.
 */
public final class ClientBoarding {

    private static volatile Set<UUID> out = Set.of();

    private ClientBoarding() {
    }

    public static void accept(BoardingStatePayload payload) {
        out = Set.copyOf(payload.ships());
    }

    /** Whether the entry reads "Back aboard" on ship {@code ship} (null: not aboard a known ship). */
    public static boolean out(@Nullable UUID ship) {
        return ship != null && out.contains(ship);
    }

    /** Whether the entry reads "Back aboard" on the ship the player stands on (from the HUD's ship status). */
    public static boolean outHere() {
        ShipStatusPayload status = ClientShipStatus.shown();
        return status != null && out(status.ship());
    }

    /** Forgets the state (leaving a server). */
    public static void clear() {
        out = Set.of();
    }
}

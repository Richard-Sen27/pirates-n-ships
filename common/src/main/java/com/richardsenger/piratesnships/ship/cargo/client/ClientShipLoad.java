package com.richardsenger.piratesnships.ship.cargo.client;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.ship.cargo.ShipLoadPayload;
import com.richardsenger.piratesnships.trade.cargo.CargoWeight;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The load level of the ship whose helm the local player holds (CW1), as last sent by the server; shown by the helm
 * overlay after the rudder line. Cleared when the client leaves the world.
 */
public final class ClientShipLoad {

    private static volatile @Nullable CargoWeight.LoadLevel current;

    private ClientShipLoad() {
    }

    public static void init() {
        ClientEvents.CLIENT_DISCONNECT.register(mc -> current = null);
    }

    public static void onPayload(ShipLoadPayload payload) {
        current = payload.loadLevel();
    }

    public static @Nullable CargoWeight.LoadLevel current() {
        return current;
    }

    /** "Rudder 12° starboard · Laden", or the rudder line alone when no level is known. */
    public static Component withLoad(Component rudderLine) {
        CargoWeight.LoadLevel level = current;
        if (level == null) {
            return rudderLine;
        }
        return Component.empty().append(rudderLine).append(" · ").append(Component.translatable(level.translationKey()));
    }
}

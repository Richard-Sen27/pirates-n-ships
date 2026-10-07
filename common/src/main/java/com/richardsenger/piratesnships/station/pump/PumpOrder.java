package com.richardsenger.piratesnships.station.pump;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.station.order.CrewOrder;
import java.util.Locale;

/**
 * The order of the bilge pump station (docs/design.md §6): pump the bilge the pump reaches until it is dry. A
 * {@link CrewOrder}: the whistle's "Pump" entry and {@code /pirates crew order pump} give it.
 */
public enum PumpOrder implements CrewOrder {
    PUMP;

    @Override
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Translation key of the order name ("pump the bilge"). */
    @Override
    public String nameKey() {
        return "pump_order." + Constants.MOD_ID + "." + id();
    }

    /** Translation key of the crew's acknowledgement ("Aye, manning the pump!"). */
    @Override
    public String ackKey() {
        return "message." + Constants.MOD_ID + ".crew.ack." + id();
    }

    /** "The bilge is dry, captain." */
    @Override
    public String nothingToDoKey() {
        return "message." + Constants.MOD_ID + ".crew.bilge_dry";
    }

    /** "The pump won't draw, captain!" (pumps switched off, no compartment in reach). */
    @Override
    public String unableKey() {
        return "message." + Constants.MOD_ID + ".crew.pump_unable";
    }
}

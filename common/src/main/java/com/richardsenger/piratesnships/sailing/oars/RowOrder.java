package com.richardsenger.piratesnships.sailing.oars;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.station.order.CrewOrder;
import java.util.Locale;

/**
 * The order of the oar bench station (OAR1, docs/design.md §5.3b, §7.2): row. A {@link CrewOrder}, so a bench shows it
 * in the ship screen's station list; the whistle's "Row" / "Ship oars" toggle ({@code WhistleOrder#ROW}) does not post
 * it on the job board but gives it through {@link Rowing}, which also sends the crew without a bench to the rail.
 */
public enum RowOrder implements CrewOrder {
    ROW;

    @Override
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** "row". */
    @Override
    public String nameKey() {
        return "oar_order." + Constants.MOD_ID + "." + id();
    }

    /** Never said: rowing always has work. */
    @Override
    public String nothingToDoKey() {
        return "message." + Constants.MOD_ID + ".oars.nothing";
    }

    /** "Oars are off on this server" (the bench takes no order while {@code oars.enabled} is off). */
    @Override
    public String unableKey() {
        return "message." + Constants.MOD_ID + ".oars.disabled";
    }
}

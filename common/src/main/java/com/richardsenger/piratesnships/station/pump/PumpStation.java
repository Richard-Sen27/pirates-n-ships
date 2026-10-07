package com.richardsenger.piratesnships.station.pump;

import com.richardsenger.piratesnships.ship.hull.pump.BilgePumps;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.Stations;
import net.minecraft.server.level.ServerLevel;

/**
 * The bilge pump as a station (docs/design.md §4.5, §6). While a crew member carries out a {@link PumpOrder} the pump
 * works like a player holding its use key: the hull runtime asks {@link BilgePumps#crewOperating} every tick. One
 * order lasts until the bilge would be dry at the pump rate, at most {@link BilgePumps#CREW_BATCH_TICKS}; when it is
 * done and water is left (inflow through a breach), the crew member takes up the next order by itself, so a manned
 * pump keeps going until the bilge is dry. The block is {@code ship.hull.pump.BilgePumpBlock}.
 */
public final class PumpStation implements StationKind<PumpOrder> {

    public static final PumpStation INSTANCE = new PumpStation();

    private PumpStation() {
    }

    @Override
    public String id() {
        return "bilge_pump";
    }

    @Override
    public Class<PumpOrder> orderType() {
        return PumpOrder.class;
    }

    @Override
    public int durationTicks(ServerLevel level, StationRef station, PumpOrder order) {
        return BilgePumps.crewTicks(level, station);
    }

    /** Water left (it came in faster than the estimate): carry on with the next order. Runs after the station ticks. */
    @Override
    public void complete(ServerLevel level, StationRef station, PumpOrder order) {
        if (BilgePumps.crewTicks(level, station) > 0) {
            Stations.order(level, station, order);
        }
    }
}

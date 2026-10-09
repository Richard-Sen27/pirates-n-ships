package com.richardsenger.piratesnships.station.lookout;

import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.StationRef;
import net.minecraft.server.level.ServerLevel;

/**
 * The crow's nest as a station (CN1, docs/design.md §6, §7): the lookout. Its work is not an order but the watch
 * itself: a crew member seated at the nest keeps watch for as long as it is assigned, and {@link Lookouts} scans for
 * every manned nest each {@code lookout.scan_interval_ticks}. So the station takes only {@link Order#KEEP_WATCH}, which
 * is no {@code CrewOrder} (the whistle's orders and the job board never reach it) and always has nothing to do.
 */
public final class LookoutStation implements StationKind<LookoutStation.Order> {

    public static final LookoutStation INSTANCE = new LookoutStation();

    /** The lookout's one standing order. */
    public enum Order { KEEP_WATCH }

    private LookoutStation() {
    }

    @Override
    public String id() {
        return "lookout";
    }

    @Override
    public Class<Order> orderType() {
        return Order.class;
    }

    /** The watch runs while the station is manned; there is no work to time (0) and none when switched off (-1). */
    @Override
    public int durationTicks(ServerLevel level, StationRef station, Order order) {
        return LookoutConfig.ENABLED.get() ? 0 : -1;
    }

    @Override
    public void complete(ServerLevel level, StationRef station, Order order) {
    }
}

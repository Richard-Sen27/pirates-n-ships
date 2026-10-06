package com.richardsenger.piratesnships.station.winch;

import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.StationRef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * The sail winch as a station (docs/design.md §6): a crew member brings all sails of the ship to the order's trim.
 * The work takes {@code crew_stations.ticks_per_trim_step} per trim step, counted from the highest trim currently
 * set (as the player's winch cycle does), and the sails change when the work is done.
 */
public final class WinchStation implements StationKind<SailOrder> {

    public static final WinchStation INSTANCE = new WinchStation();

    private WinchStation() {
    }

    @Override
    public String id() {
        return "sail_winch";
    }

    @Override
    public Class<SailOrder> orderType() {
        return SailOrder.class;
    }

    @Override
    public int durationTicks(ServerLevel level, StationRef station, SailOrder order) {
        SailingRuntime rt = runtime(level, station);
        if (rt == null || rt.sailPositions().isEmpty()) {
            return -1;
        }
        return order.durationTicks(currentTrim(rt), StationConfig.TICKS_PER_TRIM_STEP.get());
    }

    @Override
    public void complete(ServerLevel level, StationRef station, SailOrder order) {
        SailingRuntime rt = runtime(level, station);
        if (rt == null) {
            return;
        }
        for (BlockPos p : rt.sailPositions()) {
            if (rt.trimAt(p) != order.target()) {
                SailingRuntimes.setTrim(level, p, order.target()); // the block changes update the runtime
            }
        }
    }

    /** The highest trim set on the ship (the reference of the winch cycle and of the work time). */
    public static SailTrim currentTrim(SailingRuntime rt) {
        SailTrim highest = SailTrim.FURLED;
        for (BlockPos p : rt.sailPositions()) {
            SailTrim t = rt.trimAt(p);
            if (t != null && t.ordinal() > highest.ordinal()) highest = t;
        }
        return highest;
    }

    private static @Nullable SailingRuntime runtime(ServerLevel level, StationRef station) {
        ShipBody ship = SableShips.byId(level, station.ship());
        return ship == null ? null : SailingRuntimes.getOrCreate(ship);
    }
}

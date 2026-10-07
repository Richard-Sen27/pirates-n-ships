package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.StationRef;
import net.minecraft.server.level.ServerLevel;

/**
 * The cannon as a crew station (docs/design.md §6, §8.2). For now a crew member can only fire a cannon that a player
 * loaded ({@link CannonOrder#FIRE}, a short fuse of {@link #FUSE_TICKS}); loading by crew needs a powder and shot
 * supply and comes with the crew orders. A crew shot has no owner.
 */
public final class CannonStation implements StationKind<CannonStation.CannonOrder> {

    public static final CannonStation INSTANCE = new CannonStation();

    /** Ticks from the order to the shot (lighting the fuse). */
    public static final int FUSE_TICKS = 10;

    /** Orders of the cannon station. */
    public enum CannonOrder {
        FIRE
    }

    private CannonStation() {
    }

    @Override
    public String id() {
        return "cannon";
    }

    @Override
    public Class<CannonOrder> orderType() {
        return CannonOrder.class;
    }

    @Override
    public int durationTicks(ServerLevel level, StationRef station, CannonOrder order) {
        if (!CannonConfig.ENABLED.get()) return -1;
        var state = level.getBlockState(station.pos());
        if (!(state.getBlock() instanceof CannonBlock)) return -1;
        return CannonRules.canFire(state.getValue(CannonBlock.LOAD)) ? FUSE_TICKS : 0;
    }

    @Override
    public void complete(ServerLevel level, StationRef station, CannonOrder order) {
        CannonService.fire(level, station.pos(), null);
    }
}

package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.combat.cannon.CannonStation.CannonOrder;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.StationRef;
import net.minecraft.server.level.ServerLevel;

/**
 * The swivel gun as a crew station (docs/design.md §6, §8.2, P2), a sibling of {@link CannonStation}: it takes the same
 * {@link CannonOrder#FIRE}, so the whistle's "Fire!" and {@code /pirates crew order fire} reach the crew at swivel guns
 * too. Crew fires a gun a player loaded, along the aim it was left at, after the same fuse; a crew shot has no owner.
 */
public final class SwivelStation implements StationKind<CannonOrder> {

    public static final SwivelStation INSTANCE = new SwivelStation();

    private SwivelStation() {
    }

    @Override
    public String id() {
        return "swivel_gun";
    }

    @Override
    public Class<CannonOrder> orderType() {
        return CannonOrder.class;
    }

    @Override
    public int durationTicks(ServerLevel level, StationRef station, CannonOrder order) {
        if (!CannonConfig.SWIVEL_ENABLED.get()) return -1;
        var state = level.getBlockState(station.pos());
        if (!(state.getBlock() instanceof SwivelGunBlock)) return -1;
        return CannonRules.canFire(state.getValue(SwivelGunBlock.LOAD)) ? CannonStation.FUSE_TICKS : 0;
    }

    @Override
    public void complete(ServerLevel level, StationRef station, CannonOrder order) {
        SwivelService.fire(level, station.pos(), null);
    }
}

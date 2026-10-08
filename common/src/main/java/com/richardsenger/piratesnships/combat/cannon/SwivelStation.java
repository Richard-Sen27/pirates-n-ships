package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.combat.cannon.CannonStation.CannonOrder;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.StationRef;
import net.minecraft.server.level.ServerLevel;

/**
 * The swivel gun as a crew station (docs/design.md §6, §8.2, P2), a sibling of {@link CannonStation}: it takes the same
 * {@link CannonOrder}s, so the whistle's "Fire!" and "Load!" and {@code /pirates crew order fire|load} reach the crew at
 * swivel guns too ("Fire at will" does not: {@link #accepts}). Crew fires a loaded gun along the aim it was left at, after the same fuse (a crew shot has no
 * owner), and loads it from a supply in reach: powder, then {@code cannons.swivel.ammo_count} of the ammo
 * ({@link CrewLoading}, C9).
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

    /**
     * "Fire!" and "Load!", not "Fire at will": NPC gunnery (WS4a) lays cannons only, so a swivel crew neither answers
     * it nor gets it as a job.
     */
    @Override
    public boolean accepts(Object order) {
        return order instanceof CannonOrder o && o != CannonOrder.FIRE_AT_WILL;
    }

    @Override
    public int durationTicks(ServerLevel level, StationRef station, CannonOrder order) {
        if (!CannonConfig.SWIVEL_ENABLED.get()) return -1;
        CrewLoading.Gun gun = CrewLoading.swivel(level, station);
        if (gun == null) return -1;
        return order == CannonOrder.LOAD ? CrewLoading.loadTicks(level, station, gun) : CrewLoading.fireTicks(station, gun);
    }

    @Override
    public void complete(ServerLevel level, StationRef station, CannonOrder order) {
        CrewLoading.Gun gun = CrewLoading.swivel(level, station);
        if (gun == null) return;
        if (order == CannonOrder.LOAD) {
            CrewLoading.load(level, station, gun);
            return;
        }
        if (CrewLoading.firesAfterLoad(station) && !CannonRules.canFire(gun.load())) {
            CrewLoading.load(level, station, gun);
        }
        boolean fired = SwivelService.fire(level, station.pos(), null).outcome() == SwivelService.Outcome.FIRED;
        CrewLoading.afterShot(level, station, fired);
    }
}

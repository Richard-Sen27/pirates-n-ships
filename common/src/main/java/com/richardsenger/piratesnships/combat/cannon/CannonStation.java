package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.order.CrewOrder;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;

/**
 * The cannon as a crew station (docs/design.md §6, §8.2). A crew member fires a loaded cannon ({@link CannonOrder#FIRE},
 * a short fuse of {@link #FUSE_TICKS}) and loads it from a powder and shot supply in reach ({@link CannonOrder#LOAD},
 * C9, see {@link CrewLoading}); with {@code cannons.crew.auto_reload} it loads again after each of its shots. A crew
 * shot has no owner.
 */
public final class CannonStation implements StationKind<CannonStation.CannonOrder> {

    public static final CannonStation INSTANCE = new CannonStation();

    /** Ticks from the order to the shot (lighting the fuse). */
    public static final int FUSE_TICKS = 10;

    /**
     * Orders of the cannon and swivel gun stations. A {@link CrewOrder}: the whistle's "Fire!" and "Load!" entries and
     * {@code /pirates crew order fire|load} give them. A ship-wide "Fire!" reaches the crew at every gun of the ship;
     * those at an unloaded gun answer that it is not loaded (unless they are loading it: then they fire once it is
     * loaded). "Load!" makes the crew load from the supply in reach; refused at a loaded gun and without a supply.
     */
    public enum CannonOrder implements CrewOrder {
        FIRE, LOAD;

        @Override
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Translation key of the order name ("fire the cannons", "load the guns"). */
        @Override
        public String nameKey() {
            return "cannon_order." + Constants.MOD_ID + "." + id();
        }

        /** "Aye, firing!", "Aye, loading!" */
        @Override
        public String ackKey() {
            return "message." + Constants.MOD_ID + ".crew.ack." + id();
        }

        /** FIRE: "The gun is not loaded, captain!"; LOAD: "The gun is already loaded, captain!" */
        @Override
        public String nothingToDoKey() {
            return "message." + Constants.MOD_ID + ".crew." + (this == FIRE ? "cannon_not_loaded" : "cannon_already_loaded");
        }

        /**
         * FIRE: "This gun won't fire, captain!" (cannons disabled on the server); LOAD: no powder and shot in reach (or
         * crew loading disabled on the server).
         */
        @Override
        public String unableKey() {
            return "message." + Constants.MOD_ID + ".crew." + (this == FIRE ? "cannon_unable" : "cannon_no_supply");
        }
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
        // a station taken at the rear half acts on the master like every use does
        CrewLoading.Gun gun = CrewLoading.cannon(level, station);
        if (gun == null) return -1;
        return order == CannonOrder.LOAD ? CrewLoading.loadTicks(level, station, gun) : CrewLoading.fireTicks(station, gun);
    }

    @Override
    public void complete(ServerLevel level, StationRef station, CannonOrder order) {
        CrewLoading.Gun gun = CrewLoading.cannon(level, station);
        if (gun == null) return;
        if (order == CannonOrder.LOAD) {
            CrewLoading.load(level, station, gun);
            return;
        }
        if (CrewLoading.firesAfterLoad(station) && !CannonRules.canFire(gun.load())) {
            CrewLoading.load(level, station, gun);
        }
        boolean fired = CannonService.fire(level, station.pos(), null).outcome() == CannonService.Outcome.FIRED;
        CrewLoading.afterShot(level, station, fired);
    }
}

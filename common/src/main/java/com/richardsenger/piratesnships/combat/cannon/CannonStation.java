package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.cannon.npc.Gunnery;
import com.richardsenger.piratesnships.combat.cannon.npc.GunneryConfig;
import com.richardsenger.piratesnships.combat.cannon.npc.GunneryState;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.order.CrewOrder;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;

/**
 * The cannon as a crew station (docs/design.md §6, §8.2). A crew member fires a loaded cannon ({@link CannonOrder#FIRE},
 * a short fuse of {@link #FUSE_TICKS}) and loads it from a powder and shot supply in reach ({@link CannonOrder#LOAD},
 * C9, see {@link CrewLoading}); with {@code cannons.crew.auto_reload} it loads again after each of its shots. A crew
 * shot has no owner. {@link CannonOrder#FIRE_AT_WILL} (WS4a) hands the guns of the ship to {@link Gunnery}: the crews
 * aim and fire at hostile ships by themselves.
 */
public final class CannonStation implements StationKind<CannonStation.CannonOrder> {

    public static final CannonStation INSTANCE = new CannonStation();

    /** Ticks from the order to the shot (lighting the fuse). */
    public static final int FUSE_TICKS = 10;

    /** Ticks from "Fire at will" to the crew taking over its gun (the acknowledgement). */
    public static final int FIRE_AT_WILL_TICKS = 1;

    /**
     * Orders of the cannon and swivel gun stations. A {@link CrewOrder}: the whistle's "Fire!", "Load!" and "Fire at
     * will" entries and {@code /pirates crew order fire|load|fire_at_will} give them. A ship-wide "Fire!" reaches the
     * crew at every gun of the ship; those at an unloaded gun answer that it is not loaded (unless they are loading it:
     * then they fire once it is loaded). "Load!" makes the crew load from the supply in reach; refused at a loaded gun
     * and without a supply. "Fire at will" (WS4a, cannons only) sets the ship's {@link GunneryState#AT_WILL} once a crew
     * at one of its guns hears it, and that crew starts loading an empty gun; refused while {@code cannons.npc.enabled}
     * is off.
     */
    public enum CannonOrder implements CrewOrder {
        FIRE, LOAD, FIRE_AT_WILL;

        @Override
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Translation key of the order name ("fire the cannons", "load the guns", "fire at will"). */
        @Override
        public String nameKey() {
            return "cannon_order." + Constants.MOD_ID + "." + id();
        }

        /** "Aye, firing!", "Aye, loading!", "Aye, firing at will!" */
        @Override
        public String ackKey() {
            return "message." + Constants.MOD_ID + ".crew.ack." + id();
        }

        /**
         * FIRE: "The gun is not loaded, captain!"; LOAD: "The gun is already loaded, captain!"; FIRE_AT_WILL always has
         * something to do and shares FIRE's key.
         */
        @Override
        public String nothingToDoKey() {
            return "message." + Constants.MOD_ID + ".crew." + (this == LOAD ? "cannon_already_loaded" : "cannon_not_loaded");
        }

        /**
         * FIRE and FIRE_AT_WILL: "This gun won't fire, captain!" (cannons, or crews firing by themselves, disabled on
         * the server); LOAD: no powder and shot in reach (or crew loading disabled on the server).
         */
        @Override
        public String unableKey() {
            return "message." + Constants.MOD_ID + ".crew." + (this == LOAD ? "cannon_no_supply" : "cannon_unable");
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
        return switch (order) {
            case LOAD -> CrewLoading.loadTicks(level, station, gun);
            case FIRE -> CrewLoading.fireTicks(station, gun);
            case FIRE_AT_WILL -> GunneryConfig.ENABLED.get() ? FIRE_AT_WILL_TICKS : -1;
        };
    }

    @Override
    public void complete(ServerLevel level, StationRef station, CannonOrder order) {
        CrewLoading.Gun gun = CrewLoading.cannon(level, station);
        if (gun == null) return;
        if (order == CannonOrder.LOAD) {
            CrewLoading.load(level, station, gun);
            return;
        }
        if (order == CannonOrder.FIRE_AT_WILL) {
            ShipBody ship = SableShips.byId(level, station.ship());
            if (ship != null) Gunnery.set(level, ship, GunneryState.AT_WILL);
            if (!CannonRules.canFire(gun.load()) && CrewLoading.enabled()) Stations.order(level, station, CannonOrder.LOAD);
            return;
        }
        if (CrewLoading.firesAfterLoad(station) && !CannonRules.canFire(gun.load())) {
            CrewLoading.load(level, station, gun);
        }
        // WS4a: a shot the crew fired by itself breaks npc_block_damage_multiplier times the blocks
        double blockFactor = Gunnery.blockDamageFactor(Gunnery.takeShot(station));
        boolean fired = CannonService.fire(level, station.pos(), null, blockFactor).outcome() == CannonService.Outcome.FIRED;
        CrewLoading.afterShot(level, station, fired);
    }
}

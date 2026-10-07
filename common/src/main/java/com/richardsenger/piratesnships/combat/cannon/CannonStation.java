package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.order.CrewOrder;
import java.util.Locale;
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

    /**
     * Orders of the cannon station. A {@link CrewOrder}: the whistle's "Fire!" entry and {@code /pirates crew order fire}
     * give it. A ship-wide "Fire!" reaches the crew at every cannon of the ship; those at an unloaded cannon answer
     * that it is not loaded.
     */
    public enum CannonOrder implements CrewOrder {
        FIRE;

        @Override
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Translation key of the order name ("fire the cannons"). */
        @Override
        public String nameKey() {
            return "cannon_order." + Constants.MOD_ID + "." + id();
        }

        /** "Aye, firing!" */
        @Override
        public String ackKey() {
            return "message." + Constants.MOD_ID + ".crew.ack." + id();
        }

        /** "The gun is not loaded, captain!" */
        @Override
        public String nothingToDoKey() {
            return "message." + Constants.MOD_ID + ".crew.cannon_not_loaded";
        }

        /** "This gun won't fire, captain!" (cannons disabled on the server). */
        @Override
        public String unableKey() {
            return "message." + Constants.MOD_ID + ".crew.cannon_unable";
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
        var state = level.getBlockState(station.pos());
        if (!(state.getBlock() instanceof CannonBlock)) return -1;
        return CannonRules.canFire(state.getValue(CannonBlock.LOAD)) ? FUSE_TICKS : 0;
    }

    @Override
    public void complete(ServerLevel level, StationRef station, CannonOrder order) {
        CannonService.fire(level, station.pos(), null);
    }
}

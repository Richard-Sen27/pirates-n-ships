package com.richardsenger.piratesnships.ship.hull.pump;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.hull.FloodingConfig;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntime;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.pump.PumpOrder;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Server side of the bilge pump (docs/design.md §4.5, §6): a player's use, the crew station's work time and whether a
 * crew member is pumping. The water itself is removed by the flood simulation of the ship's {@link HullRuntime}, which
 * counts the working pumps per compartment every tick ({@link PumpSet}), so buoyancy follows the water as usual.
 */
public final class BilgePumps {

    static final String KEY = "message." + Constants.MOD_ID + ".bilge_pump.";
    public static final String KEY_PUMPING = KEY + "pumping";
    public static final String KEY_DRY = KEY + "dry";
    public static final String KEY_NO_COMPARTMENT = KEY + "no_compartment";
    public static final String KEY_NOT_ON_SHIP = KEY + "not_on_ship";
    public static final String KEY_DISABLED = KEY + "disabled";

    /** Longest single pumping order of a crew member; when it is done and water is left, the crew member goes on. */
    public static final int CREW_BATCH_TICKS = 100;

    public enum Outcome { PUMPING, DRY, NO_COMPARTMENT, NOT_ON_SHIP, DISABLED }

    /** What a use did, with the action-bar feedback. */
    public record Use(Outcome outcome, Component message) {
    }

    private BilgePumps() {
    }

    /**
     * A player uses the pump at plot position {@code pump} (one stroke; holding the use key repeats it every 4 ticks):
     * the pump works for {@code flooding.pump_use_ticks} from now, draining the compartment of {@link PumpIntake}.
     * Public for GameTests.
     */
    public static Use operate(ServerLevel level, BlockPos pump, @Nullable Player player) {
        if (!FloodingConfig.PUMP_ENABLED.get()) {
            return new Use(Outcome.DISABLED, Component.translatable(KEY_DISABLED));
        }
        HullRuntime rt = runtime(level, pump);
        if (rt == null) {
            return new Use(Outcome.NOT_ON_SHIP, Component.translatable(KEY_NOT_ON_SHIP));
        }
        int c = rt.pumpIntake(pump);
        if (c < 0) {
            return new Use(Outcome.NO_COMPARTMENT, Component.translatable(KEY_NO_COMPARTMENT, FloodingConfig.PUMP_REACH.get()));
        }
        double volume = rt.simulation().volume(c);
        if (volume <= PumpIntake.DRY) {
            return new Use(Outcome.DRY, Component.translatable(KEY_DRY));
        }
        rt.pumps().add(pump); // normally known from the snapshot already
        rt.pumps().use(pump, level.getGameTime() + FloodingConfig.PUMP_USE_TICKS.get());
        if (player != null && !player.getAbilities().instabuild) {
            player.causeFoodExhaustion(FloodingConfig.PUMP_EXHAUSTION.get().floatValue());
        }
        return new Use(Outcome.PUMPING, Component.translatable(KEY_PUMPING, String.format(Locale.ROOT, "%.1f", volume)));
    }

    /** Whether a crew member is carrying out a pumping order at the pump at {@code pos} on ship {@code ship}. */
    public static boolean crewOperating(UUID ship, BlockPos pos) {
        StationState<Object> s = Stations.state(new StationRef(ship, pos));
        return s != null && s.order() instanceof PumpOrder;
    }

    /**
     * Work time of one crew pumping order at {@code station} ({@link PumpIntake#crewTicks}): 0 when the bilge it reaches
     * is dry, -1 when the pump cannot work (switched off, no rate, not on a ship, no compartment in reach).
     */
    public static int crewTicks(ServerLevel level, StationRef station) {
        if (!FloodingConfig.PUMP_ENABLED.get()) {
            return -1;
        }
        ShipBody ship = SableShips.byId(level, station.ship());
        HullRuntime rt = ship == null ? null : HullRuntimes.get(level, ship.id());
        if (rt == null) {
            return -1;
        }
        int c = rt.pumpIntake(station.pos());
        if (c < 0) {
            return -1;
        }
        return PumpIntake.crewTicks(rt.simulation().volume(c), FloodingConfig.params().pumpPerTick(), CREW_BATCH_TICKS);
    }

    /**
     * The pump at plot position {@code pos} worked this tick (a player's use or a crew order drained its compartment):
     * its block entity keeps the synced pumping flag on, which rocks the drawn handle (PMP1). Called by the hull runtime
     * for every working pump each tick.
     */
    public static void markWorked(ServerLevel level, BlockPos pos) {
        if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BilgePumpBlockEntity be) {
            be.activity().worked(level.getGameTime());
        }
    }

    private static @Nullable HullRuntime runtime(ServerLevel level, BlockPos plotPos) {
        ShipBody ship = SableShips.containing(level, plotPos);
        return ship == null ? null : HullRuntimes.get(level, ship.id());
    }
}

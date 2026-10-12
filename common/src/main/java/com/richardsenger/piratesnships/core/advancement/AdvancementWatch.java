package com.richardsenger.piratesnships.core.advancement;

import com.richardsenger.piratesnships.hazards.waves.SeaState;
import com.richardsenger.piratesnships.hazards.waves.SeaStates;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The advancement criteria that are states rather than events (ADV1), checked once a second per player: standing in a
 * port ({@code port_visited}), aboard a ship making way under sail ({@code sailed_with_wind}) and aboard a ship in a
 * storm ({@code storm_weathered}, counted in seconds while it lasts). Also settles the experience of advancements on
 * vanilla criteria ({@link AdvancementXp}).
 */
public final class AdvancementWatch {

    /** Ticks between two checks of one player. */
    static final int PERIOD = 20;

    /** Seconds each player has spent aboard in the current storm (not saved: a storm is weathered in one go). */
    private static final Map<UUID, Integer> STORM_SECONDS = new ConcurrentHashMap<>();

    private AdvancementWatch() {
    }

    /** {@code CommonEvents.PLAYER_TICK_END}. */
    public static void onPlayerTick(net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer sp && sp.tickCount % PERIOD == 0) check(sp);
    }

    /** One check of {@code player}; public for the GameTests. */
    public static void check(ServerPlayer player) {
        if (!ModAdvancements.enabled()) return;
        var server = player.getServer();
        if (server == null) return;
        PortRegistry.get(server).index().containing(player.level().dimension(), player.blockPosition())
                .ifPresent(port -> ModAdvancements.portVisited(player, port.kind().getSerializedName()));

        checkShip(player, ShipEntities.standingOrRiding(player));
        AdvancementXp.settle(player);
    }

    /**
     * The ship part of {@link #check}: {@code player} aboard {@code ship} (null: not aboard). Public for the GameTests,
     * whose players Sable cannot track aboard (they are not in the level).
     */
    public static void checkShip(ServerPlayer player, @org.jetbrains.annotations.Nullable ShipBody ship) {
        if (!ModAdvancements.enabled()) return;
        if (ship != null) {
            SailingRuntime rt = SailingRuntimes.get(ship.level(), ship.id());
            if (rt != null && rt.unfurledCount() > 0) {
                Vector3d v = rt.shipFrameVelocity(ship, new Vector3d());
                int tenths = (int) Math.floor(Math.sqrt(v.x * v.x + v.z * v.z) * 10.0);
                if (tenths > 0) ModAdvancements.sailedWithWind(player, tenths);
            }
        }
        if (ship != null && SeaStates.current(player.serverLevel()) == SeaState.STORM) {
            int seconds = STORM_SECONDS.merge(player.getUUID(), PERIOD / 20, Integer::sum);
            ModAdvancements.stormWeathered(player, seconds);
        } else {
            STORM_SECONDS.remove(player.getUUID());
        }
    }

    /** Server stop and logout. */
    public static void forget(UUID player) {
        STORM_SECONDS.remove(player);
    }

    public static void clear() {
        STORM_SECONDS.clear();
    }
}

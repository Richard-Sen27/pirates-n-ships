package com.richardsenger.piratesnships.ship.hull.runtime;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.WaterRegions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * All hull runtimes of the server, per level, plus the client half of the region sync. Entry points are plain static
 * methods called from the hull module's event wiring and from {@code mixin.MixinLevelChunk}.
 *
 * <p>Lifecycle: a runtime is created right after assembly ({@link #onAssembled}) and, for saved ships, by a scan every
 * {@link #DISCOVERY_TICKS} ticks over the loaded sub-levels that carry our assembly back-pointer and have a
 * {@code ShipData}. It is dropped when Sable reports the sub-level removed or unloaded (disassembly ends in a removal).
 */
public final class HullRuntimes {

    static final int DISCOVERY_TICKS = 20;

    private static final Map<ServerLevel, Map<UUID, HullRuntime>> SERVER = new IdentityHashMap<>();
    /** Client: the regions we added per ship. Only touched on the client thread. */
    private static final Map<UUID, List<WaterRegions.Handle>> CLIENT = new HashMap<>();

    private HullRuntimes() {
    }

    // ------------------------------------------------------------------ server

    public static @Nullable HullRuntime get(ServerLevel level, UUID ship) {
        Map<UUID, HullRuntime> m = SERVER.get(level);
        return m == null ? null : m.get(ship);
    }

    /** Creates (or recreates) the runtime of a freshly assembled ship. */
    public static HullRuntime onAssembled(ShipBody ship) {
        drop(ship.level(), ship.id(), ship);
        HullRuntime rt = HullRuntime.create(ship);
        SERVER.computeIfAbsent(ship.level(), l -> new HashMap<>()).put(ship.id(), rt);
        return rt;
    }

    public static void onShipRemoved(ServerLevel level, UUID ship, boolean destroyed) {
        drop(level, ship, null);
    }

    private static void drop(ServerLevel level, UUID ship, @Nullable ShipBody body) {
        Map<UUID, HullRuntime> m = SERVER.get(level);
        HullRuntime rt = m == null ? null : m.remove(ship);
        if (rt != null) {
            rt.dispose(body);
        }
    }

    public static void onLevelTick(ServerLevel level) {
        if (level.getGameTime() % DISCOVERY_TICKS == 0) {
            discover(level);
        }
        Map<UUID, HullRuntime> m = SERVER.get(level);
        if (m == null || m.isEmpty()) {
            return;
        }
        for (HullRuntime rt : List.copyOf(m.values())) {
            ShipBody ship = SableShips.byId(level, rt.id());
            if (ship == null) {
                drop(level, rt.id(), null);
                continue;
            }
            try {
                rt.tick(ship);
            } catch (RuntimeException e) {
                Constants.LOG.error("Hull runtime of ship {} failed, dropping it", rt.id(), e);
                drop(level, rt.id(), null);
            }
        }
    }

    private static void discover(ServerLevel level) {
        ShipRegistry registry = ShipRegistry.get(level.getServer());
        for (ShipBody ship : SableShips.all(level)) {
            if (ship.isRemoved() || get(level, ship.id()) != null || registry.find(ship.id()).isEmpty()) {
                continue;
            }
            if (!ship.userData(ShipAssembler.USER_DATA_KEY).hasUUID("ship") || ship.plotBlocks().isEmpty()) {
                continue;
            }
            SERVER.computeIfAbsent(level, l -> new HashMap<>()).put(ship.id(), HullRuntime.create(ship));
        }
    }

    public static void onPhysicsTick(ServerLevel level, double timeStep) {
        Map<UUID, HullRuntime> m = SERVER.get(level);
        if (m == null || m.isEmpty()) {
            return;
        }
        for (HullRuntime rt : m.values()) {
            ShipBody ship = SableShips.byId(level, rt.id());
            if (ship != null) {
                rt.physicsTick(ship, timeStep);
            }
        }
    }

    /** From {@code MixinLevelChunk}: a block state changed in a server level (any chunk). Cheap when no ship is loaded. */
    public static void onBlockChanged(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        Map<UUID, HullRuntime> m = SERVER.get(level);
        if (m == null || m.isEmpty()) {
            return;
        }
        ShipBody ship = SableShips.containing(level, pos);
        HullRuntime rt = ship == null ? null : m.get(ship.id());
        if (rt != null) {
            rt.onBlockChanged(pos.immutable(), oldState, newState);
        }
    }

    public static void onServerStopped() {
        for (Map<UUID, HullRuntime> m : SERVER.values()) {
            for (HullRuntime rt : m.values()) {
                rt.dispose(null);
            }
        }
        SERVER.clear();
    }

    // ------------------------------------------------------------------ client

    /** Client handler of {@link HullRegionsPayload}: replaces the ship's regions in the player's level. */
    public static void onRegionsPayload(HullRegionsPayload payload, Player player) {
        Level level = player.level();
        clearClient(payload.ship());
        List<WaterRegions.Handle> handles = new ArrayList<>();
        for (CellSet s : payload.regions()) {
            WaterRegions.Handle h = WaterRegions.add(level, s.minX(), s.minY(), s.minZ(), s.sizeX(), s.sizeY(), s.sizeZ(), s.bits());
            if (h != null) {
                handles.add(h);
            }
        }
        if (!handles.isEmpty()) {
            CLIENT.put(payload.ship(), handles);
        }
    }

    /** Client: a ship left the client level. */
    public static void onClientShipRemoved(Level level, UUID ship) {
        clearClient(ship);
    }

    private static void clearClient(UUID ship) {
        List<WaterRegions.Handle> old = CLIENT.remove(ship);
        if (old != null) {
            old.forEach(WaterRegions::remove);
        }
    }
}

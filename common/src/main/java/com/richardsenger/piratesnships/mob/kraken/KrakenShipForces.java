package com.richardsenger.piratesnships.mob.kraken;

import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The grips' forces on Sable ships between the kraken's game tick that computes them and the physics substeps that
 * apply them, the H1 hazards' path ({@code hazards/HazardShipForces}): every physics substep records each point force
 * as {@code force × timeStep} in the {@code pirates_n_ships:sea_hazards} force group ({@link ShipBody#applyHazardImpulse}).
 * Lists older than one game tick are dropped, so a released grip (or a removed kraken) stops pulling at once.
 */
public final class KrakenShipForces {

    /** One point force on one ship, body (plot) frame, newtons. */
    public record PointForce(UUID ship, Vector3d plotPoint, Vector3d localForce) { }

    private record Entry(long gameTime, List<PointForce> forces) { }

    private static final Map<ServerLevel, Map<UUID, Entry>> BY_LEVEL = new IdentityHashMap<>();

    private KrakenShipForces() {
    }

    /** Replaces the forces of kraken {@code kraken} for this game tick. */
    public static synchronized void set(ServerLevel level, UUID kraken, List<PointForce> forces) {
        Map<UUID, Entry> m = BY_LEVEL.computeIfAbsent(level, l -> new HashMap<>());
        if (forces.isEmpty()) {
            m.remove(kraken);
        } else {
            m.put(kraken, new Entry(level.getGameTime(), List.copyOf(forces)));
        }
    }

    /** Physics substep hook: records the current forces as impulses. */
    public static synchronized void onPhysicsTick(ServerLevel level, double timeStep) {
        Map<UUID, Entry> m = BY_LEVEL.get(level);
        if (m == null || m.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Map<UUID, ShipBody> ships = new HashMap<>();
        Vector3d impulse = new Vector3d();
        for (Iterator<Entry> it = m.values().iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (e.gameTime() < now - 1) {
                it.remove();
                continue;
            }
            for (PointForce f : e.forces()) {
                ShipBody ship = ships.computeIfAbsent(f.ship(), id -> SableShips.byId(level, id));
                if (ship != null && !ship.isRemoved()) {
                    ship.applyHazardImpulse(f.plotPoint(), f.localForce().mul(timeStep, impulse));
                }
            }
        }
    }

    public static synchronized void clear() {
        BY_LEVEL.clear();
    }
}

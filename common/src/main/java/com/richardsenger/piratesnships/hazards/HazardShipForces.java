package com.richardsenger.piratesnships.hazards;

import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The hazard forces on Sable ships between the game tick that computes them and the physics substeps that apply them.
 * A hazard's server tick replaces its list of point forces (body frame, newtons); every physics substep records each
 * as {@code force × timeStep} in our sea hazards force group ({@link ShipBody#applyHazardImpulse}), the same path as the
 * grappling rope (docs/sable-notes.md §3.2: queued groups are reset every substep, so forces must be recorded there).
 * Lists older than one game tick are dropped, so a removed hazard stops pushing at once.
 */
final class HazardShipForces {

    /** One point force on one ship, in the ship's body (plot) frame. */
    record PointForce(UUID ship, Vector3d plotPoint, Vector3d localForce) { }

    private record Entry(long gameTime, List<PointForce> forces) { }

    private static final Map<ServerLevel, Map<UUID, Entry>> BY_LEVEL = new IdentityHashMap<>();

    private HazardShipForces() {
    }

    /** Replaces the forces of hazard {@code hazard} for this game tick. */
    static synchronized void set(ServerLevel level, UUID hazard, List<PointForce> forces) {
        Map<UUID, Entry> m = BY_LEVEL.computeIfAbsent(level, l -> new HashMap<>());
        if (forces.isEmpty()) {
            m.remove(hazard);
        } else {
            m.put(hazard, new Entry(level.getGameTime(), List.copyOf(forces)));
        }
    }

    /** Physics substep hook: records the current forces as impulses. */
    static synchronized void onPhysicsTick(ServerLevel level, double timeStep) {
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

    static synchronized void clear() {
        BY_LEVEL.clear();
    }

    /** Collects point forces while a hazard computes its tick. */
    static List<PointForce> newList() {
        return new ArrayList<>();
    }
}

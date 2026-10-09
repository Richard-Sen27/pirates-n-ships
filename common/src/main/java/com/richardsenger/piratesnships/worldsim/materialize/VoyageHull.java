package com.richardsenger.piratesnships.worldsim.materialize;

import com.richardsenger.piratesnships.ship.hull.Compartment;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodSimulation;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntime;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WS3c: a materialised ship's hull flooded back to its voyage's health ({@link HealthFlooding}) through the hull
 * runtime's flood state. Server thread only.
 */
public final class VoyageHull {

    private VoyageHull() {
    }

    /**
     * Sets the water in every compartment of {@code ship} so that its flooded fraction is {@code 1 − health}, lowest
     * compartments first. False when the ship has no hull runtime yet (try again later); true otherwise, also when
     * nothing needed flooding.
     */
    public static boolean restore(ServerLevel level, ShipBody ship, double health) {
        HullRuntime rt = HullRuntimes.get(level, ship.id());
        if (rt == null) return false;
        if (health >= HealthFlooding.FULL) return true;
        FloodSimulation sim = rt.simulation();
        List<Compartment> compartments = sim.analysis().compartments();
        List<HealthFlooding.Tank> tanks = new ArrayList<>(compartments.size());
        for (Compartment c : compartments) tanks.add(new HealthFlooding.Tank(c.id(), c.minY(), c.volume()));
        Map<Integer, Double> volumes = HealthFlooding.volumes(tanks, health);
        for (Compartment c : compartments) sim.setVolume(c.id(), volumes.getOrDefault(c.id(), 0.0));
        return true;
    }
}

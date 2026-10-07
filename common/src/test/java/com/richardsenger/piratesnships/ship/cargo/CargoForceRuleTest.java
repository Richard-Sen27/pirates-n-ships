package com.richardsenger.piratesnships.ship.cargo;

import com.richardsenger.piratesnships.trade.cargo.CargoWeight;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** CW1: the vanilla containers' downward force, its magnitude, position and frame. */
class CargoForceRuleTest {

    private static final CargoWeight.Params ON = CargoWeight.Params.DEFAULTS;
    private static final CargoWeight.Params OFF = new CargoWeight.Params(false, 1.0, 0.25, 0.33, 0.75, 1.0);
    private static final Vector3d G = new Vector3d(0, -11, 0);

    @Test
    void forceIsDownwardAtTheContainer() {
        var chest = new CargoForceRule.Container(new Vector3d(3.5, 64.5, -7.5), 432);
        var forces = CargoForceRule.forces(List.of(chest), ON, G, new Quaterniond());
        assertEquals(1, forces.size());
        assertEquals(new Vector3d(3.5, 64.5, -7.5), forces.get(0).plotPoint());
        assertEquals(0, forces.get(0).localForce().x(), 1e-9);
        assertEquals(-95.04, forces.get(0).localForce().y(), 1e-9);
        assertEquals(0, forces.get(0).localForce().z(), 1e-9);
    }

    @Test
    void forceIsInTheBodyFrame() {
        // ship rolled 90° about +z: body +x points to world +y, so world down is body -x
        Quaterniond roll = new Quaterniond().rotateZ(Math.toRadians(90));
        var f = CargoForceRule.forces(List.of(new CargoForceRule.Container(new Vector3d(), 100)), ON, G, roll).get(0);
        Vector3d world = roll.transform(new Vector3d(f.localForce()));
        assertEquals(0, world.x, 1e-9);
        assertEquals(-22.0, world.y, 1e-9);
        assertEquals(0, world.z, 1e-9);
        assertEquals(-22.0, f.localForce().x(), 1e-9, "world down is body -x after a +90° roll about z");
    }

    @Test
    void emptyContainersAndToggleOffPullNothing() {
        var c = List.of(new CargoForceRule.Container(new Vector3d(), 0), new CargoForceRule.Container(new Vector3d(1, 0, 0), 50));
        assertEquals(1, CargoForceRule.forces(c, ON, G, new Quaterniond()).size());
        assertTrue(CargoForceRule.forces(c, OFF, G, new Quaterniond()).isEmpty());
        assertEquals(50.0, CargoForceRule.totalWeight(c));
    }
}

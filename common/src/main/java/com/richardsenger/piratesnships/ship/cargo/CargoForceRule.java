package com.richardsenger.piratesnships.ship.cargo;

import com.richardsenger.piratesnships.trade.cargo.CargoMass;
import com.richardsenger.piratesnships.trade.cargo.CargoWeight;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

/**
 * The downward force of vanilla containers on a ship (CW1, design.md §4.9), pure. A container holding {@code w} weight
 * units at plot position {@code p} pulls with {@code m · g}, where {@code m = KPG_PER_UNIT · weight_factor · w}
 * ({@link CargoMass#massOf}) and {@code g} is the level's gravity vector in the world frame; the force acts at the
 * container's centre, so a heavy chest in the bow trims the ship by the bow. Sable's queued force groups take forces in
 * the body (plot) frame, so the world vector is rotated by the inverse of the ship's orientation.
 */
public final class CargoForceRule {

    /** A container to weigh: block centre in plot coordinates and its content's weight [units]. */
    public record Container(Vector3dc plotCenter, double weight) {
    }

    /** One force [N] in the body frame at a plot position. */
    public record PointForce(Vector3dc plotPoint, Vector3dc localForce) {
    }

    private CargoForceRule() {
    }

    /**
     * @param gravityWorld gravity in the world frame (Sable default {@code (0, -11, 0)})
     * @param orientation  body to world rotation of the ship
     * @return one force per container with weight, none when {@code cargo_weight_affects_ships} is off
     */
    public static List<PointForce> forces(List<Container> containers, CargoWeight.Params p, Vector3dc gravityWorld, Quaterniondc orientation) {
        List<PointForce> out = new ArrayList<>(containers.size());
        for (Container c : containers) {
            double mass = CargoMass.massOf(c.weight(), p);
            if (mass <= 0) {
                continue;
            }
            Vector3d local = orientation.transformInverse(new Vector3d(gravityWorld).mul(mass));
            out.add(new PointForce(new Vector3d(c.plotCenter()), local));
        }
        return out;
    }

    /** Sum of the containers' weights [units]. */
    public static double totalWeight(List<Container> containers) {
        double w = 0;
        for (Container c : containers) {
            w += Math.max(0, c.weight());
        }
        return w;
    }
}

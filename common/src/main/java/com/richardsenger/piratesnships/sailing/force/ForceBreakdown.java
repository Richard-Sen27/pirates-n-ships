package com.richardsenger.piratesnships.sailing.force;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Optional;

/**
 * The summed sailing <b>forces</b> on one ship for one evaluation, plus the per-contributor breakdown (debugging,
 * HUD). Everything in the {@link ShipFrame}; the torque is about the center of mass.
 *
 * <p>To apply with Sable: rotate {@code force} and {@code torque} from the ship frame into the plot frame and pass
 * {@code force × timeStep} and {@code torque × timeStep} as impulses (Sable's "local" frame is the plot frame, its
 * torques are about the center of mass, see docs/sable-notes.md §3.2).
 */
public record ForceBreakdown(List<ForceContribution> contributions, Vector3dc force, Vector3dc torque) {

    public ForceBreakdown {
        contributions = List.copyOf(contributions);
        force = new Vector3d(force);
        torque = new Vector3d(torque);
    }

    /** Sums the contributions. */
    public static ForceBreakdown of(List<ForceContribution> contributions) {
        Vector3d f = new Vector3d();
        Vector3d t = new Vector3d();
        for (ForceContribution c : contributions) {
            f.add(c.force());
            t.add(c.torque());
        }
        return new ForceBreakdown(contributions, f, t);
    }

    /** The first contribution with this source label. */
    public Optional<ForceContribution> get(String source) {
        return contributions.stream().filter(c -> c.source().equals(source)).findFirst();
    }

    /** Sum of all contributions whose label starts with {@code prefix} (e.g. {@code "sail"}). */
    public Vector3d forceOf(String prefix) {
        Vector3d f = new Vector3d();
        contributions.stream().filter(c -> c.source().startsWith(prefix)).forEach(c -> f.add(c.force()));
        return f;
    }
}

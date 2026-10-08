package com.richardsenger.piratesnships.sailing.force;

import com.richardsenger.piratesnships.sailing.wind.WindSample;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for the ship integration: all sailing <b>forces</b> on one ship for one evaluation (one physics substep
 * or one tick), summed, with a per-contributor breakdown. Pure; call it from the physics tick with fresh state.
 *
 * <pre>{@code
 * WindSample wind = WindService.sample(level, shipPos);                 // once per game tick is enough
 * ShipState state = new ShipState(comWorld, shipToWorld, linVel, angVel, mass, submerged, length, keelCenter);
 * ForceBreakdown f = ShipForceModel.compute(wind, state, sails,
 *         new ShipForceModel.Rudder(helmAngle, rudderPos), SailingConfig.sailingParams());
 * // ship frame -> plot frame, force -> impulse:
 * handle.applyLinearAndAngularImpulse(plotFromShip(f.force()).mul(dt), plotFromShip(f.torque()).mul(dt));
 * }</pre>
 */
public final class ShipForceModel {

    /** Rudder input: angle in degrees (positive = turn to starboard) and position (ship frame, relative to COM). */
    public record Rudder(double angleDeg, Vector3dc position) {
    }

    private ShipForceModel() {
    }

    /**
     * @param wind   the wind at the ship (world frame)
     * @param ship   ship state
     * @param sails  the ship's sails
     * @param rudder rudder input, or {@code null} for none
     * @param params tuning (the anchor's chain force is not part of this sum since AN2a: it has its own force group,
     *               {@code sailing.anchor.AnchorPhysics})
     */
    public static ForceBreakdown compute(WindSample wind, ShipState ship, List<SailInstance> sails,
                                         @Nullable Rudder rudder, SailingParams params) {
        List<ForceContribution> out = new ArrayList<>(sails.size() + 3);
        Vector3d trueWind = wind.velocity(new Vector3d());
        for (int i = 0; i < sails.size(); i++) {
            SailInstance s = sails.get(i);
            out.add(SailForceModel.compute(s, trueWind, ship, params, "sail[" + i + "]:" + s.type().id()));
        }
        out.add(KeelModel.compute(ship, params));
        if (rudder != null) {
            out.add(RudderModel.compute(rudder.angleDeg(), rudder.position(), ship, params));
        }
        return ForceBreakdown.of(out);
    }

    /**
     * {@code forces} plus one more contribution, e.g. {@link HullDampingModel}'s torque, which the runtime applies
     * separately (unscaled by the heel factor) but lists in the same breakdown for {@code /pirates ship forces}.
     */
    public static ForceBreakdown withContribution(ForceBreakdown forces, ForceContribution extra) {
        List<ForceContribution> out = new ArrayList<>(forces.contributions());
        out.add(extra);
        return ForceBreakdown.of(out);
    }
}

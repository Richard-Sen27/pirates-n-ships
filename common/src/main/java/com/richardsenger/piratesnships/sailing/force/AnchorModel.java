package com.richardsenger.piratesnships.sailing.force;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Anchor holding force (docs/design.md §5.3). Horizontal only, so it never drags the ship under.
 *
 * <p>With {@code d} = horizontal vector from the hawse (where the rode leaves the hull) to the anchor point and
 * {@code v} = horizontal world velocity of the hawse:
 * {@code F = hold · mass · (stiffness · max(0, |d| − slack) · d̂ − damping · v)}, then capped at
 * {@code mass · maxAcceleration}. Applied at the hawse, so an anchored ship swings bow-first into wind and current.
 * The damping acts inside the slack too (the anchor and chain drag on the seabed).
 */
public final class AnchorModel {

    private AnchorModel() {
    }

    /**
     * @param state       anchor state; {@link AnchorState#hold()} scales the force
     * @param anchorPoint where the anchor lies, world frame [blocks]
     * @param hawse       hawse position relative to the COM, ship frame [blocks]
     * @return the force in the ship frame at the hawse
     */
    public static ForceContribution compute(AnchorState state, Vector3dc anchorPoint, Vector3dc hawse, ShipState ship,
                                            SailingParams.AnchorParams p) {
        if (state.hold() <= 0.0) {
            return ForceContribution.zero("anchor", hawse);
        }
        Vector3d hawseWorld = ship.toWorld(hawse, new Vector3d()).add(ship.position());
        Vector3d d = new Vector3d(anchorPoint).sub(hawseWorld);
        d.y = 0.0;
        double dist = d.length();
        Vector3d world = new Vector3d();
        if (dist > p.slack() && dist > 1e-9) {
            world.add(d.mul(p.stiffness() * (dist - p.slack()) / dist));
        }
        Vector3d v = ship.velocityAt(hawse, new Vector3d());
        v.y = 0.0;
        world.sub(v.mul(p.damping()));
        world.mul(state.hold() * ship.mass());
        double cap = ship.mass() * p.maxAcceleration();
        double len = world.length();
        if (len > cap) {
            world.mul(cap / len);
        }
        return ForceContribution.atPoint("anchor", ship.toLocal(world, new Vector3d()), hawse);
    }
}

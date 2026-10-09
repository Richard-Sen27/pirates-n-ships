package com.richardsenger.piratesnships.sailing.oars;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * The oars' push (OAR1, docs/design.md §5.3b), every physics substep: for each rowing ship of the level the rowers of
 * {@link Rowing}'s snapshot give {@link OarRules#thrust} at the ship's current speed along the bow, applied along the
 * bow at the centre of mass with the small yaw torque of unequal sides. It goes through the sailing force group
 * ({@link ShipBody#applySailingImpulse}, the wrapper of Sable's {@code QueuedForceGroup#getForceTotal} and
 * {@code ForceTotal#applyLinearAndAngularImpulse}, which adds to what the sails and keel put there), so oars and sails
 * add up; the keel and the wind model are untouched. Like the sails, the oars push only a ship that is afloat
 * ({@link SailingRuntime#lastSubmerged()} &gt; 0, with {@code sailing_runtime.sails_need_water}).
 */
public final class OarThrust {

    private static final Map<UUID, OarRules.Thrust> LAST = new ConcurrentHashMap<>();

    private OarThrust() {
    }

    /** The last thrust applied to {@code ship}, or null (not rowing, not afloat). For tests and debug. */
    public static OarRules.@Nullable Thrust last(UUID ship) {
        return LAST.get(ship);
    }

    static void onPhysicsTick(ServerLevel level, double timeStep) {
        Map<UUID, Rowing.Snapshot> rowing = Rowing.snapshots(level);
        LAST.keySet().removeIf(id -> !rowing.containsKey(id));
        if (rowing.isEmpty() || !OarsConfig.ENABLED.get()) {
            return;
        }
        OarRules.Params params = OarsConfig.params();
        boolean needWater = SailingConfig.SAILS_NEED_WATER.get();
        Vector3d v = new Vector3d();
        for (Map.Entry<UUID, Rowing.Snapshot> e : rowing.entrySet()) {
            ShipBody ship = SableShips.byId(level, e.getKey());
            SailingRuntime rt = SailingRuntimes.get(level, e.getKey());
            if (ship == null || rt == null || needWater && !(rt.lastSubmerged() > 0.0)) {
                LAST.remove(e.getKey());
                continue;
            }
            try {
                double forward = rt.shipFrameVelocity(ship, v).z;
                OarRules.Thrust t = OarRules.thrust(e.getValue().rowers(), e.getValue().workSpeed(), forward, params);
                LAST.put(e.getKey(), t);
                if (t.force() == 0.0 && t.yawTorque() == 0.0) {
                    continue;
                }
                Vector3d impulse = rt.bow().toPlot(new Vector3d(0, 0, t.force()), new Vector3d()).mul(timeStep);
                Vector3d angular = rt.bow().toPlot(new Vector3d(0, t.yawTorque(), 0), new Vector3d()).mul(timeStep);
                ship.applySailingImpulse(impulse, angular);
            } catch (RuntimeException ex) {
                Constants.LOG.error("Oars of ship {} failed", e.getKey(), ex);
            }
        }
    }
}

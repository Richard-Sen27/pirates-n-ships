package com.richardsenger.piratesnships.hazards.waves.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.richardsenger.piratesnships.hazard.HazardConfig;
import com.richardsenger.piratesnships.hazards.waves.boats.BoatWaveRule;
import com.richardsenger.piratesnships.hazards.waves.boats.BoatWaves;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The hull tilt of a vanilla boat on the waves (WAV6, physical client only, render thread): the boat model leans along
 * the local slope of the sea it rides ({@link BoatWaves#slope}: the same point lookup, fade and open-sea rule as its
 * float level), limited to {@code wave_effects.boat_tilt_degrees} and eased over {@link #EASE_TICKS}, so crossing into
 * a lake or a beach and the clamp never snap. Called by {@code mixin.MixinBoatRenderer} right after vanilla moved the
 * pose to the boat and before its yaw turn, so the turn here is a world-space one: it takes world up to the eased
 * surface normal. Only the boat's model is turned; riders are positioned by the boat entity, as in vanilla.
 */
public final class BoatTilt {

    /** Time constant of the easing [ticks]. */
    public static final double EASE_TICKS = 4.0;

    private static final class State {
        float nx, nz;
        double time = Double.NaN;
    }

    private static final Map<Boat, State> STATES = new WeakHashMap<>();
    private static final Vector3f UP = new Vector3f(0.0f, 1.0f, 0.0f);

    private BoatTilt() {
    }

    /** Turns {@code pose} so the boat's up follows the sea's normal under it. */
    public static void apply(Boat boat, float partialTick, PoseStack pose) {
        double max = HazardConfig.BOAT_TILT_DEGREES.get();
        State st = STATES.get(boat);
        if (max <= 0.0 && st == null) {
            return;
        }
        double x = Mth.lerp(partialTick, boat.xo, boat.getX());
        double z = Mth.lerp(partialTick, boat.zo, boat.getZ());
        double time = boat.level().getGameTime() + partialTick;
        double[] slope = max <= 0.0 ? null
                : BoatWaves.slope(boat.level(), x, boat.getBoundingBox().minY, z, boat.onGround(), time);
        double[] n = slope == null ? new double[] {0.0, 1.0, 0.0} : BoatWaveRule.tiltNormal(slope[0], slope[1], max);
        if (st == null) {
            if (slope == null) {
                return;
            }
            st = new State();
            STATES.put(boat, st);
        }
        double dt = Double.isNaN(st.time) ? 1e9 : Math.max(0.0, Math.min(20.0, time - st.time));
        st.time = time;
        float k = (float) (1.0 - Math.exp(-dt / EASE_TICKS));
        st.nx += (float) (n[0] - st.nx) * k;
        st.nz += (float) (n[2] - st.nz) * k;
        float ny2 = 1.0f - st.nx * st.nx - st.nz * st.nz;
        if (slope == null && Math.abs(st.nx) < 1e-4f && Math.abs(st.nz) < 1e-4f) {
            STATES.remove(boat);
            return;
        }
        Vector3f normal = new Vector3f(st.nx, (float) Math.sqrt(Math.max(0.0f, ny2)), st.nz);
        pose.mulPose(new Quaternionf().rotationTo(UP, normal));
    }
}

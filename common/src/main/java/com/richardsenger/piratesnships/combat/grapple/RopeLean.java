package com.richardsenger.piratesnships.combat.grapple;

import net.minecraft.world.phys.Vec3;

/**
 * GR8: how a player hanging on a rope by the hands leans along it ("the body following the rope direction"), as the
 * additive transform of the Player Animation Library's {@code body} bone. Pure; tested in JUnit
 * ({@code RopeLeanTest}, a rig test that composes PAL's own body transform).
 *
 * <p>PAL applies {@code body} right after vanilla's {@code LivingEntityRenderer#setupRotations} turned the pose stack by
 * {@code 180 - bodyYaw} about +Y (the frame: origin at the feet, +y up, -z the way the body faces, +x its right), as
 * {@code translate(-px/16, py/16 + 0.75, pz/16)}, {@code rotateZYX(rz, -ry, -rx)}, {@code translate(0, -0.75, 0)}
 * ({@code PlayerRendererMixin#applyBodyTransforms}, PAL 1.1.6). This class picks {@code rx} and {@code rz} so the body's
 * up axis points along the rope toward the hook, tilted at most {@link #MAX_TILT_DEGREES}, and the position so that the
 * grip ({@link #GRIP_HEIGHT} above the feet, both hands on the rope in the hang poses) stays where it is: the body
 * hangs from its hands.
 */
public final class RopeLean {

    /** Height of the hands on the rope above the feet in the hang poses [blocks] (arms raised, model scale 0.9375). */
    public static final double GRIP_HEIGHT = 1.85;
    /** Most the body leans off upright [degrees]. */
    public static final double MAX_TILT_DEGREES = 70.0;
    /** PAL's pivot of the {@code body} bone above the feet [blocks]. */
    static final double PIVOT = 0.75;

    /** The {@code body} bone's additive rotation [radians, PAL's bone angles] and position [pixels]. */
    public record Lean(float rx, float rz, float px, float py, float pz) {
        public static final Lean NONE = new Lean(0f, 0f, 0f, 0f, 0f);

        /** This lean eased toward {@code to} by {@code t} (0..1). */
        public Lean toward(Lean to, float t) {
            return new Lean(rx + (to.rx - rx) * t, rz + (to.rz - rz) * t, px + (to.px - px) * t, py + (to.py - py) * t,
                    pz + (to.pz - pz) * t);
        }
    }

    private RopeLean() {
    }

    /**
     * The lean of a body facing {@code bodyYawDegrees} (vanilla's yaw) whose hands hold a rope running from them toward
     * {@code toHook} (world, any length); {@link Lean#NONE} for a rope too short to tell.
     */
    public static Lean of(Vec3 toHook, float bodyYawDegrees) {
        double len = toHook.length();
        if (len < 1.0e-4) {
            return Lean.NONE;
        }
        Vec3 d = local(toHook.scale(1.0 / len), bodyYawDegrees);
        if (d.y < 0) {
            return Lean.NONE; // the hook below the hands: the rope does not hold the player up
        }
        double tilt = Math.acos(Math.min(1.0, d.y));
        double max = Math.toRadians(MAX_TILT_DEGREES);
        if (tilt > max) {
            // keep the direction, limit the tilt: rebuild the vector at the largest tilt
            double h = Math.hypot(d.x, d.z);
            d = new Vec3(d.x / h * Math.sin(max), Math.cos(max), d.z / h * Math.sin(max));
        }
        // up (0,1,0) through Rz(rz)·Rx(-rx) is (-cos rx · sin rz, cos rx · cos rz, -sin rx)
        double rx = -Math.asin(clamp(d.z));
        double rz = Math.atan2(-d.x, d.y);
        // keep the grip: T = h·(0,1,0) - R·h·(0,1,0) = h·((0,1,0) - d), h the grip above the pivot
        double h = GRIP_HEIGHT - PIVOT;
        double tx = -h * d.x;
        double ty = h * (1.0 - d.y);
        double tz = -h * d.z;
        // PAL: translate(-px/16, py/16, pz/16)
        return new Lean((float) rx, (float) rz, (float) (-16.0 * tx), (float) (16.0 * ty), (float) (16.0 * tz));
    }

    /**
     * A world direction in the body's frame after vanilla's {@code setupRotations}: the inverse of the turn by
     * {@code 180 - bodyYaw} about +Y.
     */
    static Vec3 local(Vec3 world, float bodyYawDegrees) {
        double a = -Math.toRadians(180.0 - bodyYawDegrees);
        double c = Math.cos(a);
        double s = Math.sin(a);
        // rotation about +Y by a: x' = x cos a + z sin a, z' = -x sin a + z cos a
        return new Vec3(world.x * c + world.z * s, world.y, -world.x * s + world.z * c);
    }

    private static double clamp(double v) {
        return Math.max(-1.0, Math.min(1.0, v));
    }
}

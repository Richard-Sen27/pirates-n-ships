package com.richardsenger.piratesnships.hazards.waves.client.surface;

/**
 * The anchor of the visible sea (WAV3). Pure.
 *
 * <p>{@link com.richardsenger.piratesnships.hazards.waves.WaveField} is anchored: the waves run with the wind only
 * relative to an anchor point, and the server anchors the field at each ship's centre (WV1). The surface needs one
 * anchor for the whole mesh, and it has to move smoothly: the field is continuous in the anchor, but moving it shifts
 * the phase of every train by {@code kᵢ (rᵢ − dᵢ)·Δa}, which the eye reads as the waves speeding up or slowing down
 * (nothing at all when the wind blows toward the east, where {@code rᵢ = dᵢ}). So:
 * <ul>
 *   <li><b>Aboard a ship</b> the anchor glides onto the ship's position and then follows it, so the waves the player
 *       sees around the hull are the ones the server rocks the hull with.</li>
 *   <li><b>Otherwise</b> it stays where it is while the camera is within {@link #DEAD_ZONE} blocks and follows a
 *       camera beyond that, never faster than needed to keep it near the dead zone's edge.</li>
 *   <li>Farther than {@link #SNAP_DISTANCE} away (a teleport, a new dimension) it jumps to the target.</li>
 * </ul>
 * The mesh reaches up to the render distance from an anchor near the camera; the slow drift of the wave direction then
 * shifts the far phases a little (see {@code WaveField}'s class comment), which stays well below the waves' own speed.
 */
public final class SurfaceAnchor {

    /** Camera distance the free anchor tolerates before it follows [blocks]. */
    public static final double DEAD_ZONE = 48.0;
    /** Distance at which the anchor jumps instead of gliding [blocks]. */
    public static final double SNAP_DISTANCE = 384.0;
    /** Free anchor: speed per block beyond the dead zone [1/tick] and its least speed [blocks/tick]. */
    public static final double FREE_RATE = 0.05;
    public static final double FREE_MIN_SPEED = 0.25;
    /** Ship anchor: speed per block of distance [1/tick] and least speed [blocks/tick]. */
    public static final double SHIP_RATE = 0.1;
    public static final double SHIP_MIN_SPEED = 0.4;
    /** Largest time step one update integrates [ticks]. */
    public static final double MAX_STEP = 20.0;

    private double x, z;
    private boolean set;

    public double x() {
        return x;
    }

    public double z() {
        return z;
    }

    public boolean isSet() {
        return set;
    }

    public void reset() {
        set = false;
    }

    /**
     * Moves the anchor toward {@code (tx, tz)} over {@code dtTicks}.
     *
     * @param ship whether the target is the ship the player is aboard (glide onto it and follow it) or the camera (dead
     *             zone)
     */
    public void update(double tx, double tz, boolean ship, double dtTicks) {
        double dx = tx - x, dz = tz - z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (!set || d > SNAP_DISTANCE || !Double.isFinite(d)) {
            x = tx;
            z = tz;
            set = true;
            return;
        }
        double dt = Math.max(0.0, Math.min(MAX_STEP, dtTicks));
        double gap = ship ? d : d - DEAD_ZONE;
        if (gap <= 0.0 || dt == 0.0) {
            return;
        }
        double speed = ship ? Math.max(SHIP_MIN_SPEED, d * SHIP_RATE) : Math.max(FREE_MIN_SPEED, gap * FREE_RATE);
        double move = Math.min(gap, speed * dt);
        x += dx / d * move;
        z += dz / d * move;
    }
}

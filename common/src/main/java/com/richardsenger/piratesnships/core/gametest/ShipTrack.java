package com.richardsenger.piratesnships.core.gametest;

import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * A ship's horizontal motion in a GameTest as the integral of its reported velocity, one game tick (0.05 s) per sample.
 *
 * <p><b>Use it for every slow drift</b> (below about 0.2 m/s on an axis): Sable's physics is 32-bit in world
 * coordinates, so a slow ship's pose stalls or moves in whole f32 steps depending on where the test grid put it
 * (docs/sable-notes.md §9.0l, measured in HZ1: a hull's x stayed at exactly 2737.5 for 110 ticks while it reported
 * -0.069 m/s). The velocity is what the solver computes from the forces and the water, before that rounding, and its
 * integral agreed within a few percent at any grid position. The pose is kept alongside for the log only.
 *
 * <p>Not for bodies pressed against something by a steady force: under contact the reported velocity overstates the
 * motion (docs/sable-notes.md §9.0h); measure positions there.
 *
 * <pre>{@code
 * ShipTrack track = ShipTrack.follow(h, ship.ship(), pool.position(), 10);  // starts at tick 10
 * h.runAfterDelay(110, () -> {
 *     track.stop();
 *     h.assertTrue(track.radialMove() > 0.15, "not pulled in: " + track);
 * });
 * }</pre>
 */
public final class ShipTrack {

    /** Seconds per game tick: one velocity sample is integrated over this. */
    private static final double TICK_SECONDS = 0.05;

    private final ShipBody ship;
    @Nullable
    private final Vec3 centre;
    private boolean started, stopped;
    private double startX, startZ, poseX, poseZ, dx, dz;
    private int samples;

    /** A track of {@code ship}; {@code centre} is the point {@link #radialMove} measures against (may be null). */
    public ShipTrack(ShipBody ship, @Nullable Vec3 centre) {
        this.ship = ship;
        this.centre = centre;
    }

    /**
     * A track that {@link #tick}s itself every game tick from the test's tick {@code fromTick} (its start pose) until
     * {@link #stop} or the ship is removed.
     */
    public static ShipTrack follow(GameTestHelper h, ShipBody ship, @Nullable Vec3 centre, long fromTick) {
        ShipTrack track = new ShipTrack(ship, centre);
        h.onEachTick(() -> {
            if (h.getTick() >= fromTick) {
                track.tick();
            }
        });
        return track;
    }

    /**
     * One game tick: the first call records the start pose, every later one adds the current velocity × one tick.
     * Does nothing once {@link #stop}ped or when the ship is gone.
     */
    public void tick() {
        if (stopped || ship.isRemoved()) {
            return;
        }
        Vector3d c = comWorld(ship);
        if (!started) {
            started = true;
            startX = poseX = c.x;
            startZ = poseZ = c.z;
            return;
        }
        Vector3d v = ship.linearVelocity();
        dx += v.x * TICK_SECONDS;
        dz += v.z * TICK_SECONDS;
        poseX = c.x;
        poseZ = c.z;
        samples++;
    }

    /** Freezes the track: later ticks add nothing (the measured window ends here). */
    public void stop() {
        stopped = true;
    }

    public boolean started() {
        return started;
    }

    /** Velocity samples integrated so far. */
    public int samples() {
        return samples;
    }

    /** Integrated horizontal displacement along world x [blocks]. */
    public double dx() {
        return dx;
    }

    /** Integrated horizontal displacement along world z [blocks]. */
    public double dz() {
        return dz;
    }

    /** Integrated horizontal distance from the start [blocks]. */
    public double travel() {
        return Math.hypot(dx, dz);
    }

    /** How much closer to the centre the integrated motion brought the ship [blocks]; negative = farther out. */
    public double radialMove() {
        if (centre == null) {
            throw new IllegalStateException("radialMove needs a centre");
        }
        return Math.hypot(startX - centre.x, startZ - centre.z) - Math.hypot(startX + dx - centre.x, startZ + dz - centre.z);
    }

    /** Distance from the start pose to the centre [blocks]. */
    public double startDistance() {
        if (centre == null) {
            throw new IllegalStateException("startDistance needs a centre");
        }
        return Math.hypot(startX - centre.x, startZ - centre.z);
    }

    /** {@link #radialMove} by the pose, for the log only (f32 rounded, see the class comment). */
    public double poseRadialMove() {
        if (centre == null) {
            return Double.NaN;
        }
        return Math.hypot(startX - centre.x, startZ - centre.z) - Math.hypot(poseX - centre.x, poseZ - centre.z);
    }

    /** {@link #travel} by the pose, for the log only. */
    public double poseTravel() {
        return Math.hypot(poseX - startX, poseZ - startZ);
    }

    /** Angle between the ship's up and world up [degrees]. */
    public double tilt() {
        if (ship.isRemoved()) {
            return Double.NaN;
        }
        Vector3d up = ship.orientation(new Quaterniond()).transform(new Vector3d(0, 1, 0));
        return Math.toDegrees(Math.acos(Math.min(1.0, up.y)));
    }

    /** The ship's centre of mass in world coordinates (the body's origin if Sable has no mass data yet). */
    public static Vector3d comWorld(ShipBody ship) {
        Vector3d c = new Vector3d();
        ship.centerOfMass(c);
        return ship.toWorld(c, new Vector3d());
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "x=%.4f z=%.4f d=(%.3f,%.3f) travel %.3f (pose %.3f)%s tilt %.1f, %d samples",
                poseX, poseZ, dx, dz, travel(), poseTravel(),
                centre == null ? "" : String.format(java.util.Locale.ROOT, " r-move %.3f (pose %.3f)", radialMove(), poseRadialMove()),
                tilt(), samples);
    }
}

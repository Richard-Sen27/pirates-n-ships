package com.richardsenger.piratesnships.sailing.waves;

import com.richardsenger.piratesnships.sailing.ship.BowFrame;
import org.joml.Vector3d;

/**
 * How waves rock a ship (docs/design.md §5.4, WV1). Pure.
 *
 * <h2>Rule</h2>
 * The wave height is sampled at four points of the hull's plot box ({@link #samplePoints}): the middle of the bow and
 * stern faces and of the port and starboard sides. Their differences give the surface slope the hull lies across:
 * <pre>
 *   roll slope  = (h_port − h_starboard) / beam span
 *   pitch slope = (h_bow  − h_stern)     / length span
 *   k           = ship_torque · mass · 1 / sqrt(blocks / 200)
 *   τ_roll      =  k · roll slope     (about the bow axis; + lifts the port side)
 *   τ_pitch     = −k · pitch slope    (about the port axis; − lifts the bow)
 * </pre>
 * and the pair is scaled down so that its magnitude stays at or below {@code max_torque_per_mass · mass}. The torque
 * turns the hull toward the slope of the water under it; Sable's buoyancy rights it and {@code HullDampingModel}
 * takes the energy out, so the hull rolls with the sea at a steady amplitude.
 *
 * <p><b>Why these factors:</b> the mass makes the response independent of the hull's weight (inertia and buoyant
 * righting moment both grow with it). Taking the slope across the sample points filters waves shorter than the hull:
 * a long ship spans most of a wave and barely pitches. {@code 1 / sqrt(blocks / 200)} lets big ships lie steadier
 * still and small boats bob more, and the cap keeps a dinghy from being flipped in a storm.
 */
public final class WaveTorqueRule {

    /** Block count at which the size scale is 1. */
    public static final double REFERENCE_BLOCKS = 200.0;

    /**
     * @param enabled          {@code waves.enabled}
     * @param shipTorque       {@code waves.ship_torque}: torque per unit slope per kpg
     * @param maxTorquePerMass {@code waves.max_torque_per_mass}
     */
    public record Params(boolean enabled, double shipTorque, double maxTorquePerMass) {

        public static final Params DEFAULTS = new Params(true, 5.5, 2.0);
    }

    /**
     * A wave torque in the ship frame.
     *
     * @param roll  about the bow axis ({@code ShipFrame.FORWARD}); positive lifts the port side
     * @param pitch about the port axis ({@code ShipFrame.PORT}); negative lifts the bow
     */
    public record Torque(double roll, double pitch) {

        public static final Torque ZERO = new Torque(0.0, 0.0);

        public double magnitude() {
            return Math.hypot(roll, pitch);
        }

        /** As a ship-frame vector (x port, y up, z forward). */
        public Vector3d toShipVector(Vector3d dest) {
            return dest.set(pitch, 0.0, roll);
        }
    }

    private WaveTorqueRule() {
    }

    /** {@code 1 / sqrt(blocks / 200)}; a ship of under one block counts as one. */
    public static double sizeScale(int blocks) {
        return 1.0 / Math.sqrt(Math.max(1, blocks) / REFERENCE_BLOCKS);
    }

    /** Surface slope across a span: {@code (high side − low side) / span}, 0 for a span of zero. */
    public static double slope(double hFirst, double hSecond, double span) {
        return span > 1.0e-6 ? (hFirst - hSecond) / span : 0.0;
    }

    /** The wave torque for the given slopes (see the class comment). Zero when disabled or massless. */
    public static Torque torque(double rollSlope, double pitchSlope, double mass, int blocks, Params p) {
        if (!p.enabled() || !(mass > 0.0) || !Double.isFinite(rollSlope) || !Double.isFinite(pitchSlope)) {
            return Torque.ZERO;
        }
        double k = p.shipTorque() * mass * sizeScale(blocks);
        double roll = k * rollSlope;
        double pitch = -k * pitchSlope;
        double max = Math.max(0.0, p.maxTorquePerMass()) * mass;
        double mag = Math.hypot(roll, pitch);
        if (mag > max) {
            double s = mag > 0.0 ? max / mag : 0.0;
            roll *= s;
            pitch *= s;
        }
        return new Torque(roll, pitch);
    }

    /**
     * The four sample points of a plot box, in plot coordinates at plot height {@code y}: {@code [bow, stern, port,
     * starboard]}, each the middle of that face of the box {@code {minX, minY, minZ, maxX, maxY, maxZ}} (inclusive
     * block coordinates).
     */
    public static Vector3d[] samplePoints(BowFrame bow, int[] bounds, double y) {
        double cx = (bounds[0] + bounds[3] + 1) * 0.5;
        double cz = (bounds[2] + bounds[5] + 1) * 0.5;
        double sizeX = bounds[3] - bounds[0] + 1;
        double sizeZ = bounds[5] - bounds[2] + 1;
        double halfLength = bow.lengthOf(sizeX, sizeZ) * 0.5;
        double halfBeam = bow.beamOf(sizeX, sizeZ) * 0.5;
        Vector3d fwd = bow.toPlot(new Vector3d(0, 0, 1), new Vector3d());
        Vector3d port = bow.toPlot(new Vector3d(1, 0, 0), new Vector3d());
        return new Vector3d[] {
                new Vector3d(cx + fwd.x * halfLength, y, cz + fwd.z * halfLength),
                new Vector3d(cx - fwd.x * halfLength, y, cz - fwd.z * halfLength),
                new Vector3d(cx + port.x * halfBeam, y, cz + port.z * halfBeam),
                new Vector3d(cx - port.x * halfBeam, y, cz - port.z * halfBeam)};
    }

    /** The middle of the plot box at plot height {@code y}. */
    public static Vector3d center(int[] bounds, double y) {
        return new Vector3d((bounds[0] + bounds[3] + 1) * 0.5, y, (bounds[2] + bounds[5] + 1) * 0.5);
    }
}

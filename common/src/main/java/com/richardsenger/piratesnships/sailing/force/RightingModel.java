package com.richardsenger.piratesnships.sailing.force;

import org.joml.Vector3d;

/**
 * The hull's <b>righting moment</b> and the cap on the sails' heeling moment (docs/design.md §5.2 "Heel", SH1). Pure.
 *
 * <h2>Why</h2>
 * Sable floats only the solid hull blocks, each at its block centre, with whole-cube water (docs/sable-notes.md §4.1).
 * A block hull therefore has a small and stair-stepped righting moment: the starter sloop (320 kpg, 9 wide, the centre
 * of mass 4.7 blocks above the keel) showed an effective metacentric height of about 1.1 blocks at 1° of heel, 0.65 at
 * 9°, none at all around 4° (it stayed heeled 4° with the sails furled), and it capsized past about 20° in the strongest
 * wind (SH1 measurement). Our dry-volume lift was no help: the sloop's hold barely reaches below the waterline (7 to 22
 * of 3520 N). This model adds what a real hull's waterplane gives: a torque that grows with the heel.
 *
 * <h2>Righting torque (ship frame, about the centre of mass)</h2>
 * <pre>
 *   GM_roll  = clamp(B² / (12 · T) · righting_factor, 0, max_metacentric_height)
 *   GM_pitch = clamp(L² / (12 · T) · righting_factor · pitch_righting_factor, 0, max_metacentric_height)
 *   K        = m · g · GM                                        [N·block per radian]
 *   τ_roll   = −s · (K_roll  · clamp(φ, ±max_righting) + c_roll  · ω_roll)
 *   τ_pitch  = −s · (K_pitch · clamp(θ, ±max_righting) + c_pitch · ω_pitch)
 *   c        = 2 · ζ · √(K · I),   I_roll ≈ m (B² + H²) / 12,   I_pitch ≈ m (L² + H²) / 12
 * </pre>
 * {@code B}, {@code L}, {@code H} are the beam, length and height of the ship, {@code T} its draft (at least
 * {@link #MIN_DRAFT}), {@code m} the mass, {@code g} the gravity, {@code s} the submerged fraction, {@code φ} the heel
 * (rotation about {@link ShipFrame#FORWARD}, + = port side up) and {@code θ} the trim (rotation about
 * {@link ShipFrame#PORT}, + = bow down), both measured against world up. {@code B² / 12T} is the metacentric radius of a
 * box hull (the waterplane's second moment over the displaced volume): the part of the stability that Sable's
 * block buoyancy lacks. The damping term with ratio {@code ζ} ({@code righting_damping}) acts on top of
 * {@link HullDampingModel}, so a ship comes back upright without swinging through.
 *
 * <p>Zero at zero heel and at rest, linear in the angle up to {@code max_righting_degrees}, constant beyond.
 *
 * <p><b>Pitch is off by default</b> ({@code pitch_righting_factor} 0): Sable's buoyancy already holds a block hull's
 * trim along its length (the sloop rests 1.9° bow up and stays there under sail), and a trim torque at the roll's
 * strength made the small 5×5 test hulls so stiff in pitch that a chest of iron no longer trimmed one (cargo), a
 * hauled ship stopped short of its kedge (grapple) and pushed ships moved less (mobs) (SH1 measurement).
 *
 * <h2>Heeling cap</h2>
 * {@link #limitHeel} caps the roll and pitch moment of the sails (and keel) at {@code max_heel_torque_per_mass × m}
 * and fades the part that heels the ship further over to zero over the last {@link #FADE_DEGREES} before
 * {@code max_heel_degrees}, as a heeled sail spills its wind. A moment that rights the ship is never faded.
 */
public final class RightingModel {

    public static final String SOURCE = "righting";
    /** Smallest draft used in the metacentric height, so a ship lifting out of the water does not get a huge GM [blocks]. */
    public static final double MIN_DRAFT = 0.5;
    /** Width of the band below {@code max_heel_degrees} over which the heeling moment fades out [degrees]. */
    public static final double FADE_DEGREES = 10.0;

    /**
     * Stability tuning (server config section {@code stability}).
     *
     * @param enabled                whether the righting torque and the heel cap are applied
     * @param rightingFactor         multiplier on the box-hull metacentric radius {@code B² / 12T}
     * @param pitchRightingFactor    multiplier on {@code rightingFactor} for the trim (pitch) axis; 0 = roll only
     * @param maxRightingDegrees     the righting torque grows linearly up to this heel, and stays constant beyond [degrees]
     * @param maxMetacentricHeight   upper limit of the estimated metacentric height [blocks]
     * @param rightingDamping        damping ratio ζ of the righting motion (1 = critical) on top of the hull damping
     * @param maxHeelTorquePerMass   cap of the sails' heeling (and pitching) moment per kpg of mass [blocks²/s²]
     * @param maxHeelDegrees         the sails' heeling moment fades to zero toward this heel [degrees]
     */
    public record Params(boolean enabled, double rightingFactor, double pitchRightingFactor, double maxRightingDegrees,
                         double maxMetacentricHeight, double rightingDamping, double maxHeelTorquePerMass, double maxHeelDegrees) {

        /** The defaults; the server config declares its defaults from this instance. Chosen by measurement (SH1). */
        public static final Params DEFAULTS = new Params(true, 1.0, 0.0, 30.0, 4.0, 0.4, 3.0, 25.0);

        public Params {
            rightingFactor = Math.max(0.0, rightingFactor);
            pitchRightingFactor = Math.max(0.0, pitchRightingFactor);
            maxRightingDegrees = Math.min(Math.max(0.0, maxRightingDegrees), 180.0);
            maxMetacentricHeight = Math.max(0.0, maxMetacentricHeight);
            rightingDamping = Math.max(0.0, rightingDamping);
            maxHeelTorquePerMass = Math.max(0.0, maxHeelTorquePerMass);
            maxHeelDegrees = Math.min(Math.max(0.0, maxHeelDegrees), 180.0);
        }
    }

    /**
     * The hull's size for this model.
     *
     * @param beam   width across the bow axis [blocks]
     * @param length bow-to-stern length [blocks]
     * @param height keel-to-masthead height [blocks] (sets the estimated moments of inertia)
     * @param draft  depth of the hull bottom below the sea [blocks]
     */
    public record Hull(double beam, double length, double height, double draft) {
    }

    private RightingModel() {
    }

    /** Estimated metacentric height {@code clamp(width² / (12 · max(draft, MIN_DRAFT)) · righting_factor, 0, max)} [blocks]. */
    public static double metacentricHeight(double width, double draft, Params p) {
        return metacentricHeight(width, draft, p.rightingFactor(), p);
    }

    private static double metacentricHeight(double width, double draft, double factor, Params p) {
        double w = Math.max(0.0, width);
        double gm = w * w / (12.0 * Math.max(MIN_DRAFT, draft)) * factor;
        return Math.min(Math.max(0.0, gm), p.maxMetacentricHeight());
    }

    /** The trim axis' metacentric height: as {@link #metacentricHeight} with the length and × {@code pitch_righting_factor}. */
    public static double pitchMetacentricHeight(double length, double draft, Params p) {
        return metacentricHeight(length, draft, p.rightingFactor() * p.pitchRightingFactor(), p);
    }

    /** Heel φ [rad]: rotation about the bow axis against world up, + = port side up. */
    public static double roll(ShipState ship) {
        Vector3d up = ship.toLocal(new Vector3d(0, 1, 0), new Vector3d());
        return Math.atan2(up.x, up.y);
    }

    /** Trim θ [rad]: rotation about the port axis against world up, + = bow down. */
    public static double pitch(ShipState ship) {
        Vector3d up = ship.toLocal(new Vector3d(0, 1, 0), new Vector3d());
        return Math.atan2(-up.z, up.y);
    }

    /**
     * Righting torque about one axis: {@code −(K · clamp(angle) + c · rate)} with {@code K = m g GM} and
     * {@code c = 2 ζ √(K I)}.
     *
     * @param angle   heel about the axis [rad]
     * @param rate    angular velocity about the axis [rad/s]
     * @param mass    [kpg]
     * @param gravity [blocks/s²]
     * @param gm      metacentric height [blocks]
     * @param inertia moment of inertia about the axis [kpg·blocks²]
     */
    public static double axisTorque(double angle, double rate, double mass, double gravity, double gm, double inertia, Params p) {
        double k = mass * gravity * gm;
        if (!(k > 0.0)) {
            return 0.0;
        }
        double max = Math.toRadians(p.maxRightingDegrees());
        double c = 2.0 * p.rightingDamping() * Math.sqrt(k * Math.max(0.0, inertia));
        return -(k * Math.min(Math.max(angle, -max), max) + c * rate);
    }

    /**
     * The righting torque on a floating ship: a pure torque at the centre of mass (force zero), ship frame, labelled
     * {@link #SOURCE}. Zero when disabled or out of the water.
     *
     * @param gravity the magnitude of the level's gravity [blocks/s²]
     */
    public static ForceContribution compute(ShipState ship, Hull hull, double gravity, Params p) {
        Vector3d origin = new Vector3d();
        if (!p.enabled() || ship.submergedFraction() <= 0.0 || !(gravity > 0.0)) {
            return ForceContribution.zero(SOURCE, origin);
        }
        double m = ship.mass();
        double h2 = hull.height() * hull.height();
        Vector3d w = ship.toLocal(ship.angularVelocity(), new Vector3d());
        double roll = axisTorque(roll(ship), w.dot(ShipFrame.FORWARD), m, gravity, metacentricHeight(hull.beam(), hull.draft(), p),
                m * (hull.beam() * hull.beam() + h2) / 12.0, p);
        double pitch = axisTorque(pitch(ship), w.dot(ShipFrame.PORT), m, gravity, pitchMetacentricHeight(hull.length(), hull.draft(), p),
                m * (hull.length() * hull.length() + h2) / 12.0, p);
        double s = ship.submergedFraction();
        Vector3d torque = new Vector3d(ShipFrame.FORWARD).mul(roll * s).add(new Vector3d(ShipFrame.PORT).mul(pitch * s));
        return new ForceContribution(SOURCE, new Vector3d(), origin, torque);
    }

    /**
     * Limits a heeling moment about one axis: capped at {@code ±max_heel_torque_per_mass · mass}, and, when it turns the
     * ship further toward the side it already leans to, faded linearly to zero between
     * {@code max_heel_degrees − FADE_DEGREES} and {@code max_heel_degrees}. Unchanged when disabled.
     *
     * @param torque the moment about the axis (same sign convention as {@code angle})
     * @param angle  the ship's angle about the axis [rad]
     */
    public static double limitHeel(double torque, double angle, double mass, Params p) {
        if (!p.enabled()) {
            return torque;
        }
        double cap = p.maxHeelTorquePerMass() * Math.max(0.0, mass);
        double t = Math.min(Math.max(torque, -cap), cap);
        if (t * angle > 0.0) {
            double max = p.maxHeelDegrees();
            double band = Math.min(FADE_DEGREES, max);
            double deg = Math.toDegrees(Math.abs(angle));
            double fade = band <= 0.0 ? (deg < max ? 1.0 : 0.0) : Math.min(Math.max((max - deg) / band, 0.0), 1.0);
            t *= fade;
        }
        return t;
    }
}

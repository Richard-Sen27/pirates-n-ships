package com.richardsenger.piratesnships.sailing.force;

/**
 * Tuning values of the sailing force model (docs/design.md §5.2, §5.3). A plain value object so the pure model never
 * touches config state; {@code SailingConfig.sailingParams()} fills one from the server config.
 *
 * <p>Mass is Sable's "kpg" (≈ kg, a default block weighs 1), so forces are in kpg·blocks/s² ("N").
 *
 * @param sailForceScale       sail force per (blocks/s of apparent wind × blocks² of sail × efficiency) [N·s/block³]
 * @param halfTrimFactor       area fraction of a half-trimmed sail
 * @param rudderStrength       rudder side force per (kpg of mass × blocks/s of forward speed × sin(rudder angle)) [1/s]
 * @param maxRudderAngleDeg    rudder angles are clamped to ±this [degrees]
 * @param keelEnabled          whether the keel drag is applied at all
 * @param keelLongitudinalDrag drag along the hull per kpg, at full immersion [1/s]
 * @param keelLateralDrag      drag across the hull per kpg, at full immersion [1/s]
 * @param keelYawDragFactor    multiplier on the yaw damping that the lateral drag implies for a hull of the given length
 * @param anchor               anchor tuning
 */
public record SailingParams(
        double sailForceScale,
        double halfTrimFactor,
        double rudderStrength,
        double maxRudderAngleDeg,
        boolean keelEnabled,
        double keelLongitudinalDrag,
        double keelLateralDrag,
        double keelYawDragFactor,
        AnchorParams anchor) {

    /** The defaults. The server config declares its defaults from this instance. */
    public static final SailingParams DEFAULTS = new SailingParams(
            1.0, 0.5,
            0.5, 35.0,
            true, 0.1, 8.0, 1.0, // lateral 8.0 since spike 3 (2.0 drifted 0.7 m/s on a beam reach)
            AnchorParams.DEFAULTS);

    public SailingParams {
        sailForceScale = Math.max(0.0, sailForceScale);
        halfTrimFactor = Math.min(Math.max(0.0, halfTrimFactor), 1.0);
        rudderStrength = Math.max(0.0, rudderStrength);
        maxRudderAngleDeg = Math.min(Math.max(0.0, maxRudderAngleDeg), 90.0);
        keelLongitudinalDrag = Math.max(0.0, keelLongitudinalDrag);
        keelLateralDrag = Math.max(0.0, keelLateralDrag);
        keelYawDragFactor = Math.max(0.0, keelYawDragFactor);
    }

    public SailingParams withKeelEnabled(boolean on) {
        return new SailingParams(sailForceScale, halfTrimFactor, rudderStrength, maxRudderAngleDeg, on,
                keelLongitudinalDrag, keelLateralDrag, keelYawDragFactor, anchor);
    }

    public SailingParams withSailForceScale(double scale) {
        return new SailingParams(scale, halfTrimFactor, rudderStrength, maxRudderAngleDeg, keelEnabled,
                keelLongitudinalDrag, keelLateralDrag, keelYawDragFactor, anchor);
    }

    /**
     * Anchor tuning.
     *
     * @param stiffness       pull toward the anchor per kpg and per block beyond the slack [1/s²]
     * @param damping         horizontal velocity damping per kpg while the anchor holds [1/s]
     * @param maxAcceleration cap of the anchor force divided by mass [blocks/s²]
     * @param slack           rope slack: distance from the anchor point the ship may drift freely [blocks]
     * @param dropTicks       time from "drop" until the anchor holds fully [ticks]
     * @param raiseTicks      time from "raise" until the anchor is stowed [ticks]
     */
    public record AnchorParams(double stiffness, double damping, double maxAcceleration, double slack,
                               int dropTicks, int raiseTicks) {

        public static final AnchorParams DEFAULTS = new AnchorParams(0.5, 1.5, 6.0, 2.0, 40, 100);

        public AnchorParams {
            stiffness = Math.max(0.0, stiffness);
            damping = Math.max(0.0, damping);
            maxAcceleration = Math.max(0.0, maxAcceleration);
            slack = Math.max(0.0, slack);
            dropTicks = Math.max(1, dropTicks);
            raiseTicks = Math.max(1, raiseTicks);
        }
    }
}

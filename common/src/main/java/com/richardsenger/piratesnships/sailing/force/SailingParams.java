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
 *
 * <p>The anchor is not part of this model since AN2a: its chain force has its own force group and tuning
 * ({@code sailing.anchor.AnchorChain}, {@code AnchorConfig}).
 */
public record SailingParams(
        double sailForceScale,
        double halfTrimFactor,
        double rudderStrength,
        double maxRudderAngleDeg,
        boolean keelEnabled,
        double keelLongitudinalDrag,
        double keelLateralDrag,
        double keelYawDragFactor) {

    /** The defaults. The server config declares its defaults from this instance. */
    public static final SailingParams DEFAULTS = new SailingParams(
            1.0, 0.5,
            0.5, 35.0,
            true, 0.1, 8.0, 1.0); // lateral 8.0 since spike 3 (2.0 drifted 0.7 m/s on a beam reach)

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
                keelLongitudinalDrag, keelLateralDrag, keelYawDragFactor);
    }

    public SailingParams withSailForceScale(double scale) {
        return new SailingParams(scale, halfTrimFactor, rudderStrength, maxRudderAngleDeg, keelEnabled,
                keelLongitudinalDrag, keelLateralDrag, keelYawDragFactor);
    }
}

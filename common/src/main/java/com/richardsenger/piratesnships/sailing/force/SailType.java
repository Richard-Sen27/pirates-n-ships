package com.richardsenger.piratesnships.sailing.force;

/**
 * A sail kind as data (docs/design.md §5.2). Kept as a plain record so it can become a datapack definition later.
 *
 * @param id                   stable id, e.g. {@code "square"}
 * @param area                 sail area [blocks²] of a one-block sail of this type; a square sail's real area comes
 *                             from its yards ({@link SailInstance#area()}), and this is only its reference value
 * @param centerOfEffortHeight height of the center of effort above the sail's anchor position [blocks]; the force is
 *                             applied there, which is what produces the heel torque
 * @param curve                efficiency over the apparent wind angle
 */
public record SailType(String id, double area, double centerOfEffortHeight, EfficiencyCurve curve) {

    public SailType {
        if (area < 0.0) {
            throw new IllegalArgumentException("Sail area must not be negative");
        }
    }
}

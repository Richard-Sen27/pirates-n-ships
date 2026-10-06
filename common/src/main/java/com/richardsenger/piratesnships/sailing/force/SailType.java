package com.richardsenger.piratesnships.sailing.force;

/**
 * A sail kind as data (docs/design.md §5.2). Kept as a plain record so it can become a datapack definition later.
 *
 * @param id                   stable id, e.g. {@code "large_square"}
 * @param area                 sail area [blocks²]
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

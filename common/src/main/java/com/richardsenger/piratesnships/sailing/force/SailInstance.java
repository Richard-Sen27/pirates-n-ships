package com.richardsenger.piratesnships.sailing.force;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * One sail on a ship.
 *
 * @param type     sail kind (efficiency curve and center of effort height)
 * @param area     sail area [blocks²]; a square sail's comes from its yards (docs/design.md §5.2, rule F5a), a
 *                 triangular sail's from its triangle (rule F5b)
 * @param trim     current trim
 * @param position sail anchor position relative to the center of mass, ship frame [blocks]; the force acts
 *                 {@link SailType#centerOfEffortHeight()} above it (a square sail passes the centroid of its drawn
 *                 cloth here, and its type has a height of 0)
 * @param boom     the boom of a boomed sail (SAIL5), or null: its force is scaled by the boom's angle to the wind
 */
public record SailInstance(SailType type, double area, SailTrim trim, Vector3dc position, @Nullable Boom boom) {

    /**
     * A sail's boom in the ship frame (SAIL5): the vector from its pivot to its end, {@code (port, forward)}, and the
     * closest angle off the wind it draws at ({@code sailing.boom_min_wind_angle}).
     */
    public record Boom(double port, double forward, double minWindDegrees) {
    }

    public SailInstance {
        if (!(area >= 0.0)) {
            throw new IllegalArgumentException("Sail area must not be negative: " + area);
        }
        position = new Vector3d(position);
    }

    /** A sail without a boom. */
    public SailInstance(SailType type, double area, SailTrim trim, Vector3dc position) {
        this(type, area, trim, position, null);
    }

    /** A sail with its type's reference area (tests). */
    public SailInstance(SailType type, SailTrim trim, Vector3dc position) {
        this(type, type.area(), trim, position);
    }
}

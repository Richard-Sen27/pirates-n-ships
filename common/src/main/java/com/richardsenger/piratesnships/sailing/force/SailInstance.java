package com.richardsenger.piratesnships.sailing.force;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * One sail on a ship.
 *
 * @param type     sail kind (efficiency curve and center of effort height)
 * @param area     sail area [blocks²]; a square sail's comes from its yards (docs/design.md §5.2, rule F5a), a one-block
 *                 sail's from its {@link SailType#area()}
 * @param trim     current trim
 * @param position sail anchor position relative to the center of mass, ship frame [blocks]; the force acts
 *                 {@link SailType#centerOfEffortHeight()} above it (a square sail passes the centroid of its drawn
 *                 cloth here, and its type has a height of 0)
 */
public record SailInstance(SailType type, double area, SailTrim trim, Vector3dc position) {

    public SailInstance {
        if (!(area >= 0.0)) {
            throw new IllegalArgumentException("Sail area must not be negative: " + area);
        }
        position = new Vector3d(position);
    }

    /** A sail with its type's area (the one-block fore-and-aft sail). */
    public SailInstance(SailType type, SailTrim trim, Vector3dc position) {
        this(type, type.area(), trim, position);
    }
}

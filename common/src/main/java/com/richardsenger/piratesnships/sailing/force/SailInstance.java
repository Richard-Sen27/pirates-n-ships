package com.richardsenger.piratesnships.sailing.force;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * One sail on a ship.
 *
 * @param type     sail kind
 * @param trim     current trim
 * @param position sail anchor position (e.g. the yard or sail block) relative to the center of mass, ship frame
 *                 [blocks]; the force acts {@link SailType#centerOfEffortHeight()} above it
 */
public record SailInstance(SailType type, SailTrim trim, Vector3dc position) {

    public SailInstance {
        position = new Vector3d(position);
    }
}

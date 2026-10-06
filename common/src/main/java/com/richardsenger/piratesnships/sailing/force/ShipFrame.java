package com.richardsenger.piratesnships.sailing.force;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * The <b>ship frame</b> every sailing calculation works in: origin at the center of mass, {@code +Z} = bow
 * (forward), {@code +Y} = up (mast direction), {@code −X} = starboard (right when facing the bow), {@code +X} = port.
 * Right-handed, the same handedness as Minecraft and Sable.
 *
 * <p>Sable's body ("plot") frame is axis-aligned with the blocks of the ship but its bow can point along any
 * horizontal axis. The ship integration therefore passes the orientation <em>ship frame → world</em> (Sable pose
 * orientation × a fixed plot→ship rotation chosen from the helm facing) and rotates results back into the plot frame
 * before applying them.
 */
public final class ShipFrame {

    public static final Vector3dc FORWARD = new Vector3d(0, 0, 1);
    public static final Vector3dc UP = new Vector3d(0, 1, 0);
    public static final Vector3dc STARBOARD = new Vector3d(-1, 0, 0);
    public static final Vector3dc PORT = new Vector3d(1, 0, 0);

    private ShipFrame() {
    }
}

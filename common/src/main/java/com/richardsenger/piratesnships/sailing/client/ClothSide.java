package com.richardsenger.piratesnships.sailing.client;

import com.richardsenger.piratesnships.sailing.wind.WindSample;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/**
 * Which side of its rig a sail's cloth bellies out to (docs/design.md §5.2): the <b>downwind</b> side, the side the
 * wind blows <em>toward</em>. Pure; shared by {@link YardClothRenderer} and {@link StayClothRenderer}.
 *
 * <p>Conventions, the same as the server's force model ({@code SailForceModel}, which treats {@code trueWind} as the
 * flow velocity): {@link WindSample#dirX()}/{@link WindSample#dirZ()} is the direction the wind blows toward, so
 * {@code /pirates wind set 270} (wind <em>from</em> the west) blows toward +X (east). The cloth's {@code out} axis is
 * given in the block-local (plot) frame and turned into the world by the ship's pose orientation (plot → world, the
 * same rotation Sable puts on the pose stack when it draws the ship's block entities).
 */
public final class ClothSide {

    /** |cos| of the wind against the cloth's out axis below which the cloth keeps its side (hysteresis). */
    public static final double SWITCH = 0.15;

    private ClothSide() {
    }

    /**
     * The new side ({@code +1}: the cloth bellies along {@code +out}, {@code -1}: along {@code -out}).
     *
     * @param current     the side shown so far
     * @param outLocal    the cloth's out axis in the block-local frame (only x and z matter)
     * @param plotToWorld the ship's orientation, or null on land
     * @param windTowardX x of the direction the wind blows toward ({@link WindSample#dirX()})
     * @param windTowardZ z of the direction the wind blows toward ({@link WindSample#dirZ()})
     */
    public static int side(int current, Vector3d outLocal, @Nullable Quaterniondc plotToWorld, double windTowardX, double windTowardZ) {
        Vector3d out = new Vector3d(outLocal);
        if (plotToWorld != null) {
            plotToWorld.transform(out);
        }
        double h = Math.hypot(out.x, out.z);
        if (h < 1.0e-6) {
            return current;
        }
        double dot = (out.x * windTowardX + out.z * windTowardZ) / h;
        if (dot > SWITCH) {
            return 1;
        }
        if (dot < -SWITCH) {
            return -1;
        }
        return current;
    }

    /** The out axis of a square sail's cloth in the head's block frame: across the yards. */
    public static Vector3d squareSailOut(boolean yardsAlongX) {
        return yardsAlongX ? new Vector3d(0, 0, 1) : new Vector3d(1, 0, 0);
    }

    /** The world direction the cloth bellies to for {@code side} (for tests and debug). */
    public static Vector3d bellyWorld(int side, Vector3d outLocal, @Nullable Quaterniondc plotToWorld) {
        Vector3d out = new Vector3d(outLocal).mul(side);
        return plotToWorld == null ? out : plotToWorld.transform(out);
    }
}

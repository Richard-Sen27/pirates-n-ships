package com.richardsenger.piratesnships.combat.firearms;

/**
 * Pure rules of firearms (docs/design.md §8.1): misfire, spread, reload progress and the ammunition check. No world
 * access; random values come in as parameters so the rules are deterministic in tests.
 */
public final class FirearmRules {

    private FirearmRules() {
    }

    /**
     * True when a shot misfires: only in the rain, and when {@code roll} (uniform in [0, 1)) is below {@code chance}.
     * A chance of 1 always misfires in the rain, a chance of 0 never does.
     */
    public static boolean misfires(boolean inRain, double chance, double roll) {
        return inRain && chance > 0 && roll < chance;
    }

    /**
     * The shot's rotation {@code {xRot, yRot}} in degrees (Minecraft convention: positive xRot looks down) after a
     * random deviation inside a cone of {@code spreadDegrees} around the aim. {@code u} and {@code v} are uniform in
     * [0, 1): {@code u} picks the angle from the aim (square root, so shots are uniform over the cone's disc),
     * {@code v} the direction around it. The look vector is turned in 3D, so the deviation never exceeds the spread,
     * also when aiming steeply up or down. The returned yaw stays within 180 degrees of the input yaw.
     */
    public static float[] spreadRotation(float xRot, float yRot, double spreadDegrees, double u, double v) {
        if (spreadDegrees <= 0) return new float[]{xRot, yRot};
        double radius = Math.toRadians(spreadDegrees) * Math.sqrt(Math.clamp(u, 0.0, 1.0));
        double around = 2.0 * Math.PI * v;
        double[] d = direction(xRot, yRot);
        // right = d x worldUp, or any horizontal axis when aiming straight up or down
        double rx = -d[2];
        double rz = d[0];
        double rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-9) {
            rx = 1;
            rz = 0;
            rl = 1;
        }
        rx /= rl;
        rz /= rl;
        // up' = right x d
        double ux = -rz * d[1];
        double uy = rz * d[0] - rx * d[2];
        double uz = rx * d[1];
        double c = Math.cos(radius);
        double s = Math.sin(radius);
        double ca = Math.cos(around);
        double sa = Math.sin(around);
        double nx = d[0] * c + (rx * ca + ux * sa) * s;
        double ny = d[1] * c + (uy * sa) * s;
        double nz = d[2] * c + (rz * ca + uz * sa) * s;
        double pitch = -Math.toDegrees(Math.asin(Math.clamp(ny, -1.0, 1.0)));
        double yaw = Math.toDegrees(Math.atan2(-nx, nz));
        double dYaw = yaw - yRot;
        dYaw -= 360.0 * Math.floor((dYaw + 180.0) / 360.0);
        return new float[]{(float) pitch, (float) (yRot + dYaw)};
    }

    /** The unit look vector {@code {x, y, z}} of a rotation, the same formula as vanilla's {@code calculateViewVector}. */
    public static double[] direction(float xRot, float yRot) {
        double pitch = Math.toRadians(xRot);
        double yaw = Math.toRadians(-yRot);
        double cp = Math.cos(pitch);
        return new double[]{Math.sin(yaw) * cp, -Math.sin(pitch), Math.cos(yaw) * cp};
    }

    /** Angle in degrees between two rotations' look vectors. */
    public static double angleBetween(float xRotA, float yRotA, float xRotB, float yRotB) {
        double[] a = direction(xRotA, yRotA);
        double[] b = direction(xRotB, yRotB);
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        return Math.toDegrees(Math.acos(Math.clamp(dot, -1.0, 1.0)));
    }

    /** Loading progress from 0 to 1 after {@code usedTicks} of holding use. */
    public static float reloadProgress(int usedTicks, int reloadTicks) {
        if (reloadTicks <= 0) return 1.0f;
        return Math.clamp((float) usedTicks / reloadTicks, 0.0f, 1.0f);
    }

    /** True once the gun has been held for the whole reload time. */
    public static boolean reloadComplete(int usedTicks, int reloadTicks) {
        return usedTicks >= reloadTicks;
    }

    /**
     * True when a gun can be loaded: always with infinite materials (creative), otherwise one lead shot and, if
     * gunpowder is consumed, one gunpowder.
     */
    public static boolean canLoad(boolean infiniteMaterials, int leadShot, int gunpowder, boolean needsGunpowder) {
        if (infiniteMaterials) return true;
        return leadShot > 0 && (!needsGunpowder || gunpowder > 0);
    }

    // ---- holding: loading and aiming sessions ----

    /**
     * Use duration of a loading session (an unloaded gun held down). Loading completes after the reload time inside
     * the session; the player keeps "using" the gun until they let go, so holding on does not start an aim.
     */
    public static final int LOAD_SESSION_TICKS = 36000;
    /** Use duration of an aiming session (a loaded gun held down); the shot leaves when the player lets go. */
    public static final int AIM_SESSION_TICKS = 72000;

    /** The use duration for a session that starts with the gun loaded or not. */
    public static int sessionTicks(boolean loadedAtStart) {
        return loadedAtStart ? AIM_SESSION_TICKS : LOAD_SESSION_TICKS;
    }

    /**
     * True when a use session with {@code remainingTicks} left started as an aim (the gun was loaded when the player
     * pressed use). Stateless: aim sessions are longer than loading sessions, so their remaining time is too, even
     * after a loading session turned the gun loaded in the middle.
     */
    public static boolean isAimSession(int remainingTicks) {
        return remainingTicks > LOAD_SESSION_TICKS;
    }

    /** Ticks the gun has been held in the session that has {@code remainingTicks} left. */
    public static int heldTicks(int remainingTicks) {
        return (isAimSession(remainingTicks) ? AIM_SESSION_TICKS : LOAD_SESSION_TICKS) - remainingTicks;
    }

    /** True when letting go of an aiming gun after {@code heldTicks} fires it (held at least {@code minTicks}). */
    public static boolean firesOnRelease(int heldTicks, int minTicks) {
        return heldTicks >= minTicks;
    }

    /**
     * The spread of a shot after aiming for {@code heldTicks}: multiplied by {@code aimedFactor} once the aim has been
     * held at least {@code steadyTicks}, the full spread before.
     */
    public static double aimedSpread(double spreadDegrees, int heldTicks, int steadyTicks, double aimedFactor) {
        return heldTicks >= steadyTicks ? spreadDegrees * aimedFactor : spreadDegrees;
    }

    /** The field-of-view modifier while aiming with {@code zoom} (FOV divided by it); a zoom of 1 or less changes nothing. */
    public static float zoomedFov(float fovModifier, double zoom) {
        return zoom <= 1.0 ? fovModifier : (float) (fovModifier / zoom);
    }
}

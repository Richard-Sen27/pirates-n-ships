package com.richardsenger.piratesnships.ship.decor;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * The swing of the ship's bell (BELL1, design.md §4.8 "Decor"), pure math for {@code ShipsBellRenderer}. The bell hangs
 * from the pin of its yoke, which runs across the mount (along x of the north-facing model), so it only swings towards
 * the front or the back of its mount. Like vanilla's {@code BellRenderer} the swing is a damped sine over the ring
 * time ({@code sin(t / pi) / (4 + t / 3)}), first away from the side it was struck on; here it is scaled so its first
 * (largest) swing reaches exactly the configured amplitude and faded out over the second half of
 * {@code ship_decor.bell_ring_ticks}, so the bell is at rest when the ring ends. The clapper trails the bell by
 * {@link #CLAPPER_LAG_TICKS}, never more than {@link #CLAPPER_MAX_LAG_DEGREES} from it (it stays under the mouth).
 *
 * <p>Angles are "lip towards the back" in degrees: positive swings the bell's mouth towards the back of the mount (the
 * wall behind a wall bell, the side away from the player who placed a bell on a post), negative towards its front.
 */
public final class ShipsBellSwing {

    /** The pin of the yoke in the post model, px: the bell and clapper models turn about the x axis through here. */
    public static final double PIN_Y = 13.0;
    public static final double PIN_Z = 8.0;
    /** On the wall bracket the bell and clapper hang this many px lower than on the post (the pin under the arm). */
    public static final double WALL_DROP = 1.2;

    /** How far behind the bell the clapper swings, in ticks. */
    public static final double CLAPPER_LAG_TICKS = 1.5;
    /** The clapper never leaves the bell's mouth: at most this far from the bell's angle. */
    public static final double CLAPPER_MAX_LAG_DEGREES = 12.0;
    /** A strike on the side of the bell (along its pin) only rocks it this much of a full swing. */
    public static final double SIDE_STRIKE_SCALE = 0.5;

    /** Scales vanilla's damped sine so its first peak is 1. */
    private static final double PEAK = 1.0 / firstPeak();

    private ShipsBellSwing() {
    }

    /** Vanilla's bell curve, unscaled: {@code sin(t / pi) / (4 + t / 3)}, times 4 so it starts like a plain sine. */
    static double raw(double t) {
        return Math.sin(t / Math.PI) * 4.0 / (4.0 + t / 3.0);
    }

    private static double firstPeak() {
        double best = 0.0;
        for (double t = 0.0; t <= Math.PI * Math.PI; t += 0.001) {
            best = Math.max(best, raw(t));
        }
        return best;
    }

    /** 1 over the first half of the ring, then a smooth step down to 0 at its end. */
    static double fade(double t, int ringTicks) {
        double u = t / ringTicks;
        if (u <= 0.5) return 1.0;
        if (u >= 1.0) return 0.0;
        double x = (u - 0.5) / 0.5;
        return 1.0 - x * x * (3.0 - 2.0 * x);
    }

    /**
     * The bell's angle {@code t} ticks after it was rung, lip towards the back in degrees, for a strike with
     * {@link #direction} {@code sign} and an amplitude of {@code swingDegrees}; 0 before the ring and from
     * {@code ringTicks} on.
     */
    public static double bellDegrees(double t, int ringTicks, double swingDegrees, double sign) {
        if (t <= 0.0 || t >= ringTicks || ringTicks <= 0) return 0.0;
        return sign * swingDegrees * PEAK * raw(t) * fade(t, ringTicks);
    }

    /** The clapper's angle: the bell's of {@link #CLAPPER_LAG_TICKS} ago, at most {@link #CLAPPER_MAX_LAG_DEGREES} off the bell's. */
    public static double clapperDegrees(double t, int ringTicks, double swingDegrees, double sign) {
        double bell = bellDegrees(t, ringTicks, swingDegrees, sign);
        double trailing = bellDegrees(t - CLAPPER_LAG_TICKS, ringTicks, swingDegrees, sign);
        return Math.max(bell - CLAPPER_MAX_LAG_DEGREES, Math.min(bell + CLAPPER_MAX_LAG_DEGREES, trailing));
    }

    /** True while the bell or its clapper is still off rest. */
    public static boolean swinging(double t, int ringTicks) {
        return t > 0.0 && t < ringTicks + CLAPPER_LAG_TICKS;
    }

    /**
     * The transform the renderer applies to the bell or clapper model (block units, like a {@code PoseStack} entry):
     * the block state's y turn (north is the model as built, each quarter turn clockwise seen from above), the wall
     * mount's drop, then {@code lipBackDegrees} about the x axis through the pin. Rotating -y towards +z about +x needs a
     * negative angle, so the turn is by {@code -lipBackDegrees}.
     */
    public static Matrix4f partPose(Direction facing, boolean wall, double lipBackDegrees) {
        float pinY = (float) (PIN_Y / 16.0);
        float pinZ = (float) (PIN_Z / 16.0);
        return new Matrix4f()
                .translate(0.5f, 0f, 0.5f)
                .rotateY((float) Math.toRadians(180.0 - facing.toYRot()))
                .translate(-0.5f, wall ? (float) (-WALL_DROP / 16.0) : 0f, -0.5f)
                .translate(0f, pinY, pinZ)
                .rotateX((float) Math.toRadians(-lipBackDegrees))
                .translate(0f, -pinY, -pinZ);
    }

    /**
     * Which way and how far a strike swings a bell whose mount faces {@code facing}: {@code push} is the horizontal
     * direction the strike pushes the bell (from the striker towards the bell). A push towards the back of the mount
     * gives +1 (the mouth swings back first), towards the front −1; a push along the pin (a strike on the bell's side)
     * gives ±{@link #SIDE_STRIKE_SCALE}, + when it pushes towards the mount's right seen from the front (clockwise of
     * {@code facing}). No push (a raid's alarm) rings it as if struck from the front.
     */
    public static double direction(Direction facing, @Nullable Direction push) {
        if (push == null || push.getAxis() == Direction.Axis.Y) return 1.0;
        if (push == facing.getOpposite()) return 1.0;
        if (push == facing) return -1.0;
        return push == facing.getClockWise() ? SIDE_STRIKE_SCALE : -SIDE_STRIKE_SCALE;
    }
}

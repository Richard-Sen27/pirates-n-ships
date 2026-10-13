package com.richardsenger.piratesnships.combat.grapple;

/**
 * Pure flight arithmetic of a launched hook (GR7, GR8), step for step as vanilla's {@code ThrowableProjectile#tick}
 * moves it in the open air: the position advances by the velocity, then the velocity is scaled by 0.99 and loses the
 * gravity (the launch's: {@code hand_throw_gravity} by hand, {@code grapple.gravity} times
 * {@code launch.musket_gravity_factor} from the musket). That is the whole flight: one ballistic arc from the first tick,
 * no cut-off, no speed clamp. A thrown hook starts {@link #THROW_START_HEIGHT} above the thrower's feet (vanilla starts
 * a thrown item 0.1 below the eyes). No world access; tested in JUnit ({@code HookFlightTest}), which pins the ranges
 * of a throw by hand.
 */
public final class HookFlight {

    /** Height of a thrown hook's start above the thrower's feet [blocks]: the eyes (1.62) less vanilla's 0.1. */
    public static final double THROW_START_HEIGHT = 1.52;
    /** Vanilla's drag on a projectile in the air, per tick. */
    public static final double AIR_DRAG = 0.99;
    /** Ticks after which a flight is given up (a hook drops after 200 ticks in the air). */
    public static final int MAX_TICKS = 400;

    private HookFlight() {
    }

    /**
     * Horizontal distance [blocks] at which a hook launched at {@code speed} blocks per tick, {@code pitchDegrees} above
     * the horizon (negative: below), {@code startHeight} above flat ground (negative: below a ledge it must rise over)
     * comes down to that ground's height, with
     * {@code gravity} per tick squared: where it is on the first tick at or under that height.
     */
    public static double landingDistance(double speed, double pitchDegrees, double gravity, double startHeight) {
        double a = Math.toRadians(pitchDegrees);
        double vx = speed * Math.cos(a);
        double vy = speed * Math.sin(a);
        double x = 0.0;
        double y = startHeight;
        for (int t = 0; t < MAX_TICKS; t++) {
            double nx = x + vx;
            double ny = y + vy;
            if (ny <= 0.0 && ny < y) { // coming down through the ground's height (a start below it rises first)
                return nx; // the first tick at or under it, as a GameTest sees the hook land (GR4's 39.4 at 3.0)
            }
            x = nx;
            y = ny;
            vx *= AIR_DRAG;
            vy = vy * AIR_DRAG - gravity;
        }
        return x;
    }

    /** The farthest {@link #landingDistance} over launch angles from 0° to 80° in whole degrees. */
    public static double farthest(double speed, double gravity, double startHeight) {
        double best = 0.0;
        for (int p = 0; p <= 80; p++) {
            best = Math.max(best, landingDistance(speed, p, gravity, startHeight));
        }
        return best;
    }

    /**
     * Height [blocks, relative to the start] at which a hook launched at {@code speed} toward a point {@code dx} blocks
     * away horizontally and {@code dy} blocks up (aimed straight at it) passes that horizontal distance, or NaN when it
     * comes down {@code dy + 4} below the aim point before it gets there.
     */
    public static double heightAtAimedPoint(double speed, double dx, double dy, double gravity) {
        double len = Math.hypot(dx, dy);
        double vx = speed * dx / len;
        double vy = speed * dy / len;
        double x = 0.0;
        double y = 0.0;
        for (int t = 0; t < MAX_TICKS; t++) {
            double nx = x + vx;
            double ny = y + vy;
            if (nx >= dx) {
                return y + (ny - y) * ((dx - x) / (nx - x));
            }
            if (ny < dy - 4.0) {
                return Double.NaN;
            }
            x = nx;
            y = ny;
            vx *= AIR_DRAG;
            vy = vy * AIR_DRAG - gravity;
        }
        return Double.NaN;
    }
    /**
     * The first {@code ticks} positions (relative to the start; x along the throw, y up) of a hook launched at
     * {@code speed} blocks per tick, {@code pitchDegrees} above the horizon, with {@code gravity}: element {@code t} is
     * where it is after {@code t + 1} ticks, ignoring the ground.
     */
    public static double[][] path(double speed, double pitchDegrees, double gravity, int ticks) {
        double a = Math.toRadians(pitchDegrees);
        double vx = speed * Math.cos(a);
        double vy = speed * Math.sin(a);
        double x = 0.0;
        double y = 0.0;
        double[][] out = new double[ticks][];
        for (int t = 0; t < ticks; t++) {
            x += vx;
            y += vy;
            out[t] = new double[]{x, y};
            vx *= AIR_DRAG;
            vy = vy * AIR_DRAG - gravity;
        }
        return out;
    }

    /**
     * The lowest launch angle [degrees above the horizon, -60..80 in tenths] at which a hook launched at {@code speed}
     * passes a point {@code dx} blocks away and {@code dy} blocks up within a quarter block (a thrower aiming above a rail
     * to drop onto it), or NaN when no angle reaches it.
     */
    public static double aimPitch(double speed, double dx, double dy, double gravity) {
        for (int tenths = -600; tenths <= 800; tenths++) {
            double pitch = tenths / 10.0;
            double y = heightAtDistance(speed, pitch, dx, gravity);
            if (!Double.isNaN(y) && Math.abs(y - dy) <= 0.25) {
                return pitch;
            }
        }
        return Double.NaN;
    }

    /** Height [blocks, relative to the start] at which a launch at {@code pitchDegrees} passes {@code dx}, or NaN. */
    static double heightAtDistance(double speed, double pitchDegrees, double dx, double gravity) {
        double a = Math.toRadians(pitchDegrees);
        double vx = speed * Math.cos(a);
        double vy = speed * Math.sin(a);
        double x = 0.0;
        double y = 0.0;
        for (int t = 0; t < MAX_TICKS && vx > 1.0e-6; t++) {
            double nx = x + vx;
            double ny = y + vy;
            if (nx >= dx) {
                return y + (ny - y) * ((dx - x) / (nx - x));
            }
            x = nx;
            y = ny;
            vx *= AIR_DRAG;
            vy = vy * AIR_DRAG - gravity;
        }
        return Double.NaN;
    }
}

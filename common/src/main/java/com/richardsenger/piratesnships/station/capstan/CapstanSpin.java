package com.richardsenger.piratesnships.station.capstan;

/**
 * The drawn angle of the capstan's bars (CAP2), integrated on the client from the synced {@link CapstanTurning}: while
 * the capstan turns the angle grows at the synced rate (degrees per tick, {@code crew_stations.capstan.turn_degrees_per_tick})
 * in the turning's direction; when it stops, the bars run on to the next quarter turn and settle there, slowing down
 * evenly (they look the same after a quarter turn, so they always come to rest square to the block, where the crew
 * member's spot is). Times are game ticks with the partial tick, so the bars move smoothly between ticks. Pure: one
 * per block entity, read by the renderer.
 *
 * <p>Angles are degrees of a right-handed turn about +y ({@code Axis.YP}), 0 = the model as built.
 */
public final class CapstanSpin {

    private double baseAngle;
    private double baseTime;
    /** The current direction (-1, 0, 1). */
    private int sign;
    /** The rate of the current turn, or of the turn that is settling, in degrees per tick. */
    private double rate;
    /** Settling: the angle the bars come to rest at, and the time it takes. */
    private boolean settling;
    private double target;
    private double settleTicks;

    /** The direction the bars turn in now (-1, 0, 1); 0 while they settle or rest. */
    public int sign() {
        return sign;
    }

    /** The rate of the current turn in degrees per tick. */
    public double rate() {
        return rate;
    }

    /** The angle at time {@code now} (game ticks plus the partial tick). */
    public double angle(double now) {
        double dt = Math.max(0.0, now - baseTime);
        if (sign != 0) {
            return baseAngle + sign * rate * dt;
        }
        if (settling) {
            if (dt >= settleTicks) {
                return target;
            }
            double u = 1.0 - dt / settleTicks;
            return baseAngle + (target - baseAngle) * (1.0 - u * u);
        }
        return baseAngle;
    }

    /**
     * The turning changed at time {@code now}: start turning in direction {@code newSign} at {@code newRate} degrees per
     * tick, or ({@code newSign} 0) run on to the next quarter turn and settle. The angle carries on from where it is.
     */
    public void change(int newSign, double newRate, double now) {
        double a = angle(now);
        int lastSign = sign;
        baseAngle = a % 360.0;
        baseTime = now;
        if (newSign != 0) {
            sign = Integer.signum(newSign);
            rate = Math.max(0.0, newRate);
            settling = false;
            return;
        }
        sign = 0;
        if (lastSign == 0) {
            return; // already settling or at rest: carry on
        }
        target = restAngle(baseAngle, lastSign);
        double distance = Math.abs(target - baseAngle);
        // quadratic ease out from the turning speed: the distance takes twice as long as at full speed
        settleTicks = rate > 0.0 ? 2.0 * distance / rate : 0.0;
        settling = settleTicks > 0.0;
        if (!settling) {
            baseAngle = target;
        }
    }

    /** The next quarter turn from {@code angle} in direction {@code sign}, {@code angle} itself when it is on one. */
    static double restAngle(double angle, int sign) {
        double q = angle / 90.0;
        double r = Math.rint(q);
        if (Math.abs(q - r) < 1e-6) {
            return r * 90.0;
        }
        return (sign > 0 ? Math.ceil(q) : Math.floor(q)) * 90.0;
    }
}

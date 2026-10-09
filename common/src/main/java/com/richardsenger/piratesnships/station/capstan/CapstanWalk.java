package com.richardsenger.piratesnships.station.capstan;

import net.minecraft.util.Mth;

/**
 * The crew member's walk round the capstan (CAP2, docs/design.md §6 "Capstan in motion"): while the capstan works, its
 * station seat orbits the drum's axis at the bars' rate ({@code crew_stations.capstan.turn_degrees_per_tick}) in the
 * bars' direction ({@link CapstanTurning#sign()}), stepping out from its spot to {@code walk_radius} over the first
 * ticks; when the work stops it walks back along the shorter arc at the same rate and steps in to its spot. The crew
 * member faces along the circle in the walking direction, which is the way it pushes its bar. Starting at the spot, a
 * cardinal neighbour of the capstan, it walks with the bar that points at the spot when the bars rest square.
 *
 * <p>Angles are degrees of a right-handed turn about +y ({@code Axis.YP}, the bars' turn in the renderer); offsets are
 * horizontal plot vectors from the capstan's axis (x, z). One instance per capstan on the server. Pure.
 */
public final class CapstanWalk {

    /** How much of the step from the spot out to the walking radius is taken per tick (10 ticks for the whole step). */
    static final double STEP_OUT_PER_TICK = 0.1;

    /** The angle walked from the spot, wrapped to (-180, 180]. */
    private double angle;
    /** 0 at the spot's distance, 1 at the walking radius. */
    private double out;

    public double angle() {
        return angle;
    }

    /** At the spot: no angle walked and stepped in. */
    public boolean home() {
        return angle == 0.0 && out == 0.0;
    }

    public void reset() {
        angle = 0.0;
        out = 0.0;
    }

    /**
     * One server tick: walking ({@code sign} ±1) goes on round the drum at {@code rate} degrees per tick and steps out;
     * otherwise ({@code sign} 0) it goes back towards the spot along the shorter arc at the same rate and steps in.
     */
    public void tick(int sign, double rate) {
        double step = Math.max(0.0, rate);
        if (sign != 0) {
            angle = wrap(angle + Integer.signum(sign) * step);
            out = Math.min(1.0, out + STEP_OUT_PER_TICK);
            return;
        }
        angle = Math.abs(angle) <= step ? 0.0 : wrap(angle - Math.signum(angle) * step);
        out = Math.max(0.0, out - STEP_OUT_PER_TICK);
        if (out < 1e-9) {
            out = 0.0;
        }
    }

    /**
     * Where the seat stands now, as an offset from the axis: the spot's offset {@code (homeX, homeZ)} turned by the
     * walked angle and scaled from the spot's distance towards {@code radius} as far as it has stepped out.
     */
    public double[] offset(double homeX, double homeZ, double radius) {
        double home = Math.sqrt(homeX * homeX + homeZ * homeZ);
        if (home < 1e-9) {
            return new double[] {homeX, homeZ};
        }
        double[] turned = turn(homeX, homeZ, angle);
        double r = Mth.lerp(out, home, radius) / home;
        return new double[] {turned[0] * r, turned[1] * r};
    }

    /** {@code (x, z)} turned by {@code degrees} right-handed about +y, as {@code Axis.YP.rotationDegrees} turns it. */
    public static double[] turn(double x, double z, double degrees) {
        double a = Math.toRadians(degrees);
        double c = Math.cos(a);
        double s = Math.sin(a);
        return new double[] {x * c + z * s, -x * s + z * c};
    }

    /**
     * The unit direction in which a walker at {@code offset} from the axis moves round it in direction {@code sign}:
     * the circle's tangent, the way it pushes its bar. {@code (0, 0)} at the axis or for sign 0.
     */
    public static double[] heading(double[] offset, int sign) {
        double len = Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1]);
        if (len < 1e-9 || sign == 0) {
            return new double[] {0.0, 0.0};
        }
        double s = Integer.signum(sign) / len;
        return new double[] {offset[1] * s, -offset[0] * s};
    }

    private static double wrap(double degrees) {
        double d = Mth.wrapDegrees(degrees);
        return d == -180.0 ? 180.0 : d;
    }
}

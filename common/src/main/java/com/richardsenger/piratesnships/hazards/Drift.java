package com.richardsenger.piratesnships.hazards;

/** The slow drift of a whirlpool on its heading (docs/design.md §12 "stationary or slowly drifting"), pure. */
public final class Drift {

    private Drift() {
    }

    /** The position after one tick of drift: {@code heading} in radians (0 = +x, π/2 = +z), {@code speed} blocks/tick. */
    public static double[] step(double x, double z, double heading, double speed) {
        return new double[] {x + Math.cos(heading) * speed, z + Math.sin(heading) * speed};
    }

    /**
     * The heading after the drift ran into something that is not open water: turned around, plus up to ±45° from
     * {@code roll} (uniform in [0, 1)) so it does not bounce back and forth on one line. Normalized to [0, 2π).
     */
    public static double turnAway(double heading, double roll) {
        double h = heading + Math.PI + (roll - 0.5) * (Math.PI / 2);
        double twoPi = Math.PI * 2;
        h %= twoPi;
        return h < 0 ? h + twoPi : h;
    }
}

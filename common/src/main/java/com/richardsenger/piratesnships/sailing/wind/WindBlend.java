package com.richardsenger.piratesnships.sailing.wind;

/**
 * Pure helper that blends from one wind sample to the next over a number of ticks, so client visuals turn smoothly
 * between syncs. Directions blend along the shorter arc.
 *
 * @param from      the sample shown when {@code target} arrived
 * @param target    the newest received sample
 * @param startTime client game time when {@code target} arrived [ticks]
 * @param duration  blend duration [ticks], at least 1
 */
public record WindBlend(WindSample from, WindSample target, double startTime, double duration) {

    public static final WindBlend CALM = new WindBlend(WindSample.CALM, WindSample.CALM, 0.0, 1.0);

    /** The blended sample at client time {@code time} (ticks, partial ticks allowed). */
    public WindSample at(double time) {
        double t = Math.min(Math.max((time - startTime) / Math.max(1.0, duration), 0.0), 1.0);
        if (t >= 1.0) {
            return target;
        }
        double delta = ((target.towardDegrees() - from.towardDegrees()) % 360.0 + 540.0) % 360.0 - 180.0;
        return WindSample.of(from.towardDegrees() + delta * t,
                lerp(from.strength(), target.strength(), t),
                lerp(from.weatherMultiplier(), target.weatherMultiplier(), t),
                lerp(from.gust(), target.gust(), t));
    }

    /** A new blend from the currently shown value toward {@code next}. */
    public WindBlend next(WindSample next, double time, double duration) {
        return new WindBlend(at(time), next, time, duration);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}

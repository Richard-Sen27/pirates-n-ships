package com.richardsenger.piratesnships.sailing.wind;

/**
 * Tuning values of the wind field (docs/design.md §5.1). A plain value object so {@link WindField} never touches
 * config state; {@code SailingConfig.windParams()} fills one from the server config.
 *
 * @param variability              drift speed multiplier. 1 = the direction wanders on a scale of one in-game day,
 *                                 the strength on a scale of a quarter day. 0 = frozen wind.
 * @param minStrength              lower bound of the base strength in clear weather [blocks/s]
 * @param maxStrength              upper bound of the base strength in clear weather [blocks/s]
 * @param weatherAffectsWind       whether rain and thunder scale the strength and allow gusts
 * @param rainMultiplier           strength multiplier at full rain (vanilla rain level 1)
 * @param thunderMultiplier        strength multiplier at full thunder (vanilla thunder level 1)
 * @param gustsEnabled             whether storms produce gusts
 * @param gustsPerMinute           average gust count per real minute (1200 ticks) at full thunder
 * @param gustStrength             extra strength at the peak of the strongest gust, as a fraction (0.4 = +40%)
 * @param gustDirectionShiftDeg    largest direction shift at the peak of a gust [degrees]
 * @param regionalVariation        whether the wind differs by position
 * @param regionalScale            distance over which the regional pattern changes noticeably [blocks]
 * @param regionalDirectionDeg     largest regional direction offset [degrees]
 * @param regionalStrengthFraction largest regional strength change, as a fraction (0.3 = ±30%)
 */
public record WindParams(
        double variability,
        double minStrength,
        double maxStrength,
        boolean weatherAffectsWind,
        double rainMultiplier,
        double thunderMultiplier,
        boolean gustsEnabled,
        double gustsPerMinute,
        double gustStrength,
        double gustDirectionShiftDeg,
        boolean regionalVariation,
        double regionalScale,
        double regionalDirectionDeg,
        double regionalStrengthFraction) {

    /** The defaults. The server config declares its defaults from this instance. */
    public static final WindParams DEFAULTS = new WindParams(
            1.0, 3.0, 12.0,
            true, 1.5, 2.2,
            true, 3.0, 0.4, 20.0,
            false, 2048.0, 45.0, 0.3);

    public WindParams {
        variability = Math.max(0.0, variability);
        minStrength = Math.max(0.0, minStrength);
        maxStrength = Math.max(minStrength, maxStrength);
        rainMultiplier = Math.max(0.0, rainMultiplier);
        thunderMultiplier = Math.max(0.0, thunderMultiplier);
        gustsPerMinute = Math.max(1.0e-3, gustsPerMinute);
        gustStrength = Math.max(0.0, gustStrength);
        regionalScale = Math.max(1.0, regionalScale);
        regionalStrengthFraction = Math.min(Math.max(0.0, regionalStrengthFraction), 1.0);
    }

    public WindParams withRegionalVariation(boolean on) {
        return new WindParams(variability, minStrength, maxStrength, weatherAffectsWind, rainMultiplier,
                thunderMultiplier, gustsEnabled, gustsPerMinute, gustStrength, gustDirectionShiftDeg, on,
                regionalScale, regionalDirectionDeg, regionalStrengthFraction);
    }

    public WindParams withVariability(double v) {
        return new WindParams(v, minStrength, maxStrength, weatherAffectsWind, rainMultiplier,
                thunderMultiplier, gustsEnabled, gustsPerMinute, gustStrength, gustDirectionShiftDeg,
                regionalVariation, regionalScale, regionalDirectionDeg, regionalStrengthFraction);
    }

    public WindParams withGustsEnabled(boolean on) {
        return new WindParams(variability, minStrength, maxStrength, weatherAffectsWind, rainMultiplier,
                thunderMultiplier, on, gustsPerMinute, gustStrength, gustDirectionShiftDeg,
                regionalVariation, regionalScale, regionalDirectionDeg, regionalStrengthFraction);
    }

    public WindParams withWeatherAffectsWind(boolean on) {
        return new WindParams(variability, minStrength, maxStrength, on, rainMultiplier,
                thunderMultiplier, gustsEnabled, gustsPerMinute, gustStrength, gustDirectionShiftDeg,
                regionalVariation, regionalScale, regionalDirectionDeg, regionalStrengthFraction);
    }
}

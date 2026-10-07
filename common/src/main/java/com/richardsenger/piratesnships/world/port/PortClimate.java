package com.richardsenger.piratesnships.world.port;

import com.richardsenger.piratesnships.trade.market.Climate;

/**
 * A port's {@link Climate} from the biome at its centre (WG1), by the biome's base temperature. Vanilla values for
 * orientation: snowy beach 0.05, stony shore 0.2, beach 0.8, jungle 0.95, desert and savanna 2.0.
 * <ul>
 *     <li>below {@link #COLD_BELOW} (0.3): {@code COLD}</li>
 *     <li>at least {@link #ARID_FROM} (1.5) and no precipitation: {@code ARID}</li>
 *     <li>at least {@link #TROPICAL_FROM} (0.95): {@code TROPICAL}</li>
 *     <li>otherwise: {@code TEMPERATE}</li>
 * </ul>
 * The thresholds are design constants of the mapping, not balance values.
 */
public final class PortClimate {

    public static final float COLD_BELOW = 0.3f;
    public static final float TROPICAL_FROM = 0.95f;
    public static final float ARID_FROM = 1.5f;

    private PortClimate() {
    }

    public static Climate of(float baseTemperature, boolean precipitation) {
        if (baseTemperature < COLD_BELOW) return Climate.COLD;
        if (baseTemperature >= ARID_FROM && !precipitation) return Climate.ARID;
        if (baseTemperature >= TROPICAL_FROM) return Climate.TROPICAL;
        return Climate.TEMPERATE;
    }
}

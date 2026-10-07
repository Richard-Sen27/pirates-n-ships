package com.richardsenger.piratesnships.world.port;

import com.richardsenger.piratesnships.trade.market.Climate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortClimateTest {

    @Test
    void vanillaShoreBiomes() {
        assertEquals(Climate.COLD, PortClimate.of(0.05f, true), "snowy beach");
        assertEquals(Climate.COLD, PortClimate.of(0.2f, true), "stony shore");
        assertEquals(Climate.TEMPERATE, PortClimate.of(0.8f, true), "beach");
        assertEquals(Climate.TROPICAL, PortClimate.of(0.95f, true), "jungle");
        assertEquals(Climate.ARID, PortClimate.of(2.0f, false), "desert");
        assertEquals(Climate.TROPICAL, PortClimate.of(2.0f, true), "hot and wet");
    }

    @Test
    void thresholds() {
        assertEquals(Climate.COLD, PortClimate.of(PortClimate.COLD_BELOW - 0.01f, false));
        assertEquals(Climate.TEMPERATE, PortClimate.of(PortClimate.COLD_BELOW, false));
        assertEquals(Climate.TEMPERATE, PortClimate.of(PortClimate.TROPICAL_FROM - 0.01f, true));
        assertEquals(Climate.TROPICAL, PortClimate.of(PortClimate.TROPICAL_FROM, true));
        assertEquals(Climate.TROPICAL, PortClimate.of(PortClimate.ARID_FROM - 0.01f, false));
        assertEquals(Climate.ARID, PortClimate.of(PortClimate.ARID_FROM, false));
    }
}

package com.richardsenger.piratesnships.sailing.waves;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WaveHeaveRuleTest {

    @Test
    void forceIsTheShareOfTheWeightPerBlockOfMeanHeight() {
        WaveHeaveRule.Params p = new WaveHeaveRule.Params(true, 0.5, 100.0);
        assertEquals(0.5 * 180 * 11 * 0.4, WaveHeaveRule.force(0.4, 180, 11, p), 1e-9);
        assertEquals(-0.5 * 180 * 11 * 0.4, WaveHeaveRule.force(-0.4, 180, 11, p), 1e-9);
        assertEquals(0.0, WaveHeaveRule.force(0.0, 180, 11, p));
    }

    @Test
    void forceIsCappedPerMassBothWays() {
        WaveHeaveRule.Params p = new WaveHeaveRule.Params(true, 0.5, 2.0);
        assertEquals(2.0 * 180, WaveHeaveRule.force(1.5, 180, 11, p), 1e-9);
        assertEquals(-2.0 * 180, WaveHeaveRule.force(-1.5, 180, 11, p), 1e-9);
    }

    @Test
    void nothingWhenDisabledMasslessOrInvalid() {
        assertEquals(0.0, WaveHeaveRule.force(1.0, 180, 11, new WaveHeaveRule.Params(false, 0.5, 6.0)));
        assertEquals(0.0, WaveHeaveRule.force(1.0, 0.0, 11, WaveHeaveRule.Params.DEFAULTS));
        assertEquals(0.0, WaveHeaveRule.force(Double.NaN, 180, 11, WaveHeaveRule.Params.DEFAULTS));
    }

    @Test
    void meanHeightSkipsInvalidSamples() {
        assertEquals(0.2, WaveHeaveRule.meanHeight(new double[] {0.1, 0.3, Double.NaN}), 1e-12);
        assertEquals(0.0, WaveHeaveRule.meanHeight(new double[0]));
    }
}

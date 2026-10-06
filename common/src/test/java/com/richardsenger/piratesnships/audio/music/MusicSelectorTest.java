package com.richardsenger.piratesnships.audio.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicSelectorTest {

    private static final MusicSelector.Settings ON = new MusicSelector.Settings(true, true, 120, 300);

    @Test
    void disabledNeverOverridesVanilla() {
        MusicSelector.Settings off = new MusicSelector.Settings(false, true, 120, 300);
        for (MusicSituation s : MusicSituation.values()) assertNull(MusicSelector.select(s, off), s.name());
    }

    @Test
    void aboardPlaysShanties() {
        MusicSelector.Choice c = MusicSelector.select(MusicSituation.ABOARD, ON);
        assertNotNull(c);
        assertEquals(MusicSelector.Pool.SHANTY, c.pool());
    }

    @Test
    void aboardPlaysTheSeaPoolWhenShantiesAreOff() {
        MusicSelector.Choice c = MusicSelector.select(MusicSituation.ABOARD, new MusicSelector.Settings(true, false, 120, 300));
        assertNotNull(c);
        assertEquals(MusicSelector.Pool.SEA, c.pool());
    }

    @Test
    void atSeaPlaysTheSeaPool() {
        MusicSelector.Choice c = MusicSelector.select(MusicSituation.AT_SEA, ON);
        assertNotNull(c);
        assertEquals(MusicSelector.Pool.SEA, c.pool());
    }

    @Test
    void elsewhereLeavesVanillaAlone() {
        assertNull(MusicSelector.select(MusicSituation.NONE, ON));
    }

    @Test
    void gapsComeFromConfigInTicks() {
        MusicSelector.Choice c = MusicSelector.select(MusicSituation.AT_SEA, new MusicSelector.Settings(true, true, 30, 90));
        assertNotNull(c);
        assertEquals(600, c.minGapTicks());
        assertEquals(1800, c.maxGapTicks());
    }

    @Test
    void maxGapIsRaisedToTheMinimum() {
        MusicSelector.Choice c = MusicSelector.select(MusicSituation.AT_SEA, new MusicSelector.Settings(true, true, 200, 100));
        assertNotNull(c);
        assertEquals(4000, c.minGapTicks());
        assertEquals(4000, c.maxGapTicks());
    }

    @Test
    void situationPrefersAboardOverTheBiome() {
        assertEquals(MusicSituation.ABOARD, MusicSituation.of(true, true));
        assertEquals(MusicSituation.ABOARD, MusicSituation.of(true, false));
        assertEquals(MusicSituation.AT_SEA, MusicSituation.of(false, true));
        assertEquals(MusicSituation.NONE, MusicSituation.of(false, false));
    }

    @Test
    void channelVolumeScalesVanillasVolume() {
        assertEquals(0.5f, MusicSelector.channelVolume(1f, 1f, 0.5), 1e-6);
        assertEquals(0.25f, MusicSelector.channelVolume(1f, 0.5f, 0.5), 1e-6);
        assertEquals(1f, MusicSelector.channelVolume(2f, 1f, 1.0), 1e-6); // vanilla clamps first
        assertEquals(0f, MusicSelector.channelVolume(1f, 1f, -1.0), 1e-6);
    }

    @Test
    void aboardMemoryBridgesShortGaps() {
        AboardMemory m = new AboardMemory();
        assertFalse(m.update(false, 0));
        assertTrue(m.update(true, 10));
        assertTrue(m.update(false, 10 + AboardMemory.GRACE_TICKS)); // a jump
        assertFalse(m.update(false, 11 + AboardMemory.GRACE_TICKS)); // left the ship
        assertTrue(m.update(true, 200));
        m.reset();
        assertFalse(m.update(false, 201));
    }
}

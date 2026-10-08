package com.richardsenger.piratesnships.sailing.anchor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.sailing.force.AnchorState.Phase;
import org.junit.jupiter.api.Test;

/** The anchor's chain sounds (AN2b): running only while paying out, the jolt once per landing, scrape, capstan. */
class AnchorSoundCuesTest {

    private static boolean has(int cues, int cue) {
        return (cues & cue) != 0;
    }

    @Test
    void runningChainOnlyWhilePayingOut() {
        AnchorSoundCues c = new AnchorSoundCues();
        int running = 0;
        for (int i = 0; i < 20; i++) { // the fall: the chain pays out 0.2 blocks per tick
            running += has(c.tick(Phase.DROPPING, 2.0 + 0.2 * i, 2.0 + 0.2 * i, false, false, false), AnchorSoundCues.RUNNING) ? 1 : 0;
        }
        assertEquals(5, running, "every " + AnchorSoundCues.CHAIN_INTERVAL + " ticks after the first");
        for (int i = 0; i < 20; i++) { // hanging at the chain's end: nothing runs
            int cues = c.tick(Phase.DROPPING, 5.8, 5.8, false, true, false);
            assertEquals(0, cues & (AnchorSoundCues.RUNNING | AnchorSoundCues.SCRAPE));
        }
    }

    @Test
    void joltWhenTheFallReachesTheChainsEndOnce() {
        AnchorSoundCues c = new AnchorSoundCues();
        c.tick(Phase.DROPPING, 5.0, 5.0, false, false, false);
        assertTrue(has(c.tick(Phase.DROPPING, 6.0, 6.0, false, true, false), AnchorSoundCues.JOLT));
        for (int i = 0; i < 10; i++) {
            assertEquals(0, c.tick(Phase.DROPPING, 6.0, 6.0, false, true, false) & AnchorSoundCues.JOLT);
        }
    }

    @Test
    void joltWhenTheShipStretchesTheChainAfterLandingThenOnlyAfterSlack() {
        AnchorSoundCues c = new AnchorSoundCues();
        c.tick(Phase.DROPPING, 8.0, 8.0, false, false, false);
        int landing = c.tick(Phase.HOLDING, 8.0, 8.0, true, true, false);
        assertEquals(0, landing & AnchorSoundCues.JOLT, "a still ship: no jolt at the landing");
        assertTrue(has(c.tick(Phase.HOLDING, 8.0, 8.2, true, true, false), AnchorSoundCues.JOLT), "the ship pulls it taut");
        for (int i = 0; i < 20; i++) { // it stays taut, wobbling: no more jolts
            assertEquals(0, c.tick(Phase.HOLDING, 8.0, 8.0 + (i % 2) * 0.2, true, true, false) & AnchorSoundCues.JOLT);
        }
        c.tick(Phase.HOLDING, 8.0, 7.5, true, false, false); // a little slack: not enough to re-arm
        assertEquals(0, c.tick(Phase.HOLDING, 8.0, 8.2, true, true, false) & AnchorSoundCues.JOLT);
        c.tick(Phase.HOLDING, 8.0, 6.5, true, false, false); // clearly slack
        assertTrue(has(c.tick(Phase.HOLDING, 8.0, 8.2, true, true, false), AnchorSoundCues.JOLT), "snaps taut again");
    }

    @Test
    void dragScrapesAndRaisingClanksTheCapstan() {
        AnchorSoundCues c = new AnchorSoundCues();
        c.tick(Phase.HOLDING, 8.0, 8.0, true, true, false);
        int scrapes = 0;
        for (int i = 0; i < 8; i++) {
            int cues = c.tick(Phase.HOLDING, 8.0, 8.0, true, true, true);
            scrapes += has(cues, AnchorSoundCues.SCRAPE) ? 1 : 0;
            assertEquals(0, cues & AnchorSoundCues.RUNNING);
        }
        assertEquals(2, scrapes);
        int clanks = 0, running = 0;
        for (int i = 0; i < 20; i++) {
            int cues = c.tick(Phase.RAISING, 8.0 - 0.125 * i, 8.0 - 0.125 * i, true, true, false);
            clanks += has(cues, AnchorSoundCues.CAPSTAN) ? 1 : 0;
            running += has(cues, AnchorSoundCues.RUNNING) ? 1 : 0;
        }
        assertEquals(2, clanks);
        assertEquals(5, running);
    }

    @Test
    void primedAnchorAfterALoadMakesNoLandingCues() {
        AnchorSoundCues c = new AnchorSoundCues();
        c.prime(Phase.HOLDING, 8.0, true, true);
        assertEquals(0, c.tick(Phase.HOLDING, 8.0, 8.3, true, true, false));
    }
}

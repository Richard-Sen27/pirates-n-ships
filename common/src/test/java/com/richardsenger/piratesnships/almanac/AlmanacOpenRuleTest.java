package com.richardsenger.piratesnships.almanac;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlmanacOpenRuleTest {

    @Test
    void closedUntilRead() {
        assertFalse(AlmanacOpenRule.open(true, 1000, AlmanacOpenRule.NEVER, 100, false));
    }

    @Test
    void openForOpenTicksAfterARightClick() {
        assertTrue(AlmanacOpenRule.open(true, 1000, 1000, 100, false));
        assertTrue(AlmanacOpenRule.open(true, 1099, 1000, 100, false));
        assertFalse(AlmanacOpenRule.open(true, 1100, 1000, 100, false));
    }

    @Test
    void openWhileAReaderHasItOpen() {
        assertTrue(AlmanacOpenRule.open(true, 5000, 1000, 100, true));
        assertTrue(AlmanacOpenRule.open(true, 5000, AlmanacOpenRule.NEVER, 100, true));
    }

    @Test
    void neverOpenWhenSwitchedOff() {
        assertFalse(AlmanacOpenRule.open(false, 1000, 1000, 100, true));
        assertFalse(AlmanacOpenRule.open(false, 1000, 1000, 100, false));
    }

    @Test
    void aGameTimeBeforeTheClickDoesNotOpenIt() {
        assertFalse(AlmanacOpenRule.open(true, 900, 1000, 100, false));
    }
}

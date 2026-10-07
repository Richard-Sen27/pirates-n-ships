package com.richardsenger.piratesnships.crew.hammock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RestRulesTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    @Test
    void nightIsVanillasSleepingWindowOnEveryDay() {
        assertFalse(RestRules.isNight(0));
        assertFalse(RestRules.isNight(6000));
        assertFalse(RestRules.isNight(12541));
        assertTrue(RestRules.isNight(12542));
        assertTrue(RestRules.isNight(18000));
        assertTrue(RestRules.isNight(23459));
        assertFalse(RestRules.isNight(23460));
        assertTrue(RestRules.isNight(5 * 24000L + 18000));
        assertFalse(RestRules.isNight(5 * 24000L + 1000));
        assertTrue(RestRules.isNight(-6000), "negative day time wraps like the clock");
    }

    @Test
    void closestPairFirstOneBedEach() {
        List<RestRules.Bed<String>> beds = List.of(new RestRules.Bed<>("near", 0, 0, 0), new RestRules.Bed<>("far", 10, 0, 0));
        // B is closest to "near", so A, though also close to it, gets "far"
        List<RestRules.Sleeper> sleepers = List.of(new RestRules.Sleeper(A, 2, 0, 0), new RestRules.Sleeper(B, 1, 0, 0));
        assertEquals(Map.of(B, "near", A, "far"), RestRules.assign(beds, sleepers));
    }

    @Test
    void moreSleepersThanBedsLeavesTheFarthestWithout() {
        List<RestRules.Bed<String>> beds = List.of(new RestRules.Bed<>("only", 0, 0, 0));
        List<RestRules.Sleeper> sleepers = List.of(new RestRules.Sleeper(A, 5, 0, 0), new RestRules.Sleeper(B, 1, 0, 0),
                new RestRules.Sleeper(C, 3, 0, 0));
        assertEquals(Map.of(B, "only"), RestRules.assign(beds, sleepers));
    }

    @Test
    void tiesKeepListOrder() {
        List<RestRules.Bed<String>> beds = List.of(new RestRules.Bed<>("x", 0, 0, 0));
        List<RestRules.Sleeper> sleepers = List.of(new RestRules.Sleeper(A, 1, 0, 0), new RestRules.Sleeper(B, -1, 0, 0));
        assertEquals(Map.of(A, "x"), RestRules.assign(beds, sleepers));
    }

    @Test
    void nothingToPair() {
        assertTrue(RestRules.assign(List.<RestRules.Bed<String>>of(), List.of(new RestRules.Sleeper(A, 0, 0, 0))).isEmpty());
        assertTrue(RestRules.assign(List.of(new RestRules.Bed<>("x", 0, 0, 0)), List.of()).isEmpty());
    }
}

package com.richardsenger.piratesnships.mob.village;

import com.richardsenger.piratesnships.mob.village.VillageRoutine.Activity;
import com.richardsenger.piratesnships.mob.village.VillageRoutine.Span;
import com.richardsenger.piratesnships.mob.village.VillageRoutine.Step;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LIFE1: the villagers' schedules (which station at which hour). */
class VillageRoutineTest {

    @ParameterizedTest
    @EnumSource(VillageRole.class)
    void everyScheduleCoversTheDayExactlyOnce(VillageRole role) {
        int total = 0;
        for (Span s : VillageRoutine.spans(role)) total += s.length();
        assertEquals(VillageRoutine.DAY, total, role + ": the spans add up to one day");
        for (int t = 0; t < VillageRoutine.DAY; t += 50) {
            int time = t;
            long covering = VillageRoutine.spans(role).stream().filter(s -> s.contains(time)).count();
            assertEquals(1, covering, role + " at " + t);
        }
    }

    @ParameterizedTest
    @EnumSource(VillageRole.class)
    void everyRoleWorksByDayAndSleepsAtNight(VillageRole role) {
        assertEquals(Activity.SLEEP, VillageRoutine.step(role, 20000).activity(), role + " at 2 am");
        assertTrue(VillageRoutine.spans(role).stream().anyMatch(s -> s.step().activity() == Activity.WORK), role + " works");
        // no one works at midnight
        assertFalse(VillageRoutine.step(role, 18000).activity() == Activity.WORK && role != VillageRole.INNKEEPER,
                role + " is not at work at midnight");
    }

    @Test
    void theFishwifeGoesFromTheRackToTheStallToBed() {
        VillageRole f = VillageRole.FISHWIFE;
        assertEquals(new Step(Activity.WORK, 0), VillageRoutine.step(f, 1000), "morning at the rack");
        assertEquals(new Step(Activity.WORK, 1), VillageRoutine.step(f, 6000), "noon at the fish market's counter");
        assertEquals(Activity.SIT, VillageRoutine.step(f, 11000).activity(), "dusk on a bench");
        assertEquals(Activity.SLEEP, VillageRoutine.step(f, 14000).activity(), "night in bed");
        assertEquals(Activity.HOME, VillageRoutine.step(f, 23500).activity(), "dawn at home");
    }

    @Test
    void theDayWrapsAndAnyDayTimeIsAccepted() {
        VillageRole v = VillageRole.VENDOR;
        assertEquals(VillageRoutine.step(v, 3000), VillageRoutine.step(v, 3000 + 24000L * 41));
        assertEquals(VillageRoutine.step(v, 23999), VillageRoutine.step(v, -1));
        assertEquals(0, VillageRoutine.timeOfDay(48000));
        assertEquals(23999, VillageRoutine.timeOfDay(-1));
    }

    @Test
    void theLamplighterWalksTheLampsAtDuskAndAtDawn() {
        VillageRole l = VillageRole.LAMPLIGHTER;
        assertEquals(Activity.LAMP_ROUND, VillageRoutine.step(l, 12000).activity(), "dusk");
        assertEquals(Activity.LAMP_ROUND, VillageRoutine.step(l, 23000).activity(), "dawn");
        assertEquals(Activity.LAMP_ROUND, VillageRoutine.step(l, 500).activity(), "early morning");
        assertEquals(Activity.WORK, VillageRoutine.step(l, 5000).activity(), "day at the beacon tower");
    }

    @Test
    void theInnkeeperKeepsTheBarLate() {
        assertEquals(Activity.WORK, VillageRoutine.step(VillageRole.INNKEEPER, 13000).activity(), "dusk at the bar");
        assertEquals(Activity.WORK, VillageRoutine.step(VillageRole.INNKEEPER, 17500).activity(), "late at the bar");
        assertEquals(Activity.SLEEP, VillageRoutine.step(VillageRole.INNKEEPER, 19000).activity());
    }

    @Test
    void routinesOffMeansStrolling() {
        for (VillageRole role : VillageRole.values()) {
            assertEquals(Activity.WANDER, VillageRoutine.step(role, 1000, false).activity());
        }
        assertEquals(1000, VillageRoutine.startOf(VillageRole.VENDOR, new Step(Activity.WORK, 0)));
        assertEquals(-1, VillageRoutine.startOf(VillageRole.VENDOR, Step.of(Activity.LAMP_ROUND)));
    }
}

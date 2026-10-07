package com.richardsenger.piratesnships.mob.kraken;

import com.richardsenger.piratesnships.mob.kraken.KrakenTentacles.Demand;
import com.richardsenger.piratesnships.mob.kraken.KrakenTentacles.Job;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KrakenTentaclesTest {

    @Test
    void planServesSwimmersThenMastThenDeckThenGrabs() {
        List<Job> plan = KrakenTentacles.plan(8, new Demand(2, true, true, 6));
        assertEquals(List.of(Job.HOLD_SWIMMER, Job.HOLD_SWIMMER, Job.MAST_STRIKE, Job.DECK_SWIPE,
                Job.GRAB_HULL, Job.GRAB_HULL, Job.GRAB_HULL, Job.GRAB_HULL), plan);
    }

    @Test
    void planLeavesTentaclesFreeWhenThereIsLittleToDo() {
        assertEquals(List.of(Job.GRAB_HULL, Job.GRAB_HULL, Job.NONE, Job.NONE), KrakenTentacles.plan(4, new Demand(0, false, false, 2)));
        assertEquals(List.of(Job.NONE, Job.NONE), KrakenTentacles.plan(2, Demand.NONE));
        assertEquals(List.of(), KrakenTentacles.plan(0, new Demand(2, true, true, 3)));
    }

    @Test
    void planWithFewFreeTentaclesKeepsThePriority() {
        assertEquals(List.of(Job.HOLD_SWIMMER, Job.MAST_STRIKE), KrakenTentacles.plan(2, new Demand(1, true, true, 4)));
        assertEquals(List.of(Job.DECK_SWIPE), KrakenTentacles.plan(1, new Demand(0, false, true, 4)));
    }

    @Test
    void cutAfterThePoolIsEmptiedDropsTheJob() {
        KrakenTentacles t = new KrakenTentacles(40);
        t.setJob(3, Job.GRAB_HULL);
        assertFalse(t.damage(3, 25, 100, 600));
        assertEquals(15, t.pool(3), 1e-9);
        assertEquals(Job.GRAB_HULL, t.job(3));
        assertTrue(t.damage(3, 20, 110, 600));
        assertTrue(t.isCut(3));
        assertEquals(Job.NONE, t.job(3));
        assertEquals(710, t.regrowsAt(3));
        assertFalse(t.damage(3, 100, 120, 600), "a cut tentacle takes no more damage");
        t.setJob(3, Job.MAST_STRIKE);
        assertEquals(Job.NONE, t.job(3), "a cut tentacle gets no job");
        assertFalse(t.free().contains(3));
    }

    @Test
    void regrowsWithAFullPoolAtItsTime() {
        KrakenTentacles t = new KrakenTentacles(40);
        t.damage(0, 50, 0, 600);
        assertEquals(0, t.tick(599, 40));
        assertTrue(t.isCut(0));
        assertEquals(1, t.tick(600, 40));
        assertFalse(t.isCut(0));
        assertEquals(40, t.pool(0), 1e-9);
        assertTrue(t.free().contains(0));
    }

    @Test
    void freeCountAndRelease() {
        KrakenTentacles t = new KrakenTentacles(40);
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), t.free());
        t.setJob(1, Job.HOLD_SWIMMER);
        t.setJob(5, Job.HOLD_SWIMMER);
        t.damage(2, 40, 0, 10);
        assertEquals(List.of(0, 3, 4, 6, 7), t.free());
        assertEquals(2, t.count(Job.HOLD_SWIMMER));
        t.releaseAll();
        assertEquals(0, t.count(Job.HOLD_SWIMMER));
        assertEquals(7, t.free().size());
    }

    @Test
    void loweredHealthCapsThePools() {
        KrakenTentacles t = new KrakenTentacles(40);
        t.tick(0, 20);
        assertEquals(20, t.pool(4), 1e-9);
    }
}

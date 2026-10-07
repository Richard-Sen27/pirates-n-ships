package com.richardsenger.piratesnships.station.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.station.jobs.JobBoardRules.Claim;
import com.richardsenger.piratesnships.station.jobs.JobBoardRules.Hand;
import com.richardsenger.piratesnships.station.jobs.JobBoardRules.Job;
import com.richardsenger.piratesnships.station.jobs.JobBoardRules.Status;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class JobBoardRulesTest {

    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);

    private static Job<String> job(String key, double x, long seq) {
        return new Job<>(key, x, 0, 0, seq);
    }

    private static Hand hand(UUID id, double x, Status s) {
        return new Hand(id, x, 0, 0, s);
    }

    private static Map<String, UUID> byJob(List<Claim<String>> claims) {
        return claims.stream().collect(Collectors.toMap(Claim::job, Claim::crew));
    }

    @Test
    void nearestFreeHandTakesTheJob() {
        List<Claim<String>> c = JobBoardRules.assign(List.of(job("winch", 10, 1)),
                List.of(hand(A, 0, Status.FREE), hand(B, 8, Status.FREE), hand(C, 30, Status.FREE)), 0);
        assertEquals(List.of(new Claim<>("winch", B, false)), c);
    }

    @Test
    void eachHandTakesOneJobAtMost() {
        List<Claim<String>> c = JobBoardRules.assign(List.of(job("w1", 0, 1), job("w2", 1, 1), job("w3", 2, 1)),
                List.of(hand(A, 0, Status.FREE), hand(B, 2, Status.FREE)), 0);
        assertEquals(2, c.size());
        assertEquals(2, c.stream().map(Claim::crew).distinct().count(), "a hand took two jobs: " + c);
        assertEquals(Map.of("w1", A, "w3", B), byJob(c));
        assertEquals(List.of("w2"), JobBoardRules.unclaimed(List.of(job("w1", 0, 1), job("w2", 1, 1), job("w3", 2, 1)), c));
    }

    @Test
    void theNearerOfTwoStationsIsTakenByASingleHand() {
        List<Claim<String>> c = JobBoardRules.assign(List.of(job("far", 20, 1), job("near", 3, 1)),
                List.of(hand(A, 0, Status.FREE)), 0);
        assertEquals(List.of(new Claim<>("near", A, false)), c);
    }

    @Test
    void pinnedAndBusyHandsNeverClaim() {
        List<Claim<String>> c = JobBoardRules.assign(List.of(job("winch", 0, 1)),
                List.of(hand(A, 0, Status.PINNED), hand(B, 1, Status.BOARD_BUSY), hand(C, 50, Status.FREE)), 0);
        assertEquals(List.of(new Claim<>("winch", C, false)), c);
        assertTrue(JobBoardRules.assign(List.of(job("winch", 0, 1)),
                List.of(hand(A, 0, Status.PINNED), hand(B, 1, Status.BOARD_BUSY)), 0).isEmpty());
    }

    @Test
    void handsBeyondTheClaimDistanceNeverClaim() {
        List<Hand> hands = List.of(hand(A, 12, Status.FREE));
        assertTrue(JobBoardRules.assign(List.of(job("winch", 0, 1)), hands, 10).isEmpty());
        assertEquals(1, JobBoardRules.assign(List.of(job("winch", 0, 1)), hands, 12).size(), "the limit is inclusive");
        assertEquals(1, JobBoardRules.assign(List.of(job("winch", 0, 1)), hands, 0).size(), "0 means the whole ship");
    }

    @Test
    void idleBoardHandsAreReTaskedOnlyWhenNoFreeHandIsLeft() {
        // a free hand far away still comes before an idle board hand next to the job
        List<Claim<String>> c = JobBoardRules.assign(List.of(job("pump", 0, 1)),
                List.of(hand(A, 1, Status.BOARD_IDLE), hand(B, 40, Status.FREE)), 0);
        assertEquals(List.of(new Claim<>("pump", B, false)), c);
        // two jobs, one free hand: the second job re-tasks the idle board hand
        c = JobBoardRules.assign(List.of(job("pump", 0, 1), job("winch", 30, 1)),
                List.of(hand(A, 1, Status.BOARD_IDLE), hand(B, 2, Status.FREE)), 0);
        assertEquals(Map.of("pump", B, "winch", A), byJob(c));
        assertTrue(c.stream().anyMatch(x -> x.crew().equals(A) && x.retask()), "the idle hand's claim is no re-task: " + c);
        assertTrue(c.stream().anyMatch(x -> x.crew().equals(B) && !x.retask()));
    }

    @Test
    void aFreeHandOutOfReachLetsAnIdleBoardHandBeReTasked() {
        List<Claim<String>> c = JobBoardRules.assign(List.of(job("pump", 0, 1)),
                List.of(hand(A, 2, Status.BOARD_IDLE), hand(B, 40, Status.FREE)), 10);
        assertEquals(List.of(new Claim<>("pump", A, true)), c);
    }

    @Test
    void earlierOrdersGetTheScarceCrewFirst() {
        // the later order's job is nearer, but the earlier order is served first
        List<Job<String>> jobs = List.of(job("fire", 1, 7), job("hoist", 20, 3));
        List<Claim<String>> c = JobBoardRules.assign(jobs, List.of(hand(A, 0, Status.FREE)), 0);
        assertEquals(List.of(new Claim<>("hoist", A, false)), c);
        assertEquals(List.of("fire"), JobBoardRules.unclaimed(jobs, c));
        // with two hands both orders are served
        c = JobBoardRules.assign(jobs, List.of(hand(A, 0, Status.FREE), hand(B, 21, Status.FREE)), 0);
        assertEquals(Map.of("hoist", B, "fire", A), byJob(c));
    }

    @Test
    void noJobsOrNoHandsGiveNoClaims() {
        assertTrue(JobBoardRules.assign(List.<Job<String>>of(), List.of(hand(A, 0, Status.FREE)), 0).isEmpty());
        assertTrue(JobBoardRules.assign(List.of(job("winch", 0, 1)), List.of(), 0).isEmpty());
        assertFalse(JobBoardRules.unclaimed(List.of(job("winch", 0, 1)), List.of()).isEmpty());
    }
}

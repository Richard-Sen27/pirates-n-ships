package com.richardsenger.piratesnships.law.crime;

import com.richardsenger.piratesnships.law.crime.CriminalRecord.CrimeOutcome;
import com.richardsenger.piratesnships.law.crime.CriminalRecord.FineOutcome;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CriminalRecordTest {

    static final CrimeRules RULES = CrimeRules.defaults();
    static final long DAY = CrimeRules.TICKS_PER_DAY;
    static final UUID VICTIM_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID VICTIM_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    static CriminalRecord withScore(double score, long time) {
        return new CriminalRecord(score, time, Long.MIN_VALUE, 0, java.util.List.of());
    }

    @Nested
    class Crimes {
        @ParameterizedTest
        @EnumSource(CrimeType.class)
        void everyCrimeAddsItsDefaultSeverity(CrimeType type) {
            var r = CriminalRecord.EMPTY.addCrime(type, VICTIM_A, 1000, RULES);
            assertEquals(CrimeOutcome.COUNTED, r.outcome());
            assertEquals(type.defaultSeverity(), r.pointsAdded(), 1e-9);
            assertEquals(type.defaultSeverity(), r.record().score(), 1e-9);
            assertEquals(1, r.record().totalCrimes());
            assertEquals(1000, r.record().lastCrime());
        }

        @Test
        void defaultSeveritiesFollowTheSpec() {
            // Killing is worse than attacking; false colors "heavily"; piracy is the worst everyday crime
            assertTrue(CrimeType.KILL_NAVY.defaultSeverity() > CrimeType.ATTACK_NAVY.defaultSeverity());
            assertTrue(CrimeType.KILL_VILLAGER.defaultSeverity() > CrimeType.ATTACK_VILLAGER.defaultSeverity());
            assertTrue(CrimeType.CAUGHT_FALSE_COLORS.defaultSeverity() > CrimeType.SEEN_UNDER_JOLLY_ROGER.defaultSeverity());
            assertTrue(CrimeType.PIRACY.defaultSeverity() >= RULES.wantedThreshold(), "one act of piracy makes you wanted");
            for (CrimeType t : CrimeType.values()) assertTrue(t.defaultSeverity() > 0, t.id());
        }

        @Test
        void configuredSeverityIsUsed() {
            var rules = RULES.withSeverity(CrimeType.THEFT, 42);
            assertEquals(42, CriminalRecord.EMPTY.addCrime(CrimeType.THEFT, null, 0, rules).record().score(), 1e-9);
        }

        @Test
        void zeroSeverityRecordsNothing() {
            var r = CriminalRecord.EMPTY.addCrime(CrimeType.THEFT, null, 0, RULES.withSeverity(CrimeType.THEFT, 0));
            assertEquals(CrimeOutcome.NO_SEVERITY, r.outcome());
            assertEquals(0, r.record().score());
            assertEquals(0, r.record().totalCrimes());
        }

        @Test
        void crimesAddUp() {
            var r = CriminalRecord.EMPTY.addCrime(CrimeType.ATTACK_NAVY, VICTIM_A, 0, RULES).record();
            r = r.addCrime(CrimeType.KILL_NAVY, VICTIM_A, 10, RULES).record();
            assertEquals(40, r.score(), 1e-9);
            assertEquals(2, r.totalCrimes());
        }

        @Test
        void scoreIsCapped() {
            var r = withScore(990, 0).addCrime(CrimeType.PIRACY, VICTIM_A, 0, RULES);
            assertEquals(RULES.maxScore(), r.record().score(), 1e-9);
            assertEquals(10, r.pointsAdded(), 1e-9);
        }

        @Test
        void disabledRecordsNothing() {
            var r = CriminalRecord.EMPTY.addCrime(CrimeType.PIRACY, VICTIM_A, 0, RULES.withEnabled(false));
            assertEquals(CrimeOutcome.DISABLED, r.outcome());
            assertSame(CriminalRecord.EMPTY, r.record());
            assertEquals(WantedLevel.CLEAN, RULES.withEnabled(false).wantedLevel(999));
        }
    }

    @Nested
    class Repeats {
        final long cd = RULES.cooldownOf(CrimeType.ATTACK_NAVY); // 30 s

        @Test
        void sameCrimeSameVictimInsideWindowIsIgnored() {
            var r = CriminalRecord.EMPTY.addCrime(CrimeType.ATTACK_NAVY, VICTIM_A, 0, RULES).record();
            for (long t = 1; t < cd; t += 10) {
                var again = r.addCrime(CrimeType.ATTACK_NAVY, VICTIM_A, t, RULES);
                assertEquals(CrimeOutcome.REPEAT_IGNORED, again.outcome());
                assertEquals(0, again.pointsAdded());
                r = again.record();
            }
            assertEquals(10, r.score(), 1e-9, "a whole sword fight is one crime");
            assertEquals(1, r.totalCrimes());
        }

        @Test
        void windowIsNotExtendedByIgnoredRepeats() {
            var r = CriminalRecord.EMPTY.addCrime(CrimeType.ATTACK_NAVY, VICTIM_A, 0, RULES).record();
            r = r.addCrime(CrimeType.ATTACK_NAVY, VICTIM_A, cd - 1, RULES).record();
            var later = r.addCrime(CrimeType.ATTACK_NAVY, VICTIM_A, cd, RULES);
            assertEquals(CrimeOutcome.COUNTED, later.outcome(), "a fight longer than the window counts again");
        }

        @Test
        void otherVictimOrOtherCrimeCounts() {
            var r = CriminalRecord.EMPTY.addCrime(CrimeType.ATTACK_NAVY, VICTIM_A, 0, RULES).record();
            assertTrue(r.addCrime(CrimeType.ATTACK_NAVY, VICTIM_B, 1, RULES).counted());
            assertTrue(r.addCrime(CrimeType.KILL_NAVY, VICTIM_A, 1, RULES).counted(), "killing after attacking counts");
            assertTrue(r.addCrime(CrimeType.ATTACK_NAVY, null, 1, RULES).counted(), "no victim is its own key");
        }

        @Test
        void noVictimGroupsPerType() {
            var r = CriminalRecord.EMPTY.addCrime(CrimeType.SEEN_UNDER_JOLLY_ROGER, null, 0, RULES).record();
            assertFalse(r.addCrime(CrimeType.SEEN_UNDER_JOLLY_ROGER, null, 20, RULES).counted());
        }

        @Test
        void killsAreNeverDeduplicated() {
            var r = CriminalRecord.EMPTY.addCrime(CrimeType.KILL_NAVY, VICTIM_A, 0, RULES).record();
            assertTrue(r.addCrime(CrimeType.KILL_NAVY, VICTIM_A, 0, RULES).counted());
            assertTrue(r.recent().isEmpty(), "zero cooldown crimes are not remembered");
        }

        @Test
        void recentListIsPrunedAndBounded() {
            var r = CriminalRecord.EMPTY;
            for (int i = 0; i < 200; i++) {
                r = r.addCrime(CrimeType.THEFT, new UUID(0, i), i, RULES).record();
            }
            assertTrue(r.recent().size() <= CriminalRecord.MAX_RECENT);
            r = r.decayTo(200 + RULES.cooldownOf(CrimeType.THEFT), RULES);
            assertTrue(r.recent().isEmpty(), "expired windows are dropped");
        }
    }

    @Nested
    class Decay {
        final CrimeRules noDelay = RULES.withDecay(10, 0);

        @Test
        void decaysLinearlyPerDay() {
            assertEquals(40, withScore(50, 0).decayTo(DAY, noDelay).score(), 1e-9);
            assertEquals(45, withScore(50, 0).decayTo(DAY / 2, noDelay).score(), 1e-9);
        }

        @Test
        void neverBelowZeroAndLongGapsWork() {
            var r = withScore(50, 0).decayTo(1000 * DAY, noDelay);
            assertEquals(0, r.score());
            assertEquals(1000 * DAY, r.lastUpdate());
            assertEquals(0, withScore(50, 0).decayTo(Long.MAX_VALUE / 2, noDelay).score());
        }

        @Test
        void independentOfTickRate() {
            var once = withScore(80, 0).decayTo(3 * DAY + 123, noDelay);
            var many = withScore(80, 0);
            for (long t = 0; t <= 3 * DAY + 123; t += 7) many = many.decayTo(t, noDelay);
            many = many.decayTo(3 * DAY + 123, noDelay);
            assertEquals(once.score(), many.score(), 1e-6);
        }

        @Test
        void independentOfTickRateWithDelayAndCrimes() {
            Random rnd = new Random(42);
            for (int run = 0; run < 20; run++) {
                var a = CriminalRecord.EMPTY.addCrime(CrimeType.PIRACY, VICTIM_A, 100, RULES).record();
                var b = a;
                long end = 100 + (long) (rnd.nextDouble() * 6 * DAY);
                for (long t = 100; t < end; t += 1 + rnd.nextInt(5000)) b = b.decayTo(t, RULES);
                assertEquals(a.decayTo(end, RULES).score(), b.decayTo(end, RULES).score(), 1e-6);
            }
        }

        @Test
        void delayAfterLastCrime() {
            var r = CriminalRecord.EMPTY.addCrime(CrimeType.PIRACY, VICTIM_A, 0, RULES).record();
            assertEquals(50, r.decayTo(RULES.decayDelayTicks(), RULES).score(), 1e-9, "no decay during the delay");
            assertEquals(40, r.decayTo(RULES.decayDelayTicks() + DAY, RULES).score(), 1e-9);
        }

        @Test
        void newCrimeRestartsDelay() {
            var r = withScore(50, 0).decayTo(DAY, RULES).addCrime(CrimeType.THEFT, null, DAY, RULES).record();
            assertEquals(45, r.score(), 1e-9);
            assertEquals(45, r.decayTo(DAY + RULES.decayDelayTicks(), RULES).score(), 1e-9);
        }

        @Test
        void zeroRateAndBackwardsClockNeverDecay() {
            assertEquals(50, withScore(50, 0).decayTo(10 * DAY, RULES.withDecay(0, 0)).score());
            var back = withScore(50, 1000).decayTo(10, noDelay);
            assertEquals(50, back.score());
            assertEquals(10, back.lastUpdate());
        }
    }

    @Nested
    class Fines {
        final CrimeRules rules = RULES.withDecay(0, 0); // cost 3 per point

        @Test
        void fullPaymentClearsScoreAndCostsOnlyWhatIsOwed() {
            var r = withScore(20, 0).payFine(1000, 0, rules);
            assertEquals(FineOutcome.PAID_IN_FULL, r.outcome());
            assertEquals(60, r.doubloonsSpent());
            assertEquals(20, r.pointsRemoved(), 1e-9);
            assertEquals(0, r.record().score());
        }

        @Test
        void partialPayment() {
            var r = withScore(20, 0).payFine(30, 0, rules);
            assertEquals(FineOutcome.PARTIAL, r.outcome());
            assertEquals(30, r.doubloonsSpent());
            assertEquals(10, r.record().score(), 1e-9);
        }

        @Test
        void fractionalScoreRoundsCostUp() {
            var r = withScore(10.2, 0);
            assertEquals(31, r.fineCost(rules));
            assertEquals(0, r.payFine(31, 0, rules).record().score());
            assertEquals(11, r.displayScore());
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -5})
        void nothingOffered(int offer) {
            var r = withScore(20, 0).payFine(offer, 0, rules);
            assertEquals(FineOutcome.NOTHING_OFFERED, r.outcome());
            assertEquals(20, r.record().score());
        }

        @Test
        void nothingOwed() {
            assertEquals(FineOutcome.NOTHING_OWED, CriminalRecord.EMPTY.payFine(10, 0, rules).outcome());
            assertEquals(FineOutcome.NOTHING_OWED, withScore(20, 0).payFine(10, 0, rules.withEnabled(false)).outcome());
        }

        @Test
        void notoriousCriminalsCannotPayUnlessAllowed() {
            assertEquals(FineOutcome.REFUSED_NOTORIOUS, withScore(250, 0).payFine(10000, 0, rules).outcome());
            assertEquals(FineOutcome.PAID_IN_FULL,
                    withScore(250, 0).payFine(10000, 0, rules.withFines(3, true)).outcome());
        }

        @Test
        void fineDecaysFirst() {
            var r = withScore(20, 0).payFine(1000, CrimeRules.TICKS_PER_DAY, RULES.withDecay(10, 0));
            assertEquals(30, r.doubloonsSpent(), "only 10 points were left after a day");
        }
    }

    @Test
    void setScoreClamps() {
        assertEquals(RULES.maxScore(), CriminalRecord.EMPTY.setScore(1e9, 0, RULES).score());
        assertEquals(0, CriminalRecord.EMPTY.setScore(-5, 0, RULES).score());
    }
}

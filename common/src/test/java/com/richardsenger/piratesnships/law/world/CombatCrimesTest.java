package com.richardsenger.piratesnships.law.world;

import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.world.CombatCrimes.Party;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The victim-to-crime mapping and its edge cases. */
class CombatCrimesTest {

    private static final Party PLAYER = Party.NOBODY;
    private static final Party GOLEM = new Party(false, false, true, false);

    private static Optional<CrimeType> c(Party attacker, Party victim, boolean kill) {
        return CombatCrimes.classify(attacker, victim, false, kill, false);
    }

    @Test
    void playerAgainstCiviliansAndNavy() {
        assertEquals(Optional.of(CrimeType.ATTACK_VILLAGER), c(PLAYER, Party.asCivilian(), false));
        assertEquals(Optional.of(CrimeType.KILL_VILLAGER), c(PLAYER, Party.asCivilian(), true));
        assertEquals(Optional.of(CrimeType.ATTACK_NAVY), c(PLAYER, Party.asNavy(), false));
        assertEquals(Optional.of(CrimeType.KILL_NAVY), c(PLAYER, Party.asNavy(), true));
    }

    @Test
    void unprotectedVictimsAreNoCrime() {
        assertEquals(Optional.empty(), c(PLAYER, Party.NOBODY, false));
        assertEquals(Optional.empty(), c(PLAYER, Party.asMonster(), true));
    }

    @Test
    void navyBeatsProtectedWhenInBothTags() {
        assertEquals(Optional.of(CrimeType.ATTACK_NAVY), c(PLAYER, new Party(true, true, true, false), false));
    }

    @Test
    void noAttackerOrSelfHarmIsNoCrime() {
        assertEquals(Optional.empty(), CombatCrimes.classify(null, Party.asCivilian(), false, true, true));
        assertEquals(Optional.empty(), CombatCrimes.classify(Party.asCivilian(), Party.asCivilian(), true, false, true));
    }

    @Test
    void enforcersAndOwnKindAreExempt() {
        assertEquals(Optional.empty(), c(Party.asNavy(), Party.asNavy(), false));
        assertEquals(Optional.empty(), c(Party.asNavy(), Party.asCivilian(), true));
        assertEquals(Optional.empty(), c(GOLEM, Party.asCivilian(), false));
        assertEquals(Optional.empty(), c(Party.asCivilian(), Party.asCivilian(), false));
        assertEquals(Optional.of(CrimeType.ATTACK_NAVY), c(Party.asCivilian(), Party.asNavy(), false), "civilian hurting navy");
    }

    @Test
    void monstersOnlyWhenProsecuted() {
        assertEquals(Optional.empty(), CombatCrimes.classify(Party.asMonster(), Party.asCivilian(), false, false, false));
        assertEquals(Optional.of(CrimeType.KILL_VILLAGER),
                CombatCrimes.classify(Party.asMonster(), Party.asCivilian(), false, true, true));
    }
}

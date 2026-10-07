package com.richardsenger.piratesnships.law.brig;

import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.law.bounty.PirateTier;
import com.richardsenger.piratesnships.law.brig.PrisonerInteractionRules.Disposition;
import com.richardsenger.piratesnships.law.brig.PrisonerInteractionRules.FineQuote;
import com.richardsenger.piratesnships.law.brig.PrisonerInteractionRules.OfficerAction;
import com.richardsenger.piratesnships.law.brig.PrisonerInteractionRules.PressGang;
import com.richardsenger.piratesnships.law.crime.CrimeRules;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.crime.CriminalRecord;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.law.turnin.TurnInRules;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LA2: fine maths, officer precedence, ransom and the port rule, press-gang preconditions and the release record. */
class PrisonerInteractionRulesTest {

    // --- Fines --------------------------------------------------------------------------------------------------

    @Test
    void fineCoversWholePointsOnly() {
        // score 5, 20 coins at 3 a point: all 5 points for 15, 5 coins change
        assertEquals(new FineQuote(5, 15, true), PrisonerInteractionRules.fineQuote(5.0, 20, 3));
        // 10 coins: 3 points for 9, one coin left over
        assertEquals(new FineQuote(3, 9, false), PrisonerInteractionRules.fineQuote(5.0, 10, 3));
        // 2 coins don't buy a point
        assertEquals(FineQuote.NONE, PrisonerInteractionRules.fineQuote(5.0, 2, 3));
    }

    @Test
    void fractionalRestCountsAsAWholePoint() {
        assertEquals(new FineQuote(5, 15, true), PrisonerInteractionRules.fineQuote(4.2, 100, 3));
        assertEquals(new FineQuote(4, 12, false), PrisonerInteractionRules.fineQuote(4.2, 14, 3));
        // float noise doesn't add a point
        assertEquals(new FineQuote(4, 12, true), PrisonerInteractionRules.fineQuote(4.0000000001, 100, 3));
    }

    @Test
    void nothingOwedOrNothingOffered() {
        assertEquals(FineQuote.NONE, PrisonerInteractionRules.fineQuote(0.0, 100, 3));
        assertEquals(FineQuote.NONE, PrisonerInteractionRules.fineQuote(5.0, 0, 3));
    }

    /** The quote, handed to the record's payFine, takes exactly the points it names. */
    @Test
    void quoteMatchesTheRecordsFine() {
        Map<CrimeType, Integer> sev = new EnumMap<>(CrimeType.class);
        Map<CrimeType, Long> cd = new EnumMap<>(CrimeType.class);
        for (CrimeType t : CrimeType.values()) {
            sev.put(t, 5);
            cd.put(t, 0L);
        }
        CrimeRules rules = new CrimeRules(true, sev, cd, 0.0, 0L, 1000, 10, 50, 200, 3, false);
        CriminalRecord rec = CriminalRecord.EMPTY.setScore(4.2, 0L, rules);

        FineQuote partial = PrisonerInteractionRules.fineQuote(rec.score(), 14, 3);
        CriminalRecord.FineResult r = rec.payFine((int) partial.doubloons(), 0L, rules);
        assertEquals(CriminalRecord.FineOutcome.PARTIAL, r.outcome());
        assertEquals(12, r.doubloonsSpent());
        assertEquals(0.2, r.record().score(), 1e-9);

        FineQuote full = PrisonerInteractionRules.fineQuote(rec.score(), 100, 3);
        CriminalRecord.FineResult f = rec.payFine((int) full.doubloons(), 0L, rules);
        assertEquals(CriminalRecord.FineOutcome.PAID_IN_FULL, f.outcome());
        assertEquals(13, f.doubloonsSpent(), "the full cost of 4.2 points is 13, not the quoted 15");
        assertEquals(0.0, f.record().score(), 1e-9);
    }

    // --- Officer precedence -------------------------------------------------------------------------------------

    @Test
    void proofsFirstThenPrisonersThenCoins() {
        assertEquals(OfficerAction.PROOF, PrisonerInteractionRules.officerAction(true, false, false, false, true));
        assertEquals(OfficerAction.PRISONERS, PrisonerInteractionRules.officerAction(false, true, false, false, true));
        assertEquals(OfficerAction.PASS, PrisonerInteractionRules.officerAction(false, true, false, false, false));
        assertEquals(OfficerAction.FINE, PrisonerInteractionRules.officerAction(false, false, true, false, false));
        // coins with prisoners near: the fine, unless sneaking
        assertEquals(OfficerAction.FINE, PrisonerInteractionRules.officerAction(false, false, true, false, true));
        assertEquals(OfficerAction.PRISONERS, PrisonerInteractionRules.officerAction(false, false, true, true, true));
        assertEquals(OfficerAction.FINE, PrisonerInteractionRules.officerAction(false, false, true, true, false));
        assertEquals(OfficerAction.PASS, PrisonerInteractionRules.officerAction(false, false, false, true, true));
    }

    @Test
    void turnInBeforeRansomAndPiratesAreNeverRansomed() {
        TurnInRules.Delivery none = TurnInRules.delivery(false, false, null, 0);
        TurnInRules.Delivery bounty = TurnInRules.delivery(false, true, null, 0);
        TurnInRules.Delivery pirate = TurnInRules.delivery(false, false, PirateTier.DECKHAND, 20);
        assertEquals(Disposition.DELIVER, PrisonerInteractionRules.disposition(bounty, RansomRules.Kind.COMMON, false, true));
        assertEquals(Disposition.DELIVER, PrisonerInteractionRules.disposition(pirate, null, false, true));
        assertEquals(Disposition.RANSOM, PrisonerInteractionRules.disposition(none, RansomRules.Kind.COMMON, false, true));
        assertEquals(Disposition.RANSOM, PrisonerInteractionRules.disposition(none, RansomRules.Kind.MERCHANT, false, true));
        // a pirate the navy pays nothing for (reward 0) has no ransom kind: refused
        TurnInRules.Delivery unpaidPirate = TurnInRules.delivery(false, false, PirateTier.DECKHAND, 0);
        assertEquals(Disposition.REFUSE, PrisonerInteractionRules.disposition(unpaidPirate, null, false, true));
        assertEquals(Disposition.REFUSE, PrisonerInteractionRules.disposition(none, RansomRules.Kind.COMMON, true, true));
    }

    @Test
    void portRule() {
        assertTrue(PrisonerInteractionRules.portAllows(false, false));
        assertTrue(PrisonerInteractionRules.portAllows(true, true));
        assertFalse(PrisonerInteractionRules.portAllows(true, false));
        TurnInRules.Delivery none = TurnInRules.delivery(false, false, null, 0);
        assertEquals(Disposition.NO_PORT, PrisonerInteractionRules.disposition(none, RansomRules.Kind.NAVY_OFFICER, false, false));
    }

    // --- Press-gang ---------------------------------------------------------------------------------------------

    @Test
    void pressGangPreconditions() {
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertEquals(PressGang.OK, PrisonerInteractionRules.pressGang(true, true, true, true, null, me));
        assertEquals(PressGang.OK, PrisonerInteractionRules.pressGang(true, true, true, true, me, me));
        assertEquals(PressGang.NOT_YOUR_SHIP, PrisonerInteractionRules.pressGang(true, true, true, true, other, me));
        assertEquals(PressGang.NOT_ON_SHIP, PrisonerInteractionRules.pressGang(true, true, true, false, null, me));
        assertEquals(PressGang.NOT_A_SAILOR, PrisonerInteractionRules.pressGang(true, true, false, true, null, me));
        assertEquals(PressGang.NOT_A_PRISONER, PrisonerInteractionRules.pressGang(true, false, true, true, null, me));
        assertEquals(PressGang.DISABLED, PrisonerInteractionRules.pressGang(false, true, true, true, null, me));
    }

    // --- Release ------------------------------------------------------------------------------------------------

    @Test
    void releaseNeedsSneakEmptyMainHandAndOwnPrisoner() {
        assertTrue(PrisonerInteractionRules.releases(true, true, true, true, true));
        assertFalse(PrisonerInteractionRules.releases(false, true, true, true, true));
        assertFalse(PrisonerInteractionRules.releases(true, false, true, true, true));
        assertFalse(PrisonerInteractionRules.releases(true, true, false, true, true));
        assertFalse(PrisonerInteractionRules.releases(true, true, true, false, true));
        assertFalse(PrisonerInteractionRules.releases(true, true, true, true, false));
    }

    @Test
    void releaseRecordCountsPerFactionAndRoundTrips() {
        ReleaseRecord r = ReleaseRecord.EMPTY.with(Faction.NAVY).with(Faction.MERCHANTS).with(Faction.MERCHANTS).with(null);
        assertEquals(1, r.count(Faction.NAVY));
        assertEquals(2, r.count(Faction.MERCHANTS));
        assertEquals(0, r.count(Faction.PIRATES));
        assertEquals(4, r.total());
        assertEquals("navy 1, pirates 0, merchants 2, total 4", r.describe());
        var json = ReleaseRecord.CODEC.encodeStart(JsonOps.INSTANCE, r).getOrThrow();
        assertEquals(r, ReleaseRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(ReleaseRecord.EMPTY, ReleaseRecord.CODEC.parse(JsonOps.INSTANCE,
                ReleaseRecord.CODEC.encodeStart(JsonOps.INSTANCE, ReleaseRecord.EMPTY).getOrThrow()).getOrThrow());
    }
}

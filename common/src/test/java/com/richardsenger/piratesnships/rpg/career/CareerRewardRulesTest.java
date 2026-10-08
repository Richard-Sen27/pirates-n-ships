package com.richardsenger.piratesnships.rpg.career;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The pure rank-reward rules (CAR2): flag right, fee waiver, navy shipyard and discount, fence bonus, friendship, gifts. */
class CareerRewardRulesTest {

    private static final CareerRewardRules.Params P = CareerRewardRules.Params.defaults();

    private static CareerRecord navy(NavyRank rank) {
        return CareerRecord.EMPTY.withNavy(rank);
    }

    private static CareerRecord infamy(InfamyRank rank) {
        return CareerRecord.EMPTY.withInfamy(rank);
    }

    private static CareerRewardRules.Params allOff() {
        return new CareerRewardRules.Params(false, NavyRank.LIEUTENANT, false, NavyRank.LIEUTENANT, false, NavyRank.CAPTAIN,
                Map.of(), false, 20, false, InfamyRank.DREAD_CAPTAIN);
    }

    @Test
    void flagRightFromLieutenantRaisesTheStandingToTheMinimum() {
        assertFalse(CareerRewardRules.hasFlagRight(CareerRecord.EMPTY, P));
        assertFalse(CareerRewardRules.hasFlagRight(navy(NavyRank.MIDSHIPMAN), P));
        for (NavyRank r : List.of(NavyRank.LIEUTENANT, NavyRank.CAPTAIN, NavyRank.COMMODORE, NavyRank.ADMIRAL)) {
            assertTrue(CareerRewardRules.hasFlagRight(navy(r), P), r.id());
        }
        assertEquals(30, CareerRewardRules.effectiveNavyStanding(navy(NavyRank.LIEUTENANT), -20, 30, P));
        assertEquals(60, CareerRewardRules.effectiveNavyStanding(navy(NavyRank.LIEUTENANT), 60, 30, P), "never lowered");
        assertEquals(-20, CareerRewardRules.effectiveNavyStanding(navy(NavyRank.MIDSHIPMAN), -20, 30, P));
        assertEquals(-20, CareerRewardRules.effectiveNavyStanding(navy(NavyRank.ADMIRAL), -20, 30, allOff()));
    }

    @Test
    void feeWaiverFromItsRank() {
        assertFalse(CareerRewardRules.feeWaived(CareerRecord.EMPTY, P));
        assertFalse(CareerRewardRules.feeWaived(navy(NavyRank.MIDSHIPMAN), P));
        assertTrue(CareerRewardRules.feeWaived(navy(NavyRank.LIEUTENANT), P));
        assertFalse(CareerRewardRules.feeWaived(navy(NavyRank.ADMIRAL), allOff()));
    }

    @Test
    void navyShipyardFromCaptainWithTheRankDiscount() {
        assertFalse(CareerRewardRules.navyShipyard(CareerRecord.EMPTY, P));
        assertFalse(CareerRewardRules.navyShipyard(navy(NavyRank.MIDSHIPMAN), P));
        assertFalse(CareerRewardRules.navyShipyard(navy(NavyRank.LIEUTENANT), P));
        assertTrue(CareerRewardRules.navyShipyard(navy(NavyRank.CAPTAIN), P));
        assertTrue(CareerRewardRules.navyShipyard(navy(NavyRank.ADMIRAL), P));
        assertFalse(CareerRewardRules.navyShipyard(navy(NavyRank.ADMIRAL), allOff()));

        assertEquals(1.0, CareerRewardRules.shipPriceFactor(CareerRecord.EMPTY, P));
        assertEquals(1.0, CareerRewardRules.shipPriceFactor(navy(NavyRank.LIEUTENANT), P));
        assertEquals(0.7, CareerRewardRules.shipPriceFactor(navy(NavyRank.CAPTAIN), P));
        assertEquals(0.5, CareerRewardRules.shipPriceFactor(navy(NavyRank.COMMODORE), P));
        assertEquals(0.4, CareerRewardRules.shipPriceFactor(navy(NavyRank.ADMIRAL), P));
        assertEquals(1.0, CareerRewardRules.shipPriceFactor(navy(NavyRank.CAPTAIN), allOff()), "missing ranks pay full price");
        CareerRewardRules.Params odd = new CareerRewardRules.Params(true, NavyRank.LIEUTENANT, true, NavyRank.LIEUTENANT, true,
                NavyRank.CAPTAIN, Map.of(NavyRank.CAPTAIN, 3.0, NavyRank.ADMIRAL, -1.0), true, 20, true, InfamyRank.DREAD_CAPTAIN);
        assertEquals(1.0, CareerRewardRules.shipPriceFactor(navy(NavyRank.CAPTAIN), odd), "clamped to 1");
        assertEquals(0.0, CareerRewardRules.shipPriceFactor(navy(NavyRank.ADMIRAL), odd), "clamped to 0");
    }

    @Test
    void infamyPriceBonusScalesToTheTopRank() {
        assertEquals(0, CareerRewardRules.infamyPriceBonus(InfamyRank.DECKHAND, 20));
        assertEquals(7, CareerRewardRules.infamyPriceBonus(InfamyRank.BUCCANEER, 20));
        assertEquals(13, CareerRewardRules.infamyPriceBonus(InfamyRank.DREAD_CAPTAIN, 20));
        assertEquals(20, CareerRewardRules.infamyPriceBonus(InfamyRank.PIRATE_LORD, 20));
        assertEquals(0, CareerRewardRules.infamyPriceBonus(InfamyRank.PIRATE_LORD, -5));
        assertEquals(13, CareerRewardRules.infamyPriceBonus(infamy(InfamyRank.DREAD_CAPTAIN), P));
        assertEquals(0, CareerRewardRules.infamyPriceBonus(infamy(InfamyRank.PIRATE_LORD), allOff()));
    }

    @Test
    void piratesFriendlyFromDreadCaptain() {
        assertFalse(CareerRewardRules.piratesFriendly(infamy(InfamyRank.DECKHAND), P));
        assertFalse(CareerRewardRules.piratesFriendly(infamy(InfamyRank.BUCCANEER), P));
        assertTrue(CareerRewardRules.piratesFriendly(infamy(InfamyRank.DREAD_CAPTAIN), P));
        assertTrue(CareerRewardRules.piratesFriendly(infamy(InfamyRank.PIRATE_LORD), P));
        assertFalse(CareerRewardRules.piratesFriendly(infamy(InfamyRank.PIRATE_LORD), allOff()));
    }

    @Test
    void giftsForEveryRankGainedOnce() {
        String lt = CareerRewardRules.giftKey(NavyRank.LIEUTENANT);
        String mid = CareerRewardRules.giftKey(NavyRank.MIDSHIPMAN);
        String capt = CareerRewardRules.giftKey(NavyRank.CAPTAIN);
        assertEquals(List.of(mid), CareerRewardRules.giftsDue(CareerRecord.EMPTY, navy(NavyRank.MIDSHIPMAN), Set.of()));
        assertEquals(List.of(lt, capt), CareerRewardRules.giftsDue(navy(NavyRank.MIDSHIPMAN), navy(NavyRank.CAPTAIN), Set.of()),
                "a jump of two ranks gives both");
        assertEquals(List.of(capt), CareerRewardRules.giftsDue(navy(NavyRank.MIDSHIPMAN), navy(NavyRank.CAPTAIN), Set.of(lt)),
                "a rank already gifted gives nothing again");
        assertEquals(List.of(), CareerRewardRules.giftsDue(navy(NavyRank.CAPTAIN), CareerRecord.EMPTY, Set.of()), "leaving gives nothing");
        assertEquals(List.of(), CareerRewardRules.giftsDue(navy(NavyRank.CAPTAIN), navy(NavyRank.LIEUTENANT), Set.of()),
                "a demotion gives nothing");
        assertEquals(List.of(), CareerRewardRules.giftsDue(navy(NavyRank.LIEUTENANT), navy(NavyRank.LIEUTENANT).plus(CareerCounter.PIRATES_KILLED, 1),
                Set.of()), "a counter change gives nothing");
        assertEquals(List.of(CareerRewardRules.giftKey(InfamyRank.BUCCANEER), CareerRewardRules.giftKey(InfamyRank.DREAD_CAPTAIN)),
                CareerRewardRules.giftsDue(CareerRecord.EMPTY, infamy(InfamyRank.DREAD_CAPTAIN), Set.of()));
    }

    @Test
    void defaultShipPricesCoverEveryRank() {
        Map<NavyRank, Double> m = CareerRewardRules.defaultShipPrices();
        assertEquals(NavyRank.values().length, m.size());
        assertEquals(20, P.infamyPriceBonus());
        assertEquals(NavyRank.LIEUTENANT, P.flagRightRank());
        assertEquals(NavyRank.LIEUTENANT, P.feeWaiverRank());
        assertEquals(NavyRank.CAPTAIN, P.ordersMinRank());
        assertEquals(InfamyRank.DREAD_CAPTAIN, P.piratesFriendlyRank());
    }

    /** LAW3b (ART6 follow-up): a fresh Lieutenant's gift is the bicorne, the saber and the officer's coat. */
    @Test
    void lieutenantGiftIncludesTheOfficersCoat() {
        assertEquals(List.of("pirates_n_ships:officer_hat", "pirates_n_ships:saber", "pirates_n_ships:officers_coat"),
                CareerConfig.RANK_ITEMS.get(CareerRewardRules.giftKey(NavyRank.LIEUTENANT)).get());
        assertEquals(List.of(), CareerConfig.RANK_ITEMS.get(CareerRewardRules.giftKey(NavyRank.CAPTAIN)).get());
    }
}

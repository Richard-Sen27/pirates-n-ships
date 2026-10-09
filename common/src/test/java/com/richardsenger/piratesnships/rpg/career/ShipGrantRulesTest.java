package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.rpg.career.ShipGrantRules.Ladder;
import com.richardsenger.piratesnships.rpg.career.ShipGrantRules.Params;
import com.richardsenger.piratesnships.rpg.career.ShipGrantRules.Verdict;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The pure ship-grant rules (SHP1): once per ladder, the right template per ladder, the toggle, the redemption checks. */
class ShipGrantRulesTest {

    private static final Params P = Params.defaults();
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static CareerRecord navy(NavyRank rank) {
        return CareerRecord.EMPTY.withNavy(rank);
    }

    private static CareerRecord infamy(InfamyRank rank) {
        return CareerRecord.EMPTY.withInfamy(rank);
    }

    private static ShipCommission commission(Ladder ladder, UUID owner) {
        return new ShipCommission(ladder, P.template(ladder), "name", owner, "me");
    }

    @Test
    void defaultsAreCaptainDreadCaptainAndTheArmedSloops() {
        assertTrue(P.enabled());
        assertEquals(NavyRank.CAPTAIN, P.navyRank());
        assertEquals(InfamyRank.DREAD_CAPTAIN, P.infamyRank());
        assertEquals(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "navy_sloop_armed"), P.template(Ladder.NAVY));
        assertEquals(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "pirate_sloop_armed"), P.template(Ladder.PIRATES));
        assertEquals(PortKind.NAVY_OUTPOST, Ladder.NAVY.port());
        assertEquals(PortKind.PIRATE_ISLAND, Ladder.PIRATES.port());
    }

    @Test
    void navyGrantFromCaptainInService() {
        for (NavyRank r : List.of(NavyRank.NONE, NavyRank.MIDSHIPMAN, NavyRank.LIEUTENANT)) {
            assertEquals(List.of(), ShipGrantRules.grantsDue(navy(r), Set.of(), P), r.id());
        }
        for (NavyRank r : List.of(NavyRank.CAPTAIN, NavyRank.COMMODORE, NavyRank.ADMIRAL)) {
            assertEquals(List.of(Ladder.NAVY), ShipGrantRules.grantsDue(navy(r), Set.of(), P), r.id());
        }
    }

    @Test
    void pirateGrantFromDreadCaptain() {
        assertEquals(List.of(), ShipGrantRules.grantsDue(infamy(InfamyRank.DECKHAND), Set.of(), P));
        assertEquals(List.of(), ShipGrantRules.grantsDue(infamy(InfamyRank.BUCCANEER), Set.of(), P));
        assertEquals(List.of(Ladder.PIRATES), ShipGrantRules.grantsDue(infamy(InfamyRank.DREAD_CAPTAIN), Set.of(), P));
        assertEquals(List.of(Ladder.PIRATES), ShipGrantRules.grantsDue(infamy(InfamyRank.PIRATE_LORD), Set.of(), P));
    }

    @Test
    void eachLadderGrantsOnce() {
        Set<String> ledger = new HashSet<>();
        List<Ladder> first = ShipGrantRules.grantsDue(navy(NavyRank.CAPTAIN), ledger, P);
        assertEquals(List.of(Ladder.NAVY), first);
        ledger.add(ShipGrantRules.grantKey(Ladder.NAVY));
        assertEquals(List.of(), ShipGrantRules.grantsDue(navy(NavyRank.CAPTAIN), ledger, P), "the same rank again");
        assertEquals(List.of(), ShipGrantRules.grantsDue(navy(NavyRank.ADMIRAL), ledger, P), "a higher rank");
        // the other ladder is its own grant
        assertEquals(List.of(Ladder.PIRATES), ShipGrantRules.grantsDue(infamy(InfamyRank.PIRATE_LORD), ledger, P));
        // the redemption key alone does not count as granted, the grant key does
        assertFalse(ShipGrantRules.grantKey(Ladder.NAVY).equals(ShipGrantRules.redeemedKey(Ladder.NAVY)));
    }

    @Test
    void theRanksAreConfigurable() {
        Params p = new Params(true, NavyRank.ADMIRAL, InfamyRank.PIRATE_LORD, P.navyTemplate(), P.pirateTemplate());
        assertEquals(List.of(), ShipGrantRules.grantsDue(navy(NavyRank.COMMODORE), Set.of(), p));
        assertEquals(List.of(Ladder.NAVY), ShipGrantRules.grantsDue(navy(NavyRank.ADMIRAL), Set.of(), p));
        assertEquals(List.of(), ShipGrantRules.grantsDue(infamy(InfamyRank.DREAD_CAPTAIN), Set.of(), p));
        ResourceLocation custom = ResourceLocation.fromNamespaceAndPath("example", "frigate");
        assertEquals(custom, new Params(true, NavyRank.CAPTAIN, InfamyRank.DREAD_CAPTAIN, custom, P.pirateTemplate()).template(Ladder.NAVY));
    }

    @Test
    void theToggleGrantsAndRedeemsNothing() {
        Params off = new Params(false, NavyRank.CAPTAIN, InfamyRank.DREAD_CAPTAIN, P.navyTemplate(), P.pirateTemplate());
        assertEquals(List.of(), ShipGrantRules.grantsDue(navy(NavyRank.ADMIRAL), Set.of(), off));
        assertEquals(List.of(), ShipGrantRules.grantsDue(infamy(InfamyRank.PIRATE_LORD), Set.of(), off));
        assertEquals(Verdict.DISABLED, ShipGrantRules.redeem(commission(Ladder.NAVY, ME), ME, PortKind.NAVY_OUTPOST,
                navy(NavyRank.CAPTAIN), Set.of(), off));
    }

    @Test
    void redemptionChecks() {
        CareerRecord captain = navy(NavyRank.CAPTAIN);
        ShipCommission mine = commission(Ladder.NAVY, ME);
        assertEquals(Verdict.OK, ShipGrantRules.redeem(mine, ME, PortKind.NAVY_OUTPOST, captain, Set.of(), P));
        assertEquals(Verdict.NOT_YOURS, ShipGrantRules.redeem(mine, OTHER, PortKind.NAVY_OUTPOST, captain, Set.of(), P));
        assertEquals(Verdict.WRONG_PORT, ShipGrantRules.redeem(mine, ME, PortKind.PIRATE_ISLAND, captain, Set.of(), P));
        assertEquals(Verdict.WRONG_PORT, ShipGrantRules.redeem(mine, ME, PortKind.SEAFARER_VILLAGE, captain, Set.of(), P));
        assertEquals(Verdict.ALREADY_REDEEMED, ShipGrantRules.redeem(mine, ME, PortKind.NAVY_OUTPOST, captain,
                Set.of(ShipGrantRules.redeemedKey(Ladder.NAVY)), P));
        // a deserter (or a resigned captain) holds no navy rank in service any more
        assertEquals(Verdict.RANK_LOST, ShipGrantRules.redeem(mine, ME, PortKind.NAVY_OUTPOST, CareerRecord.EMPTY, Set.of(), P));

        ShipCommission pirate = commission(Ladder.PIRATES, ME);
        assertEquals(Verdict.OK, ShipGrantRules.redeem(pirate, ME, PortKind.PIRATE_ISLAND, infamy(InfamyRank.DREAD_CAPTAIN), Set.of(), P));
        assertEquals(Verdict.WRONG_PORT, ShipGrantRules.redeem(pirate, ME, PortKind.NAVY_OUTPOST, infamy(InfamyRank.DREAD_CAPTAIN), Set.of(), P));
        // the navy ship's redemption does not use up the pirate one
        assertEquals(Verdict.OK, ShipGrantRules.redeem(pirate, ME, PortKind.PIRATE_ISLAND, infamy(InfamyRank.PIRATE_LORD),
                Set.of(ShipGrantRules.redeemedKey(Ladder.NAVY)), P));
    }

    @Test
    void baseNamesComeFromTheLaddersList() {
        assertTrue(ShipGrantRules.NAVY_NAMES.contains(ShipGrantRules.baseName(Ladder.NAVY, ME)));
        assertTrue(ShipGrantRules.PIRATE_NAMES.contains(ShipGrantRules.baseName(Ladder.PIRATES, ME)));
        assertEquals(ShipGrantRules.baseName(Ladder.NAVY, ME), ShipGrantRules.baseName(Ladder.NAVY, ME), "stable per player");
    }
}

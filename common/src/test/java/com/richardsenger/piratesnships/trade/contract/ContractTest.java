package com.richardsenger.piratesnships.trade.contract;

import com.richardsenger.piratesnships.trade.good.TradeGood;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.PortProfile;
import com.richardsenger.piratesnships.trade.market.ProfileDeriver;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.richardsenger.piratesnships.trade.TradeFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ContractTest {

    private static final ContractParams C = ContractParams.DEFAULTS;
    private static final ResourceLocation A = ResourceLocation.parse("test:a");
    private static final ResourceLocation B = ResourceLocation.parse("test:b");
    private static final ResourceLocation D = ResourceLocation.parse("test:d");
    private static final UUID CAPTAIN = new UUID(1, 2);
    private static final UUID OTHER = new UUID(3, 4);

    private static List<ContractGenerator.Destination> destinations() {
        return List.of(
                new ContractGenerator.Destination(B, ProfileDeriver.derive(PortKind.NAVY_OUTPOST, Climate.COLD, 2, GOODS), 1500, 0.1),
                new ContractGenerator.Destination(D, ProfileDeriver.derive(PortKind.PIRATE_ISLAND, Climate.ARID, 3, GOODS), 4000, 0.9));
    }

    private static PortProfile origin() {
        return ProfileDeriver.derive(PortKind.SEAFARER_VILLAGE, Climate.TROPICAL, 1, GOODS);
    }

    @Test
    void generationIsDeterministicAndPlausible() {
        var a = ContractGenerator.generate(A, origin(), destinations(), GOODS, 10, 99, C, P);
        assertEquals(a, ContractGenerator.generate(A, origin(), destinations(), GOODS, 10, 99, C, P));
        assertEquals(a, ContractGenerator.generate(A, origin(), List.copyOf(destinations()).reversed(), GOODS, 10, 99, C, P), "input order doesn't matter");
        assertNotEquals(a, ContractGenerator.generate(A, origin(), destinations(), GOODS, 11, 99, C, P), "another day, other offers");
        assertEquals(C.offersPerDay(), a.size());
        for (DeliveryContract c : a) {
            assertEquals(DeliveryContract.State.OFFERED, c.state());
            assertEquals(A, c.origin());
            assertNotEquals(A, c.destination());
            assertNotEquals(GoodRole.DEMANDS, origin().role(c.good()));
            assertTrue(origin().role(c.good()).traded());
            assertTrue(c.quantity() >= 8 && c.quantity() % 8 == 0);
            double dist = c.destination().equals(B) ? 1500 : 4000;
            assertEquals(10 + (long) Math.ceil(dist / C.blocksPerDay()) + C.slackDays(), c.deadlineDay());
            assertEquals(10 + C.offerLifetimeDays(), c.offerExpiresDay());
            assertEquals((int) Math.ceil(c.reward() * C.depositFraction()), c.deposit());
            assertTrue(c.reward() > 0);
        }
        assertEquals(a.size(), a.stream().map(DeliveryContract::id).distinct().count(), "unique ids");
    }

    @Test
    void noOffersWithoutDestinationsOrWhenDisabled() {
        assertTrue(ContractGenerator.generate(A, origin(), List.of(), GOODS, 1, 1, C, P).isEmpty());
        var off = new ContractParams(false, 3, 32, 192, 0.6, 0.8, 0.15, 0.6, 4000, 2, 2, 0.2, 3);
        assertTrue(ContractGenerator.generate(A, origin(), destinations(), GOODS, 1, 1, off, P).isEmpty());
    }

    @Test
    void rewardGrowsWithDistanceQuantityPriceDifferenceAndRisk() {
        TradeGood g = SUGAR_DEF;
        int base = ContractGenerator.reward(64, g, GoodRole.NEUTRAL, GoodRole.NEUTRAL, 1000, 0, C, P);
        assertTrue(ContractGenerator.reward(64, g, GoodRole.NEUTRAL, GoodRole.NEUTRAL, 3000, 0, C, P) > base, "distance");
        assertTrue(ContractGenerator.reward(128, g, GoodRole.NEUTRAL, GoodRole.NEUTRAL, 1000, 0, C, P) > base, "quantity");
        assertTrue(ContractGenerator.reward(64, g, GoodRole.PRODUCES, GoodRole.DEMANDS, 1000, 0, C, P) > base, "price difference");
        assertTrue(ContractGenerator.reward(64, g, GoodRole.NEUTRAL, GoodRole.NEUTRAL, 1000, 1, C, P) > base, "risk");
    }

    private static DeliveryContract offer() {
        return new DeliveryContract(new UUID(9, 9), SUGAR, 64, A, B, 10, 12, 15, 300, 60, DeliveryContract.State.OFFERED, Optional.empty());
    }

    @Test
    void acceptDeliverAndPayout() {
        var accepted = offer().accept(CAPTAIN, 11);
        assertEquals(DeliveryContract.Outcome.ACCEPTED, accepted.outcome());
        assertEquals(60, accepted.depositDue());
        assertEquals(Optional.of(CAPTAIN), accepted.contract().holder());
        assertEquals(DeliveryContract.Outcome.NOT_OFFERED, accepted.contract().accept(OTHER, 11).outcome());

        DeliveryContract c = accepted.contract();
        assertEquals(DeliveryContract.Outcome.WRONG_HOLDER, c.deliver(OTHER, B, 64, 13).outcome());
        assertEquals(DeliveryContract.Outcome.WRONG_PORT, c.deliver(CAPTAIN, A, 64, 13).outcome());
        var notEnough = c.deliver(CAPTAIN, B, 63, 13);
        assertEquals(DeliveryContract.Outcome.NOT_ENOUGH, notEnough.outcome());
        assertSame(c, notEnough.contract());
        var done = c.deliver(CAPTAIN, B, 100, 15);
        assertEquals(DeliveryContract.Outcome.DELIVERED, done.outcome());
        assertEquals(64, done.consumed());
        assertEquals(360, done.payout(), "reward + deposit back");
        assertEquals(DeliveryContract.State.DELIVERED, done.contract().state());
        assertEquals(DeliveryContract.Outcome.NOT_ACCEPTED, done.contract().deliver(CAPTAIN, B, 64, 15).outcome(), "no double delivery");
        assertEquals(DeliveryContract.Outcome.UNCHANGED, done.contract().update(100).outcome(), "finished stays finished");
    }

    @Test
    void deadlinesExpiryAndAbandon() {
        // Offer lifetime
        assertEquals(DeliveryContract.Outcome.UNCHANGED, offer().update(12).outcome());
        var expired = offer().update(13);
        assertEquals(DeliveryContract.State.EXPIRED, expired.contract().state());
        assertEquals(DeliveryContract.State.EXPIRED, offer().accept(CAPTAIN, 13).contract().state(), "can't accept an old offer");
        // Deadline: the last day still counts
        DeliveryContract c = offer().accept(CAPTAIN, 10).contract();
        assertEquals(DeliveryContract.Outcome.DELIVERED, c.deliver(CAPTAIN, B, 64, 15).outcome());
        var late = c.deliver(CAPTAIN, B, 64, 16);
        assertEquals(DeliveryContract.Outcome.FAILED, late.outcome());
        assertEquals(0, late.payout());
        assertEquals(0, late.consumed());
        assertEquals(DeliveryContract.State.FAILED, c.update(16).contract().state());
        // Abandon
        assertEquals(DeliveryContract.Outcome.WRONG_HOLDER, c.abandon(OTHER).outcome());
        var abandoned = c.abandon(CAPTAIN);
        assertEquals(DeliveryContract.Outcome.ABANDONED, abandoned.outcome());
        assertEquals(DeliveryContract.State.FAILED, abandoned.contract().state());
        assertEquals(DeliveryContract.Outcome.NOT_ACCEPTED, offer().abandon(CAPTAIN).outcome());
        assertTrue(DeliveryContract.State.FAILED.finished() && !DeliveryContract.State.ACCEPTED.finished());
    }

    @Test
    void offersFollowPortProfiles() {
        PortProfile from = new PortProfile(PortKind.SEAFARER_VILLAGE, Climate.TROPICAL, 0, Map.of(SUGAR, GoodRole.PRODUCES));
        PortProfile to = new PortProfile(PortKind.NAVY_OUTPOST, Climate.COLD, 0, Map.of(SUGAR, GoodRole.DEMANDS));
        PortProfile noDemand = new PortProfile(PortKind.NAVY_OUTPOST, Climate.COLD, 0, Map.of(SUGAR, GoodRole.PRODUCES));
        var offers = ContractGenerator.generate(A, from, List.of(new ContractGenerator.Destination(B, to, 2000, 0.5),
                new ContractGenerator.Destination(D, noDemand, 2000, 0.5)), GOODS, 1, 1, C, P);
        assertEquals(1, offers.size(), "one candidate pair only");
        assertEquals(B, offers.get(0).destination());
        assertEquals(SUGAR, offers.get(0).good());
    }
}

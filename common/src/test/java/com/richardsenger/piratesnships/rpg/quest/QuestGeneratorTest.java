package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** The pure offer generator: per-kind types, what the world must back, amounts, rewards and determinism. */
class QuestGeneratorTest {

    private static final ResourceLocation PORT = Constants.id("cane_bay");
    private static final ResourceLocation DEST = Constants.id("fort_royal");
    private static final List<QuestGenerator.DeliveryOption> DELIVERIES =
            List.of(new QuestGenerator.DeliveryOption(Constants.id("sugar"), 64, DEST, 200));
    private static final List<QuestGenerator.TreasureOption> TREASURES =
            List.of(new QuestGenerator.TreasureOption(Constants.id("tortuga"), new BlockPos(100, 60, 200)));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static QuestGenerator.Context ctx(PortKind kind, Climate climate, long day, List<QuestGenerator.DeliveryOption> d,
                                              List<QuestGenerator.TreasureOption> t) {
        return new QuestGenerator.Context(PORT, kind, climate, day, 12345L, d, t);
    }

    private static QuestGenerator.Context ctx(PortKind kind, long day) {
        return ctx(kind, Climate.TEMPERATE, day, DELIVERIES, TREASURES);
    }

    private static QuestParams with(boolean treasure, boolean deeds, double scale, double krakenChance) {
        QuestParams d = QuestParams.DEFAULTS;
        return new QuestParams(d.offersPerPort(), d.offerDays(), d.deadlineDays(), d.maxActive(), scale, d.huntMin(), d.huntMax(),
                d.monsterMin(), d.monsterMax(), d.turnInMax(), krakenChance, d.perPirate(), d.perNavy(), d.perPrisoner(), d.perShark(),
                d.kraken(), d.treasure(), d.deliverMultiplier(), treasure, deeds, d.types());
    }

    @Test
    void sameDayAndPortGiveTheSameOffers() {
        List<Quest> a = QuestGenerator.offers(ctx(PortKind.SEAFARER_VILLAGE, 7), 3, QuestParams.DEFAULTS);
        List<Quest> b = QuestGenerator.offers(ctx(PortKind.SEAFARER_VILLAGE, 7), 3, QuestParams.DEFAULTS);
        assertEquals(a, b);
        assertNotEquals(a, QuestGenerator.offers(ctx(PortKind.SEAFARER_VILLAGE, 8), 3, QuestParams.DEFAULTS));
        assertEquals(3, a.size());
        assertEquals(3, a.stream().map(Quest::type).distinct().count(), "one day's offers differ in type while the pool allows");
    }

    @Test
    void offersHaveTheirDaysDeedAndState() {
        for (Quest q : QuestGenerator.offers(ctx(PortKind.NAVY_OUTPOST, 20), 5, QuestParams.DEFAULTS)) {
            assertEquals(QuestState.OFFERED, q.state());
            assertEquals(20, q.offeredDay());
            assertEquals(21, q.offerExpiresDay(), "offer_days 2: today and tomorrow");
            assertEquals(Deed.COMPLETE_NAVY_QUEST, q.rewardDeed());
            assertEquals(PortKind.NAVY_OUTPOST, q.giver());
            assertEquals(PORT, q.port());
            assertTrue(q.rewardCoins() >= 1);
        }
    }

    @Test
    void eachKindOffersOnlyItsTypes() {
        for (PortKind kind : PortKind.values()) {
            Set<QuestType> seen = EnumSet.noneOf(QuestType.class);
            for (long day = 0; day < 60; day++) {
                for (Quest q : QuestGenerator.offers(ctx(kind, day), 3, QuestParams.DEFAULTS)) seen.add(q.type());
            }
            assertEquals(Set.copyOf(QuestParams.DEFAULT_TYPES.get(kind)), seen, "types at " + kind);
            for (QuestType t : seen) {
                assertTrue(t.available());
                assertTrue(t.allowedAt(kind));
            }
        }
        assertFalse(QuestType.HUNT_NAVY.allowedAt(PortKind.NAVY_OUTPOST));
        assertFalse(QuestType.HUNT_PIRATES.allowedAt(PortKind.PIRATE_ISLAND));
        for (QuestType later : List.of(QuestType.HUNT_CAPTAIN, QuestType.ESCORT, QuestType.PLUNDER_CONVOY, QuestType.HUNT_PATROL, QuestType.HUNT_SHIP)) {
            assertFalse(later.available(), later + " is not offered yet");
        }
    }

    @Test
    void notListedOrNotBackedTypesAreNotOffered() {
        // a config listing hunt_navy at a village and an unavailable type: both filtered
        QuestParams d = QuestParams.DEFAULTS;
        QuestParams odd = new QuestParams(d.offersPerPort(), d.offerDays(), d.deadlineDays(), d.maxActive(), d.rewardScale(), d.huntMin(),
                d.huntMax(), d.monsterMin(), d.monsterMax(), d.turnInMax(), d.krakenChance(), d.perPirate(), d.perNavy(), d.perPrisoner(),
                d.perShark(), d.kraken(), d.treasure(), d.deliverMultiplier(), true, true,
                Map.of(PortKind.SEAFARER_VILLAGE, List.of(QuestType.HUNT_NAVY, QuestType.ESCORT, QuestType.DELIVER, QuestType.FIND_TREASURE)));
        assertEquals(List.of(QuestType.DELIVER, QuestType.FIND_TREASURE), QuestGenerator.offerable(ctx(PortKind.SEAFARER_VILLAGE, 1), odd));
        assertEquals(List.of(), QuestGenerator.offerable(ctx(PortKind.NAVY_OUTPOST, 1), odd), "nothing listed for outposts");
        assertTrue(QuestGenerator.offers(ctx(PortKind.NAVY_OUTPOST, 1), 3, odd).isEmpty());
        // no destinations, no treasure in reach
        List<QuestType> bare = QuestGenerator.offerable(ctx(PortKind.SEAFARER_VILLAGE, Climate.TEMPERATE, 1, List.of(), List.of()), QuestParams.DEFAULTS);
        assertEquals(List.of(QuestType.KILL_MONSTER, QuestType.HUNT_PIRATES), bare);
        // treasure quests off; deeds not recorded (reputation off)
        assertFalse(QuestGenerator.offerable(ctx(PortKind.PIRATE_ISLAND, 1), with(false, true, 1, 0.15)).contains(QuestType.FIND_TREASURE));
        assertEquals(List.of(QuestType.FIND_TREASURE, QuestType.DELIVER, QuestType.KILL_MONSTER),
                QuestGenerator.offerable(ctx(PortKind.PIRATE_ISLAND, 1), with(true, false, 1, 0.15)));
    }

    @Test
    void amountsAndRewardsFollowTheParams() {
        QuestParams p = QuestParams.DEFAULTS;
        for (long day = 0; day < 80; day++) {
            for (PortKind kind : PortKind.values()) {
                for (Quest q : QuestGenerator.offers(ctx(kind, day), 4, p)) {
                    switch (q.type()) {
                        case HUNT_PIRATES -> {
                            assertTrue(q.needed() >= p.huntMin() && q.needed() <= p.huntMax());
                            assertEquals(q.needed() * p.perPirate(), q.rewardCoins());
                        }
                        case HUNT_NAVY -> assertEquals(q.needed() * p.perNavy(), q.rewardCoins());
                        case TURN_IN -> {
                            assertTrue(q.needed() >= 1 && q.needed() <= p.turnInMax());
                            assertEquals(q.needed() * p.perPrisoner(), q.rewardCoins());
                        }
                        case KILL_MONSTER -> {
                            QuestTarget.Kill k = (QuestTarget.Kill) q.target();
                            if (k.entity().equals(QuestGenerator.KRAKEN)) {
                                assertEquals(1, q.needed());
                                assertEquals(p.kraken(), q.rewardCoins());
                            } else {
                                assertEquals(QuestGenerator.SHARK, k.entity());
                                assertTrue(q.needed() >= p.monsterMin() && q.needed() <= p.monsterMax());
                                assertEquals(q.needed() * p.perShark(), q.rewardCoins());
                            }
                        }
                        case DELIVER -> {
                            QuestTarget.Cargo c = (QuestTarget.Cargo) q.target();
                            assertEquals(DEST, c.destination());
                            assertEquals(64, c.quantity());
                            assertTrue(c.contract().isEmpty());
                            assertEquals(300, q.rewardCoins(), "200 × 1.5");
                        }
                        case FIND_TREASURE -> {
                            assertEquals(TREASURES.get(0).site(), ((QuestTarget.Treasure) q.target()).site());
                            assertEquals(p.treasure(), q.rewardCoins());
                        }
                        default -> fail("unexpected type " + q.type());
                    }
                }
            }
        }
    }

    @Test
    void rewardScaleAndKraken() {
        Quest scaled = QuestGenerator.offer(QuestType.FIND_TREASURE, ctx(PortKind.PIRATE_ISLAND, 3), 1, with(true, true, 2.5, 0.15)).orElseThrow();
        assertEquals(250, scaled.rewardCoins());
        Quest zero = QuestGenerator.offer(QuestType.FIND_TREASURE, ctx(PortKind.PIRATE_ISLAND, 3), 1, with(true, true, 0.0, 0.15)).orElseThrow();
        assertEquals(1, zero.rewardCoins(), "at least one doubloon");
        Set<ResourceLocation> monsters = QuestGenerator.offers(ctx(PortKind.SEAFARER_VILLAGE, Climate.COLD, 4, DELIVERIES, TREASURES), 8,
                        QuestParams.DEFAULTS).stream()
                .filter(q -> q.type() == QuestType.KILL_MONSTER).map(q -> ((QuestTarget.Kill) q.target()).entity()).collect(Collectors.toSet());
        assertEquals(Set.of(QuestGenerator.KRAKEN), monsters, "no sharks in a cold climate");
        assertTrue(QuestGenerator.offer(QuestType.KILL_MONSTER, ctx(PortKind.SEAFARER_VILLAGE, 4), 9, with(true, true, 1, 1.0))
                .map(q -> ((QuestTarget.Kill) q.target()).entity()).filter(QuestGenerator.KRAKEN::equals).isPresent());
        assertTrue(QuestGenerator.offer(QuestType.HUNT_NAVY, ctx(PortKind.NAVY_OUTPOST, 4), 9, QuestParams.DEFAULTS).isEmpty());
    }
}

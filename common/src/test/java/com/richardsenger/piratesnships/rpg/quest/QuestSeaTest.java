package com.richardsenger.piratesnships.rpg.quest;

import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.rpg.career.LetterState;
import com.richardsenger.piratesnships.rpg.career.ShipPrizes;
import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** QST2: the sea quests' offers (pure generator) and how ship endings, escort polls and voyage ends move them. */
class QuestSeaTest {

    private static final ResourceLocation PORT = Constants.id("cane_bay");
    private static final ResourceLocation NEAR = Constants.id("fort_royal");
    private static final ResourceLocation FAR = Constants.id("far_away");
    private static final UUID CONVOY = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final QuestGenerator.SeaOptions SEA = new QuestGenerator.SeaOptions(List.of(
            new QuestGenerator.EscortOption(NEAR, 1000), new QuestGenerator.EscortOption(FAR, 9000),
            new QuestGenerator.EscortOption(PORT, 0)), true, true, true);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static QuestGenerator.Context ctx(PortKind kind, long day, QuestGenerator.SeaOptions sea) {
        return new QuestGenerator.Context(PORT, kind, Climate.TEMPERATE, day, 777L, List.of(), List.of(), List.of(), sea);
    }

    private static Quest active(QuestType type, QuestTarget target, int needed) {
        Quest offer = new Quest(UUID.randomUUID(), PORT, PortKind.PIRATE_ISLAND, type, target, needed, 0, 100, Deed.COMPLETE_PIRATE_QUEST,
                10, 11, 0, QuestState.OFFERED);
        return QuestRules.accept(offer, 10, 5);
    }

    private static QuestEvent.ShipDefeated ship(VoyageKind kind, Faction faction, QuestEvent.How how) {
        return new QuestEvent.ShipDefeated(UUID.randomUUID(), kind, faction, how);
    }

    // ------------------------------------------------------------------ generator

    @Test
    void seaQuestsAreOfferedByTheirPortKindsOnly() {
        assertTrue(QuestType.ESCORT.allowedAt(PortKind.SEAFARER_VILLAGE) && QuestType.ESCORT.allowedAt(PortKind.NAVY_OUTPOST));
        assertFalse(QuestType.ESCORT.allowedAt(PortKind.PIRATE_ISLAND));
        for (QuestType t : List.of(QuestType.PLUNDER_CONVOY, QuestType.HUNT_PATROL)) {
            assertTrue(t.allowedAt(PortKind.PIRATE_ISLAND));
            assertFalse(t.allowedAt(PortKind.SEAFARER_VILLAGE) || t.allowedAt(PortKind.NAVY_OUTPOST), t.id());
        }
        assertTrue(QuestType.HUNT_SHIP.allowedAt(PortKind.NAVY_OUTPOST));
        assertFalse(QuestType.HUNT_SHIP.allowedAt(PortKind.PIRATE_ISLAND) || QuestType.HUNT_SHIP.allowedAt(PortKind.SEAFARER_VILLAGE));
        assertEquals(List.of(QuestType.ESCORT), seaOfferable(ctx(PortKind.SEAFARER_VILLAGE, 1, SEA), QuestParams.DEFAULTS));
        assertEquals(List.of(QuestType.ESCORT, QuestType.HUNT_SHIP), seaOfferable(ctx(PortKind.NAVY_OUTPOST, 1, SEA), QuestParams.DEFAULTS));
        assertEquals(List.of(QuestType.PLUNDER_CONVOY, QuestType.HUNT_PATROL),
                seaOfferable(ctx(PortKind.PIRATE_ISLAND, 1, SEA), QuestParams.DEFAULTS));
    }

    /** The sea quests among the offerable types (the deed-tracked hunts need no world backing and are offerable too). */
    private static List<QuestType> seaOfferable(QuestGenerator.Context ctx, QuestParams p) {
        return QuestGenerator.offerable(ctx, p).stream().filter(QuestType::seaQuest).toList();
    }

    @Test
    void aHuntIsOfferedOnlyWhileItsPreySails() {
        QuestGenerator.SeaOptions none = new QuestGenerator.SeaOptions(List.of(), false, false, false);
        for (PortKind k : PortKind.values()) {
            assertTrue(QuestGenerator.offerable(ctx(k, 1, none), QuestParams.DEFAULTS).stream().noneMatch(QuestType::seaQuest), k.name());
        }
        QuestGenerator.SeaOptions patrolsOnly = new QuestGenerator.SeaOptions(List.of(), false, true, false);
        assertEquals(List.of(QuestType.HUNT_PATROL), seaOfferable(ctx(PortKind.PIRATE_ISLAND, 1, patrolsOnly), QuestParams.DEFAULTS));
        assertEquals(List.of(), seaOfferable(ctx(PortKind.NAVY_OUTPOST, 1, patrolsOnly), QuestParams.DEFAULTS));
        // the old contexts (no sea options) offer none
        QuestGenerator.Context old = new QuestGenerator.Context(PORT, PortKind.PIRATE_ISLAND, Climate.TEMPERATE, 1, 777L, List.of(), List.of());
        assertTrue(seaOfferable(old, QuestParams.DEFAULTS).isEmpty());
    }

    @Test
    void theToggleOffersNoSeaQuest() {
        QuestParams off = QuestParams.DEFAULTS.withSea(QuestParams.Sea.DEFAULTS.withEnabled(false));
        for (PortKind k : PortKind.values()) {
            assertTrue(seaOfferable(ctx(k, 1, SEA), off).isEmpty(), k.name());
            for (long day = 0; day < 20; day++) {
                assertTrue(QuestGenerator.offers(ctx(k, day, SEA), 5, off).stream().noneMatch(q -> q.type().seaQuest()), k.name());
            }
            assertTrue(QuestGenerator.offer(QuestType.ESCORT, ctx(k, 1, SEA), 1, off).isEmpty(), k.name());
        }
    }

    @Test
    void escortsGoToANearDestinationAndPayByDistance() {
        for (long day = 0; day < 30; day++) {
            Quest q = QuestGenerator.offer(QuestType.ESCORT, ctx(PortKind.NAVY_OUTPOST, day, SEA), day, QuestParams.DEFAULTS).orElseThrow();
            QuestTarget.Escort e = assertInstanceOf(QuestTarget.Escort.class, q.target());
            assertEquals(NEAR, e.destination(), "never the port itself, never beyond escort_max_distance");
            assertTrue(e.voyage().isEmpty() && e.name().isEmpty() && e.lastLeg() == -1);
            assertEquals(120 + 60, q.rewardCoins(), "escort + escort_per_1000 × 1000 / 1000");
            assertEquals(QuestState.OFFERED, q.state());
        }
        QuestGenerator.SeaOptions farOnly = new QuestGenerator.SeaOptions(List.of(new QuestGenerator.EscortOption(FAR, 9000)), true, true, true);
        assertFalse(QuestGenerator.offerable(ctx(PortKind.SEAFARER_VILLAGE, 1, farOnly), QuestParams.DEFAULTS).contains(QuestType.ESCORT));
    }

    @Test
    void shipHuntsAskForCountMinToCountMaxShips() {
        QuestParams p = QuestParams.DEFAULTS.withSea(new QuestParams.Sea(true, 2, 4, 10, 20, 30, 120, 60, 2500, 0.5, 96));
        Set<Integer> seen = new java.util.TreeSet<>();
        for (long day = 0; day < 60; day++) {
            for (Quest q : QuestGenerator.offers(ctx(PortKind.PIRATE_ISLAND, day, SEA), 4, p)) {
                if (!q.type().seaQuest()) continue;
                assertTrue(q.needed() >= 2 && q.needed() <= 4, q.toString());
                seen.add(q.needed());
                long per = q.type() == QuestType.PLUNDER_CONVOY ? 10 : 20;
                assertEquals(per * q.needed(), q.rewardCoins(), q.type().id());
                assertEquals(Deed.COMPLETE_PIRATE_QUEST, q.rewardDeed());
            }
            for (Quest q : QuestGenerator.offers(ctx(PortKind.NAVY_OUTPOST, day, SEA), 4, p)) {
                if (q.type() == QuestType.HUNT_SHIP) assertEquals(30L * q.needed(), q.rewardCoins());
            }
        }
        assertEquals(Set.of(2, 3, 4), seen);
    }

    @Test
    void offersAreDeterministic() {
        assertEquals(QuestGenerator.offers(ctx(PortKind.NAVY_OUTPOST, 4, SEA), 3, QuestParams.DEFAULTS),
                QuestGenerator.offers(ctx(PortKind.NAVY_OUTPOST, 4, SEA), 3, QuestParams.DEFAULTS));
    }

    // ------------------------------------------------------------------ rules: the hunts

    @Test
    void aConvoyRaidCountsPlunderedConvoysOnly() {
        Quest q = active(QuestType.PLUNDER_CONVOY, QuestTarget.None.INSTANCE, 2);
        assertSame(q, QuestRules.advance(q, ship(VoyageKind.CONVOY, Faction.MERCHANTS, QuestEvent.How.SUNK)));
        assertSame(q, QuestRules.advance(q, ship(VoyageKind.CONVOY, Faction.MERCHANTS, QuestEvent.How.CAPTURED)));
        assertSame(q, QuestRules.advance(q, ship(VoyageKind.PATROL, Faction.NAVY, QuestEvent.How.PLUNDERED)));
        Quest one = QuestRules.advance(q, ship(VoyageKind.CONVOY, Faction.MERCHANTS, QuestEvent.How.PLUNDERED));
        assertEquals(1, one.progress());
        assertEquals(QuestState.ACTIVE, one.state());
        Quest two = QuestRules.advance(one, ship(VoyageKind.CONVOY, Faction.MERCHANTS, QuestEvent.How.PLUNDERED));
        assertEquals(QuestState.DONE, two.state());
    }

    @Test
    void aPatrolHuntCountsPatrolsSunkOrCaptured() {
        Quest q = active(QuestType.HUNT_PATROL, QuestTarget.None.INSTANCE, 2);
        assertSame(q, QuestRules.advance(q, ship(VoyageKind.CONVOY, Faction.MERCHANTS, QuestEvent.How.SUNK)));
        assertSame(q, QuestRules.advance(q, ship(VoyageKind.RAID, Faction.PIRATES, QuestEvent.How.SUNK)));
        assertSame(q, QuestRules.advance(q, ship(VoyageKind.PATROL, Faction.NAVY, QuestEvent.How.PLUNDERED)));
        Quest one = QuestRules.advance(q, ship(VoyageKind.PATROL, Faction.NAVY, QuestEvent.How.SUNK));
        assertEquals(1, one.progress());
        assertEquals(QuestState.DONE, QuestRules.advance(one, ship(VoyageKind.PATROL, Faction.NAVY, QuestEvent.How.CAPTURED)).state());
    }

    @Test
    void aShipHuntCountsAnyShipUnderThePiratesColours() {
        Quest q = active(QuestType.HUNT_SHIP, QuestTarget.None.INSTANCE, 3);
        assertSame(q, QuestRules.advance(q, ship(VoyageKind.PATROL, Faction.NAVY, QuestEvent.How.SUNK)));
        assertSame(q, QuestRules.advance(q, ship(VoyageKind.CONVOY, Faction.MERCHANTS, QuestEvent.How.CAPTURED)));
        assertSame(q, QuestRules.advance(q, ship(VoyageKind.RAID, Faction.PIRATES, QuestEvent.How.PLUNDERED)));
        Quest a = QuestRules.advance(q, ship(VoyageKind.RAID, Faction.PIRATES, QuestEvent.How.SUNK));
        Quest b = QuestRules.advance(a, ship(VoyageKind.CONVOY, Faction.PIRATES, QuestEvent.How.CAPTURED)); // a pirate-crewed voyage of any kind
        assertEquals(2, b.progress());
        assertEquals(QuestState.DONE, QuestRules.advance(b, ship(VoyageKind.PATROL, Faction.PIRATES, QuestEvent.How.SUNK)).state());
    }

    @Test
    void shipEventsMoveNoOtherQuest() {
        for (QuestType t : EnumSet.complementOf(EnumSet.of(QuestType.PLUNDER_CONVOY, QuestType.HUNT_PATROL, QuestType.HUNT_SHIP))) {
            for (QuestEvent.How how : QuestEvent.How.values()) {
                for (VoyageKind k : VoyageKind.values()) {
                    for (Faction f : Faction.values()) {
                        assertFalse(QuestRules.countsShip(t, new QuestEvent.ShipDefeated(CONVOY, k, f, how)), t + " " + k + " " + f + " " + how);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ rules: the escort

    private static Quest escort(int legs, double fraction) {
        Quest q = active(QuestType.ESCORT, new QuestTarget.Escort(NEAR), 1);
        return QuestRules.bindEscort(q, CONVOY, "Merry Gull", legs, fraction);
    }

    @Test
    void escortLegsNeededRoundUp() {
        assertEquals(1, QuestRules.escortLegsNeeded(1, 0.5));
        assertEquals(1, QuestRules.escortLegsNeeded(2, 0.5));
        assertEquals(2, QuestRules.escortLegsNeeded(3, 0.5));
        assertEquals(5, QuestRules.escortLegsNeeded(10, 0.5));
        assertEquals(10, QuestRules.escortLegsNeeded(10, 1.0));
        assertEquals(1, QuestRules.escortLegsNeeded(10, 0.0), "at least one leg");
        assertEquals(1, QuestRules.escortLegsNeeded(0, 0.5), "a route of no legs still needs one look");
    }

    @Test
    void bindingAnEscortNamesItsConvoy() {
        Quest q = escort(6, 0.5);
        QuestTarget.Escort t = assertInstanceOf(QuestTarget.Escort.class, q.target());
        assertEquals(CONVOY, t.voyage().orElseThrow());
        assertEquals("Merry Gull", t.name());
        assertEquals(6, t.legs());
        assertEquals(3, q.needed());
        assertEquals(0, q.progress());
        assertEquals(QuestState.ACTIVE, q.state());
    }

    @Test
    void eachLegCountsOnceAndOnlyForwards() {
        Quest q = escort(4, 0.5); // needs 2
        Quest a = QuestRules.advance(q, new QuestEvent.EscortSeen(CONVOY, 0));
        assertEquals(1, a.progress());
        assertSame(a, QuestRules.advance(a, new QuestEvent.EscortSeen(CONVOY, 0)), "the same leg again");
        assertSame(q, QuestRules.advance(q, new QuestEvent.EscortSeen(OTHER, 0)), "another convoy");
        Quest b = QuestRules.advance(a, new QuestEvent.EscortSeen(CONVOY, 2));
        assertEquals(2, b.progress());
        assertEquals(QuestState.ACTIVE, b.state(), "done only on arrival");
        assertSame(b, QuestRules.advance(b, new QuestEvent.EscortSeen(CONVOY, 1)), "a leg behind");
        Quest c = QuestRules.advance(b, new QuestEvent.EscortSeen(CONVOY, 3));
        assertEquals(2, c.progress(), "never more than needed");
    }

    @Test
    void anEscortEndsWithItsConvoy() {
        Quest q = escort(4, 0.5);
        Quest enough = QuestRules.advance(QuestRules.advance(q, new QuestEvent.EscortSeen(CONVOY, 0)), new QuestEvent.EscortSeen(CONVOY, 1));
        assertEquals(QuestState.DONE, QuestRules.advance(enough, new QuestEvent.VoyageEnded(CONVOY, VoyageEnd.ARRIVED)).state());
        Quest few = QuestRules.advance(q, new QuestEvent.EscortSeen(CONVOY, 0));
        assertEquals(QuestState.FAILED, QuestRules.advance(few, new QuestEvent.VoyageEnded(CONVOY, VoyageEnd.ARRIVED)).state(),
                "arrived without the player for long enough");
        for (VoyageEnd lost : List.of(VoyageEnd.SUNK, VoyageEnd.CAPTURED, VoyageEnd.LOST, VoyageEnd.CANCELLED)) {
            assertEquals(QuestState.FAILED, QuestRules.advance(enough, new QuestEvent.VoyageEnded(CONVOY, lost)).state(), lost.name());
        }
        assertSame(enough, QuestRules.advance(enough, new QuestEvent.VoyageEnded(OTHER, VoyageEnd.SUNK)), "another convoy");
        Quest hunt = active(QuestType.HUNT_SHIP, QuestTarget.None.INSTANCE, 1);
        assertSame(hunt, QuestRules.advance(hunt, new QuestEvent.VoyageEnded(CONVOY, VoyageEnd.SUNK)));
        assertSame(hunt, QuestRules.advance(hunt, new QuestEvent.EscortSeen(CONVOY, 0)));
    }

    @Test
    void anEscortTargetSurvivesTheCodec() {
        Quest q = QuestRules.advance(escort(5, 0.5), new QuestEvent.EscortSeen(CONVOY, 1));
        Quest back = Quest.CODEC.parse(JsonOps.INSTANCE, Quest.CODEC.encodeStart(JsonOps.INSTANCE, q).getOrThrow()).getOrThrow();
        assertEquals(q, back);
        Quest offer = QuestGenerator.offer(QuestType.ESCORT, ctx(PortKind.NAVY_OUTPOST, 2, SEA), 1, QuestParams.DEFAULTS).orElseThrow();
        assertEquals(offer, Quest.CODEC.parse(JsonOps.INSTANCE, Quest.CODEC.encodeStart(JsonOps.INSTANCE, offer).getOrThrow()).getOrThrow());
    }

    // ------------------------------------------------------------------ prize money

    @Test
    void prizeMoneyOnlyUnderAValidLetterForPirateShips() {
        assertEquals(60, ShipPrizes.prize(LetterState.ACTIVE, true, true, Faction.PIRATES, true, 60));
        assertEquals(0, ShipPrizes.prize(LetterState.NONE, true, true, Faction.PIRATES, true, 60), "no letter");
        assertEquals(0, ShipPrizes.prize(LetterState.VOIDED, true, true, Faction.PIRATES, true, 60), "voided letter");
        assertEquals(0, ShipPrizes.prize(LetterState.ACTIVE, true, true, Faction.NAVY, true, 60), "a navy ship");
        assertEquals(0, ShipPrizes.prize(LetterState.ACTIVE, true, true, Faction.MERCHANTS, true, 60), "a merchant");
        assertEquals(0, ShipPrizes.prize(LetterState.ACTIVE, true, true, Faction.PIRATES, false, 60), "only plundered");
        assertEquals(0, ShipPrizes.prize(LetterState.ACTIVE, false, true, Faction.PIRATES, true, 60), "careers off");
        assertEquals(0, ShipPrizes.prize(LetterState.ACTIVE, true, false, Faction.PIRATES, true, 60), "letters off");
        assertEquals(0, ShipPrizes.prize(LetterState.ACTIVE, true, true, Faction.PIRATES, true, 0), "prize 0");
    }
}

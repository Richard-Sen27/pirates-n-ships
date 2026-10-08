package com.richardsenger.piratesnships.rpg.quest;

import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The pure quest rules: progress per event, completion, failure, accepting, the cap and the log's codec. */
class QuestRulesTest {

    private static final ResourceLocation PORT = Constants.id("cane_bay");
    private static final ResourceLocation DEST = Constants.id("fort_royal");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Quest offer(QuestType type, QuestTarget target, int needed) {
        return new Quest(UUID.randomUUID(), PORT, PortKind.NAVY_OUTPOST, type, target, needed, 0, 90, Deed.COMPLETE_NAVY_QUEST,
                10, 11, 0, QuestState.OFFERED);
    }

    private static Quest active(QuestType type, QuestTarget target, int needed) {
        return QuestRules.accept(offer(type, target, needed), 10, 5);
    }

    @Test
    void acceptSetsStateAndDeadline() {
        Quest q = active(QuestType.HUNT_PIRATES, QuestTarget.None.INSTANCE, 3);
        assertEquals(QuestState.ACTIVE, q.state());
        assertEquals(15, q.deadlineDay());
        assertEquals(0, q.progress());
    }

    @Test
    void offeredQuestsDoNotMove() {
        Quest q = offer(QuestType.HUNT_PIRATES, QuestTarget.None.INSTANCE, 3);
        assertSame(q, QuestRules.advance(q, new QuestEvent.DeedDone(Deed.KILL_PIRATE)));
        assertSame(q, QuestRules.advance(q, new QuestEvent.Day(100)));
    }

    @Test
    void huntsCountOnlyTheirDeed() {
        Quest q = active(QuestType.HUNT_PIRATES, QuestTarget.None.INSTANCE, 2);
        assertSame(q, QuestRules.advance(q, new QuestEvent.DeedDone(Deed.KILL_NAVY)));
        Quest one = QuestRules.advance(q, new QuestEvent.DeedDone(Deed.KILL_PIRATE));
        assertEquals(1, one.progress());
        assertEquals(QuestState.ACTIVE, one.state());
        Quest two = QuestRules.advance(one, new QuestEvent.DeedDone(Deed.KILL_PIRATE));
        assertEquals(2, two.progress());
        assertEquals(QuestState.DONE, two.state());
        assertSame(two, QuestRules.advance(two, new QuestEvent.DeedDone(Deed.KILL_PIRATE)), "a done quest stays done");

        assertTrue(QuestRules.counts(QuestType.HUNT_NAVY, Deed.KILL_NAVY));
        assertTrue(QuestRules.counts(QuestType.TURN_IN, Deed.TURN_IN_PIRATE));
        assertFalse(QuestRules.counts(QuestType.TURN_IN, Deed.KILL_PIRATE));
        assertFalse(QuestRules.counts(QuestType.KILL_MONSTER, Deed.KILL_PIRATE));
    }

    @Test
    void monstersCountTheirEntityType() {
        Quest q = active(QuestType.KILL_MONSTER, new QuestTarget.Kill(QuestGenerator.SHARK), 2);
        assertSame(q, QuestRules.advance(q, new QuestEvent.Killed(QuestGenerator.KRAKEN)));
        assertEquals(1, QuestRules.advance(q, new QuestEvent.Killed(QuestGenerator.SHARK)).progress());
        Quest kraken = active(QuestType.KILL_MONSTER, new QuestTarget.Kill(QuestGenerator.KRAKEN), 1);
        assertEquals(QuestState.DONE, QuestRules.advance(kraken, new QuestEvent.Killed(QuestGenerator.KRAKEN)).state());
    }

    @Test
    void deliveriesFollowTheirContract() {
        UUID contract = UUID.randomUUID();
        Quest q = active(QuestType.DELIVER, new QuestTarget.Cargo(Constants.id("sugar"), 32, DEST, Optional.of(contract)), 1);
        assertSame(q, QuestRules.advance(q, new QuestEvent.ContractChanged(UUID.randomUUID(), DeliveryContract.State.DELIVERED)), "other contract");
        assertSame(q, QuestRules.advance(q, new QuestEvent.ContractChanged(contract, DeliveryContract.State.ACCEPTED)));
        assertEquals(QuestState.DONE, QuestRules.advance(q, new QuestEvent.ContractChanged(contract, DeliveryContract.State.DELIVERED)).state());
        assertEquals(QuestState.FAILED, QuestRules.advance(q, new QuestEvent.ContractChanged(contract, DeliveryContract.State.FAILED)).state());
        assertEquals(QuestState.FAILED, QuestRules.advance(q, new QuestEvent.ContractChanged(contract, null)).state(), "contract gone");
        assertEquals(0, QuestRules.coinsOnCompletion(q), "the contract pays a delivery");
        assertEquals(90, QuestRules.coinsOnCompletion(active(QuestType.TURN_IN, QuestTarget.None.INSTANCE, 1)));
    }

    @Test
    void treasureNeedsItsSite() {
        BlockPos site = new BlockPos(10, 60, -4);
        Quest q = active(QuestType.FIND_TREASURE, new QuestTarget.Treasure(DEST, site), 1);
        assertSame(q, QuestRules.advance(q, new QuestEvent.TreasureLooted(DEST, site.above())));
        assertSame(q, QuestRules.advance(q, new QuestEvent.TreasureLooted(PORT, site)));
        assertEquals(QuestState.DONE, QuestRules.advance(q, new QuestEvent.TreasureLooted(DEST, site)).state());
    }

    @Test
    void deadlineFailsAfterItsDay() {
        Quest q = active(QuestType.HUNT_PIRATES, QuestTarget.None.INSTANCE, 3);
        assertSame(q, QuestRules.advance(q, new QuestEvent.Day(15)));
        assertEquals(QuestState.FAILED, QuestRules.advance(q, new QuestEvent.Day(16)).state());
        assertEquals(QuestState.DONE, QuestRules.advance(q, new QuestEvent.Complete()).state());
    }

    @Test
    void acceptChecksStateExpiryAndCap() {
        Quest o = offer(QuestType.HUNT_PIRATES, QuestTarget.None.INSTANCE, 3);
        assertEquals(Optional.empty(), QuestRules.canAccept(QuestLog.EMPTY, o, 11, 3));
        assertEquals(Optional.of(QuestRules.EXPIRED), QuestRules.canAccept(QuestLog.EMPTY, o, 12, 3));
        assertEquals(Optional.of(QuestRules.NOT_OFFERED), QuestRules.canAccept(QuestLog.EMPTY, o.withState(QuestState.ACTIVE), 10, 3));
        QuestLog full = QuestLog.EMPTY;
        for (int i = 0; i < 3; i++) full = full.with(active(QuestType.TURN_IN, QuestTarget.None.INSTANCE, 1));
        assertEquals(Optional.of(QuestRules.TOO_MANY), QuestRules.canAccept(full, o, 10, 3));
        assertEquals(Optional.empty(), QuestRules.canAccept(full, o, 10, 4));
    }

    @Test
    void rewardDeedByGiver() {
        assertEquals(Deed.COMPLETE_NAVY_QUEST, QuestRules.rewardDeed(PortKind.NAVY_OUTPOST));
        assertEquals(Deed.COMPLETE_PIRATE_QUEST, QuestRules.rewardDeed(PortKind.PIRATE_ISLAND));
        assertEquals(Deed.COMPLETE_VILLAGE_QUEST, QuestRules.rewardDeed(PortKind.SEAFARER_VILLAGE));
    }

    @Test
    void settleCountsFinishedQuests() {
        Quest a = active(QuestType.HUNT_PIRATES, QuestTarget.None.INSTANCE, 1);
        Quest b = active(QuestType.TURN_IN, QuestTarget.None.INSTANCE, 1);
        Quest c = active(QuestType.KILL_MONSTER, new QuestTarget.Kill(QuestGenerator.SHARK), 3);
        QuestLog log = QuestLog.EMPTY.with(a).with(b).with(c)
                .map(q -> QuestRules.advance(q, new QuestEvent.DeedDone(Deed.KILL_PIRATE)))
                .map(q -> q.type() == QuestType.TURN_IN ? q.withState(QuestState.FAILED) : q)
                .settle();
        assertEquals(List.of(c.id()), log.active().stream().map(Quest::id).toList());
        assertEquals(1, log.completed(QuestType.HUNT_PIRATES));
        assertEquals(1, log.failed());
        assertEquals(Optional.of(c), log.findByPrefix(c.shortId()));
    }

    @Test
    void logSurvivesTheCodec() {
        UUID contract = UUID.randomUUID();
        QuestLog log = new QuestLog(List.of(
                active(QuestType.HUNT_PIRATES, QuestTarget.None.INSTANCE, 4).withProgress(2),
                active(QuestType.KILL_MONSTER, new QuestTarget.Kill(QuestGenerator.KRAKEN), 1),
                active(QuestType.DELIVER, new QuestTarget.Cargo(Constants.id("rum"), 16, DEST, Optional.of(contract)), 1),
                active(QuestType.FIND_TREASURE, new QuestTarget.Treasure(DEST, new BlockPos(1, 2, 3)), 1),
                active(QuestType.HUNT_CAPTAIN, new QuestTarget.Victim(UUID.randomUUID(), "Black-Tooth Kidd"), 1)),
                Map.of(QuestType.TURN_IN, 3, QuestType.DELIVER, 1), 2);
        var json = QuestLog.CODEC.encodeStart(JsonOps.INSTANCE, log).getOrThrow();
        QuestLog back = QuestLog.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(log, back);
        assertEquals(QuestLog.EMPTY, QuestLog.CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow());
    }
}

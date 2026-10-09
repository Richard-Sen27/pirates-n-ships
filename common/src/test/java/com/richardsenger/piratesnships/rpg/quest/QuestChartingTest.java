package com.richardsenger.piratesnships.rpg.quest;

import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** QST2b: an escort accepted before its lane is charted waits in CHARTING, then sails, or is called off. */
class QuestChartingTest {

    private static final ResourceLocation PORT = Constants.id("cane_bay");
    private static final ResourceLocation DEST = Constants.id("fort_royal");
    private static final UUID CONVOY = UUID.randomUUID();
    private static final long T = QuestRules.DEFAULT_CHART_TIMEOUT_TICKS;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Quest accepted() {
        Quest offer = new Quest(UUID.randomUUID(), PORT, PortKind.SEAFARER_VILLAGE, QuestType.ESCORT, new QuestTarget.Escort(DEST), 1, 0,
                150, Deed.COMPLETE_VILLAGE_QUEST, 10, 11, 0, QuestState.OFFERED);
        return QuestRules.accept(offer, 10, 5);
    }

    @Test
    void chartingAnEscortWaitsWithoutAConvoy() {
        Quest q = QuestRules.chart(accepted(), 1000);
        assertEquals(QuestState.CHARTING, q.state());
        assertTrue(QuestRules.charting(q));
        QuestTarget.Escort t = assertInstanceOf(QuestTarget.Escort.class, q.target());
        assertEquals(1000L, t.chartingSince().orElseThrow());
        assertTrue(t.voyage().isEmpty());
        assertEquals("", t.name());
        assertFalse(QuestRules.charting(accepted()), "an active escort is not charting");
    }

    @Test
    void chartingLeavesOtherQuestsAlone() {
        Quest hunt = new Quest(UUID.randomUUID(), PORT, PortKind.NAVY_OUTPOST, QuestType.HUNT_PIRATES, QuestTarget.None.INSTANCE, 3, 0, 90,
                Deed.COMPLETE_NAVY_QUEST, 10, 11, 15, QuestState.ACTIVE);
        assertSame(hunt, QuestRules.chart(hunt, 5));
    }

    @Test
    void aChartingEscortHearsNoEvent() {
        Quest q = QuestRules.chart(accepted(), 0);
        assertSame(q, QuestRules.advance(q, new QuestEvent.Day(10_000)), "no deadline while charting");
        assertSame(q, QuestRules.advance(q, new QuestEvent.VoyageEnded(CONVOY, VoyageEnd.SUNK)));
        assertSame(q, QuestRules.advance(q, new QuestEvent.EscortSeen(CONVOY, 0)));
        assertFalse(q.state().finished(), "a charting quest stays in the log");
    }

    @Test
    void bindingAChartingEscortMakesItActive() {
        Quest q = QuestRules.bindEscort(QuestRules.chart(accepted(), 40), CONVOY, "Merry Gull", 4, 0.5);
        assertEquals(QuestState.ACTIVE, q.state());
        QuestTarget.Escort t = (QuestTarget.Escort) q.target();
        assertTrue(t.chartingSince().isEmpty(), "charting mark cleared");
        assertTrue(t.follows(CONVOY));
        assertEquals(2, q.needed());
        assertFalse(QuestRules.charting(q));
    }

    @Test
    void theStepRule() {
        assertEquals(QuestRules.ChartStep.WAIT, QuestRules.chartStep(false, false, 100, 100, T));
        assertEquals(QuestRules.ChartStep.WAIT, QuestRules.chartStep(false, false, 100, 100 + T - 1, T));
        assertEquals(QuestRules.ChartStep.SAIL, QuestRules.chartStep(true, false, 100, 120, T));
        assertEquals(QuestRules.ChartStep.CANCEL, QuestRules.chartStep(false, true, 100, 120, T), "the lane failed");
    }

    @Test
    void theTimeoutCancels() {
        assertEquals(QuestRules.ChartStep.CANCEL, QuestRules.chartStep(false, false, 100, 100 + T, T), "exactly at the timeout");
        assertEquals(QuestRules.ChartStep.CANCEL, QuestRules.chartStep(false, false, 100, 100 + 10 * T, T));
        assertEquals(QuestRules.ChartStep.SAIL, QuestRules.chartStep(true, false, 100, 100 + 10 * T, T), "a ready lane still sails");
        assertEquals(QuestRules.ChartStep.CANCEL, QuestRules.chartStep(false, false, 100, 100, 0), "no wait at all");
        assertEquals(2400, T, "default escort_chart_timeout_ticks");
    }

    @Test
    void aChartingEscortSurvivesTheCodec() {
        Quest q = QuestRules.chart(accepted(), 1234);
        Quest back = Quest.CODEC.parse(JsonOps.INSTANCE, Quest.CODEC.encodeStart(JsonOps.INSTANCE, q).getOrThrow()).getOrThrow();
        assertEquals(q, back);
        Quest bound = QuestRules.bindEscort(q, CONVOY, "Merry Gull", 2, 0.5);
        assertEquals(bound, Quest.CODEC.parse(JsonOps.INSTANCE, Quest.CODEC.encodeStart(JsonOps.INSTANCE, bound).getOrThrow()).getOrThrow());
    }
}

package com.richardsenger.piratesnships.combat.boarding.order;

import com.richardsenger.piratesnships.combat.boarding.order.BoardingRules.Label;
import com.richardsenger.piratesnships.combat.boarding.order.BoardingRules.NpcStep;
import com.richardsenger.piratesnships.combat.boarding.order.BoardingRules.NpcView;
import com.richardsenger.piratesnships.combat.boarding.order.BoardingRules.Post;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure rules of the "Board!" order (BRD3): who goes, the toggle's label, capture, the NPC decision and recall. */
class BoardingRulesTest {

    @Test
    void gunnersAndTheHelmsmanStayUnlessEveryHandGoes() {
        assertTrue(BoardingRules.boards(Post.NONE, true));
        assertTrue(BoardingRules.boards(Post.OTHER, true));
        assertFalse(BoardingRules.boards(Post.GUN, true));
        assertFalse(BoardingRules.boards(Post.HELM, true));
        assertTrue(BoardingRules.boards(Post.GUN, false));
        assertTrue(BoardingRules.boards(Post.HELM, false));
    }

    @Test
    void theEntryReadsBoardOrBackAboard() {
        assertEquals(Label.BOARD, BoardingRules.label(false));
        assertEquals(Label.BACK_ABOARD, BoardingRules.label(true));
    }

    @Test
    void aDeckHeldClearForTheCaptureTimeIsTaken() {
        int t = BoardingRules.clearTicks(0, 0, 2, 10);
        assertEquals(10, t);
        t = BoardingRules.clearTicks(t, 0, 2, 10);
        assertEquals(20, t);
        assertTrue(BoardingRules.captured(t, 1));
        assertFalse(BoardingRules.captured(t, 2));
        assertEquals(0, BoardingRules.clearTicks(t, 1, 2, 10), "a foe aboard stops the clock");
        assertEquals(0, BoardingRules.clearTicks(t, 0, 0, 10), "nobody of ours aboard holds nothing");
    }

    private static NpcView view(boolean guns, boolean faster, boolean larger, int fighters, int defenders) {
        return new NpcView(true, true, guns, faster, larger, fighters, defenders);
    }

    @Test
    void theNpcCaptainBoardsAfterTheDelayWhenHisGunsFiredOrHeIsStronger() {
        assertEquals(NpcStep.WAIT, BoardingRules.npcStep(view(true, false, false, 3, 1), 100, 160, 2.0), "before the delay");
        assertEquals(NpcStep.BOARD, BoardingRules.npcStep(view(true, false, false, 3, 1), 160, 160, 2.0));
        assertEquals(NpcStep.BOARD, BoardingRules.npcStep(view(false, true, false, 3, 1), 160, 160, 2.0), "faster");
        assertEquals(NpcStep.BOARD, BoardingRules.npcStep(view(false, false, true, 3, 1), 160, 160, 2.0), "larger");
        assertEquals(NpcStep.WAIT, BoardingRules.npcStep(view(false, false, false, 3, 1), 999, 160, 2.0),
                "no shot fired, not faster, not larger: he waits");
    }

    @Test
    void theNpcCaptainNeverBoardsAFriendOrWithoutAWay() {
        assertEquals(NpcStep.NONE, BoardingRules.npcStep(new NpcView(false, true, true, true, true, 3, 0), 999, 0, 2.0));
        assertEquals(NpcStep.NONE, BoardingRules.npcStep(new NpcView(true, false, true, true, true, 3, 0), 999, 0, 2.0));
    }

    @Test
    void theNpcCaptainWaitsWhenOutnumberedOrWithoutFighters() {
        assertEquals(NpcStep.WAIT, BoardingRules.npcStep(view(true, false, false, 2, 4), 999, 0, 2.0), "4 against 2");
        assertEquals(NpcStep.BOARD, BoardingRules.npcStep(view(true, false, false, 2, 3), 999, 0, 2.0), "3 against 2");
        assertEquals(NpcStep.WAIT, BoardingRules.npcStep(view(true, false, false, 0, 0), 999, 0, 2.0), "nobody to send");
    }

    @Test
    void theNpcCaptainCallsHisBoardersBackWhenSinkingOrOutnumbered() {
        assertFalse(BoardingRules.npcRecalls(3, 2, 0.0, 0.3, 2.0));
        assertTrue(BoardingRules.npcRecalls(3, 2, 0.35, 0.3, 2.0), "his ship floods");
        assertTrue(BoardingRules.npcRecalls(1, 2, 0.0, 0.3, 2.0), "two defenders per boarder");
        assertTrue(BoardingRules.npcRecalls(0, 0, 0.0, 0.3, 2.0), "nobody left");
    }

    @Test
    void boardersHeadHomeWhenCalledOrWhenTheDeckIsTaken() {
        assertFalse(BoardingRules.headsHome(false, false));
        assertTrue(BoardingRules.headsHome(true, false));
        assertTrue(BoardingRules.headsHome(false, true));
    }
}

package com.richardsenger.piratesnships.rpg.career.client;

import com.richardsenger.piratesnships.rpg.career.CareerTitles;
import com.richardsenger.piratesnships.rpg.career.InfamyRank;
import com.richardsenger.piratesnships.rpg.career.LetterState;
import com.richardsenger.piratesnships.rpg.career.NavyRank;
import com.richardsenger.piratesnships.rpg.career.client.RankHudLayout.Rect;
import com.richardsenger.piratesnships.rpg.career.client.RankHudLayout.Row;
import com.richardsenger.piratesnships.rpg.career.client.RankHudLayout.View;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** HON1: the rank box's rows, title, colours and placement. */
class RankHudLayoutTest {

    private static final View DECKHAND = new View(NavyRank.NONE, false, InfamyRank.DECKHAND, LetterState.NONE, 0, true);

    @Test
    void rowsFollowWhatIsKnown() {
        assertEquals(List.of(Row.TITLE, Row.REPUTATION), RankHudLayout.rows(DECKHAND));
        assertEquals(List.of(Row.TITLE), RankHudLayout.rows(new View(NavyRank.NONE, false, InfamyRank.DECKHAND, LetterState.NONE, 0, false)),
                "no reputation row before the server sent it");
        assertEquals(List.of(Row.TITLE, Row.REPUTATION, Row.LETTER, Row.PRIZE),
                RankHudLayout.rows(new View(NavyRank.NONE, false, InfamyRank.DECKHAND, LetterState.ACTIVE, 40, true)));
        assertEquals(List.of(Row.TITLE, Row.REPUTATION, Row.LETTER),
                RankHudLayout.rows(new View(NavyRank.NONE, false, InfamyRank.DECKHAND, LetterState.VOIDED, 0, true)), "a void letter shows");
    }

    @Test
    void titleRowNamesTheRank() {
        assertEquals(NavyRank.CAPTAIN.nameKey(),
                RankHudLayout.titleKey(new View(NavyRank.CAPTAIN, true, InfamyRank.DECKHAND, LetterState.NONE, 0, true)));
        assertEquals(CareerTitles.Title.PRIVATEER.key(),
                RankHudLayout.titleKey(new View(NavyRank.NONE, false, InfamyRank.BUCCANEER, LetterState.ACTIVE, 0, true)));
        assertEquals(InfamyRank.DREAD_CAPTAIN.nameKey(),
                RankHudLayout.titleKey(new View(NavyRank.NONE, false, InfamyRank.DREAD_CAPTAIN, LetterState.NONE, 0, true)));
        assertEquals(InfamyRank.DECKHAND.nameKey(), RankHudLayout.titleKey(DECKHAND));
    }

    @Test
    void titleColourBySide() {
        assertEquals(RankHudLayout.COLOUR_NAVY,
                RankHudLayout.titleColour(new View(NavyRank.MIDSHIPMAN, true, InfamyRank.DECKHAND, LetterState.NONE, 0, true)));
        assertEquals(RankHudLayout.COLOUR_PRIVATEER,
                RankHudLayout.titleColour(new View(NavyRank.NONE, false, InfamyRank.DECKHAND, LetterState.ACTIVE, 0, true)));
        assertEquals(RankHudLayout.COLOUR_PIRATE,
                RankHudLayout.titleColour(new View(NavyRank.NONE, false, InfamyRank.PIRATE_LORD, LetterState.NONE, 0, true)));
        assertEquals(RankHudLayout.COLOUR_PLAIN, RankHudLayout.titleColour(DECKHAND));
    }

    @Test
    void sizeGrowsWithTextAndRows() {
        assertEquals(50 + 2 * RankHudLayout.PAD, RankHudLayout.width(50));
        assertEquals(2 * RankHudLayout.LINE_H - 1 + 2 * RankHudLayout.PAD, RankHudLayout.height(2));
        assertEquals(RankHudLayout.height(1), RankHudLayout.height(0), "never smaller than one row");
    }

    @Test
    void placeFromEachEdge() {
        assertEquals(new Rect(4, 4, 60, 30), RankHudLayout.place(4, 4, 60, 30, 400, 300));
        assertEquals(new Rect(400 - 4 - 60, 4, 60, 30), RankHudLayout.place(-4, 4, 60, 30, 400, 300), "negative x from the right");
        assertEquals(new Rect(4, 300 - 10 - 30, 60, 30), RankHudLayout.place(4, -10, 60, 30, 400, 300), "negative y from the bottom");
    }

    @Test
    void placeKeepsTheBoxOnScreen() {
        assertEquals(new Rect(340, 270, 60, 30), RankHudLayout.place(1000, 1000, 60, 30, 400, 300));
        assertEquals(new Rect(0, 0, 60, 30), RankHudLayout.place(-1000, -1000, 60, 30, 400, 300));
        assertEquals(new Rect(0, 4, 500, 30), RankHudLayout.place(-4, 4, 500, 30, 400, 300), "too wide: the left edge stays visible");
    }

    @Test
    void signedReputation() {
        assertEquals("+25", RankHudLayout.signed(25));
        assertEquals("-10", RankHudLayout.signed(-10));
        assertEquals("0", RankHudLayout.signed(0));
    }
}

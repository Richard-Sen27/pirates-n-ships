package com.richardsenger.piratesnships.rpg.career.client;

import com.richardsenger.piratesnships.rpg.career.CareerTitles;
import com.richardsenger.piratesnships.rpg.career.InfamyRank;
import com.richardsenger.piratesnships.rpg.career.LetterState;
import com.richardsenger.piratesnships.rpg.career.NavyRank;
import com.richardsenger.piratesnships.rpg.career.client.RankHudLayout.Row;
import com.richardsenger.piratesnships.rpg.career.client.RankHudLayout.View;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudLayout;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudLayout.Corner;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudLayout.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** HON1: the rank box's rows, title and colours; HUD5: its corner placement. */
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

    private static final int GW = 640, GH = 360, M = ShipHudLayout.DEFAULT_MARGIN;

    @Test
    void placeInEachCornerByTheMargin() {
        assertEquals(new Rect(M, M, 60, 30), RankHudLayout.place(Corner.TOP_LEFT, 60, 30, GW, GH, List.of(), M));
        assertEquals(new Rect(GW - M - 60, M, 60, 30), RankHudLayout.place(Corner.TOP_RIGHT, 60, 30, GW, GH, List.of(), M));
        assertEquals(new Rect(M, GH - M - 30, 60, 30), RankHudLayout.place(Corner.BOTTOM_LEFT, 60, 30, GW, GH, List.of(), M));
        assertEquals(new Rect(GW - M - 60, GH - M - 30, 60, 30),
                RankHudLayout.place(Corner.BOTTOM_RIGHT, 60, 30, GW, GH, List.of(), M));
        assertEquals(new Rect(2, 2, 60, 30), RankHudLayout.place(Corner.TOP_LEFT, 60, 30, GW, GH, List.of(), 2), "the margin is the ship HUD's");
    }

    @Test
    void placeKeepsClearOfTheEffectIcons() {
        assertEquals(new Rect(GW - M - 60, 26 + ShipHudLayout.GAP, 60, 30),
                RankHudLayout.place(Corner.TOP_RIGHT, 60, 30, GW, GH, ShipHudLayout.effects(GW, 26), M));
    }

    @Test
    void placeKeepsClearOfTheShipHudPanelsInItsCorner() {
        List<Rect> statics = ShipHudLayout.hotbar(GW, GH, true, 0);
        ShipHudLayout.Placement ship = ShipHudLayout.place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT,
                new ShipHudLayout.Screen(GW, GH, statics), 1.0, M);
        List<Rect> occupied = new ArrayList<>(statics);
        occupied.add(ship.compass().rect());
        occupied.add(ship.hull().rect());

        Rect right = RankHudLayout.place(Corner.BOTTOM_RIGHT, 60, 30, GW, GH, occupied, M);
        assertEquals(new Rect(GW - M - 60, ship.hull().rect().y() - ShipHudLayout.GAP - 30, 60, 30), right,
                "above the hull strip, right-aligned with the margin");
        Rect left = RankHudLayout.place(Corner.BOTTOM_LEFT, 60, 30, GW, GH, occupied, M);
        assertEquals(ship.compass().rect().y() - ShipHudLayout.GAP - 30, left.y(), "above the compass panel");
        for (Rect o : occupied) {
            assertFalse(right.intersects(o), "bottom right overlaps " + o);
            assertFalse(left.intersects(o), "bottom left overlaps " + o);
        }
        assertEquals(new Rect(M, M, 60, 30), RankHudLayout.place(Corner.TOP_LEFT, 60, 30, GW, GH, occupied, M),
                "panels in other corners leave the top left alone");
    }

    @Test
    void placeWithoutRoomKeepsTheCornerOnScreen() {
        List<Rect> wall = List.of(new Rect(0, 0, GW, GH));
        assertEquals(new Rect(M, M, 60, 30), RankHudLayout.place(Corner.TOP_LEFT, 60, 30, GW, GH, wall, M));
        assertEquals(new Rect(0, M, 700, 30), RankHudLayout.place(Corner.TOP_RIGHT, 700, 30, GW, GH, List.of(), M),
                "too wide: the left edge stays visible");
    }

    @Test
    void signedReputation() {
        assertEquals("+25", RankHudLayout.signed(25));
        assertEquals("-10", RankHudLayout.signed(-10));
        assertEquals("0", RankHudLayout.signed(0));
    }
}

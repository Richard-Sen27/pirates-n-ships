package com.richardsenger.piratesnships.rpg.career.client;

import com.richardsenger.piratesnships.rpg.career.CareerTitles;
import com.richardsenger.piratesnships.rpg.career.InfamyRank;
import com.richardsenger.piratesnships.rpg.career.LetterState;
import com.richardsenger.piratesnships.rpg.career.NavyRank;
import com.richardsenger.piratesnships.rpg.career.client.RankHudLayout.View;
import com.richardsenger.piratesnships.rpg.career.client.RankHudVisibility.Change;
import com.richardsenger.piratesnships.rpg.career.client.RankHudVisibility.Mode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.richardsenger.piratesnships.rpg.career.client.RankHudVisibility.FADE_TICKS;
import static org.junit.jupiter.api.Assertions.*;

/** HUD5: when the rank box shows, for how long, how strongly, and what change it names. */
class RankHudVisibilityTest {

    private static final View DECKHAND = new View(NavyRank.NONE, false, InfamyRank.DECKHAND, LetterState.NONE, 0, true);
    private static final View BUCCANEER = new View(NavyRank.NONE, false, InfamyRank.BUCCANEER, LetterState.NONE, 0, true);
    private static final int WINDOW = RankHudVisibility.showTicks(RankHudVisibility.DEFAULT_SHOW_SECONDS);

    private static View navy(NavyRank rank) {
        return new View(rank, true, InfamyRank.DECKHAND, LetterState.NONE, 0, true);
    }

    @Test
    void showSecondsAreTicksInRange() {
        assertEquals(100, WINDOW, "5 s by default");
        assertEquals(20, RankHudVisibility.showTicks(0), "at least a second");
        assertEquals(600, RankHudVisibility.showTicks(99), "at most 30 s");
    }

    @Test
    void onChangeFadesInHoldsAndFadesOut() {
        assertEquals(0f, RankHudVisibility.alpha(Mode.ON_CHANGE, Double.NaN, WINDOW), "never shown: hidden");
        assertEquals(0f, RankHudVisibility.alpha(Mode.ON_CHANGE, 0, WINDOW), 1e-6, "starts transparent");
        assertEquals(0.5f, RankHudVisibility.alpha(Mode.ON_CHANGE, FADE_TICKS / 2.0, WINDOW), 1e-6, "half way in");
        assertEquals(1f, RankHudVisibility.alpha(Mode.ON_CHANGE, FADE_TICKS, WINDOW), 1e-6, "fully in after the fade");
        assertEquals(1f, RankHudVisibility.alpha(Mode.ON_CHANGE, WINDOW / 2.0, WINDOW), 1e-6, "held");
        assertEquals(1f, RankHudVisibility.alpha(Mode.ON_CHANGE, WINDOW - FADE_TICKS, WINDOW), 1e-6, "fade out starts");
        assertEquals(0.5f, RankHudVisibility.alpha(Mode.ON_CHANGE, WINDOW - FADE_TICKS / 2.0, WINDOW), 1e-6, "half way out");
        assertEquals(0f, RankHudVisibility.alpha(Mode.ON_CHANGE, WINDOW, WINDOW), "gone at the end of the window");
        assertEquals(0f, RankHudVisibility.alpha(Mode.ON_CHANGE, 10_000, WINDOW), "stays gone");
    }

    @Test
    void aShortWindowSharesItselfBetweenTheFades() {
        int one = RankHudVisibility.showTicks(1);
        assertEquals(1f, RankHudVisibility.alpha(Mode.ON_CHANGE, one / 2.0, one), 1e-6, "full only at the middle");
        assertTrue(RankHudVisibility.alpha(Mode.ON_CHANGE, 5, one) < 1f);
    }

    @Test
    void alwaysAndOffIgnoreTheWindow() {
        assertEquals(1f, RankHudVisibility.alpha(Mode.ALWAYS, Double.NaN, WINDOW));
        assertEquals(1f, RankHudVisibility.alpha(Mode.ALWAYS, 10_000, WINDOW));
        assertEquals(0f, RankHudVisibility.alpha(Mode.OFF, WINDOW / 2.0, WINDOW));
    }

    @Test
    void sameTitleIsNoChange() {
        assertEquals(Optional.empty(), RankHudVisibility.change(DECKHAND, DECKHAND));
        View prize = new View(NavyRank.LIEUTENANT, true, InfamyRank.DECKHAND, LetterState.NONE, 50, true);
        assertEquals(Optional.empty(), RankHudVisibility.change(navy(NavyRank.LIEUTENANT), prize), "prize money is not a rank");
    }

    @Test
    void aHigherNavyRankInServiceIsAPromotion() {
        assertEquals(Optional.of(new Change(true, NavyRank.LIEUTENANT.nameKey())),
                RankHudVisibility.change(navy(NavyRank.MIDSHIPMAN), navy(NavyRank.LIEUTENANT)));
    }

    @Test
    void otherChangesAreNow() {
        assertEquals(Optional.of(new Change(false, InfamyRank.BUCCANEER.nameKey())),
                RankHudVisibility.change(DECKHAND, BUCCANEER), "infamy");
        assertEquals(Optional.of(new Change(false, NavyRank.MIDSHIPMAN.nameKey())),
                RankHudVisibility.change(DECKHAND, navy(NavyRank.MIDSHIPMAN)), "enlisting");
        assertEquals(Optional.of(new Change(false, NavyRank.MIDSHIPMAN.nameKey())),
                RankHudVisibility.change(navy(NavyRank.CAPTAIN), navy(NavyRank.MIDSHIPMAN)), "demotion");
        assertEquals(Optional.of(new Change(false, InfamyRank.BUCCANEER.nameKey())),
                RankHudVisibility.change(navy(NavyRank.LIEUTENANT),
                        new View(NavyRank.NONE, false, InfamyRank.BUCCANEER, LetterState.NONE, 0, true)), "deserting");
        assertEquals(Optional.of(new Change(false, CareerTitles.Title.PRIVATEER.key())),
                RankHudVisibility.change(DECKHAND, new View(NavyRank.NONE, false, InfamyRank.DECKHAND, LetterState.ACTIVE, 0, true)),
                "a letter of marque");
    }

    @Test
    void stateNamesTheChangeForHalfTheWindowThenSettles() {
        RankHudVisibility.State s = new RankHudVisibility.State();
        assertTrue(Double.isNaN(s.age(5, 0f)), "never shown");
        Change promoted = new Change(true, NavyRank.LIEUTENANT.nameKey());
        s.show(100, promoted);
        assertEquals(10.5, s.age(110, 0.5f), 1e-9);
        assertEquals(Optional.of(promoted), s.headlineChange(100, WINDOW));
        assertEquals(Optional.of(promoted), s.headlineChange(100 + WINDOW / 2 - 1, WINDOW));
        assertEquals(Optional.empty(), s.headlineChange(100 + WINDOW / 2, WINDOW), "settles on the rank line");
        assertEquals(Optional.of(promoted), s.windowChange(100 + WINDOW - 1, WINDOW), "still the same window");
        assertEquals(Optional.empty(), s.windowChange(100 + WINDOW, WINDOW));

        s.show(300, null);
        assertEquals(Optional.empty(), s.headlineChange(300, WINDOW), "join and screen close name nothing");
        assertEquals(0.0, s.age(300, 0f));
        s.reset();
        assertTrue(Double.isNaN(s.age(400, 0f)), "a disconnect forgets the window");
    }
}

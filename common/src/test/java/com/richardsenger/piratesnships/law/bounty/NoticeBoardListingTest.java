package com.richardsenger.piratesnships.law.bounty;

import com.richardsenger.piratesnships.law.bounty.NoticeBoardListing.AgeUnit;
import com.richardsenger.piratesnships.law.bounty.NoticeBoardListing.Line;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NoticeBoardListingTest {

    static final BountyRules RULES = BountyRules.defaults().withPlayerBounties(true, 10, 1000L);
    static final BountyTarget ANNE = BountyTarget.player(new UUID(1, 1), "Anne");
    static final BountyTarget BART = BountyTarget.npc(new UUID(1, 2), "Bart");
    static final BountyTarget CORA = BountyTarget.player(new UUID(1, 3), "cora");
    static final UUID PAYER = new UUID(2, 1);

    static int ids = 0;

    static BountyBoard place(BountyBoard board, BountyTarget target, int amount, long at) {
        var r = board.placePlayerBounty(new UUID(9, ids++), PAYER, "Mary", target, amount, at, RULES);
        assertTrue(r.placed());
        return r.board();
    }

    @Test
    void targetsByTotalThenNavyFirstThenAmountThenAge() {
        BountyBoard board = BountyBoard.EMPTY;
        board = place(board, BART, 30, 5);
        board = place(board, ANNE, 20, 1);
        board = place(board, ANNE, 20, 0);
        board = place(board, ANNE, 25, 3);
        board = board.syncNavy(ANNE, 50, 2, RULES).board(); // navy 100 on Anne
        board = place(board, CORA, 30, 4);

        List<Line> lines = NoticeBoardListing.lines(board, 10);
        assertEquals(List.of("Anne", "Anne", "Anne", "Anne", "Bart", "cora"), lines.stream().map(Line::targetName).toList());
        // Anne: navy first, then 25, then the two 20s, older first
        assertTrue(lines.get(0).navy());
        assertEquals("", lines.get(0).placedBy());
        assertEquals(100, lines.get(0).amount());
        assertEquals(25, lines.get(1).amount());
        assertEquals(0, lines.get(2).createdAt());
        assertEquals(1, lines.get(3).createdAt());
        assertEquals(165, lines.get(0).targetTotal());
        assertEquals("Mary", lines.get(1).placedBy());
        // Bart and cora tie at 30: by name
        assertFalse(lines.get(4).targetIsPlayer());
        assertTrue(lines.get(5).targetIsPlayer());
    }

    @Test
    void expiredBountiesAreNotListed() {
        BountyBoard board = place(BountyBoard.EMPTY, ANNE, 20, 0); // expires at 1000
        assertEquals(1, NoticeBoardListing.lines(board, 999).size());
        assertTrue(NoticeBoardListing.lines(board, 1000).isEmpty());
        assertTrue(NoticeBoardListing.lines(BountyBoard.EMPTY, 0).isEmpty());
    }

    @Test
    void namesMergeOnlinePlayersAndTargetsWithoutTheViewer() {
        BountyBoard board = place(place(BountyBoard.EMPTY, BART, 30, 0), CORA, 30, 0);
        List<String> names = NoticeBoardListing.names(List.of("Zed", "Cora", "me"), board, 1, "ME");
        assertEquals(List.of("Bart", "Cora", "Zed"), names, "online spelling wins, sorted ignoring case, viewer left out");
    }

    @Test
    void knownTargetMatchesIgnoringCase() {
        BountyBoard board = place(BountyBoard.EMPTY, BART, 30, 0);
        assertEquals(BART, NoticeBoardListing.knownTarget(board, " bart ", 1).orElseThrow());
        assertTrue(NoticeBoardListing.knownTarget(board, "Bert", 1).isEmpty());
        assertTrue(NoticeBoardListing.knownTarget(board, "", 1).isEmpty());
    }

    @Test
    void agesInTheCoarsestUnit() {
        assertEquals(AgeUnit.JUST_NOW, NoticeBoardListing.age(1199).unit());
        assertEquals(new NoticeBoardListing.Age(AgeUnit.MINUTES, 1), NoticeBoardListing.age(1200));
        assertEquals(new NoticeBoardListing.Age(AgeUnit.HOURS, 2), NoticeBoardListing.age(20L * 60 * 60 * 2 + 5));
        assertEquals(new NoticeBoardListing.Age(AgeUnit.DAYS, 3), NoticeBoardListing.age(20L * 60 * 60 * 24 * 3));
        assertEquals(AgeUnit.JUST_NOW, NoticeBoardListing.age(-50).unit());
    }
}

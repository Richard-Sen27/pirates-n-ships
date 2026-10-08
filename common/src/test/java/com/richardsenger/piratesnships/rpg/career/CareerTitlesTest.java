package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.rpg.career.CareerTitles.Title;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** HON1: which title a career earns and how it goes in front of a ship's name. */
class CareerTitlesTest {

    private static Optional<Title> navy(NavyRank r) {
        return CareerTitles.of(r, true, InfamyRank.DECKHAND, LetterState.NONE);
    }

    private static Optional<Title> pirate(InfamyRank r, LetterState letter) {
        return CareerTitles.of(NavyRank.NONE, false, r, letter);
    }

    @Test
    void navyRanksGiveTheirAbbreviations() {
        assertEquals("Mid.", navy(NavyRank.MIDSHIPMAN).orElseThrow().text());
        assertEquals("Lt.", navy(NavyRank.LIEUTENANT).orElseThrow().text());
        assertEquals("Capt.", navy(NavyRank.CAPTAIN).orElseThrow().text());
        assertEquals("Cdre.", navy(NavyRank.COMMODORE).orElseThrow().text());
        assertEquals("Adm.", navy(NavyRank.ADMIRAL).orElseThrow().text());
    }

    @Test
    void infamyGivesPirateTitles() {
        assertEquals(Optional.empty(), pirate(InfamyRank.DECKHAND, LetterState.NONE), "a plain deckhand has no title");
        assertEquals("Buccaneer", pirate(InfamyRank.BUCCANEER, LetterState.NONE).orElseThrow().text());
        assertEquals("Dread Pirate", pirate(InfamyRank.DREAD_CAPTAIN, LetterState.NONE).orElseThrow().text());
        assertEquals("Pirate Lord", pirate(InfamyRank.PIRATE_LORD, LetterState.NONE).orElseThrow().text());
    }

    @Test
    void letterHolderIsAPrivateer() {
        assertEquals(Title.PRIVATEER, pirate(InfamyRank.DECKHAND, LetterState.ACTIVE).orElseThrow());
        assertEquals(Title.PRIVATEER, pirate(InfamyRank.BUCCANEER, LetterState.ACTIVE).orElseThrow(), "the letter ranks above infamy");
        assertEquals(Optional.empty(), pirate(InfamyRank.DECKHAND, LetterState.VOIDED), "a void letter is no title");
    }

    @Test
    void navyRankWinsOverEverythingElse() {
        assertEquals(Title.CAPTAIN, CareerTitles.of(NavyRank.CAPTAIN, true, InfamyRank.PIRATE_LORD, LetterState.ACTIVE).orElseThrow());
        assertEquals(Optional.empty(), CareerTitles.of(NavyRank.CAPTAIN, false, InfamyRank.DECKHAND, LetterState.NONE),
                "a rank out of service is no title");
    }

    @Test
    void recordOverloadAgrees() {
        CareerRecord r = CareerRecord.EMPTY.withNavy(NavyRank.LIEUTENANT);
        assertEquals(Optional.of(Title.LIEUTENANT), CareerTitles.of(r));
        assertEquals(Optional.empty(), CareerTitles.of(CareerRecord.EMPTY));
    }

    @Test
    void idsAndKeysAreUnique() {
        Set<String> ids = new HashSet<>();
        for (Title t : Title.values()) {
            assertTrue(ids.add(t.id()), t.id());
            assertEquals(Optional.of(t), CareerTitles.byId(t.id()));
            assertTrue(t.key().endsWith(".career.title." + t.id()));
        }
    }

    @Test
    void shipNameGetsTheTitleInFront() {
        assertEquals("Capt. Black Gull", CareerTitles.shipName(Optional.of(Title.CAPTAIN), "Black Gull"));
        assertEquals("Black Gull", CareerTitles.shipName(Optional.empty(), "Black Gull"));
        assertEquals("", CareerTitles.shipName(Optional.of(Title.CAPTAIN), "  "), "an empty name stays empty");
    }

    @Test
    void renamingReplacesTheOldTitle() {
        assertEquals("Cdre. Black Gull", CareerTitles.shipName(Optional.of(Title.COMMODORE), "Capt. Black Gull"));
        assertEquals("Black Gull", CareerTitles.shipName(Optional.empty(), "Lt. Black Gull"), "a typed title the namer lacks is removed");
        assertEquals("Pirate Lord Black Gull", CareerTitles.shipName(Optional.of(Title.PIRATE_LORD), "Dread Pirate Capt. Black Gull"));
    }

    @Test
    void stripOnlyRemovesWholeTitles() {
        assertEquals("Ltd. Trading", CareerTitles.stripTitle("Ltd. Trading"));
        assertEquals("Captain Hook", CareerTitles.stripTitle("Captain Hook"));
        assertEquals("Capt.", CareerTitles.stripTitle("Capt."), "a title alone stays (nothing to name)");
        assertEquals("Sea Wolf", CareerTitles.stripTitle("capt. Sea Wolf"), "case-insensitive");
        assertEquals("Buccaneers Bay", CareerTitles.stripTitle("Buccaneers Bay"));
    }
}

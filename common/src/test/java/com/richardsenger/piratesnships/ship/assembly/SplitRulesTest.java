package com.richardsenger.piratesnships.ship.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SplitRulesTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static SplitRules.Piece piece(UUID id, int blocks, int helms, boolean original) {
        return new SplitRules.Piece(id, blocks, helms, original);
    }

    @Test
    void helmWinsOverSize() {
        assertEquals(B, SplitRules.keeper(List.of(piece(A, 100, 0, true), piece(B, 5, 1, false))));
        assertEquals(A, SplitRules.keeper(List.of(piece(A, 5, 1, true), piece(B, 100, 0, false))));
    }

    @Test
    void withoutHelmTheLargerKeeps() {
        assertEquals(B, SplitRules.keeper(List.of(piece(A, 10, 0, true), piece(B, 11, 0, false))));
        assertEquals(A, SplitRules.keeper(List.of(piece(A, 12, 0, true), piece(B, 11, 0, false), piece(C, 3, 0, false))));
    }

    @Test
    void twoHelmsTheLargerOfThemKeeps() {
        assertEquals(C, SplitRules.keeper(List.of(piece(A, 50, 0, true), piece(B, 10, 1, false), piece(C, 20, 1, false))));
    }

    @Test
    void tieGoesToThePieceThatKeptTheOriginalId() {
        // Sable leaves one part in the original sub-level; on a tie that part keeps the identity (no id move)
        assertEquals(A, SplitRules.keeper(List.of(piece(B, 9, 0, false), piece(A, 9, 0, true))));
        assertEquals(A, SplitRules.keeper(List.of(piece(B, 9, 1, false), piece(A, 9, 1, true))));
        // no original among the tied: the first given
        assertEquals(B, SplitRules.keeper(List.of(piece(B, 9, 0, false), piece(C, 9, 0, false))));
    }

    @Test
    void keeperNeedsPieces() {
        assertThrows(IllegalArgumentException.class, () -> SplitRules.keeper(List.of()));
    }

    @Test
    void tinyLoosePiecesDrop() {
        assertTrue(SplitRules.drops(piece(B, 2, 0, false), false, 4));
        assertTrue(SplitRules.drops(piece(B, 3, 0, false), false, 4));
        assertFalse(SplitRules.drops(piece(B, 4, 0, false), false, 4));
        // the keeper never drops, however small
        assertFalse(SplitRules.drops(piece(A, 1, 1, true), true, 4));
        // min 1: nothing drops
        assertFalse(SplitRules.drops(piece(B, 1, 0, false), false, 1));
    }

    @Test
    void originChainsToTheFirstShip() {
        // a piece of a ship that never split: the ship is the origin
        assertEquals(A, SplitRules.origin(null, A));
        // a piece of a piece: the line's origin stays
        assertEquals(A, SplitRules.origin(A, B));
        assertEquals(A, SplitRules.origin(SplitRules.origin(A, B), C));
    }
}

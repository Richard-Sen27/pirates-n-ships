package com.richardsenger.piratesnships.chart.tile;

import com.richardsenger.piratesnships.chart.data.BoardMarker;
import com.richardsenger.piratesnships.chart.data.MarkerIcon;
import com.richardsenger.piratesnships.chart.data.TileMarker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Updates of map boards (MAP3): the per-pixel merge and the markers (owned per slice, union by position, seams). */
class BoardMergeTest {

    @Test
    void theUpdaterWinsWhereItKnowsAndTheOldPixelStaysElsewhere() {
        byte[] old = {0, 5, 9, 0, 3};
        byte[] fresh = {7, 0, 2, 0, 0};
        assertArrayEquals(new byte[]{7, 5, 2, 0, 3}, BoardMerge.mergePixels(old, fresh));
        // nothing new: the old raster comes back unchanged
        assertArrayEquals(old, BoardMerge.mergePixels(old, new byte[5]));
        // knowledge never turns back into parchment
        byte[] merged = BoardMerge.mergePixels(old, fresh);
        for (int i = 0; i < old.length; i++) assertTrue(old[i] == 0 || merged[i] != 0);
        assertThrows(IllegalArgumentException.class, () -> BoardMerge.mergePixels(new byte[2], new byte[3]));
    }

    @Test
    void slicesOwnOnlyTheirOwnPixelsAndGiveBoardPixels() {
        List<TileMarker> stamped = List.of(new TileMarker(MarkerIcon.SKULL, 3, 4, "a"),
                new TileMarker(MarkerIcon.X, -2, 4, "the neighbour's"), new TileMarker(MarkerIcon.PORT, 16, 0, "too"));
        List<BoardMarker> owned = BoardMerge.owned(stamped, 1, 2, 16);
        assertEquals(List.of(new BoardMarker(MarkerIcon.SKULL, 19, 36, "a")), owned);
    }

    @Test
    void aMarkerAtASeamIsStampedOnBothTilesAndOwnedOnce() {
        int size = 16;
        int margin = 5;
        BoardMarker seam = new BoardMarker(MarkerIcon.ANCHOR, 15, 7, "Seam");    // last pixel of column 0
        BoardMarker far = new BoardMarker(MarkerIcon.X, 2, 7, "West");           // far from the seam
        List<BoardMarker> board = List.of(seam, far);
        List<TileMarker> left = BoardMerge.slice(board, 0, 0, size, margin, 512);
        List<TileMarker> right = BoardMerge.slice(board, 1, 0, size, margin, 512);
        assertEquals(2, left.size());
        assertEquals(List.of(new TileMarker(MarkerIcon.ANCHOR, -1, 7, "Seam")), right, "the right tile draws the part that reaches over");
        // collecting the owned markers of every slice gives the board's markers exactly once
        List<BoardMarker> back = new ArrayList<>(BoardMerge.owned(left, 0, 0, size));
        back.addAll(BoardMerge.owned(right, 1, 0, size));
        assertTrue(BoardMerge.sameMarkers(board, back));
        assertEquals(2, back.size());
    }

    @Test
    void unionKeepsTheOldMarkersAndAddsNewPositionsOnly() {
        BoardMarker oldSkull = new BoardMarker(MarkerIcon.SKULL, 10, 10, "Old");
        BoardMarker sameSpot = new BoardMarker(MarkerIcon.X, 10, 10, "New on the same pixel");
        BoardMarker fresh = new BoardMarker(MarkerIcon.PORT, 40, 2, "Port");
        List<BoardMarker> u = BoardMerge.union(List.of(oldSkull), List.of(sameSpot, fresh, fresh), 512);
        assertEquals(List.of(oldSkull, fresh), u);
        assertEquals(1, BoardMerge.union(List.of(oldSkull), List.of(fresh), 1).size(), "capped");
        assertTrue(BoardMerge.sameMarkers(List.of(oldSkull, fresh), List.of(fresh, oldSkull)));
        assertFalse(BoardMerge.sameMarkers(List.of(oldSkull), List.of(oldSkull, fresh)));
    }
}

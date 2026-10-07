package com.richardsenger.piratesnships.chart.tile;

import com.richardsenger.piratesnships.chart.data.BoardSlice;
import com.richardsenger.piratesnships.chart.data.MapTileDrawing;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Map boards (MAP3): which tiles group into a board, the rectangle and size rules, slice areas, what a board shows. */
class BoardRulesTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final BoardRules.Orientation FLOOR_N = new BoardRules.Orientation(false, Direction.NORTH);
    private static final BoardRules.Orientation FLOOR_E = new BoardRules.Orientation(false, Direction.EAST);
    private static final BoardRules.Orientation WALL_N = new BoardRules.Orientation(true, Direction.NORTH);

    /** A tiny world of tiles. */
    private static final class World {
        final Map<BlockPos, BoardRules.Orientation> tiles = new HashMap<>();

        World put(int x, int y, int z, BoardRules.Orientation o) {
            tiles.put(new BlockPos(x, y, z), o);
            return this;
        }

        BoardRules.Board find(int x, int y, int z, int max) {
            return BoardRules.find((a, b, c) -> tiles.get(new BlockPos(a, b, c)), x, y, z, max);
        }
    }

    private static BoardRules.Member at(BoardRules.Board b, int x, int y, int z) {
        return b.members().stream().filter(m -> m.x() == x && m.y() == y && m.z() == z).findFirst()
                .orElseThrow(() -> new AssertionError("no member at " + x + "," + y + "," + z));
    }

    @Test
    void aFloorSquareFormsOneBoardWithNorthAsRowZero() {
        World w = new World().put(0, 64, 0, FLOOR_N).put(1, 64, 0, FLOOR_N).put(0, 64, 1, FLOOR_N).put(1, 64, 1, FLOOR_N);
        BoardRules.Board b = w.find(1, 64, 1, 8);
        assertTrue(b.ok());
        assertEquals(2, b.columns());
        assertEquals(2, b.rows());
        assertEquals(4, b.members().size());
        // facing north on the floor: the drawing's north is -z, its right is +x
        assertEquals(0, at(b, 0, 64, 0).column());
        assertEquals(0, at(b, 0, 64, 0).row());
        assertEquals(1, at(b, 1, 64, 0).column());
        assertEquals(1, at(b, 0, 64, 1).row());
        // sorted by row, then column
        assertEquals(new BoardRules.Member(0, 64, 0, 0, 0), b.members().get(0));
        assertEquals(new BoardRules.Member(1, 64, 1, 1, 1), b.members().get(3));
        assertNotNull(b.member(1, 0));
    }

    @Test
    void otherFacingsHeightsAndWallsStayApart() {
        World w = new World().put(0, 64, 0, FLOOR_N).put(1, 64, 0, FLOOR_N)
                .put(2, 64, 0, FLOOR_E)            // turned the other way: its own board
                .put(0, 65, 0, FLOOR_N)            // a floor tile one block up: not in the floor's plane
                .put(0, 64, -1, WALL_N);           // a wall tile next to it
        BoardRules.Board b = w.find(0, 64, 0, 8);
        assertTrue(b.ok());
        assertEquals(2, b.columns());
        assertEquals(1, b.rows());
        BoardRules.Board alone = w.find(2, 64, 0, 8);
        assertEquals(1, alone.members().size());
    }

    @Test
    void aFloorBoardFacingEastTurnsWithIt() {
        // facing east: north of the drawing is +x, its right is +z (east turned clockwise = south)
        World w = new World().put(5, 64, 5, FLOOR_E).put(5, 64, 6, FLOOR_E).put(6, 64, 5, FLOOR_E).put(6, 64, 6, FLOOR_E);
        BoardRules.Board b = w.find(5, 64, 5, 8);
        assertEquals(new BoardRules.Member(6, 64, 5, 0, 0), b.member(0, 0));
        assertEquals(new BoardRules.Member(6, 64, 6, 1, 0), b.member(1, 0));
        assertEquals(new BoardRules.Member(5, 64, 5, 0, 1), b.member(0, 1));
    }

    @Test
    void aWallRowIsReadFromTheFront() {
        // on a wall facing north, the viewer looks south: their right is west (-x), up is +y
        World w = new World().put(0, 64, 0, WALL_N).put(1, 64, 0, WALL_N).put(2, 64, 0, WALL_N)
                .put(1, 64, 1, WALL_N); // behind the wall plane: not a neighbour in the plane
        BoardRules.Board b = w.find(1, 64, 0, 8);
        assertTrue(b.ok());
        assertEquals(3, b.columns());
        assertEquals(1, b.rows());
        assertEquals(0, at(b, 2, 64, 0).column());
        assertEquals(2, at(b, 0, 64, 0).column());
        World tall = new World().put(0, 64, 0, WALL_N).put(0, 65, 0, WALL_N);
        BoardRules.Board t = tall.find(0, 64, 0, 8);
        assertEquals(1, t.columns());
        assertEquals(2, t.rows());
        assertEquals(0, at(t, 0, 65, 0).row());
        assertEquals(1, at(t, 0, 64, 0).row());
    }

    @Test
    void anLShapeIsNoRectangle() {
        World w = new World().put(0, 64, 0, FLOOR_N).put(1, 64, 0, FLOOR_N).put(0, 64, 1, FLOOR_N);
        BoardRules.Board b = w.find(0, 64, 0, 8);
        assertEquals(BoardRules.Refusal.NOT_RECTANGLE, b.refusal());
        assertFalse(b.ok());
        assertTrue(b.members().isEmpty());
    }

    @Test
    void sidesAreLimited() {
        World w = new World();
        for (int x = 0; x < 9; x++) w.put(x, 64, 0, FLOOR_N);
        assertEquals(BoardRules.Refusal.TOO_BIG, w.find(4, 64, 0, 8).refusal());
        assertTrue(w.find(4, 64, 0, 9).ok());
        World single = new World().put(3, 70, 3, FLOOR_N);
        BoardRules.Board one = single.find(3, 70, 3, 1);
        assertTrue(one.ok());
        assertEquals(1, one.columns());
        assertEquals(1, one.rows());
    }

    @Test
    void sliceAreasFollowTheBoardArea() {
        BoardRules.Area a = new BoardRules.Area(100, -50, 128, 4, 4);
        assertEquals(100, a.sliceMinCx(0));
        assertEquals(100 + 2 * 128 * 4, a.sliceMinCx(2));
        assertEquals(-50 + 128 * 4, a.sliceMinCz(1));
        assertEquals(3L * 128 * 4, BoardRules.cells(3, 128, 4));
    }

    // --- what a board shows -------------------------------------------------------------------------------------

    private static MapTileDrawing slice(BoardRules.Area a, UUID id, int column, int row, int columns, int rows) {
        return new MapTileDrawing(a.size(), new byte[a.size() * a.size()], a.sliceMinCx(column), a.sliceMinCz(row), a.cellBlocks(),
                "Anne", 3, List.of(), a.zoom(), Optional.of(new BoardSlice(id, column, row, columns, rows)));
    }

    private static List<BoardRules.Slot> board(BoardRules.Area a, UUID id, int columns, int rows) {
        List<BoardRules.Slot> slots = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) slots.add(new BoardRules.Slot(c, r, slice(a, id, c, r, columns, rows)));
        }
        return slots;
    }

    @Test
    void consistentSlicesAreAnIntactBoard() {
        BoardRules.Area a = new BoardRules.Area(-300, 40, 16, 2, 4);
        UUID id = UUID.randomUUID();
        BoardRules.Existing e = BoardRules.existing(board(a, id, 2, 2), 2, 2);
        assertEquals(BoardRules.State.INTACT, e.state());
        assertEquals(a, e.area());
        assertEquals(id, e.id());
    }

    @Test
    void blankBrokenAndLegacyBoards() {
        BoardRules.Area a = new BoardRules.Area(0, 0, 16, 1, 4);
        UUID id = UUID.randomUUID();
        List<BoardRules.Slot> blank = List.of(new BoardRules.Slot(0, 0, null), new BoardRules.Slot(1, 0, null));
        assertEquals(BoardRules.State.BLANK, BoardRules.existing(blank, 2, 1).state());

        // one tile missing its drawing (a blank tile put in place of a broken one)
        List<BoardRules.Slot> holes = new ArrayList<>(board(a, id, 2, 1));
        holes.set(1, new BoardRules.Slot(1, 0, null));
        assertEquals(BoardRules.State.BROKEN, BoardRules.existing(holes, 2, 1).state());

        // the board was 3 wide, one tile is gone: the record no longer matches the tiles
        List<BoardRules.Slot> shrunk = board(a, id, 3, 1).subList(0, 2).stream()
                .map(s -> new BoardRules.Slot(s.column(), s.row(), s.drawing())).toList();
        assertEquals(BoardRules.State.BROKEN, BoardRules.existing(shrunk, 2, 1).state());

        // two boards' tiles side by side
        List<BoardRules.Slot> mixed = new ArrayList<>(board(a, id, 2, 1));
        mixed.set(1, new BoardRules.Slot(1, 0, slice(a, UUID.randomUUID(), 1, 0, 2, 1)));
        assertEquals(BoardRules.State.BROKEN, BoardRules.existing(mixed, 2, 1).state());

        // swapped tiles: the columns do not match their places
        List<BoardRules.Slot> swapped = List.of(new BoardRules.Slot(0, 0, slice(a, id, 1, 0, 2, 1)), new BoardRules.Slot(1, 0, slice(a, id, 0, 0, 2, 1)));
        assertEquals(BoardRules.State.BROKEN, BoardRules.existing(swapped, 2, 1).state());

        // a MAP2 tile (no board record) on its own is an intact 1x1 board without an id
        MapTileDrawing legacy = new MapTileDrawing(16, new byte[256], 7, 9, 4, "Mary", 1, List.of());
        BoardRules.Existing e = BoardRules.existing(List.of(new BoardRules.Slot(0, 0, legacy)), 1, 1);
        assertEquals(BoardRules.State.INTACT, e.state());
        assertNull(e.id());
        assertEquals(new BoardRules.Area(7, 9, 16, 1, 4), e.area());
    }
}

package com.richardsenger.piratesnships.chart.tile;

import com.richardsenger.piratesnships.chart.data.BoardSlice;
import com.richardsenger.piratesnships.chart.data.MapTileDrawing;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Which map tiles form one board and what the board already shows (work package MAP3), pure: no world access, the
 * world is read through a {@link TileLookup}.
 *
 * <p><b>Grouping.</b> Tiles with the same orientation (floor or wall, same {@code facing}) that touch side by side in
 * their plane belong together: from the used tile, the neighbours one step "right" and "up" of the drawing (and
 * back) are followed. On the floor "up" is {@code facing} (the drawing's north) and "right" is {@code facing} turned
 * clockwise, all at the same height; on a wall "up" is up and "right" is {@code facing} turned counter-clockwise, all
 * in the same wall plane. This matches {@link com.richardsenger.piratesnships.chart.client.MapTileRenderer}.
 *
 * <p><b>The board.</b> The set must fill its bounding rectangle ({@link Refusal#NOT_RECTANGLE}) of at most
 * {@code maxSide} tiles per side ({@link Refusal#TOO_BIG}). Column 0 is the drawing's west (left) edge, row 0 its
 * north (top) edge. A single tile is a 1x1 board.
 *
 * <p><b>Areas.</b> A board of {@code columns x rows} tiles of {@code size} pixels at zoom {@code z} shows
 * {@code size * columns * z} by {@code size * rows * z} chart cells from cell {@code (minCx, minCz)}; the slice in
 * column {@code c}, row {@code r} starts at {@code (minCx + c * size * z, minCz + r * size * z)} ({@link Area#sliceMinCx}).
 */
public final class BoardRules {

    /** Why tiles do not form a board. */
    public enum Refusal { NONE, NOT_RECTANGLE, TOO_BIG }

    /** How a map tile lies: on a wall or the floor, and where the drawing's north points. */
    public record Orientation(boolean wall, Direction facing) {
        public Orientation {
            Objects.requireNonNull(facing);
        }

        /** One step to the drawing's right, as {x, y, z}. */
        public int[] right() {
            Direction d = wall ? facing.getCounterClockWise() : facing.getClockWise();
            return new int[]{d.getStepX(), d.getStepY(), d.getStepZ()};
        }

        /** One step to the drawing's top (north), as {x, y, z}. */
        public int[] up() {
            return wall ? new int[]{0, 1, 0} : new int[]{facing.getStepX(), facing.getStepY(), facing.getStepZ()};
        }
    }

    /** The orientation of the map tile at a block position, or {@code null} for anything else. */
    @FunctionalInterface
    public interface TileLookup {
        @Nullable Orientation at(int x, int y, int z);
    }

    /** One tile of a board: its block position and its column and row. */
    public record Member(int x, int y, int z, int column, int row) {
    }

    /** The tiles of a board (sorted by row, then column), or why there is none. */
    public record Board(Refusal refusal, List<Member> members, int columns, int rows) {
        public boolean ok() {
            return refusal == Refusal.NONE;
        }

        public @Nullable Member member(int column, int row) {
            for (Member m : members) {
                if (m.column() == column && m.row() == row) return m;
            }
            return null;
        }
    }

    /** The area a board shows: its first cell, the tile size in pixels, the zoom and the cell size in blocks. */
    public record Area(int minCx, int minCz, int size, int zoom, int cellBlocks) {

        /** The first cell of the slice in {@code column}, {@code row}. */
        public int sliceMinCx(int column) {
            return BoardRules.sliceMin(minCx, column, size, zoom);
        }

        public int sliceMinCz(int row) {
            return BoardRules.sliceMin(minCz, row, size, zoom);
        }
    }

    /** What the tiles of a board show now. */
    public enum State {
        /** No tile holds a drawing. */
        BLANK,
        /** Every tile holds its slice of one board that matches the tiles' arrangement: it can be updated. */
        INTACT,
        /** Some tiles hold drawings, but not one consistent board (a tile was removed, added or moved). */
        BROKEN
    }

    /** {@link #state()}, and for an intact board its area and id ({@code null} id: a MAP2 single tile without a board record). */
    public record Existing(State state, @Nullable Area area, @Nullable UUID id) {
        public boolean intact() {
            return state == State.INTACT;
        }

        public boolean anyDrawing() {
            return state != State.BLANK;
        }
    }

    /** A tile of the board with what it shows ({@code null}: blank). */
    public record Slot(int column, int row, @Nullable MapTileDrawing drawing) {
    }

    private BoardRules() {
    }

    // --- grouping ----------------------------------------------------------------------------------------------------

    /**
     * The board the tile at {@code (x, y, z)} belongs to (it must be a tile: {@code lookup} returns its orientation).
     * The search stops as soon as the tiles span more than {@code maxSide} in either direction.
     */
    public static Board find(TileLookup lookup, int x, int y, int z, int maxSide) {
        Orientation o = lookup.at(x, y, z);
        if (o == null) throw new IllegalArgumentException("no map tile at " + x + "," + y + "," + z);
        int[] r = o.right();
        int[] up = o.up();
        Set<Long> seen = new HashSet<>();
        Deque<long[]> queue = new ArrayDeque<>();
        seen.add(key(0, 0));
        queue.add(new long[]{0, 0});
        int minU = 0, maxU = 0, minV = 0, maxV = 0;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            long[] cur = queue.poll();
            for (int[] s : steps) {
                int u = (int) cur[0] + s[0];
                int v = (int) cur[1] + s[1];
                if (seen.contains(key(u, v))) continue;
                if (!o.equals(lookup.at(x + r[0] * u + up[0] * v, y + r[1] * u + up[1] * v, z + r[2] * u + up[2] * v))) continue;
                seen.add(key(u, v));
                queue.add(new long[]{u, v});
                minU = Math.min(minU, u);
                maxU = Math.max(maxU, u);
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
                if (maxU - minU + 1 > maxSide || maxV - minV + 1 > maxSide) return new Board(Refusal.TOO_BIG, List.of(), 0, 0);
            }
        }
        int columns = maxU - minU + 1;
        int rows = maxV - minV + 1;
        if (seen.size() != columns * rows) return new Board(Refusal.NOT_RECTANGLE, List.of(), 0, 0);
        List<Member> members = new ArrayList<>(seen.size());
        for (long k : seen) {
            int u = (int) (k >> 32);
            int v = (int) k;
            members.add(new Member(x + r[0] * u + up[0] * v, y + r[1] * u + up[1] * v, z + r[2] * u + up[2] * v, u - minU, maxV - v));
        }
        members.sort(Comparator.comparingInt(Member::row).thenComparingInt(Member::column));
        return new Board(Refusal.NONE, List.copyOf(members), columns, rows);
    }

    private static long key(int u, int v) {
        return ((long) u << 32) | (v & 0xFFFFFFFFL);
    }

    // --- areas -------------------------------------------------------------------------------------------------------

    /** The first cell of slice {@code index} (a column or a row) of a board starting at cell {@code boardMin}. */
    public static int sliceMin(int boardMin, int index, int size, int zoom) {
        return boardMin + index * size * zoom;
    }

    /** Chart cells along one side of a board of {@code tiles} tiles. */
    public static long cells(int tiles, int size, int zoom) {
        return (long) tiles * size * zoom;
    }

    // --- what the board shows ----------------------------------------------------------------------------------------

    /**
     * What the tiles show. {@link State#INTACT}: every slot holds a drawing with a {@link BoardSlice} of the same board
     * id and size whose column and row are the slot's, all with the same tile size, cell size and zoom, and areas that
     * fit together ({@link Area#sliceMinCx}). A 1x1 board whose tile holds a MAP2 drawing without a board record is intact
     * too (id {@code null}).
     */
    public static Existing existing(List<Slot> slots, int columns, int rows) {
        boolean any = false;
        boolean all = true;
        for (Slot s : slots) {
            if (s.drawing() != null) any = true;
            else all = false;
        }
        if (!any) return new Existing(State.BLANK, null, null);
        if (!all || slots.size() != columns * rows) return new Existing(State.BROKEN, null, null);
        Slot first = slots.get(0);
        MapTileDrawing d0 = first.drawing();
        int size = d0.size();
        int zoom = d0.zoom();
        int cb = d0.cellBlocks();
        int baseX = d0.minCx() - first.column() * size * zoom;
        int baseZ = d0.minCz() - first.row() * size * zoom;
        Area area = new Area(baseX, baseZ, size, zoom, cb);
        if (columns * rows == 1 && d0.board().isEmpty()) return new Existing(State.INTACT, area, null);
        UUID id = d0.board().map(BoardSlice::board).orElse(null);
        if (id == null) return new Existing(State.BROKEN, null, null);
        for (Slot s : slots) {
            MapTileDrawing d = s.drawing();
            BoardSlice b = d.board().orElse(null);
            if (b == null || !b.board().equals(id) || b.columns() != columns || b.rows() != rows
                    || b.column() != s.column() || b.row() != s.row()
                    || d.size() != size || d.zoom() != zoom || d.cellBlocks() != cb
                    || d.minCx() != area.sliceMinCx(s.column()) || d.minCz() != area.sliceMinCz(s.row())) {
                return new Existing(State.BROKEN, null, null);
            }
        }
        return new Existing(State.INTACT, area, id);
    }
}

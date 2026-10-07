package com.richardsenger.piratesnships.chart.data;

/**
 * A chart marker on a whole map board (work package MAP3): its icon, its position in <b>board</b> pixels (column
 * {@code c}, pixel {@code x} of a slice of {@code size} pixels is board pixel {@code c * size + x}; rows likewise) and
 * its name. Slices store {@link TileMarker}s in their own pixels; {@link com.richardsenger.piratesnships.chart.tile.BoardMerge}
 * converts between the two.
 */
public record BoardMarker(MarkerIcon icon, int bx, int by, String name) {

    public BoardMarker {
        name = name == null ? "" : name;
    }

    /** The position as one key, for the union by position. */
    public long position() {
        return ((long) bx << 32) | (by & 0xFFFFFFFFL);
    }
}

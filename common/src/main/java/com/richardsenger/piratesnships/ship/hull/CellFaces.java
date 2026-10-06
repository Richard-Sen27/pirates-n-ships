package com.richardsenger.piratesnships.ship.hull;

/**
 * Six-bit masks of a cell's faces, one bit per face in vanilla's {@code Direction#get3DDataValue} order: down, up,
 * north (−z), south (+z), west (−x), east (+x). Pure, no world access.
 */
public final class CellFaces {

    public static final int DOWN = 1;
    public static final int UP = 1 << 1;
    public static final int NORTH = 1 << 2;
    public static final int SOUTH = 1 << 3;
    public static final int WEST = 1 << 4;
    public static final int EAST = 1 << 5;
    public static final int ALL = 0b111111;

    /** Grid offsets of the neighbour across face {@code bit} ({@code 0..5}). */
    static final int[] DX = {0, 0, 0, 0, -1, 1};
    static final int[] DY = {-1, 1, 0, 0, 0, 0};
    static final int[] DZ = {0, 0, -1, 1, 0, 0};

    private CellFaces() {
    }

    /** The bit of the face with vanilla 3D data value {@code dataValue} ({@code Direction#get3DDataValue}). */
    public static int bit(int dataValue) {
        return 1 << dataValue;
    }
}

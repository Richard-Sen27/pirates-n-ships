package com.richardsenger.piratesnships.sailing.sail;

/**
 * The frame in which a cleat stores the offset to the other end of its stay: the world offset turned back by the
 * cleat's horizontal facing. A structure rotation (disassembly in quarter turns, structure templates) turns the
 * facing, and with it the stored offset, so a stay survives being rotated although block entity data itself is never
 * rotated.
 *
 * <p>{@code quarterTurns} is the facing as clockwise quarter turns seen from above (vanilla's
 * {@code Direction#get2DDataValue}: south 0, west 1, north 2, east 3). One clockwise quarter turn maps
 * {@code (x, z)} to {@code (-z, x)}.
 */
public final class CleatFrame {

    private CleatFrame() {
    }

    /** A world offset {@code {x, y, z}} in the frame of a cleat facing {@code quarterTurns}. */
    public static int[] toLocal(int quarterTurns, int x, int y, int z) {
        return turn(Math.floorMod(-quarterTurns, 4), x, y, z);
    }

    /** A local offset back to the world, for a cleat facing {@code quarterTurns}. */
    public static int[] toWorld(int quarterTurns, int x, int y, int z) {
        return turn(Math.floorMod(quarterTurns, 4), x, y, z);
    }

    private static int[] turn(int q, int x, int y, int z) {
        for (int i = 0; i < q; i++) {
            int nx = -z;
            z = x;
            x = nx;
        }
        return new int[] {x, y, z};
    }
}

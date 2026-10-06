package com.richardsenger.piratesnships.ship.hull;

import java.util.Arrays;

/**
 * Immutable voxel snapshot of a ship's bounding box. Cell {@code (x, y, z)} (grid coordinates, from 0) is the ship-local
 * block at {@code origin + (x, y, z)}; its index is {@code x + sizeX * (z + sizeZ * y)}. Storage is four bitsets
 * ({@code long[]}): solid, opening, the open state of openings, and <em>partial</em> cells (a solid or opening block
 * whose shape does not fill its cube: slabs, stairs, trapdoors, doors), plus for each partial cell the
 * {@link CellFaces} mask of its faces that the block's shape does not fully cover. Partial cells are walls like any
 * other for the analysis; only the dry regions read them ({@link PartialCellRule}). Being immutable, a grid can be
 * handed to another thread for analysis. Build one with {@link #builder}.
 */
public final class HullGrid {

    private final int sizeX, sizeY, sizeZ;
    private final int originX, originY, originZ;
    private final long[] solid;
    private final long[] opening;
    private final long[] open;
    private final long[] partial;
    /** Uncovered-face mask per cell, {@code null} when the grid has no partial cell. */
    private final byte[] faces;

    private HullGrid(Builder b) {
        this.sizeX = b.sizeX;
        this.sizeY = b.sizeY;
        this.sizeZ = b.sizeZ;
        this.originX = b.originX;
        this.originY = b.originY;
        this.originZ = b.originZ;
        this.solid = b.solid.clone();
        this.opening = b.opening.clone();
        this.open = b.open.clone();
        this.partial = b.partial.clone();
        boolean anyPartial = false;
        for (long w : partial) anyPartial |= w != 0;
        // normalized (null when no cell is partial; zero for every non-partial cell) so equals compares content
        this.faces = b.faces == null || !anyPartial ? null : b.faces.clone();
    }

    /** A builder for an all-air grid of the given size with origin (0, 0, 0). */
    public static Builder builder(int sizeX, int sizeY, int sizeZ) {
        return new Builder(sizeX, sizeY, sizeZ);
    }

    /** A builder pre-filled with this grid's contents. */
    public Builder toBuilder() {
        Builder b = new Builder(sizeX, sizeY, sizeZ).origin(originX, originY, originZ);
        System.arraycopy(solid, 0, b.solid, 0, solid.length);
        System.arraycopy(opening, 0, b.opening, 0, opening.length);
        System.arraycopy(open, 0, b.open, 0, open.length);
        System.arraycopy(partial, 0, b.partial, 0, partial.length);
        b.faces = faces == null ? null : faces.clone();
        return b;
    }

    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }
    public int originX() { return originX; }
    public int originY() { return originY; }
    public int originZ() { return originZ; }

    public int cellCount() {
        return sizeX * sizeY * sizeZ;
    }

    public boolean inBounds(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    public int index(int x, int y, int z) {
        return x + sizeX * (z + sizeZ * y);
    }

    public int x(int index) { return index % sizeX; }
    public int z(int index) { return (index / sizeX) % sizeZ; }
    public int y(int index) { return index / (sizeX * sizeZ); }

    public CellKind kind(int index) {
        if (get(solid, index)) return CellKind.SOLID;
        if (get(opening, index)) return CellKind.OPENING;
        return CellKind.AIR;
    }

    public CellKind kind(int x, int y, int z) {
        return kind(index(x, y, z));
    }

    /** Whether the cell is an opening in its open state (false for every other kind). */
    public boolean isOpen(int index) {
        return get(opening, index) && get(open, index);
    }

    public boolean isOpen(int x, int y, int z) {
        return isOpen(index(x, y, z));
    }

    /** Whether the cell is a partial block: a solid or opening cell whose shape does not fill its whole cube. */
    public boolean isPartial(int index) {
        return get(partial, index);
    }

    public boolean isPartial(int x, int y, int z) {
        return isPartial(index(x, y, z));
    }

    /** {@link CellFaces} mask of a partial cell's faces that its shape does not fully cover (0 for other cells). */
    public int uncoveredFaces(int index) {
        return faces == null || !get(partial, index) ? 0 : faces[index] & CellFaces.ALL;
    }

    /** Grid indices of all partial cells, ascending. */
    public int[] partialCells() {
        int n = 0;
        for (long w : partial) n += Long.bitCount(w);
        int[] out = new int[n];
        int k = 0;
        for (int wi = 0; wi < partial.length; wi++) {
            long w = partial[wi];
            while (w != 0) {
                out[k++] = (wi << 6) + Long.numberOfTrailingZeros(w);
                w &= w - 1;
            }
        }
        return out;
    }

    /** Ship-local center of a cell. */
    public HullVec center(int index) {
        return new HullVec(originX + x(index) + 0.5, originY + y(index) + 0.5, originZ + z(index) + 0.5);
    }

    /** Height of a cell's center along {@code up}: {@code up · center}. */
    public double height(int index, HullVec up) {
        return up.x() * (originX + x(index) + 0.5) + up.y() * (originY + y(index) + 0.5) + up.z() * (originZ + z(index) + 0.5);
    }

    static boolean get(long[] bits, int i) {
        return (bits[i >>> 6] & (1L << i)) != 0;
    }

    static void put(long[] bits, int i, boolean v) {
        if (v) bits[i >>> 6] |= 1L << i;
        else bits[i >>> 6] &= ~(1L << i);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof HullGrid g && g.sizeX == sizeX && g.sizeY == sizeY && g.sizeZ == sizeZ
                && g.originX == originX && g.originY == originY && g.originZ == originZ
                && Arrays.equals(g.solid, solid) && Arrays.equals(g.opening, opening) && Arrays.equals(g.open, open)
                && Arrays.equals(g.partial, partial) && Arrays.equals(g.faces, faces);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(solid) * 31 + Arrays.hashCode(opening);
    }

    /** Mutable grid used to build snapshots. Not thread-safe. */
    public static final class Builder {
        private final int sizeX, sizeY, sizeZ;
        private int originX, originY, originZ;
        private final long[] solid, opening, open, partial;
        private byte[] faces;

        Builder(int sizeX, int sizeY, int sizeZ) {
            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                throw new IllegalArgumentException("Grid size must be positive");
            }
            if ((long) sizeX * sizeY * sizeZ > Integer.MAX_VALUE / 2) {
                throw new IllegalArgumentException("Grid too large");
            }
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            int words = (sizeX * sizeY * sizeZ + 63) >>> 6;
            this.solid = new long[words];
            this.opening = new long[words];
            this.open = new long[words];
            this.partial = new long[words];
        }

        /** Ship-local coordinates of grid cell (0, 0, 0). */
        public Builder origin(int x, int y, int z) {
            this.originX = x;
            this.originY = y;
            this.originZ = z;
            return this;
        }

        public int sizeX() { return sizeX; }
        public int sizeY() { return sizeY; }
        public int sizeZ() { return sizeZ; }

        public Builder set(int x, int y, int z, CellKind kind) {
            return set(x, y, z, kind, false);
        }

        /** Sets a cell; {@code open} only matters for {@link CellKind#OPENING}. */
        public Builder set(int x, int y, int z, CellKind kind, boolean isOpen) {
            if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
                throw new IndexOutOfBoundsException("Cell out of grid: " + x + "," + y + "," + z);
            }
            int i = x + sizeX * (z + sizeZ * y);
            put(solid, i, kind == CellKind.SOLID);
            put(opening, i, kind == CellKind.OPENING);
            put(open, i, kind == CellKind.OPENING && isOpen);
            put(partial, i, false);
            if (faces != null) faces[i] = 0;
            return this;
        }

        /**
         * Marks a solid or opening cell as a partial block whose shape leaves {@code uncoveredFaces} (a
         * {@link CellFaces} mask) not fully covered. A mask of 0, or an air cell, clears the mark. Call after
         * {@link #set}, which resets it.
         */
        public Builder partial(int x, int y, int z, int uncoveredFaces) {
            int i = x + sizeX * (z + sizeZ * y);
            int mask = uncoveredFaces & CellFaces.ALL;
            boolean on = mask != 0 && (get(solid, i) || get(opening, i));
            put(partial, i, on);
            if (on) {
                if (faces == null) faces = new byte[sizeX * sizeY * sizeZ];
                faces[i] = (byte) mask;
            } else if (faces != null) {
                faces[i] = 0;
            }
            return this;
        }

        /** A breach (destroyed hull block, §4.5): an opening that is permanently open until patched. */
        public Builder breach(int x, int y, int z) {
            return set(x, y, z, CellKind.OPENING, true);
        }

        public CellKind kind(int x, int y, int z) {
            int i = x + sizeX * (z + sizeZ * y);
            if (get(solid, i)) return CellKind.SOLID;
            if (get(opening, i)) return CellKind.OPENING;
            return CellKind.AIR;
        }

        /** Fills the inclusive box with one kind. */
        public Builder fill(int x0, int y0, int z0, int x1, int y1, int z1, CellKind kind) {
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++)
                    for (int x = x0; x <= x1; x++)
                        set(x, y, z, kind);
            return this;
        }

        public HullGrid build() {
            return new HullGrid(this);
        }
    }
}

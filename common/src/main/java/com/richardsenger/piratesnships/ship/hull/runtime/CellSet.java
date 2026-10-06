package com.richardsenger.piratesnships.ship.hull.runtime;

import com.richardsenger.piratesnships.ship.hull.HullGrid;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.BitSet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * A compact set of plot cells: the tight bounding box plus one bit per cell, layout {@code x + sx·(z + sz·y)} (the
 * {@link HullGrid} layout, which is also what {@code ship.sable.WaterRegions#add} takes). A 10×4×20 hold is 100 bytes.
 */
public record CellSet(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, BitSet bits) {

    public static final StreamCodec<ByteBuf, CellSet> CODEC = StreamCodec.of(CellSet::write, CellSet::read);

    /** Largest box a payload may announce (guards the client against a malformed or hostile packet). */
    static final int MAX_CELLS = 1 << 22;

    public CellSet {
        bits = (BitSet) bits.clone();
    }

    /** The cells {@code gridCells} (grid indices of {@code grid}) as a plot-coordinate set cropped to their bounds. */
    public static CellSet fromGrid(HullGrid grid, BitSet gridCells) {
        if (gridCells.isEmpty()) {
            return new CellSet(grid.originX(), grid.originY(), grid.originZ(), 1, 1, 1, new BitSet());
        }
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (int i = gridCells.nextSetBit(0); i >= 0; i = gridCells.nextSetBit(i + 1)) {
            int x = grid.x(i), y = grid.y(i), z = grid.z(i);
            x0 = Math.min(x0, x); y0 = Math.min(y0, y); z0 = Math.min(z0, z);
            x1 = Math.max(x1, x); y1 = Math.max(y1, y); z1 = Math.max(z1, z);
        }
        int sx = x1 - x0 + 1, sy = y1 - y0 + 1, sz = z1 - z0 + 1;
        BitSet b = new BitSet(sx * sy * sz);
        for (int i = gridCells.nextSetBit(0); i >= 0; i = gridCells.nextSetBit(i + 1)) {
            b.set((grid.x(i) - x0) + sx * ((grid.z(i) - z0) + sz * (grid.y(i) - y0)));
        }
        return new CellSet(grid.originX() + x0, grid.originY() + y0, grid.originZ() + z0, sx, sy, sz, b);
    }

    public boolean isEmpty() {
        return bits.isEmpty();
    }

    public int count() {
        return bits.cardinality();
    }

    /** Whether a plot cell is in the set. */
    public boolean contains(int x, int y, int z) {
        int lx = x - minX, ly = y - minY, lz = z - minZ;
        if (lx < 0 || ly < 0 || lz < 0 || lx >= sizeX || ly >= sizeY || lz >= sizeZ) {
            return false;
        }
        return bits.get(lx + sizeX * (lz + sizeZ * ly));
    }

    private static void write(ByteBuf buf, CellSet s) {
        FriendlyByteBuf out = new FriendlyByteBuf(buf);
        out.writeVarInt(s.minX).writeVarInt(s.minY).writeVarInt(s.minZ);
        out.writeVarInt(s.sizeX).writeVarInt(s.sizeY).writeVarInt(s.sizeZ);
        out.writeLongArray(s.bits.toLongArray());
    }

    private static CellSet read(ByteBuf buf) {
        FriendlyByteBuf in = new FriendlyByteBuf(buf);
        int x = in.readVarInt(), y = in.readVarInt(), z = in.readVarInt();
        int sx = in.readVarInt(), sy = in.readVarInt(), sz = in.readVarInt();
        if (sx <= 0 || sy <= 0 || sz <= 0 || (long) sx * sy * sz > MAX_CELLS) {
            throw new IllegalArgumentException("Bad cell set size " + sx + "x" + sy + "x" + sz);
        }
        long[] words = in.readLongArray(null, (MAX_CELLS + 63) / 64);
        return new CellSet(x, y, z, sx, sy, sz, BitSet.valueOf(words));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CellSet c && minX == c.minX && minY == c.minY && minZ == c.minZ
                && sizeX == c.sizeX && sizeY == c.sizeY && sizeZ == c.sizeZ && bits.equals(c.bits);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new int[] {minX, minY, minZ, sizeX, sizeY, sizeZ, bits.hashCode()});
    }
}

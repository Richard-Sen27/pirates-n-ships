package com.richardsenger.piratesnships.chart.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

/**
 * 64 x 64 chart cells (work package MAP1), one byte each ({@link ChartCells}), at region coordinates
 * {@code (rx, rz)}: cell {@code (cx, cz)} lies in region {@code (cx >> 6, cz >> 6)} at {@code (cx & 63, cz & 63)}.
 * Immutable: the byte array is never changed after construction (copies share it when only {@link #touched}
 * changes). {@link #version} is the {@link ChartData#version()} at the last change of a cell, so a client that has
 * seen a version needs nothing older; {@link #touched} is the game time the owner last sampled anything in it (the
 * cap drops the least recently touched regions first).
 *
 * <p>CHART4: beside the cells a region may hold a colour per cell (vanilla's packed map colour, 0 = none; see
 * {@link ChartCells}). A region saved or sent before CHART4 has none ({@code null}); its cells draw in ink until they
 * are sampled again.
 */
public final class ChartRegion {

    public static final int SHIFT = 6;
    public static final int SIZE = 1 << SHIFT;
    public static final int MASK = SIZE - 1;
    public static final int CELLS = SIZE * SIZE;

    private static final Codec<ChartRegion> RAW = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x").forGetter(ChartRegion::rx),
            Codec.INT.fieldOf("z").forGetter(ChartRegion::rz),
            Codec.LONG.fieldOf("version").forGetter(ChartRegion::version),
            Codec.LONG.fieldOf("touched").forGetter(ChartRegion::touched),
            Codec.BYTE_BUFFER.fieldOf("cells").forGetter(r -> ByteBuffer.wrap(r.cells.clone())),
            Codec.BYTE_BUFFER.optionalFieldOf("colours").forGetter(r -> Optional.ofNullable(r.colours).map(c -> ByteBuffer.wrap(c.clone())))
    ).apply(i, ChartRegion::fromBuffer));

    /** Saved form; a region whose cell or colour array has the wrong size fails to decode. */
    public static final Codec<ChartRegion> CODEC = RAW.flatXmap(
            r -> r.cells.length != CELLS ? DataResult.error(() -> "chart region " + r.rx + "," + r.rz + " has " + r.cells.length + " cells")
                    : r.colours != null && r.colours.length != CELLS ? DataResult.error(() -> "chart region " + r.rx + "," + r.rz + " has " + r.colours.length + " colours")
                    : DataResult.success(r),
            DataResult::success);

    private final int rx;
    private final int rz;
    private final byte[] cells;
    /** CHART4: packed map colours per cell, or null (a region from before CHART4). */
    private final byte[] colours;
    private final long version;
    private final long touched;

    private ChartRegion(int rx, int rz, byte[] cells, byte[] colours, long version, long touched) {
        this.rx = rx;
        this.rz = rz;
        this.cells = cells;
        this.colours = colours;
        this.version = version;
        this.touched = touched;
    }

    /** A region with a copy of {@code cells} (exactly {@link #CELLS} bytes, row-major: index {@code z * 64 + x}), no colours. */
    public static ChartRegion of(int rx, int rz, byte[] cells, long version, long touched) {
        return of(rx, rz, cells, null, version, touched);
    }

    /** CHART4: {@link #of} with a copy of {@code colours} ({@link #CELLS} bytes like the cells, or null for none). */
    public static ChartRegion of(int rx, int rz, byte[] cells, byte[] colours, long version, long touched) {
        if (cells.length != CELLS) throw new IllegalArgumentException("a chart region has " + CELLS + " cells, got " + cells.length);
        if (colours != null && colours.length != CELLS) throw new IllegalArgumentException("a chart region has " + CELLS + " colours, got " + colours.length);
        return new ChartRegion(rx, rz, cells.clone(), colours == null ? null : colours.clone(), version, touched);
    }

    /** Takes ownership of {@code cells} and {@code colours} (may be null): the caller must not change them afterwards. */
    static ChartRegion adopt(int rx, int rz, byte[] cells, byte[] colours, long version, long touched) {
        return new ChartRegion(rx, rz, cells, colours, version, touched);
    }

    private static ChartRegion fromBuffer(int rx, int rz, long version, long touched, ByteBuffer buffer, Optional<ByteBuffer> colourBuffer) {
        return new ChartRegion(rx, rz, bytes(buffer), colourBuffer.map(ChartRegion::bytes).orElse(null), version, touched);
    }

    private static byte[] bytes(ByteBuffer buffer) {
        ByteBuffer b = buffer.duplicate();
        byte[] out = new byte[b.remaining()];
        b.get(out);
        return out;
    }

    public static long key(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    public static int keyX(long key) {
        return (int) (key >> 32);
    }

    public static int keyZ(long key) {
        return (int) key;
    }

    /** The key of the region holding cell {@code (cx, cz)}. */
    public static long keyOfCell(int cx, int cz) {
        return key(cx >> SHIFT, cz >> SHIFT);
    }

    public static int index(int localX, int localZ) {
        return (localZ << SHIFT) | localX;
    }

    public long key() {
        return key(rx, rz);
    }

    public int rx() {
        return rx;
    }

    public int rz() {
        return rz;
    }

    public long version() {
        return version;
    }

    public long touched() {
        return touched;
    }

    /** The cell byte at local coordinates (0..63). */
    public byte get(int localX, int localZ) {
        return cells[index(localX, localZ)];
    }

    /** CHART4: the packed map colour of the cell at local coordinates (0..63), 0 when it has none. */
    public int colour(int localX, int localZ) {
        return colours == null ? 0 : colours[index(localX, localZ)] & 0xFF;
    }

    /** CHART4: the wide cell ({@link ChartCells#wide}) at local coordinates (0..63): the cell byte and its colour. */
    public int wide(int localX, int localZ) {
        int i = index(localX, localZ);
        return (cells[i] & 0xFF) | (colours == null ? 0 : (colours[i] & 0xFF) << 8);
    }

    /** CHART4: whether this region carries colours (sampled since CHART4). */
    public boolean hasColours() {
        return colours != null;
    }

    /** A copy of all cell bytes. */
    public byte[] copyCells() {
        return cells.clone();
    }

    /** CHART4: a copy of the colours, or null when the region has none. */
    public byte[] copyColours() {
        return colours == null ? null : colours.clone();
    }

    /** Package code only: the backing array, never to be changed. */
    byte[] cellsUnsafe() {
        return cells;
    }

    /** Package code only: the backing colour array (may be null), never to be changed. */
    byte[] coloursUnsafe() {
        return colours;
    }

    public ChartRegion withTouched(long time) {
        return time == touched ? this : new ChartRegion(rx, rz, cells, colours, version, time);
    }

    /** How many cells are known. */
    public int knownCount() {
        int n = 0;
        for (byte b : cells) if (ChartCells.known(b)) n++;
        return n;
    }

    /** Same coordinates, cells and colours (ignores version and touched time). */
    public boolean sameCells(ChartRegion other) {
        return other.rx == rx && other.rz == rz && Arrays.equals(other.cells, cells) && Arrays.equals(other.colours, colours);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChartRegion r && r.version == version && r.touched == touched && sameCells(r);
    }

    @Override
    public int hashCode() {
        return (31 * rx + rz) * 31 + Long.hashCode(version);
    }

    @Override
    public String toString() {
        return "ChartRegion[" + rx + "," + rz + " v" + version + " known " + knownCount() + "]";
    }
}

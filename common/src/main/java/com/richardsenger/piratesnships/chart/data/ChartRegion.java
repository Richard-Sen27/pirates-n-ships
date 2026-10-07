package com.richardsenger.piratesnships.chart.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * 64 x 64 chart cells (work package MAP1), one byte each ({@link ChartCells}), at region coordinates
 * {@code (rx, rz)}: cell {@code (cx, cz)} lies in region {@code (cx >> 6, cz >> 6)} at {@code (cx & 63, cz & 63)}.
 * Immutable: the byte array is never changed after construction (copies share it when only {@link #touched}
 * changes). {@link #version} is the {@link ChartData#version()} at the last change of a cell, so a client that has
 * seen a version needs nothing older; {@link #touched} is the game time the owner last sampled anything in it (the
 * cap drops the least recently touched regions first).
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
            Codec.BYTE_BUFFER.fieldOf("cells").forGetter(r -> ByteBuffer.wrap(r.cells.clone()))
    ).apply(i, ChartRegion::fromBuffer));

    /** Saved form; a region whose cell array has the wrong size fails to decode. */
    public static final Codec<ChartRegion> CODEC = RAW.flatXmap(
            r -> r.cells.length == CELLS ? DataResult.success(r) : DataResult.error(() -> "chart region " + r.rx + "," + r.rz + " has " + r.cells.length + " cells"),
            DataResult::success);

    private final int rx;
    private final int rz;
    private final byte[] cells;
    private final long version;
    private final long touched;

    private ChartRegion(int rx, int rz, byte[] cells, long version, long touched) {
        this.rx = rx;
        this.rz = rz;
        this.cells = cells;
        this.version = version;
        this.touched = touched;
    }

    /** A region with a copy of {@code cells} (exactly {@link #CELLS} bytes, row-major: index {@code z * 64 + x}). */
    public static ChartRegion of(int rx, int rz, byte[] cells, long version, long touched) {
        if (cells.length != CELLS) throw new IllegalArgumentException("a chart region has " + CELLS + " cells, got " + cells.length);
        return new ChartRegion(rx, rz, cells.clone(), version, touched);
    }

    /** Takes ownership of {@code cells}: the caller must not change the array afterwards. */
    static ChartRegion adopt(int rx, int rz, byte[] cells, long version, long touched) {
        return new ChartRegion(rx, rz, cells, version, touched);
    }

    private static ChartRegion fromBuffer(int rx, int rz, long version, long touched, ByteBuffer buffer) {
        ByteBuffer b = buffer.duplicate();
        byte[] cells = new byte[b.remaining()];
        b.get(cells);
        return new ChartRegion(rx, rz, cells, version, touched);
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

    /** A copy of all cell bytes. */
    public byte[] copyCells() {
        return cells.clone();
    }

    /** Package code only: the backing array, never to be changed. */
    byte[] cellsUnsafe() {
        return cells;
    }

    public ChartRegion withTouched(long time) {
        return time == touched ? this : new ChartRegion(rx, rz, cells, version, time);
    }

    /** How many cells are known. */
    public int knownCount() {
        int n = 0;
        for (byte b : cells) if (ChartCells.known(b)) n++;
        return n;
    }

    /** Same coordinates and cells (ignores version and touched time). */
    public boolean sameCells(ChartRegion other) {
        return other.rx == rx && other.rz == rz && Arrays.equals(other.cells, cells);
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

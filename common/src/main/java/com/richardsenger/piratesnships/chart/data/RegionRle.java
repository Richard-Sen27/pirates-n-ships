package com.richardsenger.piratesnships.chart.data;

import java.io.ByteArrayOutputStream;

/**
 * Run-length encoding of a region's cells for the network (work package MAP1): pairs of (run length - 1, cell byte),
 * runs of 1..256. Open sea and unexplored parchment are long runs, so a typical region shrinks from 4096 bytes to a
 * few hundred; the worst case (every cell different from its neighbour) is 8192.
 */
public final class RegionRle {

    private RegionRle() {
    }

    public static byte[] encode(byte[] cells) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(256);
        int i = 0;
        while (i < cells.length) {
            byte v = cells[i];
            int run = 1;
            while (i + run < cells.length && cells[i + run] == v && run < 256) run++;
            out.write(run - 1);
            out.write(v);
            i += run;
        }
        return out.toByteArray();
    }

    /** Decodes exactly {@code length} bytes; throws {@link IllegalArgumentException} on malformed input. */
    public static byte[] decode(byte[] rle, int length) {
        if ((rle.length & 1) != 0) throw new IllegalArgumentException("odd run-length data");
        byte[] out = new byte[length];
        int pos = 0;
        for (int i = 0; i < rle.length; i += 2) {
            int run = (rle[i] & 0xFF) + 1;
            if (pos + run > length) throw new IllegalArgumentException("run-length data longer than " + length);
            java.util.Arrays.fill(out, pos, pos + run, rle[i + 1]);
            pos += run;
        }
        if (pos != length) throw new IllegalArgumentException("run-length data has " + pos + " of " + length + " bytes");
        return out;
    }
}

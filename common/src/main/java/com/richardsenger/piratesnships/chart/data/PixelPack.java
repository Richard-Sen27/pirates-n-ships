package com.richardsenger.piratesnships.chart.data;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Packs a map tile's palette pixels for storage and the network (work package MAP2): plain deflate. A tile is drawn
 * with the chart's decorations (diagonal hatching on shallow water, dots on land and beach), which repeat every few
 * pixels: a run-length encoding like {@link RegionRle} gets almost nothing out of them (a 128-pixel coast stays above
 * 10 kB), while deflate's back-references catch the repeating patterns. Decoding is bounded by the expected length, so
 * a hostile packet cannot inflate into more memory than one tile.
 */
public final class PixelPack {

    private PixelPack() {
    }

    public static byte[] pack(byte[] pixels) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(pixels);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, pixels.length / 8));
            byte[] chunk = new byte[4096];
            while (!deflater.finished()) {
                int n = deflater.deflate(chunk);
                out.write(chunk, 0, n);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /** Unpacks exactly {@code length} bytes; throws {@link IllegalArgumentException} on malformed input. */
    public static byte[] unpack(byte[] packed, int length) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(packed);
            byte[] out = new byte[length];
            int pos = 0;
            while (pos < length && !inflater.finished()) {
                int n = inflater.inflate(out, pos, length - pos);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                pos += n;
            }
            if (pos != length) throw new IllegalArgumentException("packed pixels have " + pos + " of " + length + " bytes");
            if (!inflater.finished()) {
                // More data behind the expected length: probe one byte to tell "longer" from "ended exactly here".
                if (inflater.inflate(new byte[1]) > 0 || !inflater.finished()) {
                    throw new IllegalArgumentException("packed pixels longer than " + length + " bytes");
                }
            }
            if (inflater.getRemaining() > 0) throw new IllegalArgumentException("trailing bytes after packed pixels");
            return out;
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("malformed packed pixels: " + e.getMessage(), e);
        } finally {
            inflater.end();
        }
    }
}

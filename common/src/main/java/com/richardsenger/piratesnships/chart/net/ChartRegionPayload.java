package com.richardsenger.piratesnships.chart.net;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.chart.data.ChartRegion;
import com.richardsenger.piratesnships.chart.data.PixelPack;
import com.richardsenger.piratesnships.chart.data.RegionRle;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Arrays;

/**
 * Server to client: one region of the player's chart at its {@code version}, cells run-length encoded
 * ({@link RegionRle}). The server sends a region only when the client has not seen this version yet (delta per
 * region); the client replaces its copy. Decoding checks the cell count, so a malformed payload is rejected.
 *
 * <p>CHART4: the cells' map colours (a byte per cell) follow deflate-packed ({@link PixelPack}: the brightness dither
 * makes runs short, deflate's back-references catch it), empty for a region without colours. At most
 * {@link #MAX_RLE} + {@link #MAX_COLOURS} bytes of data, about 16 kB, far below a custom payload's 1 MiB.
 */
public record ChartRegionPayload(int rx, int rz, long version, byte[] rle, byte[] colours) implements CustomPacketPayload {

    /** The run-length cells' worst case: every cell different from its neighbour. */
    public static final int MAX_RLE = ChartRegion.CELLS * 2;
    /** The packed colours' bound: deflate of incompressible data adds a few bytes per block. */
    public static final int MAX_COLOURS = ChartRegion.CELLS * 2;

    public static final Type<ChartRegionPayload> TYPE = new Type<>(Constants.id("chart_region"));
    public static final StreamCodec<ByteBuf, ChartRegionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChartRegionPayload::rx,
            ByteBufCodecs.VAR_INT, ChartRegionPayload::rz,
            ByteBufCodecs.VAR_LONG, ChartRegionPayload::version,
            ByteBufCodecs.byteArray(MAX_RLE), ChartRegionPayload::rle,
            ByteBufCodecs.byteArray(MAX_COLOURS), ChartRegionPayload::colours,
            ChartRegionPayload::checked);

    /** A region without colours (as before CHART4). */
    public ChartRegionPayload(int rx, int rz, long version, byte[] rle) {
        this(rx, rz, version, rle, new byte[0]);
    }

    public static ChartRegionPayload of(ChartRegion region) {
        byte[] colours = region.copyColours();
        return new ChartRegionPayload(region.rx(), region.rz(), region.version(), RegionRle.encode(region.copyCells()),
                colours == null ? new byte[0] : PixelPack.pack(colours));
    }

    private static ChartRegionPayload checked(int rx, int rz, long version, byte[] rle, byte[] colours) {
        RegionRle.decode(rle, ChartRegion.CELLS);
        if (colours.length > 0) PixelPack.unpack(colours, ChartRegion.CELLS);
        return new ChartRegionPayload(rx, rz, version, rle, colours);
    }

    /** The region as the client keeps it ({@code touched} is not sent). */
    public ChartRegion region() {
        byte[] c = colours.length == 0 ? null : PixelPack.unpack(colours, ChartRegion.CELLS);
        return ChartRegion.of(rx, rz, RegionRle.decode(rle, ChartRegion.CELLS), c, version, 0);
    }

    @Override
    public Type<ChartRegionPayload> type() {
        return TYPE;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChartRegionPayload p && p.rx == rx && p.rz == rz && p.version == version && Arrays.equals(p.rle, rle)
                && Arrays.equals(p.colours, colours);
    }

    @Override
    public int hashCode() {
        return ((31 * rx + rz) * 31 + Arrays.hashCode(rle)) * 31 + Arrays.hashCode(colours);
    }

    @Override
    public String toString() {
        return "ChartRegionPayload[" + rx + "," + rz + " v" + version + ", " + rle.length + " + " + colours.length + " bytes]";
    }
}

package com.richardsenger.piratesnships.chart.net;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.chart.data.ChartRegion;
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
 */
public record ChartRegionPayload(int rx, int rz, long version, byte[] rle) implements CustomPacketPayload {

    public static final Type<ChartRegionPayload> TYPE = new Type<>(Constants.id("chart_region"));
    public static final StreamCodec<ByteBuf, ChartRegionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChartRegionPayload::rx,
            ByteBufCodecs.VAR_INT, ChartRegionPayload::rz,
            ByteBufCodecs.VAR_LONG, ChartRegionPayload::version,
            ByteBufCodecs.byteArray(ChartRegion.CELLS * 2), ChartRegionPayload::rle,
            ChartRegionPayload::checked);

    public static ChartRegionPayload of(ChartRegion region) {
        return new ChartRegionPayload(region.rx(), region.rz(), region.version(), RegionRle.encode(region.copyCells()));
    }

    private static ChartRegionPayload checked(int rx, int rz, long version, byte[] rle) {
        RegionRle.decode(rle, ChartRegion.CELLS);
        return new ChartRegionPayload(rx, rz, version, rle);
    }

    /** The region as the client keeps it ({@code touched} is not sent). */
    public ChartRegion region() {
        return ChartRegion.of(rx, rz, RegionRle.decode(rle, ChartRegion.CELLS), version, 0);
    }

    @Override
    public Type<ChartRegionPayload> type() {
        return TYPE;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChartRegionPayload p && p.rx == rx && p.rz == rz && p.version == version && Arrays.equals(p.rle, rle);
    }

    @Override
    public int hashCode() {
        return (31 * rx + rz) * 31 + Arrays.hashCode(rle);
    }

    @Override
    public String toString() {
        return "ChartRegionPayload[" + rx + "," + rz + " v" + version + ", " + rle.length + " bytes]";
    }
}

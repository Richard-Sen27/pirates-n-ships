package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.ChartData;
import com.richardsenger.piratesnships.chart.data.ChartMarker;
import com.richardsenger.piratesnships.chart.data.ChartMerge;
import com.richardsenger.piratesnships.chart.data.ChartRegion;
import com.richardsenger.piratesnships.chart.data.MarkerIcon;
import com.richardsenger.piratesnships.chart.data.MarkerRules;
import com.richardsenger.piratesnships.chart.data.RegionRle;
import com.richardsenger.piratesnships.chart.data.SampleGrid;
import com.richardsenger.piratesnships.chart.net.ChartMarkerPayload;
import com.richardsenger.piratesnships.chart.net.ChartOpenPayload;
import com.richardsenger.piratesnships.chart.net.ChartRegionPayload;
import com.richardsenger.piratesnships.chart.net.ChartSettings;
import com.richardsenger.piratesnships.chart.net.ChartSettingsPayload;
import com.richardsenger.piratesnships.chart.net.ChartSimplePayloads;
import com.richardsenger.piratesnships.chart.net.ChartStatePayload;
import com.richardsenger.piratesnships.chart.net.ChartViewPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The saved chart and every chart payload survive a round trip (MAP1); malformed region data is rejected. */
class ChartCodecsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ChartData sample() {
        SampleGrid g = SampleGrid.empty(-3, 60, 6, 6);
        CellClass[] all = CellClass.values();
        for (int z = 0; z < 6; z++) {
            for (int x = 0; x < 6; x++) g.set(-3 + x, 60 + z, all[1 + (x + z) % (all.length - 1)]);
        }
        ChartData d = ChartMerge.merge(ChartData.EMPTY.resetCells(4), g, 77, Long.MAX_VALUE).data();
        d = MarkerRules.add(d, 10, -20, MarkerIcon.SKULL, "Skull Rock", 64).data();
        return MarkerRules.add(d, -5, 5, MarkerIcon.PORT, "", 64).data();
    }

    @Test
    void chartDataSurvivesNbt() {
        ChartData d = sample();
        Tag tag = ChartData.CODEC.encodeStart(NbtOps.INSTANCE, d).getOrThrow();
        ChartData back = ChartData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertEquals(d.cellBlocks(), back.cellBlocks());
        assertEquals(d.version(), back.version());
        assertEquals(d.markers(), back.markers());
        assertEquals(d.nextMarkerId(), back.nextMarkerId());
        assertEquals(d.regions(), back.regions(), "regions with cells, version and visit time");
        // cells are stored compactly, as a byte array
        ListTag regions = (ListTag) ((CompoundTag) tag).get("regions");
        assertInstanceOf(ByteArrayTag.class, regions.getCompound(0).get("cells"));
    }

    @Test
    void anEmptyTagIsAnEmptyChart() {
        ChartData d = ChartData.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
        assertEquals(0, d.cellBlocks());
        assertTrue(d.regions().isEmpty());
        assertTrue(d.markers().isEmpty());
    }

    @Test
    void aRegionWithTheWrongCellCountIsRejected() {
        CompoundTag t = new CompoundTag();
        t.putInt("x", 0);
        t.putInt("z", 0);
        t.putLong("version", 1);
        t.putLong("touched", 1);
        t.putByteArray("cells", new byte[10]);
        assertTrue(ChartRegion.CODEC.parse(NbtOps.INSTANCE, t).isError());
    }

    @Test
    void runLengthEncodingRoundTrips() {
        byte[] blank = new byte[ChartRegion.CELLS];
        assertEquals(2 * ((ChartRegion.CELLS + 255) / 256), RegionRle.encode(blank).length, "a blank region is tiny");
        Random r = new Random(42);
        byte[] noisy = new byte[ChartRegion.CELLS];
        for (int i = 0; i < noisy.length; i++) noisy[i] = (byte) (r.nextInt(3) == 0 ? r.nextInt(16) : 1);
        assertArrayEquals(noisy, RegionRle.decode(RegionRle.encode(noisy), ChartRegion.CELLS));
        byte[] worst = new byte[ChartRegion.CELLS];
        for (int i = 0; i < worst.length; i++) worst[i] = (byte) (i & 1);
        assertEquals(2 * ChartRegion.CELLS, RegionRle.encode(worst).length);
        assertArrayEquals(worst, RegionRle.decode(RegionRle.encode(worst), ChartRegion.CELLS));
    }

    @Test
    void malformedRunLengthDataIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RegionRle.decode(new byte[]{5}, 16), "odd length");
        assertThrows(IllegalArgumentException.class, () -> RegionRle.decode(new byte[]{(byte) 255, 1}, 16), "too long");
        assertThrows(IllegalArgumentException.class, () -> RegionRle.decode(new byte[]{3, 1}, 16), "too short");
    }

    private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buf = Unpooled.buffer();
        codec.encode(buf, value);
        T back = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "everything was read");
        return back;
    }

    @Test
    void payloadsRoundTrip() {
        ChartData d = sample();
        ChartSettings s = new ChartSettings(true, true, false, 4, 64);
        assertEquals(new ChartSettingsPayload(s), roundTrip(ChartSettingsPayload.CODEC, new ChartSettingsPayload(s)));
        ChartOpenPayload open = new ChartOpenPayload(s, d.markers());
        assertEquals(open, roundTrip(ChartOpenPayload.CODEC, open));
        ChartStatePayload state = new ChartStatePayload(d.markers(),
                List.of(new ChartStatePayload.OtherPlayer("Anne", -100, 2000, -91.5f)), Optional.of("message.x"));
        assertEquals(state, roundTrip(ChartStatePayload.CODEC, state));
        ChartStatePayload quiet = new ChartStatePayload(List.of(), List.of(), Optional.empty());
        assertEquals(quiet, roundTrip(ChartStatePayload.CODEC, quiet));
        for (ChartRegion r : d.regions().values()) {
            ChartRegionPayload p = ChartRegionPayload.of(r);
            ChartRegionPayload back = roundTrip(ChartRegionPayload.CODEC, p);
            assertEquals(p, back);
            assertTrue(back.region().sameCells(r));
            assertEquals(r.version(), back.region().version());
        }
        ChartViewPayload view = new ChartViewPayload(-7_500_000, 12, 300);
        assertEquals(view, roundTrip(ChartViewPayload.CODEC, view));
        for (ChartMarkerPayload m : List.of(ChartMarkerPayload.add(1, -2, MarkerIcon.DANGER, "Reef"),
                ChartMarkerPayload.edit(4, 30_000_000, -30_000_000, MarkerIcon.ANCHOR, "Bay"), ChartMarkerPayload.remove(9))) {
            assertEquals(m, roundTrip(ChartMarkerPayload.CODEC, m));
        }
        assertEquals(ChartSimplePayloads.RequestOpen.INSTANCE, roundTrip(ChartSimplePayloads.RequestOpen.CODEC, ChartSimplePayloads.RequestOpen.INSTANCE));
        assertEquals(ChartSimplePayloads.Close.INSTANCE, roundTrip(ChartSimplePayloads.Close.CODEC, ChartSimplePayloads.Close.INSTANCE));
        ChartMarker m = new ChartMarker(3, 1, 2, MarkerIcon.X, "Ünïcødé ⚓");
        assertEquals(m, roundTrip(ChartMarker.STREAM_CODEC, m));
    }

    @Test
    void aRegionPayloadWithBrokenCellsFailsToDecode() {
        ByteBuf buf = Unpooled.buffer();
        ChartRegionPayload.CODEC.encode(buf, new ChartRegionPayload(0, 0, 1, new byte[]{0, 1}));
        assertThrows(IllegalArgumentException.class, () -> ChartRegionPayload.CODEC.decode(buf));
    }

    @Test
    void theViewRadiusIsClamped() {
        assertEquals(ChartViewPayload.MAX_RADIUS, new ChartViewPayload(0, 0, Integer.MAX_VALUE).clampedRadius());
        assertEquals(0, new ChartViewPayload(0, 0, -5).clampedRadius());
    }
}

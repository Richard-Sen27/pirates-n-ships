package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.data.ChartCells;
import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.MapTileDrawing;
import com.richardsenger.piratesnships.chart.data.MarkerIcon;
import com.richardsenger.piratesnships.chart.data.RegionRle;
import com.richardsenger.piratesnships.chart.data.TileMarker;
import com.richardsenger.piratesnships.chart.net.ChartOpenPayload;
import com.richardsenger.piratesnships.chart.net.ChartSettings;
import com.richardsenger.piratesnships.chart.net.DrawTilePayload;
import com.richardsenger.piratesnships.chart.net.TileTarget;
import com.richardsenger.piratesnships.chart.render.MapTileRaster;
import com.richardsenger.piratesnships.chart.tile.MapTileRules;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The map tile's data (MAP2): the drawing survives NBT and the network run-length encoded, the payloads, the rules. */
class MapTileDataTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A 128-pixel coast: land, an inked coast, sea, unknown parchment at the bottom. */
    static MapTileDrawing sample() {
        byte[] px = MapTileRaster.draw((cx, cz) -> cz > 100 ? 0 : ChartCells.of(cx < 40 + cz / 3 ? CellClass.LAND : CellClass.SHALLOW_WATER,
                cx == 39 + cz / 3), -10, 5, 128);
        return new MapTileDrawing(128, px, -10, 5, 4, "Anne Bonny", 42,
                List.of(new TileMarker(MarkerIcon.SKULL, 3, 120, "Skull Rock"), new TileMarker(MarkerIcon.PORT, 127, 0, "")));
    }

    private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buf = Unpooled.buffer();
        codec.encode(buf, value);
        T back = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "everything read");
        return back;
    }

    @Test
    void rleRoundTripOfATile() {
        MapTileDrawing d = sample();
        byte[] rle = d.rle();
        assertTrue(rle.length < 128 * 128 / 4, "a coast compresses well: " + rle.length + " bytes");
        assertArrayEquals(d.pixels(), RegionRle.decode(rle, 128 * 128));
        assertEquals(d, MapTileDrawing.fromRle(128, rle, -10, 5, 4, "Anne Bonny", 42, d.markers()));
    }

    @Test
    void drawingSurvivesNbt() {
        MapTileDrawing d = sample();
        Tag tag = MapTileDrawing.CODEC.encodeStart(NbtOps.INSTANCE, d).getOrThrow();
        MapTileDrawing back = MapTileDrawing.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertEquals(d, back);
        assertEquals(d.hashCode(), back.hashCode());
        assertEquals(-40, back.minX());
        assertEquals((5 + 128) * 4, back.maxZ());
    }

    @Test
    void badStoredPixelsAreACodecError() {
        CompoundTag tag = (CompoundTag) MapTileDrawing.CODEC.encodeStart(NbtOps.INSTANCE, sample()).getOrThrow();
        tag.putByteArray("pixels", new byte[]{5, 1});
        assertTrue(MapTileDrawing.CODEC.parse(NbtOps.INSTANCE, tag).error().isPresent(), "too few pixels");
        tag.putInt("size", 4000);
        assertTrue(MapTileDrawing.CODEC.parse(NbtOps.INSTANCE, tag).error().isPresent(), "absurd size");
    }

    @Test
    void drawingSurvivesTheNetwork() {
        MapTileDrawing d = sample();
        assertEquals(d, roundTrip(MapTileDrawing.STREAM_CODEC, d));
        ByteBuf bad = Unpooled.buffer();
        net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(bad, 128);
        net.minecraft.network.codec.ByteBufCodecs.BYTE_ARRAY.encode(bad, new byte[]{0, 1});
        for (int i = 0; i < 3; i++) net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(bad, 0);
        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.encode(bad, "");
        net.minecraft.network.codec.ByteBufCodecs.VAR_LONG.encode(bad, 0L);
        net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(bad, 0);
        assertThrows(RuntimeException.class, () -> MapTileDrawing.STREAM_CODEC.decode(bad), "malformed pixels are rejected");
    }

    @Test
    void equalityLooksAtThePixels() {
        MapTileDrawing a = sample();
        byte[] px = a.pixels();
        px[500] = (byte) ((px[500] + 1) % MapTileRaster.paletteSize());
        MapTileDrawing b = new MapTileDrawing(128, px, -10, 5, 4, "Anne Bonny", 42, a.markers());
        assertNotEquals(a, b);
        px[0] = 9;
        assertNotEquals(9, a.pixel(0, 0), "the drawing keeps its own copy");
        assertThrows(IllegalArgumentException.class, () -> new MapTileDrawing(128, new byte[10], 0, 0, 4, "", 0, List.of()));
    }

    @Test
    void payloadsSurviveTheNetwork() {
        DrawTilePayload draw = new DrawTilePayload(new BlockPos(10, -3, 99), -1234, 567, true);
        assertEquals(draw, roundTrip(DrawTilePayload.CODEC, draw));
        TileTarget target = new TileTarget(new BlockPos(1, 2, 3), 128, true, -70, 80);
        ChartOpenPayload open = new ChartOpenPayload(ChartSettings.DEFAULT, List.of(), Optional.of(target));
        assertEquals(open, roundTrip(ChartOpenPayload.CODEC, open));
        ChartOpenPayload plain = new ChartOpenPayload(ChartSettings.DEFAULT, List.of());
        assertEquals(Optional.empty(), roundTrip(ChartOpenPayload.CODEC, plain).tile());
    }

    @Test
    void rulesRefuseInOrder() {
        MapTileRules.Request ok = new MapTileRules.Request(true, true, true, true, true, true, false, true, true);
        assertEquals(MapTileRules.Refusal.NONE, MapTileRules.check(ok));
        assertEquals(MapTileRules.Refusal.CHARTS_DISABLED, MapTileRules.check(new MapTileRules.Request(false, false, false, false, true, false, true, false, false)));
        assertEquals(MapTileRules.Refusal.TILES_DISABLED, MapTileRules.check(new MapTileRules.Request(true, false, true, true, true, true, false, true, true)));
        assertEquals(MapTileRules.Refusal.NO_TILE, MapTileRules.check(new MapTileRules.Request(true, true, false, false, true, true, false, true, true)));
        assertEquals(MapTileRules.Refusal.TOO_FAR, MapTileRules.check(new MapTileRules.Request(true, true, true, false, true, true, false, true, true)));
        assertEquals(MapTileRules.Refusal.NEEDS_CHART, MapTileRules.check(new MapTileRules.Request(true, true, true, true, true, false, false, true, true)));
        assertEquals(MapTileRules.Refusal.NONE, MapTileRules.check(new MapTileRules.Request(true, true, true, true, false, false, false, true, true)),
                "no chart needed when require_chart_item is off");
        assertEquals(MapTileRules.Refusal.NONE, MapTileRules.check(new MapTileRules.Request(true, true, true, true, true, true, true, true, true)), "redraw allowed");
        assertEquals(MapTileRules.Refusal.PERMANENT, MapTileRules.check(new MapTileRules.Request(true, true, true, true, true, true, true, false, true)));
        assertEquals(MapTileRules.Refusal.NONE, MapTileRules.check(new MapTileRules.Request(true, true, true, true, true, true, false, false, true)),
                "a blank tile can be drawn even when drawings are permanent");
        assertEquals(MapTileRules.Refusal.OUT_OF_WORLD, MapTileRules.check(new MapTileRules.Request(true, true, true, true, true, true, false, true, false)));
    }
}

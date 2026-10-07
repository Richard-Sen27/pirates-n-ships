package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.data.ChartCells;
import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.MapTileDrawing;
import com.richardsenger.piratesnships.chart.data.MarkerIcon;
import com.richardsenger.piratesnships.chart.data.PixelPack;
import com.richardsenger.piratesnships.chart.data.TileMarker;
import com.richardsenger.piratesnships.chart.net.ChartOpenPayload;
import com.richardsenger.piratesnships.chart.net.ClearBoardPayload;
import com.richardsenger.piratesnships.chart.tile.BoardRules;
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
import java.util.UUID;
import com.richardsenger.piratesnships.chart.data.BoardSlice;

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
    void packedRoundTripOfATile() {
        MapTileDrawing d = sample();
        byte[] packed = d.packed();
        // The hatched shallows and dotted land repeat every few pixels: deflate must get well under a quarter.
        assertTrue(packed.length < 128 * 128 / 4, "a coast compresses well: " + packed.length + " bytes");
        assertArrayEquals(d.pixels(), PixelPack.unpack(packed, 128 * 128));
        assertEquals(d, MapTileDrawing.fromPacked(128, packed, -10, 5, 4, "Anne Bonny", 42, d.markers()));
    }

    @Test
    void malformedPackedPixelsAreRejected() {
        byte[] packed = sample().packed();
        assertThrows(IllegalArgumentException.class, () -> PixelPack.unpack(packed, 128 * 128 - 1), "longer than expected");
        assertThrows(IllegalArgumentException.class, () -> PixelPack.unpack(packed, 128 * 128 + 1), "shorter than expected");
        assertThrows(IllegalArgumentException.class, () -> PixelPack.unpack(new byte[]{1, 2, 3, 4}, 16), "not deflate");
        byte[] blank = new byte[256 * 256];
        assertTrue(PixelPack.pack(blank).length < 200, "a blank tile is tiny");
        assertArrayEquals(blank, PixelPack.unpack(PixelPack.pack(blank), blank.length));
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
    void zoomAndBoardSliceSurviveNbtAndTheNetwork() {
        MapTileDrawing a = sample();
        assertEquals(1, a.zoom(), "MAP2 draws at zoom 1");
        assertTrue(a.board().isEmpty(), "and single tiles");
        BoardSlice slice = new BoardSlice(new UUID(1, 2), 1, 0, 3, 2);
        MapTileDrawing b = new MapTileDrawing(128, a.pixels(), -10, 5, 4, "Anne Bonny", 42, a.markers(), 2, Optional.of(slice));
        assertNotEquals(a, b);
        assertEquals((-10 + 256) * 4, b.maxX(), "the area grows with the zoom");
        assertEquals(b, MapTileDrawing.CODEC.parse(NbtOps.INSTANCE, MapTileDrawing.CODEC.encodeStart(NbtOps.INSTANCE, b).getOrThrow()).getOrThrow());
        assertEquals(b, roundTrip(MapTileDrawing.STREAM_CODEC, b));
        CompoundTag old = (CompoundTag) MapTileDrawing.CODEC.encodeStart(NbtOps.INSTANCE, a).getOrThrow();
        assertTrue(!old.contains("board"), "a single tile stores no board");
        assertThrows(IllegalArgumentException.class, () -> new BoardSlice(new UUID(0, 0), 3, 0, 3, 1), "slice outside its board");
        assertThrows(IllegalArgumentException.class, () -> new MapTileDrawing(128, a.pixels(), 0, 0, 4, "", 0, List.of(), 0, Optional.empty()));
        assertEquals(MapTileRaster.known(a.pixel(0, 0)), a.known(0, 0));
        assertTrue(!a.known(0, 127), "the unknown bottom rows are not known");
    }

    /** MAP3: the slices of one board, built from the board's area, are consistent and survive NBT, the network and the item. */
    @Test
    void boardSlicesAreConsistentAndSurviveTheItem() {
        MapTileDrawing a = sample();
        UUID id = new UUID(7, 9);
        BoardRules.Area area = new BoardRules.Area(-10, 5, 128, 2, 4);
        List<BoardRules.Slot> slots = new java.util.ArrayList<>();
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                MapTileDrawing d = new MapTileDrawing(128, a.pixels(), area.sliceMinCx(col), area.sliceMinCz(row), 4, "Mary Read", 50,
                        List.of(new TileMarker(MarkerIcon.X, -2, 3, "seam")), 2, Optional.of(new BoardSlice(id, col, row, 3, 2)), true);
                assertEquals(d, MapTileDrawing.CODEC.parse(NbtOps.INSTANCE, MapTileDrawing.CODEC.encodeStart(NbtOps.INSTANCE, d).getOrThrow()).getOrThrow());
                assertEquals(d, roundTrip(MapTileDrawing.STREAM_CODEC, d), "the item component's network form");
                assertTrue(d.updated(), "the update flag survives");
                slots.add(new BoardRules.Slot(col, row, d));
            }
        }
        BoardRules.Existing e = BoardRules.existing(slots, 3, 2);
        assertEquals(BoardRules.State.INTACT, e.state());
        assertEquals(area, e.area());
        // the slice in column 2 starts 2 * 128 * 2 cells east of the board
        assertEquals(-10 + 512, slots.get(2).drawing().minCx());
        assertEquals(slots.get(2).drawing().minX(), (long) (-10 + 512) * 4);
        // an older drawing without the flag reads as a first draw
        CompoundTag tag = (CompoundTag) MapTileDrawing.CODEC.encodeStart(NbtOps.INSTANCE, slots.get(0).drawing()).getOrThrow();
        tag.remove("updated");
        assertTrue(!MapTileDrawing.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().updated());
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
        int before = b.pixel(0, 0);
        px[0] = (byte) ((before + 1) % MapTileRaster.paletteSize());
        assertEquals(before, b.pixel(0, 0), "the drawing keeps its own copy of the array it was built from");
        b.pixels()[0] = px[0];
        assertEquals(before, b.pixel(0, 0), "pixels() hands out a copy");
        assertThrows(IllegalArgumentException.class, () -> new MapTileDrawing(128, new byte[10], 0, 0, 4, "", 0, List.of()));
    }

    @Test
    void payloadsSurviveTheNetwork() {
        DrawTilePayload draw = new DrawTilePayload(new BlockPos(10, -3, 99), -1234, 567, true);
        assertEquals(draw, roundTrip(DrawTilePayload.CODEC, draw));
        assertEquals(1, draw.zoom(), "MAP2's draw is zoom 1");
        DrawTilePayload zoomed = new DrawTilePayload(new BlockPos(10, -3, 99), -1234, 567, 6, false, false);
        assertEquals(zoomed, roundTrip(DrawTilePayload.CODEC, zoomed));
        DrawTilePayload update = DrawTilePayload.update(new BlockPos(4, 5, 6), true);
        assertEquals(update, roundTrip(DrawTilePayload.CODEC, update));
        assertTrue(update.update());
        ClearBoardPayload clear = new ClearBoardPayload(new BlockPos(-8, 70, 12));
        assertEquals(clear, roundTrip(ClearBoardPayload.CODEC, clear));
        TileTarget target = new TileTarget(new BlockPos(1, 2, 3), 128, BoardRules.State.INTACT, -70, 80, 3, 2, 4, 8, false, 1, 8);
        assertEquals(target, roundTrip(TileTarget.STREAM_CODEC, target));
        assertTrue(target.drawn() && target.updatable());
        assertEquals(6, target.tiles());
        ChartOpenPayload open = new ChartOpenPayload(ChartSettings.DEFAULT, List.of(), Optional.of(target));
        assertEquals(open, roundTrip(ChartOpenPayload.CODEC, open));
        ChartOpenPayload plain = new ChartOpenPayload(ChartSettings.DEFAULT, List.of());
        assertEquals(Optional.empty(), roundTrip(ChartOpenPayload.CODEC, plain).tile());
    }

    @Test
    void rulesRefuseInOrder() {
        MapTileRules.Request ok = new MapTileRules.Request(true, true, true, true, true, true, false, true, true, true);
        assertEquals(MapTileRules.Refusal.NONE, MapTileRules.check(ok));
        assertEquals(MapTileRules.Refusal.CHARTS_DISABLED, MapTileRules.check(new MapTileRules.Request(false, false, false, false, true, false, true, false, false, true)));
        assertEquals(MapTileRules.Refusal.TILES_DISABLED, MapTileRules.check(new MapTileRules.Request(true, false, true, true, true, true, false, true, true, true)));
        assertEquals(MapTileRules.Refusal.NO_TILE, MapTileRules.check(new MapTileRules.Request(true, true, false, false, true, true, false, true, true, true)));
        assertEquals(MapTileRules.Refusal.TOO_FAR, MapTileRules.check(new MapTileRules.Request(true, true, true, false, true, true, false, true, true, true)));
        assertEquals(MapTileRules.Refusal.NEEDS_CHART, MapTileRules.check(new MapTileRules.Request(true, true, true, true, true, false, false, true, true, true)));
        assertEquals(MapTileRules.Refusal.NONE, MapTileRules.check(new MapTileRules.Request(true, true, true, true, false, false, false, true, true, true)),
                "no chart needed when require_chart_item is off");
        assertEquals(MapTileRules.Refusal.NONE, MapTileRules.check(new MapTileRules.Request(true, true, true, true, true, true, true, true, true, true)), "redraw allowed");
        assertEquals(MapTileRules.Refusal.PERMANENT, MapTileRules.check(new MapTileRules.Request(true, true, true, true, true, true, true, false, true, true)));
        assertEquals(MapTileRules.Refusal.NONE, MapTileRules.check(new MapTileRules.Request(true, true, true, true, true, true, false, false, true, true)),
                "a blank tile can be drawn even when drawings are permanent");
        assertEquals(MapTileRules.Refusal.OUT_OF_WORLD, MapTileRules.check(new MapTileRules.Request(true, true, true, true, true, true, false, true, false, true)));
    }
}

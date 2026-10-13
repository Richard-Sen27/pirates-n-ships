package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.ChartCells;
import com.richardsenger.piratesnships.chart.data.ChartData;
import com.richardsenger.piratesnships.chart.data.ChartMerge;
import com.richardsenger.piratesnships.chart.data.ChartRegion;
import com.richardsenger.piratesnships.chart.data.SampleGrid;
import com.richardsenger.piratesnships.chart.net.ChartRegionPayload;
import com.richardsenger.piratesnships.chart.render.CellLookup;
import com.richardsenger.piratesnships.chart.render.ChartRaster;
import com.richardsenger.piratesnships.chart.render.ChartStyle;
import com.richardsenger.piratesnships.chart.render.ChartZoomOut;
import com.richardsenger.piratesnships.chart.render.MapTileRaster;
import com.richardsenger.piratesnships.chart.sample.MapColours;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.MapColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CHART4: the cells' map colours through the chart: merged with the classes, saved (old saves without colours still
 * load), sent within the payload budget, downsampled for the far tiles, and drawn only in the coloured style.
 */
class ChartColoursTest {

    static final int WOOD = MapColor.WOOD.getPackedId(MapColor.Brightness.NORMAL) & 0xFF;
    static final int WOOD_HIGH = MapColor.WOOD.getPackedId(MapColor.Brightness.HIGH) & 0xFF;
    static final int GRASS = MapColor.GRASS.getPackedId(MapColor.Brightness.NORMAL) & 0xFF;
    static final int PATH = MapColor.DIRT.getPackedId(MapColor.Brightness.NORMAL) & 0xFF;
    static final int WATER = MapColor.WATER.getPackedId(MapColor.Brightness.LOW) & 0xFF;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Land with a village (wood roofs, paths, fields) west of x = 12, sea east of it; 24 x 24 cells from (0, 0). */
    static SampleGrid village(boolean colours) {
        SampleGrid g = SampleGrid.empty(0, 0, 24, 24);
        for (int z = 0; z < 24; z++) {
            for (int x = 0; x < 24; x++) {
                if (x >= 12) {
                    g.set(x, z, x >= 14 ? CellClass.DEEP_WATER : CellClass.SHALLOW_WATER, colours ? WATER : 0);
                } else if (x == 11) {
                    g.set(x, z, CellClass.BEACH, colours ? MapColor.SAND.getPackedId(MapColor.Brightness.NORMAL) & 0xFF : 0);
                } else {
                    int c = (x % 4 == 1 && z % 4 == 1) || (x % 4 == 2 && z % 4 == 1) ? ((x + z) % 3 == 0 ? WOOD_HIGH : WOOD)
                            : z % 4 == 3 || x == 4 ? PATH : GRASS;
                    g.set(x, z, CellClass.LAND, colours ? c : 0);
                }
            }
        }
        return g;
    }

    static ChartData merged(SampleGrid g) {
        return ChartMerge.merge(ChartData.EMPTY.resetCells(4), g, 1, Long.MAX_VALUE).data();
    }

    @Test
    void mergeKeepsColoursAndAResampleWithNewColoursIsAChange() {
        ChartData d = merged(village(true));
        assertEquals(WOOD, ChartCells.colour(d.wide(1, 1)), "a roof");
        assertEquals(CellClass.LAND, ChartCells.cellClass(d.wide(1, 1)));
        assertEquals(d.cell(1, 1) & 0xFF, ChartCells.narrow(d.wide(1, 1)), "the wide cell's low byte is the cell");
        ChartMerge.Result same = ChartMerge.merge(d, village(true), 2, Long.MAX_VALUE);
        assertTrue(same.changedRegions().isEmpty(), "the same colours again change nothing");
        SampleGrid newRoof = SampleGrid.empty(1, 1, 1, 1);
        newRoof.set(1, 1, CellClass.LAND, PATH);
        ChartMerge.Result changed = ChartMerge.merge(d, newRoof, 3, Long.MAX_VALUE);
        assertEquals(1, changed.changedRegions().size(), "only the colour changed, still a new version");
        assertEquals(PATH, ChartCells.colour(changed.data().wide(1, 1)));
        assertTrue(changed.data().version() > d.version());
    }

    @Test
    void regionsWithoutColoursStayInkUntilResampled() {
        ChartData old = merged(village(false));
        ChartRegion r = old.regions().values().iterator().next();
        // a pre-CHART4 region: what merging a grid without colours gives once saved and loaded without the field
        CompoundTag tag = (CompoundTag) ChartRegion.CODEC.encodeStart(NbtOps.INSTANCE, ChartRegion.of(r.rx(), r.rz(), r.copyCells(), 1, 1)).getOrThrow();
        assertFalse(tag.contains("colours"), "no colours field without colours");
        ChartRegion loaded = ChartRegion.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertFalse(loaded.hasColours());
        assertEquals(0, loaded.colour(1, 1));
        ChartData resampled = ChartMerge.merge(new ChartData(4, Map.of(loaded.key(), loaded), java.util.List.of(), 1, 1), village(true), 5, Long.MAX_VALUE).data();
        assertEquals(WOOD, ChartCells.colour(resampled.wide(1, 1)), "visited again: coloured");
    }

    @Test
    void coloursSurviveNbtAndAWrongLengthIsRejected() {
        ChartData d = merged(village(true));
        Tag tag = ChartData.CODEC.encodeStart(NbtOps.INSTANCE, d).getOrThrow();
        ChartData back = ChartData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertEquals(d.regions(), back.regions());
        assertEquals(WOOD, ChartCells.colour(back.wide(1, 1)));
        CompoundTag bad = (CompoundTag) ChartRegion.CODEC.encodeStart(NbtOps.INSTANCE, d.regions().values().iterator().next()).getOrThrow();
        bad.putByteArray("colours", new byte[10]);
        assertTrue(ChartRegion.CODEC.parse(NbtOps.INSTANCE, bad).isError());
    }

    private static int encodedSize(ChartRegionPayload p) {
        ByteBuf buf = Unpooled.buffer();
        ChartRegionPayload.CODEC.encode(buf, p);
        int n = buf.readableBytes();
        assertEquals(p, ChartRegionPayload.CODEC.decode(buf));
        return n;
    }

    /**
     * The region payload with colours: the worst case (every cell and colour different from its neighbour) stays far
     * below a custom payload's 1 MiB; a realistic coloured region costs little more than its cells.
     */
    @Test
    void theRegionPayloadCarriesColoursWithinItsBudget() {
        Random r = new Random(4);
        byte[] cells = new byte[ChartRegion.CELLS];
        byte[] colours = new byte[ChartRegion.CELLS];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = (byte) (1 + (i & 1));
            colours[i] = (byte) r.nextInt(256);
        }
        ChartRegion worst = ChartRegion.of(-3, 7, cells, colours, 9, 0);
        ChartRegionPayload wp = ChartRegionPayload.of(worst);
        int worstSize = encodedSize(wp);
        assertTrue(worstSize <= 4 * ChartRegion.CELLS + 32, "worst case " + worstSize + " bytes");
        assertTrue(worstSize < 1_048_576 / 32, "a tick's four regions stay far under 1 MiB: " + worstSize);
        assertTrue(wp.region().sameCells(worst), "cells and colours arrive");

        ChartData village = merged(village(true));
        ChartRegion vr = village.regions().get(ChartRegion.key(0, 0));
        ChartRegionPayload vp = ChartRegionPayload.of(vr);
        int withColours = encodedSize(vp);
        int without = encodedSize(new ChartRegionPayload(vr.rx(), vr.rz(), vr.version(), vp.rle()));
        assertTrue(withColours - without < 600, "a village region's colours cost " + (withColours - without) + " bytes");
        assertArrayEquals(vr.copyColours(), vp.region().copyColours());
        assertNull(new ChartRegionPayload(0, 0, 1, vp.rle()).region().copyColours(), "no colours sent: none kept");
    }

    @Test
    void aBrokenColourPackIsRejected() {
        ByteBuf buf = Unpooled.buffer();
        ChartRegionPayload ok = ChartRegionPayload.of(ChartRegion.of(0, 0, new byte[ChartRegion.CELLS], 1, 0));
        ChartRegionPayload.CODEC.encode(buf, new ChartRegionPayload(0, 0, 1, ok.rle(), new byte[]{1, 2, 3}));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> ChartRegionPayload.CODEC.decode(buf));
    }

    /** Far tiles and zoomed pixels: the most common colour of the land cells (ties: lower id), water pixels none. */
    @Test
    void downsamplingKeepsTheDominantLandColour() {
        int land = ChartCells.of(CellClass.LAND, false);
        int deep = ChartCells.of(CellClass.DEEP_WATER, false);
        Map<Long, Integer> m = new HashMap<>();
        CellLookup cells = (cx, cz) -> m.getOrDefault(((long) cx << 32) | (cz & 0xFFFFFFFFL), 0);
        java.util.function.BiConsumer<int[], Integer> put = (xz, v) -> m.put(((long) xz[0] << 32) | (xz[1] & 0xFFFFFFFFL), v);
        put.accept(new int[]{0, 0}, ChartCells.wide(land, WOOD));
        put.accept(new int[]{1, 0}, ChartCells.wide(land, WOOD_HIGH));
        put.accept(new int[]{0, 1}, ChartCells.wide(land, GRASS));
        put.accept(new int[]{1, 1}, ChartCells.wide(deep, WATER));
        int down = MapTileRaster.downsample(cells, 0, 0, 2);
        assertEquals(CellClass.LAND, ChartCells.cellClass(down));
        assertEquals(MapColor.WOOD.id, MapColours.id(ChartCells.colour(down)), "two wood cells beat one grass and the water");
        put.accept(new int[]{0, 1}, ChartCells.wide(deep, WATER));
        put.accept(new int[]{1, 0}, ChartCells.wide(deep, WATER));
        int sea = MapTileRaster.downsample(cells, 0, 0, 2);
        assertEquals(CellClass.DEEP_WATER, ChartCells.cellClass(sea));
        assertEquals(0, ChartCells.colour(sea), "a water pixel carries no colour");
        assertEquals(ChartCells.wide(land, WOOD), MapTileRaster.downsample(cells, 0, 0, 1), "zoom 1 is the cell");
        assertEquals(0, ChartCells.colour(MapTileRaster.downsample(cells, 1, 1, 1)), "zoom 1 water: no colour");
        // ties: the lower id; the colour's most common step
        int[] counts = new int[256];
        counts[GRASS] = 2;
        counts[WOOD] = 1;
        counts[WOOD_HIGH] = 1;
        assertEquals(GRASS, colourOf(counts), "grass (id 1) ties wood (id 13): lower id");
        counts[WOOD_HIGH] = 2;
        assertEquals(WOOD_HIGH, colourOf(counts), "wood wins and its most common step");
    }

    private static int colourOf(int[] counts) {
        Map<Long, Integer> m = new HashMap<>();
        int i = 0;
        int land = ChartCells.of(CellClass.LAND, false);
        for (int c = 0; c < 256; c++) {
            for (int k = 0; k < counts[c]; k++, i++) m.put((long) i, ChartCells.wide(land, c));
        }
        int n = i;
        int side = (int) Math.ceil(Math.sqrt(n));
        CellLookup cells = (cx, cz) -> {
            int idx = cz * side + cx;
            return cx < side && idx < n ? m.get((long) idx) : 0;
        };
        return ChartCells.colour(MapTileRaster.downsample(cells, 0, 0, side));
    }

    @Test
    void farTilesCarryTheColoursInTheColouredStyleOnly() {
        ChartData d = merged(village(true));
        int[] ink = ChartZoomOut.render(k -> d.regions().get(k), 0, 0, 2, ChartStyle.INK);
        int[] coloured = ChartZoomOut.render(k -> d.regions().get(k), 0, 0, 2, ChartStyle.COLOURED);
        assertArrayEquals(ink, ChartZoomOut.render(k -> d.regions().get(k), 0, 0, 2), "ink is the old far tile");
        assertNotEquals(ink[2 * 64 + 2], coloured[2 * 64 + 2], "a land pixel differs");
        assertEquals(ink[2 * 64 + 10], coloured[2 * 64 + 10], "a sea pixel is the same");
    }

    /** The coloured style paints land with the softened block colour; ink ignores the colours; the sea keeps its wash. */
    @Test
    void onlyTheColouredStyleDrawsTheColours() {
        ChartData coloured = merged(village(true));
        ChartData plain = merged(village(false));
        CellLookup wide = coloured::wide;
        int[] ink = ChartRaster.render(wide, 0, 0, 24, 24, 2, ChartStyle.INK);
        assertArrayEquals(ChartRaster.render(CellLookup.of(plain), 0, 0, 24, 24, 2), ink, "ink without built cells ignores the colours");
        int[] col = ChartRaster.render(wide, 0, 0, 24, 24, 2, ChartStyle.COLOURED);
        int[] colPlain = ChartRaster.render(plain::wide, 0, 0, 24, 24, 2, ChartStyle.COLOURED);
        assertArrayEquals(ink, colPlain, "cells without colours draw in ink");
        int w = 48;
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 48; x++) {
                int cx = x / 2;
                int cz = y / 2;
                if (cx >= 12) assertEquals(ink[y * w + x], col[y * w + x], "the sea keeps its wash at " + x + "," + y);
            }
        }
        // a roof cell (1, 1) away from the coast: wood, softened
        int roof = col[2 * w + 2];
        int expect = ChartRaster.softened(WOOD);
        assertTrue(roof == expect || roof == darkened(expect), "roof pixel " + Integer.toHexString(roof));
        assertNotEquals(ink[2 * w + 2], roof);
        int r = (roof >> 16) & 0xFF, g = (roof >> 8) & 0xFF, b = roof & 0xFF;
        assertTrue(r > g && g > b, "wood reads brown: " + Integer.toHexString(roof));
        int grass = col[0];
        assertTrue(((grass >> 8) & 0xFF) > ((grass >> 16) & 0xFF), "grass reads green: " + Integer.toHexString(grass));
        // the coast stroke stays ink
        assertEquals(ChartRaster.INK, col[2 * w + 11 * 2 + 1], "the beach's sea side is inked");
    }

    private static int darkened(int argb) {
        int r = ((argb >> 16) & 0xFF) * 85 / 100;
        int g = ((argb >> 8) & 0xFF) * 85 / 100;
        int b = (argb & 0xFF) * 85 / 100;
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /** The softened colours keep their hue but sit closer to the parchment than vanilla's. */
    @Test
    void softenedColoursAreLessSaturatedThanVanillas() {
        for (MapColor c : new MapColor[]{MapColor.GRASS, MapColor.WOOD, MapColor.COLOR_RED, MapColor.DIRT, MapColor.SAND}) {
            int packed = c.getPackedId(MapColor.Brightness.NORMAL) & 0xFF;
            int v = MapColours.argb(packed);
            int s = ChartRaster.softened(packed);
            assertEquals(0xD8, s >>> 24, "laid on the parchment, partly transparent");
            assertTrue(saturation(s) <= saturation(v) + 1e-6, c.id + ": saturation " + saturation(s) + " vs " + saturation(v));
        }
        assertEquals(0, ChartRaster.softened(0));
    }

    private static double saturation(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        return max == 0 ? 0 : (max - min) / (double) max;
    }

    /** The client redraws its chart when the server names another style, and only then. */
    @Test
    void aNewStyleRedrawsTheClientsChart() {
        java.util.List<Long> heard = new java.util.ArrayList<>();
        com.richardsenger.piratesnships.chart.client.ClientChart.reset();
        com.richardsenger.piratesnships.chart.client.ClientChart.setRegionListener(heard::add);
        try {
            com.richardsenger.piratesnships.chart.net.ChartSettings base = com.richardsenger.piratesnships.chart.net.ChartSettings.DEFAULT;
            assertEquals(ChartStyle.INK, base.style(), "the client assumes the default style");
            com.richardsenger.piratesnships.chart.client.ClientChart.acceptSettings(base);
            assertTrue(heard.isEmpty(), "the same style: nothing redrawn");
            com.richardsenger.piratesnships.chart.client.ClientChart.acceptSettings(new com.richardsenger.piratesnships.chart.net.ChartSettings(
                    true, false, 4, 0, 3, ChartStyle.COLOURED));
            assertEquals(java.util.List.of(Long.MIN_VALUE), heard, "a new style drops every texture");
            assertEquals(ChartStyle.COLOURED, com.richardsenger.piratesnships.chart.client.ClientChart.settings().style());
        } finally {
            com.richardsenger.piratesnships.chart.client.ClientChart.setRegionListener(k -> { });
            com.richardsenger.piratesnships.chart.client.ClientChart.reset();
        }
    }

    /** Writes both styles of the test village side by side to {@code build/chart4/styles.png} (a look, not a check). */
    @Test
    void writesAStylePreview() throws IOException {
        ChartData d = merged(village(true));
        int px = 6;
        int[] ink = ChartRaster.render(d::wide, 0, 0, 24, 24, px, ChartStyle.INK);
        int[] col = ChartRaster.render(d::wide, 0, 0, 24, 24, px, ChartStyle.COLOURED);
        int s = 24 * px;
        BufferedImage img = new BufferedImage(2 * s + 8, s, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < 2 * s + 8; x++) {
                int v = x < s ? ink[y * s + x] : x >= s + 8 ? col[y * s + x - s - 8] : 0;
                img.setRGB(x, y, MapTileRaster.over(v, MapTileRaster.PARCHMENT));
            }
        }
        Path out = Path.of("build/chart4/styles.png");
        Files.createDirectories(out.getParent());
        ImageIO.write(img, "png", out.toFile());
        assertTrue(Files.size(out) > 0);
    }
}

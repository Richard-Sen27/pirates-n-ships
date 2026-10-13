package com.richardsenger.piratesnships.chart;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.ChartCells;
import com.richardsenger.piratesnships.chart.data.ChartData;
import com.richardsenger.piratesnships.chart.data.ChartMerge;
import com.richardsenger.piratesnships.chart.data.ChartRegion;
import com.richardsenger.piratesnships.chart.data.SampleGrid;
import com.richardsenger.piratesnships.chart.net.ChartRegionPayload;
import com.richardsenger.piratesnships.chart.net.ChartSettings;
import com.richardsenger.piratesnships.chart.render.CellLookup;
import com.richardsenger.piratesnships.chart.render.ChartRaster;
import com.richardsenger.piratesnships.chart.render.ChartStyle;
import com.richardsenger.piratesnships.chart.render.ChartZoomOut;
import com.richardsenger.piratesnships.chart.render.MapTileRaster;
import com.richardsenger.piratesnships.chart.sample.ChartBuilt;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static com.richardsenger.piratesnships.chart.ChartColoursTest.GRASS;
import static com.richardsenger.piratesnships.chart.ChartColoursTest.PATH;
import static com.richardsenger.piratesnships.chart.ChartColoursTest.WOOD;
import static com.richardsenger.piratesnships.chart.ChartColoursTest.WOOD_HIGH;
import static com.richardsenger.piratesnships.chart.ChartColoursTest.WATER;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CHART5: the built flag (bit 4 of the cell byte): the tag {@code pirates_n_ships:chart_built}, the built rule, merge
 * and downsample, the payload, and the ink style colouring only the built cells.
 */
class ChartBuiltTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** The datagen'd tag file: its entries as written ("#tag" for a tag, "ns:block" for a block). */
    private static Set<String> tagEntries() throws IOException {
        Path file = Path.of("src/generated/resources/data/pirates_n_ships/tags/block/chart_built.json");
        try (Reader r = Files.newBufferedReader(file)) {
            JsonArray values = JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("values");
            Set<String> out = new HashSet<>();
            for (JsonElement e : values) out.add(e.isJsonObject() ? e.getAsJsonObject().get("id").getAsString() : e.getAsString());
            return out;
        }
    }

    @Test
    void theTagHoldsTheBuiltFamiliesAndNotTheNaturalOnes() throws IOException {
        Set<String> t = tagEntries();
        for (String yes : new String[]{"#minecraft:planks", "#minecraft:wooden_slabs", "#minecraft:wooden_stairs", "#minecraft:fences",
                "#minecraft:fence_gates", "#minecraft:doors", "#minecraft:trapdoors", "#minecraft:wool", "#minecraft:wool_carpets",
                "#minecraft:stone_bricks", "#minecraft:walls", "#minecraft:beds", "#minecraft:campfires",
                "minecraft:bricks", "minecraft:cobblestone", "minecraft:cobblestone_slab", "minecraft:cobblestone_stairs",
                "minecraft:cobblestone_wall", "minecraft:mossy_cobblestone", "minecraft:smooth_stone", "minecraft:glass",
                "minecraft:glass_pane", "minecraft:red_stained_glass", "minecraft:blue_stained_glass_pane", "minecraft:blue_terracotta",
                "minecraft:red_glazed_terracotta", "minecraft:white_concrete", "minecraft:lime_concrete_powder", "minecraft:dirt_path",
                "minecraft:lantern", "minecraft:barrel", "minecraft:chest", "minecraft:crafting_table", "minecraft:furnace",
                "minecraft:bookshelf", "minecraft:hay_block", "minecraft:scaffolding",
                "pirates_n_ships:oak_railing", "pirates_n_ships:oak_helm", "pirates_n_ships:oak_cleat", "pirates_n_ships:oak_harbor_desk",
                "pirates_n_ships:oak_sea_cot", "pirates_n_ships:nameplate", "pirates_n_ships:map_tile"}) {
            assertTrue(t.contains(yes), yes + " is built");
        }
        for (String no : new String[]{"#minecraft:logs", "#minecraft:leaves", "minecraft:oak_log", "minecraft:oak_leaves", "minecraft:stone",
                "minecraft:sand", "minecraft:sandstone", "minecraft:dirt", "minecraft:grass_block", "minecraft:gravel", "minecraft:cobbled_deepslate",
                "minecraft:terracotta", "minecraft:orange_terracotta", "minecraft:red_terracotta"}) {
            assertFalse(t.contains(no), no + " is natural");
        }
    }

    @Test
    void theBuiltRule() {
        assertFalse(ChartBuilt.cellBuilt(0, 16, 2), "nothing built");
        assertFalse(ChartBuilt.cellBuilt(1, 16, 2), "one column of 16 is a stray block");
        assertTrue(ChartBuilt.cellBuilt(2, 16, 2));
        assertTrue(ChartBuilt.cellBuilt(16, 16, 2));
        assertTrue(ChartBuilt.cellBuilt(1, 16, 1), "the threshold 1: any");
        assertTrue(ChartBuilt.cellBuilt(1, 1, 2), "a cell read through one column needs only that column");
        assertFalse(ChartBuilt.cellBuilt(1, 4, 2));
        assertFalse(ChartBuilt.cellBuilt(0, 0, 1), "nothing read");
    }

    /** The test village of ChartColoursTest with the roofs and paths flagged built. */
    static SampleGrid builtVillage() {
        SampleGrid g = ChartColoursTest.village(true);
        for (int z = 0; z < 24; z++) {
            for (int x = 0; x < 12; x++) {
                int colour = g.colour(x, z);
                if (colour == WOOD || colour == WOOD_HIGH || colour == PATH) g.set(x, z, CellClass.LAND, colour, true);
            }
        }
        return g;
    }

    @Test
    void mergeStoresTheFlagInTheCellByteAndAResampleCanClearIt() {
        ChartData d = ChartColoursTest.merged(builtVillage());
        assertTrue(ChartCells.built(d.cell(1, 1)), "a roof");
        assertEquals(CellClass.LAND, ChartCells.cellClass(d.cell(1, 1)), "the class is untouched");
        assertFalse(ChartCells.built(d.cell(0, 0)), "grass");
        assertEquals(1, d.cell(1, 1) >> 4 & 1);
        ChartMerge.Result same = ChartMerge.merge(d, builtVillage(), 2, Long.MAX_VALUE);
        assertTrue(same.changedRegions().isEmpty(), "the same sample changes nothing");
        // the roof was pulled down: the cell is no longer built, a new version
        SampleGrid gone = SampleGrid.empty(1, 1, 1, 1);
        gone.set(1, 1, CellClass.LAND, GRASS);
        ChartMerge.Result cleared = ChartMerge.merge(d, gone, 3, Long.MAX_VALUE);
        assertFalse(ChartCells.built(cleared.data().cell(1, 1)));
        assertEquals(1, cleared.changedRegions().size());
        // a cell that was never sampled keeps its flag, and the coast recomputation keeps it too
        assertTrue(ChartCells.built(cleared.data().cell(2, 1)));
        SampleGrid beach = SampleGrid.empty(11, 1, 1, 1);
        beach.set(11, 1, CellClass.BEACH, 0, true);
        ChartData withBeach = ChartMerge.merge(d, beach, 4, Long.MAX_VALUE).data();
        assertTrue(ChartCells.built(withBeach.cell(11, 1)) && ChartCells.coast(withBeach.cell(11, 1)), "a built beach is still coast");
    }

    @Test
    void aFarPixelIsBuiltWhenAnyOfItsLandCellsIs() {
        int land = ChartCells.of(CellClass.LAND, false);
        int built = ChartCells.of(CellClass.LAND, false, true);
        int deep = ChartCells.of(CellClass.DEEP_WATER, false);
        CellLookup one = (cx, cz) -> cx == 1 && cz == 1 ? ChartCells.wide(built, WOOD) : ChartCells.wide(land, GRASS);
        int px = MapTileRaster.downsample(one, 0, 0, 4);
        assertTrue(ChartCells.built(px), "one built cell of sixteen");
        assertEquals(CellClass.LAND, ChartCells.cellClass(px));
        assertTrue(ChartCells.built(MapTileRaster.downsample(one, 1, 1, 1)), "zoom 1 keeps the flag");
        assertFalse(ChartCells.built(MapTileRaster.downsample((cx, cz) -> ChartCells.wide(land, GRASS), 0, 0, 4)));
        // a sea pixel is never built
        CellLookup sea = (cx, cz) -> cx == 0 && cz == 0 ? ChartCells.wide(built, WOOD) : ChartCells.wide(deep, WATER);
        int seaPx = MapTileRaster.downsample(sea, 0, 0, 4);
        assertEquals(CellClass.DEEP_WATER, ChartCells.cellClass(seaPx));
        assertFalse(ChartCells.built(seaPx));
    }

    @Test
    void theFlagTravelsInTheRegionPayloadWithinTheBudget() {
        ChartData d = ChartColoursTest.merged(builtVillage());
        ChartRegion r = d.regions().get(ChartRegion.key(0, 0));
        ChartRegionPayload p = ChartRegionPayload.of(r);
        ByteBuf buf = Unpooled.buffer();
        ChartRegionPayload.CODEC.encode(buf, p);
        int size = buf.readableBytes();
        ChartRegionPayload back = ChartRegionPayload.CODEC.decode(buf);
        assertTrue(back.region().sameCells(r));
        assertTrue(ChartCells.built(back.region().get(1, 1)));
        assertTrue(size < 2500, "a village region stays small: " + size);
        // the worst case (every cell different, every flag different) is the same bound as CHART4's
        java.util.Random rnd = new java.util.Random(5);
        byte[] cells = new byte[ChartRegion.CELLS];
        byte[] colours = new byte[ChartRegion.CELLS];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = ChartCells.of(CellClass.LAND, (i & 1) == 0, rnd.nextBoolean());
            colours[i] = (byte) rnd.nextInt(256);
        }
        ByteBuf big = Unpooled.buffer();
        ChartRegionPayload.CODEC.encode(big, ChartRegionPayload.of(ChartRegion.of(0, 0, cells, colours, 1, 0)));
        assertTrue(big.readableBytes() <= 4 * ChartRegion.CELLS + 32, "worst case " + big.readableBytes());
    }

    @Test
    void theInkStyleColoursOnlyTheBuiltCells() {
        ChartData d = ChartColoursTest.merged(builtVillage());
        int[] plain = ChartRaster.render(d::wide, 0, 0, 24, 24, 2, ChartStyle.PLAIN);
        int[] ink = ChartRaster.render(d::wide, 0, 0, 24, 24, 2, ChartStyle.INK);
        int[] col = ChartRaster.render(d::wide, 0, 0, 24, 24, 2, ChartStyle.COLOURED);
        assertArrayEquals(ChartRaster.render(CellLookup.of(d), 0, 0, 24, 24, 2), plain, "plain is the old ink");
        int w = 48;
        int differing = 0;
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 48; x++) {
                int cx = x / 2;
                int cz = y / 2;
                boolean built = ChartCells.built(d.cell(cx, cz));
                if (built) {
                    assertEquals(col[y * w + x], ink[y * w + x], "a built cell reads as in the coloured style at " + x + "," + y);
                    if (ink[y * w + x] != plain[y * w + x]) differing++;
                } else {
                    assertEquals(plain[y * w + x], ink[y * w + x], "everything else stays ink at " + x + "," + y);
                }
            }
        }
        assertTrue(differing > 100, "the built cells are coloured: " + differing);
        // a built cell without a colour (an old save) draws in ink
        SampleGrid g = SampleGrid.empty(0, 0, 2, 2);
        g.set(0, 0, CellClass.LAND, 0, true);
        ChartData noColour = ChartColoursTest.merged(g);
        assertArrayEquals(ChartRaster.render(noColour::wide, 0, 0, 2, 2, 2, ChartStyle.PLAIN), ChartRaster.render(noColour::wide, 0, 0, 2, 2, 2, ChartStyle.INK));
        // the far tile in the ink style
        int[] farInk = ChartZoomOut.render(k -> d.regions().get(k), 0, 0, 2, ChartStyle.INK);
        int[] farPlain = ChartZoomOut.render(k -> d.regions().get(k), 0, 0, 2, ChartStyle.PLAIN);
        assertNotEquals(farPlain[2 * 64 + 2], farInk[2 * 64 + 2], "a built far pixel is coloured");
        assertEquals(farPlain[2 * 64 + 10], farInk[2 * 64 + 10], "an unbuilt one is not");
    }

    @Test
    void settingsCarryThePlainStyleAndDefaultToInk() {
        assertEquals(ChartStyle.INK, ChartSettings.DEFAULT.style());
        assertEquals(ChartStyle.PLAIN, ChartStyle.of(ChartStyle.PLAIN.ordinal()));
        assertEquals(ChartStyle.INK, ChartStyle.of(77), "an unknown style reads as ink");
    }

    /** Writes plain ink, ink with the built cells and the coloured style side by side to {@code build/chart5/styles.png}. */
    @Test
    void writesAStylePreview() throws IOException {
        ChartData d = ChartColoursTest.merged(builtVillage());
        int px = 6;
        ChartStyle[] styles = {ChartStyle.PLAIN, ChartStyle.INK, ChartStyle.COLOURED};
        int s = 24 * px;
        int gap = 8;
        BufferedImage img = new BufferedImage(3 * s + 2 * gap, s, BufferedImage.TYPE_INT_ARGB);
        for (int k = 0; k < 3; k++) {
            int[] pixels = ChartRaster.render(d::wide, 0, 0, 24, 24, px, styles[k]);
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) {
                    img.setRGB(k * (s + gap) + x, y, MapTileRaster.over(pixels[y * s + x], MapTileRaster.PARCHMENT));
                }
            }
        }
        Path out = Path.of("build/chart5/styles.png");
        Files.createDirectories(out.getParent());
        ImageIO.write(img, "png", out.toFile());
        assertTrue(Files.size(out) > 0);
    }
}

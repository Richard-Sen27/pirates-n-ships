package com.richardsenger.piratesnships.sailing.client;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ART5: which part of a hanging cloth takes the frayed foot tile, and that the two tiles fit together. */
class SailFootTest {

    private static final Path TEXTURES = Path.of("src/main/resources/assets/pirates_n_ships/textures/block");
    private static final int CELLS = 2;

    /** The yard renderer's rows for a cloth {@code bottom} blocks deep. */
    private static int rows(float bottom) {
        return Math.max(1, (int) Math.ceil(bottom * CELLS));
    }

    private static List<Integer> footRows(float bottom) {
        int rows = rows(bottom);
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            if (SailFoot.yardFootRow(i, rows, CELLS, bottom)) out.add(i);
        }
        return out;
    }

    @Test
    void aSquareSailShowsItsFootOnTheBottomBlockOnly() {
        assertEquals(List.of(4, 5), footRows(3f), "3-block sail: the last two half-block rows");
        assertEquals(List.of(2, 3), footRows(2f), "2-block sail");
        assertEquals(List.of(3, 4), footRows(2.5f), "reefed 5-block sail (half trim): its lower edge is the foot");
    }

    @Test
    void aShortOrFurledClothKeepsThePlainTile() {
        assertEquals(List.of(), footRows(1f), "1-block sail");
        assertEquals(List.of(), footRows(1.5f), "reefed 3-block sail");
        assertEquals(List.of(), footRows(1.99f), "hoisting, still under 2 blocks");
        assertFalse(SailFoot.shown(0f), "furled");
    }

    @Test
    void theTilesAreCountedUpFromTheFoot() {
        // an even row count keeps the old phase from the top; the last row always ends at the tile's bottom
        for (int rows = 1; rows <= 9; rows++) {
            float last = SailFoot.yardV0(rows - 1, rows, CELLS);
            assertEquals(0.5f, last, 1e-6f, rows + " rows: the last row is the tile's lower half");
            if (rows % 2 == 0) {
                for (int i = 0; i < rows; i++) {
                    assertEquals((i % CELLS) / (float) CELLS, SailFoot.yardV0(i, rows, CELLS), 1e-6f, rows + " rows, row " + i);
                }
            }
        }
        // the foot rows of a sail that shows one are the foot tile's top half, then its bottom half
        assertEquals(0f, SailFoot.yardV0(3, 5, CELLS), 1e-6f);
        assertEquals(0.5f, SailFoot.yardV0(4, 5, CELLS), 1e-6f);
    }

    @Test
    void aTriangularSailMeasuresFromItsTackClewEdge() {
        assertEquals(3f, SailFoot.heightAboveFoot(0f, 0f, 3f), 1e-6f, "head");
        assertEquals(0f, SailFoot.heightAboveFoot(1f, 0f, 3f), 1e-6f, "tack");
        assertEquals(0f, SailFoot.heightAboveFoot(0f, 1f, 3f), 1e-6f, "clew");
        assertEquals(0f, SailFoot.heightAboveFoot(0.4f, 0.6f, 3f), 1e-6f, "on the foot");
        assertEquals(1.5f, SailFoot.heightAboveFoot(0.25f, 0.25f, 3f), 1e-6f);
        // the foot sits on a whole tile: v is an integer there and one tile higher a block up
        assertEquals(3f, SailFoot.stayV(0f, 3f), 1e-6f);
        assertEquals(2f, SailFoot.stayV(1f, 3f), 1e-6f);
        assertEquals(3f, SailFoot.stayV(0f, 2.5f), 1e-6f, "reefed: still a whole tile at the foot");
        // a horizontal foot of a full sail keeps the old planar mapping v = -y (y = h - bottom)
        assertEquals(-(0.7f - 3f), SailFoot.stayV(0.7f, 3f), 1e-6f);
    }

    @Test
    void aTriangularSailTakesTheFootTileOnlyWithinItsBottomBlock() {
        assertTrue(SailFoot.stayFootTriangle(0.5f, 3f));
        assertTrue(SailFoot.stayFootTriangle(1f, 3f));
        assertFalse(SailFoot.stayFootTriangle(1.25f, 3f), "reaches above the bottom block");
        assertFalse(SailFoot.stayFootTriangle(0.5f, 1.5f), "too short a cloth");
        // the renderer's grid: cells are at most half a block deep, so the edge row always fits in the bottom block
        for (float bottom : new float[]{2f, 2.5f, 3f, 4f, 7f}) {
            int n = Math.max(2, (int) Math.ceil(bottom * CELLS));
            float edgeRow = SailFoot.heightAboveFoot(0f, (n - 1f) / n, bottom);
            assertTrue(SailFoot.stayFootTriangle(edgeRow, bottom), bottom + ": the row on the foot edge");
        }
    }

    /**
     * The foot tile is the plain tile above its last 9 rows (so a switch between them does not show), frays only in
     * its last four rows, and still tiles left to right (the seams stay where they were).
     */
    @Test
    void theFootTileIsThePlainTileWithAFrayedHem() throws IOException {
        BufferedImage plain = ImageIO.read(TEXTURES.resolve("sail_cloth.png").toFile());
        BufferedImage foot = ImageIO.read(TEXTURES.resolve("sail_cloth_foot.png").toFile());
        assertEquals(32, foot.getWidth());
        assertEquals(32, foot.getHeight());
        for (int y = 0; y < 23; y++) {
            for (int x = 0; x < 32; x++) {
                assertEquals(plain.getRGB(x, y), foot.getRGB(x, y), "pixel " + x + "," + y);
            }
        }
        int notches = 0;
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int alpha = foot.getRGB(x, y) >>> 24;
                assertTrue(alpha == 0 || alpha == 255, "cutout: alpha is 0 or 255 at " + x + "," + y);
                if (alpha == 0) {
                    assertTrue(y >= 28, "transparent above the frayed rows at " + x + "," + y);
                    notches++;
                }
            }
        }
        assertTrue(notches >= 16, "a frayed edge: " + notches + " transparent texels");
        for (int x = 0; x < 32; x++) {
            assertEquals(255, foot.getRGB(x, 27) >>> 24, "the tabling holds at column " + x);
        }
    }
}

package com.richardsenger.piratesnships.almanac.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Almanac's textures (ALM3, ART14; run from {@code common/}): {@code tools/gen_almanac_textures.py} (the screen) and
 * {@code tools/gen_almanac_book_texture.py} (the placed tome and the item model) write exactly the committed bytes, and
 * the sizes match the screen's constants and vanilla's book model layout.
 */
class AlmanacTexturesTest {

    private static final Path REPO = Path.of("..").toAbsolutePath().normalize();
    private static final Path DIR = Path.of("src/main/resources/assets/pirates_n_ships/textures/gui/almanac");
    private static final List<String> FILES = List.of("book.png", "bookmark.png", "panels.png");
    private static final Path TEXTURES = Path.of("src/main/resources/assets/pirates_n_ships/textures");
    private static final List<String> TOME_FILES = List.of("entity/captains_almanac_book.png", "item/captains_almanac_tome.png");

    private static int[] size(Path png) throws IOException {
        try (InputStream in = Files.newInputStream(png); DataInputStream data = new DataInputStream(in)) {
            data.readNBytes(8);
            data.readInt();
            assertEquals("IHDR", new String(data.readNBytes(4)));
            return new int[]{data.readInt(), data.readInt()};
        }
    }

    @Test
    void sizesMatchTheScreen() throws IOException {
        assertArrayEquals(new int[]{AlmanacScreen.BOOK_W, AlmanacScreen.BOOK_H}, size(DIR.resolve("book.png")));
        assertArrayEquals(new int[]{Bookmarks.TAB_W, Bookmarks.MAX_H}, size(DIR.resolve("bookmark.png")));
        assertArrayEquals(new int[]{AlmanacPanels.TEX_W, AlmanacPanels.TEX_H}, size(DIR.resolve("panels.png")));
        // BookModel.createBodyLayer's texture size
        assertArrayEquals(new int[]{64, 32}, size(TEXTURES.resolve("entity/captains_almanac_book.png")));
        assertArrayEquals(new int[]{32, 32}, size(TEXTURES.resolve("item/captains_almanac_tome.png")));
    }

    /**
     * ALM10: the 3D spotlight's card lies inside the atlas below every ALM8 piece, has the layout's size, is opaque all
     * over (leather rim, brass corners, light card) with a darker rim than its inside.
     */
    @Test
    void the3dSpotlightCardIsInTheAtlas() throws IOException {
        java.awt.image.BufferedImage atlas = javax.imageio.ImageIO.read(DIR.resolve("panels.png").toFile());
        int u = AlmanacPanels.SPOTLIGHT_3D_U, v = AlmanacPanels.SPOTLIGHT_3D_V;
        int w = PageLayout.SPOTLIGHT_3D_W, h = PageLayout.SPOTLIGHT_3D_FRAME;
        assertTrue(u >= 0 && v >= 0 && u + w <= AlmanacPanels.TEX_W && v + h <= AlmanacPanels.TEX_H, "inside the atlas");
        int below = Math.max(AlmanacPanels.SPOTLIGHT_V + PageLayout.SPOTLIGHT_FRAME, AlmanacPanels.TILE_V + AlmanacPanels.TILE_MAX);
        assertTrue(v >= below, "below the spotlight frame and the index tiles");
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                assertEquals(0xFF, atlas.getRGB(u + x, v + y) >>> 24, "opaque at " + x + "," + y);
            }
        }
        // nothing drawn right of it or under it in its rows (the card is the only piece there)
        assertEquals(0, atlas.getRGB(u + w, v + h / 2) >>> 24, "transparent right of the card");
        assertEquals(0, atlas.getRGB(u + w / 2, v + h) >>> 24, "transparent under the card");
        int inset = AlmanacPanels.SPOTLIGHT_3D_INSET;
        assertTrue(luma(atlas.getRGB(u + w / 2, v + 1)) < luma(atlas.getRGB(u + w / 2, v + h / 2)), "the rim is darker than the card");
        assertTrue(luma(atlas.getRGB(u + inset, v + h / 2)) > luma(atlas.getRGB(u + inset - 1, v + h / 2)), "the inset starts inside the rim's shade");
    }

    private static int luma(int argb) {
        return ((argb >> 16) & 0xFF) * 299 + ((argb >> 8) & 0xFF) * 587 + (argb & 0xFF) * 114;
    }

    @Test
    void committedTexturesAreWhatTheScriptWrites(@TempDir Path tmp) throws Exception {
        run("tools/gen_almanac_textures.py", tmp);
        for (String f : FILES) {
            assertTrue(Arrays.equals(Files.readAllBytes(tmp.resolve(f)), Files.readAllBytes(DIR.resolve(f))),
                    f + " is out of date (run python3 tools/gen_almanac_textures.py)");
        }
    }

    @Test
    void committedTomeTexturesAreWhatTheScriptWrites(@TempDir Path tmp) throws Exception {
        run("tools/gen_almanac_book_texture.py", tmp);
        for (String f : TOME_FILES) {
            assertTrue(Arrays.equals(Files.readAllBytes(tmp.resolve(f)), Files.readAllBytes(TEXTURES.resolve(f))),
                    f + " is out of date (run python3 tools/gen_almanac_book_texture.py)");
        }
    }

    private static void run(String script, Path out) throws Exception {
        Process p = new ProcessBuilder("python3", REPO.resolve(script).toString(), "--out", out.toString())
                .directory(REPO.toFile()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), script + " timed out");
        assertEquals(0, p.exitValue(), script + " failed:\n" + output);
    }
}

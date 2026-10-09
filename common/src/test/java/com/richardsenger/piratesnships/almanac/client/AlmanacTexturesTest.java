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
    private static final List<String> FILES = List.of("book.png", "bookmark.png");
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
        // BookModel.createBodyLayer's texture size
        assertArrayEquals(new int[]{64, 32}, size(TEXTURES.resolve("entity/captains_almanac_book.png")));
        assertArrayEquals(new int[]{32, 32}, size(TEXTURES.resolve("item/captains_almanac_tome.png")));
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

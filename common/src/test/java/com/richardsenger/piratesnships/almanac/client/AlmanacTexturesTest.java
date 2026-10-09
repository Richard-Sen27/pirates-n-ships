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
 * The Almanac screen's placeholder textures (ALM3, run from {@code common/}): {@code tools/gen_almanac_textures.py}
 * writes exactly the committed bytes, and the sizes match the screen's constants.
 */
class AlmanacTexturesTest {

    private static final Path REPO = Path.of("..").toAbsolutePath().normalize();
    private static final Path DIR = Path.of("src/main/resources/assets/pirates_n_ships/textures/gui/almanac");
    private static final List<String> FILES = List.of("book.png", "bookmark.png");

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
    }

    @Test
    void committedTexturesAreWhatTheScriptWrites(@TempDir Path tmp) throws Exception {
        Process p = new ProcessBuilder("python3", REPO.resolve("tools/gen_almanac_textures.py").toString(), "--out", tmp.toString())
                .directory(REPO.toFile()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "generator timed out");
        assertEquals(0, p.exitValue(), "generator failed:\n" + output);
        for (String f : FILES) {
            assertTrue(Arrays.equals(Files.readAllBytes(tmp.resolve(f)), Files.readAllBytes(DIR.resolve(f))),
                    f + " is out of date (run python3 tools/gen_almanac_textures.py)");
        }
    }
}

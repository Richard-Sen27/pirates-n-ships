package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.data.MarkerIcon;
import com.richardsenger.piratesnships.chart.render.ChartDoodles;
import com.richardsenger.piratesnships.chart.render.ChartSheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chart's textures (MAP1, run from {@code common/}): {@code tools/gen_gui_textures.py --textures-out} writes
 * exactly the committed sheet and item sprite, the sheet has the size {@link ChartSheet} expects and every part lies
 * inside it without overlapping another.
 */
class ChartTexturesTest {

    private static final Path REPO = Path.of("..").toAbsolutePath().normalize();
    private static final Path TEXTURES = Path.of("src/main/resources/assets/pirates_n_ships/textures");
    private static final List<String> FILES = List.of("gui/chart/sheet.png", "item/chart.png", "block/map_tile.png", "item/map_tile.png");

    private static int[] size(Path png) throws IOException {
        try (InputStream in = Files.newInputStream(png); DataInputStream data = new DataInputStream(in)) {
            data.readNBytes(8);
            data.readInt();
            assertEquals("IHDR", new String(data.readNBytes(4)));
            return new int[]{data.readInt(), data.readInt()};
        }
    }

    @Test
    void committedTexturesAreWhatTheScriptWrites(@TempDir Path tmp) throws Exception {
        Path sprites = tmp.resolve("sprites");
        Path textures = tmp.resolve("textures");
        Process p = new ProcessBuilder("python3", REPO.resolve("tools/gen_gui_textures.py").toString(),
                "--out", sprites.toString(), "--textures-out", textures.toString())
                .directory(REPO.toFile()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "generator timed out");
        assertEquals(0, p.exitValue(), "generator failed:\n" + output);
        for (String f : FILES) {
            assertTrue(Files.isRegularFile(TEXTURES.resolve(f)), "missing " + f + " (run python3 tools/gen_gui_textures.py)");
            assertArrayEquals(Files.readAllBytes(textures.resolve(f)), Files.readAllBytes(TEXTURES.resolve(f)),
                    f + " is out of date (run python3 tools/gen_gui_textures.py)");
        }
    }

    @Test
    void sheetPartsFitAndDoNotOverlap() throws IOException {
        int[] wh = size(TEXTURES.resolve("gui/chart/sheet.png"));
        assertEquals(ChartSheet.WIDTH, wh[0]);
        assertEquals(ChartSheet.HEIGHT, wh[1]);
        assertArrayEquals(new int[]{16, 16}, size(TEXTURES.resolve("item/chart.png")));
        assertArrayEquals(new int[]{16, 16}, size(TEXTURES.resolve("block/map_tile.png")));
        assertArrayEquals(new int[]{16, 16}, size(TEXTURES.resolve("item/map_tile.png")));
        List<ChartSheet.Part> parts = new ArrayList<>(List.of(ChartSheet.COMPASS, ChartSheet.SERPENT, ChartSheet.WHALE,
                ChartSheet.SMALL_ROSE, ChartSheet.RING, ChartSheet.OWN_SHIP, ChartSheet.OTHER_SHIP));
        for (MarkerIcon icon : MarkerIcon.values()) parts.add(ChartSheet.marker(icon));
        for (ChartSheet.Part a : parts) {
            assertTrue(a.u() >= 0 && a.v() >= 0 && a.u() + a.w() <= ChartSheet.WIDTH && a.v() + a.h() <= ChartSheet.HEIGHT, a + " inside");
            for (ChartSheet.Part b : parts) {
                if (a == b) continue;
                boolean apart = a.u() + a.w() <= b.u() || b.u() + b.w() <= a.u() || a.v() + a.h() <= b.v() || b.v() + b.h() <= a.v();
                assertTrue(apart, a + " overlaps " + b);
            }
        }
        for (ChartDoodles.Kind k : ChartDoodles.Kind.values()) {
            assertEquals(k.w, ChartSheet.doodle(k).w(), k + " footprint width matches its sprite");
            assertEquals(k.h, ChartSheet.doodle(k).h(), k + " footprint height matches its sprite");
        }
    }
}

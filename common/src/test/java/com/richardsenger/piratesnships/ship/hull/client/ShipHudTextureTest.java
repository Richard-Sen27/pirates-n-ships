package com.richardsenger.piratesnships.ship.hull.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The ship HUD texture (HUD1, run from {@code common/}): {@code tools/gen_ship_hud_texture.py} writes exactly the
 * committed sheet, which has the size {@link ShipHudSheet} expects, with every part inside it and none overlapping.
 */
class ShipHudTextureTest {

    private static final Path REPO = Path.of("..").toAbsolutePath().normalize();
    private static final Path SHEET = Path.of("src/main/resources/assets/pirates_n_ships/textures/gui/ship_hud.png");

    @Test
    void committedSheetIsWhatTheScriptWrites(@TempDir Path tmp) throws Exception {
        Process p = new ProcessBuilder("python3", REPO.resolve("tools/gen_ship_hud_texture.py").toString(), "--out", tmp.toString())
                .directory(REPO.toFile()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "generator timed out");
        assertEquals(0, p.exitValue(), "generator failed:\n" + output);
        assertTrue(Files.isRegularFile(SHEET), "missing ship_hud.png (run python3 tools/gen_ship_hud_texture.py)");
        assertArrayEquals(Files.readAllBytes(tmp.resolve("gui/ship_hud.png")), Files.readAllBytes(SHEET),
                "ship_hud.png is out of date (run python3 tools/gen_ship_hud_texture.py)");
    }

    @Test
    void partsFitAndDoNotOverlap() throws IOException {
        try (InputStream in = Files.newInputStream(SHEET); DataInputStream data = new DataInputStream(in)) {
            data.readNBytes(8);
            data.readInt();
            assertEquals("IHDR", new String(data.readNBytes(4)));
            assertEquals(ShipHudSheet.WIDTH, data.readInt());
            assertEquals(ShipHudSheet.HEIGHT, data.readInt());
        }
        assertTrue(ShipHudSheet.WIDTH <= 128 && ShipHudSheet.HEIGHT <= 64, "the sheet stays small");
        List<ShipHudSheet.Part> parts = List.of(ShipHudSheet.ROSE, ShipHudSheet.NEEDLE, ShipHudSheet.BOW, ShipHudSheet.PUMP,
                ShipHudSheet.BREACH);
        for (ShipHudSheet.Part a : parts) {
            assertTrue(a.u() >= 0 && a.v() >= 0 && a.u() + a.w() <= ShipHudSheet.WIDTH && a.v() + a.h() <= ShipHudSheet.HEIGHT, a + " inside");
            for (ShipHudSheet.Part b : parts) {
                if (a == b) continue;
                boolean apart = a.u() + a.w() <= b.u() || b.u() + b.w() <= a.u() || a.v() + a.h() <= b.v() || b.v() + b.h() <= a.v();
                assertTrue(apart, a + " overlaps " + b);
            }
        }
        assertEquals(ShipHudLayout.STRIP_H, ShipHudSheet.BOW.h(), "the bow cap is as tall as the strip");
    }
}

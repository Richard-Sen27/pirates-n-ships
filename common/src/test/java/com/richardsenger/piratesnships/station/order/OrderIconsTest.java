package com.richardsenger.piratesnships.station.order;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.Constants;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whistle orders' icons (ART19, run from {@code common/}): an icon is an item or a GUI sprite, the anchor toggle
 * shows the script-drawn anchor sprite and every other order keeps its item, every sprite icon has its 16x16 PNG with
 * transparency in the GUI sprite atlas folder, and {@code tools/gen_order_icons.py} writes exactly the committed files.
 */
class OrderIconsTest {

    private static final Path REPO = Path.of("..").toAbsolutePath().normalize();
    private static final Path SPRITES = Path.of("src/main/resources/assets/pirates_n_ships/textures/gui/sprites");

    /** Vanilla's GUI atlas ({@code atlases/gui.json}: directory source {@code gui/sprites}, no prefix). */
    private static Path png(ResourceLocation sprite) {
        return SPRITES.resolve(sprite.getPath() + ".png");
    }

    @Test
    void iconKindsResolve() {
        WhistleOrder.Icon anchor = WhistleOrder.ANCHOR.icon();
        assertEquals(WhistleOrder.Icon.Kind.SPRITE, anchor.kind());
        assertTrue(anchor.isSprite());
        assertEquals(Constants.id("order/anchor"), anchor.id());
        for (WhistleOrder o : WhistleOrder.entries()) {
            if (o == WhistleOrder.ANCHOR) continue;
            assertEquals(WhistleOrder.Icon.Kind.ITEM, o.icon().kind(), o + " keeps its item icon");
            assertFalse(o.icon().isSprite());
        }
        assertEquals(WhistleOrder.Icon.Kind.ITEM, WhistleOrder.Icon.item(Constants.id("oar")).kind());
        assertEquals(WhistleOrder.Icon.Kind.SPRITE, WhistleOrder.Icon.sprite(Constants.id("order/anchor")).kind());
        assertEquals(Constants.id("oar"), WhistleOrder.ROW.icon().id());
        assertThrows(NullPointerException.class, () -> WhistleOrder.Icon.sprite(null));
    }

    @Test
    void everySpriteIconIsA16x16PngWithTransparency() throws IOException {
        for (WhistleOrder o : WhistleOrder.entries()) {
            if (!o.icon().isSprite()) continue;
            ResourceLocation id = o.icon().id();
            assertEquals(Constants.MOD_ID, id.getNamespace(), o + ": a sprite of ours");
            Path png = png(id);
            assertTrue(Files.isRegularFile(png), "missing " + png + " (run python3 tools/gen_order_icons.py)");
            assertTrue(Files.isRegularFile(Path.of(png + ".mcmeta")), "missing " + png + ".mcmeta");
            BufferedImage image = ImageIO.read(png.toFile());
            assertEquals(16, image.getWidth(), png + " width");
            assertEquals(16, image.getHeight(), png + " height");
            assertTrue(image.getColorModel().hasAlpha(), png + " has an alpha channel");
            int clear = 0;
            int opaque = 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int a = image.getRGB(x, y) >>> 24;
                    if (a == 0) clear++;
                    if (a == 255) opaque++;
                }
            }
            assertTrue(clear >= 32, png + ": transparent around the shape (" + clear + " clear pixels)");
            assertTrue(opaque >= 32, png + ": a solid shape (" + opaque + " opaque pixels)");
            assertEquals(0, image.getRGB(0, 0) >>> 24, png + ": a clear corner");
            assertEquals(0, image.getRGB(15, 15) >>> 24, png + ": a clear corner");
        }
    }

    @Test
    void committedIconsAreWhatTheScriptWrites(@TempDir Path tmp) throws Exception {
        Process p = new ProcessBuilder("python3", REPO.resolve("tools/gen_order_icons.py").toString(), "--out", tmp.toString())
                .directory(REPO.toFile()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "generator timed out");
        assertEquals(0, p.exitValue(), "generator failed:\n" + output);
        Set<String> fresh = files(tmp.resolve("order"));
        assertEquals(files(SPRITES.resolve("order")), fresh, "order icon file set differs (run python3 tools/gen_order_icons.py)");
        for (String f : fresh) {
            assertArrayEquals(Files.readAllBytes(tmp.resolve("order").resolve(f)), Files.readAllBytes(SPRITES.resolve("order").resolve(f)),
                    f + " is out of date (run python3 tools/gen_order_icons.py)");
        }
        // every icon the script draws is used by an order
        for (String f : fresh) {
            if (!f.endsWith(".png")) continue;
            ResourceLocation id = Constants.id("order/" + f.substring(0, f.length() - 4));
            assertTrue(WhistleOrder.entries().stream().anyMatch(o -> o.icon().isSprite() && o.icon().id().equals(id)), id + " is unused");
        }
    }

    private static Set<String> files(Path root) throws IOException {
        Set<String> out = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(f -> out.add(root.relativize(f).toString().replace('\\', '/')));
        }
        return out;
    }
}

package com.richardsenger.piratesnships.core.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The GUI texture kit (U1, run from {@code common/}): every sprite id in {@link GuiSprites} has a PNG and a
 * {@code .png.mcmeta} with a valid GUI scaling (nine-slice size equal to the PNG, borders that fit), no sprite in the
 * folder is left unused, and {@code tools/gen_gui_textures.py} writes exactly the committed bytes.
 */
class GuiTexturesTest {

    private static final Path REPO = Path.of("..").toAbsolutePath().normalize();
    private static final Path SPRITES = Path.of("src/main/resources/assets/pirates_n_ships/textures/gui/sprites");

    private static Path png(ResourceLocation id) {
        return SPRITES.resolve(id.getPath() + ".png");
    }

    /** Width and height from a PNG's IHDR chunk. */
    private static int[] size(Path png) throws IOException {
        try (InputStream in = Files.newInputStream(png); DataInputStream data = new DataInputStream(in)) {
            byte[] sig = data.readNBytes(8);
            assertArrayEquals(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}, sig, png + " is not a PNG");
            data.readInt();
            assertEquals("IHDR", new String(data.readNBytes(4)));
            return new int[]{data.readInt(), data.readInt()};
        }
    }

    private static int border(JsonObject border, String side) {
        return border.get(side).getAsInt();
    }

    @Test
    void everySpriteHasItsPngAndScaling() throws IOException {
        for (ResourceLocation id : GuiSprites.ALL) {
            assertEquals("pirates_n_ships", id.getNamespace());
            Path png = png(id);
            assertTrue(Files.isRegularFile(png), "missing " + png + " (run python3 tools/gen_gui_textures.py)");
            Path meta = Path.of(png + ".mcmeta");
            assertTrue(Files.isRegularFile(meta), "missing " + meta);
            JsonObject scaling = JsonParser.parseString(Files.readString(meta)).getAsJsonObject()
                    .getAsJsonObject("gui").getAsJsonObject("scaling");
            String type = scaling.get("type").getAsString();
            assertTrue(Set.of("stretch", "nine_slice").contains(type), id + ": scaling " + type);
            if (!type.equals("nine_slice")) continue;
            int[] wh = size(png);
            int w = scaling.get("width").getAsInt();
            int h = scaling.get("height").getAsInt();
            assertEquals(wh[0], w, id + ": nine-slice width differs from the PNG");
            assertEquals(wh[1], h, id + ": nine-slice height differs from the PNG");
            JsonElement b = scaling.get("border");
            int left, top, right, bottom;
            if (b.isJsonPrimitive()) {
                left = top = right = bottom = b.getAsInt();
                assertTrue(left > 0, id + ": a single border must be positive");
            } else {
                JsonObject o = b.getAsJsonObject();
                left = border(o, "left");
                top = border(o, "top");
                right = border(o, "right");
                bottom = border(o, "bottom");
            }
            // GuiSpriteScaling.NineSlice's own validation, plus symmetry: vanilla's nine-slice blit draws the right
            // edge column with the left border's width
            assertTrue(left + right < w && top + bottom < h, id + ": borders do not fit");
            assertEquals(left, right, id + ": left and right borders must match");
            assertTrue(left >= 0 && top >= 0 && bottom >= 0, id + ": negative border");
        }
    }

    @Test
    void noSpriteIsLeftUnused() throws IOException {
        Set<String> expected = new HashSet<>();
        for (ResourceLocation id : GuiSprites.ALL) expected.add(id.getPath() + ".png");
        try (Stream<Path> walk = Files.walk(SPRITES)) {
            walk.filter(p -> p.toString().endsWith(".png")).forEach(p ->
                    assertTrue(expected.contains(SPRITES.relativize(p).toString().replace('\\', '/')), p + " is not in GuiSprites.ALL"));
        }
    }

    @Test
    void committedSpritesAreWhatTheScriptWrites(@TempDir Path tmp) throws Exception {
        Process p = new ProcessBuilder("python3", REPO.resolve("tools/gen_gui_textures.py").toString(), "--out", tmp.toString())
                .directory(REPO.toFile()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "generator timed out");
        assertEquals(0, p.exitValue(), "generator failed:\n" + output);
        Set<String> fresh = files(tmp);
        assertEquals(files(SPRITES), fresh, "sprite file set differs (run python3 tools/gen_gui_textures.py)");
        for (String f : fresh) {
            assertTrue(Arrays.equals(Files.readAllBytes(tmp.resolve(f)), Files.readAllBytes(SPRITES.resolve(f))),
                    f + " is out of date (run python3 tools/gen_gui_textures.py)");
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

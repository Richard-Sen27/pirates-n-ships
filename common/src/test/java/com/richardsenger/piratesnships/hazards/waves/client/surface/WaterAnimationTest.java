package com.richardsenger.piratesnships.hazards.waves.client.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import org.junit.jupiter.api.Test;

class WaterAnimationTest {

    /** The vanilla sheet and its {@code .mcmeta}, read from the game's jar the way the sprite loader reads them. */
    private static WaterAnimation vanillaWater() throws Exception {
        String base = "/assets/minecraft/textures/block/water_still.png";
        BufferedImage sheet;
        try (InputStream in = WaterAnimationTest.class.getResourceAsStream(base)) {
            assertNotNull(in, "the vanilla water sheet is on the test classpath");
            sheet = ImageIO.read(in);
        }
        JsonObject json;
        try (InputStream in = WaterAnimationTest.class.getResourceAsStream(base + ".mcmeta")) {
            assertNotNull(in);
            json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        AnimationMetadataSection meta = AnimationMetadataSection.SERIALIZER.fromJson(json.getAsJsonObject("animation"));
        var size = meta.calculateFrameSize(sheet.getWidth(), sheet.getHeight());
        int count = (sheet.getWidth() / size.width()) * (sheet.getHeight() / size.height());
        List<int[]> explicit = new ArrayList<>();
        meta.forEachFrame((index, time) -> explicit.add(new int[] {index, time}));
        assertEquals(16, size.width());
        return WaterAnimation.of(count, meta.getDefaultFrameTime(), explicit, meta.isInterpolatedFrames());
    }

    @Test
    void vanillaWaterShowsThirtyTwoFramesOfTwoTicks() throws Exception {
        WaterAnimation a = vanillaWater();
        assertTrue(a.animated());
        assertFalse(a.interpolate());
        assertEquals(32, a.images().length);
        assertEquals(64, a.period());
        for (long t = 0; t < 200; t++) {
            WaterAnimation.Position p = a.at(t);
            assertEquals((int) ((t / 2) % 32), a.image(p), "tick " + t);
            assertFalse(a.blends(p));
        }
    }

    /** Replays vanilla's ticker ({@code SpriteContents.Ticker#tickAndUpload}) step by step against the closed form. */
    @Test
    void matchesTheTickerStepByStep() {
        WaterAnimation a = WaterAnimation.of(6, 3, List.of(new int[] {5, 4}, new int[] {1, 1}, new int[] {1, 2}, new int[] {0, 7}), true);
        int frame = 0, sub = 0;
        for (long t = 0; t < 500; t++) {
            WaterAnimation.Position p = a.at(t);
            assertEquals(frame, p.frame(), "tick " + t);
            assertEquals(sub, p.subFrame(), "tick " + t);
            sub++;
            if (sub >= a.times()[frame]) {
                frame = (frame + 1) % a.times().length;
                sub = 0;
            }
        }
    }

    @Test
    void theTextureIsUploadedExactlyWhenThePixelsChange() throws Exception {
        WaterAnimation a = vanillaWater();
        int changes = 0;
        long last = a.key(a.at(0));
        for (long t = 1; t <= 128; t++) {
            long k = a.key(a.at(t));
            if (k != last) {
                changes++;
                assertEquals(0, t % 2, "only on a frame boundary");
            }
            last = k;
        }
        assertEquals(64, changes, "every 2 ticks");
    }

    @Test
    void invalidFramesAreDroppedAndInterpolationMixes() {
        // index 9 is outside the 4-image sheet, time 0 is invalid: frames 0 (5 ticks) and 3 (1 tick) remain
        WaterAnimation a = WaterAnimation.of(4, 3, List.of(new int[] {0, 5}, new int[] {9, 2}, new int[] {2, 0}, new int[] {3, 1}), true);
        assertEquals(2, a.images().length);
        WaterAnimation.Position p = a.at(4);
        assertEquals(0, a.image(p));
        assertEquals(3, a.nextImage(p));
        assertTrue(a.blends(p));
        assertEquals(0.2, a.weight(p), 1e-12);
        assertFalse(a.blends(a.at(0)), "a frame's first tick shows it unmixed");
        assertEquals(3, a.image(a.at(5)));
        assertEquals(0, a.image(a.at(6)));
        assertTrue(a.key(a.at(3)) != a.key(a.at(4)), "every mixed tick uploads");
    }

    @Test
    void oneFrameDoesNotAnimate() {
        WaterAnimation a = WaterAnimation.of(1, 2, List.of(), false);
        assertFalse(a.animated());
        assertEquals(0, a.image(a.at(12345)));
        WaterAnimation b = WaterAnimation.of(8, 2, List.of(new int[] {6, 3}), false);
        assertFalse(b.animated());
        assertEquals(0, b.image(b.at(7)), "vanilla shows the sheet's first frame then");
    }
}

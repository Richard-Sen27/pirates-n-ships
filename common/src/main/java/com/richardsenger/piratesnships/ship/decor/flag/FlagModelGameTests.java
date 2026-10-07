package com.richardsenger.piratesnships.ship.decor.flag;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * The flagpole's look, checked headlessly. A server has no resource manager for assets, so this reads the generated
 * block state, the pole model and the cloth textures from the mod's resources on the classpath: since FL1 every state
 * shows only the hand-made pole (the cloth is drawn by {@code client/FlagClothRenderer}), and every flag kind's cloth
 * texture the renderer binds exists at 32 × 16.
 */
public final class FlagModelGameTests {

    private FlagModelGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(FlagModelGameTests.class);
    }

    private static String read(GameTestHelper helper, String path) {
        try (InputStream in = FlagModelGameTests.class.getResourceAsStream("/" + path)) {
            helper.assertTrue(in != null, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(path, e);
        }
    }

    private static String assetPath(String location, String dir, String ext) {
        ResourceLocation id = ResourceLocation.parse(location);
        return "assets/" + id.getNamespace() + "/" + dir + "/" + id.getPath() + ext;
    }

    /** Width and height from a PNG's IHDR chunk. */
    private static int[] pngSize(GameTestHelper helper, String path) {
        try (InputStream in = FlagModelGameTests.class.getResourceAsStream("/" + path)) {
            helper.assertTrue(in != null, "missing texture " + path);
            byte[] head = in.readNBytes(24);
            helper.assertTrue(head.length == 24 && head[12] == 'I' && head[13] == 'H', "not a PNG: " + path);
            return new int[]{readInt(head, 16), readInt(head, 20)};
        } catch (IOException e) {
            throw new IllegalStateException(path, e);
        }
    }

    private static int readInt(byte[] b, int o) {
        return ((b[o] & 0xff) << 24) | ((b[o + 1] & 0xff) << 16) | ((b[o + 2] & 0xff) << 8) | (b[o + 3] & 0xff);
    }

    @ModGameTest
    public static void everyFlagpoleStateShowsThePoleAndEveryClothTextureExists(GameTestHelper helper) {
        JsonObject root = JsonParser.parseString(read(helper, "assets/pirates_n_ships/blockstates/flagpole.json")).getAsJsonObject();
        helper.assertTrue(!root.has("multipart"), "the cloth is drawn by the block entity renderer, not the block model");
        JsonObject variants = root.getAsJsonObject("variants");
        helper.assertTrue(variants != null && variants.size() == 1 && variants.has(""), "one variant for every state: " + variants);
        String model = variants.getAsJsonObject("").get("model").getAsString();
        helper.assertTrue(model.equals("pirates_n_ships:block/flagpole"), "every state shows the pole, not " + model);
        read(helper, assetPath(model, "models", ".json"));
        for (FlagKind kind : FlagKind.values()) {
            if (kind == FlagKind.NONE) continue;
            ResourceLocation file = FlagClothModel.textureFile(kind);
            int[] size = pngSize(helper, "assets/" + file.getNamespace() + "/" + file.getPath());
            helper.assertTrue(size[0] == FlagClothModel.TEXTURE_WIDTH && size[1] == FlagClothModel.TEXTURE_HEIGHT,
                    file + " is " + size[0] + "x" + size[1] + ", expected 32x16");
        }
        helper.succeed();
    }
}

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
 * block state, the pole models and the cloth textures from the mod's resources on the classpath: since FL1 every state
 * shows only a hand-made pole (the cloth is drawn by {@code client/FlagClothRenderer}), since VIS1a one per
 * {@link FlagpolePart} (the crown only at the head, the cleat only at the foot, segments that carry another pole run
 * it the full block), and every flag kind's cloth
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
        // VIS1a: one variant per part, whatever the flag shows
        helper.assertTrue(variants != null && variants.size() == FlagpolePart.values().length, "one variant per part: " + variants);
        for (FlagpolePart part : FlagpolePart.values()) {
            JsonObject variant = variants.getAsJsonObject("part=" + part.getSerializedName());
            helper.assertTrue(variant != null, "no variant for part " + part);
            String model = variant.get("model").getAsString();
            String expected = "pirates_n_ships:block/flagpole" + (part == FlagpolePart.SINGLE ? "" : "_" + part.getSerializedName());
            helper.assertTrue(model.equals(expected), part + " shows " + model + ", expected " + expected);
            helper.assertTrue(!variant.has("x") && !variant.has("y"), part + " is turned: " + variant);
            JsonObject json = JsonParser.parseString(read(helper, assetPath(model, "models", ".json"))).getAsJsonObject();
            String textures = json.getAsJsonObject("textures").toString();
            // the crown (truck and finial, gold) only at the head, the cleat (anvil iron) only at the foot
            boolean crown = part == FlagpolePart.SINGLE || part == FlagpolePart.TOP;
            boolean cleat = part == FlagpolePart.SINGLE || part == FlagpolePart.BOTTOM;
            helper.assertTrue(textures.contains("gold_block") == crown, part + ": finial expected " + crown + ", textures " + textures);
            helper.assertTrue(textures.contains("anvil") == cleat, part + ": cleat expected " + cleat + ", textures " + textures);
            helper.assertTrue(textures.contains("stripped_birch_log"), part + ": no halyard");
            // a segment that has a pole above it runs the 3 px pole the full 16 px, so the segments meet seamlessly
            JsonObject pole = json.getAsJsonArray("elements").get(0).getAsJsonObject();
            float top = pole.getAsJsonArray("to").get(1).getAsFloat();
            float width = pole.getAsJsonArray("to").get(0).getAsFloat() - pole.getAsJsonArray("from").get(0).getAsFloat();
            helper.assertTrue(width == 3f && pole.getAsJsonArray("from").get(1).getAsFloat() == 0f, part + ": pole " + pole);
            helper.assertTrue((top == 16f) == part.hasAbove(), part + ": pole ends at " + top);
        }
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

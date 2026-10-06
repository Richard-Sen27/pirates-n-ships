package com.richardsenger.piratesnships.ship.decor.flag;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.decor.FlagpoleBlock;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The flagpole's look, checked headlessly. A server has no resource manager for assets, so this reads the generated
 * block state, models and textures from the mod's resources on the classpath and resolves them against every real
 * state of the registered block: each shown flag kind and facing selects exactly one cloth (the kind's model, rotated
 * to the facing), {@code flag=none} only the pole, and every referenced model and texture exists (cloth textures are
 * 32 × 16).
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

    /** A simple multipart {@code when} (key → value, values may be {@code a|b}) against a block state. */
    private static boolean matches(GameTestHelper helper, JsonObject when, BlockState state) {
        for (Map.Entry<String, JsonElement> term : when.entrySet()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(term.getKey());
            helper.assertTrue(property != null, "block state condition on unknown property " + term.getKey());
            if (!Arrays.asList(term.getValue().getAsString().split("\\|")).contains(valueName(state, property))) return false;
        }
        return true;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    @ModGameTest
    public static void everyFlagKindAndFacingResolvesToItsCloth(GameTestHelper helper) {
        JsonArray parts = JsonParser.parseString(read(helper, "assets/pirates_n_ships/blockstates/flagpole.json"))
                .getAsJsonObject().getAsJsonArray("multipart");
        List<BlockState> states = ShipDecor.FLAGPOLE.get().getStateDefinition().getPossibleStates();
        helper.assertTrue(states.size() == FlagKind.values().length * 4, "unexpected flagpole states: " + states.size());
        for (BlockState state : states) {
            List<JsonObject> applied = new ArrayList<>();
            for (JsonElement p : parts) {
                JsonObject part = p.getAsJsonObject();
                if (!part.has("when") || matches(helper, part.getAsJsonObject("when"), state)) applied.add(part.getAsJsonObject("apply"));
            }
            FlagKind kind = state.getValue(FlagpoleBlock.FLAG);
            int expected = kind == FlagKind.NONE ? 1 : 2;
            helper.assertTrue(applied.size() == expected, state + " selects " + applied.size() + " models, expected " + expected);
            helper.assertTrue(applied.get(0).get("model").getAsString().equals("pirates_n_ships:block/flagpole"), state + ": pole first");
            if (kind == FlagKind.NONE) continue;
            JsonObject cloth = applied.get(1);
            helper.assertTrue(cloth.get("model").getAsString().equals(FlagClothModel.modelId(kind).toString()), state + " shows " + cloth);
            int y = cloth.has("y") ? cloth.get("y").getAsInt() : 0;
            helper.assertTrue(y == FlagClothModel.yRotation(state.getValue(FlagpoleBlock.FACING)), state + " rotates the cloth by " + y);
        }
        for (FlagKind kind : FlagKind.values()) {
            if (kind == FlagKind.NONE) continue;
            JsonObject model = JsonParser.parseString(read(helper, assetPath(FlagClothModel.modelId(kind).toString(), "models", ".json"))).getAsJsonObject();
            helper.assertTrue(model.getAsJsonArray("elements").size() == 1, kind + ": one cloth element");
            String texture = model.getAsJsonObject("textures").get(FlagClothModel.TEXTURE_KEY).getAsString();
            int[] size = pngSize(helper, assetPath(texture, "textures", ".png"));
            helper.assertTrue(size[0] == FlagClothModel.TEXTURE_WIDTH && size[1] == FlagClothModel.TEXTURE_HEIGHT,
                    texture + " is " + size[0] + "x" + size[1] + ", expected 32x16");
        }
        read(helper, assetPath("pirates_n_ships:block/flagpole", "models", ".json"));
        helper.succeed();
    }
}

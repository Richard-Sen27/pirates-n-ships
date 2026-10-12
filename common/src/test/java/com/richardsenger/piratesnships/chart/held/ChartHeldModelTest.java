package com.richardsenger.piratesnships.chart.held;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The held chart (CHART3, run from {@code common/}): the unroll motion, the item model frames and their overrides, and
 * a rig test that composes vanilla 1.21.1's real render chain for a held item ({@link ModelPart#translateAndRotate} of
 * the arm posed by the PAL file, {@code ItemInHandLayer.renderArmWithItem}'s turns and offset, the frame's own
 * {@link ItemTransform#apply}, {@code ItemRenderer}'s centring) for both hands: the rollers sit in the fists, the map
 * faces the reader's eyes and reads the right way round. Vanilla model frame: y down, the player faces -z, the right
 * arm on -x (its pivot (-5, 2, 0), the fist 9 px down the arm's centre column).
 */
class ChartHeldModelTest {

    static final Path MODELS = Path.of("src/main/resources/assets/pirates_n_ships/models/item");
    static final Path ANIMS = Path.of("src/main/resources/assets/pirates_n_ships/chart_animations");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // --- motion ----------------------------------------------------------------------------------------------------

    @Test
    void unrollsInHalfASecondAndRollsUpFromWhereItWas() {
        float p = 0f;
        for (int i = 0; i < ChartHeldMotion.UNROLL_TICKS; i++) {
            assertTrue(p < 1f, "not open before tick " + i);
            p = ChartHeldMotion.step(p, true);
        }
        assertEquals(1f, p, 1e-5f, "open after " + ChartHeldMotion.UNROLL_TICKS + " ticks");
        assertEquals(1f, ChartHeldMotion.step(p, true), "stays open");
        p = ChartHeldMotion.step(ChartHeldMotion.step(0.4f, false), false);
        assertEquals(0.2f, p, 1e-5f, "closing half way rolls up from there");
        assertEquals(0f, ChartHeldMotion.step(0.05f, false), "never below rolled up");
        assertEquals(0.55f, ChartHeldMotion.lerp(0.5f, 0.6f, 0.5f), 1e-5f);
    }

    @Test
    void framesFollowTheProgress() {
        assertEquals("chart", ChartHeldMotion.frame(0f));
        assertEquals("chart_unroll_0", ChartHeldMotion.frame(0.1f));
        assertEquals("chart_unroll_1", ChartHeldMotion.frame(0.3f));
        assertEquals("chart_unroll_3", ChartHeldMotion.frame(0.99f));
        assertEquals("chart_open", ChartHeldMotion.frame(1f));
    }

    @Test
    void armAnimationsMatchTheProgress() {
        assertNull(ChartHeldMotion.animation(false, true, 0.5f), "no pose without a chart in hand");
        assertEquals(ChartHeldMotion.READ, ChartHeldMotion.animation(true, true, 0f));
        assertEquals(ChartHeldMotion.STOW, ChartHeldMotion.animation(true, false, 0.3f));
        assertNull(ChartHeldMotion.animation(true, false, 0f), "rolled up: back to vanilla");
        assertEquals(4f, ChartHeldMotion.readStartTick(0.4f), 1e-5f);
        assertEquals(6f, ChartHeldMotion.stowStartTick(0.4f), 1e-5f);
    }

    // --- models ----------------------------------------------------------------------------------------------------

    static JsonObject model(String name) throws IOException {
        return JsonParser.parseString(Files.readString(MODELS.resolve(name + ".json"))).getAsJsonObject();
    }

    @Test
    void theClosedChartOverridesToEveryFrameInOrder() throws IOException {
        JsonArray overrides = model("chart").getAsJsonArray("overrides");
        assertEquals(ChartHeldMotion.FRAMES.size(), overrides.size());
        for (int i = 0; i < overrides.size(); i++) {
            JsonObject o = overrides.get(i).getAsJsonObject();
            ChartHeldMotion.Frame f = ChartHeldMotion.FRAMES.get(i);
            assertEquals(Set.of("pirates_n_ships:chart_open"), o.getAsJsonObject("predicate").keySet());
            assertEquals(f.from(), o.getAsJsonObject("predicate").get("pirates_n_ships:chart_open").getAsFloat(), 1e-5f);
            assertEquals("pirates_n_ships:item/" + f.model(), o.get("model").getAsString());
            assertFalse(model(f.model()).has("overrides"), f.model() + ": overrides of an override target are never read");
        }
    }

    /** ART17: paper items repeat the right-hand values in the left-hand entries; the game mirrors them. */
    @Test
    void leftHandEntriesRepeatTheRightHand() throws IOException {
        for (String name : names()) {
            JsonObject display = model(name).getAsJsonObject("display");
            assertEquals(display.get("thirdperson_righthand"), display.get("thirdperson_lefthand"), name + " third person");
            assertEquals(display.get("firstperson_righthand"), display.get("firstperson_lefthand"), name + " first person");
        }
    }

    static List<String> names() {
        List<String> out = new ArrayList<>(List.of("chart"));
        ChartHeldMotion.FRAMES.forEach(f -> out.add(f.model()));
        return out;
    }

    /** The rollers move apart frame by frame, and only frames past the first show a sheet with the map on it. */
    @Test
    void theRollersSpreadFrameByFrame() throws IOException {
        float last = 0f;
        for (ChartHeldMotion.Frame f : ChartHeldMotion.FRAMES) {
            float half = rollerHalf(f.model());
            assertTrue(half > last, f.model() + ": rollers further apart than the frame before");
            last = half;
            boolean sheet = elementNames(f.model()).contains("sheet");
            assertEquals(!f.model().equals("chart_unroll_0"), sheet, f.model() + ": sheet");
        }
        assertEquals(15f, last, 1e-4f, "open: 30 px of chart between the roller axes");
        assertTrue(elementNames("chart_open").stream().filter(n -> n.startsWith("map_")).count() >= 25, "the map on the open chart");
        assertTrue(elementNames("chart").containsAll(List.of("tie", "knot", "rod_left_a", "rod_right_a", "knob_left_0_a")),
                "the closed chart: two rolls on wooden rollers, tied");
    }

    static List<String> elementNames(String name) throws IOException {
        List<String> out = new ArrayList<>();
        for (JsonElement e : model(name).getAsJsonArray("elements")) out.add(e.getAsJsonObject().get("name").getAsString());
        return out;
    }

    /** Half the distance between the two roller axes of a frame, from its rod elements. */
    static float rollerHalf(String name) throws IOException {
        float left = Float.NaN, right = Float.NaN;
        for (JsonElement el : model(name).getAsJsonArray("elements")) {
            JsonObject e = el.getAsJsonObject();
            String n = e.get("name").getAsString();
            float cx = (e.getAsJsonArray("from").get(0).getAsFloat() + e.getAsJsonArray("to").get(0).getAsFloat()) / 2;
            if (n.equals("rod_left_a")) left = cx;
            if (n.equals("rod_right_a")) right = cx;
        }
        return (right - left) / 2;
    }

    // --- the rig ---------------------------------------------------------------------------------------------------

    /** The PAL time each frame's pose is solved for (tools/gen_chart_held_models.py FRAMES). */
    static final Map<String, Float> SOLVED_AT = Map.of("chart_unroll_0", 0.15f, "chart_unroll_1", 0.2f,
            "chart_unroll_2", 0.3f, "chart_unroll_3", 0.42f, "chart_open", 0.5f);
    static final Vector3f EYES = new Vector3f(0, -3, -4);

    /** A bone's rotation (degrees) in a PAL file at time t, linear between keys. */
    static Vector3f key(JsonObject anim, String bone, float t) {
        JsonObject rot = anim.getAsJsonObject("bones").getAsJsonObject(bone).getAsJsonObject("rotation");
        float prevT = Float.NaN;
        Vector3f prev = null;
        for (String k : rot.keySet()) {
            float kt = Float.parseFloat(k);
            JsonArray a = rot.getAsJsonArray(k);
            Vector3f v = new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
            if (kt >= t - 1e-6f) {
                if (prev == null || kt - prevT < 1e-6f) return v;
                return prev.lerp(v, (t - prevT) / (kt - prevT), new Vector3f());
            }
            prev = v;
            prevT = kt;
        }
        return prev;
    }

    static JsonObject anim(String name) throws IOException {
        return JsonParser.parseString(Files.readString(ANIMS.resolve(name + ".json"))).getAsJsonObject()
                .getAsJsonObject("animations").getAsJsonObject(name);
    }

    static ModelPart arm(float x, Vector3f degrees) {
        ModelPart part = new ModelPart(List.of(), Map.of());
        part.x = x;
        part.y = 2;
        part.xRot = (float) Math.toRadians(degrees.x);
        part.yRot = (float) Math.toRadians(degrees.y);
        part.zRot = (float) Math.toRadians(degrees.z);
        return part;
    }

    /** A point of the arm (arm-local px) in the model frame (px). */
    static Vector3f onArm(ModelPart arm, float lx, float ly) {
        PoseStack ps = new PoseStack();
        arm.translateAndRotate(ps);
        return ps.last().pose().transformPosition(new Vector3f(lx / 16, ly / 16, 0)).mul(16);
    }

    /** The item model's matrix in the model frame (blocks -> blocks), held by {@code holder} as ItemInHandLayer does. */
    static Matrix4f held(ModelPart holder, boolean left, ItemTransform transform) {
        PoseStack ps = new PoseStack();
        holder.translateAndRotate(ps);
        ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
        ps.mulPose(Axis.YP.rotationDegrees(180.0F));
        ps.translate((float) (left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
        transform.apply(left, ps);
        ps.translate(-0.5F, -0.5F, -0.5F);
        return new Matrix4f(ps.last().pose());
    }

    static ItemTransform transform(String name, ItemDisplayContext ctx) throws IOException {
        return BlockModel.fromString(Files.readString(MODELS.resolve(name + ".json"))).getTransforms().getTransform(ctx);
    }

    static Vector3f point(Matrix4f m, float x, float y, float z) {
        return m.transformPosition(new Vector3f(x / 16, y / 16, z / 16)).mul(16);
    }

    static Vector3f dir(Matrix4f m, float x, float y, float z) {
        return m.transformDirection(new Vector3f(x, y, z)).normalize();
    }

    @Test
    void rollersSitInTheFistsAndTheMapFacesTheReader() throws IOException {
        JsonObject read = anim(ChartHeldMotion.READ);
        for (ChartHeldMotion.Frame f : ChartHeldMotion.FRAMES) {
            float t = SOLVED_AT.get(f.model());
            ModelPart right = arm(-5, key(read, "right_arm", t));
            ModelPart left = arm(5, key(read, "left_arm", t));
            Vector3f rightFist = onArm(right, -1, 9), leftFist = onArm(left, 1, 9);
            float half = rollerHalf(f.model());
            for (boolean leftHand : new boolean[]{false, true}) {
                String what = f.model() + (leftHand ? " (left hand)" : " (right hand)");
                ItemDisplayContext ctx = leftHand ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
                Matrix4f m = held(leftHand ? left : right, leftHand, transform(f.model(), ctx));
                Vector3f centre = point(m, 8, 8, 8);
                Vector3f mid = new Vector3f(rightFist).add(leftFist).mul(0.5f);
                assertTrue(centre.distance(mid) < 0.3f, what + ": centred between the fists, off by " + centre.distance(mid));
                if (!f.model().equals("chart_unroll_0")) {
                    // model +x is the reader's right: the right roller in the right fist
                    assertTrue(point(m, 8 + half, 8, 8).distance(rightFist) < 0.6f, what + ": right roller in the right fist, off by "
                            + point(m, 8 + half, 8, 8).distance(rightFist));
                    assertTrue(point(m, 8 - half, 8, 8).distance(leftFist) < 0.6f, what + ": left roller in the left fist");
                }
                Vector3f face = dir(m, 0, 0, 1);
                Vector3f toEyes = new Vector3f(EYES).sub(centre).normalize();
                assertTrue(face.dot(toEyes) > 0.95f, what + ": the map faces the eyes (" + face + ")");
                assertTrue(dir(m, 1, 0, 0).dot(new Vector3f(-1, 0, 0)) > 0.95f, what + ": the map reads left to right for its reader");
                assertTrue(dir(m, 0, 1, 0).y < -0.3f, what + ": the map's top is up");
            }
        }
    }

    /** Before the open pose is reached the chart rides on the holding arm; at the end of the raise it is at chest height. */
    @Test
    void theChartIsReadAtChestHeightInFront() throws IOException {
        JsonObject read = anim(ChartHeldMotion.READ);
        ModelPart right = arm(-5, key(read, "right_arm", 0.5f));
        Matrix4f m = held(right, false, transform("chart_open", ItemDisplayContext.THIRD_PERSON_RIGHT_HAND));
        Vector3f centre = point(m, 8, 8, 8);
        assertTrue(centre.y > 3 && centre.y < 9, "between the shoulders (y 2) and the belt (y 12): " + centre);
        assertTrue(centre.z < -7, "well in front of the chest: " + centre);
        Vector3f top = point(m, 8, 17.9f, 8), bottom = point(m, 8, -1.9f, 8);
        assertTrue(top.z < bottom.z, "the top edge leans away from the reader");
    }

    /** First person: the open chart lies low in the middle of the view, tilted away, for either hand. */
    @Test
    void firstPersonShowsTheOpenChartLowInTheView() throws IOException {
        for (boolean leftHand : new boolean[]{false, true}) {
            ItemTransform tf = transform("chart_open", leftHand ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
            PoseStack ps = new PoseStack();
            ps.translate((leftHand ? -1 : 1) * 0.56F, -0.52F, -0.72F); // ItemInHandRenderer.applyItemArmTransform, fully equipped
            tf.apply(leftHand, ps);
            ps.translate(-0.5F, -0.5F, -0.5F);
            Matrix4f m = ps.last().pose();
            Vector3f centre = m.transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
            assertEquals(0f, centre.x, 0.01f, "centred (" + leftHand + ")");
            assertTrue(centre.y < -0.3f && centre.y > -0.5f, "low in the view: " + centre);
            Vector3f face = m.transformDirection(new Vector3f(0, 0, 1)).normalize();
            assertTrue(face.z > 0.5f && face.y > 0.5f, "the face turned up towards the camera: " + face);
            assertTrue(m.transformDirection(new Vector3f(1, 0, 0)).normalize().x > 0.99f, "reads left to right");
        }
    }

    // --- PAL files -------------------------------------------------------------------------------------------------

    @Test
    void palLoadsBothAnimationsAndStowIsReadBackwards() throws IOException {
        for (String name : List.of(ChartHeldMotion.READ, ChartHeldMotion.STOW)) {
            Map<String, Animation> loaded;
            try (InputStream in = Files.newInputStream(ANIMS.resolve(name + ".json"))) {
                loaded = UniversalAnimLoader.loadAnimations(in);
            }
            assertEquals(Set.of(name), loaded.keySet());
            assertEquals(ChartHeldMotion.UNROLL_TICKS, loaded.get(name).length(), 1e-3, name + " lasts the unroll");
            assertEquals(Set.of("right_arm", "left_arm"), anim(name).getAsJsonObject("bones").keySet(), name + ": only the arms");
        }
        assertEquals("hold_on_last_frame", anim(ChartHeldMotion.READ).get("loop").getAsString());
        assertFalse(anim(ChartHeldMotion.STOW).get("loop").getAsBoolean());
        JsonObject read = anim(ChartHeldMotion.READ), stow = anim(ChartHeldMotion.STOW);
        for (float t : new float[]{0f, 0.1f, 0.2f, 0.33f, 0.5f}) {
            for (String bone : List.of("right_arm", "left_arm")) {
                assertTrue(key(read, bone, t).distance(key(stow, bone, 0.5f - t)) < 0.05f, bone + " at " + t);
            }
        }
        // vanilla angles: both arms forward (negative x), the fists turned in towards each other while the chart is rolled
        assertTrue(key(read, "right_arm", 0.5f).x < -50 && key(read, "left_arm", 0.5f).x < -50);
        assertTrue(key(read, "right_arm", 0.15f).y < -10 && key(read, "left_arm", 0.15f).y > 10);
    }
}

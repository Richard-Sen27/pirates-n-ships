package com.richardsenger.piratesnships.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.Constants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.richardsenger.piratesnships.mob.client.HumanoidGeoModel;
import com.richardsenger.piratesnships.mob.client.HumanoidGeoRenderer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.keyframe.BoneAnimation;
import software.bernie.geckolib.animation.keyframe.Keyframe;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.loading.math.MathValue;
import software.bernie.geckolib.util.RenderUtil;
import org.joml.Vector3f;
import com.mojang.math.Axis;
import software.bernie.geckolib.model.data.EntityModelData;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Blockbench models of the pirate, sailor, navy soldier and officer (M3-art) on the humanoid rig
 * ({@code art/README.md}, "Entities"): each type's {@code geo/<type>.geo.json} has exactly the crew member's bones with
 * the same parents and pivots and the same contract cubes (detail cubes may be added), so the shared
 * {@code crew_member.animation.json} drives it; GeckoLib's own parser bakes it; every per-face UV stays on the 64x64
 * skin sheet; and the model resolves the geometry from the texture name.
 */
class SeafarerRigTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/pirates_n_ships");
    private static final Path RIG = ASSETS.resolve("geo/crew_member.geo.json");
    private static final Path ANIMATIONS = ASSETS.resolve("animations/crew_member.animation.json");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Path geo(MobKind kind) {
        return ASSETS.resolve("geo/" + kind.id() + ".geo.json");
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static JsonObject geometry(Path file) throws IOException {
        return read(file).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
    }

    private static Map<String, JsonObject> bones(Path file) throws IOException {
        Map<String, JsonObject> byName = new HashMap<>();
        for (JsonElement b : geometry(file).getAsJsonArray("bones")) byName.put(b.getAsJsonObject().get("name").getAsString(), b.getAsJsonObject());
        return byName;
    }

    /** The box-UV cubes of a bone (the rig's contract cubes: body, head, hat layer, limbs and their outer layers). */
    private static List<JsonObject> boxCubes(JsonObject bone) {
        List<JsonObject> out = new ArrayList<>();
        if (!bone.has("cubes")) return out;
        for (JsonElement c : bone.getAsJsonArray("cubes")) {
            JsonObject cube = c.getAsJsonObject();
            if (cube.get("uv").isJsonArray()) out.add(cube);
        }
        return out;
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    @ParameterizedTest
    @EnumSource(MobKind.class)
    void geometryIsBedrock1_12WithItsOwnIdentifierAndA64x64Sheet(MobKind kind) throws IOException {
        JsonObject json = read(geo(kind));
        assertEquals("1.12.0", json.get("format_version").getAsString(), "GeckoLib supports geometry 1.12.0 only");
        JsonObject desc = geometry(geo(kind)).getAsJsonObject("description");
        assertEquals("geometry." + kind.id(), desc.get("identifier").getAsString());
        assertEquals(64, desc.get("texture_width").getAsInt());
        assertEquals(64, desc.get("texture_height").getAsInt());
    }

    @ParameterizedTest
    @EnumSource(MobKind.class)
    void bonesParentsPivotsAndContractCubesAreTheCrewRigs(MobKind kind) throws IOException {
        Map<String, JsonObject> rig = bones(RIG), mob = bones(geo(kind));
        assertEquals(rig.keySet(), mob.keySet(), kind + " bone names");
        for (String name : rig.keySet()) {
            JsonObject r = rig.get(name), m = mob.get(name);
            assertEquals(r.has("parent") ? r.get("parent").getAsString() : null,
                    m.has("parent") ? m.get("parent").getAsString() : null, kind + " " + name + " parent");
            assertArrayEquals(floats(r.getAsJsonArray("pivot")), floats(m.getAsJsonArray("pivot")), 1e-4f, kind + " " + name + " pivot");
            assertFalse(m.has("rotation"), kind + " " + name + " must not carry a rest rotation");
            assertEquals(boxCubes(r), boxCubes(m), kind + " " + name + " contract cubes");
        }
        assertFalse(mob.get("right_hand").has("cubes"), "right_hand is an empty locator");
        assertFalse(mob.get("left_hand").has("cubes"), "left_hand is an empty locator");
    }

    @ParameterizedTest
    @EnumSource(MobKind.class)
    void everyFaceUvStaysOnTheSheet(MobKind kind) throws IOException {
        int detail = 0;
        for (JsonObject bone : bones(geo(kind)).values()) {
            if (!bone.has("cubes")) continue;
            for (JsonElement c : bone.getAsJsonArray("cubes")) {
                JsonElement uv = c.getAsJsonObject().get("uv");
                if (!uv.isJsonObject()) continue;
                detail++;
                for (Map.Entry<String, JsonElement> face : uv.getAsJsonObject().entrySet()) {
                    float[] at = floats(face.getValue().getAsJsonObject().getAsJsonArray("uv"));
                    float[] size = floats(face.getValue().getAsJsonObject().getAsJsonArray("uv_size"));
                    for (int i = 0; i < 2; i++) {
                        float lo = Math.min(at[i], at[i] + size[i]), hi = Math.max(at[i], at[i] + size[i]);
                        assertTrue(lo >= 0 && hi <= 64, kind + " " + bone.get("name").getAsString() + " " + face.getKey() + " uv off the sheet");
                    }
                }
            }
        }
        assertTrue(detail > 10, kind + " has its detail cubes");
    }

    @ParameterizedTest
    @EnumSource(MobKind.class)
    void geckoLibBakesItAndTheSharedAnimationsFindEveryBone(MobKind kind) throws IOException {
        Model model = KeyFramesAdapter.GEO_GSON.fromJson(read(geo(kind)), Model.class);
        BakedGeoModel baked = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(model));
        assertEquals(1, baked.topLevelBones().size(), "one root bone");
        GeoBone arm = baked.getBone("right_arm").orElseThrow();
        assertEquals(5f, arm.getPivotX(), 1e-4f, "GeckoLib mirrors x when baking");
        BakedAnimations anims = KeyFramesAdapter.GEO_GSON.fromJson(read(ANIMATIONS).getAsJsonObject("animations"), BakedAnimations.class);
        for (Animation anim : anims.animations().values()) {
            for (BoneAnimation b : anim.boneAnimations()) {
                assertNotNull(baked.getBone(b.boneName()).orElse(null), kind + ": " + anim.name() + " animates missing bone " + b.boneName());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MobKind.class)
    void textureIsAPainted64x64SkinSheet(MobKind kind) throws IOException {
        BufferedImage img = ImageIO.read(ASSETS.resolve("textures/entity/" + kind.id() + ".png").toFile());
        assertEquals(64, img.getWidth());
        assertEquals(64, img.getHeight());
        assertEquals(0xFF, img.getRGB(12, 12) >>> 24, "face");
        assertEquals(0xFF, img.getRGB(24, 26) >>> 24, "body front");
    }

    @ParameterizedTest
    @EnumSource(MobKind.class)
    void theModelResolvesTheTypesGeometryFromItsTexture(MobKind kind) {
        ResourceLocation geo = HumanoidGeoModel.geoFor(HumanoidGeoModel.entityTexture(kind.id()));
        assertEquals(Constants.id("geo/" + kind.id() + ".geo.json"), geo);
        assertTrue(Files.isRegularFile(ASSETS.resolve(geo.getPath())), geo + " exists");
        assertEquals(HumanoidGeoModel.RIG_GEO, HumanoidGeoModel.geoFor(HumanoidGeoModel.entityTexture("crew_member")),
                "the crew member keeps its geometry");
    }

    /** Bones the musket animations may key (M6): arms, the right hand locator (turns the held musket) and the waist. */
    private static final Set<String> MUSKET_BONES = Set.of("right_arm", "left_arm", "right_hand", "waist");

    @Test
    void musketAnimationsExistWithTheirLengthsAndLoopModesAndKeyOnlyTheUpperBody() throws IOException {
        BakedAnimations anims = KeyFramesAdapter.GEO_GSON.fromJson(read(ANIMATIONS).getAsJsonObject("animations"), BakedAnimations.class);
        Animation aim = anims.getAnimation(NavySoldier.ANIM_AIM);
        Animation reload = anims.getAnimation(NavySoldier.ANIM_RELOAD);
        Animation shove = anims.getAnimation(NavySoldier.ANIM_SHOVE);
        assertNotNull(aim, "musket_aim");
        assertNotNull(reload, "musket_reload");
        assertNotNull(shove, "musket_shove");
        assertEquals(Animation.LoopType.HOLD_ON_LAST_FRAME, aim.loopType(), "the aim holds its last frame");
        assertEquals(Animation.LoopType.PLAY_ONCE, reload.loopType(), "the reload plays once");
        assertEquals(Animation.LoopType.PLAY_ONCE, shove.loopType(), "the shove plays once");
        // GeckoLib lengths are in ticks
        assertEquals(7.0, aim.length(), 1e-6, "aim rises in 0.35 s");
        assertEquals(MusketAction.RELOAD_ANIMATION_TICKS, reload.length(), 1e-6, "reload is 5 s (the speed stretches it)");
        assertEquals(MusketAction.SHOVE_TICKS, shove.length(), 1e-6, "shove is 0.5 s");
        for (Animation anim : List.of(aim, reload, shove)) {
            Set<String> keyed = new java.util.HashSet<>();
            for (BoneAnimation b : anim.boneAnimations()) {
                assertTrue(MUSKET_BONES.contains(b.boneName()), anim.name() + " keys " + b.boneName() + " (legs walk, the head looks)");
                keyed.add(b.boneName());
            }
            assertEquals(MUSKET_BONES, keyed, anim.name() + " keys both arms, the musket hand and the waist");
        }
        // the musket lies along the aim: the hand locator turns the barrel level (about +84 in file convention)
        JsonObject hand = read(ANIMATIONS).getAsJsonObject("animations").getAsJsonObject(NavySoldier.ANIM_AIM)
                .getAsJsonObject("bones").getAsJsonObject("right_hand").getAsJsonObject("rotation");
        JsonArray last = hand.getAsJsonObject("0.35").getAsJsonArray("vector");
        assertTrue(last.get(0).getAsFloat() > 70f, "aim turns the musket level along the arm");
    }

    // ---- Where the held musket points (M6b) ----------------------------------------------------------------------

    /** Butt, muzzle and hand of the soldier's musket in the model frame (px; y up, the mob faces -z, its right at +x). */
    private record MusketPose(Vector3f butt, Vector3f muzzle, Vector3f hand) {
        Vector3f barrel() {
            return new Vector3f(muzzle).sub(butt).normalize();
        }

        /** Degrees above the horizontal. */
        double pitch() {
            return Math.toDegrees(Math.asin(barrel().y));
        }

        /** Degrees off straight ahead (-z) towards the mob's right. */
        double yaw() {
            Vector3f b = barrel();
            return Math.toDegrees(Math.atan2(b.x, -b.z));
        }
    }

    /** The value a keyframe channel reaches at {@code tick}, which must be a key time (GeckoLib keyframe lengths are in ticks). */
    private static float keyedAt(List<Keyframe<MathValue>> frames, double tick, String what) {
        double t = 0;
        for (Keyframe<MathValue> k : frames) {
            t += k.length();
            if (Math.abs(t - tick) < 1e-6) return (float) k.endValue().get();
        }
        throw new AssertionError(what + " has no key at tick " + tick);
    }

    /** A point of the musket item model (px): the centre of the named element's rotation. */
    private static Vector3f musketPoint(JsonObject model, String element) {
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject o = e.getAsJsonObject();
            if (element.equals(o.get("name").getAsString())) {
                float[] at = floats(o.getAsJsonObject("rotation").getAsJsonArray("origin"));
                return new Vector3f(at[0], at[1], at[2]);
            }
        }
        throw new AssertionError("musket has no element " + element);
    }

    /**
     * The soldier's musket at a key time of a musket animation, composed the way the game draws it: GeckoLib's loaded
     * key values on the baked navy soldier bones, {@code GeoEntityRenderer#renderRecursively}'s bone transforms
     * ({@link RenderUtil#prepMatrixForBone}) down {@code root -> waist -> right_arm -> right_hand}, the held-item layer's
     * frame ({@link HumanoidGeoRenderer.ItemFrame}), then the musket model's {@code thirdperson_righthand} display
     * transform and the item renderer's half-block offset.
     */
    private static MusketPose musketAt(String animation, double seconds) throws IOException {
        Model model = KeyFramesAdapter.GEO_GSON.fromJson(read(geo(MobKind.NAVY_SOLDIER)), Model.class);
        BakedGeoModel baked = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(model));
        BakedAnimations anims = KeyFramesAdapter.GEO_GSON.fromJson(read(ANIMATIONS).getAsJsonObject("animations"), BakedAnimations.class);
        Animation anim = anims.getAnimation(animation);
        double tick = seconds * 20;
        for (BoneAnimation b : anim.boneAnimations()) {
            GeoBone bone = baked.getBone(b.boneName()).orElseThrow();
            String what = animation + " " + b.boneName();
            if (!b.rotationKeyFrames().xKeyframes().isEmpty()) {
                bone.setRotX(keyedAt(b.rotationKeyFrames().xKeyframes(), tick, what));
                bone.setRotY(keyedAt(b.rotationKeyFrames().yKeyframes(), tick, what));
                bone.setRotZ(keyedAt(b.rotationKeyFrames().zKeyframes(), tick, what));
            }
            if (!b.positionKeyFrames().xKeyframes().isEmpty()) {
                bone.setPosX(keyedAt(b.positionKeyFrames().xKeyframes(), tick, what));
                bone.setPosY(keyedAt(b.positionKeyFrames().yKeyframes(), tick, what));
                bone.setPosZ(keyedAt(b.positionKeyFrames().zKeyframes(), tick, what));
            }
        }
        PoseStack pose = new PoseStack();
        for (String name : List.of("root", "waist", "right_arm", "right_hand")) {
            RenderUtil.prepMatrixForBone(pose, baked.getBone(name).orElseThrow());
        }
        HumanoidGeoRenderer.ItemFrame.apply(pose, baked.getBone("right_hand").orElseThrow());
        Vector3f hand = pose.last().pose().transformPosition(new Vector3f());
        JsonObject musket = read(ASSETS.resolve("models/item/musket.json"));
        JsonObject display = musket.getAsJsonObject("display").getAsJsonObject("thirdperson_righthand");
        float[] r = floats(display.getAsJsonArray("rotation")), t = floats(display.getAsJsonArray("translation")),
                sc = floats(display.getAsJsonArray("scale"));
        new ItemTransform(new Vector3f(r[0], r[1], r[2]), new Vector3f(t[0], t[1], t[2]).mul(1 / 16f),
                new Vector3f(sc[0], sc[1], sc[2])).apply(false, pose);
        pose.translate(-0.5f, -0.5f, -0.5f);
        Vector3f butt = pose.last().pose().transformPosition(musketPoint(musket, "butt_plate").div(16f)).mul(16f);
        Vector3f muzzle = pose.last().pose().transformPosition(musketPoint(musket, "muzzle_band").div(16f)).mul(16f);
        return new MusketPose(butt, muzzle, hand.mul(16f));
    }

    @Test
    void theAimHoldsTheMusketLevelAndStraightAhead() throws IOException {
        MusketPose aim = musketAt(NavySoldier.ANIM_AIM, 0.35);
        assertTrue(Math.abs(aim.pitch()) < 5, "barrel level while aiming, pitch " + aim.pitch());
        assertTrue(Math.abs(aim.yaw()) < 5, "barrel straight ahead while aiming, yaw " + aim.yaw());
        assertTrue(aim.muzzle().z < aim.butt().z - 15, "muzzle in front of the butt: " + aim);
        assertTrue(aim.butt().y > 20, "stock at the shoulder: " + aim);
    }

    @Test
    void theReloadStandsTheMusketOnItsButtWithTheMuzzleUp() throws IOException {
        for (double pour : new double[]{0.75, 1.35, 2.1}) { // pour, pour again, rod down
            MusketPose p = musketAt(NavySoldier.ANIM_RELOAD, pour);
            double fromUp = Math.toDegrees(Math.acos(p.barrel().y));
            assertTrue(fromUp < 20, "muzzle up at " + pour + " s, " + fromUp + " degrees off: " + p);
            assertTrue(p.butt().y < p.hand().y, "butt below the hand at " + pour + " s: " + p);
            assertTrue(p.butt().y < 3, "butt on the ground at " + pour + " s: " + p);
            assertTrue(p.butt().z < -2, "butt in front of the feet at " + pour + " s: " + p);
            assertTrue(p.muzzle().y > 16 && p.muzzle().y < 26, "muzzle at chest height at " + pour + " s: " + p);
        }
        MusketPose cock = musketAt(NavySoldier.ANIM_RELOAD, 4.3);
        assertTrue(cock.pitch() > 10 && cock.pitch() < 45, "raised across the body to cock, pitch " + cock.pitch());
        assertTrue(cock.muzzle().z < cock.butt().z, "muzzle forward while cocking: " + cock);
    }

    @Test
    void theShoveDrivesTheButtForward() throws IOException {
        MusketPose hit = musketAt(NavySoldier.ANIM_SHOVE, 0.25);
        assertTrue(hit.butt().z < hit.muzzle().z - 10, "butt leads, muzzle back: " + hit);
        assertTrue(hit.butt().z < -12, "butt well in front of the chest: " + hit);
        assertTrue(Math.abs(hit.pitch()) < 25, "musket roughly level in the stroke, pitch " + hit.pitch());
    }

    /**
     * GL1 rule 2: {@code HumanoidGeoModel} writes {@link EntityModelData}'s angles into the head as they are, because
     * {@code GeoEntityRenderer} already negated them ({@code new EntityModelData(sit, baby, -netHeadYaw, -headPitch)}).
     * Composed with the renderer's turn by {@code 180 - bodyYaw} the face then looks along Minecraft's look vector.
     */
    @Test
    void theHeadLooksWhereTheEntityLooks() throws IOException {
        Model model = KeyFramesAdapter.GEO_GSON.fromJson(read(geo(MobKind.NAVY_SOLDIER)), Model.class);
        for (float bodyYaw : new float[]{0f, 90f, 200f}) {
            for (float[] look : new float[][]{{30f, 0f}, {-30f, 0f}, {0f, 40f}, {20f, -35f}}) {
                float pitch = look[0], netHeadYaw = look[1];
                EntityModelData data = new EntityModelData(false, false, -netHeadYaw, -pitch); // as GeoEntityRenderer fills it
                BakedGeoModel baked = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(model));
                GeoBone head = baked.getBone("head").orElseThrow();
                head.setRotX(data.headPitch() * net.minecraft.util.Mth.DEG_TO_RAD);
                head.setRotY(data.netHeadYaw() * net.minecraft.util.Mth.DEG_TO_RAD);
                PoseStack pose = new PoseStack();
                pose.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
                for (String name : List.of("root", "waist", "head")) RenderUtil.prepMatrixForBone(pose, baked.getBone(name).orElseThrow());
                // the head cube is -4 24 -4, 8x8x8: centre (0, 28, 0), face centre (0, 28, -4) (the model faces -z)
                Vector3f centre = pose.last().pose().transformPosition(new Vector3f(0, 28, 0).div(16f));
                Vector3f face = pose.last().pose().transformPosition(new Vector3f(0, 28, -4).div(16f));
                Vector3f dir = face.sub(centre).normalize();
                double p = Math.toRadians(pitch), y = Math.toRadians(bodyYaw + netHeadYaw);
                Vector3f want = new Vector3f((float) (-Math.sin(y) * Math.cos(p)), (float) -Math.sin(p), (float) (Math.cos(y) * Math.cos(p)));
                double off = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dir.dot(want)))));
                assertTrue(off < 0.5, "body yaw " + bodyYaw + " look " + pitch + "/" + netHeadYaw + ": the face points " + off + " deg off");
            }
        }
    }
}

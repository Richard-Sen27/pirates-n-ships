package com.richardsenger.piratesnships.crew.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.mob.MeleePose;
import com.richardsenger.piratesnships.mob.client.DuelistArmPose;
import com.richardsenger.piratesnships.mob.client.HumanoidGeoRenderer;
import com.richardsenger.piratesnships.mob.client.MeleeTelegraph;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.util.RenderUtil;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MEL3 rig test of the crew's sword telegraph, composed the way the game draws it (art/README.md "GeckoLib bone rotations
 * in code", rule 5): {@link DuelistArmPose}'s angles written by {@link MeleeTelegraph#pose} (the code the crew's and the
 * M3 mobs' models run) into the baked crew rig, {@link RenderUtil#prepMatrixForBone} down
 * {@code root -> waist -> arm -> hand}, the held-item layer's frame ({@link HumanoidGeoRenderer.ItemFrame}), the
 * cutlass model's third-person display transform and the item renderer's half-block offset. Model space, px: the
 * duelist faces -z, its right is +x, its feet are at y 0 (the renderer's turn by {@code 180 - bodyYaw} makes -z its
 * look direction). A sign error in the angles or in their conversion would swing the blade backwards or up on a strike.
 */
class CrewMeleePoseTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/pirates_n_ships");
    private static final Path RIG = ASSETS.resolve("geo/crew_member.geo.json");
    private static final Path CUTLASS = ASSETS.resolve("models/item/cutlass.json");

    /** The blade: grip, tip and hand locator (px, model space), and the shoulder pivot of the sword arm. */
    record Blade(Vector3f grip, Vector3f tip, Vector3f hand, Vector3f shoulder) {
        Vector3f dir() {
            return new Vector3f(tip).sub(grip).normalize();
        }

        @Override
        public String toString() {
            return "grip " + grip + " tip " + tip + " hand " + hand + " shoulder " + shoulder;
        }
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    /** The centre of the named element of the cutlass model (px, item space). */
    private static Vector3f centre(JsonObject model, String element) {
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject o = e.getAsJsonObject();
            if (element.equals(o.get("name").getAsString())) {
                float[] from = floats(o.getAsJsonArray("from")), to = floats(o.getAsJsonArray("to"));
                return new Vector3f(from[0] + to[0], from[1] + to[1], from[2] + to[2]).mul(0.5f);
            }
        }
        throw new AssertionError("the cutlass has no element " + element);
    }

    /** The cutlass in the main hand of a duelist posed with {@code a} (null: the rest pose); {@code right} its main arm. */
    private static Blade blade(@Nullable DuelistArmPose.Angles a, boolean right) throws IOException {
        Model model = KeyFramesAdapter.GEO_GSON.fromJson(read(RIG), Model.class);
        BakedGeoModel baked = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(model));
        String arm = right ? "right_arm" : "left_arm", hand = right ? "right_hand" : "left_hand";
        if (a != null) {
            MeleeTelegraph.pose(baked.getBone(arm).orElseThrow(), baked.getBone(hand).orElseThrow(),
                    baked.getBone("waist").orElseThrow(), a, right);
        }
        PoseStack pose = new PoseStack();
        for (String name : List.of("root", "waist")) RenderUtil.prepMatrixForBone(pose, baked.getBone(name).orElseThrow());
        GeoBone armBone = baked.getBone(arm).orElseThrow();
        Vector3f shoulder = pose.last().pose().transformPosition(
                new Vector3f(armBone.getPivotX(), armBone.getPivotY(), armBone.getPivotZ()).div(16f)).mul(16f);
        for (String name : List.of(arm, hand)) RenderUtil.prepMatrixForBone(pose, baked.getBone(name).orElseThrow());
        HumanoidGeoRenderer.ItemFrame.apply(pose, baked.getBone(hand).orElseThrow());
        Vector3f handAt = pose.last().pose().transformPosition(new Vector3f()).mul(16f);
        JsonObject cutlass = read(CUTLASS);
        JsonObject display = cutlass.getAsJsonObject("display").getAsJsonObject(right ? "thirdperson_righthand" : "thirdperson_lefthand");
        float[] r = floats(display.getAsJsonArray("rotation")), t = floats(display.getAsJsonArray("translation")),
                sc = floats(display.getAsJsonArray("scale"));
        new ItemTransform(new Vector3f(r[0], r[1], r[2]), new Vector3f(t[0], t[1], t[2]).mul(1 / 16f),
                new Vector3f(sc[0], sc[1], sc[2])).apply(!right, pose);
        pose.translate(-0.5f, -0.5f, -0.5f);
        Vector3f grip = pose.last().pose().transformPosition(centre(cutlass, "grip").div(16f)).mul(16f);
        Vector3f tip = pose.last().pose().transformPosition(centre(cutlass, "clip_point").div(16f)).mul(16f);
        return new Blade(grip, tip, handAt, shoulder);
    }

    private static DuelistArmPose.Angles at(Phase phase, @Nullable AttackKind attack, float progress) {
        DuelistArmPose.Angles a = DuelistArmPose.of(new MeleePose(phase, attack, false, 8), progress);
        assertNotNull(a, phase + " " + attack);
        return a;
    }

    @Test
    void atRestTheBladePointsForward() throws IOException {
        Blade rest = blade(null, true);
        System.out.println("rest " + rest + " dir " + rest.dir());
        assertTrue(rest.tip().z < rest.grip().z - 4, "the resting blade does not point forward: " + rest);
        assertTrue(rest.hand().y < rest.shoulder().y - 8, "the arm does not hang: " + rest);
    }

    @Test
    void theSlashIsRaisedHighThenStrikesForwardAndDown() throws IOException {
        Blade cocked = blade(at(Phase.WINDUP, AttackKind.SLASH, 1f), true);
        Blade struck = blade(at(Phase.ACTIVE, AttackKind.SLASH, 1f), true);
        System.out.println("slash cocked " + cocked + " dir " + cocked.dir());
        System.out.println("slash struck " + struck + " dir " + struck.dir());
        assertTrue(cocked.hand().y > cocked.shoulder().y + 4, "the wind-up does not raise the sword hand: " + cocked);
        assertTrue(cocked.tip().y > 32, "the cocked blade is not above the head: " + cocked);
        // the strike: the tip comes down and forward, sweeping across to the off side
        assertTrue(struck.tip().y < cocked.tip().y - 10, "the strike does not bring the blade down: " + cocked + " -> " + struck);
        assertTrue(struck.tip().z < cocked.tip().z - 4, "the strike does not bring the blade forward: " + cocked + " -> " + struck);
        assertTrue(struck.tip().z < struck.shoulder().z - 6, "the struck blade is not in front: " + struck);
        assertTrue(struck.tip().x < cocked.tip().x, "the strike does not sweep across the body: " + cocked + " -> " + struck);
        assertTrue(struck.hand().y < struck.shoulder().y, "the hand ends below the shoulder: " + struck);
    }

    @Test
    void theThrustDrawsBackThenLungesStraightForward() throws IOException {
        Blade drawn = blade(at(Phase.WINDUP, AttackKind.THRUST, 1f), true);
        Blade lunge = blade(at(Phase.ACTIVE, AttackKind.THRUST, 1f), true);
        System.out.println("thrust drawn " + drawn + " dir " + drawn.dir());
        System.out.println("thrust lunge " + lunge + " dir " + lunge.dir());
        assertTrue(drawn.hand().z > lunge.hand().z + 6, "the hand is not drawn back before the lunge: " + drawn + " / " + lunge);
        assertTrue(lunge.hand().z < lunge.shoulder().z - 8, "the lunge does not reach forward: " + lunge);
        assertTrue(lunge.dir().z < -0.85f, "the lunging blade does not point forward: " + lunge.dir());
        assertTrue(drawn.dir().z < -0.85f, "the drawn blade does not point forward: " + drawn.dir());
        assertTrue(Math.abs(lunge.dir().x) < 0.3f, "the lunge is not straight ahead: " + lunge.dir());
        assertTrue(Math.abs(lunge.hand().y - lunge.shoulder().y) < 6, "the lunge is not at shoulder height: " + lunge);
    }

    @Test
    void theGuardHoldsTheBladeUpAndAcrossAndTheParryHigher() throws IOException {
        Blade guard = blade(at(Phase.GUARDING, null, 1f), true);
        Blade parry = blade(at(Phase.PARRYING, null, 1f), true);
        System.out.println("guard " + guard + " dir " + guard.dir());
        System.out.println("parry " + parry + " dir " + parry.dir());
        assertTrue(guard.hand().z < guard.shoulder().z - 4, "the guard is not held in front: " + guard);
        assertTrue(guard.dir().y > 0.4f, "the guarding blade does not point up: " + guard.dir());
        assertTrue(guard.tip().x < guard.grip().x - 2, "the guarding blade does not lie across the body: " + guard);
        assertTrue(parry.tip().y > guard.tip().y, "the parry is not higher than the guard: " + guard + " / " + parry);
        assertTrue(parry.hand().z < parry.shoulder().z - 4, "the parry is not in front: " + parry);
    }

    @Test
    void aStaggerFlingsTheArmOutAndBack() throws IOException {
        Blade rest = blade(null, true);
        Blade staggered = blade(at(Phase.STAGGERED, null, 1f), true);
        System.out.println("stagger " + staggered);
        assertTrue(staggered.hand().x > rest.hand().x + 2, "the arm is not flung outwards: " + rest + " / " + staggered);
        assertTrue(staggered.hand().z > rest.hand().z, "the arm is not flung back: " + rest + " / " + staggered);
    }

    @Test
    void aPunchReachesForwardAtShoulderHeight() throws IOException {
        DuelistArmPose.Angles peak = DuelistArmPose.punch(0.5f);
        assertNotNull(peak);
        Blade fist = blade(peak, true); // the hand of a bare-handed punch; the blade only matters for a vanilla sword
        System.out.println("punch " + fist);
        assertTrue(fist.hand().z < fist.shoulder().z - 8, "the punch does not reach forward: " + fist);
        assertTrue(Math.abs(fist.hand().y - fist.shoulder().y) < 5, "the punch is not at shoulder height: " + fist);
        assertTrue(fist.hand().x < fist.shoulder().x + 1, "the punch swings outwards instead of in: " + fist);
        assertTrue(DuelistArmPose.punch(0f) == null && DuelistArmPose.punch(1f) == null, "a punch at rest");
    }

    @Test
    void aLeftHandedDuelistMirrorsTheGuard() throws IOException {
        Blade right = blade(at(Phase.GUARDING, null, 1f), true);
        Blade left = blade(at(Phase.GUARDING, null, 1f), false);
        System.out.println("left guard " + left + " dir " + left.dir());
        assertTrue(Math.abs(left.hand().x + right.hand().x) < 0.5f, "the hands are not mirrored: " + right + " / " + left);
        assertTrue(Math.abs(left.hand().y - right.hand().y) < 0.5f && Math.abs(left.hand().z - right.hand().z) < 0.5f,
                "the hands are not mirrored: " + right + " / " + left);
        assertTrue(left.tip().x > left.grip().x + 2, "the left-handed guard does not lie across the body: " + left);
        assertTrue(left.dir().y > 0.4f, "the left-handed guarding blade does not point up: " + left.dir());
    }
}

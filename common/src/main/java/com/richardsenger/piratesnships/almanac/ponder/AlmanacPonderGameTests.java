package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * PND5 with the real registries (no Ponder class: the scenes' subjects are plain data): every subject of every scene
 * is one of our registered blocks or items, a subject's wood family expands to every registered member that is an item,
 * a subject without an item is only a block the scene shows (the railing latch), and Ponder's index keeps exactly one
 * member, the face, of each wood family.
 */
public final class AlmanacPonderGameTests {

    /** Subjects that are blocks without an item: they get no inventory hint, but stay listed as what the scene shows. */
    static final Set<String> BLOCKS_WITHOUT_ITEM = Set.of("railing_latch");

    private AlmanacPonderGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(AlmanacPonderGameTests.class);
    }

    @ModGameTest
    public static void everySubjectIsRegisteredAndExpandsToItsFamily(GameTestHelper helper) {
        Function<ResourceLocation, List<ResourceLocation>> registered = SceneSubjects.registered();
        for (AlmanacScenes.Scene scene : AlmanacScenes.ALL) {
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(scene.component()), scene.id() + ": the component "
                    + scene.component() + " is an item");
            List<ResourceLocation> expanded = scene.expand(registered);
            for (ResourceLocation subject : scene.subjects()) {
                boolean item = BuiltInRegistries.ITEM.containsKey(subject);
                boolean block = BuiltInRegistries.BLOCK.containsKey(subject);
                helper.assertTrue(item || block, scene.id() + ": " + subject + " is registered");
                helper.assertTrue(item || BLOCKS_WITHOUT_ITEM.contains(subject.getPath()),
                        scene.id() + ": " + subject + " has no item and is not a known item-less block");
                WoodFamily<?> family = WoodFamily.ofPath(subject.getPath());
                if (family != null) {
                    for (ResourceLocation member : family.ids()) {
                        helper.assertTrue(expanded.contains(member), scene.id() + ": " + subject + "'s family member "
                                + member + " is a subject");
                    }
                } else if (item) {
                    helper.assertTrue(expanded.contains(subject), scene.id() + ": " + subject + " is a subject");
                }
            }
            for (ResourceLocation id : expanded) {
                helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), scene.id() + ": " + id + " is an item");
            }
        }
        helper.succeed();
    }

    @ModGameTest
    public static void theIndexKeepsOneMemberPerWoodFamily(GameTestHelper helper) {
        Set<ResourceLocation> hidden = SceneSubjects.hiddenFromIndex();
        helper.assertTrue(WoodFamily.all().size() >= 27, "the wood families are registered: " + WoodFamily.all().size());
        for (WoodFamily<?> family : WoodFamily.all()) {
            ResourceLocation lead = family.lead().id();
            helper.assertFalse(hidden.contains(lead), "the face " + lead + " of " + family.suffix() + " stays in the index");
            for (ResourceLocation member : family.ids()) {
                helper.assertTrue(member.equals(lead) || hidden.contains(member), member + " is left out of the index");
            }
        }
        for (AlmanacScenes.Scene scene : AlmanacScenes.ALL) {
            helper.assertFalse(hidden.contains(scene.component()), "the component " + scene.component() + " stays in the index");
        }
        helper.succeed();
    }
}

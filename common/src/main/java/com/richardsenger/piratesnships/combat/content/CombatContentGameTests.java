package com.richardsenger.piratesnships.combat.content;

import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.Collection;
import java.util.List;

/** Registration, tags, attributes and recipes of the {@code combat.content} module. */
public final class CombatContentGameTests {

    public static final List<String> BLOCK_IDS = List.of();
    public static final List<String> ITEM_IDS = List.of("rapier", "cutlass", "saber", "pistol", "musket", "lead_shot", "cannonball", "grappling_hook");

    private CombatContentGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CombatContentGameTests.class);
    }

    @ModGameTest
    public static void combatItemsAreRegistered(GameTestHelper helper) {
        ContentTestSupport.assertRegistered(helper, ITEM_IDS, List.of());
        helper.succeed();
    }

    @ModGameTest
    public static void swordsAreTaggedWithTheirAttributes(GameTestHelper helper) {
        assertSword(helper, CombatContent.RAPIER.get(), CombatContent.RAPIER_DAMAGE, CombatContent.RAPIER_SPEED);
        assertSword(helper, CombatContent.CUTLASS.get(), CombatContent.CUTLASS_DAMAGE, CombatContent.CUTLASS_SPEED);
        assertSword(helper, CombatContent.SABER.get(), CombatContent.SABER_DAMAGE, CombatContent.SABER_SPEED);
        helper.succeed();
    }

    @ModGameTest
    public static void combatRecipesAreLoaded(GameTestHelper helper) {
        ContentTestSupport.assertRecipe(helper, "rapier", CombatContent.RAPIER.get(), 1);
        ContentTestSupport.assertRecipe(helper, "cutlass", CombatContent.CUTLASS.get(), 1);
        ContentTestSupport.assertRecipe(helper, "saber", CombatContent.SABER.get(), 1);
        ContentTestSupport.assertRecipe(helper, "pistol", CombatContent.PISTOL.get(), 1);
        ContentTestSupport.assertRecipe(helper, "musket", CombatContent.MUSKET.get(), 1);
        ContentTestSupport.assertRecipe(helper, "lead_shot", CombatContent.LEAD_SHOT.get(), 4);
        ContentTestSupport.assertRecipe(helper, "cannonball", CombatContent.CANNONBALL.get(), 2);
        ContentTestSupport.assertRecipe(helper, "grappling_hook", CombatContent.GRAPPLING_HOOK.get(), 1);
        helper.succeed();
    }

    private static void assertSword(GameTestHelper helper, Item sword, int damage, float speed) {
        ItemStack stack = new ItemStack(sword);
        helper.assertTrue(stack.is(ItemTags.SWORDS), sword + " is not in #minecraft:swords");
        helper.assertTrue(stack.is(CombatContentModule.C_MELEE_WEAPONS), sword + " is not in #c:tools/melee_weapon");
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double expectedDamage = damage + CombatContent.SWORD_TIER.getAttackDamageBonus();
        helper.assertValueEqual(sum(modifiers, Attributes.ATTACK_DAMAGE), expectedDamage, sword + " attack damage modifier");
        helper.assertValueEqual(sum(modifiers, Attributes.ATTACK_SPEED), (double) speed, sword + " attack speed modifier");
    }

    private static double sum(ItemAttributeModifiers modifiers, Holder<Attribute> attribute) {
        return modifiers.modifiers().stream().filter(e -> e.attribute().equals(attribute)).mapToDouble(e -> e.modifier().amount()).sum();
    }
}

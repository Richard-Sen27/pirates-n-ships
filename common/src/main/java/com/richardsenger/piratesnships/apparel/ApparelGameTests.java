package com.richardsenger.piratesnships.apparel;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.richardsenger.piratesnships.combat.content.ContentTestSupport;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.Collection;
import java.util.List;

/** The hats and the officer's coat of the {@code apparel} module: registration, wearing, the armour bonus and the recipes. */
public final class ApparelGameTests {

    public static final List<String> ITEM_IDS = List.of("pirate_hat", "bandana", "navy_hat", "officer_hat", "captains_hat",
            "officers_coat");

    private ApparelGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(ApparelGameTests.class);
    }

    @ModGameTest
    public static void hatsAreRegisteredHeadEquipment(GameTestHelper helper) {
        ContentTestSupport.assertRegistered(helper, ITEM_IDS, List.of());
        for (RegistryEntry<Item, HatItem> hat : ApparelContent.HATS) {
            ItemStack stack = new ItemStack(hat.get());
            Equipable equipable = Equipable.get(stack);
            helper.assertTrue(equipable != null && equipable.getEquipmentSlot() == EquipmentSlot.HEAD, hat.id() + " should be worn on the head");
            helper.assertValueEqual(stack.getMaxStackSize(), 1, hat.id() + " stack size");
        }
        helper.succeed();
    }

    /** Right-click with a hat: it goes onto the head and whatever was there comes back into the hand. */
    @ModGameTest
    public static void rightClickPutsTheHatOnAndTheOldOneInTheHand(GameTestHelper helper) {
        for (RegistryEntry<Item, HatItem> hat : ApparelContent.HATS) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(hat.get()));
            use(helper, player);
            helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(hat.get()), hat.id() + " should be on the head, got " + player.getItemBySlot(EquipmentSlot.HEAD));
            helper.assertTrue(player.getMainHandItem().is(Items.LEATHER_HELMET), "the helmet should be in the hand, got " + player.getMainHandItem());
            // An empty head takes the hat and leaves the hand empty
            Player bare = helper.makeMockPlayer(GameType.SURVIVAL);
            bare.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(hat.get()));
            use(helper, bare);
            helper.assertTrue(bare.getItemBySlot(EquipmentSlot.HEAD).is(hat.get()), hat.id() + " should be on the bare head");
            helper.assertTrue(bare.getMainHandItem().isEmpty(), "the hand should be empty, got " + bare.getMainHandItem());
        }
        helper.succeed();
    }

    /** What {@code ServerPlayerGameMode.useItem} does with the result: it becomes the hand's stack. */
    private static void use(GameTestHelper helper, Player player) {
        ItemStack stack = player.getMainHandItem();
        InteractionResultHolder<ItemStack> result = stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult().consumesAction(), "using " + stack + " should succeed, got " + result.getResult());
        player.setItemInHand(InteractionHand.MAIN_HAND, result.getObject());
    }

    @ModGameTest
    public static void hatGivesTheConfiguredArmour(GameTestHelper helper) {
        int armor = ApparelConfig.HAT_ARMOR.get();
        for (RegistryEntry<Item, HatItem> hat : ApparelContent.HATS) {
            ItemStack stack = new ItemStack(hat.get());
            assertArmor(helper, stack, EquipmentSlot.HEAD, armor);
            assertArmor(helper, stack, EquipmentSlot.MAINHAND, 0);
        }
        helper.succeed();
    }

    /** Own batch: changes config. */
    @ModGameTest(batch = "pirates_n_ships_config_apparel_hat_armor")
    public static void hatArmourFollowsTheConfig(GameTestHelper helper) {
        ConfigOverrides.during(helper, ApparelConfig.HAT_ARMOR, 3);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ApparelContent.NAVY_HAT.get()));
        use(helper, player);
        ItemStack worn = player.getItemBySlot(EquipmentSlot.HEAD);
        assertArmor(helper, worn, EquipmentSlot.HEAD, 3);
        // What the game applies when the slot changes: the worn hat's modifiers on the player's armour attribute
        player.getAttributes().addTransientAttributeModifiers(modifiers(worn));
        helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR), 3.0, "armour with a hat on");
        helper.succeed();
    }

    /** Own batch: changes config. */
    @ModGameTest(batch = "pirates_n_ships_config_apparel_hat_armor_off")
    public static void hatArmourZeroGivesNoModifier(GameTestHelper helper) {
        ConfigOverrides.during(helper, ApparelConfig.HAT_ARMOR, 0);
        for (RegistryEntry<Item, HatItem> hat : ApparelContent.HATS) {
            ItemStack stack = new ItemStack(hat.get());
            int[] count = {0};
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                stack.forEachModifier(slot, (attribute, modifier) -> count[0]++);
            }
            helper.assertValueEqual(count[0], 0, hat.id() + " modifiers with hat_armor 0");
        }
        helper.succeed();
    }

    @ModGameTest
    public static void hatRecipesAreLoaded(GameTestHelper helper) {
        for (RegistryEntry<Item, HatItem> hat : ApparelContent.CRAFTED_HATS) {
            ContentTestSupport.assertRecipe(helper, hat.id().getPath(), hat.get(), 1);
        }
        ContentTestSupport.assertRecipe(helper, "officers_coat", ApparelContent.OFFICERS_COAT.get(), 1);
        helper.succeed();
    }

    /** The captain's hat is a drop of the named pirate captain (ART6), never crafted. */
    @ModGameTest
    public static void captainsHatHasNoRecipe(GameTestHelper helper) {
        boolean crafted = helper.getLevel().getRecipeManager().getRecipes().stream()
                .anyMatch(r -> r.value().getResultItem(helper.getLevel().registryAccess()).is(ApparelContent.CAPTAINS_HAT.get()));
        helper.assertFalse(crafted, "no recipe should make the captain's hat");
        helper.succeed();
    }

    // --- officer's coat (ART6) ----------------------------------------------------------------------------------

    /** The coat is chest armour: right-click puts it on the chest, swapping with what was there, and it stacks to 1. */
    @ModGameTest
    public static void coatGoesOnTheChest(GameTestHelper helper) {
        ItemStack coat = new ItemStack(ApparelContent.OFFICERS_COAT.get());
        Equipable equipable = Equipable.get(coat);
        helper.assertTrue(equipable != null && equipable.getEquipmentSlot() == EquipmentSlot.CHEST, "the coat should be worn on the chest");
        helper.assertValueEqual(coat.getMaxStackSize(), 1, "coat stack size");
        helper.assertFalse(coat.isDamageableItem(), "the coat has no durability, like the hats");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        player.setItemInHand(InteractionHand.MAIN_HAND, coat);
        use(helper, player);
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).is(ApparelContent.OFFICERS_COAT.get()), "the coat should be on the chest, got " + player.getItemBySlot(EquipmentSlot.CHEST));
        helper.assertTrue(player.getMainHandItem().is(Items.LEATHER_CHESTPLATE), "the old chestplate should be in the hand, got " + player.getMainHandItem());
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "nothing on the head");
        helper.succeed();
    }

    @ModGameTest
    public static void coatGivesTheConfiguredArmour(GameTestHelper helper) {
        ItemStack coat = new ItemStack(ApparelContent.OFFICERS_COAT.get());
        assertArmor(helper, coat, EquipmentSlot.CHEST, ApparelConfig.OFFICERS_COAT_ARMOR.get());
        assertArmor(helper, coat, EquipmentSlot.HEAD, 0);
        assertArmor(helper, coat, EquipmentSlot.MAINHAND, 0);
        helper.succeed();
    }

    /** Own batch: changes config. */
    @ModGameTest(batch = "pirates_n_ships_config_apparel_coat_armor")
    public static void coatArmourFollowsTheConfig(GameTestHelper helper) {
        ConfigOverrides.during(helper, ApparelConfig.OFFICERS_COAT_ARMOR, 5);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ApparelContent.OFFICERS_COAT.get()));
        use(helper, player);
        ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
        assertArmor(helper, worn, EquipmentSlot.CHEST, 5);
        Multimap<Holder<Attribute>, AttributeModifier> map = HashMultimap.create();
        worn.forEachModifier(EquipmentSlot.CHEST, map::put);
        player.getAttributes().addTransientAttributeModifiers(map);
        helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR), 5.0, "armour with the coat on");
        helper.succeed();
    }

    /** Own batch: changes config. */
    @ModGameTest(batch = "pirates_n_ships_config_apparel_coat_armor_off")
    public static void coatArmourZeroGivesNoModifier(GameTestHelper helper) {
        ConfigOverrides.during(helper, ApparelConfig.OFFICERS_COAT_ARMOR, 0);
        ItemStack coat = new ItemStack(ApparelContent.OFFICERS_COAT.get());
        int[] count = {0};
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            coat.forEachModifier(slot, (attribute, modifier) -> count[0]++);
        }
        helper.assertValueEqual(count[0], 0, "coat modifiers with officers_coat_armor 0");
        helper.succeed();
    }

    private static void assertArmor(GameTestHelper helper, ItemStack stack, EquipmentSlot slot, int expected) {
        double[] sum = {0};
        stack.forEachModifier(slot, (attribute, modifier) -> {
            if (attribute.is(Attributes.ARMOR)) {
                sum[0] += modifier.amount();
            }
        });
        helper.assertValueEqual(sum[0], (double) expected, BuiltInRegistries.ITEM.getKey(stack.getItem()) + " armour in " + slot);
    }

    private static Multimap<Holder<Attribute>, AttributeModifier> modifiers(ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> map = HashMultimap.create();
        stack.forEachModifier(EquipmentSlot.HEAD, map::put);
        return map;
    }
}

package com.richardsenger.piratesnships.crew.provisions;

import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Provisions against real items, the food component and the generated tags, in a running server. */
public final class ProvisionsGameTests {

    private static final ProvisionSettings S = ProvisionSettings.DEFAULTS;

    private ProvisionsGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(ProvisionsGameTests.class);
    }

    @ModGameTest
    public static void classifiesRealItems(GameTestHelper helper) {
        ProvisionType bread = expect(helper, new ItemStack(Items.BREAD), ProvisionKind.FOOD);
        helper.assertTrue(bread.valuePerUnit() == 5 && bread.preserved() && !bread.perishable(), "bread: 5 nutrition, preserved");

        ProvisionType beef = expect(helper, new ItemStack(Items.COOKED_BEEF), ProvisionKind.FOOD);
        helper.assertTrue(beef.valuePerUnit() == 8 && !beef.preserved() && beef.perishable(), "cooked beef: 8 nutrition, fresh");
        helper.assertTrue(ProvisionRules.preventsScurvy(beef, S), "fresh food prevents scurvy by default");

        ProvisionType kelp = expect(helper, new ItemStack(Items.DRIED_KELP), ProvisionKind.FOOD);
        helper.assertTrue(kelp.valuePerUnit() == 1 && kelp.preserved(), "dried kelp: 1 nutrition, preserved");
        helper.assertFalse(ProvisionRules.preventsScurvy(kelp, S), "preserved kelp is no citrus");

        ProvisionType apple = expect(helper, new ItemStack(Items.APPLE), ProvisionKind.FOOD);
        helper.assertTrue(apple.antiScurvy(), "apple is tagged anti-scurvy");

        ProvisionType bottle = expect(helper, PotionContents.createItemStack(Items.POTION, Potions.WATER), ProvisionKind.WATER);
        helper.assertTrue(bottle.valuePerUnit() == 1, "water bottle: 1 ration");
        ProvisionType bucket = expect(helper, new ItemStack(Items.WATER_BUCKET), ProvisionKind.WATER);
        helper.assertTrue(bucket.valuePerUnit() == S.waterBucketRations(), "water bucket rations");

        helper.assertTrue(ProvisionClassifier.classify(PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS), S).isEmpty(),
                "a swiftness potion is not water");
        helper.assertTrue(ProvisionClassifier.classify(new ItemStack(Items.STONE), S).isEmpty(), "stone is no provision");
        helper.assertTrue(ProvisionClassifier.classify(new ItemStack(Items.ROTTEN_FLESH), S).isEmpty(), "rotten flesh is excluded");

        helper.assertTrue(ProvisionClassifier.leftover(new ItemStack(Items.MUSHROOM_STEW)).is(Items.BOWL), "stew leaves a bowl");
        helper.assertTrue(ProvisionClassifier.leftover(PotionContents.createItemStack(Items.POTION, Potions.WATER)).is(Items.GLASS_BOTTLE),
                "water bottle leaves a glass bottle");
        helper.assertTrue(ProvisionClassifier.leftover(new ItemStack(Items.WATER_BUCKET)).is(Items.BUCKET), "water bucket leaves a bucket");
        helper.assertTrue(ProvisionClassifier.leftover(new ItemStack(Items.BREAD)).isEmpty(), "bread leaves nothing");
        helper.succeed();
    }

    @ModGameTest
    public static void chestDayOfConsumption(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.CHEST);
        Container chest = helper.getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.BREAD, 64));
        chest.setItem(1, new ItemStack(Items.COOKED_BEEF, 10));
        chest.setItem(2, new ItemStack(Items.STONE, 5));
        for (int i = 3; i < 8; i++) {
            chest.setItem(i, PotionContents.createItemStack(Items.POTION, Potions.WATER));
        }

        ProvisionStore store = ProvisionStacks.toStore(contents(chest), S, ProvisionStore.EMPTY);
        helper.assertTrue(store.units("minecraft:bread") == 64 && store.units("minecraft:cooked_beef") == 10
                && store.units("minecraft:potion") == 5, "store mirrors the chest: " + store);
        helper.assertTrue(store.totalValue(ProvisionKind.FOOD) == 64 * 5 + 10 * 8, "food value");

        // 4 crew for one day: 24 nutrition (3 cooked beef, eaten before the bread because it spoils) and 4 water.
        ProvisionUpdate u = ProvisionRules.advance(store, ProvisioningState.INITIAL, CrewHeadcount.crew(4).withRum(0), S,
                ProvisionSettings.TICKS_PER_DAY);
        List<ItemStack> extra = ProvisionStacks.apply(chest, ProvisionStacks.removals(contents(chest), u.outcome().removed(), S));

        helper.assertTrue(extra.isEmpty(), "leftover bottles fit into the emptied slots");
        helper.assertTrue(chest.getItem(0).is(Items.BREAD) && chest.getItem(0).getCount() == 64, "bread untouched");
        helper.assertTrue(chest.getItem(1).is(Items.COOKED_BEEF) && chest.getItem(1).getCount() == 7, "3 cooked beef eaten");
        helper.assertTrue(chest.getItem(2).is(Items.STONE) && chest.getItem(2).getCount() == 5, "stone untouched");
        helper.assertTrue(count(chest, Items.GLASS_BOTTLE) == 4 && count(chest, Items.POTION) == 1, "4 water bottles drunk");
        helper.assertFalse(u.outcome().hungry() || u.outcome().thirsty(), "crew fed and watered");

        ProvisionStore after = ProvisionStacks.toStore(contents(chest), S, u.store());
        helper.assertTrue(after.equals(u.store()), "store after applying equals the rules' store: " + after + " vs " + u.store());
        helper.succeed();
    }

    @ModGameTest
    public static void configAdapterMatchesDefaults(GameTestHelper helper) {
        helper.assertTrue(ProvisionsConfig.settings().equals(ProvisionSettings.DEFAULTS), "provisions config defaults");
        helper.succeed();
    }

    private static ProvisionType expect(GameTestHelper helper, ItemStack stack, ProvisionKind kind) {
        Optional<ProvisionType> t = ProvisionClassifier.classify(stack, S);
        helper.assertTrue(t.isPresent() && t.get().kind() == kind, stack + " should be " + kind + " but is " + t);
        return t.orElseThrow();
    }

    private static List<ItemStack> contents(Container c) {
        List<ItemStack> l = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            l.add(c.getItem(i));
        }
        return l;
    }

    private static int count(Container c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(item)) {
                n += c.getItem(i).getCount();
            }
        }
        return n;
    }
}

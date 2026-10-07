package com.richardsenger.piratesnships.crew.galley;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.crew.provisions.CrewHeadcount;
import com.richardsenger.piratesnships.crew.provisions.ProvisionSettings;
import com.richardsenger.piratesnships.crew.provisions.ProvisioningState;
import com.richardsenger.piratesnships.crew.provisions.ProvisionsConfig;
import com.richardsenger.piratesnships.trade.content.TradeContent;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Pantry, water barrel, the ship-level entry point and the debug command ({@code crew.galley}). */
public final class GalleyGameTests {

    private static final BlockPos P = new BlockPos(1, 1, 1);

    private GalleyGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(GalleyGameTests.class);
    }

    // --- pantry -------------------------------------------------------------------------------------------------

    /** A pantry placed by a player looking in each direction opens its doors (the model's north side) towards them. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void pantryDoorsFaceThePlacer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (Direction looking : Direction.Plane.HORIZONTAL) {
            BlockPos floor = new BlockPos(1 + 2 * looking.get2DDataValue(), 1, 4);
            helper.setBlock(floor, Blocks.STONE);
            player.setYRot(looking.toYRot());
            ItemStack stack = new ItemStack(CrewContent.PANTRY.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockPos abs = helper.absolutePos(floor);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
            InteractionResult r = ((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
            helper.assertTrue(r.consumesAction(), "placing the pantry looking " + looking + " failed: " + r);
            helper.assertBlockPresent(CrewContent.PANTRY.get(), floor.above());
            helper.assertBlockProperty(floor.above(), PantryBlock.FACING, looking.getOpposite());
        }
        helper.succeed();
    }

    @ModGameTest
    public static void pantryOpensAndKeepsItemsThroughReload(GameTestHelper helper) {
        PantryBlockEntity pantry = pantry(helper, P);
        pantry.setItem(0, new ItemStack(Items.BREAD, 7));
        pantry.setItem(5, new ItemStack(CrewContent.HARDTACK.get(), 3));
        pantry.setItem(9, new ItemStack(Items.GLASS_BOTTLE, 2));
        // a mock server player would join the player list, which Sable's login sync rejects in a test server;
        // so: use the block with a mock player (openMenu is a no-op there) and check the menu the pantry provides
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(P, player);
        helper.assertTrue(pantry.canOpen(player), "pantry should open");
        helper.assertTrue(pantry.createMenu(1, player.getInventory(), player) instanceof ChestMenu menu
                && menu.getContainer() == pantry && menu.getRowCount() == 3, "pantry should provide a 3-row chest menu on itself");

        ProvisionSettings s = ProvisionsConfig.settings();
        long now = helper.getLevel().getGameTime();
        pantry.catchUp(now, s);
        PantryBlockEntity copy = reload(helper, pantry);
        for (int i = 0; i < PantryBlockEntity.SIZE; i++) {
            helper.assertTrue(ItemStack.matches(pantry.getItem(i), copy.getItem(i)), "slot " + i + " changed on reload");
        }
        helper.assertValueEqual(copy.store(s), pantry.store(s), "provisions store after reload");
        helper.assertValueEqual(copy.agedUntil(), pantry.agedUntil(), "pantry clock after reload");
        helper.succeed();
    }

    @ModGameTest
    public static void breakingPantryDropsContents(GameTestHelper helper) {
        PantryBlockEntity pantry = pantry(helper, P);
        pantry.setItem(0, new ItemStack(Items.BREAD, 20));
        pantry.setItem(1, new ItemStack(Items.COBBLESTONE, 5));
        destroyWithDrops(helper, P);
        helper.assertValueEqual(droppedCount(helper, Items.BREAD), 20, "bread dropped");
        helper.assertValueEqual(droppedCount(helper, Items.COBBLESTONE), 5, "cobblestone dropped");
        helper.assertValueEqual(droppedCount(helper, CrewContent.PANTRY.get().asItem()), 1, "pantry dropped");
        helper.succeed();
    }

    @ModGameTest
    public static void pantryComparatorFollowsContents(GameTestHelper helper) {
        PantryBlockEntity pantry = pantry(helper, P);
        BlockPos abs = helper.absolutePos(P);
        helper.assertValueEqual(helper.getLevel().getBlockState(abs).getAnalogOutputSignal(helper.getLevel(), abs), 0, "empty pantry signal");
        pantry.setItem(0, new ItemStack(Items.BREAD, 64));
        helper.assertTrue(helper.getLevel().getBlockState(abs).getAnalogOutputSignal(helper.getLevel(), abs) > 0, "filled pantry gives no signal");
        helper.succeed();
    }

    @ModGameTest(timeoutTicks = 100)
    public static void hopperAboveInsertsOnlyProvisions(GameTestHelper helper) {
        PantryBlockEntity pantry = pantry(helper, P);
        BlockPos hopperPos = P.above();
        helper.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        HopperBlockEntity hopper = (HopperBlockEntity) helper.getBlockEntity(hopperPos);
        hopper.setItem(0, new ItemStack(Items.COBBLESTONE, 2));
        hopper.setItem(1, new ItemStack(Items.BREAD, 2));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(pantry, Items.BREAD), 2, "bread in pantry");
            helper.assertValueEqual(count(pantry, Items.COBBLESTONE), 0, "cobblestone in pantry");
            helper.assertValueEqual(count(hopper, Items.COBBLESTONE), 2, "cobblestone left in hopper");
        });
    }

    @ModGameTest(timeoutTicks = 100)
    public static void hopperBelowExtractsOnlyLeftovers(GameTestHelper helper) {
        BlockPos pantryPos = P.above();
        PantryBlockEntity pantry = pantry(helper, pantryPos);
        pantry.setItem(0, new ItemStack(Items.BREAD, 3));
        pantry.setItem(1, new ItemStack(Items.GLASS_BOTTLE, 2));
        pantry.setItem(2, new ItemStack(Items.ROTTEN_FLESH, 1));
        helper.setBlock(P, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        HopperBlockEntity hopper = (HopperBlockEntity) helper.getBlockEntity(P);
        helper.runAfterDelay(60, () -> {
            helper.assertValueEqual(count(hopper, Items.GLASS_BOTTLE), 2, "bottles pulled out");
            helper.assertValueEqual(count(hopper, Items.ROTTEN_FLESH), 1, "rotten flesh pulled out");
            helper.assertValueEqual(count(hopper, Items.BREAD), 0, "bread pulled out");
            helper.assertValueEqual(count(pantry, Items.BREAD), 3, "bread left in pantry");
            helper.succeed();
        });
    }

    @ModGameTest
    public static void freshFoodSpoilsPreservedDoesNot(GameTestHelper helper) {
        ProvisionSettings s = ProvisionsConfig.settings();
        PantryBlockEntity pantry = pantry(helper, P);
        pantry.setItem(0, new ItemStack(Items.COOKED_BEEF, 6));
        pantry.setItem(1, new ItemStack(Items.APPLE, 4));
        pantry.setItem(2, new ItemStack(CrewContent.HARDTACK.get(), 5));
        pantry.setItem(3, new ItemStack(Items.MUSHROOM_STEW, 1));
        long start = helper.getLevel().getGameTime();
        pantry.catchUp(start, s);
        pantry.catchUp(start + s.freshShelfLifeTicks() / 2, s);
        helper.assertValueEqual(count(pantry, Items.COOKED_BEEF), 6, "beef after half its shelf life");
        pantry.catchUp(start + s.freshShelfLifeTicks() + 1, s);
        helper.assertValueEqual(count(pantry, Items.COOKED_BEEF), 0, "beef after its shelf life");
        helper.assertValueEqual(count(pantry, Items.APPLE), 0, "apples after their shelf life");
        helper.assertValueEqual(count(pantry, Items.MUSHROOM_STEW), 0, "stew after its shelf life");
        helper.assertValueEqual(count(pantry, Items.BOWL), 1, "the stew's bowl stays");
        helper.assertValueEqual(count(pantry, CrewContent.HARDTACK.get()), 5, "hardtack is preserved");
        helper.assertValueEqual(count(pantry, Items.ROTTEN_FLESH), 11, "one rotten flesh per spoiled item");
        helper.assertValueEqual(PantryInfo.of(pantry.store(s), s).foodItems(), 5, "only the hardtack is left as food");
        helper.succeed();
    }

    @ModGameTest(batch = "pirates_n_ships_config_crew_galley_spoiled_nothing")
    public static void spoiledFoodCanVanish(GameTestHelper helper) {
        ConfigOverrides.during(helper, ProvisionsConfig.SPOILED_FOOD_RESULT, com.richardsenger.piratesnships.crew.provisions.SpoiledFood.NOTHING);
        ProvisionSettings s = ProvisionsConfig.settings();
        PantryBlockEntity pantry = pantry(helper, P);
        pantry.setItem(0, new ItemStack(Items.COOKED_BEEF, 6));
        long start = helper.getLevel().getGameTime();
        pantry.catchUp(start, s);
        pantry.catchUp(start + s.freshShelfLifeTicks() + 1, s);
        helper.assertValueEqual(count(pantry, Items.COOKED_BEEF), 0, "beef after its shelf life");
        helper.assertValueEqual(count(pantry, Items.ROTTEN_FLESH), 0, "rotten flesh");
        helper.succeed();
    }

    // --- water barrel -------------------------------------------------------------------------------------------

    @ModGameTest
    public static void waterBarrelFillsAndEmptiesWithBucketsAndBottles(GameTestHelper helper) {
        ProvisionSettings s = ProvisionsConfig.settings();
        helper.setBlock(P, CrewContent.WATER_BARREL.get());
        WaterBarrelBlockEntity barrel = (WaterBarrelBlockEntity) helper.getBlockEntity(P);
        helper.assertValueEqual(barrel.rations(), 0, "a barrel set without an item starts empty");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        use(helper, player, new ItemStack(Items.WATER_BUCKET));
        helper.assertValueEqual(barrel.rations(), s.waterBucketRations(), "rations after a water bucket");
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "the bucket should come back empty");
        use(helper, player, PotionContents.createItemStack(Items.POTION, Potions.WATER));
        helper.assertValueEqual(barrel.rations(), s.waterBucketRations() + 1, "rations after a water bottle");
        helper.assertTrue(player.getMainHandItem().is(Items.GLASS_BOTTLE), "the bottle should come back empty");
        helper.assertValueEqual(fill(helper), WaterBarrelRules.fillLevel(barrel.rations(), WaterBarrelRules.capacity(s)), "fill level");

        use(helper, player, new ItemStack(Items.GLASS_BOTTLE));
        helper.assertValueEqual(barrel.rations(), s.waterBucketRations(), "rations after filling a bottle");
        PotionContents potion = player.getMainHandItem().get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        helper.assertTrue(player.getMainHandItem().is(Items.POTION) && potion != null && potion.is(Potions.WATER), "should hold a water bottle");
        use(helper, player, new ItemStack(Items.BUCKET));
        helper.assertValueEqual(barrel.rations(), 0, "rations after filling a bucket");
        helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET), "should hold a water bucket");
        helper.assertValueEqual(fill(helper), 0, "fill level of the empty barrel");
        use(helper, player, new ItemStack(Items.BUCKET));
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "an empty barrel gives no water");

        barrel.setRations(WaterBarrelRules.capacity(s) - 1, s);
        use(helper, player, new ItemStack(Items.WATER_BUCKET));
        helper.assertValueEqual(barrel.rations(), WaterBarrelRules.capacity(s) - 1, "a bucket that does not fit is refused");
        helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET), "the refused bucket stays full");

        BlockPos abs = helper.absolutePos(P);
        helper.assertValueEqual(helper.getLevel().getBlockState(abs).getAnalogOutputSignal(helper.getLevel(), abs),
                WaterBarrelRules.comparator(barrel.rations(), WaterBarrelRules.capacity(s)), "comparator signal");
        WaterBarrelBlockEntity copy = reload(helper, barrel);
        helper.assertValueEqual(copy.rations(), barrel.rations(), "rations after reload");
        helper.assertValueEqual(fill(helper), WaterBarrelRules.MAX_FILL - 1, "fill level of an almost full barrel");
        helper.succeed();
    }

    @ModGameTest
    public static void waterBarrelKeepsRationsWhenBroken(GameTestHelper helper) {
        ProvisionSettings s = ProvisionsConfig.settings();
        helper.setBlock(P, CrewContent.WATER_BARREL.get());
        WaterBarrelBlockEntity barrel = (WaterBarrelBlockEntity) helper.getBlockEntity(P);
        barrel.setRations(5, s);
        destroyWithDrops(helper, P);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(P)).inflate(2));
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(CrewContent.WATER_BARREL.get().asItem()), "should drop the barrel, dropped " + drops);
        ItemStack item = drops.getFirst().getItem();
        helper.assertValueEqual(item.get(CrewContent.WATER_RATIONS.get()), 5, "rations on the dropped item");
        helper.assertTrue(com.richardsenger.piratesnships.crew.provisions.ProvisionClassifier.classify(item, s)
                .map(t -> t.valuePerUnit() == 5).orElse(false), "the dropped barrel should count 5 water rations");
        helper.succeed();
    }

    @ModGameTest
    public static void noRainNoRefill(GameTestHelper helper) {
        helper.setBlock(P, CrewContent.WATER_BARREL.get());
        WaterBarrelBlockEntity barrel = (WaterBarrelBlockEntity) helper.getBlockEntity(P);
        if (!helper.getLevel().isRainingAt(helper.absolutePos(P).above())) {
            WaterBarrelBlock.rainTick(helper.getLevel(), helper.absolutePos(P), 0.0);
            helper.assertValueEqual(barrel.rations(), 0, "rations without rain");
        }
        helper.succeed();
    }

    // --- ship entry point and debug command ---------------------------------------------------------------------

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void shipProvisionsFeedACrewForADay(GameTestHelper helper) {
        ProvisionSettings s = ProvisionsConfig.settings();
        Setup x = shipSetup(helper);
        ShipProvisions.Result r = ShipProvisions.advance(helper.getLevel(), x.positions, ProvisioningState.INITIAL, CrewHeadcount.crew(4),
                ProvisionSettings.TICKS_PER_DAY);
        helper.assertValueEqual(r.pantries(), 2, "pantries in the update");
        helper.assertValueEqual(r.barrels(), 1, "barrels in the update");
        helper.assertTrue(!r.update().outcome().hungry() && !r.update().outcome().thirsty(), "the crew should be fed and watered");

        // fresh beef (8 nutrition each) is eaten before the hardtack (4 each), which keeps
        double demand = 4 * s.foodPerCrewPerDay() * s.consumptionRate();
        int eatenBeef = r.update().outcome().consumed().getOrDefault("minecraft:cooked_beef", 0);
        int eatenTack = r.update().outcome().consumed().getOrDefault(CrewContent.HARDTACK.id().toString(), 0);
        helper.assertValueEqual(eatenBeef, Math.min(2, (int) Math.ceil(demand / 8 - 1e-9)), "beef eaten first");
        double eaten = eatenBeef * 8 + eatenTack * 4;
        helper.assertTrue(eaten + 1e-9 >= demand && eaten < demand + 8, "nutrition eaten " + eaten + " for a demand of " + demand);
        helper.assertValueEqual(count(x.a, Items.COOKED_BEEF), 2 - eatenBeef, "beef left in pantry A");
        helper.assertValueEqual(count(x.a, CrewContent.HARDTACK.get()), 20 - eatenTack, "hardtack left in pantry A");

        int water = (int) Math.ceil(4 * s.waterPerCrewPerDay() * s.consumptionRate() - 1e-9);
        int bottles = Math.min(4, water);
        helper.assertValueEqual(countWaterBottles(x.b), 4 - bottles, "water bottles left in pantry B (drunk before barrel water)");
        helper.assertValueEqual(count(x.b, Items.GLASS_BOTTLE) - 0, bottles + rumEaten(r), "empty bottles from water and rum");
        helper.assertValueEqual(x.barrel.rations(), 16 - Math.max(0, water - 4), "barrel water");
        helper.assertValueEqual(count(x.b, TradeContent.RUM.get()), 6 - rumEaten(r), "rum left");
        helper.assertValueEqual(count(x.a, Items.COBBLESTONE), 3, "non-provisions are untouched");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_crew_galley_consumption_off")
    public static void consumptionOffShrinksNothing(GameTestHelper helper) {
        ConfigOverrides.during(helper, ProvisionsConfig.CONSUMPTION_ENABLED, false);
        Setup x = shipSetup(helper);
        ShipProvisions.Result r = ShipProvisions.advance(helper.getLevel(), x.positions, ProvisioningState.INITIAL, CrewHeadcount.crew(4),
                10 * ProvisionSettings.TICKS_PER_DAY);
        helper.assertTrue(r.update().outcome().consumed().isEmpty(), "nothing consumed");
        helper.assertValueEqual(count(x.a, CrewContent.HARDTACK.get()), 20, "hardtack");
        helper.assertValueEqual(count(x.a, Items.COOKED_BEEF), 2, "beef (no spoilage either)");
        helper.assertValueEqual(countWaterBottles(x.b), 4, "water bottles");
        helper.assertValueEqual(x.barrel.rations(), 16, "barrel water");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void debugCommandReportsPantry(GameTestHelper helper) {
        // in the middle of the 9-wide template, so no other test's pantry is within the group radius
        BlockPos at = new BlockPos(4, 1, 4);
        PantryBlockEntity pantry = pantry(helper, at);
        pantry.setItem(0, new ItemStack(CrewContent.HARDTACK.get(), 12));
        BlockPos abs = helper.absolutePos(at);
        List<Component> out = run(helper, "pirates provisions show 4 " + abs.getX() + " " + abs.getY() + " " + abs.getZ());
        helper.assertValueEqual(out.size(), 4, "show lines " + out);
        assertKey(helper, out.get(0), GalleyText.CMD_CONTAINERS, 1, 0, ProvisionsCommands.GROUP_RADIUS);
        assertKey(helper, out.get(1), GalleyText.PANTRY_INFO, 12, "48.0");
        assertKey(helper, out.get(2), GalleyText.NO_SPOIL);
        ProvisionSettings s = ProvisionsConfig.settings();
        String foodDays = GalleyText.number(48 / (4 * s.foodPerCrewPerDay() * s.consumptionRate()));
        assertKey(helper, out.get(3), GalleyText.CMD_DAYS, 4, foodDays);

        List<Component> adv = run(helper, "pirates provisions advance 1 4 0 0 " + abs.getX() + " " + abs.getY() + " " + abs.getZ());
        helper.assertTrue(adv.stream().anyMatch(c -> key(c).equals(GalleyText.CMD_CONSUMED)), "advance should report what was eaten: " + adv);
        int expected = 12 - (int) Math.ceil(4 * s.foodPerCrewPerDay() * s.consumptionRate() / 4.0 - 1e-9);
        helper.assertValueEqual(count(pantry, CrewContent.HARDTACK.get()), expected, "hardtack after a simulated day");
        helper.succeed();
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private record Setup(PantryBlockEntity a, PantryBlockEntity b, WaterBarrelBlockEntity barrel, List<BlockPos> positions) {
    }

    /** Pantry A: 20 hardtack, 2 cooked beef, 3 cobblestone. Pantry B: 4 water bottles, 6 rum. Barrel: 16 rations. */
    private static Setup shipSetup(GameTestHelper helper) {
        ProvisionSettings s = ProvisionsConfig.settings();
        PantryBlockEntity a = pantry(helper, new BlockPos(1, 1, 1));
        PantryBlockEntity b = pantry(helper, new BlockPos(3, 1, 1));
        a.setItem(0, new ItemStack(CrewContent.HARDTACK.get(), 20));
        a.setItem(1, new ItemStack(Items.COOKED_BEEF, 2));
        a.setItem(2, new ItemStack(Items.COBBLESTONE, 3));
        for (int i = 0; i < 4; i++) {
            b.setItem(i, PotionContents.createItemStack(Items.POTION, Potions.WATER));
        }
        b.setItem(4, new ItemStack(TradeContent.RUM.get(), 6));
        helper.setBlock(new BlockPos(5, 1, 1), CrewContent.WATER_BARREL.get());
        WaterBarrelBlockEntity barrel = (WaterBarrelBlockEntity) helper.getBlockEntity(new BlockPos(5, 1, 1));
        barrel.setRations(16, s);
        List<BlockPos> positions = List.of(helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(3, 1, 1)),
                helper.absolutePos(new BlockPos(5, 1, 1)));
        return new Setup(a, b, barrel, positions);
    }

    /** {@code GameTestHelper.destroyBlock} drops nothing; this breaks the block like a player without a tool. */
    private static void destroyWithDrops(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
    }

    private static int rumEaten(ShipProvisions.Result r) {
        return r.update().outcome().consumed().getOrDefault(TradeContent.RUM.id().toString(), 0);
    }

    private static PantryBlockEntity pantry(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, CrewContent.PANTRY.get());
        return (PantryBlockEntity) helper.getBlockEntity(pos);
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> T reload(GameTestHelper helper, T be) {
        CompoundTag tag = be.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockEntity copy = BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), tag, helper.getLevel().registryAccess());
        helper.assertTrue(copy != null && copy.getClass() == be.getClass(), "block entity did not reload");
        return (T) copy;
    }

    private static void use(GameTestHelper helper, Player player, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        helper.useBlock(P, player);
    }

    private static int fill(GameTestHelper helper) {
        return helper.getLevel().getBlockState(helper.absolutePos(P)).getValue(WaterBarrelBlock.FILL);
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

    private static int countWaterBottles(Container c) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            PotionContents p = c.getItem(i).get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            if (c.getItem(i).is(Items.POTION) && p != null && p.is(Potions.WATER)) {
                n += c.getItem(i).getCount();
            }
        }
        return n;
    }

    private static int droppedCount(GameTestHelper helper, Item item) {
        int n = 0;
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(P)).inflate(3))) {
            if (e.getItem().is(item)) {
                n += e.getItem().getCount();
            }
        }
        return n;
    }

    private static List<Component> run(GameTestHelper helper, String command) {
        List<Component> out = new ArrayList<>();
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(Component component) {
                out.add(component);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
                .withSource(capture).withLevel(helper.getLevel()).withPermission(4);
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source.withSource(capture), command);
        return out;
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    private static void assertKey(GameTestHelper helper, Component c, String key, Object... firstArgs) {
        helper.assertTrue(c.getContents() instanceof TranslatableContents, "not translatable: " + c);
        TranslatableContents t = (TranslatableContents) c.getContents();
        helper.assertValueEqual(t.getKey(), key, "message key");
        for (int i = 0; i < firstArgs.length; i++) {
            helper.assertValueEqual(String.valueOf(t.getArgs()[i]), String.valueOf(firstArgs[i]), key + " argument " + i);
        }
    }
}

package com.richardsenger.piratesnships.seachest;

import com.richardsenger.piratesnships.combat.content.ContentTestSupport;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/**
 * GameTests of the sea chest (S1, docs/design.md §11): contents in every state, launching and picking up the
 * floating chest, buoyancy and wind drift, the wearer's restrictions, ships, and the toggle.
 */
public final class SeaChestGameTests {

    private static final double PLAYER_BASE_SPEED = 0.1;
    private static final double PLAYER_BASE_JUMP = 0.42;

    private SeaChestGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(SeaChestGameTests.class);
    }

    // ------------------------------------------------------------------ block and item

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void blockKeepsContentsWhenBrokenAndCloned(GameTestHelper h) {
        ContentTestSupport.assertRecipe(h, "sea_chest", SeaChestContent.ITEM.get(), 1);
        h.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, SeaChestTestSupport.filledChest());
        SeaChestTestSupport.useOnTop(h, player, new BlockPos(2, 1, 2));
        BlockPos rel = new BlockPos(2, 2, 2);
        h.assertBlockPresent(SeaChestContent.BLOCK.get(), rel);
        h.assertTrue(player.getMainHandItem().isEmpty(), "the placed item should be used up");
        BlockPos pos = h.absolutePos(rel);
        ServerLevel level = h.getLevel();
        if (!(level.getBlockEntity(pos) instanceof SeaChestBlockEntity be)) {
            throw new AssertionError("no sea chest block entity");
        }
        h.assertTrue(be.getItem(0).is(Items.DIAMOND) && be.getItem(0).getCount() == 5 && be.getItem(30).getCount() == 12,
                "the placed block should hold the item's contents");
        h.assertTrue(be.getDisplayName().getString().equals("Loot"), "the custom name should carry over, got " + be.getDisplayName().getString());

        BlockState state = level.getBlockState(pos);
        // Survival harvest (the same call vanilla's harvest makes)
        Player survivor = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        survivor.setItemInHand(InteractionHand.MAIN_HAND, axe);
        List<ItemStack> drops = Block.getDrops(state, level, pos, be, survivor, axe);
        h.assertTrue(drops.size() == 1 && SeaChestTestSupport.holdsFilledContents(drops.getFirst()),
                "breaking should drop exactly one chest with the contents, dropped " + drops);
        h.assertTrue("Loot".equals(drops.getFirst().getHoverName().getString()), "the dropped chest should keep its name");

        // Creative pick-block keeps the contents
        ItemStack clone = state.getBlock().getCloneItemStack(level, pos, state);
        h.assertTrue(SeaChestTestSupport.holdsFilledContents(clone), "pick-block should keep the contents, got " + clone);

        // Creative breaking drops the chest with its contents, like a shulker box
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        state.getBlock().playerWillDestroy(level, pos, state, creative);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5));
        h.assertTrue(items.size() == 1 && SeaChestTestSupport.holdsFilledContents(items.getFirst().getItem()),
                "creative breaking should drop one chest with the contents, got " + items.size() + " item entities");
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void chestRefusesAnotherSeaChest(GameTestHelper h) {
        h.setBlock(new BlockPos(2, 2, 2), SeaChestContent.BLOCK.get());
        if (!(h.getBlockEntity(new BlockPos(2, 2, 2)) instanceof SeaChestBlockEntity be)) {
            throw new AssertionError("no sea chest block entity");
        }
        h.assertFalse(be.canPlaceItem(0, new ItemStack(SeaChestContent.ITEM.get())), "a sea chest must not go into a sea chest");
        h.assertTrue(be.canPlaceItem(0, new ItemStack(Items.SHULKER_BOX)), "other items (even shulker boxes) fit");
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        SeaChestMenu menu = new SeaChestMenu(1, player.getInventory(), be);
        h.assertTrue(menu.slots.size() == 54 + 36, "six rows plus the player inventory");
        h.assertFalse(menu.getSlot(5).mayPlace(new ItemStack(SeaChestContent.ITEM.get())), "the menu slot must refuse a sea chest");
        h.assertTrue(menu.getSlot(5).mayPlace(new ItemStack(Items.DIAMOND)), "the menu slot takes other items");
        h.succeed();
    }

    // ------------------------------------------------------------------ the floating chest

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 120)
    public static void placingIntoWaterLaunchesTheChest(GameTestHelper h) {
        SeaChestTestSupport.basin(h, 9, 4);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, SeaChestTestSupport.filledChest());
        SeaChestTestSupport.useOnTop(h, player, new BlockPos(4, 1, 4));
        h.assertTrue(player.getMainHandItem().isEmpty(), "launching should use up the item");
        h.assertBlockNotPresent(SeaChestContent.BLOCK.get(), new BlockPos(4, 2, 4));
        List<SeaChestEntity> chests = h.getEntities(SeaChestContent.ENTITY.get());
        h.assertTrue(chests.size() == 1, "expected one floating chest, found " + chests.size());
        h.assertTrue(SeaChestTestSupport.holdsFilledContents(chests.getFirst().toItem()), "the floating chest should hold the contents");
        h.assertTrue(chests.getFirst().getCustomName() != null && "Loot".equals(chests.getFirst().getCustomName().getString()), "name kept");
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 120)
    public static void pickingUpGivesTheItemWithContents(GameTestHelper h) {
        SeaChestTestSupport.basin(h, 9, 4);
        ServerLevel level = h.getLevel();
        SeaChestEntity a = SeaChestEntity.fromItem(level, h.absoluteVec(new Vec3(2.5, 4.4, 2.5)), 0f, SeaChestTestSupport.filledChest());
        SeaChestEntity b = SeaChestEntity.fromItem(level, h.absoluteVec(new Vec3(6.5, 4.4, 6.5)), 0f, SeaChestTestSupport.filledChest());
        level.addFreshEntity(a);
        level.addFreshEntity(b);
        // Sneak-use picks it up into the inventory
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        a.interact(player, InteractionHand.MAIN_HAND);
        h.assertTrue(a.isRemoved() && a.isEmpty(), "the picked-up chest should be gone and empty");
        int found = 0;
        for (ItemStack s : player.getInventory().items) {
            if (SeaChestTestSupport.holdsFilledContents(s)) found++;
        }
        h.assertTrue(found == 1, "the player should hold exactly one chest with the contents, found " + found);
        // A hit knocks it loose as a dropped item
        Player hitter = h.makeMockPlayer(GameType.SURVIVAL);
        b.hurt(level.damageSources().playerAttack(hitter), 1.0f);
        h.assertTrue(b.isRemoved() && b.isEmpty(), "the hit chest should be gone and empty");
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, b.getBoundingBox().inflate(2.0));
        h.assertTrue(drops.size() == 1 && SeaChestTestSupport.holdsFilledContents(drops.getFirst().getItem()),
                "the hit should drop one chest with the contents, got " + drops.size());
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 100)
    public static void risesToTheSurfaceWhenSpawnedUnderWater(GameTestHelper h) {
        SeaChestTestSupport.basin(h, 9, 5);
        ServerLevel level = h.getLevel();
        SeaChestEntity chest = SeaChestEntity.fromItem(level, h.absoluteVec(new Vec3(4.5, 2.05, 4.5)), 0f, SeaChestTestSupport.filledChest());
        level.addFreshEntity(chest);
        double surface = h.absolutePos(new BlockPos(0, 5, 0)).getY() + 8.0 / 9.0;
        double draft = SeaChestConfig.DRAFT.get() * chest.getBbHeight();
        // Settled: the bottom within a quarter block of the rest draft below the waterline, and nearly still
        h.succeedWhen(() -> {
            double depth = surface - chest.getY();
            h.assertTrue(Math.abs(depth - draft) < 0.25, "draft " + depth + ", expected about " + draft);
            h.assertTrue(Math.abs(chest.getDeltaMovement().y) < 0.03, "still moving: " + chest.getDeltaMovement().y);
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = "pirates_n_ships_seachest_wind")
    public static void driftsEastInAWestWind(GameTestHelper h) {
        SeaChestTestSupport.basin(h, 24, 4);
        ServerLevel level = h.getLevel();
        String dim = level.dimension().location().toString();
        WindOverride.set(dim, 270.0, 6.0, level.getGameTime() + 140);
        SeaChestEntity chest = SeaChestEntity.fromItem(level, h.absoluteVec(new Vec3(3.5, 4.5, 12.5)), 0f, new ItemStack(SeaChestContent.ITEM.get()));
        level.addFreshEntity(chest);
        double x0 = chest.getX();
        double z0 = chest.getZ();
        h.runAfterDelay(100, () -> {
            WindOverride.clear(dim);
            double dx = chest.getX() - x0;
            double dz = chest.getZ() - z0;
            h.assertTrue(dx > 3.0, "drifted only " + dx + " blocks east in 100 ticks");
            h.assertTrue(Math.abs(dz) < 1.0, "drifted sideways by " + dz);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ wearing

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void wornChestRestrictsTheWearer(GameTestHelper h) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        Player player = SeaChestTestSupport.playerInLevel(h, new Vec3(4.5, 2.0, 4.5));
        player.setXRot(-90f); // looking straight up: nothing in reach, so use = wear
        player.setItemInHand(InteractionHand.MAIN_HAND, SeaChestTestSupport.filledChest());
        h.assertTrue(player.getEquipmentSlotForItem(player.getMainHandItem()) == EquipmentSlot.CHEST, "the chest belongs in the chest slot");
        player.getMainHandItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(SeaChestTestSupport.holdsFilledContents(player.getItemBySlot(EquipmentSlot.CHEST)), "use in the air should put it on, contents and all");
        h.assertTrue(player.getMainHandItem().isEmpty(), "the hand should be empty after putting it on");
        player.setSprinting(true);
        h.runAfterDelay(2, () -> {
            h.assertFalse(player.isSprinting(), "the wearer should stop sprinting");
            double jump = player.getAttributeValue(Attributes.JUMP_STRENGTH);
            double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            h.assertTrue(jump < 1.0e-6, "jump strength should be 0, is " + jump);
            double expected = PLAYER_BASE_SPEED * SeaChestConfig.WORN_SPEED_MULTIPLIER.get();
            h.assertTrue(Math.abs(speed - expected) < 1.0e-6, "speed should be " + expected + ", is " + speed);
            // Taking it off lifts the restrictions
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            h.runAfterDelay(2, () -> {
                h.assertTrue(Math.abs(player.getAttributeValue(Attributes.JUMP_STRENGTH) - PLAYER_BASE_JUMP) < 1.0e-6, "jump restored");
                h.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - PLAYER_BASE_SPEED) < 1.0e-6, "speed restored");
                h.succeed();
            });
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void wornChestSinksItsWearer(GameTestHelper h) {
        SeaChestTestSupport.basin(h, 24, 11);
        Player wearer = SeaChestTestSupport.playerInLevel(h, new Vec3(6.5, 7.0, 12.5));
        Player control = SeaChestTestSupport.playerInLevel(h, new Vec3(17.5, 7.0, 12.5));
        wearer.setItemSlot(EquipmentSlot.CHEST, new ItemStack(SeaChestContent.ITEM.get()));
        double w0 = wearer.getY();
        double c0 = control.getY();
        h.runAfterDelay(40, () -> {
            double dw = wearer.getY() - w0;
            double dc = control.getY() - c0;
            h.assertTrue(dw < -2.0, "the wearer should sink, moved " + dw);
            h.assertTrue(dw < dc - 1.5, "the wearer should sink much faster than a player without the chest (" + dw + " vs " + dc + ")");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ ships

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 100)
    public static void onAShipDeckTheItemPlacesTheBlock(GameTestHelper h) {
        SeaChestTestSupport.basin(h, 9, 3);
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                h.setBlock(new BlockPos(x, 3, z), Blocks.OAK_PLANKS);
            }
        }
        BlockPos helm = new BlockPos(4, 4, 4);
        h.setBlock(helm, AssemblyContent.HELM.get());
        AssemblyResult r = ShipTestCleanup.assemble(h, helm);
        if (r.shipId() == null) {
            throw new AssertionError("assembly failed: " + r);
        }
        ShipBody ship = SableShips.byId(h.getLevel(), r.shipId());
        if (ship == null) throw new AssertionError("no ship after assembly");
        ServerLevel level = h.getLevel();
        BlockPos deck = null;
        for (BlockPos p : ship.plotBlocks()) {
            if (level.getBlockState(p).is(Blocks.OAK_PLANKS) && level.getBlockState(p.above()).isAir()) {
                deck = p;
                break;
            }
        }
        if (deck == null) throw new AssertionError("no free deck plank in the plot");
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, SeaChestTestSupport.filledChest());
        SeaChestTestSupport.useOnTopAbsolute(player, deck);
        h.assertTrue(level.getBlockState(deck.above()).is(SeaChestContent.BLOCK.get()), "on deck the chest should be placed as a block");
        h.assertTrue(level.getBlockEntity(deck.above()) instanceof SeaChestBlockEntity be && be.getItem(0).getCount() == 5,
                "the block on deck should hold the contents");
        h.assertTrue(h.getEntities(SeaChestContent.ENTITY.get()).isEmpty(), "no floating chest on a ship");
        h.succeed();
    }

    // ------------------------------------------------------------------ toggle

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60, batch = "pirates_n_ships_config_seachest_disabled")
    public static void disabledChestIsAPlainBlockItem(GameTestHelper h) {
        ConfigOverrides.during(h, SeaChestConfig.ENABLED, false);
        SeaChestTestSupport.basin(h, 9, 3);
        Player placer = h.makeMockPlayer(GameType.SURVIVAL);
        placer.setItemInHand(InteractionHand.MAIN_HAND, SeaChestTestSupport.filledChest());
        SeaChestTestSupport.useOnTop(h, placer, new BlockPos(4, 1, 4));
        h.assertTrue(h.getEntities(SeaChestContent.ENTITY.get()).isEmpty(), "disabled: nothing floats");
        h.assertBlockPresent(SeaChestContent.BLOCK.get(), new BlockPos(4, 2, 4));

        Player player = SeaChestTestSupport.playerInLevel(h, new Vec3(2.5, 4.0, 2.5));
        player.setXRot(-90f);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SeaChestContent.ITEM.get()));
        h.assertTrue(player.getEquipmentSlotForItem(player.getMainHandItem()) != EquipmentSlot.CHEST, "disabled: not wearable");
        player.getMainHandItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "disabled: use must not put it on");
        // Forced on anyway (e.g. worn when the toggle was switched off): inert
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(SeaChestContent.ITEM.get()));
        h.runAfterDelay(2, () -> {
            h.assertTrue(Math.abs(player.getAttributeValue(Attributes.JUMP_STRENGTH) - PLAYER_BASE_JUMP) < 1.0e-6, "disabled: jumping unchanged");
            h.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - PLAYER_BASE_SPEED) < 1.0e-6, "disabled: speed unchanged");
            h.succeed();
        });
    }
}

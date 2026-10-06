package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.crew.galley.PantryBlockEntity;
import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.law.content.LawContent;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import com.richardsenger.piratesnships.trade.cargo.CargoContainerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * GameTests of the NeoForge platform wiring that common code can't reach: the item handler capability that
 * {@link NeoForgeCapabilityHelper} registers, and the real {@code PlayerInteractEvent.EntityInteract} path into
 * {@code CommonEvents.ENTITY_INTERACT}. Registered by the entry point next to the modules' test classes.
 */
public final class NeoForgeGameTests {

    private static final BlockPos P = new BlockPos(1, 1, 1);

    private NeoForgeGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(NeoForgeGameTests.class);
    }

    private static @Nullable IItemHandler handler(GameTestHelper helper, BlockPos pos, @Nullable Direction side) {
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), side);
    }

    @ModGameTest
    public static void pantryItemHandlerInsertsProvisionsAndExtractsTheRest(GameTestHelper helper) {
        helper.setBlock(P, CrewContent.PANTRY.get());
        PantryBlockEntity pantry = (PantryBlockEntity) helper.getBlockEntity(P);
        IItemHandler h = handler(helper, P, Direction.UP);
        helper.assertTrue(h != null, "pantry exposes an item handler");
        helper.assertTrue(handler(helper, P, null) != null, "pantry exposes an item handler without a side");

        ItemStack junk = new ItemStack(Items.COBBLESTONE, 2);
        helper.assertValueEqual(h.insertItem(0, junk, false).getCount(), 2, "cobblestone refused");
        helper.assertTrue(pantry.isEmpty(), "nothing went in");
        helper.assertTrue(h.insertItem(0, new ItemStack(Items.BREAD, 3), false).isEmpty(), "bread accepted");
        helper.assertValueEqual(pantry.getItem(0).getCount(), 3, "bread in the pantry");

        pantry.setItem(1, new ItemStack(Items.GLASS_BOTTLE, 2));
        helper.assertTrue(h.extractItem(0, 64, false).isEmpty(), "bread can't be pulled out");
        ItemStack bottles = h.extractItem(1, 64, false);
        helper.assertTrue(bottles.is(Items.GLASS_BOTTLE) && bottles.getCount() == 2, "bottles pulled out: " + bottles);
        helper.assertValueEqual(pantry.getItem(0).getCount(), 3, "bread left in the pantry");
        helper.succeed();
    }

    @ModGameTest
    public static void cargoCrateItemHandlerHoldsOneKindAndEmpties(GameTestHelper helper) {
        helper.setBlock(P, ShipDecor.CARGO_CRATE.get());
        CargoContainerBlockEntity crate = (CargoContainerBlockEntity) helper.getBlockEntity(P);
        IItemHandler h = handler(helper, P, Direction.NORTH);
        helper.assertTrue(h != null, "crate exposes an item handler");
        helper.assertValueEqual(h.getSlots(), 2, "input and output slot");

        helper.assertTrue(h.insertItem(CargoContainerBlockEntity.INPUT, new ItemStack(Items.SUGAR, 6), false).isEmpty(), "sugar accepted");
        helper.assertValueEqual(crate.count(), 6, "sugar in the store");
        helper.assertValueEqual(h.insertItem(CargoContainerBlockEntity.INPUT, new ItemStack(Items.COBBLESTONE, 2), false).getCount(), 2, "second kind refused");
        helper.assertValueEqual(h.insertItem(CargoContainerBlockEntity.INPUT, new ItemStack(Items.DIAMOND_SWORD), false).getCount(), 1, "unstackable refused");

        ItemStack out = h.extractItem(CargoContainerBlockEntity.OUTPUT, 64, false);
        helper.assertTrue(out.is(Items.SUGAR) && out.getCount() == 6, "sugar pulled out: " + out);
        helper.assertValueEqual(crate.count(), 0, "crate empty again");
        helper.succeed();
    }

    @ModGameTest
    public static void waterBarrelExposesNoItemHandler(GameTestHelper helper) {
        helper.setBlock(P, CrewContent.WATER_BARREL.get());
        helper.assertTrue(handler(helper, P, Direction.UP) == null, "the water barrel holds rations, not items");
        helper.setBlock(P, Blocks.AIR);
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void shacklesReachTheBrigBeforeVillagerTrades(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(LawContent.SHACKLES.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        villager.setHealth(2.0f);
        // Player.interactOn fires PlayerInteractEvent.EntityInteract before Villager.mobInteract
        InteractionResult r = player.interactOn(villager, InteractionHand.MAIN_HAND);
        helper.assertTrue(r.consumesAction(), "shackled through the loader event, got " + r);
        helper.assertTrue(BrigService.state(villager).heldBy(player.getUUID()), "villager is the player's prisoner");
        helper.assertFalse(villager.isTrading(), "no trade screen");
        helper.assertValueEqual(stack.getCount(), 1, "one pair of shackles used");
        BrigService.clear(villager);
        helper.killAllEntities();
        helper.succeed();
    }
}

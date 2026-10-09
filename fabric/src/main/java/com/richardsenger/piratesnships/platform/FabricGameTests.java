package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.ModModules;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.crew.galley.PantryBlockEntity;
import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.law.content.LawContent;
import com.richardsenger.piratesnships.law.world.PlacedBlocks;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import com.richardsenger.piratesnships.trade.cargo.CargoContainerBlockEntity;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

/**
 * Registers the mod's GameTests on Fabric and tests the Fabric platform wiring that common code can't reach, the twin
 * of {@code NeoForgeGameTests}: the transfer API storage that {@link FabricCapabilityHelper} registers, Fabric API's
 * entity interaction event into {@code CommonEvents.ENTITY_INTERACT}, and the block place mixin into
 * {@code CommonEvents.BLOCK_PLACE}.
 *
 * <p>Registration goes into vanilla's {@link GameTestRegistry} like NeoForge's {@code RegisterGameTestsEvent} (see
 * {@link #registerClass}), not through Fabric API's {@code fabric-gametest} entry point: that one needs public test
 * class constructors, only renames templates of {@code @GameTest} methods (ours are generated, {@code ModGameTests})
 * and could run the generators before common init. Fabric API's headless runner
 * ({@code -Dfabric-api.gametest}, {@code ./gradlew :fabric:runGameTest}) then runs everything registered.
 */
public final class FabricGameTests {

    private static final BlockPos P = new BlockPos(1, 1, 1);

    private FabricGameTests() {
    }

    /**
     * Called by the entry point after common init. Registers only where tests can run: the GameTest server and
     * development runs (the {@code /test} command), never in a player's game.
     */
    public static void register() {
        if (System.getProperty("fabric-api.gametest") == null && !Services.PLATFORM.isDevelopmentEnvironment()) return;
        for (ModModule m : ModModules.ALL) m.gameTestClasses().forEach(FabricGameTests::registerClass);
        registerClass(FabricGameTests.class);
    }

    /**
     * Vanilla's {@link GameTestRegistry#register(Class)} without its instantiation of the test class: it calls
     * {@code newInstance()} even for a static {@code @GameTestGenerator} (NeoForge patches that out) and our test classes
     * have private constructors. Static generators are invoked directly; the registry's collections are access-widened.
     */
    @SuppressWarnings("unchecked")
    private static void registerClass(Class<?> testClass) {
        Method[] methods = testClass.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        for (Method m : methods) {
            if (!m.isAnnotationPresent(GameTestGenerator.class)) continue;
            if (!Modifier.isStatic(m.getModifiers())) {
                throw new IllegalStateException("@GameTestGenerator must be static on Fabric: " + testClass.getName() + "#" + m.getName());
            }
            try {
                m.setAccessible(true);
                GameTestRegistry.TEST_FUNCTIONS.addAll((Collection<TestFunction>) m.invoke(null));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("GameTest generator failed: " + testClass.getName() + "#" + m.getName(), e);
            }
            GameTestRegistry.TEST_CLASS_NAMES.add(testClass.getSimpleName());
        }
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(FabricGameTests.class);
    }

    private static @Nullable Storage<ItemVariant> storage(GameTestHelper helper, BlockPos pos, @Nullable Direction side) {
        return ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(pos), side);
    }

    private static long insert(Storage<ItemVariant> storage, ItemStack stack) {
        try (Transaction tx = Transaction.openOuter()) {
            long n = storage.insert(ItemVariant.of(stack), stack.getCount(), tx);
            tx.commit();
            return n;
        }
    }

    private static long insert(SingleSlotStorage<ItemVariant> slot, ItemStack stack) {
        try (Transaction tx = Transaction.openOuter()) {
            long n = slot.insert(ItemVariant.of(stack), stack.getCount(), tx);
            tx.commit();
            return n;
        }
    }

    private static long extract(SingleSlotStorage<ItemVariant> slot, long max) {
        if (slot.isResourceBlank()) return 0;
        try (Transaction tx = Transaction.openOuter()) {
            long n = slot.extract(slot.getResource(), max, tx);
            tx.commit();
            return n;
        }
    }

    @SuppressWarnings("unchecked")
    private static SlottedStorage<ItemVariant> slotted(GameTestHelper helper, Storage<ItemVariant> s) {
        helper.assertTrue(s instanceof SlottedStorage<?>, "container storage has slots: " + s);
        return (SlottedStorage<ItemVariant>) s;
    }

    @ModGameTest
    public static void pantryStorageInsertsProvisionsAndExtractsTheRest(GameTestHelper helper) {
        helper.setBlock(P, CrewContent.PANTRY.get());
        PantryBlockEntity pantry = (PantryBlockEntity) helper.getBlockEntity(P);
        Storage<ItemVariant> s = storage(helper, P, Direction.UP);
        helper.assertTrue(s != null, "pantry exposes an item storage");
        helper.assertTrue(storage(helper, P, null) != null, "pantry exposes an item storage without a side");

        helper.assertValueEqual(insert(s, new ItemStack(Items.COBBLESTONE, 2)), 0L, "cobblestone refused");
        helper.assertTrue(pantry.isEmpty(), "nothing went in");
        helper.assertValueEqual(insert(s, new ItemStack(Items.BREAD, 3)), 3L, "bread accepted");
        helper.assertValueEqual(pantry.getItem(0).getCount(), 3, "bread in the pantry");

        pantry.setItem(1, new ItemStack(Items.GLASS_BOTTLE, 2));
        SlottedStorage<ItemVariant> slots = slotted(helper, s); // the pantry's face slots are all its slots, in order
        helper.assertValueEqual(extract(slots.getSlot(0), 64), 0L, "bread can't be pulled out");
        helper.assertValueEqual(extract(slots.getSlot(1), 64), 2L, "bottles pulled out");
        helper.assertTrue(pantry.getItem(1).isEmpty(), "bottle slot empty");
        helper.assertValueEqual(pantry.getItem(0).getCount(), 3, "bread left in the pantry");
        helper.succeed();
    }

    @ModGameTest
    public static void cargoCrateStorageHoldsOneKindAndEmpties(GameTestHelper helper) {
        helper.setBlock(P, ShipDecor.CARGO_CRATE.get());
        CargoContainerBlockEntity crate = (CargoContainerBlockEntity) helper.getBlockEntity(P);
        Storage<ItemVariant> s = storage(helper, P, Direction.NORTH);
        helper.assertTrue(s != null, "crate exposes an item storage");
        SlottedStorage<ItemVariant> slots = slotted(helper, s);
        helper.assertValueEqual(slots.getSlotCount(), 2, "input and output slot");

        SingleSlotStorage<ItemVariant> in = slots.getSlot(CargoContainerBlockEntity.INPUT);
        helper.assertValueEqual(insert(in, new ItemStack(Items.SUGAR, 6)), 6L, "sugar accepted");
        helper.assertValueEqual(crate.count(), 6, "sugar in the store");
        helper.assertValueEqual(insert(in, new ItemStack(Items.COBBLESTONE, 2)), 0L, "second kind refused");
        helper.assertValueEqual(insert(in, new ItemStack(Items.DIAMOND_SWORD)), 0L, "unstackable refused");

        SingleSlotStorage<ItemVariant> out = slots.getSlot(CargoContainerBlockEntity.OUTPUT);
        helper.assertTrue(out.getResource().isOf(Items.SUGAR), "sugar offered: " + out.getResource());
        long taken = 0;
        for (int i = 0; i < 6 && !out.isResourceBlank(); i++) taken += extract(out, 64);
        helper.assertValueEqual(taken, 6L, "sugar pulled out");
        helper.assertValueEqual(crate.count(), 0, "crate empty again");
        helper.succeed();
    }

    @ModGameTest
    public static void waterBarrelExposesNoItemStorage(GameTestHelper helper) {
        helper.setBlock(P, CrewContent.WATER_BARREL.get());
        helper.assertTrue(storage(helper, P, Direction.UP) == null, "the water barrel holds rations, not items");
        helper.setBlock(P, Blocks.AIR);
        helper.succeed();
    }

    /**
     * Fabric API fires {@link UseEntityCallback} from the interaction packet handler (and the client's game mode),
     * not from {@code Player#interactOn}, so this test invokes the callback as the server's handler does for a plain
     * interaction ({@code hitResult == null}) and checks it reaches the shackles' listener.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void shacklesReachTheBrigThroughFabricInteractEvent(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(LawContent.SHACKLES.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        villager.setHealth(2.0f);
        InteractionResult at = UseEntityCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, villager,
                new net.minecraft.world.phys.EntityHitResult(villager));
        helper.assertValueEqual(at, InteractionResult.PASS, "the interact-at variant is not forwarded");
        InteractionResult r = UseEntityCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, villager, null);
        helper.assertTrue(r.consumesAction(), "shackled through Fabric's event, got " + r);
        helper.assertTrue(BrigService.state(villager).heldBy(player.getUUID()), "villager is the player's prisoner");
        helper.assertValueEqual(stack.getCount(), 1, "one pair of shackles used");
        BrigService.clear(villager);
        helper.killAllEntities();
        helper.succeed();
    }

    /** A player placing a chest goes through BlockItem#place, MixinBlockItem and PlacedBlocks' listener. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void placedChestIsRecordedThroughTheBlockPlaceMixin(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        // a mock player that is not added to the level: a mock server player would stay in the player list and keep
        // other tests from skipping the night (PlayerSleepGameTests)
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHEST));
        BlockPos floor = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        InteractionResult r = player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(r.consumesAction(), "chest placed, got " + r);
        BlockPos chest = floor.above();
        helper.assertTrue(helper.getLevel().getBlockState(chest).is(Blocks.CHEST), "a chest stands on the floor");
        helper.assertTrue(PlacedBlocks.isPlayerPlaced(helper.getLevel(), chest), "the chest is recorded as player-placed");
        PlacedBlocks.forget(helper.getLevel(), chest);
        helper.getLevel().removeBlock(chest, false);
        helper.killAllEntities();
        helper.succeed();
    }
}

package com.richardsenger.piratesnships.world.treasure;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.ChartCells;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.exchange.TransactionResult;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.SpecialOffer;
import com.richardsenger.piratesnships.trade.market.SpecialOffers;
import com.richardsenger.piratesnships.trade.net.MarketBackend;
import com.richardsenger.piratesnships.trade.net.MarketView;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.world.port.TreasureSite;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Treasure maps (TM1) on a test port: a pirate island record with two buried chests (relative (2, 1, 2) and (6, 1, 6))
 * on a sand floor with a pool of water, registered for the test and removed when it passes. The reader stands next to
 * the first chest, so a blank map binds to it. Mock players are plain {@code Player}s (a mock {@code ServerPlayer}
 * makes Sable send to a fake connection), so the container event is fired through {@link CommonEvents} like the law
 * module's theft tests; the NeoForge forwarding is covered by the playtest.
 */
public final class TreasureMapGameTests {

    private static final BlockPos SITE_A = new BlockPos(2, 1, 2);
    private static final BlockPos SITE_B = new BlockPos(6, 1, 6);
    private static int menuIds = 300;

    private TreasureMapGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(TreasureMapGameTests.class);
    }

    /** Sand floor, a 3×3 pool, two chests; registers the port and returns it. */
    private static Port island(GameTestHelper helper, PortKind kind) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                helper.setBlock(x, 0, z, Blocks.SAND);
            }
        }
        for (int x = 6; x <= 8; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(x, 0, z, Blocks.WATER);
            }
        }
        helper.setBlock(SITE_A, Blocks.CHEST);
        helper.setBlock(SITE_B, Blocks.CHEST);
        BlockPos a = helper.absolutePos(SITE_A);
        BlockPos b = helper.absolutePos(SITE_B);
        BlockPos centre = helper.absolutePos(new BlockPos(4, 1, 4));
        ResourceLocation id = Constants.id("test_treasure_" + centre.getX() + "_" + centre.getZ());
        Port port = new Port(id, kind, helper.getLevel().dimension(), centre,
                BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(8, 5, 8))), Climate.TROPICAL,
                List.of(), List.of(new TreasureSite(a, false), new TreasureSite(b, false)));
        PortRegistry registry = PortRegistry.get(helper.getLevel().getServer());
        registry.remove(id);
        registry.add(port);
        return port;
    }

    private static void cleanUp(GameTestHelper helper, Port port) {
        PortRegistry.get(helper.getLevel().getServer()).remove(port.id());
    }

    private static Player reader(GameTestHelper helper, double x, double z) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(x, 1, z));
        player.moveTo(at.x, at.y, at.z, 0, 0);
        return player;
    }

    /** Uses a blank map in the main hand and returns what the hand holds afterwards. */
    private static ItemStack useBlank(GameTestHelper helper, Player player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TreasureMapContent.TREASURE_MAP.get()));
        InteractionResultHolder<ItemStack> r = player.getItemInHand(InteractionHand.MAIN_HAND).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, r.getObject());
        return r.getObject();
    }

    private static TreasureSite site(GameTestHelper helper, Port port, BlockPos relative) {
        BlockPos abs = helper.absolutePos(relative);
        return PortRegistry.get(helper.getLevel().getServer()).index().byId(port.id()).orElseThrow()
                .treasures().stream().filter(s -> s.pos().equals(abs)).findFirst()
                .orElseThrow(() -> new GameTestAssertException("no site at " + abs));
    }

    private static void openChest(GameTestHelper helper, Player player, BlockPos relative) {
        ChestBlockEntity chest = helper.getBlockEntity(relative);
        AbstractContainerMenu menu = chest.createMenu(++menuIds, player.getInventory(), player);
        if (menu == null) throw new GameTestAssertException("chest menu not created");
        CommonEvents.CONTAINER_OPEN.invoker().on(player, menu);
        menu.removed(player);
        CommonEvents.CONTAINER_CLOSE.invoker().on(player, menu);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void blankMapBindsToTheNearestUnlootedSiteWithAPicture(GameTestHelper helper) {
        Port port = island(helper, PortKind.PIRATE_ISLAND);
        Player player = reader(helper, 2.5, 3.5);
        ItemStack map = useBlank(helper, player);
        TreasureMapData data = TreasureMapService.data(map);
        helper.assertTrue(data != null, "the blank map bound itself");
        helper.assertValueEqual(data.port(), port.id(), "bound port");
        helper.assertValueEqual(data.site(), helper.absolutePos(SITE_A), "bound to the nearer site");
        helper.assertFalse(data.found(), "a fresh map is not found");
        helper.assertTrue(data.knownCells() > 0, "the picture has sampled cells");
        BlockPos sand = helper.absolutePos(new BlockPos(4, 0, 4));
        int cb = data.cellBlocks();
        int sandCell = data.cell(Math.floorDiv(sand.getX(), cb), Math.floorDiv(sand.getZ(), cb));
        helper.assertTrue(ChartCells.known(sandCell), "the island under the site is charted");
        helper.assertValueEqual(data.minCx(), TreasureMapData.originCell(data.site().getX(), cb), "picture centred on the site");
        // the component survives a save and load
        var saved = TreasureMapData.CODEC.encodeStart(helper.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), data)
                .getOrThrow();
        helper.assertValueEqual(TreasureMapData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, saved).getOrThrow(), data, "saved map");
        cleanUp(helper, port);
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void openingTheChestFindsTheTreasureAndTheNextMapLeadsToTheOther(GameTestHelper helper) {
        Port port = island(helper, PortKind.PIRATE_ISLAND);
        MinecraftServer server = helper.getLevel().getServer();
        Player player = reader(helper, 2.5, 3.5);
        ItemStack first = useBlank(helper, player);
        helper.assertValueEqual(TreasureMapService.data(first).site(), helper.absolutePos(SITE_A), "first map");
        helper.assertFalse(TreasureMapService.refresh(first, server), "nothing found yet");

        // a stranger's map of the same treasure, not in the finder's inventory
        Player stranger = reader(helper, 2.5, 1.5);
        ItemStack strangers = useBlank(helper, stranger);
        stranger.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        openChest(helper, player, SITE_A);
        helper.assertTrue(site(helper, port, SITE_A).looted(), "site A looted on the port");
        helper.assertFalse(site(helper, port, SITE_B).looted(), "site B still buried");
        helper.assertTrue(TreasureMapService.data(player.getMainHandItem()).found(), "the finder's map turned found at once");
        helper.assertTrue(TreasureMapService.refresh(strangers, server), "the stranger's map turns found on its next check");
        helper.assertTrue(TreasureMapService.data(strangers).found(), "found state");
        helper.assertFalse(TreasureMapService.refresh(strangers, server), "found only once");

        // opening the found chest again changes nothing; a second map leads to the other treasure
        openChest(helper, player, SITE_A);
        ItemStack second = useBlank(helper, player);
        helper.assertValueEqual(TreasureMapService.data(second).site(), helper.absolutePos(SITE_B), "second map");
        openChest(helper, player, SITE_B);
        helper.assertTrue(site(helper, port, SITE_B).looted(), "site B looted");
        cleanUp(helper, port);
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aChestAwayFromTheSitesFindsNothing(GameTestHelper helper) {
        Port port = island(helper, PortKind.PIRATE_ISLAND);
        BlockPos other = new BlockPos(2, 1, 6); // 4 blocks from site A, 4 from site B
        helper.setBlock(other, Blocks.CHEST);
        Player player = reader(helper, 2.5, 5.5);
        openChest(helper, player, other);
        helper.assertFalse(site(helper, port, SITE_A).looted(), "site A");
        helper.assertFalse(site(helper, port, SITE_B).looted(), "site B");
        cleanUp(helper, port);
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void giveCommandGivesAMapOfThePortsFirstTreasure(GameTestHelper helper) {
        Port port = island(helper, PortKind.PIRATE_ISLAND);
        MinecraftServer server = helper.getLevel().getServer();
        Player player = reader(helper, 6.5, 5.5);
        CommandSourceStack source = server.createCommandSourceStack().withEntity(player).withLevel(helper.getLevel())
                .withPosition(player.position()).withPermission(4).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, "pirates world treasure give " + port.id());
        ItemStack given = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(TreasureMapContent.TREASURE_MAP.get())) given = player.getInventory().getItem(i);
        }
        helper.assertFalse(given.isEmpty(), "the command gave a map");
        TreasureMapData data = TreasureMapService.data(given);
        helper.assertTrue(data != null, "the given map is bound");
        helper.assertValueEqual(data.site(), helper.absolutePos(SITE_A), "the port's first unlooted site");
        cleanUp(helper, port);
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void fencesSellBlankMapsOnlyOnPirateIslands(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Port island = island(helper, PortKind.PIRATE_ISLAND);
        PortService.openMarket(server, island);
        SpecialOffer offer = SpecialOffers.byId(TreasureMapContent.TREASURE_MAP.id()).orElseThrow();
        long price = TradeConfig.TREASURE_MAP_PRICE.get();

        List<MarketView.GoodLine> lines = MarketBackend.specialLines(PortKind.PIRATE_ISLAND, 2);
        helper.assertTrue(lines.stream().anyMatch(l -> l.good().equals(offer.id()) && l.buy().total() == 2 * price), "pirate island line");
        helper.assertTrue(MarketBackend.specialLines(PortKind.SEAFARER_VILLAGE, 1).isEmpty(), "no line in villages");

        Player buyer = reader(helper, 4.5, 4.5);
        Wallet.give(buyer, price + 5);
        TransactionResult bought = MarketBackend.buySpecial(buyer, island.id(), true, 1, offer);
        helper.assertTrue(bought.done(), "bought: " + bought.status());
        helper.assertValueEqual(Wallet.count(buyer), 5L, "doubloons left");
        helper.assertTrue(buyer.getInventory().contains(new ItemStack(TreasureMapContent.TREASURE_MAP.get())), "a blank map in the inventory");
        helper.assertValueEqual(MarketBackend.buySpecial(buyer, island.id(), true, 1, offer).status(),
                TransactionResult.Status.NOT_ENOUGH_COINS, "too poor for a second");
        helper.assertValueEqual(MarketBackend.buySpecial(buyer, island.id(), false, 1, offer).status(),
                TransactionResult.Status.NOT_TRADED, "fences never buy maps back");
        cleanUp(helper, island);

        Port village = new Port(Constants.id("test_treasure_village_" + island.centre().getX()), PortKind.SEAFARER_VILLAGE,
                island.dimension(), island.centre(), island.box(), Climate.TEMPERATE, List.of());
        PortService.openMarket(server, village);
        Wallet.give(buyer, price);
        helper.assertValueEqual(MarketBackend.buySpecial(buyer, village.id(), true, 1, offer).status(),
                TransactionResult.Status.NOT_TRADED, "villages sell no treasure maps");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_world_treasure")
    public static void disabledTreasureMapsStayBlankAndAreNotSold(GameTestHelper helper) {
        ConfigOverrides.during(helper, WorldConfig.TREASURE_MAPS_ENABLED, false);
        Port port = island(helper, PortKind.PIRATE_ISLAND);
        Player player = reader(helper, 2.5, 3.5);
        ItemStack map = useBlank(helper, player);
        helper.assertTrue(map.is(TreasureMapContent.TREASURE_MAP.get()), "the map is still there");
        helper.assertTrue(TreasureMapService.data(map) == null, "the map stayed blank");
        helper.assertTrue(MarketBackend.specialLines(PortKind.PIRATE_ISLAND, 1).isEmpty(), "fences stop selling maps");
        // a bound map still reads its treasure (the command works with the feature off)
        Optional<TreasureSite> first = TreasureBinding.firstUnlooted(port);
        helper.assertTrue(first.isPresent(), "test port has a treasure");
        ItemStack bound = TreasureMapService.boundMap(helper.getLevel(), port, first.get());
        helper.assertTrue(TreasureMapService.data(bound) != null, "bound map made directly");
        cleanUp(helper, port);
        helper.succeed();
    }
}

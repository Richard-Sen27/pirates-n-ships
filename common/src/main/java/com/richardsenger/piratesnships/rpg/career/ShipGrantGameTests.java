package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import com.richardsenger.piratesnships.rpg.career.ShipGrantRules.Ladder;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.decor.flag.ShipAllegiance;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import com.richardsenger.piratesnships.trade.desk.HarborDeskBlockEntity;
import com.richardsenger.piratesnships.trade.desk.HarborDesks;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.Berth;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageGuns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fighting ships by rank (SHP1, docs/design.md §15) in a real server: a promotion to Captain hands out a navy
 * commission through the rank rewards (once; a full pack drops it); redeemed at a navy outpost's desk or officer, an
 * armed navy sloop owned by the player lies at a berth with the navy flag, a titled name and an empty shot locker; a
 * second commission is refused; a Dread Captain's pirate sloop comes at a pirate island's desk under the Jolly Roger;
 * no free berth refuses and keeps the commission; {@code careers.ship_grants.enabled} off grants and redeems nothing.
 * Config changes run in batches of their own.
 */
public final class ShipGrantGameTests {

    private static final String BATCH = "pirates_n_ships_ship_grants_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_ship_grants_";
    /** Water y 2..7; the pier deck at y 8 over x 19..21; the berths at the surface beside it (the SW1 test harbor). */
    private static final int SURFACE = 7;
    private static final BlockPos BERTH_1 = new BlockPos(18, SURFACE, 20);
    private static final BlockPos BERTH_2 = new BlockPos(22, SURFACE, 20);
    private static final BlockPos DESK = new BlockPos(20, 9, 37);

    private ShipGrantGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(ShipGrantGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** Basin (stone floor y 1, walls), water y 2..7, the pier deck, the barrier ceiling removed (masts reach above). */
    private static void harbor(GameTestHelper h) {
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 39 || z == 0 || z == 39;
                for (int y = 2; y <= 8; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : y <= SURFACE ? Blocks.WATER : Blocks.AIR);
                }
                if (!wall && x >= 19 && x <= 21) h.setBlock(new BlockPos(x, 8, z), Blocks.SPRUCE_PLANKS);
                BlockPos ceiling = new BlockPos(x, 13, z);
                if (h.getBlockState(ceiling).is(Blocks.BARRIER)) h.setBlock(ceiling, Blocks.AIR);
            }
        }
    }

    /** A registered port of {@code kind} over the test area with {@code berths}, and its desk bound to it. */
    private static Port port(GameTestHelper h, PortKind kind, List<BlockPos> berths) {
        BlockPos min = h.absolutePos(BlockPos.ZERO);
        BlockPos max = h.absolutePos(new BlockPos(39, 30, 39));
        Port port = new Port(Constants.id("gametest/grant_" + UUID.randomUUID().toString().substring(0, 8)), kind,
                h.getLevel().dimension(), h.absolutePos(new BlockPos(20, 8, 20)), BoundingBox.fromCorners(min, max), Climate.TEMPERATE,
                berths.stream().map(b -> new Berth(h.absolutePos(b), Direction.NORTH)).toList());
        PortService.register(h.getLevel().getServer(), port);
        h.setBlock(DESK, HarborDesks.HARBOR_DESK.get().defaultBlockState());
        ((HarborDeskBlockEntity) h.getBlockEntity(DESK)).setPort(Optional.of(port.id()));
        return port;
    }

    /** A real server player with a mock connection, not added to the level or the player list, by the desk. */
    private static ServerPlayer serverPlayer(GameTestHelper h) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "grant_test");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(h.absolutePos(DESK.north())));
        p.getInventory().clearContent();
        return p;
    }

    private static int commissions(Player p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(ShipGrantContent.SHIP_COMMISSION.get())) n++;
        }
        return n;
    }

    /** Moves the first commission in the inventory into the main hand and returns it. */
    private static ItemStack commissionInHand(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(ShipGrantContent.SHIP_COMMISSION.get())) {
                p.getInventory().setItem(i, ItemStack.EMPTY);
                p.setItemInHand(InteractionHand.MAIN_HAND, s);
                return s;
            }
        }
        throw new AssertionError("no commission in the inventory");
    }

    /** Uses the desk with the main-hand item, as a click does. */
    private static ItemInteractionResult useDesk(GameTestHelper h, ServerPlayer p) {
        BlockPos abs = h.absolutePos(DESK);
        return h.getBlockState(DESK).useItemOn(p.getMainHandItem(), h.getLevel(), p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false));
    }

    /** The ids of the ships {@code p} owns (each test has its own player). */
    private static List<UUID> shipsOf(GameTestHelper h, Player p) {
        ShipRegistry registry = ShipRegistry.get(h.getLevel().getServer());
        return SableShips.all(h.getLevel()).stream().filter(b -> !b.isRemoved())
                .filter(b -> registry.find(b.id()).flatMap(ShipData::owner).equals(Optional.of(p.getUUID())))
                .map(ShipBody::id).toList();
    }

    /** The delivered ship: owner, flag, empty lockers, titled name; tracked for cleanup. */
    private static ShipBody assertDelivered(GameTestHelper h, ServerPlayer p, UUID id, FlagKind flag, String titlePrefix) {
        ShipBody ship = SableShips.byId(h.getLevel(), id);
        h.assertTrue(ship != null, "ship body exists");
        Optional<ShipData> data = ShipRegistry.get(h.getLevel().getServer()).find(id);
        h.assertTrue(data.isPresent() && data.get().owner().equals(Optional.of(p.getUUID())), "player owns the ship: " + data);
        h.assertTrue(data.get().name().startsWith(titlePrefix), "named by the title rule: '" + data.get().name() + "'");
        h.assertValueEqual(ShipAllegiance.read(ship).kind(), flag, "flag");
        List<BlockPos> lockers = VoyageGuns.lockers(h.getLevel(), ship);
        h.assertFalse(lockers.isEmpty(), "the armed sloop has a shot locker");
        for (BlockPos pos : lockers) {
            h.assertTrue(h.getLevel().getBlockEntity(pos) instanceof Container c && c.isEmpty(), "shot locker " + pos + " is empty");
        }
        List<BlockPos> guns = VoyageGuns.guns(h.getLevel(), ship);
        h.assertFalse(guns.isEmpty(), "the sloop carries guns");
        for (BlockPos gun : guns) h.assertFalse(VoyageGuns.isLoaded(h.getLevel(), gun), "gun " + gun + " is loaded");
        return ship;
    }

    // ------------------------------------------------------------------ the grant

    /**
     * A real promotion to Captain (enlisting with a Captain's record, the promotion chain) hands out a navy commission
     * for the armed navy sloop, once: a further promotion, resigning and enlisting again give no second one. A
     * Lieutenant gets none.
     */
    @ModGameTest(batch = BATCH + "grant")
    public static void promotionToCaptainHandsTheCommission(GameTestHelper h) {
        ServerPlayer p = serverPlayer(h);
        Careers.setNavy(p, NavyRank.LIEUTENANT);
        h.assertValueEqual(commissions(p), 0, "a lieutenant's commissions");
        Careers.setNavy(p, NavyRank.NONE);

        CareerThresholds.NavyStep capt = CareerConfig.thresholds().step(NavyRank.CAPTAIN);
        Reputation.set(p, Faction.NAVY, capt.minNavyRep(), "test");
        Careers.store(p, Careers.record(p).plus(CareerCounter.PIRATES_KILLED, capt.piratesDefeated())
                .plus(CareerCounter.NAVY_QUESTS, capt.quests()));
        h.assertValueEqual(Careers.enlist(p), CareerRules.EnlistVerdict.OK, "enlist");
        h.assertTrue(Careers.navyRank(p).atLeast(NavyRank.CAPTAIN), "promoted to captain: " + Careers.navyRank(p));
        h.assertValueEqual(commissions(p), 1, "commissions after the promotion");
        ShipCommission c = ShipCommissionItem.commission(commissionInHand(p)).orElseThrow();
        h.assertValueEqual(c.ladder(), Ladder.NAVY, "ladder");
        h.assertValueEqual(c.template(), ShipTemplates.NAVY_SLOOP_ARMED_ID, "template");
        h.assertValueEqual(c.owner(), p.getUUID(), "made out to the player");
        h.assertTrue(CareerRewards.gifted(p).contains(ShipGrantRules.grantKey(Ladder.NAVY)), "grant noted");

        Careers.setNavy(p, NavyRank.ADMIRAL);
        h.assertTrue(Careers.resign(p), "resign");
        h.assertValueEqual(Careers.enlist(p), CareerRules.EnlistVerdict.OK, "enlist again");
        h.assertValueEqual(commissions(p), 1, "no second commission (the first is in the main hand, an inventory slot)");

        // the operators' reset forgets the grant: the held rank grants again at once
        h.assertValueEqual(ShipGrants.reset(p), 1, "commissions after the reset");
        h.assertValueEqual(commissions(p), 2, "a fresh commission after the reset");
        h.succeed();
    }

    /** With a full pack the commission is dropped at the player's feet. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "full")
    public static void commissionDropsWhenThePackIsFull(GameTestHelper h) {
        ServerPlayer p = serverPlayer(h);
        p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(4, 1, 4))));
        for (int i = 0; i < p.getInventory().items.size(); i++) p.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
        Careers.setInfamy(p, InfamyRank.DREAD_CAPTAIN);
        List<ItemEntity> dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4));
        h.assertTrue(dropped.stream().anyMatch(e -> ShipCommissionItem.commission(e.getItem()).map(c -> c.ladder() == Ladder.PIRATES).orElse(false)),
                "no pirate commission dropped: " + dropped);
        dropped.forEach(ItemEntity::discard);
        h.succeed();
    }

    // ------------------------------------------------------------------ redemption

    /**
     * A Captain redeems the commission at a navy outpost's desk: an assembled navy sloop owned by him lies at berth 1,
     * named "Capt. ...", under the navy flag, its shot locker empty; the commission is consumed. A second commission
     * (a copy) is refused and kept, and no second ship appears.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, batch = BATCH + "navy", timeoutTicks = 400)
    public static void captainRedeemsAtANavyOutpostDesk(GameTestHelper h) {
        harbor(h);
        Port port = port(h, PortKind.NAVY_OUTPOST, List.of(BERTH_1, BERTH_2));
        ServerPlayer p = serverPlayer(h);
        Careers.setNavy(p, NavyRank.CAPTAIN);
        ItemStack stack = commissionInHand(p);
        ItemStack copy = stack.copy();

        h.assertValueEqual(useDesk(h, p), ItemInteractionResult.CONSUME, "the desk takes the commission");
        h.assertTrue(p.getMainHandItem().isEmpty(), "commission consumed");
        h.assertTrue(CareerRewards.gifted(p).contains(ShipGrantRules.redeemedKey(Ladder.NAVY)), "redemption noted");
        List<UUID> ships = shipsOf(h, p);
        ships.forEach(id -> ShipTestCleanup.track(h, id));
        h.assertValueEqual(ships.size(), 1, "one ship of the player's at the outpost");
        assertDelivered(h, p, ships.get(0), FlagKind.NAVY, "Capt. ");

        // a second commission (a copy) is refused
        p.setItemInHand(InteractionHand.MAIN_HAND, copy);
        ShipGrants.Redeem second = ShipGrants.redeemAtDesk(p, h.absolutePos(DESK), p.getMainHandItem()).orElseThrow();
        h.assertValueEqual(second.verdict(), ShipGrantRules.Verdict.ALREADY_REDEEMED, "second redeem: " + second.message().getString());
        h.assertTrue(p.getMainHandItem().is(ShipGrantContent.SHIP_COMMISSION.get()), "refused commission kept");
        h.assertValueEqual(shipsOf(h, p).size(), 1, "no second ship");
        PortRegistry.get(h.getLevel().getServer()).remove(port.id());
        h.succeed();
    }

    /** A navy officer standing in the outpost delivers too (the commission used on him). */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, batch = BATCH + "officer", timeoutTicks = 400)
    public static void captainRedeemsAtANavyOfficer(GameTestHelper h) {
        harbor(h);
        Port port = port(h, PortKind.NAVY_OUTPOST, List.of(BERTH_1));
        ServerPlayer p = serverPlayer(h);
        Reputation.set(p, Faction.NAVY, 60, "test");
        Careers.setNavy(p, NavyRank.CAPTAIN);
        commissionInHand(p);
        NavyOfficer officer = h.spawnWithNoFreeWill(MobContent.NAVY_OFFICER.get(), 20, 9, 34);
        h.assertValueEqual(ShipGrants.onEntityInteract(p, officer, InteractionHand.MAIN_HAND), InteractionResult.CONSUME, "officer");
        h.assertTrue(p.getMainHandItem().isEmpty(), "commission consumed at the officer");
        List<UUID> ships = shipsOf(h, p);
        ships.forEach(id -> ShipTestCleanup.track(h, id));
        h.assertValueEqual(ships.size(), 1, "one ship of the player's at the outpost");
        assertDelivered(h, p, ships.get(0), FlagKind.NAVY, "Capt. ");
        officer.discard();
        PortRegistry.get(h.getLevel().getServer()).remove(port.id());
        h.succeed();
    }

    /**
     * A Dread Captain's pirate commission: refused at a navy outpost, redeemed at a pirate island's desk, where a
     * pirate sloop owned by him lies under the Jolly Roger with an empty locker.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, batch = BATCH + "pirate", timeoutTicks = 400)
    public static void dreadCaptainRedeemsAtAPirateIsland(GameTestHelper h) {
        harbor(h);
        ServerPlayer p = serverPlayer(h);
        Careers.setInfamy(p, InfamyRank.DREAD_CAPTAIN);
        ItemStack stack = commissionInHand(p);
        h.assertValueEqual(ShipCommissionItem.commission(stack).orElseThrow().template(), ShipTemplates.PIRATE_SLOOP_ARMED_ID, "template");

        Port navy = port(h, PortKind.NAVY_OUTPOST, List.of(BERTH_1));
        ShipGrants.Redeem wrong = ShipGrants.redeemAtDesk(p, h.absolutePos(DESK), stack).orElseThrow();
        h.assertValueEqual(wrong.verdict(), ShipGrantRules.Verdict.WRONG_PORT, "at a navy outpost");
        h.assertTrue(p.getMainHandItem().is(ShipGrantContent.SHIP_COMMISSION.get()), "kept at the wrong port");
        PortRegistry.get(h.getLevel().getServer()).remove(navy.id());

        Port island = port(h, PortKind.PIRATE_ISLAND, List.of(BERTH_1, BERTH_2));
        ShipGrants.Redeem r = ShipGrants.redeemAtDesk(p, h.absolutePos(DESK), p.getMainHandItem()).orElseThrow();
        if (r.ship() != null) ShipTestCleanup.track(h, r.ship());
        h.assertValueEqual(r.outcome(), ShipGrants.Outcome.DELIVERED, "redeem: " + r.message().getString());
        h.assertTrue(r.ship() != null, "ship assembled: " + r.message().getString());
        assertDelivered(h, p, r.ship(), FlagKind.JOLLY_ROGER, "Dread Pirate ");
        h.assertValueEqual(r.berth(), 1, "berth 1");
        h.assertTrue(p.getMainHandItem().isEmpty(), "commission consumed");
        PortRegistry.get(h.getLevel().getServer()).remove(island.id());
        h.succeed();
    }

    /** The only berth lies in solid rock: no berth is free, the commission is kept and not noted as redeemed. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, batch = BATCH + "no_berth", timeoutTicks = 200)
    public static void noFreeBerthKeepsTheCommission(GameTestHelper h) {
        for (int x = 1; x < 39; x++) {
            for (int z = 1; z < 36; z++) {
                for (int y = 1; y <= 8; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        Port port = port(h, PortKind.NAVY_OUTPOST, List.of(BERTH_1));
        ServerPlayer p = serverPlayer(h);
        Careers.setNavy(p, NavyRank.CAPTAIN);
        commissionInHand(p);
        ShipGrants.Redeem r = ShipGrants.redeemAtDesk(p, h.absolutePos(DESK), p.getMainHandItem()).orElseThrow();
        h.assertValueEqual(r.outcome(), ShipGrants.Outcome.NO_BERTH, "redeem: " + r.message().getString());
        h.assertTrue(p.getMainHandItem().is(ShipGrantContent.SHIP_COMMISSION.get()), "commission kept");
        h.assertFalse(CareerRewards.gifted(p).contains(ShipGrantRules.redeemedKey(Ladder.NAVY)), "not noted as redeemed");
        h.assertTrue(shipsOf(h, p).isEmpty(), "no ship");
        PortRegistry.get(h.getLevel().getServer()).remove(port.id());
        h.succeed();
    }

    // ------------------------------------------------------------------ the toggle

    /** {@code careers.ship_grants.enabled = false}: a new Captain gets no commission, a held one is refused and kept. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, batch = CONFIG_BATCH + "toggle")
    public static void disabledGrantsNothing(GameTestHelper h) {
        ConfigOverrides.during(h, CareerConfig.SHIP_GRANTS_ENABLED, false);
        Port port = port(h, PortKind.NAVY_OUTPOST, List.of());
        ServerPlayer p = serverPlayer(h);
        Careers.setNavy(p, NavyRank.CAPTAIN);
        h.assertValueEqual(commissions(p), 0, "commissions while disabled");
        h.assertFalse(CareerRewards.gifted(p).contains(ShipGrantRules.grantKey(Ladder.NAVY)), "no grant noted");
        p.setItemInHand(InteractionHand.MAIN_HAND, ShipGrants.commission(p, Ladder.NAVY, ShipGrantRules.Params.defaults()));
        h.assertValueEqual(useDesk(h, p), ItemInteractionResult.CONSUME, "the desk answers");
        ShipGrants.Redeem r = ShipGrants.redeemAtDesk(p, h.absolutePos(DESK), p.getMainHandItem()).orElseThrow();
        h.assertValueEqual(r.verdict(), ShipGrantRules.Verdict.DISABLED, "redeem while disabled");
        h.assertTrue(p.getMainHandItem().is(ShipGrantContent.SHIP_COMMISSION.get()), "commission kept");
        PortRegistry.get(h.getLevel().getServer()).remove(port.id());
        h.succeed();
    }
}

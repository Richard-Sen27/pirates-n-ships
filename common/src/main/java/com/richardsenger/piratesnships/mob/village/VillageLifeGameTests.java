package com.richardsenger.piratesnships.mob.village;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.crew.hammock.HammockBlock;
import com.richardsenger.piratesnships.crew.hammock.ShipBunks;
import com.richardsenger.piratesnships.crew.hiring.HiringConfig;
import com.richardsenger.piratesnships.crew.hiring.HiringData;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.mob.MobKind;
import com.richardsenger.piratesnships.mob.village.ResidentPosts.Bed;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.station.StationGameTests;
import com.richardsenger.piratesnships.trade.TradeData;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.exchange.TransactionResult;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.PortProfile;
import com.richardsenger.piratesnships.trade.net.MarketBackend;
import com.richardsenger.piratesnships.trade.net.MarketPayloads;
import com.richardsenger.piratesnships.trade.net.MarketView;
import com.richardsenger.piratesnships.trade.stall.StallKind;
import com.richardsenger.piratesnships.trade.stall.StallTrades;
import com.richardsenger.piratesnships.trade.stall.StallWares;
import com.richardsenger.piratesnships.world.outpost.GarrisonPosts.PlacedPiece;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The villagers in a real server (LIFE1): each role stands in its building with its outfit, the fishwife's day takes her
 * from the rack to the fish market to bed, a vendor sells and buys at his stall, a hired villager boards as crew in its
 * outfit and its slot is filled again, and MOB3's monsters hunt villagers as they hunt sailors. Village plans come from
 * {@link VillagePlan} over pieces laid out in the test area (the posts only need a floor); the time-of-day tests each run
 * in their own batch, since they set the clock.
 */
public final class VillageLifeGameTests {

    private static final String BATCH = "pirates_n_ships_villagers_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_villagers_";

    private VillageLifeGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(VillageLifeGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    private static void floor(GameTestHelper h, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static PlacedPiece piece(GameTestHelper h, String name, int x, int z) {
        return new PlacedPiece(name, h.absolutePos(new BlockPos(x, 0, z)), Rotation.NONE);
    }

    private static ResourceLocation fakePort(String what) {
        return Constants.id("gametest/villagers_" + what + "_" + UUID.randomUUID().toString().substring(0, 8));
    }

    private static VillageResident spawn(GameTestHelper h, ResourceLocation port, ResidentPlan plan) {
        VillageResident v = Residents.spawn(h.getLevel(), port, plan);
        h.assertTrue(v != null, "the villager of " + plan.slot() + " spawns");
        return v;
    }

    private static void cleanUp(GameTestHelper h, ResourceLocation port) {
        ResidentRegistry.get(h.getLevel().getServer()).remove(port);
    }

    /** A real server player with a mock connection, not in the level (the market tests' pattern). */
    private static ServerPlayer player(GameTestHelper h, String name, long coins, BlockPos at) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(at)));
        p.getInventory().clearContent();
        Wallet.give(p, coins);
        MarketBackend.record(p.getUUID());
        return p;
    }

    private static void close(ServerPlayer p) {
        MarketBackend.close(p);
        MarketBackend.stopRecording(p.getUUID());
    }

    private static <T> List<T> sent(ServerPlayer p, Class<T> type) {
        List<CustomPacketPayload> rec = MarketBackend.record(p.getUUID());
        synchronized (rec) {
            return rec.stream().filter(type::isInstance).map(type::cast).toList();
        }
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static void setTime(GameTestHelper h, long time) {
        ServerLevel level = h.getLevel();
        level.setDayTime(time);
        com.richardsenger.piratesnships.crew.upkeep.ShipDayTick.observe(level);
    }

    private static double horizontal(VillageResident v, BlockPos p) {
        double dx = p.getX() + 0.5 - v.getX(), dz = p.getZ() + 0.5 - v.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A real bed (foot and head) where {@code bed} says. */
    private static void placeBed(ServerLevel level, Bed bed) {
        BlockState foot = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, bed.facing());
        level.setBlockAndUpdate(bed.foot(), foot.setValue(BedBlock.PART, BedPart.FOOT));
        level.setBlockAndUpdate(bed.head(), foot.setValue(BedBlock.PART, BedPart.HEAD));
    }

    // ------------------------------------------------------------------ roles and outfits

    /**
     * Every role spawns in its building with its role's outfit: one piece per role laid out in the test area, its plan
     * from the pieces, the villager on its first station inside the piece's footprint, synced role, persistent,
     * registered alive, named; and the dock head adds nobody (it keeps its harbor master).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, batch = BATCH + "roles")
    public static void eachRoleSpawnsWithItsOutfitInItsBuilding(GameTestHelper h) {
        floor(h, 40);
        List<PlacedPiece> pieces = List.of(piece(h, "house_small", 1, 1), piece(h, "house_fisher", 12, 1),
                piece(h, "net_loft", 26, 1), piece(h, "warehouse", 1, 12), piece(h, "shipwright", 14, 12),
                piece(h, "tavern", 27, 12), piece(h, "lighthouse", 1, 26), piece(h, "fish_market", 10, 26),
                piece(h, "dock_head", 22, 26));
        ResourceLocation port = fakePort("roles");
        List<ResidentPlan> plans = VillagePlan.plan(pieces);
        h.assertValueEqual(plans.size(), VillageRole.values().length, "one villager per role, none at the dock head");
        List<VillageRole> seen = new ArrayList<>();
        for (ResidentPlan plan : plans) {
            VillageResident v = spawn(h, port, plan);
            seen.add(v.role());
            String pieceName = plan.slot().substring(0, plan.slot().indexOf('@'));
            h.assertTrue(ResidentPosts.rolesOf(pieceName).contains(v.role()), v.role() + " belongs in " + pieceName);
            h.assertValueEqual(VillagerOutfit.of(v.role()).texture(), v.role().textureName(), "outfit of " + v.role());
            h.assertValueEqual(v.kind(), MobKind.VILLAGER, "kind");
            h.assertValueEqual(v.blockPosition(), plan.home().pos(), v.role() + " stands on its first station");
            PlacedPiece p = pieces.stream().filter(x -> plan.slot().startsWith(x.name() + "@")).findFirst().orElseThrow();
            BlockPos local = v.blockPosition().subtract(p.origin());
            h.assertTrue(local.getX() >= 0 && local.getX() < 16 && local.getZ() >= 0 && local.getZ() < 16,
                    v.role() + " inside its building: local " + local.toShortString());
            h.assertTrue(v.isPersistenceRequired(), "never despawns");
            h.assertTrue(v.hasCustomName(), "named");
            h.assertTrue(ResidentRegistry.get(h.getLevel().getServer()).get(port, plan.slot()).map(ResidentRegistry.Entry::alive).orElse(false),
                    "registered alive");
        }
        h.assertTrue(seen.containsAll(List.of(VillageRole.values())), "every role: " + seen);
        // the shipwright and the vendor keep stalls
        h.assertTrue(plans.stream().anyMatch(p -> p.stall().equals(Optional.of(StallKind.SHIPWRIGHT))), "the shipwright's stall");
        h.assertTrue(plans.stream().anyMatch(p -> p.stall().equals(Optional.of(StallKind.FISH))), "the fish stall");
        h.getLevel().getEntitiesOfClass(VillageResident.class, new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(48))
                .forEach(v -> v.discard());
        cleanUp(h, port);
        h.succeed();
    }

    /** {@code mobs.villagers.roles} off: a building's villager is a plain villager without a stall. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = CONFIG_BATCH + "roles_off")
    public static void withRolesOffEveryoneIsAPlainVillager(GameTestHelper h) {
        floor(h, 9);
        ConfigOverrides.during(h, VillagerConfig.ROLES, false);
        ResourceLocation port = fakePort("plain");
        ResidentPlan plan = VillagePlan.plan(List.of(piece(h, "fish_market", 0, 0))).get(0);
        VillageResident v = spawn(h, port, plan);
        h.assertValueEqual(v.role(), VillageRole.VILLAGER, "plain");
        h.assertTrue(v.plan().stall().isEmpty(), "no stall");
        v.discard();
        cleanUp(h, port);
        h.succeed();
    }

    // ------------------------------------------------------------------ the day

    /**
     * The fishwife's day with the clock set by the test: at 1000 she works at the drying rack in her yard, at 6000 at
     * the fish market's counter, at 14000 she lies in her bed (the sleep pose, on the mattress, facing the foot).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 1500, batch = BATCH + "fishwife_day")
    public static void theFishwifeGoesFromTheRackToTheStallToBed(GameTestHelper h) {
        floor(h, 40);
        List<ResidentPlan> plans = VillagePlan.plan(List.of(piece(h, "house_fisher", 2, 2), piece(h, "fish_market", 22, 14)));
        ResidentPlan plan = plans.stream().filter(p -> p.role() == VillageRole.FISHWIFE).findFirst().orElseThrow();
        Bed bed = plan.bed().orElseThrow(() -> new AssertionError("she has a bed"));
        placeBed(h.getLevel(), bed);
        ResourceLocation port = fakePort("fishwife");
        setTime(h, 1000);
        VillageResident v = spawn(h, port, plan);
        int[] stage = {0};
        h.onEachTick(() -> {
            switch (stage[0]) {
                case 0 -> {
                    if (v.isWorking() && horizontal(v, plan.station(0).pos()) <= 1.5) {
                        stage[0] = 1;
                        setTime(h, 6000);
                    }
                }
                case 1 -> {
                    if (v.isWorking() && horizontal(v, plan.station(1).pos()) <= 1.5) {
                        stage[0] = 2;
                        setTime(h, 14000);
                    }
                }
                case 2 -> {
                    if (v.isAsleep() && v.position().distanceTo(ResidentRoutineGoal.lyingSpot(bed)) < 0.6) {
                        h.assertValueEqual(v.pose(false), CrewPose.SLEEP, "the sleep pose");
                        h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(v.getYRot() - bed.facing().getOpposite().toYRot())) < 1,
                                "facing the foot: " + v.getYRot());
                        stage[0] = 3;
                        v.discard();
                        cleanUp(h, port);
                        h.succeed();
                    }
                }
                default -> {
                }
            }
        });
    }

    /** At dusk a villager takes a free seat and sits ({@code sit} pose, riding a bench seat); a taken seat is skipped. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 600, batch = BATCH + "dusk")
    public static void atDuskVillagersSitOnFreeSeats(GameTestHelper h) {
        floor(h, 24);
        List<ResidentPlan> plans = VillagePlan.plan(List.of(piece(h, "house_small", 1, 1), piece(h, "street_square_well", 8, 8)));
        ResidentPlan plan = plans.get(0);
        for (ResidentPosts.Post seat : plan.seats()) {
            h.getLevel().setBlockAndUpdate(seat.pos(), Blocks.SPRUCE_STAIRS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.StairBlock.FACING, seat.facing().getOpposite()));
        }
        ResourceLocation port = fakePort("dusk");
        setTime(h, 11000);
        VillageResident v = spawn(h, port, plan);
        h.succeedWhen(() -> {
            h.assertTrue(v.getVehicle() instanceof BenchSeat, "she sits on a seat");
            h.assertValueEqual(v.pose(false), CrewPose.SIT, "the sit pose");
            h.assertTrue(plan.seats().stream().anyMatch(s -> s.pos().closerToCenterThan(v.position(), 1.2)), "on one of her seats");
            v.discard();
            cleanUp(h, port);
        });
    }

    // ------------------------------------------------------------------ the market

    /**
     * A provisions vendor at his stall: talking opens the stall's market screen with its three wares only; salt pork
     * (fixed price) is bought from the purse into the inventory and sold back at half; rum (a trade good the port trades)
     * goes through the port's market; a ware of another stall is refused.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 100, batch = BATCH + "vendor")
    public static void aVendorSellsAndBuys(GameTestHelper h) {
        floor(h, 9);
        setTime(h, 3000);
        ResourceLocation port = fakePort("vendor");
        TradeService.openMarket(h.getLevel().getServer(), port,
                () -> new PortProfile(PortKind.SEAFARER_VILLAGE, Climate.TEMPERATE, 1L, Map.of(TradeGoods.RUM, GoodRole.NEUTRAL)));
        BlockPos stall = h.absolutePos(new BlockPos(4, 1, 4));
        ResidentPlan plan = new ResidentPlan("market_test@0,0,0#0", VillageRole.VENDOR,
                List.of(new ResidentPosts.Post(stall, Direction.NORTH)), Optional.empty(), List.of(), Optional.of(StallKind.PROVISIONS));
        VillageResident v = spawn(h, port, plan);
        ServerPlayer p = player(h, "vendor_customer", 100, new BlockPos(4, 1, 2));
        h.assertValueEqual(ResidentInteractions.talk(p, v), ResidentInteractions.Talk.STALL, "the stall opens");
        h.assertValueEqual(MarketBackend.stallOf(p, port), Optional.of(StallKind.PROVISIONS), "a stall session");
        List<MarketPayloads.State> states = sent(p, MarketPayloads.State.class);
        MarketView view = states.get(states.size() - 1).view().orElseThrow(() -> new AssertionError("no view"));
        h.assertValueEqual(view.goods().size(), StallWares.of(StallKind.PROVISIONS).size(), "the stall's wares only: " + view.goods());

        Item pork = BuiltInRegistries.ITEM.get(Constants.id("salt_pork"));
        long unit = StallTrades.unitBuy(StallWares.byLine(StallKind.PROVISIONS, Constants.id("salt_pork")).orElseThrow());
        TransactionResult bought = MarketBackend.trade(p, new MarketPayloads.Trade(port, true, Constants.id("salt_pork"), 2, false, Optional.empty()));
        h.assertTrue(bought.done(), "bought: " + bought);
        h.assertValueEqual(count(p, pork), 2, "two salt pork in the inventory");
        h.assertValueEqual(Wallet.count(p), 100 - 2 * unit, "paid from the purse (no reputation yet)");
        TransactionResult sold = MarketBackend.trade(p, new MarketPayloads.Trade(port, false, Constants.id("salt_pork"), 1, false, Optional.empty()));
        h.assertTrue(sold.done(), "sold: " + sold);
        h.assertValueEqual(count(p, pork), 1, "one sold back");
        h.assertValueEqual(Wallet.count(p), 100 - 2 * unit + StallTrades.unitSell(StallWares.byLine(StallKind.PROVISIONS, Constants.id("salt_pork")).orElseThrow()),
                "half the price back");
        TransactionResult rum = MarketBackend.trade(p, new MarketPayloads.Trade(port, true, TradeGoods.RUM, 1, false, Optional.empty()));
        h.assertTrue(rum.done(), "rum through the market: " + rum);
        h.assertValueEqual(count(p, BuiltInRegistries.ITEM.get(Constants.id("rum"))), 1, "a bottle of rum");
        TransactionResult other = MarketBackend.trade(p, new MarketPayloads.Trade(port, true, Constants.id("nails"), 1, false, Optional.empty()));
        h.assertValueEqual(other.status(), TransactionResult.Status.NOT_TRADED, "nails are the chandler's");
        close(p);
        v.discard();
        TradeData.get(h.getLevel().getServer()).removeMarket(port);
        cleanUp(h, port);
        h.succeed();
    }

    // ------------------------------------------------------------------ hiring

    /**
     * Sneak-using a dockhand twice hires him: the first names the fee, the second takes it and puts a crew member in the
     * dockhand's outfit on the player's ship moored at the port; the villager leaves the village and its slot is free,
     * and with {@code respawn_days} 0 a new dockhand moves in at the next check.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = CONFIG_BATCH + "hire")
    public static void aHiredVillagerBoardsAsCrewInItsOutfit(GameTestHelper h) {
        ServerPlayer p = player(h, "village_captain", 100, new BlockPos(12, 5, 20));
        StationGameTests.Fixture f = StationGameTests.ship(h, false, x -> {
            x.setBlock(new BlockPos(18, 7, 19), Blocks.OAK_FENCE);
            BlockState foot = CrewContent.HAMMOCK.get().defaultBlockState().setValue(HammockBlock.FACING, Direction.EAST);
            x.setBlock(new BlockPos(19, 7, 19), foot.setValue(HammockBlock.PART, BedPart.FOOT));
            x.setBlock(new BlockPos(20, 7, 19), foot.setValue(HammockBlock.PART, BedPart.HEAD));
        });
        ShipRegistry registry = ShipRegistry.get(h.getLevel().getServer());
        registry.put(registry.find(f.ship().id()).orElseThrow().withOwner(Optional.of(p.getUUID())));
        Port port = new Port(fakePort("hire"), PortKind.SEAFARER_VILLAGE, h.getLevel().dimension(), h.absolutePos(new BlockPos(20, 5, 20)),
                BoundingBox.fromCorners(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(39, 20, 39))), Climate.TEMPERATE, List.of());
        PortService.register(h.getLevel().getServer(), port);
        ConfigOverrides.during(h, VillagerConfig.RESPAWN_DAYS, 0);
        h.runAfterDelay(3, () -> {
            ResidentPlan plan = new ResidentPlan("warehouse@0,0,0#0", VillageRole.DOCKHAND,
                    List.of(new ResidentPosts.Post(h.absolutePos(new BlockPos(12, 5, 21)), Direction.NORTH)), Optional.empty(), List.of(),
                    Optional.empty());
            VillageResident v = spawn(h, port.id(), plan);
            h.assertValueEqual(ResidentInteractions.hire(p, v), ResidentInteractions.Talk.HIRE_OFFER, "the fee is named first");
            h.assertValueEqual(Wallet.count(p), 100L, "nothing paid yet");
            h.assertValueEqual(ResidentInteractions.hire(p, v), ResidentInteractions.Talk.HIRED, "hired on the second");
            h.assertValueEqual(Wallet.count(p), 100L - HiringConfig.FEE_SAILOR.get(), "the sailor's fee");
            h.assertTrue(v.isRemoved(), "gone from the village");
            List<CrewMember> aboard = ShipBunks.crewOf(h.getLevel(), f.ship());
            h.assertValueEqual(aboard.size(), 1, "one crew member aboard");
            CrewMember crew = aboard.get(0);
            h.assertValueEqual(crew.outfit(), VillageRole.DOCKHAND.id(), "in the dockhand's outfit");
            h.assertValueEqual(crew.getCustomName().getString(), v.getCustomName().getString(), "the villager's name");
            ResidentRegistry.Entry entry = ResidentRegistry.get(h.getLevel().getServer()).get(port.id(), plan.slot()).orElseThrow();
            h.assertFalse(entry.alive(), "the slot is free");
            h.assertTrue(Residents.check(h.getLevel().getServer()) >= 1, "a new dockhand moves in");
            List<VillageResident> back = h.getLevel().getEntitiesOfClass(VillageResident.class,
                    new net.minecraft.world.phys.AABB(plan.home().pos()).inflate(1), VillageResident::isAlive);
            h.assertValueEqual(back.size(), 1, "the new dockhand at the warehouse");
            h.assertValueEqual(back.get(0).role(), VillageRole.DOCKHAND, "a dockhand again");
            back.forEach(VillageResident::discard);
            crew.discard();
            close(p);
            PortRegistry.get(h.getLevel().getServer()).remove(port.id());
            TradeData.get(h.getLevel().getServer()).removeMarket(port.id());
            HiringData.get(h.getLevel().getServer()).forget(port.id());
            cleanUp(h, port.id());
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ monsters (MOB3)

    /** A zombie hunts a villager as it hunts sailors, and the villager runs from it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 120, batch = BATCH + "zombie")
    public static void aZombieHuntsAVillager(GameTestHelper h) {
        floor(h, 9);
        setTime(h, 6000);
        ResourceLocation port = fakePort("zombie");
        VillageResident v = spawn(h, port, ResidentPlan.standalone(VillageRole.NET_MENDER, h.absolutePos(new BlockPos(2, 1, 4)), Direction.NORTH));
        Zombie zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(6, 1, 4));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        h.succeedWhen(() -> {
            h.assertTrue(zombie.getTarget() == v, "the zombie did not target the villager: " + zombie.getTarget());
            h.assertTrue(v.fleesFrom(zombie), "the villager runs from it");
            v.discard();
            cleanUp(h, port);
        });
    }
}

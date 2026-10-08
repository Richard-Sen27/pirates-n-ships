package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.cannon.CannonShipHits;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.rpg.career.CareerConfig;
import com.richardsenger.piratesnships.rpg.career.Careers;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.ShipHelm;
import com.richardsenger.piratesnships.ship.hull.Compartment;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntime;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import com.richardsenger.piratesnships.trade.TradeData;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.materialize.MaterializeConfig;
import com.richardsenger.piratesnships.worldsim.materialize.Materializer;
import com.richardsenger.piratesnships.worldsim.materialize.RouteMath;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageCrew;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageEndings;
import com.richardsenger.piratesnships.trade.cargo.CargoContainerBlockEntity;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageData;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Quests at sea in a real server (QST2, docs/design.md §15): real NPC hulls in 48×48 basins, driven through the WS3b
 * endings like {@code MaterializeGameTests} (test voyages from unregistered {@code gametest/} ports, so the automatic
 * check leaves them alone). The quest-taking player is a server player that is not in the player list: it stands in
 * for the endings' player id ({@link SeaQuests#standIn}); where the ending needs a body aboard, a mock player with the
 * same id stands on the deck.
 */
public final class QuestSeaGameTests {

    private static final String BATCH = "pirates_n_ships_quests_sea";
    private static final double HEADING = 30.0;
    private static final int SURFACE = 7;
    private static final Map<UUID, List<VoyageEndings.Ending>> ENDINGS = new ConcurrentHashMap<>();

    static {
        VoyageEndings.onEnding(e -> {
            if (e.voyage().from().getPath().startsWith("gametest/qst2_")) {
                ENDINGS.computeIfAbsent(e.voyage().id(), k -> new CopyOnWriteArrayList<>()).add(e);
            }
        });
    }

    private QuestSeaGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(QuestSeaGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private static void basin(GameTestHelper h) {
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 48; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 47 || z == 0 || z == 47;
                for (int y = 2; y <= 8; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : y <= SURFACE ? Blocks.WATER : Blocks.AIR);
                }
                for (int y = 9; y <= 18; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (h.getBlockState(p).is(Blocks.BARRIER)) h.setBlock(p, Blocks.AIR);
                }
            }
        }
    }

    /** A SAILING test voyage through the basin's middle at {@link #HEADING}: two legs (100 behind, 60 and 200 ahead). */
    private static Voyage voyage(GameTestHelper h, VoyageKind kind, Faction faction, Map<ResourceLocation, Integer> cargo) {
        BlockPos c = h.absolutePos(new BlockPos(24, SURFACE, 24));
        Vec3 m = new Vec3(c.getX(), c.getY(), c.getZ());
        double dx = Math.sin(Math.toRadians(HEADING));
        double dz = -Math.cos(Math.toRadians(HEADING));
        List<Lane.Point> route = List.of(point(m, dx, dz, -100), point(m, dx, dz, 60), point(m, dx, dz, 200));
        ResourceLocation from = Constants.id("gametest/qst2_" + UUID.randomUUID().toString().substring(0, 8));
        Voyage v = Voyage.depart(UUID.randomUUID(), kind, faction, ShipTemplates.STARTER_SLOOP_ID, from, from, route, cargo,
                h.getLevel().getGameTime());
        v = v.withProgress(RouteMath.project(route, m.x, m.z, 100));
        VoyageData.get(h.getLevel().getServer()).put(v);
        return v;
    }

    private static Lane.Point point(Vec3 m, double dx, double dz, double d) {
        return new Lane.Point((int) Math.round(m.x + dx * d), (int) Math.round(m.z + dz * d));
    }

    private static Map<ResourceLocation, Integer> cargo() {
        Map<ResourceLocation, Integer> cargo = new LinkedHashMap<>();
        cargo.put(TradeGoods.SUGAR, 64);
        cargo.put(TradeGoods.IRON, 32);
        return cargo;
    }

    private static ShipBody materialize(GameTestHelper h, Voyage v) {
        MinecraftServer server = h.getLevel().getServer();
        Materializer.Outcome o = Materializer.materialize(server, v.id());
        Voyage m = Voyages.get(server, v.id()).orElseThrow();
        m.shipId().ifPresent(id -> ShipTestCleanup.track(h, id));
        h.assertTrue(o == Materializer.Outcome.SPAWNED, "materialize: " + o);
        ShipBody ship = SableShips.byId(h.getLevel(), m.shipId().orElseThrow());
        h.assertTrue(ship != null, "no ship");
        return ship;
    }

    private static void finish(GameTestHelper h, Voyage v, ServerPlayer p) {
        Voyages.end(h.getLevel().getServer(), v.id(), VoyageEnd.CANCELLED);
        VoyageCrew.discard(h.getLevel(), v.id());
        ENDINGS.remove(v.id());
        if (p != null) SeaQuests.dropStandIn(p.getUUID());
    }

    /** A real server player with a mock connection and the id {@code id}, not in the level, standing in for that id. */
    private static ServerPlayer questTaker(GameTestHelper h, UUID id) {
        MinecraftServer server = h.getLevel().getServer();
        SeaQuests.onServerStarted(server);
        var profile = new com.mojang.authlib.GameProfile(id, "qst2_test");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(server, h.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(server, connection, p, cookie);
        p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 9, 1))));
        p.getInventory().clearContent();
        SeaQuests.standIn(p);
        return p;
    }

    /** An active quest of {@code type} in {@code p}'s log, given by a pirate island or a navy outpost. */
    private static Quest give(GameTestHelper h, ServerPlayer p, QuestType type, QuestTarget target, int needed, long reward) {
        long day = Quests.day(h.getLevel().getServer());
        PortKind giver = type == QuestType.PLUNDER_CONVOY || type == QuestType.HUNT_PATROL ? PortKind.PIRATE_ISLAND : PortKind.NAVY_OUTPOST;
        Quest q = new Quest(UUID.randomUUID(), Constants.id("gametest/qst2_port"), giver, type, target, needed, 0, reward,
                QuestRules.rewardDeed(giver), day, day + 1, 0L, QuestState.OFFERED);
        q = QuestRules.accept(q, day, 5);
        Quests.store(p, QuestLog.EMPTY.with(q));
        return q;
    }

    /** A mock player on the deck next to the helm, with the id {@code id}. */
    private static Player boarder(GameTestHelper h, ShipBody ship) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        keepAboard(p, ship);
        h.getLevel().addFreshEntity(p);
        return p;
    }

    private static void keepAboard(Player p, ShipBody ship) {
        BlockPos helm = ShipHelm.steering(ship);
        Vec3 at = ship.toWorld(Vec3.atBottomCenterOf(helm.east()));
        p.moveTo(at.x, at.y + 0.05, at.z, 0f, 0f);
    }

    private static void flood(GameTestHelper h, ShipBody ship) {
        HullRuntime rt = HullRuntimes.get(h.getLevel(), ship.id());
        if (rt == null) return;
        for (Compartment c : rt.simulation().analysis().compartments()) rt.simulation().setVolume(c.id(), c.volume());
    }

    private static Optional<Quest> active(ServerPlayer p, UUID quest) {
        return Quests.log(p).find(quest);
    }

    // ------------------------------------------------------------------ tests
    // (every runAtTickTime / onEachTick is registered in the test body, as in MaterializeGameTests)

    /** Taking cargo from a merchant convoy while aboard is a plunder: a convoy raid of one completes and pays. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "_plunder")
    public static void plunderingAConvoyCompletesAConvoyRaid(GameTestHelper h) {
        basin(h);
        Voyage v = voyage(h, VoyageKind.CONVOY, Faction.MERCHANTS, cargo());
        MinecraftServer server = h.getLevel().getServer();
        ShipBody[] ship = new ShipBody[1];
        Player[] body = new Player[1];
        ServerPlayer[] p = new ServerPlayer[1];
        Quest[] q = new Quest[1];
        h.runAtTickTime(5, () -> {
            ship[0] = materialize(h, v);
            body[0] = boarder(h, ship[0]);
            p[0] = questTaker(h, body[0].getUUID());
            q[0] = give(h, p[0], QuestType.PLUNDER_CONVOY, QuestTarget.None.INSTANCE, 1, 90);
        });
        h.onEachTick(() -> {
            if (body[0] != null && !body[0].isRemoved()) keepAboard(body[0], ship[0]);
        });
        h.runAtTickTime(15, () -> {
            Materializer.update(server, v.id());
            ship[0].plotBlockEntities().stream().filter(be -> be instanceof CargoContainerBlockEntity c && c.count() >= 15)
                    .map(CargoContainerBlockEntity.class::cast).findFirst().orElseThrow().extract(10);
            Materializer.update(server, v.id());
            List<VoyageEndings.Ending> e = ENDINGS.getOrDefault(v.id(), List.of());
            h.assertTrue(e.size() == 1 && e.get(0).outcome() == VoyageEndings.Outcome.PLUNDERED, "endings " + e);
            h.assertTrue(active(p[0], q[0].id()).isEmpty(), "quest still active: " + active(p[0], q[0].id()));
            h.assertTrue(Quests.log(p[0]).completed(QuestType.PLUNDER_CONVOY) == 1, "not completed: " + Quests.log(p[0]));
            h.assertTrue(Wallet.count(p[0]) == 90, "paid " + Wallet.count(p[0]));
            body[0].discard();
            finish(h, v, p[0]);
            h.succeed();
        });
    }

    /**
     * A navy patrol sinking after the player's cannonball hit it (the {@code CannonShipHits} path) counts for the
     * patrol hunt; a pirate ship hunt of the same player does not move.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 300, batch = BATCH + "_sink")
    public static void sinkingAPatrolByCannonCreditsThePatrolHunt(GameTestHelper h) {
        basin(h);
        Voyage v = voyage(h, VoyageKind.PATROL, Faction.NAVY, Map.of());
        MinecraftServer server = h.getLevel().getServer();
        ShipBody[] ship = new ShipBody[1];
        ServerPlayer[] p = new ServerPlayer[1];
        Quest[] q = new Quest[2];
        h.runAtTickTime(5, () -> {
            ship[0] = materialize(h, v);
            p[0] = questTaker(h, UUID.randomUUID());
            q[0] = give(h, p[0], QuestType.HUNT_PATROL, QuestTarget.None.INSTANCE, 2, 300);
            Quest other = QuestRules.accept(new Quest(UUID.randomUUID(), Constants.id("gametest/qst2_port"), PortKind.NAVY_OUTPOST,
                    QuestType.HUNT_SHIP, QuestTarget.None.INSTANCE, 1, 0, 120, QuestRules.rewardDeed(PortKind.NAVY_OUTPOST),
                    0, 1, 0, QuestState.OFFERED), Quests.day(server), 5);
            q[1] = other;
            Quests.store(p[0], Quests.log(p[0]).with(other));
            Vec3 at = Materializer.centre(ship[0]);
            VoyageEndings.onShipHit(new CannonShipHits.ShipHit(h.getLevel(), ship[0].id(), p[0], null, at));
        });
        h.onEachTick(() -> {
            if (h.getTick() < 10 || ship[0] == null) return;
            List<VoyageEndings.Ending> e = ENDINGS.getOrDefault(v.id(), List.of());
            if (e.isEmpty()) {
                flood(h, ship[0]);
                Materializer.update(server, v.id());
                return;
            }
            h.assertTrue(e.get(0).outcome() == VoyageEndings.Outcome.SUNK && e.get(0).player().equals(Optional.of(p[0].getUUID())),
                    "ending " + e.get(0));
            Quest hunt = active(p[0], q[0].id()).orElseThrow();
            h.assertTrue(hunt.progress() == 1 && hunt.state() == QuestState.ACTIVE, "patrol hunt " + hunt);
            Quest ships = active(p[0], q[1].id()).orElseThrow();
            h.assertTrue(ships.progress() == 0, "ship hunt moved: " + ships);
            h.assertTrue(Wallet.count(p[0]) == 0, "paid " + Wallet.count(p[0]));
            finish(h, v, p[0]);
            h.succeed();
        });
    }

    /** Captures a pirate ship with the mock body aboard; checks the quest and the doubloons paid ({@code expected}). */
    private static void captureRun(GameTestHelper h, boolean letter, long expected) {
        ConfigOverrides.during(h, MaterializeConfig.CAPTURE_HOLD_TICKS, 20);
        basin(h);
        Voyage v = voyage(h, VoyageKind.RAID, Faction.PIRATES, Map.of());
        MinecraftServer server = h.getLevel().getServer();
        ShipBody[] ship = new ShipBody[1];
        Player[] body = new Player[1];
        ServerPlayer[] p = new ServerPlayer[1];
        Quest[] q = new Quest[1];
        h.runAtTickTime(5, () -> ship[0] = materialize(h, v));
        h.runAtTickTime(10, () -> {
            VoyageCrew.alive(h.getLevel(), ship[0], v.id(), VoyageCrew.FIGHTER_TAG).forEach(LivingEntity::kill);
            body[0] = boarder(h, ship[0]);
            p[0] = questTaker(h, body[0].getUUID());
            if (letter) Careers.forceLetter(p[0]);
            q[0] = give(h, p[0], QuestType.HUNT_SHIP, QuestTarget.None.INSTANCE, 1, 120);
        });
        h.onEachTick(() -> {
            if (body[0] == null || body[0].isRemoved()) return;
            keepAboard(body[0], ship[0]);
            List<VoyageEndings.Ending> e = ENDINGS.getOrDefault(v.id(), List.of());
            if (e.isEmpty()) {
                Materializer.update(server, v.id());
                return;
            }
            h.assertTrue(e.get(0).outcome() == VoyageEndings.Outcome.CAPTURED, "ending " + e.get(0));
            h.assertTrue(Quests.log(p[0]).completed(QuestType.HUNT_SHIP) == 1, "ship hunt not completed: " + Quests.log(p[0]));
            h.assertTrue(Wallet.count(p[0]) == expected, "paid " + Wallet.count(p[0]) + ", expected " + expected);
            // the ending fires once per voyage: a repeated award pays nothing
            h.assertTrue(com.richardsenger.piratesnships.rpg.career.ShipPrizes.award(p[0], v.id(), Faction.PIRATES, true) == 0,
                    "paid twice");
            body[0].discard();
            finish(h, v, p[0]);
            h.succeed();
        });
    }

    /** Capturing a pirate ship under a letter of marque: the ship hunt pays 120, the letter 60 more. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 300, batch = "pirates_n_ships_config_quests_sea_capture_letter")
    public static void capturingAPirateShipUnderALetterPaysThePrize(GameTestHelper h) {
        captureRun(h, true, 120 + CareerConfig.PRIZE_MONEY.get());
    }

    /** The same capture without a letter: only the ship hunt's reward. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 300, batch = "pirates_n_ships_config_quests_sea_capture_plain")
    public static void capturingAPirateShipWithoutALetterPaysNoPrize(GameTestHelper h) {
        captureRun(h, false, 120);
    }

    /** An escort bound to a two-leg convoy (one leg needed) and the player close by when it is polled. */
    private static Quest escort(GameTestHelper h, ServerPlayer p, Voyage v, ShipBody ship) {
        Quest q = give(h, p, QuestType.ESCORT, new QuestTarget.Escort(Constants.id("gametest/qst2_dest")), 1, 150);
        q = QuestRules.bindEscort(q, v.id(), Materializer.name(v), v.waypoints().size() - 1, 0.5);
        Quests.store(p, QuestLog.EMPTY.with(q));
        Vec3 c = Materializer.centre(ship);
        p.setPos(c.x + 10, c.y + 3, c.z);
        return q;
    }

    /** Sailing close by on one of two legs and the convoy arriving completes the escort and pays. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "_escort")
    public static void anEscortCompletesWhenTheConvoyArrives(GameTestHelper h) {
        basin(h);
        Voyage v = voyage(h, VoyageKind.CONVOY, Faction.MERCHANTS, cargo());
        MinecraftServer server = h.getLevel().getServer();
        ShipBody[] ship = new ShipBody[1];
        h.runAtTickTime(5, () -> ship[0] = materialize(h, v));
        h.runAtTickTime(15, () -> {
            ServerPlayer p = questTaker(h, UUID.randomUUID());
            Quest q = escort(h, p, v, ship[0]);
            h.assertTrue(q.needed() == 1 && ((QuestTarget.Escort) q.target()).legs() == 2, "escort " + q);
            // the record's leg would do too; with the ship real, its centre's leg counts
            h.assertTrue(!SeaQuests.pollEscort(p, (QuestTarget.Escort) q.target()).isEmpty(), "close by, not counted");
            Quest seen = active(p, q.id()).orElseThrow();
            h.assertTrue(seen.progress() == 1 && seen.state() == QuestState.ACTIVE, "after the poll " + seen);
            h.assertTrue(SeaQuests.pollEscort(p, (QuestTarget.Escort) seen.target()).isEmpty(), "the same leg counted twice");
            Voyages.end(server, v.id(), VoyageEnd.ARRIVED);
            h.assertTrue(active(p, q.id()).isEmpty() && Quests.log(p).completed(QuestType.ESCORT) == 1, "not completed " + Quests.log(p));
            h.assertTrue(Wallet.count(p) == 150, "paid " + Wallet.count(p));
            finish(h, v, p);
            h.succeed();
        });
    }

    /** A player too far away counts no leg; the convoy then sinks: the escort fails, nothing paid. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 300, batch = BATCH + "_escort_sunk")
    public static void anEscortFailsWhenTheConvoySinks(GameTestHelper h) {
        basin(h);
        Voyage v = voyage(h, VoyageKind.CONVOY, Faction.MERCHANTS, cargo());
        MinecraftServer server = h.getLevel().getServer();
        ShipBody[] ship = new ShipBody[1];
        ServerPlayer[] p = new ServerPlayer[1];
        Quest[] q = new Quest[1];
        h.runAtTickTime(5, () -> {
            ship[0] = materialize(h, v);
            p[0] = questTaker(h, UUID.randomUUID());
            q[0] = escort(h, p[0], v, ship[0]);
            Vec3 c = Materializer.centre(ship[0]);
            p[0].setPos(c.x + QuestConfig.ESCORT_RADIUS.get() + 20, c.y, c.z);
            h.assertTrue(SeaQuests.pollEscort(p[0], (QuestTarget.Escort) q[0].target()).isEmpty(), "counted from afar");
        });
        h.onEachTick(() -> {
            if (h.getTick() < 10 || ship[0] == null) return;
            List<VoyageEndings.Ending> e = ENDINGS.getOrDefault(v.id(), List.of());
            if (e.isEmpty()) {
                flood(h, ship[0]);
                Materializer.update(server, v.id());
                return;
            }
            h.assertTrue(e.get(0).outcome() == VoyageEndings.Outcome.SUNK, "ending " + e.get(0));
            h.assertTrue(active(p[0], q[0].id()).isEmpty(), "escort still active");
            h.assertTrue(Quests.log(p[0]).failed() == 1 && Quests.log(p[0]).completed(QuestType.ESCORT) == 0, "log " + Quests.log(p[0]));
            h.assertTrue(Wallet.count(p[0]) == 0, "paid " + Wallet.count(p[0]));
            finish(h, v, p[0]);
            h.succeed();
        });
    }

    /**
     * A registered village sees the registered navy outpost as an escort destination and a convoy at sea, so it can offer
     * an escort; with {@code quests.sea_quests} off no sea quest is offerable.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 100, batch = "pirates_n_ships_config_quests_sea_toggle")
    public static void seaQuestsAreOfferedOnlyWhileOn(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Port village = port(h, PortKind.SEAFARER_VILLAGE, new BlockPos(2, 1, 2));
        Port outpost = port(h, PortKind.NAVY_OUTPOST, new BlockPos(30, 1, 30));
        Voyage convoy = voyage(h, VoyageKind.CONVOY, Faction.MERCHANTS, cargo());
        try {
            long day = Quests.day(server);
            QuestGenerator.Context ctx = Quests.context(server, village.id(), day).orElseThrow();
            h.assertTrue(ctx.sea().convoys(), "no convoy seen at sea");
            h.assertTrue(ctx.sea().escorts().stream().anyMatch(o -> o.destination().equals(outpost.id())), "escorts " + ctx.sea().escorts());
            QuestParams params = QuestConfig.params();
            h.assertTrue(QuestGenerator.offerable(ctx, params).contains(QuestType.ESCORT), "escort not offerable");
            Quest offer = QuestGenerator.offer(QuestType.ESCORT, ctx, 1, params).orElseThrow();
            h.assertTrue(offer.target() instanceof QuestTarget.Escort e && e.destination().equals(outpost.id()), "offer " + offer);
            ConfigOverrides.during(h, QuestConfig.SEA_QUESTS, false);
            QuestParams off = QuestConfig.params();
            h.assertTrue(QuestGenerator.offerable(ctx, off).stream().noneMatch(QuestType::seaQuest), "sea quests while off");
            QuestGenerator.Context island = ctx;
            h.assertTrue(QuestGenerator.offer(QuestType.ESCORT, island, 1, off).isEmpty(), "escort offered while off");
        } finally {
            Voyages.end(server, convoy.id(), VoyageEnd.CANCELLED);
            for (Port port : List.of(village, outpost)) {
                PortRegistry.get(server).remove(port.id());
                TradeData.get(server).removeMarket(port.id());
                QuestData.get(server).forget(port.id());
            }
        }
        h.succeed();
    }

    private static Port port(GameTestHelper h, PortKind kind, BlockPos at) {
        BlockPos min = h.absolutePos(at);
        BlockPos max = h.absolutePos(at.offset(6, 4, 6));
        Port port = new Port(Constants.id("gametest/qst2_" + kind.getSerializedName() + "_" + UUID.randomUUID().toString().substring(0, 8)),
                kind, h.getLevel().dimension(), h.absolutePos(at.offset(3, 0, 3)), BoundingBox.fromCorners(min, max), Climate.TEMPERATE,
                List.of(), List.of());
        PortService.register(h.getLevel().getServer(), port);
        return port;
    }
}

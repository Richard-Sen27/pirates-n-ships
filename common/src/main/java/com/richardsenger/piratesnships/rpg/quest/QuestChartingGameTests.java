package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.trade.TradeData;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.PortProfile;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.worldsim.lane.Lanes;
import com.richardsenger.piratesnships.worldsim.lane.SeaGrid;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * QST2b: accepting an escort never charts a sea lane on the tick. Two registered villages far east of the test area
 * ({@code gametest/qst2b_…}, removed at the end) with open markets; a cached lane (on {@link SeaGrid#allSea}) sails the
 * convoy at once, an uncharted one leaves the quest CHARTING until the lane is in the cache, a failed lane (the flat
 * test world has no sea, searched by the scheduler's own {@link Lanes#computeNext}) or the chart timeout calls it off.
 */
public final class QuestChartingGameTests {

    private static final String BATCH = "pirates_n_ships_quests_charting";

    private QuestChartingGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(QuestChartingGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private record Harbors(MinecraftServer server, Port a, Port b) {
        void cleanUp(ServerPlayer p) {
            for (Voyage v : Voyages.active(server)) {
                if (v.from().equals(a.id()) || v.to().equals(a.id()) || v.from().equals(b.id()) || v.to().equals(b.id())) {
                    Voyages.end(server, v.id(), VoyageEnd.CANCELLED);
                }
            }
            for (ResourceLocation id : List.of(a.id(), b.id())) {
                PortRegistry.get(server).remove(id);
                TradeData.get(server).removeMarket(id);
                QuestData.get(server).forget(id);
                Lanes.forgetPort(server, id);
            }
            if (p != null) SeaQuests.dropStandIn(p.getUUID());
        }
    }

    private static Port port(GameTestHelper h, String name, int dx) {
        BlockPos centre = h.absolutePos(BlockPos.ZERO).offset(20_000 + dx, 0, 0).atY(63);
        ResourceLocation id = Constants.id("gametest/qst2b_" + name + "_" + UUID.randomUUID().toString().substring(0, 8));
        return new Port(id, PortKind.SEAFARER_VILLAGE, h.getLevel().dimension(), centre,
                BoundingBox.fromCorners(centre.offset(-8, -8, -8), centre.offset(8, 8, 8)), Climate.TEMPERATE, List.of());
    }

    private static PortProfile profile(ResourceLocation produces, ResourceLocation demands) {
        Map<ResourceLocation, GoodRole> roles = new HashMap<>();
        for (ResourceLocation g : TradeService.goods(false).tradeable().ids()) roles.put(g, GoodRole.NEUTRAL);
        roles.put(produces, GoodRole.PRODUCES);
        roles.put(demands, GoodRole.DEMANDS);
        return new PortProfile(PortKind.SEAFARER_VILLAGE, Climate.TEMPERATE, 0L, roles);
    }

    /** Villages A and B 600 blocks apart with open markets; no lane between them yet. */
    private static Harbors harbors(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Port a = port(h, "a", 0);
        Port b = port(h, "b", 600);
        PortRegistry.get(server).add(a);
        PortRegistry.get(server).add(b);
        TradeService.openMarket(server, a.id(), () -> profile(TradeGoods.SUGAR, TradeGoods.IRON));
        TradeService.openMarket(server, b.id(), () -> profile(TradeGoods.IRON, TradeGoods.SUGAR));
        return new Harbors(server, a, b);
    }

    /** Charts the lane A → B on open sea, as the scheduler's background search would leave it in the cache. */
    private static void chartLane(GameTestHelper h, Harbors hb) {
        h.assertTrue(Lanes.compute(hb.server(), hb.a(), hb.b(), SeaGrid.allSea(32)).lane().isPresent(), "lane on open sea");
    }

    /** A real server player with a mock connection (not in the level). */
    private static ServerPlayer player(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qst2b_test");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(server, h.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(server, connection, p, cookie);
        p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1))));
        p.getInventory().clearContent();
        SeaQuests.onServerStarted(server);
        SeaQuests.standIn(p);
        return p;
    }

    /** An escort offer from A to B, made by hand after today's generated offers, accepted by {@code p}. */
    private static Quest acceptEscort(GameTestHelper h, Harbors hb, ServerPlayer p) {
        MinecraftServer server = hb.server();
        Quests.refresh(server, hb.a().id());
        long day = Quests.day(server);
        Quest offer = new Quest(UUID.randomUUID(), hb.a().id(), PortKind.SEAFARER_VILLAGE, QuestType.ESCORT, new QuestTarget.Escort(hb.b().id()),
                1, 0, 150, QuestRules.rewardDeed(PortKind.SEAFARER_VILLAGE), day, day + 1, 0L, QuestState.OFFERED);
        QuestData.get(server).addOffer(offer);
        Quests.Result r = Quests.accept(p, hb.a().id(), offer.id());
        h.assertTrue(r.done(), "accept refused: " + r.key());
        h.assertTrue(QuestData.get(server).offer(hb.a().id(), offer.id()).isEmpty(), "offer still open");
        return r.quest().orElseThrow();
    }

    private static List<Voyage> convoysFrom(Harbors hb) {
        return Voyages.active(hb.server()).stream().filter(v -> v.from().equals(hb.a().id())).toList();
    }

    private static void run(GameTestHelper h, Harbors hb, ServerPlayer p, Runnable body) {
        try {
            body.run();
        } finally {
            hb.cleanUp(p);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ tests

    /** A cached lane: accepting sails and names the convoy at once, the escort is active (as in QST2). */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void aCachedLaneSailsAtOnce(GameTestHelper h) {
        Harbors hb = harbors(h);
        ServerPlayer p = player(h);
        run(h, hb, p, () -> {
            chartLane(h, hb);
            Quest q = acceptEscort(h, hb, p);
            h.assertValueEqual(q.state(), QuestState.ACTIVE, "state");
            QuestTarget.Escort e = (QuestTarget.Escort) q.target();
            h.assertTrue(e.voyage().isPresent() && !e.name().isEmpty() && e.chartingSince().isEmpty(), "bound " + e);
            h.assertTrue(Voyages.get(hb.server(), e.voyage().get()).isPresent(), "convoy at sea");
            h.assertValueEqual(Quests.log(p).find(q.id()).orElseThrow(), q, "stored");
        });
    }

    /**
     * An uncharted lane: accepting queues it and the escort is CHARTING with nothing at sea; polls wait while it is
     * queued; once the lane is in the cache the next poll sails and names the convoy and the escort is active.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void anUnchartedLaneChartsFirstAndSailsWhenReady(GameTestHelper h) {
        Harbors hb = harbors(h);
        ServerPlayer p = player(h);
        MinecraftServer server = hb.server();
        run(h, hb, p, () -> {
            Quest q = acceptEscort(h, hb, p);
            h.assertValueEqual(q.state(), QuestState.CHARTING, "state");
            QuestTarget.Escort e = (QuestTarget.Escort) q.target();
            h.assertTrue(e.voyage().isEmpty() && e.chartingSince().isPresent(), "charting " + e);
            h.assertTrue(convoysFrom(hb).isEmpty(), "a convoy sailed before its lane");
            h.assertValueEqual(Lanes.status(server, hb.a().id(), hb.b().id()), Lanes.Status.QUEUED, "lane queued, not searched");
            h.assertTrue(QuestTracker.poll(p).isEmpty(), "a poll changed a quest while charting");
            h.assertValueEqual(Quests.log(p).find(q.id()).orElseThrow().state(), QuestState.CHARTING, "still charting");

            chartLane(h, hb);
            List<Quest> changed = QuestTracker.poll(p);
            h.assertValueEqual(changed.size(), 1, "changed " + changed);
            Quest sailing = Quests.log(p).find(q.id()).orElseThrow();
            h.assertValueEqual(sailing.state(), QuestState.ACTIVE, "state after the lane");
            QuestTarget.Escort s = (QuestTarget.Escort) sailing.target();
            h.assertTrue(s.voyage().isPresent() && !s.name().isEmpty() && s.chartingSince().isEmpty(), "bound " + s);
            h.assertTrue(Voyages.get(server, s.voyage().get()).isPresent(), "convoy at sea");
            h.assertValueEqual(convoysFrom(hb).size(), 1, "one convoy");
        });
    }

    /** The scheduler's background search fails (no sea in the flat world): the next poll calls the escort off. */
    // its own batch: the search loop below would also work off the other tests' queued pairs
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "_failed")
    public static void aFailedLaneCallsTheEscortOff(GameTestHelper h) {
        Harbors hb = harbors(h);
        ServerPlayer p = player(h);
        MinecraftServer server = hb.server();
        run(h, hb, p, () -> {
            Quest q = acceptEscort(h, hb, p);
            h.assertValueEqual(q.state(), QuestState.CHARTING, "state");
            int calls = 0;
            while (Lanes.status(server, hb.a().id(), hb.b().id()) == Lanes.Status.QUEUED && calls < 1000) {
                Lanes.computeNext(server);
                calls++;
            }
            h.assertValueEqual(Lanes.status(server, hb.a().id(), hb.b().id()), Lanes.Status.FAILED, "searched");
            QuestTracker.poll(p);
            h.assertTrue(Quests.log(p).find(q.id()).isEmpty(), "escort still in the log");
            h.assertValueEqual(Quests.log(p).failed(), 0, "a called-off escort is not a failure");
            h.assertTrue(convoysFrom(hb).isEmpty(), "a convoy sailed");
            h.assertValueEqual(Wallet.count(p), 0L, "no coins");
        });
    }

    /**
     * {@code escort_chart_timeout_ticks} after accepting without a lane, the escort is called off: out of the log, not
     * counted as failed, nothing taken from the player (escorts take no deposit) and no convoy; the offer stays gone.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void theChartTimeoutCallsTheEscortOff(GameTestHelper h) {
        Harbors hb = harbors(h);
        ServerPlayer p = player(h);
        MinecraftServer server = hb.server();
        run(h, hb, p, () -> {
            long coins = Wallet.count(p);
            Quest q = acceptEscort(h, hb, p);
            h.assertValueEqual(q.state(), QuestState.CHARTING, "state");
            h.assertValueEqual(Wallet.count(p), coins, "accepting took coins");
            long now = server.overworld().getGameTime();
            int timeout = QuestConfig.ESCORT_CHART_TIMEOUT_TICKS.get();
            // one tick short: still waiting
            Quests.store(p, Quests.log(p).with(QuestRules.chart(q, now - timeout + 1)));
            h.assertTrue(QuestTracker.poll(p).isEmpty(), "called off before the timeout");
            h.assertTrue(Quests.log(p).find(q.id()).isPresent(), "gone before the timeout");
            // the timeout reached
            Quests.store(p, Quests.log(p).with(QuestRules.chart(q, now - timeout)));
            List<Quest> changed = QuestTracker.poll(p);
            h.assertValueEqual(changed.size(), 1, "changed " + changed);
            h.assertTrue(Quests.log(p).find(q.id()).isEmpty(), "escort still in the log");
            h.assertValueEqual(Quests.log(p).failed(), 0, "a called-off escort is not a failure");
            h.assertValueEqual(Wallet.count(p), coins, "coins after the timeout");
            h.assertTrue(convoysFrom(hb).isEmpty(), "a convoy sailed");
        });
    }
}

package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.bounty.PirateTier;
import com.richardsenger.piratesnships.mob.captain.CaptainRegistry;
import com.richardsenger.piratesnships.mob.captain.IslandCaptains;
import com.richardsenger.piratesnships.mob.captain.PirateCaptain;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import com.richardsenger.piratesnships.trade.TradeData;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.net.MarketBackend;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Captain hunts in a real server (QST1b, docs/design.md §15): offered at a village or navy outpost only while a living
 * captain is in reach (never at a pirate island), withdrawn when he is lost; completed by the player's kill or turn-in
 * with the reward; failed when he dies by other hands, and his successor never counts. Each test uses its own fake
 * port and island ids and removes them, their captains' registry entries and bounties at the end.
 */
public final class CaptainHuntGameTests {

    private static final String BATCH = "pirates_n_ships_quests_captain";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_quests_captain_radius";

    private CaptainHuntGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CaptainHuntGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** A real server player with a mock connection, not added to the level (the quest tests' pattern). */
    private static ServerPlayer player(GameTestHelper h, String name) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1))));
        p.getInventory().clearContent();
        MarketBackend.record(p.getUUID());
        return p;
    }

    /** A registered port of {@code kind} centred at (4, 1, 4) of the test area. */
    private static Port port(GameTestHelper h, PortKind kind) {
        Port port = new Port(Constants.id("gametest/hunt_" + kind.getSerializedName() + "_" + UUID.randomUUID().toString().substring(0, 8)),
                kind, h.getLevel().dimension(), h.absolutePos(new BlockPos(4, 1, 4)),
                BoundingBox.fromCorners(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(8, 5, 8))), Climate.TEMPERATE,
                List.of(), List.of());
        PortService.register(h.getLevel().getServer(), port);
        return port;
    }

    private static ResourceLocation island() {
        return Constants.id("gametest/hunt_island_" + UUID.randomUUID());
    }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static PirateCaptain captain(GameTestHelper h, ResourceLocation island, int x, int z, int generation) {
        PirateCaptain c = IslandCaptains.spawn(h.getLevel(), island, h.absolutePos(new BlockPos(x, 1, z)), Direction.NORTH, generation);
        h.assertTrue(c != null, "the captain of " + island + " spawns");
        c.setNoAi(true);
        return c;
    }

    /** A hand-made captain hunt for {@code c} at {@code port} (today, open two days). */
    private static Quest offer(GameTestHelper h, Port port, PirateCaptain c, long reward) {
        MinecraftServer server = h.getLevel().getServer();
        Quests.refresh(server, port.id());
        long day = Quests.day(server);
        Quest q = new Quest(UUID.randomUUID(), port.id(), port.kind(), QuestType.HUNT_CAPTAIN,
                new QuestTarget.Victim(c.getUUID(), c.getName().getString(), Optional.of("east")), 1, 0, reward,
                QuestRules.rewardDeed(port.kind()), day, day + 1, 0L, QuestState.OFFERED);
        QuestData.get(server).addOffer(q);
        return q;
    }

    private static Quest accept(GameTestHelper h, ServerPlayer p, Quest offer) {
        Quests.Result r = Quests.accept(p, offer.port(), offer.id());
        h.assertTrue(r.done(), "accept refused: " + r.key());
        return r.quest().orElseThrow();
    }

    private static void cleanup(GameTestHelper h, List<ServerPlayer> players, List<ResourceLocation> islands, List<UUID> captains,
                                Port... ports) {
        MinecraftServer server = h.getLevel().getServer();
        for (ServerPlayer p : players) MarketBackend.stopRecording(p.getUUID());
        for (ResourceLocation island : islands) {
            CaptainRegistry.get(server).get(island).ifPresent(e -> LawService.withdrawBounties(server, e.id()));
            CaptainRegistry.get(server).remove(island);
        }
        for (UUID id : captains) LawService.withdrawBounties(server, id);
        for (Port port : ports) {
            PortRegistry.get(server).remove(port.id());
            TradeData.get(server).removeMarket(port.id());
            QuestData.get(server).forget(port.id());
        }
    }

    // ------------------------------------------------------------------ offers

    /**
     * With {@code captain_hunt_radius} 8 (neighbouring tests stay out of reach): no captain hunt before a captain lives
     * nearby; then a village and an outpost offer one for him (target, name, bearing, reward × scale) and a pirate island
     * never does; once he is lost the open offer is withdrawn and a stale one is refused.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = CONFIG_BATCH)
    public static void offeredOnlyWhileACaptainLivesInReach(GameTestHelper h) {
        floor(h);
        MinecraftServer server = h.getLevel().getServer();
        ConfigOverrides.during(h, QuestConfig.CAPTAIN_HUNT_RADIUS, 8);
        Port village = port(h, PortKind.SEAFARER_VILLAGE);
        Port outpost = port(h, PortKind.NAVY_OUTPOST);
        Port pirates = port(h, PortKind.PIRATE_ISLAND);
        h.assertTrue(Quests.offerOfType(server, village.id(), QuestType.HUNT_CAPTAIN).isEmpty(), "no captain in reach, no hunt");

        ResourceLocation island = island();
        PirateCaptain c = captain(h, island, 7, 4, 0);
        Quest q = Quests.offerOfType(server, village.id(), QuestType.HUNT_CAPTAIN)
                .orElseThrow(() -> new AssertionError("no captain hunt at the village"));
        QuestTarget.Victim v = (QuestTarget.Victim) q.target();
        h.assertValueEqual(v.id(), c.getUUID(), "the target is the captain");
        h.assertValueEqual(v.name(), c.getName().getString(), "named after him");
        h.assertValueEqual(v.bearing(), Optional.of("east"), "his island lies east of the port");
        h.assertValueEqual(q.rewardCoins(), Math.max(1L, Math.round(QuestConfig.CAPTAIN.get() * QuestConfig.REWARD_SCALE.get())), "reward");
        h.assertValueEqual(q.needed(), 1, "one captain");
        h.assertTrue(Quests.offerOfType(server, outpost.id(), QuestType.HUNT_CAPTAIN).isPresent(), "offered at a navy outpost");
        h.assertTrue(Quests.offerOfType(server, pirates.id(), QuestType.HUNT_CAPTAIN).isEmpty(), "never at a pirate island");
        h.assertTrue(Quests.offers(server, village.id()).stream().anyMatch(o -> o.id().equals(q.id())), "the offer is open");

        c.hurt(h.getLevel().damageSources().generic(), 1000f);
        h.assertTrue(c.isDeadOrDying(), "the captain fell");
        h.assertTrue(Quests.offers(server, village.id()).stream().noneMatch(o -> o.type() == QuestType.HUNT_CAPTAIN),
                "the hunt is withdrawn once he is lost");
        h.assertTrue(Quests.offerOfType(server, village.id(), QuestType.HUNT_CAPTAIN).isEmpty(), "no new hunt for a lost captain");
        ServerPlayer p = player(h, "late_hunter");
        Quest stale = offer(h, village, c, 400);
        Quests.Result r = Quests.accept(p, village.id(), stale.id());
        h.assertFalse(r.done(), "a stale hunt accepted");
        h.assertValueEqual(r.key(), Quests.CAPTAIN_GONE, "refusal");
        cleanup(h, List.of(p), List.of(island), List.of(c.getUUID()), village, outpost, pirates);
        h.succeed();
    }

    // ------------------------------------------------------------------ completion

    /** The player's kill completes the hunt with its doubloons and the outpost's quest deed. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void completesOnThePlayersKill(GameTestHelper h) {
        floor(h);
        Port outpost = port(h, PortKind.NAVY_OUTPOST);
        ResourceLocation island = island();
        PirateCaptain c = captain(h, island, 4, 4, 0);
        ServerPlayer p = player(h, "captain_hunter");
        accept(h, p, offer(h, outpost, c, 400));
        long coins = Wallet.count(p);
        int completedBefore = Quests.log(p).completed(QuestType.HUNT_CAPTAIN);
        c.hurt(p.damageSources().playerAttack(p), 1000f);
        h.assertTrue(c.isDeadOrDying(), "the captain fell");
        QuestLog log = Quests.log(p);
        h.assertTrue(log.active().isEmpty(), "the hunt left the log");
        h.assertValueEqual(log.completed(QuestType.HUNT_CAPTAIN), completedBefore + 1, "completed");
        h.assertValueEqual(Wallet.count(p) - coins, 400L, "reward paid");
        h.assertTrue(QuestTracker.poll(p).isEmpty(), "nothing left for the poll");
        cleanup(h, List.of(p), List.of(island), List.of(c.getUUID()), outpost);
        h.succeed();
    }

    /** Turned in alive (the turn_in_pirate deed names him): complete, then he is handed over without failing it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void completesOnTheTurnIn(GameTestHelper h) {
        floor(h);
        Port village = port(h, PortKind.SEAFARER_VILLAGE);
        ResourceLocation island = island();
        PirateCaptain c = captain(h, island, 4, 4, 0);
        ServerPlayer p = player(h, "captain_catcher");
        accept(h, p, offer(h, village, c, 400));
        h.assertTrue(Reputation.enabled(), "reputation records the turn-in deed (default on)");
        long coins = Wallet.count(p);
        LawService.turnInPirate(c, PirateTier.CAPTAIN, p);
        c.discard(); // handed over: the island loses its captain
        h.assertFalse(CaptainRegistry.get(h.getLevel().getServer()).get(island).orElseThrow().alive(), "the island lost him");
        h.assertTrue(Quests.log(p).active().isEmpty(), "the hunt is done");
        h.assertValueEqual(Quests.log(p).failed(), 0, "not failed");
        h.assertValueEqual(Wallet.count(p) - coins, 400L, "the quest reward (the officer's pay is the caller's)");
        h.assertTrue(QuestTracker.poll(p).isEmpty(), "the loss does not fail a finished hunt");
        cleanup(h, List.of(p), List.of(island), List.of(c.getUUID()), village);
        h.succeed();
    }

    /**
     * Killed by another player: the hunter's kill of the successor does not count, and the next poll fails the hunt with
     * no reward.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void failsWhenTheCaptainDiesByOtherHands(GameTestHelper h) {
        floor(h);
        Port village = port(h, PortKind.SEAFARER_VILLAGE);
        ResourceLocation island = island();
        PirateCaptain c = captain(h, island, 3, 4, 0);
        ServerPlayer hunter = player(h, "slow_hunter");
        ServerPlayer rival = player(h, "rival");
        Quest active = accept(h, hunter, offer(h, village, c, 400));
        h.assertTrue(QuestTracker.poll(hunter).isEmpty(), "nothing while he lives");
        c.hurt(rival.damageSources().playerAttack(rival), 1000f);
        h.assertTrue(c.isDeadOrDying(), "the rival got him");
        h.assertValueEqual(Quests.log(hunter).find(active.id()).map(Quest::state), Optional.of(QuestState.ACTIVE),
                "the rival's kill does not complete the hunter's quest");

        PirateCaptain successor = captain(h, island, 5, 4, 1);
        h.assertFalse(successor.getUUID().equals(c.getUUID()), "a new man");
        successor.hurt(hunter.damageSources().playerAttack(hunter), 1000f);
        h.assertTrue(successor.isDeadOrDying(), "the successor fell");
        h.assertTrue(Quests.log(hunter).find(active.id()).isPresent(), "the successor does not count");

        List<Quest> changed = QuestTracker.poll(hunter);
        h.assertTrue(changed.size() == 1 && changed.get(0).state() == QuestState.FAILED, "failed at the poll: " + changed);
        h.assertTrue(Quests.log(hunter).active().isEmpty(), "left the log");
        h.assertValueEqual(Quests.log(hunter).failed(), 1, "failed count");
        h.assertValueEqual(Wallet.count(hunter), 0L, "no reward");
        cleanup(h, List.of(hunter, rival), List.of(island), List.of(c.getUUID(), successor.getUUID()), village);
        h.succeed();
    }
}

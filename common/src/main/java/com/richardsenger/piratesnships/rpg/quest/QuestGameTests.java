package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.entity.Shark;
import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.rpg.deeds.DeedContext;
import com.richardsenger.piratesnships.rpg.deeds.Deeds;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import com.richardsenger.piratesnships.trade.TradeData;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.desk.HarborDeskBlockEntity;
import com.richardsenger.piratesnships.trade.desk.HarborDesks;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.net.MarketBackend;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.world.port.TreasureSite;
import com.richardsenger.piratesnships.world.treasure.TreasureBinding;
import com.richardsenger.piratesnships.world.treasure.TreasureMapContent;
import com.richardsenger.piratesnships.world.treasure.TreasureMapData;
import com.richardsenger.piratesnships.world.treasure.TreasureMapService;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Quests in a real server (QST1, docs/design.md §15): offers per port kind, accepting into the log, each type's
 * progress and completion with its reward, the deadline, the active cap, abandoning, the desk's Quests tab, the
 * commands and the {@code quests.enabled} toggle. Ports are built directly with {@code gametest/} ids and removed at the
 * end (with their market and offers); offers are mostly made by hand so amounts are known.
 */
public final class QuestGameTests {

    private static final String BATCH = "pirates_n_ships_quests";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_quests";

    private QuestGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(QuestGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** A real server player with a mock connection, not added to the level (the market tests' pattern). */
    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "quest_test");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(h.absolutePos(at)));
        p.getInventory().clearContent();
        MarketBackend.record(p.getUUID());
        return p;
    }

    private static ServerPlayer player(GameTestHelper h) {
        return player(h, new BlockPos(1, 1, 1));
    }

    /** A registered port of {@code kind} over the test area (its market opens), with {@code treasures}. */
    private static Port port(GameTestHelper h, PortKind kind, List<TreasureSite> treasures) {
        BlockPos min = h.absolutePos(BlockPos.ZERO);
        BlockPos max = h.absolutePos(new BlockPos(8, 5, 8));
        Port port = new Port(Constants.id("gametest/quest_" + kind.getSerializedName() + "_" + UUID.randomUUID().toString().substring(0, 8)),
                kind, h.getLevel().dimension(), h.absolutePos(new BlockPos(4, 1, 4)), BoundingBox.fromCorners(min, max), Climate.TEMPERATE,
                List.of(), treasures);
        PortService.register(h.getLevel().getServer(), port);
        return port;
    }

    private static Port port(GameTestHelper h, PortKind kind) {
        return port(h, kind, List.of());
    }

    private static void cleanup(GameTestHelper h, ServerPlayer p, Port... ports) {
        MinecraftServer server = h.getLevel().getServer();
        if (p != null) {
            MarketBackend.close(p);
            MarketBackend.stopRecording(p.getUUID());
        }
        for (Port port : ports) {
            PortRegistry.get(server).remove(port.id());
            TradeData.get(server).removeMarket(port.id());
            QuestData.get(server).forget(port.id());
        }
    }

    /** An offer made by hand at {@code port} (today, open two days) and added to the port's offers. */
    private static Quest offer(GameTestHelper h, Port port, QuestType type, QuestTarget target, int needed, long reward) {
        MinecraftServer server = h.getLevel().getServer();
        Quests.refresh(server, port.id()); // today's generated offers first, so they don't replace this one
        long day = Quests.day(server);
        Quest q = new Quest(UUID.randomUUID(), port.id(), port.kind(), type, target, needed, 0, reward, QuestRules.rewardDeed(port.kind()),
                day, day + 1, 0L, QuestState.OFFERED);
        QuestData.get(server).addOffer(q);
        return q;
    }

    private static Quest accept(GameTestHelper h, ServerPlayer p, Quest offer) {
        Quests.Result r = Quests.accept(p, offer.port(), offer.id());
        h.assertTrue(r.done(), "accept refused: " + r.key());
        return r.quest().orElseThrow();
    }

    private static void near(GameTestHelper h, int actual, int expected, String what) {
        h.assertTrue(Math.abs(actual - expected) <= 1, what + ": expected " + expected + " (±1), got " + actual);
    }

    // ------------------------------------------------------------------ offers

    /** Every port kind gets {@code offers_per_port} offers of types it may give; the list is stable within a day. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void offersForEveryPortKind(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Port village = port(h, PortKind.SEAFARER_VILLAGE);
        Port outpost = port(h, PortKind.NAVY_OUTPOST);
        Port island = port(h, PortKind.PIRATE_ISLAND, List.of(new TreasureSite(h.absolutePos(new BlockPos(2, 1, 2)), false)));
        for (Port port : List.of(village, outpost, island)) {
            List<Quest> offers = Quests.offers(server, port.id());
            h.assertValueEqual(offers.size(), QuestConfig.OFFERS_PER_PORT.get(), "offers at " + port.kind());
            for (Quest q : offers) {
                h.assertValueEqual(q.state(), QuestState.OFFERED, "offer state");
                h.assertTrue(q.type().available() && q.type().allowedAt(port.kind()), q.type() + " offered at " + port.kind());
                h.assertValueEqual(q.giver(), port.kind(), "giver");
                h.assertValueEqual(q.rewardDeed(), QuestRules.rewardDeed(port.kind()), "reward deed");
                h.assertTrue(q.rewardCoins() > 0, "reward");
            }
            h.assertValueEqual(Quests.offers(server, port.id()), offers, "same offers again today at " + port.kind());
        }
        cleanup(h, null, village, outpost, island);
        h.succeed();
    }

    // ------------------------------------------------------------------ types

    /** Pirate hunt at an outpost: accepted into the log, three kill deeds complete it with coins and the navy quest deed. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void huntPiratesCompletesWithCoinsAndDeed(GameTestHelper h) {
        Port outpost = port(h, PortKind.NAVY_OUTPOST);
        ServerPlayer p = player(h);
        Quest offer = offer(h, outpost, QuestType.HUNT_PIRATES, QuestTarget.None.INSTANCE, 3, 90);
        Quest active = accept(h, p, offer);
        h.assertValueEqual(active.state(), QuestState.ACTIVE, "accepted state");
        h.assertValueEqual(active.deadlineDay(), Quests.day(h.getLevel().getServer()) + QuestConfig.DEADLINE_DAYS.get(), "deadline");
        h.assertValueEqual(Quests.log(p).active().size(), 1, "in the log");
        h.assertTrue(QuestData.get(h.getLevel().getServer()).offer(outpost.id(), offer.id()).isEmpty(), "offer taken off the port");
        Deeds.record(p, Deed.KILL_PIRATE, DeedContext.NONE);
        Deeds.record(p, Deed.KILL_PIRATE, DeedContext.NONE);
        h.assertValueEqual(Quests.log(p).find(offer.id()).orElseThrow().progress(), 2, "progress after two kills");
        Deeds.record(p, Deed.KILL_NAVY, DeedContext.NONE);
        h.assertValueEqual(Quests.log(p).find(offer.id()).orElseThrow().progress(), 2, "a navy kill doesn't count");
        long coins = Wallet.count(p);
        Deeds.record(p, Deed.KILL_PIRATE, DeedContext.NONE);
        QuestLog log = Quests.log(p);
        h.assertTrue(log.active().isEmpty(), "done quest left the log");
        h.assertValueEqual(log.completed(QuestType.HUNT_PIRATES), 1, "completed count");
        h.assertValueEqual(Wallet.count(p) - coins, 90L, "reward paid");
        // navy: 3 × kill pirate (+5), kill navy (−15), the quest (+6); pirates: 3 × −15, +5, −2
        near(h, Reputation.get(p, Faction.NAVY), 15 - 15 + 6, "navy reputation");
        near(h, Reputation.get(p, Faction.PIRATES), -45 + 5 - 2, "pirate reputation");
        cleanup(h, p, outpost);
        h.succeed();
    }

    /** Monster hunt: a shark killed by the player completes it; the pirate quest deed at an island. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void killMonsterCountsTheSharkThePlayerKills(GameTestHelper h) {
        Port island = port(h, PortKind.PIRATE_ISLAND);
        ServerPlayer p = player(h);
        Quest offer = offer(h, island, QuestType.KILL_MONSTER, new QuestTarget.Kill(QuestGenerator.SHARK), 1, 25);
        accept(h, p, offer);
        Shark shark = h.spawn(MobContent.SHARK.get(), new BlockPos(4, 2, 4));
        long coins = Wallet.count(p);
        shark.hurt(p.damageSources().playerAttack(p), 1000f);
        h.succeedWhen(() -> {
            h.assertTrue(shark.isDeadOrDying(), "shark dead");
            h.assertTrue(Quests.log(p).active().isEmpty(), "quest done");
            h.assertValueEqual(Quests.log(p).completed(QuestType.KILL_MONSTER), 1, "completed count");
            h.assertValueEqual(Wallet.count(p) - coins, 25L, "reward");
            near(h, Reputation.get(p, Faction.PIRATES), 6, "pirate quest deed");
            cleanup(h, p, island);
        });
    }

    /** Prisoner delivery: two turn-in deeds complete it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void turnInCountsTurnedInPirates(GameTestHelper h) {
        Port outpost = port(h, PortKind.NAVY_OUTPOST);
        ServerPlayer p = player(h);
        Quest offer = offer(h, outpost, QuestType.TURN_IN, QuestTarget.None.INSTANCE, 2, 100);
        accept(h, p, offer);
        Deeds.record(p, Deed.TURN_IN_PIRATE, DeedContext.NONE);
        h.assertValueEqual(Quests.log(p).find(offer.id()).orElseThrow().progress(), 1, "one turned in");
        Deeds.record(p, Deed.TURN_IN_PIRATE, DeedContext.NONE);
        h.assertTrue(Quests.log(p).active().isEmpty(), "quest done");
        h.assertValueEqual(Wallet.count(p), 100L, "reward");
        cleanup(h, p, outpost);
        h.succeed();
    }

    /** Treasure hunt: accepting hands out a map bound to the site; looting the site completes it at the next poll. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void findTreasureHandsAMapAndCompletesWhenLooted(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        BlockPos site = h.absolutePos(new BlockPos(2, 1, 2));
        Port island = port(h, PortKind.PIRATE_ISLAND, List.of(new TreasureSite(site, false)));
        ServerPlayer p = player(h);
        Quest offer = offer(h, island, QuestType.FIND_TREASURE, new QuestTarget.Treasure(island.id(), site), 1, 100);
        accept(h, p, offer);
        ItemStack map = ItemStack.EMPTY;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(TreasureMapContent.TREASURE_MAP.get())) map = p.getInventory().getItem(i);
        }
        h.assertFalse(map.isEmpty(), "a treasure map was handed out");
        TreasureMapData data = TreasureMapService.data(map);
        h.assertTrue(data != null && data.port().equals(island.id()) && data.site().equals(site), "map bound to the site: " + data);
        h.assertTrue(QuestTracker.poll(p).isEmpty(), "nothing changes before the dig");
        Port looted = TreasureBinding.markLooted(PortRegistry.get(server).index().byId(island.id()).orElseThrow(), List.of(site),
                TreasureMapService.CHEST_REACH).orElseThrow();
        TreasureMapService.replace(PortRegistry.get(server), looted);
        List<Quest> changed = QuestTracker.poll(p);
        h.assertTrue(changed.size() == 1 && changed.get(0).state() == QuestState.DONE, "done at the poll: " + changed);
        h.assertValueEqual(Wallet.count(p), 100L, "reward");
        cleanup(h, p, island);
        h.succeed();
    }

    /**
     * Cargo run: accepting makes a contract held by the player (quest reward, no deposit, the quest's deadline);
     * delivering it completes the quest at the next poll, which pays no coins twice but records the village deed.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void deliverCompletesWhenTheContractIsDelivered(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Port village = port(h, PortKind.SEAFARER_VILLAGE);
        Port outpost = port(h, PortKind.NAVY_OUTPOST);
        ServerPlayer p = player(h);
        Quest offer = offer(h, village, QuestType.DELIVER, new QuestTarget.Cargo(TradeGoods.SUGAR, 8, outpost.id(), Optional.empty()), 1, 120);
        Quest active = accept(h, p, offer);
        UUID contractId = ((QuestTarget.Cargo) active.target()).contract().orElseThrow(() -> new AssertionError("no contract made"));
        DeliveryContract c = TradeService.contract(server, contractId).orElseThrow();
        h.assertValueEqual(c.state(), DeliveryContract.State.ACCEPTED, "contract accepted");
        h.assertValueEqual(c.holder(), Optional.of(p.getUUID()), "held by the player");
        h.assertValueEqual(c.reward(), 120, "contract reward = quest reward");
        h.assertValueEqual(c.deposit(), 0, "no deposit");
        h.assertValueEqual(c.deadlineDay(), active.deadlineDay(), "same deadline");
        h.assertValueEqual(c.destination(), outpost.id(), "destination");
        h.assertTrue(TradeService.contractsOf(server, p.getUUID()).stream().anyMatch(x -> x.id().equals(contractId)), "in the contracts tab");
        h.assertTrue(QuestTracker.poll(p).isEmpty(), "nothing before delivery");
        var delivered = TradeService.deliver(server, contractId, p.getUUID(), outpost.id(), 8).orElseThrow();
        h.assertValueEqual(delivered.outcome(), DeliveryContract.Outcome.DELIVERED, "delivered");
        h.assertValueEqual(delivered.payout(), 120, "the contract pays the reward");
        List<Quest> changed = QuestTracker.poll(p);
        h.assertTrue(changed.size() == 1 && changed.get(0).state() == QuestState.DONE, "done: " + changed);
        h.assertValueEqual(Wallet.count(p), 0L, "the quest pays nothing on top");
        near(h, Reputation.get(p, Faction.VILLAGERS), 4, "village quest deed");
        cleanup(h, p, village, outpost);
        h.succeed();
    }

    // ------------------------------------------------------------------ rules in the world

    /** The deadline: still active on the deadline day, failed the day after. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void passedDeadlineFailsTheQuest(GameTestHelper h) {
        Port outpost = port(h, PortKind.NAVY_OUTPOST);
        ServerPlayer p = player(h);
        Quest active = accept(h, p, offer(h, outpost, QuestType.HUNT_PIRATES, QuestTarget.None.INSTANCE, 5, 150));
        h.assertTrue(QuestTracker.pollAt(p, active.deadlineDay()).isEmpty(), "still on the deadline day");
        List<Quest> changed = QuestTracker.pollAt(p, active.deadlineDay() + 1);
        h.assertTrue(changed.size() == 1 && changed.get(0).state() == QuestState.FAILED, "failed: " + changed);
        h.assertTrue(Quests.log(p).active().isEmpty(), "left the log");
        h.assertValueEqual(Quests.log(p).failed(), 1, "failed count");
        h.assertValueEqual(Wallet.count(p), 0L, "no reward");
        cleanup(h, p, outpost);
        h.succeed();
    }

    /** {@code max_active} (3): a fourth quest is refused and stays offered. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void maxActiveRefusesAFourthQuest(GameTestHelper h) {
        Port outpost = port(h, PortKind.NAVY_OUTPOST);
        ServerPlayer p = player(h);
        for (int i = 0; i < QuestConfig.MAX_ACTIVE.get(); i++) {
            accept(h, p, offer(h, outpost, QuestType.HUNT_PIRATES, QuestTarget.None.INSTANCE, 3, 90));
        }
        Quest fourth = offer(h, outpost, QuestType.TURN_IN, QuestTarget.None.INSTANCE, 1, 50);
        Quests.Result r = Quests.accept(p, outpost.id(), fourth.id());
        h.assertFalse(r.done(), "fourth accepted");
        h.assertValueEqual(r.key(), QuestRules.TOO_MANY, "refusal");
        h.assertTrue(QuestData.get(h.getLevel().getServer()).offer(outpost.id(), fourth.id()).isPresent(), "still offered");
        h.assertValueEqual(Quests.log(p).active().size(), QuestConfig.MAX_ACTIVE.get(), "active quests");
        cleanup(h, p, outpost);
        h.succeed();
    }

    // ------------------------------------------------------------------ the desk tab

    /** Opening a desk sends the Quests tab; accepting through it answers with the result; without a session it refuses. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void deskTabListsAndAcceptsQuests(GameTestHelper h) {
        Port village = port(h, PortKind.SEAFARER_VILLAGE);
        BlockPos desk = new BlockPos(4, 1, 4);
        h.setBlock(desk, HarborDesks.HARBOR_DESK.get().defaultBlockState());
        ((HarborDeskBlockEntity) h.getBlockEntity(desk)).setPort(Optional.of(village.id()));
        ServerPlayer p = player(h, desk.north());
        BlockPos abs = h.absolutePos(desk);
        h.getBlockState(desk).useWithoutItem(h.getLevel(), p, new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false));
        h.assertTrue(MarketBackend.canUse(p, village.id()), "desk session open");
        QuestPayloads.QuestsPayload opened = lastQuests(h, p);
        QuestPayloads.QuestsView view = opened.view().orElseThrow(() -> new AssertionError("no quests tab"));
        h.assertValueEqual(view.offers().size(), QuestConfig.OFFERS_PER_PORT.get(), "offers in the tab");
        h.assertTrue(view.mine().isEmpty(), "no active quests yet");
        // a hunt by hand (a generated delivery or treasure would hand out things)
        Quest offer = offer(h, village, QuestType.HUNT_PIRATES, QuestTarget.None.INSTANCE, 3, 90);
        MarketBackend.handleQuest(p, new QuestPayloads.QuestAction(village.id(), true, offer.id()));
        QuestPayloads.QuestsPayload answer = lastQuests(h, p);
        h.assertTrue(answer.result().orElseThrow().done(), "accepted through the tab: " + answer.result());
        h.assertValueEqual(answer.view().orElseThrow().mine().size(), 1, "active quest in the tab");
        MarketBackend.close(p);
        MarketBackend.handleQuest(p, new QuestPayloads.QuestAction(village.id(), false, offer.id()));
        QuestPayloads.QuestsPayload refused = lastQuests(h, p);
        h.assertValueEqual(refused.result().orElseThrow().key(), Quests.NO_SESSION, "no session");
        h.assertValueEqual(Quests.log(p).active().size(), 1, "quest kept");
        cleanup(h, p, village);
        h.succeed();
    }

    private static QuestPayloads.QuestsPayload lastQuests(GameTestHelper h, ServerPlayer p) {
        List<CustomPacketPayload> sent = MarketBackend.record(p.getUUID());
        synchronized (sent) {
            List<QuestPayloads.QuestsPayload> qs = sent.stream().filter(QuestPayloads.QuestsPayload.class::isInstance)
                    .map(QuestPayloads.QuestsPayload.class::cast).toList();
            h.assertFalse(qs.isEmpty(), "a quests payload was sent");
            return qs.get(qs.size() - 1);
        }
    }

    // ------------------------------------------------------------------ commands

    private static final class Recorder implements CommandSource {
        final List<String> lines = new ArrayList<>();

        @Override public void sendSystemMessage(Component component) { lines.add(component.getString()); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }

    private static Recorder run(GameTestHelper h, ServerPlayer p, int permission, String command) {
        Recorder out = new Recorder();
        var source = p.createCommandSourceStack().withSource(out).withPermission(permission);
        h.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
        return out;
    }

    /** {@code list} shows the quest with its id, {@code abandon} drops it, ops {@code offer} adds and {@code complete} pays. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void commandsListAbandonOfferAndComplete(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Port outpost = port(h, PortKind.NAVY_OUTPOST);
        ServerPlayer p = player(h);
        Quest active = accept(h, p, offer(h, outpost, QuestType.HUNT_PIRATES, QuestTarget.None.INSTANCE, 4, 120));
        Recorder list = run(h, p, 0, "pirates quest list");
        h.assertTrue(list.lines.stream().anyMatch(l -> l.contains(active.shortId())), "list: " + list.lines);
        run(h, p, 0, "pirates quest abandon " + active.shortId());
        h.assertTrue(Quests.log(p).active().isEmpty(), "abandoned");
        h.assertValueEqual(Quests.log(p).failed(), 1, "abandon counts as failed");

        int before = QuestData.get(server).offers(outpost.id()).size();
        run(h, p, 0, "pirates quest offer " + outpost.id() + " turn_in");
        h.assertValueEqual(QuestData.get(server).offers(outpost.id()).size(), before, "offer needs an operator");
        run(h, p, 2, "pirates quest offer " + outpost.id() + " hunt_navy");
        h.assertValueEqual(QuestData.get(server).offers(outpost.id()).size(), before, "no navy hunt at an outpost");
        run(h, p, 2, "pirates quest offer " + outpost.id() + " turn_in");
        List<Quest> offers = QuestData.get(server).offers(outpost.id());
        h.assertValueEqual(offers.size(), before + 1, "operator offer added");
        Quest added = offers.get(offers.size() - 1);
        h.assertValueEqual(added.type(), QuestType.TURN_IN, "offered type");

        Quest second = accept(h, p, added);
        run(h, p, 2, "pirates quest complete " + second.shortId());
        h.assertTrue(Quests.log(p).active().isEmpty(), "completed by the operator");
        h.assertValueEqual(Wallet.count(p), second.rewardCoins(), "operator completion pays");
        cleanup(h, p, outpost);
        h.succeed();
    }

    // ------------------------------------------------------------------ toggle

    /** {@code quests.enabled = false}: no offers, no tab, accepting refused. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = CONFIG_BATCH)
    public static void disabledQuestsOfferNothing(GameTestHelper h) {
        Port outpost = port(h, PortKind.NAVY_OUTPOST);
        ServerPlayer p = player(h);
        Quest offer = offer(h, outpost, QuestType.HUNT_PIRATES, QuestTarget.None.INSTANCE, 3, 90);
        ConfigOverrides.during(h, QuestConfig.ENABLED, false);
        h.assertTrue(Quests.offers(h.getLevel().getServer(), outpost.id()).isEmpty(), "no offers while off");
        h.assertTrue(QuestBackend.view(p, outpost.id()).isEmpty(), "no tab while off");
        Quests.Result r = Quests.accept(p, outpost.id(), offer.id());
        h.assertFalse(r.done(), "accepted while off");
        h.assertValueEqual(r.key(), Quests.DISABLED, "refusal");
        cleanup(h, p, outpost);
        h.succeed();
    }
}

package com.richardsenger.piratesnships.rpg;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.crime.WantedLevel;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.rpg.deeds.DeedContext;
import com.richardsenger.piratesnships.rpg.deeds.Deeds;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import com.richardsenger.piratesnships.rpg.reputation.ReputationAttachments;
import com.richardsenger.piratesnships.rpg.reputation.ReputationConfig;
import com.richardsenger.piratesnships.rpg.reputation.ReputationRecord;
import com.richardsenger.piratesnships.rpg.reputation.ReputationRules;
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.exchange.TransactionResult;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.PortProfile;
import com.richardsenger.piratesnships.trade.net.MarketBackend;
import com.richardsenger.piratesnships.trade.net.MarketPayloads;
import com.richardsenger.piratesnships.trade.net.MarketView;
import com.richardsenger.piratesnships.trade.plunder.PlunderMark;
import com.richardsenger.piratesnships.trade.plunder.PlunderRules;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reputation in a real server (REP1, docs/design.md §15): deeds and their configured deltas, persistence through death
 * and NBT, decay, the market price swing and refusal, pirate and navy hostility, the false-flag rule and port fees, the
 * commands, and the {@code reputation.enabled} toggle. Tests that change config run in batches of their own
 * ({@code pirates_n_ships_config_rpg_*}).
 */
public final class ReputationGameTests {

    private static final String BATCH = "pirates_n_ships_rpg_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_rpg_";

    private ReputationGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(ReputationGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    /** A real server player with a mock connection, not added to the level or the player list (as in the trade tests). */
    private static ServerPlayer serverPlayer(GameTestHelper helper, UUID id, String name) {
        var profile = new com.mojang.authlib.GameProfile(id, name);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        p.getInventory().clearContent();
        return p;
    }

    private static ServerPlayer serverPlayer(GameTestHelper helper) {
        return serverPlayer(helper, UUID.randomUUID(), "rep_test");
    }

    /** A survival mock player in the level (mob targeting queries the level), discarded when the test ends. */
    private static Player playerInLevel(GameTestHelper helper, Vec3 relative) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, 90f, 0f);
        helper.getLevel().addFreshEntity(player);
        testInfo(helper).addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo testInfo) { }
            @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { player.discard(); }
            @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { player.discard(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { player.discard(); }
        });
        return player;
    }

    private static GameTestInfo testInfo(GameTestHelper helper) {
        try {
            for (Field f : GameTestHelper.class.getDeclaredFields()) {
                if (f.getType() == GameTestInfo.class) {
                    f.setAccessible(true);
                    return (GameTestInfo) f.get(helper);
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("GameTestHelper has no GameTestInfo field");
    }

    private static void floor(GameTestHelper h, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static <T extends SeafarerMob> T spawn(GameTestHelper h, EntityType<T> type, int x, int z) {
        T mob = h.spawn(type, new BlockPos(x, 1, z));
        mob.setYRot(-90);
        mob.setYHeadRot(-90);
        mob.yBodyRot = -90;
        return mob;
    }

    private static ResourceLocation port(GameTestHelper helper, PortKind kind) {
        ResourceLocation id = Constants.id("gametest/rep_" + UUID.randomUUID());
        TradeService.openMarket(helper.getLevel().getServer(), id,
                () -> new PortProfile(kind, Climate.TROPICAL, 1L, Map.of(TradeGoods.SUGAR, GoodRole.NEUTRAL)));
        return id;
    }

    private static long quote(GameTestHelper helper, ResourceLocation port, Market.Side side, int quantity) {
        return TradeService.quote(helper.getLevel().getServer(), port, TradeGoods.SUGAR, side, quantity).total();
    }

    private static MarketView.GoodLine sugarLine(ServerPlayer p, ResourceLocation port, int quantity) {
        MarketView view = MarketBackend.view(p, port, quantity).orElseThrow(() -> new AssertionError("no market view"));
        return view.goods().stream().filter(l -> l.good().equals(TradeGoods.SUGAR)).findFirst()
                .orElseThrow(() -> new AssertionError("no sugar line"));
    }

    private static TransactionResult trade(ServerPlayer p, ResourceLocation port, boolean buy, int quantity, boolean plundered) {
        return MarketBackend.trade(p, new MarketPayloads.Trade(port, buy, TradeGoods.SUGAR, quantity, plundered, Optional.empty()));
    }

    private static int delta(Deed deed, Faction faction) {
        return ReputationConfig.DEED_DELTAS.get(deed).get(faction).get();
    }

    // ------------------------------------------------------------------ deeds and persistence

    /**
     * A recorded deed and a real kill change the scores by the configured deltas; the scores survive death (the clone
     * NeoForge's respawn makes) and an NBT round trip (relog).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "deeds")
    public static void deedsChangeScoresAndSurviveDeathAndRelog(GameTestHelper h) {
        floor(h, 9);
        UUID id = UUID.randomUUID();
        ServerPlayer p = serverPlayer(h, id, "rep_deeds");
        Map<Faction, Integer> applied = Deeds.record(p, Deed.KILL_NAVY, DeedContext.NONE);
        h.assertValueEqual(applied.getOrDefault(Faction.NAVY, 0), delta(Deed.KILL_NAVY, Faction.NAVY), "applied navy delta");
        h.assertValueEqual(Reputation.get(p, Faction.NAVY), delta(Deed.KILL_NAVY, Faction.NAVY), "navy after kill_navy");
        h.assertValueEqual(Reputation.get(p, Faction.PIRATES), delta(Deed.KILL_NAVY, Faction.PIRATES), "pirates after kill_navy");
        h.assertValueEqual(Reputation.get(p, Faction.VILLAGERS), delta(Deed.KILL_NAVY, Faction.VILLAGERS), "villagers after kill_navy");

        // A real kill of a pirate: the killing blow is attack_pirate, the death kill_pirate
        Pirate pirate = h.spawnWithNoFreeWill(MobContent.PIRATE.get(), 4, 1, 4);
        pirate.hurt(p.damageSources().playerAttack(p), 1000f);
        h.assertTrue(pirate.isDeadOrDying(), "the pirate survived");
        for (Faction f : Faction.values()) {
            int expected = delta(Deed.KILL_NAVY, f) + delta(Deed.ATTACK_PIRATE, f) + delta(Deed.KILL_PIRATE, f);
            h.assertValueEqual(Reputation.get(p, f), expected, f.id() + " after killing a pirate");
        }
        int navy = Reputation.get(p, Faction.NAVY);
        int pirates = Reputation.get(p, Faction.PIRATES);

        // Death: the respawned player is a clone, and the attachment is copied on death
        ServerPlayer respawned = serverPlayer(h, id, "rep_deeds");
        respawned.restoreFrom(p, false);
        h.assertValueEqual(Reputation.get(respawned, Faction.NAVY), navy, "navy after death");
        h.assertValueEqual(Reputation.get(respawned, Faction.PIRATES), pirates, "pirates after death");

        // Relog: saved with the player
        CompoundTag tag = respawned.saveWithoutId(new CompoundTag());
        ServerPlayer loaded = serverPlayer(h, id, "rep_deeds");
        loaded.load(tag);
        h.assertValueEqual(Reputation.get(loaded, Faction.NAVY), navy, "navy after NBT round trip");
        h.assertValueEqual(Reputation.get(loaded, Faction.PIRATES), pirates, "pirates after NBT round trip");
        h.succeed();
    }

    /** The deltas come from config: an overridden {@code reputation.deeds.kill_navy.navy} applies. */
    @ModGameTest(batch = CONFIG_BATCH + "deed_delta")
    public static void deedDeltasComeFromConfig(GameTestHelper h) {
        ConfigOverrides.during(h, ReputationConfig.DEED_DELTAS.get(Deed.KILL_NAVY).get(Faction.NAVY), -40);
        ConfigOverrides.during(h, ReputationConfig.DEED_DELTAS.get(Deed.KILL_NAVY).get(Faction.PIRATES), 0);
        ServerPlayer p = serverPlayer(h);
        Map<Faction, Integer> applied = Deeds.record(p, Deed.KILL_NAVY, DeedContext.NONE);
        h.assertValueEqual(Reputation.get(p, Faction.NAVY), -40, "navy after the configured delta");
        h.assertValueEqual(Reputation.get(p, Faction.PIRATES), 0, "pirates with a zero delta");
        h.assertFalse(applied.containsKey(Faction.PIRATES), "a zero delta is not applied");
        // Clamped at -100
        for (int i = 0; i < 5; i++) Deeds.record(p, Deed.KILL_NAVY, DeedContext.NONE);
        h.assertValueEqual(Reputation.get(p, Faction.NAVY), -100, "navy is clamped");
        h.succeed();
    }

    /** Scores decay toward 0 by {@code decay_per_day} per in-game day, also while nobody reads them. */
    @ModGameTest(batch = BATCH + "decay")
    public static void scoresDecayTowardZero(GameTestHelper h) {
        ServerPlayer p = serverPlayer(h);
        long now = Reputation.now(p);
        long twoDays = 2 * ReputationRules.TICKS_PER_DAY;
        Services.ATTACHMENTS.set(p, ReputationAttachments.REPUTATION, new ReputationRecord(30, -30, 1, now - twoDays, -1L, 0));
        double perDay = ReputationConfig.DECAY_PER_DAY.get();
        h.assertTrue(perDay > 0, "decay is configured off");
        h.assertValueEqual(Reputation.get(p, Faction.NAVY), ReputationRules.display(30 - 2 * perDay), "navy after two days");
        h.assertValueEqual(Reputation.get(p, Faction.PIRATES), ReputationRules.display(-30 + 2 * perDay), "pirates after two days");
        h.assertValueEqual(Reputation.get(p, Faction.VILLAGERS), 0, "a small score decays to 0, not past it");
        // An adjustment stores the decayed record
        Reputation.adjust(p, Faction.NAVY, 0, "test");
        h.assertValueEqual(Services.ATTACHMENTS.get(p, ReputationAttachments.REPUTATION).lastUpdate(), now, "stored at now");
        h.succeed();
    }

    // ------------------------------------------------------------------ markets

    /**
     * Village prices swing with the villagers' score: at +50 buying costs less and selling pays more, at -50 the
     * reverse; the market view shows exactly what the trade charges. A village trade is the trade_village deed.
     */
    @ModGameTest(batch = BATCH + "village_prices")
    public static void villagePricesSwingWithVillagerReputation(GameTestHelper h) {
        ResourceLocation port = port(h, PortKind.SEAFARER_VILLAGE);
        ServerPlayer p = serverPlayer(h);
        double swing = ReputationConfig.PRICE_SWING.get();
        int q = 64;

        Reputation.set(p, Faction.VILLAGERS, 50, "test");
        long baseBuy = quote(h, port, Market.Side.BUY, q);
        long baseSell = quote(h, port, Market.Side.SELL, q);
        MarketView.GoodLine liked = sugarLine(p, port, q);
        h.assertValueEqual(liked.buy().total(), ReputationRules.buyPrice(baseBuy, 50, swing), "liked buy price");
        h.assertValueEqual(liked.sell().total(), ReputationRules.sellPrice(baseSell, 50, swing), "liked sell price");
        h.assertTrue(liked.buy().total() < baseBuy && liked.sell().total() > baseSell,
                "liked prices don't favour the player: " + liked + " vs " + baseBuy + "/" + baseSell);

        Wallet.give(p, liked.buy().total());
        TransactionResult bought = trade(p, port, true, q, false);
        h.assertValueEqual(bought.status(), TransactionResult.Status.OK, "liked buy");
        h.assertValueEqual(bought.coins(), liked.buy().total(), "paid the shown price");
        h.assertValueEqual(Wallet.count(p), 0L, "coins left after the buy");
        h.assertValueEqual(Reputation.get(p, Faction.VILLAGERS), 50 + delta(Deed.TRADE_VILLAGE, Faction.VILLAGERS), "trade_village deed");

        Reputation.set(p, Faction.VILLAGERS, -50, "test");
        long nowBuy = quote(h, port, Market.Side.BUY, q);
        long nowSell = quote(h, port, Market.Side.SELL, q);
        MarketView.GoodLine disliked = sugarLine(p, port, q);
        h.assertValueEqual(disliked.buy().total(), ReputationRules.buyPrice(nowBuy, -50, swing), "disliked buy price");
        h.assertValueEqual(disliked.sell().total(), ReputationRules.sellPrice(nowSell, -50, swing), "disliked sell price");
        h.assertTrue(disliked.buy().total() > nowBuy && disliked.sell().total() < nowSell,
                "disliked prices don't hurt the player: " + disliked + " vs " + nowBuy + "/" + nowSell);
        TransactionResult sold = trade(p, port, false, q, false);
        h.assertValueEqual(sold.status(), TransactionResult.Status.OK, "disliked sell");
        h.assertValueEqual(sold.coins(), disliked.sell().total(), "received the shown price");
        h.assertValueEqual(Wallet.count(p), disliked.sell().total(), "coins after the sale");
        h.succeed();
    }

    /** A fence pays more for plunder to a player the pirates like, and fencing is the fence_plunder deed. */
    @ModGameTest(batch = BATCH + "fence")
    public static void fencePaysWithPirateReputation(GameTestHelper h) {
        ResourceLocation port = port(h, PortKind.PIRATE_ISLAND);
        ServerPlayer p = serverPlayer(h);
        Reputation.set(p, Faction.PIRATES, 50, "test");
        int q = 32;
        long normal = quote(h, port, Market.Side.SELL, q);
        PlunderRules.Verdict fenced = PlunderRules.judge(PortKind.PIRATE_ISLAND, true, q, normal, 0.0, TradeConfig.plunderParams());
        h.assertValueEqual(fenced.outcome(), PlunderRules.Outcome.FENCED, "fence verdict");
        long expected = ReputationRules.sellPrice(fenced.payout(), 50, ReputationConfig.PRICE_SWING.get());
        p.getInventory().add(PlunderMark.mark(new ItemStack(Items.SUGAR, q)));
        TransactionResult r = trade(p, port, false, q, true);
        h.assertValueEqual(r.plunder(), PlunderRules.Outcome.FENCED, "sold to the fence");
        h.assertValueEqual(r.coins(), expected, "fence payout with the swing");
        h.assertTrue(expected > fenced.payout(), "the liked seller got no more than " + fenced.payout());
        h.assertValueEqual(Reputation.get(p, Faction.PIRATES), 50 + delta(Deed.FENCE_PLUNDER, Faction.PIRATES), "fence_plunder deed");
        h.succeed();
    }

    /** Village markets refuse a player below {@code villager_trade_threshold}; nothing moves. */
    @ModGameTest(batch = BATCH + "refusal")
    public static void villagersRefuseBelowTheThreshold(GameTestHelper h) {
        ResourceLocation port = port(h, PortKind.SEAFARER_VILLAGE);
        ServerPlayer p = serverPlayer(h);
        int threshold = ReputationConfig.VILLAGER_TRADE_THRESHOLD.get();
        Wallet.give(p, 1_000);
        Reputation.set(p, Faction.VILLAGERS, threshold - 1, "test");
        long before = quote(h, port, Market.Side.BUY, 8);
        TransactionResult refused = trade(p, port, true, 8, false);
        h.assertValueEqual(refused.status(), TransactionResult.Status.REPUTATION_REFUSED, "below the threshold");
        h.assertValueEqual(Wallet.count(p), 1_000L, "coins untouched");
        h.assertValueEqual(quote(h, port, Market.Side.BUY, 8), before, "market untouched");
        h.assertValueEqual(Reputation.get(p, Faction.VILLAGERS), threshold - 1, "a refused trade is no deed");
        // A navy outpost does not care about villagers
        ResourceLocation navy = port(h, PortKind.NAVY_OUTPOST);
        h.assertValueEqual(trade(p, navy, true, 8, false).status(), TransactionResult.Status.OK, "navy outpost trades");
        Reputation.set(p, Faction.VILLAGERS, threshold, "test");
        h.assertValueEqual(trade(p, port, true, 8, false).status(), TransactionResult.Status.OK, "at the threshold");
        h.succeed();
    }

    /** Village trades count as deeds at most {@code village_trade_daily_cap} times a day. */
    @ModGameTest(batch = CONFIG_BATCH + "trade_cap")
    public static void villageTradeDeedsAreCappedPerDay(GameTestHelper h) {
        ConfigOverrides.during(h, ReputationConfig.VILLAGE_TRADE_DAILY_CAP, 2);
        ResourceLocation port = port(h, PortKind.SEAFARER_VILLAGE);
        ServerPlayer p = serverPlayer(h);
        Wallet.give(p, 1_000);
        for (int i = 0; i < 4; i++) {
            h.assertValueEqual(trade(p, port, true, 1, false).status(), TransactionResult.Status.OK, "buy " + i);
        }
        h.assertValueEqual(Reputation.get(p, Faction.VILLAGERS), 2 * delta(Deed.TRADE_VILLAGE, Faction.VILLAGERS), "capped deeds");
        h.succeed();
    }

    // ------------------------------------------------------------------ hostility

    /** A pirate leaves a player it likes alone, until that player hits it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200, batch = BATCH + "pirate_liked")
    public static void pirateIgnoresALikedPlayerUntilAttacked(GameTestHelper h) {
        floor(h, 9);
        Pirate pirate = spawn(h, MobContent.PIRATE.get(), 2, 4);
        Player player = playerInLevel(h, new Vec3(5.5, 1, 4.5));
        Reputation.set(player, Faction.PIRATES, ReputationConfig.PIRATE_FRIENDLY_THRESHOLD.get() + 10, "test");
        h.assertFalse(pirate.attacksOnSight(player), "the pirate would attack a liked player");
        h.runAfterDelay(60, () -> {
            h.assertTrue(pirate.getTarget() == null, "the pirate targets a liked player");
            h.assertTrue(player.getHealth() >= player.getMaxHealth(), "the liked player was hurt");
            pirate.hurt(player.damageSources().playerAttack(player), 1f);
            h.succeedWhen(() -> h.assertTrue(pirate.getTarget() == player, "the pirate does not fight back"));
        });
    }

    /** A pirate goes for a player it dislikes. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 100, batch = BATCH + "pirate_disliked")
    public static void pirateTargetsADislikedPlayer(GameTestHelper h) {
        floor(h, 9);
        Pirate pirate = spawn(h, MobContent.PIRATE.get(), 2, 4);
        Player player = playerInLevel(h, new Vec3(5.5, 1, 4.5));
        Reputation.set(player, Faction.PIRATES, -50, "test");
        h.succeedWhen(() -> h.assertTrue(pirate.getTarget() == player, "the pirate does not target the disliked player"));
    }

    /** A navy soldier attacks a player the navy hates, without any bounty or criminal score. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH + "navy_hated")
    public static void navySoldierAttacksAHatedPlayerWithoutABounty(GameTestHelper h) {
        floor(h, 24);
        NavySoldier soldier = spawn(h, MobContent.NAVY_SOLDIER.get(), 4, 12);
        Player player = playerInLevel(h, new Vec3(12.5, 1, 12.5));
        Reputation.set(player, Faction.NAVY, ReputationConfig.NAVY_HOSTILE_THRESHOLD.get() - 10, "test");
        h.assertValueEqual(LawService.wantedLevel(player), WantedLevel.CLEAN, "wanted level");
        h.assertFalse(LawService.hasBounty(h.getLevel().getServer(), player.getUUID()), "the player has a bounty");
        h.succeedWhen(() -> h.assertTrue(soldier.getTarget() == player, "the soldier does not target the hated player"));
    }

    // ------------------------------------------------------------------ false flag and port fees

    /** The false-flag rule and the docking fee take the navy standing from the navy reputation. */
    @ModGameTest(batch = BATCH + "false_flag")
    public static void falseFlagAndPortFeesUseTheNavyScore(GameTestHelper h) {
        ServerPlayer p = serverPlayer(h);
        h.assertValueEqual(LawService.wantedLevel(p), WantedLevel.CLEAN, "clean captain");
        int min = com.richardsenger.piratesnships.law.LawConfig.NAVY_FLAG_MIN_STANDING.get();
        Reputation.set(p, Faction.NAVY, min, "test");
        h.assertFalse(LawService.isFalseFlag(p, FlagKind.NAVY), "a navy flag at the minimum standing counts as a false flag");
        h.assertFalse(LawService.fliesFalseColours(p, FlagKind.NAVY), "a clean captain at the minimum standing flies false colours");
        Reputation.set(p, Faction.NAVY, min - 1, "test");
        h.assertTrue(LawService.isFalseFlag(p, FlagKind.NAVY), "a navy flag below the minimum standing is not a false flag");
        h.assertTrue(LawService.fliesFalseColours(p, FlagKind.NAVY), "a captain below the minimum standing does not fly false colours");
        h.assertFalse(LawService.fliesFalseColours(p, FlagKind.MERCHANT), "a merchant flag counted as false colours");

        int waiver = TradeConfig.feeParams().waiverStanding();
        Reputation.set(p, Faction.NAVY, waiver, "test");
        h.assertValueEqual(TradeService.dockingFee(PortKind.NAVY_OUTPOST, p), 0, "fee waived for a trusted captain");
        Reputation.set(p, Faction.NAVY, waiver - 1, "test");
        h.assertValueEqual(TradeService.dockingFee(PortKind.NAVY_OUTPOST, p), TradeConfig.feeParams().navyDockingFee(), "fee charged");
        h.succeed();
    }

    // ------------------------------------------------------------------ commands

    /** A command source that keeps what it is told. */
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

    /** {@code /pirates rep} shows, {@code /pirates rep set} sets (operators only). */
    @ModGameTest(batch = BATCH + "commands")
    public static void commandsShowAndSetReputation(GameTestHelper h) {
        ServerPlayer p = serverPlayer(h);
        run(h, p, 2, "pirates rep set @s navy 30");
        h.assertValueEqual(Reputation.get(p, Faction.NAVY), 30, "navy after rep set");
        run(h, p, 2, "pirates rep set @s villagers -100");
        h.assertValueEqual(Reputation.get(p, Faction.VILLAGERS), -100, "villagers after rep set");
        run(h, p, 0, "pirates rep set @s navy 90");
        h.assertValueEqual(Reputation.get(p, Faction.NAVY), 30, "a non-operator set the reputation");
        Recorder shown = run(h, p, 0, "pirates rep");
        h.assertTrue(shown.lines.size() == 1 && shown.lines.getFirst().contains("30") && shown.lines.getFirst().contains("-100"),
                "rep shows " + shown.lines);
        Recorder other = run(h, p, 2, "pirates rep @s");
        h.assertTrue(other.lines.size() == 1 && other.lines.getFirst().contains("30"), "rep <player> shows " + other.lines);
        h.succeed();
    }

    // ------------------------------------------------------------------ toggle

    /** {@code reputation.enabled = false}: deeds are no-ops, prices don't swing, nobody refuses, hostility ignores it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100, batch = CONFIG_BATCH + "disabled")
    public static void disabledReputationChangesNothing(GameTestHelper h) {
        ConfigOverrides.during(h, ReputationConfig.ENABLED, false);
        floor(h, 24);
        ServerPlayer p = serverPlayer(h);
        h.assertTrue(Deeds.record(p, Deed.KILL_VILLAGER, DeedContext.NONE).isEmpty(), "a deed applied deltas");
        for (Faction f : Faction.values()) h.assertValueEqual(Reputation.get(p, f), 0, f.id() + " after a disabled deed");

        // Markets: no swing, no refusal, no deed
        ResourceLocation village = port(h, PortKind.SEAFARER_VILLAGE);
        Reputation.set(p, Faction.VILLAGERS, -100, "test");
        long base = quote(h, village, Market.Side.BUY, 16);
        h.assertValueEqual(sugarLine(p, village, 16).buy().total(), base, "price with reputation off");
        Wallet.give(p, base);
        TransactionResult r = trade(p, village, true, 16, false);
        h.assertValueEqual(r.status(), TransactionResult.Status.OK, "a hated player trades with reputation off");
        h.assertValueEqual(r.coins(), base, "paid the plain price");
        h.assertValueEqual(Reputation.get(p, Faction.VILLAGERS), -100, "a village trade is no deed");

        // Flags and fees: navy standing 0
        Reputation.set(p, Faction.NAVY, -100, "test");
        h.assertValueEqual(LawService.navyStanding(p), 0, "navy standing with reputation off");
        h.assertFalse(LawService.fliesFalseColours(p, FlagKind.NAVY), "a clean captain flies false colours");

        // Hostility: a liked player is attacked by pirates, a hated one left alone by the navy
        Pirate pirate = spawn(h, MobContent.PIRATE.get(), 2, 4);
        NavySoldier soldier = spawn(h, MobContent.NAVY_SOLDIER.get(), 2, 20);
        Player player = playerInLevel(h, new Vec3(12.5, 1, 12.5));
        Reputation.set(player, Faction.PIRATES, 100, "test");
        Reputation.set(player, Faction.NAVY, -100, "test");
        h.assertTrue(pirate.attacksOnSight(player), "the pirate ignores reputation being off");
        h.assertFalse(soldier.attacksOnSight(player), "the soldier attacks with reputation off");
        h.succeed();
    }
}

package com.richardsenger.piratesnships.law.world;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.LawAttachments;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.crime.CriminalRecord;
import com.richardsenger.piratesnships.law.crime.CriminalRecord.CrimeOutcome;
import com.richardsenger.piratesnships.platform.Services;
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
import com.richardsenger.piratesnships.trade.plunder.PlunderMark;
import com.richardsenger.piratesnships.trade.plunder.PlunderRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Plunder at the harbor master's desk (LAW3, docs/design.md §13.4; G13 before it): a village or navy outpost desk
 * refuses plunder-marked goods ({@link TransactionResult.Status#PLUNDER_REFUSED}) and, with {@code law.plunder_notice},
 * reports the seller once per port and day as {@code selling_plunder}; the fence buys silently. Sales go through the
 * market protocol ({@link MarketBackend#handleTrade}, as the desk sends it) or the direct sell command. Each test has a
 * batch of its own.
 */
public final class PlunderCrimeGameTests {

    static final String BATCH = "pirates_n_ships_law_plunder_";
    static final String CONFIG_BATCH = "pirates_n_ships_config_law_plunder_";

    private PlunderCrimeGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(PlunderCrimeGameTests.class);
    }

    private static ResourceLocation port(GameTestHelper helper, PortKind kind) {
        ResourceLocation id = Constants.id("gametest/law_plunder_" + UUID.randomUUID());
        TradeService.openMarket(helper.getLevel().getServer(), id,
                () -> new PortProfile(kind, Climate.TROPICAL, 1L, Map.of(TradeGoods.SUGAR, GoodRole.NEUTRAL)));
        return id;
    }

    /** A real server player with a mock connection (not added to the level), as in the trade tests. */
    private static ServerPlayer seller(GameTestHelper helper) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "plunder_test");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        p.getInventory().clearContent();
        return p;
    }

    /** Offers {@code quantity} plundered sugar through the market protocol and returns the transaction result. */
    private static TransactionResult sellPlunder(ServerPlayer p, ResourceLocation port, int quantity, List<CustomPacketPayload> sent) {
        p.getInventory().add(PlunderMark.mark(new ItemStack(Items.SUGAR, quantity)));
        MarketBackend.handleTrade(p, new MarketPayloads.Trade(port, false, TradeGoods.SUGAR, quantity, true, Optional.empty()));
        synchronized (sent) {
            for (int i = sent.size() - 1; i >= 0; i--) {
                if (sent.get(i) instanceof MarketPayloads.State s && s.result().isPresent()) return s.result().get();
            }
        }
        throw new AssertionError("no trade result was sent: " + sent);
    }

    private static int markedSugar(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(Items.SUGAR) && PlunderMark.isPlundered(s)) n += s.getCount();
        return n;
    }

    private static int crimes(ServerPlayer p) {
        return Services.ATTACHMENTS.get(p, LawAttachments.CRIMINAL_RECORD).totalCrimes();
    }

    private static void finish(GameTestHelper helper, ServerPlayer p) {
        MarketBackend.close(p);
        MarketBackend.stopRecording(p.getUUID());
        helper.succeed();
    }

    /**
     * A desk of {@code kind} refuses the plunder (goods kept, no coins) and reports the seller once: a second refusal at
     * the same port the same day records nothing more, another port is another victim.
     */
    private static void refusesAndReportsOncePerDay(GameTestHelper helper, PortKind kind) {
        ResourceLocation port = port(helper, kind);
        ServerPlayer p = seller(helper);
        List<CustomPacketPayload> sent = MarketBackend.record(p.getUUID());
        helper.assertTrue(MarketBackend.open(p, port, 1), "the market did not open");
        helper.assertValueEqual(LawService.displayScore(p), 0, "score before the offer");

        TransactionResult r = sellPlunder(p, port, 32, sent);
        helper.assertValueEqual(r.status(), TransactionResult.Status.PLUNDER_REFUSED, "status");
        helper.assertFalse(r.done(), "a refused offer moved goods: " + r);
        helper.assertValueEqual(markedSugar(p), 32, "the plunder stays with the seller");
        helper.assertValueEqual(Wallet.count(p), 0L, "no coins for refused plunder");
        int severity = LawConfig.SEVERITIES.get(CrimeType.SELLING_PLUNDER).get();
        helper.assertValueEqual(severity, CrimeType.SELLING_PLUNDER.defaultSeverity(), "configured severity");
        helper.assertValueEqual(LawService.displayScore(p), severity, "score after the refusal");
        helper.assertValueEqual(crimes(p), 1, "crimes on record");
        CrimeLog.Entry last = CrimeLog.last(p.getUUID()).orElseThrow(() -> new AssertionError("nothing in the crime log"));
        helper.assertValueEqual(last.type(), CrimeType.SELLING_PLUNDER, "crime type");
        helper.assertValueEqual(last.outcome(), CrimeOutcome.COUNTED, "crime outcome");
        helper.assertValueEqual(last.victim(), port.toString(), "the port is the victim");

        // A second refusal at the same port the same day records nothing more
        TransactionResult again = sellPlunder(p, port, 16, sent);
        helper.assertValueEqual(again.status(), TransactionResult.Status.PLUNDER_REFUSED, "second status");
        helper.assertValueEqual(markedSugar(p), 48, "the plunder still stays with the seller");
        helper.assertValueEqual(CrimeLog.last(p.getUUID()).orElseThrow().outcome(), CrimeOutcome.REPEAT_IGNORED, "repeat outcome");
        helper.assertValueEqual(LawService.displayScore(p), severity, "score after the repeat");
        helper.assertValueEqual(crimes(p), 1, "crimes after the repeat");

        // Another port of the kind is another victim
        ResourceLocation other = port(helper, kind);
        MarketBackend.open(p, other, 1);
        sellPlunder(p, other, 16, sent);
        helper.assertValueEqual(LawService.displayScore(p), 2 * severity, "score after a refusal at a second port");
        finish(helper, p);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "village")
    public static void villageDeskRefusesPlunderAndReportsOncePerDay(GameTestHelper helper) {
        refusesAndReportsOncePerDay(helper, PortKind.SEAFARER_VILLAGE);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "outpost")
    public static void outpostDeskRefusesPlunderAndReportsOncePerDay(GameTestHelper helper) {
        refusesAndReportsOncePerDay(helper, PortKind.NAVY_OUTPOST);
    }

    /** The report raises the score like any crime: a seller just below the bounty threshold gets a navy bounty. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "bounty")
    public static void reportedPlunderLeadsToABountyThroughTheScore(GameTestHelper helper) {
        ResourceLocation port = port(helper, PortKind.NAVY_OUTPOST);
        ServerPlayer p = seller(helper);
        List<CustomPacketPayload> sent = MarketBackend.record(p.getUUID());
        helper.assertTrue(MarketBackend.open(p, port, 1), "the market did not open");
        int threshold = LawConfig.BOUNTY_THRESHOLD.get();
        LawService.setScore(p, threshold - 1);
        helper.assertTrue(LawService.board(helper.getLevel().getServer()).navyBounty(p.getUUID()).isEmpty(), "a bounty before the report");
        sellPlunder(p, port, 8, sent);
        helper.assertTrue(LawService.board(helper.getLevel().getServer()).navyBounty(p.getUUID()).isPresent(),
                "no navy bounty after the report, score " + LawService.score(p));
        finish(helper, p);
    }

    /** The fence stays the legal outlet: it buys plunder at its discount, notices nothing and reports nobody. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "fence")
    public static void fenceBuysPlunderAndReportsNobody(GameTestHelper helper) {
        ResourceLocation port = port(helper, PortKind.PIRATE_ISLAND);
        ServerPlayer p = seller(helper);
        List<CustomPacketPayload> sent = MarketBackend.record(p.getUUID());
        helper.assertTrue(MarketBackend.open(p, port, 1), "the market did not open");
        TransactionResult r = sellPlunder(p, port, 32, sent);
        helper.assertValueEqual(r.status(), TransactionResult.Status.OK, "status");
        helper.assertValueEqual(r.plunder(), PlunderRules.Outcome.FENCED, "fenced");
        helper.assertFalse(r.noticedPlunder(), "the fence noticed");
        helper.assertValueEqual(markedSugar(p), 0, "the plunder was sold");
        helper.assertTrue(Wallet.count(p) > 0, "the fence paid nothing");
        helper.assertValueEqual(crimes(p), 0, "crimes after fencing");
        helper.assertValueEqual(LawService.displayScore(p), 0, "score after fencing");
        finish(helper, p);
    }

    /** {@code law.plunder_notice = false}: the desk still refuses, but nobody is reported. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = CONFIG_BATCH + "notice_off")
    public static void plunderNoticeOffRefusesButRecordsNothing(GameTestHelper helper) {
        ConfigOverrides.during(helper, LawConfig.PLUNDER_NOTICE, false);
        ResourceLocation port = port(helper, PortKind.SEAFARER_VILLAGE);
        ServerPlayer p = seller(helper);
        List<CustomPacketPayload> sent = MarketBackend.record(p.getUUID());
        helper.assertTrue(MarketBackend.open(p, port, 1), "the market did not open");
        TransactionResult r = sellPlunder(p, port, 32, sent);
        helper.assertValueEqual(r.status(), TransactionResult.Status.PLUNDER_REFUSED, "status");
        helper.assertValueEqual(markedSugar(p), 32, "the plunder stays with the seller");
        helper.assertValueEqual(crimes(p), 0, "crimes on record");
        helper.assertValueEqual(LawService.displayScore(p), 0, "score");
        helper.assertTrue(CrimeLog.last(p.getUUID()).isEmpty(), "something was logged: " + CrimeLog.last(p.getUUID()));
        finish(helper, p);
    }

    /** Runs {@code /pirates trade sell <name> sugar <quantity> [plundered]} as {@code p} with operator permission. */
    private static void sellByCommand(GameTestHelper helper, ServerPlayer p, String portName, int quantity, boolean plundered) {
        var source = p.createCommandSourceStack().withSource(net.minecraft.commands.CommandSource.NULL).withPermission(2);
        String command = "pirates trade sell " + portName + " " + TradeGoods.SUGAR + " " + quantity + (plundered ? " plundered" : "");
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "command")
    public static void directSellCommandRefusesAndReportsPlunder(GameTestHelper helper) {
        String name = "law_plunder_cmd_" + UUID.randomUUID().toString().substring(0, 8);
        ResourceLocation port = com.richardsenger.piratesnships.trade.TradeCommands.portId(name);
        TradeService.openMarket(helper.getLevel().getServer(), port,
                () -> new PortProfile(PortKind.NAVY_OUTPOST, Climate.TROPICAL, 1L, Map.of(TradeGoods.SUGAR, GoodRole.NEUTRAL)));
        ServerPlayer p = seller(helper);

        // Clean goods: sold, nothing to notice, no crime
        p.getInventory().add(new ItemStack(Items.SUGAR, 16));
        sellByCommand(helper, p, name, 16, false);
        helper.assertValueEqual(p.getInventory().countItem(Items.SUGAR), 0, "clean sugar left after the sale");
        helper.assertValueEqual(crimes(p), 0, "crimes after a clean sale");

        // Plundered goods: refused, and the seller commits selling_plunder against the port
        p.getInventory().add(PlunderMark.mark(new ItemStack(Items.SUGAR, 32)));
        sellByCommand(helper, p, name, 32, true);
        helper.assertValueEqual(markedSugar(p), 32, "the plunder stays with the seller");
        helper.assertValueEqual(crimes(p), 1, "crimes after offering plunder");
        helper.assertValueEqual(LawService.displayScore(p), LawConfig.SEVERITIES.get(CrimeType.SELLING_PLUNDER).get(), "score");
        CrimeLog.Entry last = CrimeLog.last(p.getUUID()).orElseThrow(() -> new AssertionError("nothing in the crime log"));
        helper.assertValueEqual(last.type(), CrimeType.SELLING_PLUNDER, "crime type");
        helper.assertValueEqual(last.victim(), port.toString(), "the port is the victim");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = CONFIG_BATCH + "score_disabled")
    public static void disabledCriminalScoreRecordsNoPlunderCrime(GameTestHelper helper) {
        ConfigOverrides.during(helper, LawConfig.CRIMINAL_SCORE_ENABLED, false);
        ResourceLocation port = port(helper, PortKind.NAVY_OUTPOST);
        ServerPlayer p = seller(helper);
        List<CustomPacketPayload> sent = MarketBackend.record(p.getUUID());
        helper.assertTrue(MarketBackend.open(p, port, 1), "the market did not open");

        TransactionResult r = sellPlunder(p, port, 32, sent);
        helper.assertValueEqual(r.status(), TransactionResult.Status.PLUNDER_REFUSED, "status");
        CriminalRecord stored = Services.ATTACHMENTS.get(p, LawAttachments.CRIMINAL_RECORD);
        helper.assertValueEqual(stored.totalCrimes(), 0, "crimes on record");
        helper.assertTrue(stored.score() == 0.0, "score on record: " + stored.score());
        helper.assertValueEqual(LawService.displayScore(p), 0, "score shown");
        helper.assertValueEqual(CrimeLog.last(p.getUUID()).orElseThrow().outcome(), CrimeOutcome.DISABLED, "logged as disabled");
        finish(helper, p);
    }
}

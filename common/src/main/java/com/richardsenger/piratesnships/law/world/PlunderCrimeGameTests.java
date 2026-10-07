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
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.exchange.TransactionResult;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.PortProfile;
import com.richardsenger.piratesnships.trade.net.MarketBackend;
import com.richardsenger.piratesnships.trade.net.MarketPayloads;
import com.richardsenger.piratesnships.trade.plunder.PlunderMark;
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
 * Noticed plunder is a crime (G13, docs/design.md §10.3, §13.1): a plundered sale that a navy port notices through the
 * market protocol ({@link MarketBackend#handleTrade}, as the harbor master's desk and the market command send it,
 * or the direct sell command)
 * raises the seller's criminal score by {@code law.severity.fence_plunder}. The notice chance is forced to 1.0, so
 * each test changes config and runs in a batch of its own.
 */
public final class PlunderCrimeGameTests {

    static final String NOTICED_BATCH = "pirates_n_ships_config_law_fence_plunder";
    static final String DISABLED_BATCH = "pirates_n_ships_config_law_fence_plunder_disabled";
    static final String COMMAND_BATCH = "pirates_n_ships_config_law_fence_plunder_command";

    private PlunderCrimeGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(PlunderCrimeGameTests.class);
    }

    private static ResourceLocation navyPort(GameTestHelper helper) {
        ResourceLocation id = Constants.id("gametest/law_plunder_" + UUID.randomUUID());
        TradeService.openMarket(helper.getLevel().getServer(), id,
                () -> new PortProfile(PortKind.NAVY_OUTPOST, Climate.TROPICAL, 1L, Map.of(TradeGoods.SUGAR, GoodRole.NEUTRAL)));
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

    /** Sells {@code quantity} plundered sugar through the market protocol and returns the transaction result. */
    private static TransactionResult sellPlunder(GameTestHelper helper, ServerPlayer p, ResourceLocation port, int quantity,
                                                 List<CustomPacketPayload> sent) {
        p.getInventory().add(PlunderMark.mark(new ItemStack(Items.SUGAR, quantity)));
        MarketBackend.handleTrade(p, new MarketPayloads.Trade(port, false, TradeGoods.SUGAR, quantity, true, Optional.empty()));
        synchronized (sent) {
            for (int i = sent.size() - 1; i >= 0; i--) {
                if (sent.get(i) instanceof MarketPayloads.State s && s.result().isPresent()) return s.result().get();
            }
        }
        throw new AssertionError("no trade result was sent: " + sent);
    }

    private static void finish(GameTestHelper helper, ServerPlayer p) {
        MarketBackend.close(p);
        MarketBackend.stopRecording(p.getUUID());
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = NOTICED_BATCH)
    public static void noticedPlunderSaleRaisesTheCriminalScore(GameTestHelper helper) {
        ConfigOverrides.during(helper, TradeConfig.NAVY_NOTICE_CHANCE, 1.0);
        ResourceLocation port = navyPort(helper);
        ServerPlayer p = seller(helper);
        List<CustomPacketPayload> sent = MarketBackend.record(p.getUUID());
        helper.assertTrue(MarketBackend.open(p, port, 1), "the market did not open");
        helper.assertValueEqual(LawService.displayScore(p), 0, "score before the sale");

        TransactionResult r = sellPlunder(helper, p, port, 32, sent);
        helper.assertTrue(r.noticedPlunder(), "the port did not notice: " + r);
        int severity = LawConfig.SEVERITIES.get(CrimeType.FENCE_PLUNDER).get();
        helper.assertValueEqual(severity, CrimeType.FENCE_PLUNDER.defaultSeverity(), "configured severity");
        helper.assertValueEqual(LawService.displayScore(p), severity, "score after the noticed sale");
        CriminalRecord stored = Services.ATTACHMENTS.get(p, LawAttachments.CRIMINAL_RECORD);
        helper.assertValueEqual(stored.totalCrimes(), 1, "crimes on record");
        CrimeLog.Entry last = CrimeLog.last(p.getUUID()).orElseThrow(() -> new AssertionError("nothing in the crime log"));
        helper.assertValueEqual(last.type(), CrimeType.FENCE_PLUNDER, "crime type");
        helper.assertValueEqual(last.outcome(), CrimeOutcome.COUNTED, "crime outcome");
        helper.assertValueEqual(last.victim(), port.toString(), "the port is the victim");

        // A second noticed sale at the same port within the repeat window is not counted again
        TransactionResult again = sellPlunder(helper, p, port, 16, sent);
        helper.assertTrue(again.noticedPlunder(), "the second sale was not noticed: " + again);
        helper.assertValueEqual(CrimeLog.last(p.getUUID()).orElseThrow().outcome(), CrimeOutcome.REPEAT_IGNORED, "repeat outcome");
        helper.assertValueEqual(LawService.displayScore(p), severity, "score after the repeat");

        // Another navy port is another victim
        ResourceLocation other = navyPort(helper);
        MarketBackend.open(p, other, 1);
        sellPlunder(helper, p, other, 16, sent);
        helper.assertValueEqual(LawService.displayScore(p), 2 * severity, "score after a sale at a second port");
        finish(helper, p);
    }

    /** Runs {@code /pirates trade sell <name> sugar <quantity> [plundered]} as {@code p} with operator permission. */
    private static void sellByCommand(GameTestHelper helper, ServerPlayer p, String portName, int quantity, boolean plundered) {
        var source = p.createCommandSourceStack().withSource(net.minecraft.commands.CommandSource.NULL).withPermission(2);
        String command = "pirates trade sell " + portName + " " + TradeGoods.SUGAR + " " + quantity + (plundered ? " plundered" : "");
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = COMMAND_BATCH)
    public static void directSellCommandReportsNoticedPlunder(GameTestHelper helper) {
        ConfigOverrides.during(helper, TradeConfig.NAVY_NOTICE_CHANCE, 1.0);
        String name = "law_plunder_cmd_" + UUID.randomUUID().toString().substring(0, 8);
        ResourceLocation port = com.richardsenger.piratesnships.trade.TradeCommands.portId(name);
        TradeService.openMarket(helper.getLevel().getServer(), port,
                () -> new PortProfile(PortKind.NAVY_OUTPOST, Climate.TROPICAL, 1L, Map.of(TradeGoods.SUGAR, GoodRole.NEUTRAL)));
        ServerPlayer p = seller(helper);

        // Clean goods: sold, nothing to notice, no crime
        p.getInventory().add(new ItemStack(Items.SUGAR, 16));
        sellByCommand(helper, p, name, 16, false);
        helper.assertValueEqual(p.getInventory().countItem(Items.SUGAR), 0, "clean sugar left after the sale");
        helper.assertValueEqual(Services.ATTACHMENTS.get(p, LawAttachments.CRIMINAL_RECORD).totalCrimes(), 0, "crimes after a clean sale");
        helper.assertValueEqual(LawService.displayScore(p), 0, "score after a clean sale");

        // Plundered goods: the port notices, the seller commits fence_plunder against it
        p.getInventory().add(PlunderMark.mark(new ItemStack(Items.SUGAR, 32)));
        sellByCommand(helper, p, name, 32, true);
        int severity = LawConfig.SEVERITIES.get(CrimeType.FENCE_PLUNDER).get();
        helper.assertValueEqual(Services.ATTACHMENTS.get(p, LawAttachments.CRIMINAL_RECORD).totalCrimes(), 1, "crimes after a plundered sale");
        helper.assertValueEqual(LawService.displayScore(p), severity, "score after the noticed sale");
        CrimeLog.Entry last = CrimeLog.last(p.getUUID()).orElseThrow(() -> new AssertionError("nothing in the crime log"));
        helper.assertValueEqual(last.type(), CrimeType.FENCE_PLUNDER, "crime type");
        helper.assertValueEqual(last.outcome(), CrimeOutcome.COUNTED, "crime outcome");
        helper.assertValueEqual(last.victim(), port.toString(), "the port is the victim");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = DISABLED_BATCH)
    public static void disabledCriminalScoreRecordsNoPlunderCrime(GameTestHelper helper) {
        ConfigOverrides.during(helper, TradeConfig.NAVY_NOTICE_CHANCE, 1.0);
        ConfigOverrides.during(helper, LawConfig.CRIMINAL_SCORE_ENABLED, false);
        ResourceLocation port = navyPort(helper);
        ServerPlayer p = seller(helper);
        List<CustomPacketPayload> sent = MarketBackend.record(p.getUUID());
        helper.assertTrue(MarketBackend.open(p, port, 1), "the market did not open");

        TransactionResult r = sellPlunder(helper, p, port, 32, sent);
        helper.assertTrue(r.noticedPlunder(), "the port did not notice: " + r);
        CriminalRecord stored = Services.ATTACHMENTS.get(p, LawAttachments.CRIMINAL_RECORD);
        helper.assertValueEqual(stored.totalCrimes(), 0, "crimes on record");
        helper.assertTrue(stored.score() == 0.0, "score on record: " + stored.score());
        helper.assertValueEqual(LawService.displayScore(p), 0, "score shown");
        helper.assertValueEqual(CrimeLog.last(p.getUUID()).orElseThrow().outcome(), CrimeOutcome.DISABLED, "logged as disabled");
        finish(helper, p);
    }
}

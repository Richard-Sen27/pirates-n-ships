package com.richardsenger.piratesnships.chart.held;

import com.richardsenger.piratesnships.chart.ChartConfig;
import com.richardsenger.piratesnships.chart.ChartContent;
import com.richardsenger.piratesnships.chart.net.ChartBackend;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.UUID;

/**
 * The held chart's open flag (CHART3) on the server: set on the player while their chart screen is open, cleared when
 * it closes (the client's close payload) and never set with {@code chart.held_open} off. The client side (frames and
 * arm pose) is covered by {@code ChartHeldModelTest} and the playtest.
 */
public final class ChartHeldGameTests {

    private ChartHeldGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(ChartHeldGameTests.class);
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        // the chart tests' mock player: not added to the level, payloads recorded instead of sent
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, p, cookie);
        p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ChartContent.CHART.get()));
        ChartBackend.record(p.getUUID());
        return p;
    }

    @ModGameTest
    public static void readingFlagFollowsTheOpenChart(GameTestHelper helper) {
        ServerPlayer p = player(helper, "chart_reader");
        try {
            helper.assertFalse(ChartHeld.isReading(p), "not reading before the chart opens");
            helper.assertTrue(ChartBackend.open(p), "the chart opens");
            helper.assertTrue(ChartHeld.isReading(p), "reading while the chart is open");
            ChartBackend.open(p);
            helper.assertTrue(ChartHeld.isReading(p), "opening again keeps it set");
            ChartBackend.close(p);
            helper.assertFalse(ChartHeld.isReading(p), "cleared when the screen closes");
            helper.assertFalse(ChartBackend.isOpen(p.getUUID()), "and the session ended");
        } finally {
            ChartBackend.stopRecording(p.getUUID());
        }
        helper.succeed();
    }

    /** Own batch: changes config. */
    @ModGameTest(batch = "pirates_n_ships_config_chart_held_open")
    public static void noReadingFlagWithTheOptionOff(GameTestHelper helper) {
        ConfigOverrides.during(helper, ChartConfig.HELD_OPEN, false);
        ServerPlayer p = player(helper, "chart_reader_off");
        try {
            helper.assertTrue(ChartBackend.open(p), "the chart still opens");
            helper.assertFalse(ChartHeld.isReading(p), "but the flag stays clear");
            ChartBackend.close(p);
            helper.assertFalse(ChartHeld.isReading(p), "and clear after closing");
        } finally {
            ChartBackend.stopRecording(p.getUUID());
        }
        helper.succeed();
    }
}

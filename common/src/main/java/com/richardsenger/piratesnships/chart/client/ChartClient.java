package com.richardsenger.piratesnships.chart.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.richardsenger.piratesnships.chart.ChartContent;
import com.richardsenger.piratesnships.chart.ChartText;
import com.richardsenger.piratesnships.chart.net.ChartSettings;
import com.richardsenger.piratesnships.chart.net.ChartSimplePayloads;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of the chart (physical client only, work package MAP1): the "Open Chart" key (default M), the screen
 * opener, texture invalidation, the cache reset on leaving a server, and a tooltip line on the chart item while the
 * server allows the key without the item.
 *
 * <p>The key: with {@code chart.open_without_item} (from the synced settings) or a chart in either hand it asks the
 * server to open the chart; otherwise it only shows a hint on the action bar and sends nothing.
 */
public final class ChartClient {

    public static final KeyMapping OPEN_KEY = new KeyMapping(ChartText.KEY_OPEN, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, ChartText.KEY_CATEGORY);

    private ChartClient() {
    }

    public static void init() {
        ClientEvents.registerKeyMapping(OPEN_KEY);
        ClientChart.setOpener(ChartScreen::open);
        ClientChart.setRegionListener(ChartTextures::onRegion);
        ClientEvents.CLIENT_DISCONNECT.register(mc -> ClientChart.reset());
        ClientEvents.CLIENT_TICK_END.register(ChartClient::tick);
        ClientEvents.ITEM_TOOLTIP.register((stack, context, flag, player, lines) -> {
            if (player != null && stack.is(ChartContent.CHART.get()) && ClientChart.settings().openWithoutItem()) {
                lines.add(Component.translatable(ChartText.KEY_TOOLTIP, OPEN_KEY.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY));
            }
        });
    }

    private static void tick(Minecraft mc) {
        while (OPEN_KEY.consumeClick()) {
            if (mc.player == null || mc.screen != null) continue;
            ChartSettings s = ClientChart.settings();
            boolean holding = mc.player.getMainHandItem().is(ChartContent.CHART.get()) || mc.player.getOffhandItem().is(ChartContent.CHART.get());
            if (!s.enabled()) {
                mc.player.displayClientMessage(Component.translatable(com.richardsenger.piratesnships.chart.net.ChartBackend.MSG + "disabled"), true);
            } else if (s.openWithoutItem() || holding) {
                Services.NETWORK.sendToServer(ChartSimplePayloads.RequestOpen.INSTANCE);
            } else {
                mc.player.displayClientMessage(Component.translatable(com.richardsenger.piratesnships.chart.net.ChartBackend.MSG + "needs_item"), true);
            }
        }
    }
}

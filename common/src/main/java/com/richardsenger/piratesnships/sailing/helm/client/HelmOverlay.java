package com.richardsenger.piratesnships.sailing.helm.client;

import com.richardsenger.piratesnships.sailing.helm.HelmConfig;
import com.richardsenger.piratesnships.sailing.helm.HelmService;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The helmsman's HUD while holding the wheel (HELM1): the rudder angle and side ("Rudder 12° starboard", "Rudder
 * midships") above the action bar, from the locally predicted wheel, behind {@code helm_view.show_rudder_angle}.
 */
public final class HelmOverlay {

    private HelmOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (HelmSteeringClient.activeHelm() == null || mc.options.hideGui || !HelmConfig.SHOW_RUDDER_ANGLE.get()) {
            return;
        }
        Component text = HelmService.rudderMessage(HelmService.rudderAngle(HelmSteeringClient.predictedWheel()));
        Font font = mc.font;
        int w = font.width(text);
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - 84;
        graphics.fill(x - w / 2 - 3, y - 2, x + w / 2 + 3, y + font.lineHeight, 0x80000000);
        graphics.drawCenteredString(font, text, x, y, 0xFFE8D8B0);
    }
}

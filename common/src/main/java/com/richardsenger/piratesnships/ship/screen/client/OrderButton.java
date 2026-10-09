package com.richardsenger.piratesnships.ship.screen.client;

import com.richardsenger.piratesnships.core.client.gui.BrassButton;
import com.richardsenger.piratesnships.core.client.gui.GuiKit;
import com.richardsenger.piratesnships.station.client.OrderIcons;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A brass button of the helm screen's Crew tab for one whistle order (ART19): the order's icon at 16x16 on the left,
 * its label centred in the rest. {@link #HEIGHT} fits the icon.
 */
final class OrderButton extends BrassButton {

    static final int HEIGHT = OrderIcons.SIZE + 2;
    private static final int ICON_X = 2;
    private static final int TEXT_X = ICON_X + OrderIcons.SIZE + 2;

    private final WhistleOrder.Icon icon;

    OrderButton(int x, int y, int w, WhistleOrder.Icon icon, Component label, Runnable onPress) {
        super(x, y, w, HEIGHT, label, onPress);
        this.icon = icon;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        GuiKit.ButtonState state = GuiKit.buttonState(active, isHovered());
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        GuiKit.button(g, font, x, y, w, h, Component.empty(), state);
        int dy = state == GuiKit.ButtonState.PRESSED ? 1 : 0;
        OrderIcons.draw(g, icon, x + ICON_X, y + (h - OrderIcons.SIZE) / 2 + dy);
        int textW = w - TEXT_X - 2;
        Component cut = GuiKit.clip(font, getMessage(), textW);
        int color = state == GuiKit.ButtonState.DISABLED ? GuiKit.BUTTON_DISABLED_TEXT : GuiKit.INK;
        g.drawString(font, cut, x + TEXT_X + (textW - font.width(cut)) / 2, y + (h - 8) / 2 + dy, color, false);
    }
}

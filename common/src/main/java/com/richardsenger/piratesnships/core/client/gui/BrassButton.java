package com.richardsenger.piratesnships.core.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A vanilla button widget drawn with the kit's brass sprites (normal, hover, pressed, disabled). {@link #selected}
 * keeps the pressed look (a tab that is open). Behaves like {@code Button}: click sound, keyboard activation,
 * narration.
 */
public class BrassButton extends AbstractButton {

    private final Runnable onPress;
    private boolean selected;

    public BrassButton(int x, int y, int w, int h, Component label, Runnable onPress) {
        super(x, y, w, h, label);
        this.onPress = onPress;
    }

    public BrassButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    @Override
    public void onPress() {
        onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiKit.ButtonState state = selected ? GuiKit.ButtonState.PRESSED : GuiKit.buttonState(active, isHovered());
        GuiKit.button(g, Minecraft.getInstance().font, getX(), getY(), getWidth(), getHeight(), getMessage(), state);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}

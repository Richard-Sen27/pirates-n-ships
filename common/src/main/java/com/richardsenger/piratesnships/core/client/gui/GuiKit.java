package com.richardsenger.piratesnships.core.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;

/**
 * Drawing helpers of the GUI texture kit (client only, work package U1): the wooden frame, the header plaque,
 * parchment panels, brass buttons, dividers, styled text fields, the scrollbar and the coin label. Text on parchment
 * is dark ink without a shadow; text on wood is light with vanilla's shadow.
 */
public final class GuiKit {

    /** Dark ink on parchment. */
    public static final int INK = 0xFF2C2018;
    /** Faded ink: secondary lines. */
    public static final int INK_DIM = 0xFF7A6852;
    public static final int INK_RED = 0xFF9A1C14;
    public static final int INK_GREEN = 0xFF2E6A24;
    public static final int INK_NAVY = 0xFF2A4580;
    public static final int INK_AMBER = 0xFF9A5A10;
    /** Light text on the wooden frame. */
    public static final int ON_WOOD = 0xFFEEE0BC;
    public static final int ON_WOOD_DIM = 0xFFB8A27C;
    public static final int ON_WOOD_RED = 0xFFFF8A70;
    public static final int BRASS_TEXT = 0xFFF4D27A;
    /** Ink of a disabled brass button's label. */
    public static final int BUTTON_DISABLED_TEXT = 0xFF4A463E;

    /** Width of the wooden frame's border (the sprite's nine-slice border). */
    public static final int FRAME_BORDER = 8;
    public static final int HEADER_H = 16;
    public static final int FIELD_H = 14;
    public static final int TAG_H = 11;
    public static final int SCROLLBAR_W = 6;

    public enum ButtonState { NORMAL, HOVER, PRESSED, DISABLED }

    private GuiKit() {
    }

    /** One sprite of the kit, alpha-blended. */
    public static void sprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blitSprite(sprite, x, y, w, h);
    }

    /** A 9x9 icon at its pixel size. */
    public static void icon(GuiGraphics g, ResourceLocation sprite, int x, int y) {
        sprite(g, sprite, x, y, GuiSprites.ICON, GuiSprites.ICON);
    }

    public static void frame(GuiGraphics g, int x, int y, int w, int h) {
        sprite(g, GuiSprites.FRAME, x, y, w, h);
    }

    public static void parchment(GuiGraphics g, int x, int y, int w, int h) {
        sprite(g, GuiSprites.PARCHMENT, x, y, w, h);
    }

    /** The header plaque with {@code title} centred in brass. */
    public static void header(GuiGraphics g, Font font, Component title, int x, int y, int w) {
        sprite(g, GuiSprites.HEADER, x, y, w, HEADER_H);
        Component cut = clip(font, title, w - 12);
        g.drawString(font, cut, x + (w - font.width(cut)) / 2, y + 4, BRASS_TEXT, true);
    }

    public static void divider(GuiGraphics g, int x, int y, int w) {
        sprite(g, GuiSprites.DIVIDER, x, y, w, 3);
    }

    public static void dividerVertical(GuiGraphics g, int x, int y, int h) {
        sprite(g, GuiSprites.DIVIDER_VERTICAL, x, y, 3, h);
    }

    /** The pressed look while the left button is held on a hovered, active button. */
    public static ButtonState buttonState(boolean active, boolean hovered) {
        if (!active) return ButtonState.DISABLED;
        if (!hovered) return ButtonState.NORMAL;
        return Minecraft.getInstance().mouseHandler.isLeftPressed() ? ButtonState.PRESSED : ButtonState.HOVER;
    }

    /** A brass button with its label centred in ink. */
    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, Component label, ButtonState state) {
        ResourceLocation sprite = switch (state) {
            case NORMAL -> GuiSprites.BUTTON;
            case HOVER -> GuiSprites.BUTTON_HOVER;
            case PRESSED -> GuiSprites.BUTTON_PRESSED;
            case DISABLED -> GuiSprites.BUTTON_DISABLED;
        };
        sprite(g, sprite, x, y, w, h);
        Component cut = clip(font, label, w - 6);
        int dy = state == ButtonState.PRESSED ? 1 : 0;
        int color = state == ButtonState.DISABLED ? BUTTON_DISABLED_TEXT : INK;
        g.drawString(font, cut, x + (w - font.width(cut)) / 2, y + (h - 8) / 2 + dy, color, false);
    }

    /**
     * Turns a vanilla text field into a kit field: no vanilla border, light text, the hint in faded parchment. The
     * box's bounds are the text area; {@link #field} draws the inset around it.
     */
    public static EditBox styleField(EditBox box, Component hint) {
        box.setBordered(false);
        box.setTextColor(ON_WOOD);
        box.setHint(hint.copy().withColor(ON_WOOD_DIM));
        return box;
    }

    /** Bounds for a kit field whose inset sprite covers {@code x, y, w, FIELD_H}: the text area inside it. */
    public static int fieldTextX(int x) {
        return x + 4;
    }

    public static int fieldTextY(int y) {
        return y + 3;
    }

    public static int fieldTextW(int w) {
        return w - 8;
    }

    /** The inset behind a field made by {@link #styleField} with the text-area bounds above. */
    public static void field(GuiGraphics g, EditBox box) {
        if (!box.visible) return;
        sprite(g, box.isFocused() ? GuiSprites.FIELD_FOCUSED : GuiSprites.FIELD, box.getX() - 4, box.getY() - 3,
                box.getWidth() + 8, FIELD_H);
    }

    /** A name tag (chip) of width {@code w}. */
    public static void tag(GuiGraphics g, Font font, int x, int y, int w, String name, boolean hover) {
        sprite(g, hover ? GuiSprites.TAG_HOVER : GuiSprites.TAG, x, y, w, TAG_H);
        g.drawString(font, name, x + 6, y + 2, INK, false);
    }

    /** Width of a tag for {@code name}. */
    public static int tagWidth(Font font, String name) {
        return font.width(name) + 9;
    }

    /**
     * Doubloons right-aligned at {@code right}: the coin icon, then the amount. Returns the left edge of what was
     * drawn.
     */
    public static int coins(GuiGraphics g, Font font, Component amount, int right, int y, int color, boolean shadow) {
        int w = font.width(amount);
        g.drawString(font, amount, right - w, y, color, shadow);
        int iconX = right - w - GuiSprites.ICON - 2;
        icon(g, GuiSprites.COIN, iconX, y - 1);
        return iconX;
    }

    /** {@code text} cut to {@code maxW} with ".." when it does not fit. */
    public static Component clip(Font font, Component text, int maxW) {
        if (font.width(text) <= maxW) return text;
        FormattedText cut = font.substrByWidth(text, Math.max(0, maxW - font.width("..")));
        return Component.literal(cut.getString() + "..").withStyle(text.getStyle());
    }

    /** Draws {@code text} clipped to {@code maxW}, keeping its styled parts where it fits. */
    public static void text(GuiGraphics g, Font font, Component text, int x, int y, int maxW, int color, boolean shadow) {
        if (font.width(text) <= maxW) {
            g.drawString(font, text, x, y, color, shadow);
            return;
        }
        FormattedText cut = font.substrByWidth(text, Math.max(0, maxW - font.width("..")));
        g.drawString(font, Language.getInstance().getVisualOrder(cut), x, y, color, shadow);
        g.drawString(font, "..", x + font.width(cut), y, color, shadow);
    }

    /** Ink text on parchment (no shadow), clipped. */
    public static void ink(GuiGraphics g, Font font, Component text, int x, int y, int maxW, int color) {
        text(g, font, text, x, y, maxW, color, false);
    }

    /** A translucent ink wash over a parchment row (row stripes, hover). */
    public static void wash(GuiGraphics g, int x0, int y0, int x1, int y1, int alpha) {
        g.fill(x0, y0, x1, y1, (alpha << 24) | 0x5A3A1A);
    }
}

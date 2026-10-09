package com.richardsenger.piratesnships.compat.jei;

import com.richardsenger.piratesnships.compat.TextLine;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/** Turns the compat module's {@link TextLine}s into components and draws them on a category page. Client only. */
final class JeiText {

    /** Line height on a page. */
    static final int LINE = 10;
    /** JEI's dark grey text colour. */
    static final int COLOR = 0xFF404040;

    private JeiText() {
    }

    static MutableComponent component(TextLine line) {
        Object[] args = line.args().stream().map(JeiText::arg).toArray();
        return Component.translatable(line.key(), args);
    }

    private static Object arg(Object a) {
        if (a instanceof TextLine t) return component(t);
        if (a instanceof List<?> list) {
            return ComponentUtils.formatList(list, Component.literal(", "),
                    o -> o instanceof TextLine t ? component(t) : Component.literal(String.valueOf(o)));
        }
        return String.valueOf(a);
    }

    /** Draws {@code lines} from {@code (x, y)} down, one per {@link #LINE}, cut to {@code width}. */
    static void draw(GuiGraphics graphics, Font font, List<TextLine> lines, int x, int y, int width) {
        for (TextLine line : lines) {
            Component c = component(line);
            String s = c.getString();
            if (font.width(s) > width) {
                graphics.drawString(font, font.plainSubstrByWidth(s, width - font.width("...")) + "...", x, y, COLOR, false);
            } else {
                graphics.drawString(font, c, x, y, COLOR, false);
            }
            y += LINE;
        }
    }
}

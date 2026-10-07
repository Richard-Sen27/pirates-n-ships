package com.richardsenger.piratesnships.core.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The kit's scrollbar (client only): a dark track with a brass knob that can be dragged or clicked into. The screen
 * owns the scroll offset (in rows); this class draws it and turns mouse input into a new offset with
 * {@link ScrollMath}.
 */
public final class ScrollBar {

    private int x;
    private int y;
    private int h;
    private boolean dragging;
    private double grabOffset;

    /** Places the track: {@link GuiKit#SCROLLBAR_W} wide, {@code h} tall. */
    public void place(int x, int y, int h) {
        this.x = x;
        this.y = y;
        this.h = h;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + GuiKit.SCROLLBAR_W && my >= y && my < y + h;
    }

    /** Draws the track and, when the list overflows, the knob. */
    public void render(GuiGraphics g, int total, int visible, int scroll, int mouseX, int mouseY) {
        if (h <= 0) return;
        GuiKit.sprite(g, GuiSprites.SCROLL_TRACK, x, y, GuiKit.SCROLLBAR_W, h);
        if (ScrollMath.maxScroll(total, visible) == 0) return;
        int size = ScrollMath.knobSize(h, total, visible);
        int ky = y + ScrollMath.knobOffset(h, total, visible, scroll);
        boolean hover = dragging || (mouseX >= x && mouseX < x + GuiKit.SCROLLBAR_W && mouseY >= ky && mouseY < ky + size);
        GuiKit.sprite(g, hover ? GuiSprites.SCROLL_KNOB_HOVER : GuiSprites.SCROLL_KNOB, x, ky, GuiKit.SCROLLBAR_W, size);
    }

    /**
     * A click on the track: grabs the knob (or centres it on the click first). Returns the new scroll offset, or -1
     * if the click missed the bar or there is nothing to scroll.
     */
    public int click(double mx, double my, int total, int visible, int scroll) {
        if (!contains(mx, my) || ScrollMath.maxScroll(total, visible) == 0) return -1;
        int size = ScrollMath.knobSize(h, total, visible);
        int ky = y + ScrollMath.knobOffset(h, total, visible, scroll);
        if (my < ky || my >= ky + size) {
            scroll = ScrollMath.scrollForOffset(h, total, visible, my - y - size / 2.0);
            ky = y + ScrollMath.knobOffset(h, total, visible, scroll);
        }
        dragging = true;
        grabOffset = my - ky;
        return scroll;
    }

    /** While dragging: the scroll offset for the mouse at {@code my}, or -1 when not dragging. */
    public int drag(double my, int total, int visible) {
        if (!dragging) return -1;
        return ScrollMath.scrollForOffset(h, total, visible, my - y - grabOffset);
    }

    public void release() {
        dragging = false;
    }
}

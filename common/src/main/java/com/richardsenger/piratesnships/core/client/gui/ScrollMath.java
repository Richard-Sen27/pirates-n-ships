package com.richardsenger.piratesnships.core.client.gui;

/**
 * Pure scrollbar maths for the kit's lists (no client classes, tested with JUnit): the knob's size and position in
 * its track, and the scroll offset a dragged knob stands for. Offsets count rows.
 */
public final class ScrollMath {

    /** Smallest knob, so it stays grabbable in a long list. */
    public static final int MIN_KNOB = 8;

    private ScrollMath() {
    }

    /** Largest scroll offset: {@code total - visible}, never below 0. */
    public static int maxScroll(int total, int visible) {
        return Math.max(0, total - Math.max(0, visible));
    }

    /** {@code scroll} clamped to {@code 0..maxScroll}. */
    public static int clamp(int scroll, int total, int visible) {
        return Math.max(0, Math.min(scroll, maxScroll(total, visible)));
    }

    /** Knob height in a track of {@code track} px: the visible share of the list, at least {@link #MIN_KNOB}. */
    public static int knobSize(int track, int total, int visible) {
        if (track <= 0) return 0;
        if (total <= visible || total <= 0) return track;
        int size = (int) ((long) track * visible / total);
        return Math.min(track, Math.max(Math.min(MIN_KNOB, track), size));
    }

    /** Knob offset from the track's top for {@code scroll}. */
    public static int knobOffset(int track, int total, int visible, int scroll) {
        int max = maxScroll(total, visible);
        if (max == 0) return 0;
        int room = track - knobSize(track, total, visible);
        return (int) Math.round((double) room * clamp(scroll, total, visible) / max);
    }

    /** The scroll offset for a knob whose top is {@code offset} px below the track's top (dragging). */
    public static int scrollForOffset(int track, int total, int visible, double offset) {
        int max = maxScroll(total, visible);
        int room = track - knobSize(track, total, visible);
        if (max == 0 || room <= 0) return 0;
        return clamp((int) Math.round(offset / room * max), total, visible);
    }
}

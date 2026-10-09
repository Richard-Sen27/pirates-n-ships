package com.richardsenger.piratesnships.almanac;

import java.util.function.IntUnaryOperator;

/**
 * The placed Captain's Almanac's book animation (ALM1, docs/design.md §3.4), without world access: how far the book is
 * open and where its turning pages are, stepped once per client tick from the synced "being read" flag of
 * {@link AlmanacBlockEntity}. The enchanting table's book is the model for the feel (it opens over a few ticks, then
 * turns a page now and then), but the rule is ours: it opens only while someone reads it, never by itself.
 *
 * <ul>
 *   <li>{@link #open()} goes from 0 (closed, lying on its cover) to 1 (lying open) by {@link #OPEN_STEP} a tick while
 *       read and back down by the same step afterwards.</li>
 *   <li>A page turns as the book starts being read; once fully open, a further 1 to 3 pages turn with a chance of
 *       1 in {@link #FLIP_CHANCE} each tick. Pages only turn forward, the way a reader goes.</li>
 *   <li>{@link #flip()} counts pages turned so far (fractional while one is turning); it eases towards the target with
 *       at most {@link #MAX_FLIP_SPEED} pages a tick.</li>
 * </ul>
 * The partial-tick getters interpolate between the previous and the current tick for the renderer.
 */
public final class AlmanacAnimation {

    /** How much the book opens or closes per tick: fully open in 10 ticks. */
    public static final float OPEN_STEP = 0.1f;
    /** Once open, a page turn starts with a chance of 1 in this many per tick. */
    public static final int FLIP_CHANCE = 30;
    /** The fastest the pages turn, in pages per tick. */
    public static final float MAX_FLIP_SPEED = 0.2f;

    private float open;
    private float oOpen;
    private float flip;
    private float oFlip;
    private float flipTarget;
    private float flipSpeed;
    private boolean wasRead;
    private int time;

    /**
     * One tick. {@code read}: the block entity's synced "being read" flag; {@code random}: a bound to a random int in
     * {@code [0, bound)} (the level's random in game, a fixed sequence in tests).
     */
    public void tick(boolean read, IntUnaryOperator random) {
        oOpen = open;
        oFlip = flip;
        time++;
        if (read) {
            if (!wasRead) flipTarget += 1.0f; // the reader opens it: a page turns
            open = open + OPEN_STEP > 0.999f ? 1.0f : open + OPEN_STEP;
            if (open >= 1.0f && random.applyAsInt(FLIP_CHANCE) == 0) {
                flipTarget += 1 + random.applyAsInt(3);
            }
        } else {
            open = open - OPEN_STEP < 0.001f ? 0.0f : open - OPEN_STEP;
        }
        wasRead = read;
        float wanted = clamp((flipTarget - flip) * 0.4f, -MAX_FLIP_SPEED, MAX_FLIP_SPEED);
        flipSpeed += (wanted - flipSpeed) * 0.9f;
        flip += flipSpeed;
    }

    /** How far open now: 0 closed, 1 fully open. */
    public float open() {
        return open;
    }

    public float open(float partialTick) {
        return oOpen + (open - oOpen) * partialTick;
    }

    /** Pages turned so far (fractional while one turns). */
    public float flip() {
        return flip;
    }

    public float flip(float partialTick) {
        return oFlip + (flip - oFlip) * partialTick;
    }

    /** Pages the book is turning towards. */
    public float flipTarget() {
        return flipTarget;
    }

    /** Ticks stepped so far. */
    public int time() {
        return time;
    }

    /** Closed and still: nothing to animate. */
    public boolean resting() {
        return open == 0.0f && oOpen == 0.0f && Math.abs(flipTarget - flip) < 1.0e-3f;
    }

    /**
     * How far one of the two loose pages the model draws is through its turn at {@code flip} pages: 0 lying on the
     * right, 1 lying on the left. The two pages are half a page turn apart ({@code phase} 0.25 and 0.75), and each turns
     * over the middle 62.5% of its cycle and rests on either side for the rest, so one page is always in flight while
     * the book turns.
     */
    public static float pageTurn(float flip, float phase) {
        float cycle = flip + phase;
        cycle -= (float) Math.floor(cycle);
        return clamp((cycle - 0.1875f) / 0.625f, 0.0f, 1.0f);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }
}

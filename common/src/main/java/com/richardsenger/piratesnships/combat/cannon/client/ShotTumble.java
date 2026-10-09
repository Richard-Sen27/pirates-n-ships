package com.richardsenger.piratesnships.combat.cannon.client;

/**
 * The in-flight spin of chain shot (ART15, docs/design.md §8.2): the two balls turn end over end about the axis across
 * their flight at a steady rate, so the angle is a plain function of the shot's age and the frame's partial tick. Pure
 * math, no client classes (tested by {@code ShotTumbleTest}); drawn by {@link CannonballRenderer}.
 */
public final class ShotTumble {

    /** Degrees the chain shot turns per tick: 36, so one turn in half a second (two turns a second). */
    public static final float DEGREES_PER_TICK = 36.0f;

    private ShotTumble() {
    }

    /**
     * The tumble angle in degrees, 0 (inclusive) to 360 (exclusive), {@code partialTick} (0..1) of the way from tick
     * {@code age} to the next. Continuous across ticks: {@code angle(age, 1)} is {@code angle(age + 1, 0)}.
     */
    public static float angle(int age, float partialTick) {
        double turned = (age + (double) partialTick) * DEGREES_PER_TICK;
        double wrapped = turned % 360.0;
        if (wrapped < 0) wrapped += 360.0;
        float angle = (float) wrapped;
        return angle >= 360.0f ? 0.0f : angle;
    }
}

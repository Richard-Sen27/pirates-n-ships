package com.richardsenger.piratesnships.chart.held;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pure part of the held chart (CHART3, no world, no library): how far a held chart is unrolled, which item model frame
 * that shows and which arm animation plays.
 *
 * <p>The unroll progress runs from 0 (rolled up) to 1 (open) in {@link #UNROLL_TICKS} ticks while the player reads
 * the chart, and back at the same speed when they stop, so closing the screen half way rolls it up from where it was.
 * The item property {@code pirates_n_ships:chart_open} is the progress (interpolated over the partial tick); the
 * chart's item model overrides on it pick one of {@link #FRAMES}. The arms play {@link #READ} (raise the rolled chart,
 * draw the fists apart) while it unrolls or stays open, and {@link #STOW} (the same keys backwards) while it rolls up;
 * both last the same {@link #UNROLL_TICKS}, so an animation started at {@link #readStartTick} or
 * {@link #stowStartTick} meets the item frame that shows. Files and frames come from
 * {@code tools/gen_chart_held_models.py}.
 *
 * <p>CHART4, smoother: the progress still runs linearly (and the property is interpolated over the partial tick), but
 * the motion it shows is eased in and out ({@link #ease}): the PAL keys sample the eased curve every 25 ms, and
 * fourteen frames split it ({@code chart_unroll_0} holds the rolled chart while the fists come together, twelve frames
 * share the unroll evenly in progress, {@code chart_open} at 1), each drawn for the pose at {@link Frame#at}. Rolling up
 * plays the same keys backwards, so the close follows the same curve.
 */
public final class ChartHeldMotion {

    /** Ticks from rolled up to open (half a second), the length of both arm animations. */
    public static final int UNROLL_TICKS = 10;
    /** Arm animation while unrolling and reading; file {@code assets/pirates_n_ships/chart_animations/chart_read.json}. */
    public static final String READ = "chart_read";
    /** Arm animation while rolling up; file {@code assets/pirates_n_ships/chart_animations/chart_stow.json}. */
    public static final String STOW = "chart_stow";

    /**
     * One item model frame, the progress it shows from (the chart model's overrides, in order) and the progress whose
     * pose it is drawn for (the arms of {@link #READ} at {@code at * UNROLL_TICKS} ticks hold its rollers).
     */
    public record Frame(String model, float from, float at) {
    }

    /** The unroll frames after the closed {@code chart} model, shown from their threshold on (the last match wins); printed by the generator. */
    public static final List<Frame> FRAMES = List.of(
            new Frame("chart_unroll_0", 0.01f, 0.364f),
            new Frame("chart_unroll_1", 0.3633f, 0.39f),
            new Frame("chart_unroll_2", 0.4163f, 0.442f),
            new Frame("chart_unroll_3", 0.4694f, 0.496f),
            new Frame("chart_unroll_4", 0.5224f, 0.548f),
            new Frame("chart_unroll_5", 0.5755f, 0.602f),
            new Frame("chart_unroll_6", 0.6286f, 0.656f),
            new Frame("chart_unroll_7", 0.6816f, 0.708f),
            new Frame("chart_unroll_8", 0.7347f, 0.762f),
            new Frame("chart_unroll_9", 0.7878f, 0.814f),
            new Frame("chart_unroll_10", 0.8408f, 0.868f),
            new Frame("chart_unroll_11", 0.8939f, 0.92f),
            new Frame("chart_unroll_12", 0.9469f, 0.974f),
            new Frame("chart_open", 1.0f, 1.0f));

    private ChartHeldMotion() {
    }

    /** The progress one tick later: towards 1 while {@code open}, towards 0 otherwise. */
    public static float step(float progress, boolean open) {
        float delta = 1f / UNROLL_TICKS;
        float next = open ? progress + delta : progress - delta;
        return Math.max(0f, Math.min(1f, next));
    }

    /** CHART4: the ease-in-out the motion follows (smoothstep): how far along CHART3's linear motion progress {@code p} stands. */
    public static float ease(float p) {
        float t = Math.max(0f, Math.min(1f, p));
        return t * t * (3f - 2f * t);
    }

    /** The progress shown between two ticks. */
    public static float lerp(float previous, float current, float partialTick) {
        return previous + (current - previous) * Math.max(0f, Math.min(1f, partialTick));
    }

    /** The item model the chart shows at {@code progress}: {@code chart} below the first frame, else the last frame reached. */
    public static String frame(float progress) {
        String model = "chart";
        for (Frame f : FRAMES) {
            if (progress >= f.from()) model = f.model();
        }
        return model;
    }

    /** The arm animation of a player whose chart is at {@code progress} and who reads ({@code open}) or not; null for none. */
    public static @Nullable String animation(boolean holding, boolean open, float progress) {
        if (!holding) return null;
        if (open) return READ;
        return progress > 0f ? STOW : null;
    }

    /** Where {@link #READ} starts (animation ticks) when the chart is already {@code progress} unrolled. */
    public static float readStartTick(float progress) {
        return Math.max(0f, Math.min(1f, progress)) * UNROLL_TICKS;
    }

    /** Where {@link #STOW} starts (animation ticks) when the chart is still {@code progress} unrolled. */
    public static float stowStartTick(float progress) {
        return (1f - Math.max(0f, Math.min(1f, progress))) * UNROLL_TICKS;
    }
}

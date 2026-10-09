package com.richardsenger.piratesnships.crew.npc;

import net.minecraft.util.Mth;

/**
 * How a crew member at its station turns to face it (ST5, docs/design.md §6 "Facing the station"): every server tick
 * the body yaw moves towards the station's yaw ({@link StationPoses#yaw}, which changes as the ship turns) along the
 * shorter arc by at most {@code crew_stations.turn_degrees_per_tick}, so it turns smoothly after it walked in and then
 * stays locked to it while seated. The head looks at most {@link #MAX_HEAD_DEGREES} away from the body; a pose that
 * holds the head (the helmsman, the gun crew: {@link StationPoses.Shown#faces()}) keeps it on the body.
 *
 * <p>Angles are Minecraft yaw degrees. Results stay continuous with their inputs (no jump by 360), so the renderer's
 * interpolation between two ticks never sweeps round the long way. Pure.
 */
public final class StationFacing {

    /** How far the head may look away from the body while the body is held to the station. */
    public static final float MAX_HEAD_DEGREES = 45.0f;

    /** Within this many degrees the body counts as facing the station. */
    public static final float LOCKED_DEGREES = 1.0e-3f;

    /** Body and head yaw after one tick. */
    public record Turn(float body, float head) {
    }

    private StationFacing() {
    }

    /**
     * One tick of the turn.
     *
     * @param body        the body yaw now
     * @param head        the head yaw now
     * @param target      the yaw the station faces
     * @param maxStep     degrees the body may turn this tick (180 or more: at once)
     * @param maxHead     degrees the head may look away from the body
     * @param headFollows the head is held on the body (a station pose), rather than free within {@code maxHead}
     */
    public static Turn step(float body, float head, float target, float maxStep, float maxHead, boolean headFollows) {
        float newBody = approach(body, target, maxStep);
        float newHead = headFollows ? continuous(head, newBody) : clampHead(head, newBody, maxHead);
        return new Turn(newBody, newHead);
    }

    /** {@code from} moved towards {@code to} along the shorter arc by at most {@code maxStep} degrees (continuous with {@code from}). */
    public static float approach(float from, float to, float maxStep) {
        float diff = Mth.wrapDegrees(to - from);
        float step = Math.max(0.0f, maxStep);
        return from + Mth.clamp(diff, -step, step);
    }

    /** {@code head} kept within {@code maxHead} degrees of {@code body} (continuous with {@code head}). */
    public static float clampHead(float head, float body, float maxHead) {
        float rel = Mth.wrapDegrees(head - body);
        float limit = Math.max(0.0f, maxHead);
        float clamped = Mth.clamp(rel, -limit, limit);
        return head + (clamped - rel);
    }

    /** {@code angle} written as the yaw nearest to {@code reference} (differs by a multiple of 360). */
    public static float continuous(float reference, float angle) {
        return reference + Mth.wrapDegrees(angle - reference);
    }

    /** Whether {@code body} faces {@code target}. */
    public static boolean locked(float body, float target) {
        return Math.abs(Mth.wrapDegrees(target - body)) <= LOCKED_DEGREES;
    }
}

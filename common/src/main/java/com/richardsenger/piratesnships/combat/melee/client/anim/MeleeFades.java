package com.richardsenger.piratesnships.combat.melee.client.anim;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

import static com.richardsenger.piratesnships.combat.melee.client.anim.MeleeAnimationMapping.*;

/**
 * Pure part of the cross-fades between melee animations (MEL1, no library, no world): when a new animation should
 * blend in from the pose on screen instead of snapping to its first frame, and for how long.
 *
 * <p>The authored animations (art/README.md, "Player animations") chain exactly: a wind-up ends where its active
 * starts, an active where its recovery starts, the guard where the parry and the guard lowering start, and every
 * attack, the guard and the stagger start at the rest pose that recoveries, the guard lowering, the parry and the
 * stagger end in. A transition between two such matching poses needs no fade (a fade would only blur the wind-up's
 * telegraph). Everything else is a jump: an animation cut short (a parry or hit staggering an attack mid-swing, a
 * successful parry ending its window early, a guard released before it was up), a riposte wind-up going into a
 * slash, a parry tapped from rest (it starts at the guard pose). Those blend over {@code melee_animations.fade_ticks}.
 *
 * <p>The fade is cosmetic: it never changes which animation plays, its speed or its start offset
 * ({@link MeleeAnimationMapping#speed}, {@link MeleeAnimationMapping#startTick}), and the server's phase timing does
 * not know about it. A fade into a timed phase is capped at the phase's length so it is complete before the phase
 * ends.
 */
public final class MeleeFades {

    /**
     * How many game ticks before its end an animation still counts as finished. A phase change from the server can
     * arrive a tick before the client's stretched animation reaches its last frame (network jitter); the pose is then
     * within one tick of the chained pose and needs no fade.
     */
    public static final float END_TOLERANCE_TICKS = 1.5f;

    /** Animations whose first frame is the rest pose (vanilla's "holding an item" arm). */
    public static final Set<String> STARTS_AT_REST = Set.of(SLASH_WINDUP, THRUST_WINDUP, RIPOSTE_WINDUP, GUARD, STAGGER);
    /** Animations whose last frame is the rest pose. */
    public static final Set<String> ENDS_AT_REST = Set.of(SLASH_RECOVERY, THRUST_RECOVERY, GUARD_LOWER, PARRY, STAGGER);
    /** Pairs where the first animation's last frame is the second one's first frame (neither of them the rest pose). */
    public static final Map<String, Set<String>> CHAINS = Map.of(
            SLASH_WINDUP, Set.of(SLASH_ACTIVE),
            SLASH_ACTIVE, Set.of(SLASH_RECOVERY),
            THRUST_WINDUP, Set.of(THRUST_ACTIVE),
            THRUST_ACTIVE, Set.of(THRUST_RECOVERY),
            RIPOSTE_WINDUP, Set.of(THRUST_ACTIVE),
            GUARD, Set.of(GUARD_LOWER, PARRY));

    /**
     * The bone channels every melee animation keys. A fade snapshots exactly these, so it never freezes the walking
     * legs or the looking head ({@code MeleeFadesTest} checks the files).
     */
    public static final Map<String, Set<String>> FADED_CHANNELS = Map.of(
            "torso", Set.of("rotation", "position"),
            "head", Set.of("position"),
            "right_arm", Set.of("rotation", "position"),
            "left_arm", Set.of("rotation", "position"),
            "right_item", Set.of("rotation"));

    private MeleeFades() {
    }

    /** Whether {@code to} starts where {@code from} ends ({@code null} = the rest pose, no melee animation). */
    public static boolean seamless(@Nullable String from, @Nullable String to) {
        boolean fromRest = from == null || ENDS_AT_REST.contains(from);
        boolean toRest = to == null || STARTS_AT_REST.contains(to);
        if (fromRest && toRest) return true;
        return from != null && to != null && CHAINS.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Fade length in game ticks for switching from what is on screen to {@code to}.
     *
     * @param from          the melee animation on screen, {@code null} for none (rest) or a fade back to rest
     * @param fromRemaining game ticks until {@code from} (or the fade back to rest) reaches its last frame; 0 or less
     *                      when it is there (finished, or holding its last frame)
     * @param to            the animation starting now, {@code null} when the layer goes back to rest
     * @param configured    {@code melee_animations.fade_ticks}; 0 or less turns fades off (snap, as before MEL1)
     * @param toDuration    the new phase's length in game ticks, 0 for an open-ended phase (guard) or going to rest
     * @return 0 for no fade, else 1..{@code configured}
     */
    public static int fadeTicks(@Nullable String from, float fromRemaining, @Nullable String to, int configured, int toDuration) {
        if (configured <= 0) return 0;
        boolean atEnd = fromRemaining <= END_TOLERANCE_TICKS;
        if (atEnd && seamless(from, to)) return 0;
        int ticks = configured;
        if (to != null && toDuration > 0) ticks = Math.min(ticks, toDuration);
        return Math.max(1, ticks);
    }

    /**
     * Blend weight of the new animation at {@code progress} (0..1) through the fade: smoothstep, so the blend leaves
     * the old pose and reaches the new one without a kink.
     */
    public static float alpha(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        return p * p * (3f - 2f * p);
    }

    /**
     * What one entity's melee layer shows, for deciding the next fade: the current animation and when it reaches its
     * last frame, in game ticks. Client thread only (one per animated player).
     */
    public static final class Track {

        private @Nullable String current;
        private float endsAt = Float.NEGATIVE_INFINITY;

        /** The animation on screen, {@code null} at rest or while fading back to rest. */
        public @Nullable String current() {
            return current;
        }

        /** Game ticks until the animation on screen (or the fade back to rest) reaches its last frame. */
        public float remaining(long now) {
            return endsAt - now;
        }

        /**
         * {@code name} starts now at {@code speed} (animation ticks per game tick) from animation tick
         * {@code startTick}. Returns the fade to use.
         *
         * @param lengthTicks the animation's length in animation ticks
         * @param toDuration  the phase's length in game ticks (0 = open-ended)
         */
        public int play(String name, float lengthTicks, float startTick, float speed, long now, int configured, int toDuration) {
            int fade = fadeTicks(current, remaining(now), name, configured, toDuration);
            current = name;
            float left = Math.max(0f, lengthTicks - startTick);
            endsAt = now + (speed > 0f ? left / speed : left);
            return fade;
        }

        /** The layer goes back to rest. Returns the fade-out length (0 = stop at once). */
        public int stop(long now, int configured) {
            int fade = fadeTicks(current, remaining(now), null, configured, 0);
            current = null;
            endsAt = now + fade;
            return fade;
        }

        /** Stopped at once (animations off, a missing file). */
        public void halt(long now) {
            current = null;
            endsAt = now;
        }
    }
}

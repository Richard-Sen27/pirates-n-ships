package com.richardsenger.piratesnships.combat.firearms.client.anim;

import com.richardsenger.piratesnships.combat.firearms.FirearmKind;
import com.richardsenger.piratesnships.combat.firearms.FirearmRules;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pure part of the firearm animations (no library, no world): which animation a player holding use on a gun shows,
 * when it restarts, and how fast a reload plays (docs/design.md §8.1, art/README.md "Firearm animations").
 *
 * <p>The session is read from the remaining use time exactly like {@link FirearmRules} does on the server: an aim
 * session (the gun was loaded at the press) holds the aim animation for as long as the player holds; a loading session
 * plays the reload animation once, stretched to the gun's {@code reload_ticks}. Nothing plays when the player is not
 * using a gun. Files live in {@code assets/pirates_n_ships/player_animations/} (key = name).
 */
public final class FirearmAnimationMapping {

    /** Layer id path ({@code pirates_n_ships:firearms}). */
    public static final String LAYER = "firearms";

    /** One-handed aim: arm out at eye height; holds its last frame. */
    public static final String PISTOL_AIM = "pistol_aim";
    /** Two-handed aim: right hand at the lock, left arm under the barrel; holds its last frame. */
    public static final String MUSKET_AIM = "musket_aim";
    /** Powder, ramrod, cock; 3 s (the default 60 reload ticks), plays once. */
    public static final String PISTOL_RELOAD = "pistol_reload";
    /** Butt to the ground, powder, ram twice, raise, cock; 5 s (the default 100 reload ticks), plays once. */
    public static final String MUSKET_RELOAD = "musket_reload";

    /** Every animation this mapping can return. */
    public static final List<String> ALL = List.of(PISTOL_AIM, MUSKET_AIM, PISTOL_RELOAD, MUSKET_RELOAD);

    /** Speed limits, so an extreme {@code reload_ticks} (1 tick, a minute) can't make the reload absurd. */
    public static final float MIN_SPEED = 0.05f;
    public static final float MAX_SPEED = 20f;

    private FirearmAnimationMapping() {
    }

    /**
     * What a gun-holding player shows: the animation, whether it is an aim (the look pitch and head turn are added to
     * the arms) and how long the session has been held.
     */
    public record Pose(String animation, boolean aim, int heldTicks) {
    }

    /**
     * The pose of a player who is using a gun of {@code kind} with {@code remainingUseTicks} left, or {@code null}
     * when they are not using a gun ({@code kind == null}, or no use time left).
     */
    public static @Nullable Pose forUse(@Nullable FirearmKind kind, int remainingUseTicks) {
        if (kind == null || remainingUseTicks <= 0) return null;
        boolean aim = FirearmRules.isAimSession(remainingUseTicks);
        String name = aim ? aimAnimation(kind) : reloadAnimation(kind);
        return new Pose(name, aim, Math.max(0, FirearmRules.heldTicks(remainingUseTicks)));
    }

    public static String aimAnimation(FirearmKind kind) {
        return kind == FirearmKind.MUSKET ? MUSKET_AIM : PISTOL_AIM;
    }

    public static String reloadAnimation(FirearmKind kind) {
        return kind == FirearmKind.MUSKET ? MUSKET_RELOAD : PISTOL_RELOAD;
    }

    /**
     * Whether going from {@code previous} (what plays now, {@code null} = nothing) to {@code next} has to trigger
     * {@code next}'s animation from its start: a different animation, or the same one in a new session (the held
     * time went down: the player let go and pressed again between two ticks, e.g. aim, fire and reload).
     */
    public static boolean restarts(@Nullable Pose previous, Pose next) {
        return previous == null || !previous.animation().equals(next.animation()) || next.heldTicks() < previous.heldTicks();
    }

    /**
     * Playback speed of a pose's animation of {@code animationLengthTicks} (PAL's {@code SpeedModifier}: 2 = twice as
     * fast): a reload is stretched to last exactly {@code reloadTicks}; an aim (or a bad length or reload time)
     * plays at normal speed. Clamped to {@link #MIN_SPEED}..{@link #MAX_SPEED}.
     */
    public static float speed(boolean aim, float animationLengthTicks, int reloadTicks) {
        if (aim || reloadTicks <= 0 || !(animationLengthTicks > 0f)) return 1f;
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, animationLengthTicks / reloadTicks));
    }

    /**
     * Where to start the animation, in animation ticks, for a session already held {@code heldTicks} game ticks (a
     * remote player first seen mid-reload, or the tick that passed before the first update). At {@code speed} the
     * animation advances {@code speed} animation ticks per game tick. Clamped to the animation's length.
     */
    public static float startTick(int heldTicks, float speed, float animationLengthTicks) {
        float start = Math.max(0, heldTicks) * speed;
        return animationLengthTicks > 0f ? Math.min(start, animationLengthTicks) : start;
    }
}

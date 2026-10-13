package com.richardsenger.piratesnships.combat.grapple;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The rope pose a player shows (GR8, the human, 2026-10-13: "a proper animation/pose for the player … when e.g. hanging
 * on the vertical rope, swinging on it, or pulling it. when hanging on the rope, it is essentially climbing it up"),
 * picked from what every client knows about a rope: the hook's synced state (latched, tied off, the hand-held rope's
 * length, its pull counter, taut) and the player (on the ground, riding, sneaking, moving). Pure rules, no world access;
 * the client plays the pose's PAL animation ({@code client.anim.RopeSlidePoses}), JUnit and a GameTest check the choice.
 *
 * <ul>
 *   <li>{@link #SLIDE} riding a rope (GR2) and {@link #HAUL} hauling a ship by hand (GR5, ART7) as before;</li>
 *   <li>hanging on one's own hand-held rope off the ground: {@link #CLIMB} for {@link #PULL_TICKS} after each pull (one
 *       hand-over-hand per {@code rope_climb_step}), {@link #SWING} while swinging faster than {@link #SWING_SPEED}
 *       (until it slows below {@link #SWING_SPEED_OFF}), else {@link #HANG};</li>
 *   <li>{@link #PULL} for {@link #PULL_TICKS} after a pull while standing (the pull that lifts a player off the ground).</li>
 * </ul>
 */
public enum RopePose {
    NONE(null),
    SLIDE("rope_slide"),
    HAUL("haul"),
    HANG("rope_hang"),
    CLIMB("rope_climb"),
    SWING("rope_swing"),
    PULL("rope_pull");

    /** How long a pull shows its hand-over-hand [ticks]: the animation's length and the default pull cooldown. */
    public static final int PULL_TICKS = 10;
    /** Swing speed [blocks per tick, across the rope] from which a hanging player shows the swing. */
    public static final double SWING_SPEED = 0.12;
    /** Swing speed below which a swinging player goes back to the hang (hysteresis, no flicker at the turning points). */
    public static final double SWING_SPEED_OFF = 0.05;

    private final @Nullable String animation;

    RopePose(@Nullable String animation) {
        this.animation = animation;
    }

    /** The PAL animation's name and file ({@code rope_animations/<name>.json}), null for {@link #NONE}. */
    public @Nullable String animation() {
        return animation;
    }

    /** Whether the player hangs from the rope by the hands in this pose (the body leans along the rope, {@link RopeLean}). */
    public boolean hangs() {
        return this == HANG || this == CLIMB || this == SWING;
    }

    /** The pose of one player. */
    public static RopePose select(Facts f, RopePose previous) {
        if (f.sliding()) {
            return SLIDE;
        }
        if (f.hauling()) {
            return HAUL;
        }
        boolean pulled = f.ticksSincePull() >= 0 && f.ticksSincePull() < PULL_TICKS;
        if (f.hanging()) {
            if (pulled) {
                return CLIMB;
            }
            double needed = previous == SWING ? SWING_SPEED_OFF : SWING_SPEED;
            return f.swingSpeed() >= needed ? SWING : HANG;
        }
        return pulled && f.ropeInHand() ? PULL : NONE;
    }

    /**
     * What the pose depends on.
     *
     * @param sliding        the player rides a rope ({@code RopeRiderEntity}, GR2)
     * @param hauling        the player hauls on their own frozen, taut rope ({@code RopeSlidePoses#hauls}, GR5)
     * @param hanging        the player hangs on their own hand-held rope ({@link #hanging})
     * @param ropeInHand     the player's own latched rope is in their hand (not tied off)
     * @param ticksSincePull ticks since that rope's last pull, negative for none
     * @param swingSpeed     the player's speed across the rope [blocks per tick] ({@link #swingSpeed})
     */
    public record Facts(boolean sliding, boolean hauling, boolean hanging, boolean ropeInHand, int ticksSincePull,
                        double swingSpeed) {
    }

    /**
     * The facts of {@code player} with {@code hook}, their own hook (null: none), as either side sees them: the rope's
     * length {@code ropeLength} (a client predicts its own, others use the synced one) and the speed across the rope
     * {@code swingSpeed} ({@link #swingSpeed}); {@code sliding}: the player rides a rope.
     */
    public static Facts facts(boolean sliding, @Nullable GrapplingHookEntity hook, Player player, double ropeLength, double swingSpeed) {
        if (hook == null) {
            return new Facts(sliding, false, false, false, -1, 0.0);
        }
        boolean latched = hook.state() == GrapplingHookEntity.State.LATCHED;
        boolean tied = hook.syncedTiedRing().isPresent();
        boolean riding = player.isPassenger();
        return new Facts(sliding, hauls(latched, hook.taut(), tied, player.isShiftKeyDown(), riding),
                hanging(latched, tied, ropeLength, player.onGround(), riding), latched && !tied, hook.ticksSincePull(), swingSpeed);
    }

    /**
     * Whether a player hauls on a rope (GR5's freeze as far as a client can see it, ART7): their hook is latched, the
     * rope is taut (synced), its near end is in their hand (not tied to a ring or cleat), they sneak and do not ride.
     */
    public static boolean hauls(boolean latched, boolean taut, boolean tied, boolean sneaking, boolean riding) {
        return latched && taut && !tied && sneaking && !riding;
    }

    /**
     * Whether a player hangs on their own hand-held rope: the hook latched, the near end in their hand (not tied to a
     * cleat or ring), the rope holding at a length ({@link RopeSwing#holding}), off the ground and not riding.
     */
    public static boolean hanging(boolean latched, boolean tied, double ropeLength, boolean onGround, boolean riding) {
        return latched && !tied && RopeSwing.holding(ropeLength) && !onGround && !riding;
    }

    /**
     * The speed [blocks per tick] across the rope of a player whose hand is at {@code rel} from the hook, moving at
     * {@code vel} relative to it: the velocity less its part along the rope.
     */
    public static double swingSpeed(Vec3 rel, Vec3 vel) {
        double d = rel.length();
        if (d < 1.0e-6) {
            return vel.length();
        }
        Vec3 u = rel.scale(1.0 / d);
        return vel.subtract(u.scale(vel.dot(u))).length();
    }
}

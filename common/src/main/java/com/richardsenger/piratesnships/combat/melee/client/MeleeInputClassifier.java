package com.richardsenger.piratesnships.combat.melee.client;

import com.richardsenger.piratesnships.combat.melee.net.MeleeAction;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the attack and use keys, sampled once per client tick, into melee actions (docs/design.md §8.5). Pure, no
 * client classes, so JUnit drives it directly.
 *
 * <ul>
 *   <li>Attack held for {@code n} ticks, then released: {@code n < holdToThrustTicks} = {@link MeleeAction#SLASH},
 *       else {@link MeleeAction#THRUST}. Both start on release.</li>
 *   <li>Use pressed: {@link MeleeAction#GUARD_DOWN} at once (the guard protects from the first tick). Released after
 *       {@code n} ticks: {@link MeleeAction#GUARD_UP}, followed by {@link MeleeAction#PARRY} when
 *       {@code n < parryTapTicks} (a tap is a parry).</li>
 * </ul>
 *
 * A press and release inside one tick counts as held for one tick. The two keys are independent.
 */
public final class MeleeInputClassifier {

    /** @param holdToThrustTicks attack held at least this long = thrust; @param parryTapTicks use released sooner = parry */
    public record Thresholds(int holdToThrustTicks, int parryTapTicks) {
        public static final Thresholds DEFAULTS = new Thresholds(6, 4);

        public Thresholds {
            if (holdToThrustTicks < 1 || parryTapTicks < 1) throw new IllegalArgumentException("thresholds must be >= 1");
        }
    }

    private int attackTicks;
    private int useTicks;

    /** Feeds one client tick: whether each key was down (or clicked) during it. Returns the actions to send, in order. */
    public List<MeleeAction> tick(boolean attackDown, boolean useDown, Thresholds t) {
        List<MeleeAction> out = new ArrayList<>(2);
        if (attackDown) {
            attackTicks++;
        } else if (attackTicks > 0) {
            out.add(attackTicks < t.holdToThrustTicks() ? MeleeAction.SLASH : MeleeAction.THRUST);
            attackTicks = 0;
        }
        if (useDown) {
            if (useTicks == 0) out.add(MeleeAction.GUARD_DOWN);
            useTicks++;
        } else if (useTicks > 0) {
            out.add(MeleeAction.GUARD_UP);
            if (useTicks < t.parryTapTicks()) out.add(MeleeAction.PARRY);
            useTicks = 0;
        }
        return out;
    }

    /**
     * Drops both keys without an attack (the sword was put away, a screen opened, the system turned off). Returns
     * {@link MeleeAction#GUARD_UP} if a guard was raised, so the server never keeps a stale guard.
     */
    public List<MeleeAction> reset() {
        boolean guard = useTicks > 0;
        attackTicks = 0;
        useTicks = 0;
        return guard ? List.of(MeleeAction.GUARD_UP) : List.of();
    }

    /** Ticks the attack key has been held so far (0 = up); a later HUD may show the thrust charge. */
    public int attackHeldTicks() {
        return attackTicks;
    }

    public boolean guarding() {
        return useTicks > 0;
    }
}

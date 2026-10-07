package com.richardsenger.piratesnships.combat.melee.net;

import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** When a melee state change is worth a {@link MeleeStatePayload}. Pure, no world access. */
public final class MeleeSyncRules {

    private MeleeSyncRules() {
    }

    /**
     * Whether observers (other clients, and the owner's animation layer) need to hear about the change from
     * {@code last} (what was sent last, {@code null} = nothing yet) to {@code now}. The per-tick advance of
     * {@code elapsed} and stamina alone are not relevant: clients extrapolate them.
     */
    public static boolean observerRelevant(@Nullable CombatState last, CombatState now) {
        if (last == null) return true;
        return last.phase() != now.phase()
                || !Objects.equals(last.attack(), now.attack())
                || last.guardHeld() != now.guardHeld()
                || last.riposteAttack() != now.riposteAttack()
                || last.riposteReady() != now.riposteReady()
                || last.lockedOut() != now.lockedOut()
                || last.duration() != now.duration()
                || now.elapsed() < last.elapsed(); // the same phase was entered again
    }

    /**
     * Whether the owner's stamina display needs an update: never when unchanged; at once when it reached empty or
     * full (so the bar ends right); otherwise at most every {@code intervalTicks}.
     */
    public static boolean ownerStaminaDue(float lastSent, long lastSentTick, float stamina, float max, long now, int intervalTicks) {
        if (stamina == lastSent) return false;
        if (stamina <= CombatState.EXHAUSTED_EPSILON || stamina >= max) return true;
        return now - lastSentTick >= intervalTicks;
    }
}

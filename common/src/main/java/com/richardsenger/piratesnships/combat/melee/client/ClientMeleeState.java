package com.richardsenger.piratesnships.combat.melee.client;

import com.richardsenger.piratesnships.combat.melee.net.MeleeStatePayload;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.combat.melee.rules.Refusal;
import org.jetbrains.annotations.Nullable;

/**
 * The local player's melee state as last told by the server, for the HUD. Client thread only. Timers are
 * extrapolated from the client tick the payload arrived on.
 */
public final class ClientMeleeState {

    /** Ticks a refused action flashes the bar. */
    public static final int FLASH_TICKS = 8;

    private static long clientTicks;
    private static @Nullable MeleeStatePayload own;
    private static long receivedAt;
    private static float stamina = -1f;
    private static float maxStamina;
    private static long flashUntil;

    private ClientMeleeState() {
    }

    /** Advanced once per client tick by {@link MeleeInput}. */
    static void tick() {
        clientTicks++;
    }

    public static long now() {
        return clientTicks;
    }

    /** A state payload about the local player. */
    static void acceptOwn(MeleeStatePayload p) {
        own = p;
        receivedAt = clientTicks;
        if (p.hasStamina()) {
            stamina = p.stamina();
            maxStamina = p.maxStamina();
        }
        if (p.refusal() != Refusal.NONE) {
            flashUntil = clientTicks + FLASH_TICKS;
        }
    }

    public static Phase phase() {
        MeleeStatePayload p = own;
        return p == null ? Phase.IDLE : p.phase();
    }

    /** Stamina, or {@code fallbackMax} before the server sent any. */
    public static float stamina(float fallbackMax) {
        return stamina < 0 ? fallbackMax : stamina;
    }

    public static float maxStamina(float fallbackMax) {
        return maxStamina > 0 ? maxStamina : fallbackMax;
    }

    /** Remaining stagger ticks, extrapolated. */
    public static int staggerTicks() {
        MeleeStatePayload p = own;
        return p == null ? 0 : (int) Math.max(0, p.staggerTicks() - (clientTicks - receivedAt));
    }

    /** Stagger length of the current stagger (for a shrinking indicator), 0 if not staggered. */
    public static int staggerDuration() {
        MeleeStatePayload p = own;
        return p == null || p.phase() != Phase.STAGGERED ? 0 : p.duration();
    }

    /** Remaining riposte window, extrapolated. */
    public static int riposteTicks() {
        MeleeStatePayload p = own;
        return p == null ? 0 : (int) Math.max(0, p.riposteTicks() - (clientTicks - receivedAt));
    }

    /** Remaining parry lockout, extrapolated. */
    public static int lockoutTicks() {
        MeleeStatePayload p = own;
        return p == null ? 0 : (int) Math.max(0, p.lockoutTicks() - (clientTicks - receivedAt));
    }

    /** Remaining ticks of the refusal flash. */
    public static int flashTicks() {
        return (int) Math.max(0, flashUntil - clientTicks);
    }

    /** Left the world: forget everything. */
    static void reset() {
        own = null;
        stamina = -1f;
        maxStamina = 0f;
        flashUntil = 0;
    }
}

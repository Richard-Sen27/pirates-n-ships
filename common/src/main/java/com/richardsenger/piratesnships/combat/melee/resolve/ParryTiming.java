package com.richardsenger.piratesnships.combat.melee.resolve;

/**
 * When a parry counts. {@code offset = hitTick - parryStartTick} in server ticks:
 * <ul>
 *   <li>{@code 0 <= offset < window}: the hit landed while the window was open: deflected.</li>
 *   <li>{@code -allowance <= offset < 0}: the parry started up to {@code allowance} ticks after the hit (its input
 *       reached the server late): still deflected. Only possible for hits the server held back for the allowance.</li>
 *   <li>{@code offset >= window}: too early (the window had closed).</li>
 *   <li>{@code offset < -allowance}: too late.</li>
 * </ul>
 */
public enum ParryTiming {
    SUCCESS,
    TOO_EARLY,
    TOO_LATE;

    public static ParryTiming classify(int offset, int window, int allowance) {
        if (offset >= window) return TOO_EARLY;
        if (offset < -Math.max(0, allowance)) return TOO_LATE;
        return SUCCESS;
    }
}

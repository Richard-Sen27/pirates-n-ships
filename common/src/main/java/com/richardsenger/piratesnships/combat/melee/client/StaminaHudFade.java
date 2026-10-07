package com.richardsenger.piratesnships.combat.melee.client;

/**
 * The stamina bar's fade (pure, tested with JUnit; client config {@code melee_hud}). While stamina is full and the
 * player is out of a fight, the bar stays for {@code delay} ticks, then fades linearly to transparent over
 * {@code fade} ticks. It reappears at once (full opacity, counter reset) when stamina drops below full or a fight
 * state begins: guard, an attack, a parry, a stagger, the riposte window, a parry lockout, a refused action or a hit
 * taken.
 */
public final class StaminaHudFade {

    private int fullTicks;

    /** Once per client tick while the bar could show. */
    public void tick(boolean full, boolean fighting) {
        if (!full || fighting) fullTicks = 0;
        else if (fullTicks < Integer.MAX_VALUE) fullTicks++;
    }

    /** The bar was hidden (no sword in hand): start over, so a drawn sword shows the bar first. */
    public void reset() {
        fullTicks = 0;
    }

    public int fullTicks() {
        return fullTicks;
    }

    /**
     * Visibility in 0..1 for this frame. {@code full} and {@code fighting} are the current state, so the bar comes
     * back in the very frame stamina drops, not a tick later.
     */
    public float visibility(boolean full, boolean fighting, float partialTick, int delay, int fade) {
        if (!full || fighting) return 1f;
        float t = fullTicks == 0 ? 0f : fullTicks + partialTick;
        return visibility(t, delay, fade);
    }

    /** The fade curve: 1 up to {@code delay} ticks, then linear to 0 over {@code fade} ticks. */
    public static float visibility(float fullTicks, int delay, int fade) {
        if (fullTicks <= delay) return 1f;
        if (fade <= 0) return 0f;
        return Math.max(0f, Math.min(1f, 1f - (fullTicks - delay) / fade));
    }
}

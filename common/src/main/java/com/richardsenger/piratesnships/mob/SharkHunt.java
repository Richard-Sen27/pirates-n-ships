package com.richardsenger.piratesnships.mob;

/**
 * The shark's hunting rhythm against one target (pure; one {@link #tick} per server tick while it hunts):
 * <ol>
 *   <li><b>circle</b> the prey for {@code circleTicks};</li>
 *   <li><b>charge</b> straight at it until it is within bite reach, then <b>bite</b> (at most once per
 *       {@code biteCooldownTicks}) and go back to circling;</li>
 *   <li><b>blood frenzy</b>: while the prey is below {@code mobs.shark.frenzy_health_fraction} of its health the
 *       shark skips the circling and keeps charging (bites still wait for the cooldown);</li>
 *   <li><b>give up</b> after {@code giveUpTicks} without a bite.</li>
 * </ol>
 */
public final class SharkHunt {

    public enum Phase { CIRCLE, CHARGE }

    public enum Action { CIRCLE, CHARGE, BITE, GIVE_UP }

    /** Config of a hunt ({@code mobs.shark.circle_ticks}, {@code bite_cooldown_ticks}, {@code give_up_ticks}). */
    public record Params(int circleTicks, int biteCooldownTicks, int giveUpTicks) {
    }

    private Phase phase = Phase.CIRCLE;
    private int phaseTicks;
    private int sinceBite;
    private int cooldown;

    public Phase phase() {
        return phase;
    }

    /** Ticks since the hunt started or the last bite landed. */
    public int sinceBite() {
        return sinceBite;
    }

    /** Starts over against a new target: circling, no cooldown, give-up timer at zero. */
    public void reset() {
        phase = Phase.CIRCLE;
        phaseTicks = 0;
        sinceBite = 0;
        cooldown = 0;
    }

    /**
     * Advances one tick and says what the shark does now.
     *
     * @param frenzy  the prey is below the frenzy health fraction
     * @param inReach the prey is within bite reach this tick
     */
    public Action tick(boolean frenzy, boolean inReach, Params p) {
        if (cooldown > 0) cooldown--;
        sinceBite++;
        if (sinceBite > p.giveUpTicks()) return Action.GIVE_UP;
        if (phase == Phase.CIRCLE) {
            phaseTicks++;
            if (!frenzy && phaseTicks < p.circleTicks()) return Action.CIRCLE;
            phase = Phase.CHARGE;
            phaseTicks = 0;
        }
        if (inReach && cooldown == 0) {
            cooldown = p.biteCooldownTicks();
            sinceBite = 0;
            phase = frenzy ? Phase.CHARGE : Phase.CIRCLE;
            return Action.BITE;
        }
        return Action.CHARGE;
    }
}

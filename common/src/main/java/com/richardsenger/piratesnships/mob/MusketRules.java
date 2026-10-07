package com.richardsenger.piratesnships.mob;

/**
 * A navy musketeer's decision per tick (docs/design.md §9: "muskets"), pure. The soldier keeps its distance like a
 * ranged mob: it closes in when the target is out of range or out of sight, backs off when the target is closer than
 * {@code minRange}, and otherwise stands and aims; a loaded musket fires after {@code aimTicks} of steady aim. A target
 * at arm's length ({@code shoveRange}) gets a musket-butt shove instead (no bayonet yet), at most once per shove
 * cooldown. Reloading runs on its own timer ({@code mobs.musket_reload_ticks}) in every state.
 */
public final class MusketRules {

    public enum Move { HOLD, APPROACH, BACK_OFF }

    /** @param aim keep the musket on the target; {@code fire} pull the trigger now; {@code shove} butt-stroke now */
    public record Decision(Move move, boolean aim, boolean fire, boolean shove) {
    }

    /**
     * @param range      farthest shot (blocks)
     * @param minRange   closer than this the soldier backs off
     * @param shoveRange at or closer than this it shoves
     * @param aimTicks   steady aim before a shot
     */
    public record Params(double range, double minRange, double shoveRange, int aimTicks) {
    }

    private MusketRules() {
    }

    /**
     * @param distance    to the target (blocks, centre to centre)
     * @param lineOfSight the soldier can see the target
     * @param loaded      the musket is loaded (and the soldier has a shot left)
     * @param aimedTicks  ticks aimed so far without interruption
     * @param shoveReady  the shove cooldown has run out
     */
    public static Decision decide(double distance, boolean lineOfSight, boolean loaded, int aimedTicks, boolean shoveReady, Params p) {
        if (distance <= p.shoveRange() && shoveReady) return new Decision(Move.BACK_OFF, false, false, true);
        if (distance < p.minRange()) return new Decision(Move.BACK_OFF, false, false, false);
        if (distance > p.range() || !lineOfSight) return new Decision(Move.APPROACH, false, false, false);
        if (!loaded) return new Decision(Move.HOLD, false, false, false);
        return new Decision(Move.HOLD, true, aimedTicks >= p.aimTicks(), false);
    }
}

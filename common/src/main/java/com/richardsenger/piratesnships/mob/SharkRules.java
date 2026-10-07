package com.richardsenger.piratesnships.mob;

/**
 * Pure rules of the shark (work package M4, docs/design.md §9, §12): whom it hunts, whom it keeps hunting and where it
 * may spawn. No world access; {@code mob/entity/Shark} describes entities and positions with the records below.
 * <ul>
 *   <li>It hunts players, villager-like mobs (villagers, traders, the humanoid mobs, crew) and animals <b>in water</b>
 *       within {@code mobs.shark.detection_range}. Never other sharks, creative or spectator players, or anyone
 *       standing on a ship's deck or riding (a boat, a seat).</li>
 *   <li>It retaliates against anything but another shark that hurt it, for {@code mobs.grudge_ticks}, but only while
 *       the attacker is in the water and not on a ship: a target that leaves the water is dropped at once.</li>
 *   <li>{@code mobs.shark.peaceful}: it never hunts and never fights back.</li>
 * </ul>
 */
public final class SharkRules {

    /** Blocks below sea level (the water surface block is at {@code seaLevel - 1}) a natural spawn needs at least. */
    public static final int SPAWN_DEPTH = 3;

    private SharkRules() {
    }

    /**
     * What the shark knows about a living entity.
     *
     * @param shark    another shark
     * @param exempt   creative or spectator player, or invulnerable
     * @param prey     a kind it hunts on sight: player, villager-like mob or animal
     * @param inWater  in the water (feet or eyes in it; {@code isInWater} or {@code isUnderWater})
     * @param onShip   on a ship's deck (Sable tracking) or riding anything (a boat, a seat)
     * @param distance blocks from the shark
     */
    public record Prey(boolean shark, boolean exempt, boolean prey, boolean inWater, boolean onShip, double distance) {
    }

    /** Config as seen by the rules ({@code mobs.shark.peaceful}, {@code mobs.shark.detection_range}). */
    public record Params(boolean peaceful, double detectionRange) {
    }

    /** It can be bitten at all: in the open water, not on a ship or in a boat, not another shark, not exempt. */
    public static boolean reachable(Prey p) {
        return !p.shark() && !p.exempt() && p.inWater() && !p.onShip();
    }

    /** Whether the shark starts hunting {@code p} by itself. */
    public static boolean huntsOnSight(Prey p, Params params) {
        return !params.peaceful() && p.prey() && reachable(p) && p.distance() <= params.detectionRange();
    }

    /** Whether the shark fights back against {@code attacker}, which just hurt it (a grudge, wherever it is now). */
    public static boolean retaliates(Prey attacker, Params params) {
        return !params.peaceful() && !attacker.shark() && !attacker.exempt();
    }

    /**
     * Whether the shark keeps its current target: one it would hunt on sight, or one it holds a grudge against, as
     * long as the target is still reachable (in the water, off any ship) and within {@code followRange}.
     */
    public static boolean keepsTarget(Prey p, Params params, boolean grudge, double followRange) {
        if (params.peaceful() || !reachable(p) || p.distance() > followRange) return false;
        return p.prey() || grudge;
    }

    /** Blood frenzy: the target is below {@code fraction} of its health. */
    public static boolean frenzy(float health, float maxHealth, double fraction) {
        return maxHealth > 0 && health / maxHealth < fraction;
    }

    /**
     * The spawn placement: sharks spawn naturally in water with water above it, at least {@link #SPAWN_DEPTH} blocks
     * below sea level, and only while {@code mobs.shark.enabled}. Light doesn't matter.
     */
    public static boolean canSpawnAt(boolean enabled, boolean water, boolean waterAbove, int y, int seaLevel) {
        return enabled && water && waterAbove && y <= seaLevel - SPAWN_DEPTH;
    }
}

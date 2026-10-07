package com.richardsenger.piratesnships.mob.kraken;

/**
 * The kraken's numbers (docs/design.md §12), pure: weak spots, the grip force, whom it grabs, and how often it appears.
 */
public final class KrakenRules {

    /** Ticks in an in-game day. */
    public static final double DAY_TICKS = 24000.0;

    private KrakenRules() {
    }

    /** Where a hit lands. */
    public enum HitZone { BODY, EYE, TENTACLE }

    /**
     * Damage the kraken's body takes from a hit of {@code amount} on {@code zone}: the body as dealt, an eye times
     * {@code eyeMultiplier}, a tentacle {@code tentacleShare} of it (the tentacle's own pool takes the full amount).
     */
    public static double bodyDamage(HitZone zone, double amount, double eyeMultiplier, double tentacleShare) {
        return switch (zone) {
            case BODY -> amount;
            case EYE -> amount * eyeMultiplier;
            case TENTACLE -> amount * tentacleShare;
        };
    }

    /**
     * The force in newtons of one gripping tentacle on a ship of {@code mass}: {@code gripForce} m/s² times the mass,
     * capped at {@code massCap} (as the H1 hazards: a ship heavier than the cap feels the force of one at the cap, so it
     * barely sinks but lists).
     */
    public static double gripNewtons(double gripForce, double mass, double massCap) {
        if (mass <= 0 || gripForce <= 0) return 0;
        return gripForce * Math.min(mass, Math.max(0, massCap));
    }

    /**
     * The world-frame pull of a grip as {x, y, z}: {@code newtons} straight down plus {@code sideFraction} of it
     * horizontally from the gripped point toward the kraken ({@code toKrakenX}, {@code toKrakenZ}, any length).
     */
    public static double[] gripVector(double newtons, double sideFraction, double toKrakenX, double toKrakenZ) {
        double len = Math.sqrt(toKrakenX * toKrakenX + toKrakenZ * toKrakenZ);
        double side = newtons * sideFraction;
        double sx = len > 1e-6 ? toKrakenX / len * side : 0;
        double sz = len > 1e-6 ? toKrakenZ / len * side : 0;
        return new double[] {sx, -newtons, sz};
    }

    /** What the kraken needs to know about a living thing to grab it out of the water. */
    public record Swimmer(boolean kraken, boolean waterAnimal, boolean exempt, boolean inWater, boolean onShip,
                          boolean passenger) { }

    /**
     * Whether a tentacle may grab {@code s}: someone in the water, not riding or standing on a ship, not a sea creature
     * (sharks, fish, squid, other krakens), not invulnerable, creative or spectator.
     */
    public static boolean grabs(Swimmer s) {
        return !s.kraken() && !s.waterAnimal() && !s.exempt() && s.inWater() && !s.onShip() && !s.passenger();
    }

    /**
     * The chance per in-game day that a kraken appears near a player in the deep ocean: {@code base}, times
     * {@code nightMultiplier} at night and {@code thunderMultiplier} in a thunderstorm, at most 1.
     */
    public static double dayChance(double base, boolean night, boolean thundering, double nightMultiplier, double thunderMultiplier) {
        double c = Math.max(0, base) * (night ? Math.max(0, nightMultiplier) : 1) * (thundering ? Math.max(0, thunderMultiplier) : 1);
        return Math.min(1.0, c);
    }

    /**
     * The chance per spawn check that gives {@code perDay} over a day of checks {@code intervalTicks} apart:
     * {@code 1 - (1 - perDay)^(interval / 24000)}.
     */
    public static double perCheck(double perDay, int intervalTicks) {
        if (perDay <= 0 || intervalTicks <= 0) return 0;
        if (perDay >= 1) return 1;
        return 1 - Math.pow(1 - perDay, intervalTicks / DAY_TICKS);
    }

    /**
     * One spawn roll for a player: {@code roll} uniform in [0, 1). Never when disabled, outside the deep ocean, or with
     * another kraken within {@code minSeparation} ({@code nearestKraken} is the distance to the nearest one, or
     * {@link Double#POSITIVE_INFINITY}).
     */
    public static boolean rollSpawn(boolean enabled, boolean deepOcean, double nearestKraken, double minSeparation,
                                    double chancePerCheck, double roll) {
        return enabled && deepOcean && nearestKraken >= minSeparation && roll < chancePerCheck;
    }
}

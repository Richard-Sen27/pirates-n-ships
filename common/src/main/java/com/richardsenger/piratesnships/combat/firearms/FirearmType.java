package com.richardsenger.piratesnships.combat.firearms;

/**
 * The numbers of one firearm (docs/design.md §8.1), built from config by {@link FirearmsConfig#type(FirearmKind)}.
 * Pure data: no world access.
 *
 * @param damage              damage of one ball hit (before armor)
 * @param muzzleVelocity      start speed of the ball in blocks per tick
 * @param spreadDegrees       half angle of the cone the ball leaves the barrel in
 * @param reloadTicks         ticks the gun has to be held to load it
 * @param misfireChanceInRain chance (0 to 1) that a shot fired in the rain misfires
 * @param soundPitch          pitch of the shot sound (the musket reuses the pistol sound lower)
 */
public record FirearmType(float damage, float muzzleVelocity, float spreadDegrees, int reloadTicks,
                          double misfireChanceInRain, float soundPitch) {

    public FirearmType {
        if (damage < 0) throw new IllegalArgumentException("damage must be >= 0: " + damage);
        if (muzzleVelocity <= 0) throw new IllegalArgumentException("muzzleVelocity must be > 0: " + muzzleVelocity);
        if (spreadDegrees < 0) throw new IllegalArgumentException("spreadDegrees must be >= 0: " + spreadDegrees);
        if (reloadTicks < 1) throw new IllegalArgumentException("reloadTicks must be >= 1: " + reloadTicks);
        if (misfireChanceInRain < 0 || misfireChanceInRain > 1) {
            throw new IllegalArgumentException("misfireChanceInRain must be in [0, 1]: " + misfireChanceInRain);
        }
        if (soundPitch <= 0) throw new IllegalArgumentException("soundPitch must be > 0: " + soundPitch);
    }

    /** The same gun with another rain misfire chance. */
    public FirearmType withMisfireChance(double chance) {
        return new FirearmType(damage, muzzleVelocity, spreadDegrees, reloadTicks, chance, soundPitch);
    }

    /** The same gun with its damage multiplied (the {@code combat.damage_multipliers.firearm} value). */
    public FirearmType withDamageMultiplier(double multiplier) {
        return new FirearmType((float) (damage * multiplier), muzzleVelocity, spreadDegrees, reloadTicks, misfireChanceInRain, soundPitch);
    }
}

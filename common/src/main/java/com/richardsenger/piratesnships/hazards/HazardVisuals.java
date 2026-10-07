package com.richardsenger.piratesnships.hazards;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * The look and sound of the hazards, run from the entity's client tick (the entity renderer draws nothing). Only vanilla
 * {@link Level} calls, which do nothing on a server level, so this class is safe in {@code common}. Particles go
 * through {@code addAlwaysVisibleParticle(…, ignoreRange = true, …)} so a waterspout 48–96 blocks away is still seen
 * (plain particles are culled beyond 32 blocks); the particle setting (all / decreased / minimal) still applies.
 *
 * <ul>
 * <li>Waterspout: a rotating funnel of {@code cloud} particles that widens toward {@code funnel_height}, with a ring
 *     of {@code splash} spray at its foot; a low rain roar ({@code weather.rain.above} at pitch 0.5) every second.</li>
 * <li>Whirlpool: a flat ring of {@code splash} particles turning counter-clockwise (faster toward the centre),
 *     {@code bubble}s under the surface and a dark centre of {@code squid_ink}; the vanilla bubble column whirlpool
 *     sound every two seconds.</li>
 * </ul>
 * Density is the client config {@code hazard_visuals.particle_density}.
 */
public final class HazardVisuals {

    private static final int SPOUT_PARTICLES = 14;
    private static final int POOL_PARTICLES = 12;

    private HazardVisuals() {
    }

    static void tick(HazardEntity e) {
        Level level = e.level();
        double density = HazardsConfig.PARTICLE_DENSITY.get();
        if (e instanceof WaterspoutEntity) {
            spout(level, e, density);
            if (HazardsConfig.HAZARD_SOUNDS.get() && e.tickCount % 20 == 0) {
                level.playLocalSound(e.getX(), e.getY() + 3, e.getZ(), SoundEvents.WEATHER_RAIN_ABOVE, SoundSource.WEATHER, 3.0f,
                        0.45f + e.getRandom().nextFloat() * 0.1f, false);
            }
        } else if (e instanceof WhirlpoolEntity) {
            pool(level, e, density);
            if (HazardsConfig.HAZARD_SOUNDS.get() && e.tickCount % 40 == 0) {
                level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, SoundSource.WEATHER, 3.0f,
                        0.6f + e.getRandom().nextFloat() * 0.1f, false);
            }
        }
    }

    /** How many particles to spawn this tick: {@code base × density}, the fraction rounded at random. */
    static int count(double base, double density, double roll) {
        double n = Math.max(0, base * density);
        int whole = (int) Math.floor(n);
        return whole + (roll < n - whole ? 1 : 0);
    }

    private static void spout(Level level, HazardEntity e, double density) {
        RandomSource r = e.getRandom();
        double radius = Math.max(1, e.radius());
        double height = Math.max(4, e.height());
        int n = count(SPOUT_PARTICLES, density, r.nextDouble());
        for (int i = 0; i < n; i++) {
            double t = r.nextDouble();
            double h = t * t * height * 0.6 + t * height * 0.4; // denser near the water
            double frac = h / height;
            double rf = 0.6 + radius * 0.35 * frac * frac;
            double a = r.nextDouble() * Math.PI * 2;
            double cos = Math.cos(a), sin = Math.sin(a);
            double speed = 0.25 + 0.2 * frac;
            // counter-clockwise tangent (sin, -cos), a little inward and upward
            add(level, h < 1.5 ? ParticleTypes.SPLASH : ParticleTypes.CLOUD,
                    e.getX() + cos * rf, e.getY() + h, e.getZ() + sin * rf,
                    sin * speed - cos * 0.03, 0.06 + 0.05 * r.nextDouble(), -cos * speed - sin * 0.03);
        }
        int spray = count(SPOUT_PARTICLES / 2.0, density, r.nextDouble());
        for (int i = 0; i < spray; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double rr = 1.0 + r.nextDouble() * 2.5;
            add(level, ParticleTypes.SPLASH, e.getX() + Math.cos(a) * rr, e.getY() + 0.2, e.getZ() + Math.sin(a) * rr,
                    Math.sin(a) * 0.3, 0, -Math.cos(a) * 0.3);
        }
    }

    private static void pool(Level level, HazardEntity e, double density) {
        RandomSource r = e.getRandom();
        double radius = Math.max(1, e.radius());
        int n = count(POOL_PARTICLES, density, r.nextDouble());
        for (int i = 0; i < n; i++) {
            double rr = radius * Math.sqrt(r.nextDouble());
            double a = r.nextDouble() * Math.PI * 2;
            double cos = Math.cos(a), sin = Math.sin(a);
            double speed = 0.12 + 0.3 * (1 - rr / radius);
            double x = e.getX() + cos * rr, z = e.getZ() + sin * rr;
            add(level, ParticleTypes.SPLASH, x, e.getY() + 0.05, z, sin * speed - cos * 0.04, 0, -cos * speed - sin * 0.04);
            if (rr < radius * 0.6 && r.nextInt(2) == 0) {
                add(level, ParticleTypes.BUBBLE, x, e.getY() - 0.4 - r.nextDouble(), z, sin * speed, -0.05, -cos * speed);
            }
        }
        int ink = count(POOL_PARTICLES / 3.0, density, r.nextDouble());
        for (int i = 0; i < ink; i++) {
            double rr = radius / 6 * Math.sqrt(r.nextDouble());
            double a = r.nextDouble() * Math.PI * 2;
            add(level, ParticleTypes.SQUID_INK, e.getX() + Math.cos(a) * rr, e.getY() - 0.1, e.getZ() + Math.sin(a) * rr,
                    Math.sin(a) * 0.05, -0.02, -Math.cos(a) * 0.05);
        }
    }

    private static void add(Level level, ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
        level.addAlwaysVisibleParticle(type, true, x, y, z, vx, vy, vz);
    }
}

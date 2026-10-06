package com.richardsenger.piratesnships.law.crime;

import java.util.EnumMap;
import java.util.Map;

/**
 * Plain parameters of the criminal score rules. Filled from config by {@code LawConfig.crimeRules()}; tests build
 * their own with {@link #defaults()} and the {@code with...} methods.
 *
 * @param enabled             master toggle; disabled = no crime is recorded and nobody is wanted
 * @param severity            points per crime
 * @param cooldownTicks       repeat window per crime type (same type + same victim inside it is not counted again)
 * @param decayPerDay         points lost per in-game day (24000 ticks) once decay has started
 * @param decayDelayTicks     no decay until this long after the last counted crime (no cooling off mid-fight)
 * @param maxScore            score cap
 * @param suspectThreshold    score from which an entity is {@link WantedLevel#SUSPECT}
 * @param wantedThreshold     score from which an entity is {@link WantedLevel#WANTED}; also the navy bounty threshold
 * @param notoriousThreshold  score from which an entity is {@link WantedLevel#NOTORIOUS}
 * @param fineCostPerPoint    doubloons per point when paying a fine
 * @param finesWhenNotorious  whether notorious criminals may pay fines at all
 */
public record CrimeRules(boolean enabled, Map<CrimeType, Integer> severity, Map<CrimeType, Long> cooldownTicks,
                         double decayPerDay, long decayDelayTicks, double maxScore,
                         double suspectThreshold, double wantedThreshold, double notoriousThreshold,
                         int fineCostPerPoint, boolean finesWhenNotorious) {

    public static final long TICKS_PER_DAY = 24000L;

    public CrimeRules {
        severity = Map.copyOf(severity);
        cooldownTicks = Map.copyOf(cooldownTicks);
        if (fineCostPerPoint < 1) throw new IllegalArgumentException("fineCostPerPoint must be >= 1");
        if (!(suspectThreshold <= wantedThreshold && wantedThreshold <= notoriousThreshold)) {
            throw new IllegalArgumentException("thresholds must be ordered suspect <= wanted <= notorious");
        }
    }

    /** The built-in defaults (same numbers as the config defaults). */
    public static CrimeRules defaults() {
        Map<CrimeType, Integer> sev = new EnumMap<>(CrimeType.class);
        Map<CrimeType, Long> cd = new EnumMap<>(CrimeType.class);
        for (CrimeType t : CrimeType.values()) {
            sev.put(t, t.defaultSeverity());
            cd.put(t, t.defaultCooldownSeconds() * 20L);
        }
        return new CrimeRules(true, sev, cd, 10.0, 6000L, 1000.0, 10.0, 50.0, 200.0, 3, false);
    }

    public int severityOf(CrimeType type) {
        return severity.getOrDefault(type, type.defaultSeverity());
    }

    public long cooldownOf(CrimeType type) {
        return cooldownTicks.getOrDefault(type, type.defaultCooldownSeconds() * 20L);
    }

    /** Points lost per tick of decay. */
    public double decayPerTick() {
        return decayPerDay / TICKS_PER_DAY;
    }

    public WantedLevel wantedLevel(double score) {
        if (!enabled) return WantedLevel.CLEAN;
        return WantedLevel.of(score, suspectThreshold, wantedThreshold, notoriousThreshold);
    }

    public CrimeRules withEnabled(boolean value) {
        return new CrimeRules(value, severity, cooldownTicks, decayPerDay, decayDelayTicks, maxScore,
                suspectThreshold, wantedThreshold, notoriousThreshold, fineCostPerPoint, finesWhenNotorious);
    }

    public CrimeRules withDecay(double perDay, long delayTicks) {
        return new CrimeRules(enabled, severity, cooldownTicks, perDay, delayTicks, maxScore,
                suspectThreshold, wantedThreshold, notoriousThreshold, fineCostPerPoint, finesWhenNotorious);
    }

    public CrimeRules withSeverity(CrimeType type, int points) {
        Map<CrimeType, Integer> sev = new EnumMap<>(CrimeType.class);
        sev.putAll(severity);
        sev.put(type, points);
        return new CrimeRules(enabled, sev, cooldownTicks, decayPerDay, decayDelayTicks, maxScore,
                suspectThreshold, wantedThreshold, notoriousThreshold, fineCostPerPoint, finesWhenNotorious);
    }

    public CrimeRules withFines(int costPerPoint, boolean whenNotorious) {
        return new CrimeRules(enabled, severity, cooldownTicks, decayPerDay, decayDelayTicks, maxScore,
                suspectThreshold, wantedThreshold, notoriousThreshold, costPerPoint, whenNotorious);
    }
}

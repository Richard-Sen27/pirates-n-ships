package com.richardsenger.piratesnships.law.crime;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The immutable criminal record of one player or NPC (docs/design.md §13.1). All operations are pure and return a
 * new record; {@code now} is the server game time in ticks.
 *
 * <p><b>Decay</b> is linear: {@code decayPerDay} points per 24000 ticks, starting {@code decayDelayTicks} after the
 * last counted crime, never below zero. Because it only depends on the elapsed time since {@link #lastUpdate}, one
 * decay over a long (offline) gap gives the same result as many small decays, so it doesn't matter how often it is
 * called.
 *
 * <p><b>Repeat handling:</b> a crime of the same type against the same victim (or, without a victim, of the same type
 * at all) inside that type's cooldown window is not counted again. The window starts at the counted crime and is not
 * extended by ignored repeats, so a fight that lasts longer than the window counts once per window.
 *
 * @param score       current score (fractional because of continuous decay; display it rounded up)
 * @param lastUpdate  game time the score was last brought up to date
 * @param lastCrime   game time of the last counted crime ({@link Long#MIN_VALUE} if none)
 * @param totalCrimes number of crimes ever counted
 * @param recent      counted offences still inside their cooldown window (bounded list)
 */
public record CriminalRecord(double score, long lastUpdate, long lastCrime, int totalCrimes, List<Offence> recent) {

    /** Upper bound of {@link #recent}, so the saved data stays small. */
    public static final int MAX_RECENT = 64;

    public static final CriminalRecord EMPTY = new CriminalRecord(0.0, 0L, Long.MIN_VALUE, 0, List.of());

    public static final Codec<CriminalRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("score", 0.0).forGetter(CriminalRecord::score),
            Codec.LONG.optionalFieldOf("last_update", 0L).forGetter(CriminalRecord::lastUpdate),
            Codec.LONG.optionalFieldOf("last_crime", Long.MIN_VALUE).forGetter(CriminalRecord::lastCrime),
            Codec.INT.optionalFieldOf("total_crimes", 0).forGetter(CriminalRecord::totalCrimes),
            Offence.CODEC.listOf().optionalFieldOf("recent", List.of()).forGetter(CriminalRecord::recent)
    ).apply(i, CriminalRecord::new));

    public CriminalRecord {
        if (Double.isNaN(score) || score < 0) score = 0.0;
        recent = List.copyOf(recent);
    }

    /** One counted crime, kept while its repeat window is open. */
    public record Offence(CrimeType type, Optional<UUID> victim, long time) {
        public static final Codec<Offence> CODEC = RecordCodecBuilder.create(i -> i.group(
                CrimeType.CODEC.fieldOf("type").forGetter(Offence::type),
                UUIDUtil.CODEC.optionalFieldOf("victim").forGetter(Offence::victim),
                Codec.LONG.fieldOf("time").forGetter(Offence::time)
        ).apply(i, Offence::new));
    }

    /** Outcome of {@link #addCrime}. */
    public enum CrimeOutcome {
        /** The crime was counted. */
        COUNTED,
        /** Same crime against the same victim inside the repeat window. */
        REPEAT_IGNORED,
        /** The criminal score is disabled in config. */
        DISABLED,
        /** The crime's severity is configured as 0. */
        NO_SEVERITY
    }

    /** Result of {@link #addCrime}: the new record and how many points were added. */
    public record CrimeResult(CriminalRecord record, double pointsAdded, CrimeOutcome outcome) {
        public boolean counted() {
            return outcome == CrimeOutcome.COUNTED;
        }
    }

    /** Outcome of {@link #payFine}. */
    public enum FineOutcome { PAID_IN_FULL, PARTIAL, NOTHING_OFFERED, NOTHING_OWED, REFUSED_NOTORIOUS }

    /** Result of {@link #payFine}: the new record, the doubloons actually taken and the points removed. */
    public record FineResult(CriminalRecord record, int doubloonsSpent, double pointsRemoved, FineOutcome outcome) {
    }

    /** The score brought up to {@code now} (decay applied). */
    public CriminalRecord decayTo(long now, CrimeRules rules) {
        if (now <= lastUpdate) {
            // Clock did not move (or moved backwards, e.g. a different save): never decay, never "un-decay".
            return now == lastUpdate ? this : new CriminalRecord(score, now, lastCrime, totalCrimes, recent);
        }
        long decayStart = lastUpdate;
        if (lastCrime != Long.MIN_VALUE) {
            decayStart = Math.max(decayStart, saturatedAdd(lastCrime, rules.decayDelayTicks()));
        }
        double newScore = score;
        if (now > decayStart && score > 0) {
            newScore = Math.max(0.0, score - (now - decayStart) * rules.decayPerTick());
        }
        return new CriminalRecord(newScore, now, lastCrime, totalCrimes, prune(recent, now, rules));
    }

    /**
     * Records a crime at {@code now}. Decays first. {@code victim} identifies the victim for repeat handling
     * ({@code null} = repeats are grouped per crime type only).
     */
    public CrimeResult addCrime(CrimeType type, @Nullable UUID victim, long now, CrimeRules rules) {
        if (!rules.enabled()) return new CrimeResult(this, 0, CrimeOutcome.DISABLED);
        CriminalRecord base = decayTo(now, rules);
        int severity = rules.severityOf(type);
        if (severity <= 0) return new CrimeResult(base, 0, CrimeOutcome.NO_SEVERITY);
        Optional<UUID> v = Optional.ofNullable(victim);
        long cooldown = rules.cooldownOf(type);
        for (Offence o : base.recent) {
            if (o.type() == type && o.victim().equals(v) && now - o.time() < cooldown) {
                return new CrimeResult(base, 0, CrimeOutcome.REPEAT_IGNORED);
            }
        }
        double newScore = Math.min(rules.maxScore(), base.score + severity);
        List<Offence> list = new ArrayList<>(base.recent);
        if (cooldown > 0) list.add(new Offence(type, v, now));
        CriminalRecord next = new CriminalRecord(newScore, now, now, base.totalCrimes + 1, cap(list));
        return new CrimeResult(next, newScore - base.score, CrimeOutcome.COUNTED);
    }

    /**
     * Pays a fine of up to {@code offeredDoubloons} at {@code now} (decays first). Paying the full cost (score times
     * cost per point, rounded up) clears the score; less removes {@code offered / costPerPoint} points.
     */
    public FineResult payFine(int offeredDoubloons, long now, CrimeRules rules) {
        CriminalRecord base = decayTo(now, rules);
        if (!rules.enabled() || base.score <= 0) return new FineResult(base, 0, 0, FineOutcome.NOTHING_OWED);
        if (!rules.finesWhenNotorious() && rules.wantedLevel(base.score) == WantedLevel.NOTORIOUS) {
            return new FineResult(base, 0, 0, FineOutcome.REFUSED_NOTORIOUS);
        }
        if (offeredDoubloons <= 0) return new FineResult(base, 0, 0, FineOutcome.NOTHING_OFFERED);
        int fullCost = base.fineCost(rules);
        if (offeredDoubloons >= fullCost) {
            return new FineResult(base.withScore(0.0), fullCost, base.score, FineOutcome.PAID_IN_FULL);
        }
        double removed = (double) offeredDoubloons / rules.fineCostPerPoint();
        return new FineResult(base.withScore(base.score - removed), offeredDoubloons, removed, FineOutcome.PARTIAL);
    }

    /** Doubloons needed to clear the current score (without decaying first). */
    public int fineCost(CrimeRules rules) {
        double raw = score * rules.fineCostPerPoint();
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(raw - 1e-9));
    }

    /** Same record with another score (clamped to [0, max]); for commands, pardons and claims. */
    public CriminalRecord withScore(double value) {
        return new CriminalRecord(Math.max(0.0, value), lastUpdate, lastCrime, totalCrimes, recent);
    }

    /** Sets the score at {@code now} (decay state is brought up to date first, clamped to the rules' cap). */
    public CriminalRecord setScore(double value, long now, CrimeRules rules) {
        return decayTo(now, rules).withScore(Math.min(rules.maxScore(), value));
    }

    /** The score as shown to players: rounded up, so any remaining fraction still shows as 1. */
    public int displayScore() {
        return (int) Math.ceil(score - 1e-9);
    }

    private static List<Offence> prune(List<Offence> list, long now, CrimeRules rules) {
        if (list.isEmpty()) return list;
        List<Offence> out = new ArrayList<>(list.size());
        for (Offence o : list) {
            if (now - o.time() < rules.cooldownOf(o.type())) out.add(o);
        }
        return out.size() == list.size() ? list : out;
    }

    private static List<Offence> cap(List<Offence> list) {
        if (list.size() <= MAX_RECENT) return list;
        list.sort(Comparator.comparingLong(Offence::time));
        return list.subList(list.size() - MAX_RECENT, list.size());
    }

    private static long saturatedAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r;
    }
}

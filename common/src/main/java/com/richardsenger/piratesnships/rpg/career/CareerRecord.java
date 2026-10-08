package com.richardsenger.piratesnships.rpg.career;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A player's career (docs/design.md §15, CAR1): the navy rank and whether the player serves, the infamy rank, the
 * letter of marque (and until when a voided one blocks a new one, in game ticks), the deed counters and the prize money
 * waiting at a navy officer. Immutable; stored as the {@code pirates_n_ships:career} attachment.
 *
 * <p>Invariant: {@code enlisted} ⇔ {@code navy != NONE}; the constructor enforces it (a rank without service is
 * dropped). Counters are never negative.
 */
public record CareerRecord(NavyRank navy, boolean enlisted, InfamyRank infamy, LetterState letter, long letterBlockedUntil,
                           Map<CareerCounter, Long> counters, long prizeMoney) {

    public static final CareerRecord EMPTY = new CareerRecord(NavyRank.NONE, false, InfamyRank.DECKHAND, LetterState.NONE, 0L,
            Map.of(), 0L);

    /** Counters are saved by id; unknown ids (a newer save) are skipped, zero counters are not written. */
    private static final Codec<Map<CareerCounter, Long>> COUNTERS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG).xmap(
            m -> {
                Map<CareerCounter, Long> out = new EnumMap<>(CareerCounter.class);
                m.forEach((k, v) -> CareerCounter.byId(k).ifPresent(c -> out.put(c, v)));
                return out;
            },
            m -> {
                Map<String, Long> out = new LinkedHashMap<>();
                m.forEach((c, v) -> {
                    if (v != 0) out.put(c.id(), v);
                });
                return out;
            });

    public static final Codec<CareerRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            NavyRank.CODEC.optionalFieldOf("navy", NavyRank.NONE).forGetter(CareerRecord::navy),
            Codec.BOOL.optionalFieldOf("enlisted", false).forGetter(CareerRecord::enlisted),
            InfamyRank.CODEC.optionalFieldOf("infamy", InfamyRank.DECKHAND).forGetter(CareerRecord::infamy),
            LetterState.CODEC.optionalFieldOf("letter", LetterState.NONE).forGetter(CareerRecord::letter),
            Codec.LONG.optionalFieldOf("letter_blocked_until", 0L).forGetter(CareerRecord::letterBlockedUntil),
            COUNTERS_CODEC.optionalFieldOf("counters", Map.of()).forGetter(CareerRecord::counters),
            Codec.LONG.optionalFieldOf("prize_money", 0L).forGetter(CareerRecord::prizeMoney)
    ).apply(i, CareerRecord::new));

    public CareerRecord {
        if (navy == null) navy = NavyRank.NONE;
        if (infamy == null) infamy = InfamyRank.DECKHAND;
        if (letter == null) letter = LetterState.NONE;
        enlisted = enlisted && navy != NavyRank.NONE;
        if (!enlisted) navy = NavyRank.NONE;
        Map<CareerCounter, Long> copy = new EnumMap<>(CareerCounter.class);
        if (counters != null) counters.forEach((c, v) -> {
            if (c != null && v != null && v > 0) copy.put(c, v);
        });
        counters = Collections.unmodifiableMap(copy);
        prizeMoney = Math.max(0L, prizeMoney);
    }

    public long count(CareerCounter counter) {
        return counters.getOrDefault(counter, 0L);
    }

    /** {@code amount} added to {@code counter} (saturating, never below 0). */
    public CareerRecord plus(CareerCounter counter, long amount) {
        if (amount == 0) return this;
        Map<CareerCounter, Long> m = new EnumMap<>(CareerCounter.class);
        m.putAll(counters);
        long v = count(counter);
        long sum = amount > 0 && v > Long.MAX_VALUE - amount ? Long.MAX_VALUE : Math.max(0L, v + amount);
        m.put(counter, sum);
        return new CareerRecord(navy, enlisted, infamy, letter, letterBlockedUntil, m, prizeMoney);
    }

    /** All of {@code amounts} added. */
    public CareerRecord plus(Map<CareerCounter, Long> amounts) {
        CareerRecord r = this;
        for (Map.Entry<CareerCounter, Long> e : amounts.entrySet()) r = r.plus(e.getKey(), e.getValue());
        return r;
    }

    /** In the navy at {@code rank} ({@code NONE} = out of the navy). */
    public CareerRecord withNavy(NavyRank rank) {
        return new CareerRecord(rank, rank != NavyRank.NONE, infamy, letter, letterBlockedUntil, counters, prizeMoney);
    }

    public CareerRecord withInfamy(InfamyRank rank) {
        return new CareerRecord(navy, enlisted, rank, letter, letterBlockedUntil, counters, prizeMoney);
    }

    public CareerRecord withLetter(LetterState state, long blockedUntil) {
        return new CareerRecord(navy, enlisted, infamy, state, blockedUntil, counters, prizeMoney);
    }

    public CareerRecord withPrize(long prize) {
        return new CareerRecord(navy, enlisted, infamy, letter, letterBlockedUntil, counters, prize);
    }

    /** Prize money added (saturating). */
    public CareerRecord plusPrize(long amount) {
        long sum = amount > 0 && prizeMoney > Long.MAX_VALUE - amount ? Long.MAX_VALUE : prizeMoney + amount;
        return withPrize(sum);
    }
}

package com.richardsenger.piratesnships.rpg.reputation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

/**
 * A player's reputation (docs/design.md §15): one score per {@link Faction} in −100..100, the game time the scores were
 * last brought up to date ({@link #decayTo}), and the counter that caps village trade deeds per in-game day. Immutable;
 * stored as the {@code pirates_n_ships:reputation} attachment.
 *
 * @param tradeDay   the in-game day {@code tradeDeeds} counts for
 * @param tradeDeeds village trade deeds already counted on {@code tradeDay}
 */
public record ReputationRecord(double navy, double pirates, double villagers, long lastUpdate, long tradeDay, int tradeDeeds) {

    public static final ReputationRecord EMPTY = new ReputationRecord(0, 0, 0, 0L, -1L, 0);

    public static final Codec<ReputationRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("navy", 0.0).forGetter(ReputationRecord::navy),
            Codec.DOUBLE.optionalFieldOf("pirates", 0.0).forGetter(ReputationRecord::pirates),
            Codec.DOUBLE.optionalFieldOf("villagers", 0.0).forGetter(ReputationRecord::villagers),
            Codec.LONG.optionalFieldOf("last_update", 0L).forGetter(ReputationRecord::lastUpdate),
            Codec.LONG.optionalFieldOf("trade_day", -1L).forGetter(ReputationRecord::tradeDay),
            Codec.INT.optionalFieldOf("trade_deeds", 0).forGetter(ReputationRecord::tradeDeeds)
    ).apply(i, ReputationRecord::new));

    public ReputationRecord {
        navy = ReputationRules.clamp(navy);
        pirates = ReputationRules.clamp(pirates);
        villagers = ReputationRules.clamp(villagers);
        tradeDeeds = Math.max(0, tradeDeeds);
    }

    public double get(Faction faction) {
        return switch (faction) {
            case NAVY -> navy;
            case PIRATES -> pirates;
            case VILLAGERS -> villagers;
        };
    }

    /** The whole score shown to players and compared with the thresholds. */
    public int display(Faction faction) {
        return ReputationRules.display(get(faction));
    }

    /** The score of {@code faction} set to {@code value} (clamped). */
    public ReputationRecord with(Faction faction, double value) {
        return switch (faction) {
            case NAVY -> new ReputationRecord(value, pirates, villagers, lastUpdate, tradeDay, tradeDeeds);
            case PIRATES -> new ReputationRecord(navy, value, villagers, lastUpdate, tradeDay, tradeDeeds);
            case VILLAGERS -> new ReputationRecord(navy, pirates, value, lastUpdate, tradeDay, tradeDeeds);
        };
    }

    /** {@code delta} added to the score of {@code faction}, clamped to −100..100. */
    public ReputationRecord plus(Faction faction, double delta) {
        return with(faction, get(faction) + delta);
    }

    /**
     * Every score decayed toward 0 by {@code perDay} per in-game day from {@link #lastUpdate} to {@code now}. A clock that
     * did not move (or moved backwards: another save) changes nothing.
     */
    public ReputationRecord decayTo(long now, double perDay) {
        if (now <= lastUpdate) return this;
        long ticks = now - lastUpdate;
        return new ReputationRecord(ReputationRules.decay(navy, ticks, perDay), ReputationRules.decay(pirates, ticks, perDay),
                ReputationRules.decay(villagers, ticks, perDay), now, tradeDay, tradeDeeds);
    }

    /**
     * Counts one village trade deed on {@code day} if fewer than {@code cap} were counted that day; {@code null} once the
     * cap is reached.
     */
    public @Nullable ReputationRecord countTradeDeed(long day, int cap) {
        int used = day == tradeDay ? tradeDeeds : 0;
        if (used >= cap) return null;
        return new ReputationRecord(navy, pirates, villagers, lastUpdate, day, used + 1);
    }
}

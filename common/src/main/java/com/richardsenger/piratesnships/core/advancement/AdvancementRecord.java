package com.richardsenger.piratesnships.core.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the advancement tab remembers per player (ADV1), immutable: the advancements whose experience was paid, the
 * doubloons earned in all, the Almanac chapters read, and per ship the days its crew served without a deserter.
 */
public record AdvancementRecord(List<String> rewarded, long doubloons, List<String> chapters, Map<String, Integer> loyalDays) {

    public static final AdvancementRecord EMPTY = new AdvancementRecord(List.of(), 0L, List.of(), Map.of());

    public static final Codec<AdvancementRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("rewarded", List.of()).forGetter(AdvancementRecord::rewarded),
            Codec.LONG.optionalFieldOf("doubloons", 0L).forGetter(AdvancementRecord::doubloons),
            Codec.STRING.listOf().optionalFieldOf("chapters", List.of()).forGetter(AdvancementRecord::chapters),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("loyal_days", Map.of()).forGetter(AdvancementRecord::loyalDays)
    ).apply(i, AdvancementRecord::new));

    public AdvancementRecord {
        rewarded = List.copyOf(rewarded);
        chapters = List.copyOf(chapters);
        loyalDays = Map.copyOf(loyalDays);
    }

    public AdvancementRecord withRewarded(String id) {
        if (rewarded.contains(id)) return this;
        List<String> next = new ArrayList<>(rewarded);
        next.add(id);
        return new AdvancementRecord(next, doubloons, chapters, loyalDays);
    }

    /** Adds {@code amount} earned doubloons (saturating). */
    public AdvancementRecord earned(long amount) {
        long next = amount > 0 && doubloons > Long.MAX_VALUE - amount ? Long.MAX_VALUE : doubloons + Math.max(0, amount);
        return new AdvancementRecord(rewarded, next, chapters, loyalDays);
    }

    public AdvancementRecord withChapter(String chapter) {
        if (chapters.contains(chapter)) return this;
        Set<String> next = new LinkedHashSet<>(chapters);
        next.add(chapter);
        return new AdvancementRecord(rewarded, doubloons, new ArrayList<>(next), loyalDays);
    }

    /**
     * Pure: one dawn of a ship's crew. A day without a deserter adds one to its count, a desertion starts it over.
     */
    public AdvancementRecord crewDay(String ship, boolean deserted) {
        Map<String, Integer> next = new HashMap<>(loyalDays);
        next.put(ship, deserted ? 0 : next.getOrDefault(ship, 0) + 1);
        return new AdvancementRecord(rewarded, doubloons, chapters, next);
    }

    public int loyalDays(String ship) {
        return loyalDays.getOrDefault(ship, 0);
    }
}

package com.richardsenger.piratesnships.rpg.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * A player's quest log (the {@link QuestAttachments#LOG} attachment): the active quests (at most
 * {@code quests.max_active}, checked on accepting), how many of each type were completed, and how many failed or were
 * abandoned. Immutable.
 */
public record QuestLog(List<Quest> active, Map<QuestType, Integer> completed, int failed) {

    public static final QuestLog EMPTY = new QuestLog(List.of(), Map.of(), 0);

    public static final Codec<QuestLog> CODEC = RecordCodecBuilder.create(i -> i.group(
            Quest.CODEC.listOf().optionalFieldOf("active", List.of()).forGetter(QuestLog::active),
            Codec.unboundedMap(QuestType.CODEC, Codec.INT).optionalFieldOf("completed", Map.of()).forGetter(QuestLog::completed),
            Codec.INT.optionalFieldOf("failed", 0).forGetter(QuestLog::failed)
    ).apply(i, QuestLog::new));

    public QuestLog {
        active = List.copyOf(active);
        Map<QuestType, Integer> c = new EnumMap<>(QuestType.class);
        c.putAll(completed);
        completed = Collections.unmodifiableMap(c);
        failed = Math.max(0, failed);
    }

    public Optional<Quest> find(UUID id) {
        return active.stream().filter(q -> q.id().equals(id)).findFirst();
    }

    /** The active quest whose id starts with {@code prefix} (the short id); empty if none or ambiguous. */
    public Optional<Quest> findByPrefix(String prefix) {
        List<Quest> hits = active.stream().filter(q -> q.id().toString().startsWith(prefix)).toList();
        return hits.size() == 1 ? Optional.of(hits.get(0)) : Optional.empty();
    }

    public QuestLog with(Quest quest) {
        List<Quest> a = new ArrayList<>(active);
        a.removeIf(q -> q.id().equals(quest.id()));
        a.add(quest);
        return new QuestLog(a, completed, failed);
    }

    public QuestLog without(UUID id) {
        List<Quest> a = new ArrayList<>(active);
        a.removeIf(q -> q.id().equals(id));
        return new QuestLog(a, completed, failed);
    }

    /** Every active quest through {@code f}. */
    public QuestLog map(UnaryOperator<Quest> f) {
        return new QuestLog(active.stream().map(f).toList(), completed, failed);
    }

    /** Removes finished quests and counts them (DONE per type, FAILED in {@link #failed}). */
    public QuestLog settle() {
        List<Quest> a = new ArrayList<>();
        Map<QuestType, Integer> c = new EnumMap<>(QuestType.class);
        c.putAll(completed);
        int f = failed;
        for (Quest q : active) {
            if (q.state() == QuestState.DONE) c.merge(q.type(), 1, Integer::sum);
            else if (q.state() == QuestState.FAILED) f++;
            else a.add(q);
        }
        return new QuestLog(a, c, f);
    }

    public int completed(QuestType type) {
        return completed.getOrDefault(type, 0);
    }

    public int completedTotal() {
        return completed.values().stream().mapToInt(Integer::intValue).sum();
    }
}

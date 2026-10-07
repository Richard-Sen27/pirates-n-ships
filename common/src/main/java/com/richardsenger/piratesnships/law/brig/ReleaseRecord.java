package com.richardsenger.piratesnships.law.brig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.law.flag.Faction;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Prisoners a player set free (LA2, docs/design.md §13.3 "Release them"), counted per faction of the released mob, for
 * the reputation of §15 later: no reputation value is derived yet. Immutable; saved on the player
 * ({@code LawAttachments.RELEASES}, written only through {@code LawService.recordRelease}).
 *
 * @param byFaction releases of navy, pirate and merchant (sailors, villagers, traders) prisoners
 * @param total     every release, including mobs of no faction (a pillager) and players
 */
public record ReleaseRecord(Map<Faction, Integer> byFaction, int total) {

    public static final ReleaseRecord EMPTY = new ReleaseRecord(Map.of(), 0);

    private static final Codec<Faction> FACTION_CODEC = Codec.STRING.xmap(
            s -> Faction.valueOf(s.toUpperCase(Locale.ROOT)), f -> f.name().toLowerCase(Locale.ROOT));

    public static final Codec<ReleaseRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(FACTION_CODEC, Codec.INT).optionalFieldOf("by_faction", Map.of()).forGetter(ReleaseRecord::byFaction),
            Codec.INT.optionalFieldOf("total", 0).forGetter(ReleaseRecord::total)
    ).apply(i, ReleaseRecord::new));

    public ReleaseRecord {
        Map<Faction, Integer> copy = new EnumMap<>(Faction.class);
        byFaction.forEach((f, n) -> {
            if (n != null && n > 0) copy.put(f, n);
        });
        byFaction = Collections.unmodifiableMap(copy);
        total = Math.max(total, copy.values().stream().mapToInt(Integer::intValue).sum());
    }

    /** One more release of a prisoner of {@code faction} ({@code null}: no faction). */
    public ReleaseRecord with(@Nullable Faction faction) {
        Map<Faction, Integer> next = new EnumMap<>(Faction.class);
        next.putAll(byFaction);
        if (faction != null) next.merge(faction, 1, Integer::sum);
        return new ReleaseRecord(next, total + 1);
    }

    public int count(Faction faction) {
        return byFaction.getOrDefault(faction, 0);
    }

    /** "navy 1, pirates 0, merchants 2, total 3" for commands and logs. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (Faction f : Faction.values()) sb.append(f.name().toLowerCase(Locale.ROOT)).append(' ').append(count(f)).append(", ");
        return sb.append("total ").append(total).toString();
    }
}

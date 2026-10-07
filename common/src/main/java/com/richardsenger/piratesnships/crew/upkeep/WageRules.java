package com.richardsenger.piratesnships.crew.upkeep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Who gets paid and where the coins come from (CR2, docs/design.md §7.3). Pure.
 * <ul>
 *   <li>Every crew member costs {@code wage} whole doubloons. As many members are paid in full as the coins on the
 *       ship allow ({@code coins / wage}, rounded down); the rest are unpaid. Nobody is paid in part, and coins that
 *       do not make up a full wage stay where they are.</li>
 *   <li>The coins are taken from the sources nearest the helm first (ties keep the given order), each source
 *       emptied before the next is touched.</li>
 *   <li>A wage of 0 pays everyone without taking anything.</li>
 * </ul>
 * Wages owed are not carried over to the next day.
 */
public final class WageRules {

    private WageRules() {
    }

    /** A container with {@code coins} doubloons at {@code distance} from the helm. */
    public record Source<K>(K key, double distance, long coins) {
        public Source {
            Objects.requireNonNull(key, "key");
        }
    }

    /**
     * The result: {@code paid} members get their wage, {@code unpaid} do not; {@code takes} is how many coins to take
     * out of each source, in the order they are taken (sources left untouched are not listed).
     */
    public record Payment<K>(int paid, int unpaid, long coinsTaken, Map<K, Long> takes) {
        public Payment {
            takes = Collections.unmodifiableMap(new LinkedHashMap<>(takes));
        }

        /** Wages are off: nobody is paid, nobody is unpaid. */
        public static <K> Payment<K> none() {
            return new Payment<>(0, 0, 0, Map.of());
        }
    }

    /** Pays {@code members} crew members {@code wage} doubloons each from {@code sources}. */
    public static <K> Payment<K> pay(int members, int wage, List<Source<K>> sources) {
        int n = Math.max(0, members);
        if (wage <= 0) {
            return new Payment<>(n, 0, 0, Map.of());
        }
        long total = 0;
        for (Source<K> s : sources) total += Math.max(0, s.coins());
        int paid = (int) Math.min(n, total / wage);
        long left = (long) paid * wage;
        List<Source<K>> order = new ArrayList<>(sources);
        order.sort(Comparator.comparingDouble(Source::distance)); // stable: ties keep the given order
        Map<K, Long> takes = new LinkedHashMap<>();
        for (Source<K> s : order) {
            if (left <= 0) break;
            long t = Math.min(left, Math.max(0, s.coins()));
            if (t > 0) {
                takes.merge(s.key(), t, Long::sum);
                left -= t;
            }
        }
        return new Payment<>(paid, n - paid, (long) paid * wage, takes);
    }
}

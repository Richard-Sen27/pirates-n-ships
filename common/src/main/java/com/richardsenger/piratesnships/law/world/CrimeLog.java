package com.richardsenger.piratesnships.law.world;

import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.crime.CriminalRecord.CrimeOutcome;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The last crime reported for each offender, whatever its outcome (debug and playtests:
 * {@code /pirates law last <target>}). In memory only, cleared when the server stops.
 */
public final class CrimeLog {

    /** One report. {@code victim} is a display name or a UUID string, empty without victim. */
    public record Entry(CrimeType type, CrimeOutcome outcome, double points, String victim, long gameTime) {
    }

    private static final Map<UUID, Entry> LAST = new ConcurrentHashMap<>();

    private CrimeLog() {
    }

    public static void record(UUID offender, Entry entry) {
        LAST.put(offender, entry);
    }

    public static Optional<Entry> last(UUID offender) {
        return Optional.ofNullable(LAST.get(offender));
    }

    public static void clear() {
        LAST.clear();
    }
}

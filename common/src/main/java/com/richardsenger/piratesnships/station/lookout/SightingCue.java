package com.richardsenger.piratesnships.station.lookout;

import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * The action-bar line of a lookout's call (LKO2): "Sail two points to starboard, 140 blocks", "Land ahead, 300
 * blocks", at most {@link #MAX} characters. Built from translation templates ({@link LookoutLang}) through
 * {@code lang} (key → template, {@code %s} arguments), so JUnit checks the English one and the client its own
 * language. The line tries, in order, until one fits:
 * <ol>
 *   <li>what, the bearing in points ("two points to starboard", "abeam to port", "on the starboard quarter"), "N blocks";</li>
 *   <li>what, the side only ("to starboard", "aft to port"), "N blocks";</li>
 *   <li>what, the side only, the bare number.</li>
 * </ol>
 * A ship the lookout knows by name (TPL2) is named when one of the rungs fits, else called by its kind. The last rung
 * of the kind is cut to {@link #MAX} if a language makes even that too long. Pure.
 */
public final class SightingCue {

    /** Longest line shown. */
    public static final int MAX = 40;

    private SightingCue() {
    }

    /** The line for {@code p}. */
    public static String text(SightingPayload p, UnaryOperator<String> lang) {
        return text(p.what(), p.name(), p.relative(), p.distance(), lang);
    }

    /** The line for a sighting of {@code what} ({@code name} or empty) at {@code relative} degrees, {@code distance} blocks. */
    public static String text(SightingPayload.What what, String name, double relative, int distance, UnaryOperator<String> lang) {
        Bearings.Relative r = Bearings.points(relative);
        String kind = lang.apply(LookoutLang.whatKey(what));
        List<String> whats = name == null || name.isBlank() ? List.of(kind) : List.of(name, kind);
        String last = "";
        for (String w : whats) {
            for (int rung = 0; rung < 3; rung++) {
                String bearing = rung == 0 ? longBearing(r, lang) : shortBearing(r, lang);
                String dist = rung == 2 ? Integer.toString(distance) : format(lang.apply(LookoutLang.KEY_CUE_BLOCKS), distance);
                last = format(lang.apply(LookoutLang.KEY_CUE), w, bearing, dist);
                if (last.length() <= MAX) return last;
            }
        }
        return last.substring(0, MAX);
    }

    /** "two points to starboard", "abeam to port", "on the starboard quarter", "ahead", "astern". */
    static String longBearing(Bearings.Relative r, UnaryOperator<String> lang) {
        String side = side(r, lang);
        return switch (r.sector()) {
            case AHEAD -> lang.apply(LookoutLang.KEY_CUE_AHEAD);
            case ASTERN -> lang.apply(LookoutLang.KEY_CUE_ASTERN);
            case BOW -> format(lang.apply(LookoutLang.KEY_CUE_POINTS), lang.apply(LookoutLang.KEY_POINTS + r.count()), side);
            case BEAM -> format(lang.apply(LookoutLang.KEY_CUE_BEAM), side);
            case ABAFT_BEAM -> format(lang.apply(LookoutLang.KEY_CUE_QUARTER), side);
        };
    }

    /** "to starboard", "aft to port", "ahead", "astern". */
    static String shortBearing(Bearings.Relative r, UnaryOperator<String> lang) {
        String side = side(r, lang);
        return switch (r.sector()) {
            case AHEAD -> lang.apply(LookoutLang.KEY_CUE_AHEAD);
            case ASTERN -> lang.apply(LookoutLang.KEY_CUE_ASTERN);
            case BOW, BEAM -> format(lang.apply(LookoutLang.KEY_CUE_SIDE), side);
            case ABAFT_BEAM -> format(lang.apply(LookoutLang.KEY_CUE_AFT), side);
        };
    }

    private static String side(Bearings.Relative r, UnaryOperator<String> lang) {
        return lang.apply(r.side() == Bearings.Side.STARBOARD ? LookoutLang.KEY_STARBOARD : LookoutLang.KEY_PORT);
    }

    private static String format(String template, Object... args) {
        try {
            return String.format(Locale.ROOT, template, args);
        } catch (IllegalFormatException e) {
            return template;
        }
    }
}

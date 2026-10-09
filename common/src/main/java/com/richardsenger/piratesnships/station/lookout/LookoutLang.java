package com.richardsenger.piratesnships.station.lookout;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The lookout's calls (CN1, LKO2): translation keys of the action-bar cue ({@link SightingCue}), their English text for
 * datagen and tests ({@link #ENGLISH}), and what a sighted ship is called by.
 */
public final class LookoutLang {

    static final String KEY = "message." + Constants.MOD_ID + ".lookout.";

    /** The cue: "%1$s %2$s, %3$s" (what, bearing, distance). */
    public static final String KEY_CUE = KEY + "cue";
    /** The distance: "%s blocks". */
    public static final String KEY_CUE_BLOCKS = KEY + "cue.blocks";

    static final String KEY_WHAT = KEY + "what.";
    public static final String KEY_WHAT_SAIL = KEY_WHAT + "sail";
    public static final String KEY_WHAT_MERCHANT = KEY_WHAT + "merchant";
    public static final String KEY_WHAT_NAVY = KEY_WHAT + "navy";
    public static final String KEY_WHAT_PIRATE = KEY_WHAT + "pirate";
    public static final String KEY_WHAT_WRECK = KEY_WHAT + "wreck";
    public static final String KEY_WHAT_LAND = KEY_WHAT + "land";
    public static final String KEY_WHAT_SHARK = KEY_WHAT + "shark";
    public static final String KEY_WHAT_KRAKEN = KEY_WHAT + "kraken";

    static final String KEY_BEARING = KEY + "bearing.";
    public static final String KEY_CUE_AHEAD = KEY_BEARING + "ahead";
    public static final String KEY_CUE_ASTERN = KEY_BEARING + "astern";
    /** "%s to %s": two points to starboard. */
    public static final String KEY_CUE_POINTS = KEY_BEARING + "points";
    /** "abeam to %s". */
    public static final String KEY_CUE_BEAM = KEY_BEARING + "beam";
    /** "on the %s quarter": abaft the beam. */
    public static final String KEY_CUE_QUARTER = KEY_BEARING + "quarter";
    /** "to %s": the short form ahead of the beam and on it. */
    public static final String KEY_CUE_SIDE = KEY_BEARING + "side";
    /** "aft to %s": the short form abaft the beam. */
    public static final String KEY_CUE_AFT = KEY_BEARING + "aft";
    public static final String KEY_STARBOARD = KEY_BEARING + "starboard";
    public static final String KEY_PORT = KEY_BEARING + "port";
    /** "one point" .. "seven points": {@code KEY_POINTS + n}. */
    public static final String KEY_POINTS = KEY_BEARING + "points.";

    /** The hail's subtitle. */
    public static final String KEY_HAIL_SUBTITLE = "subtitles." + Constants.MOD_ID + ".lookout.hail";

    private static final String[] NUMBERS = {"one point", "two points", "three points", "four points", "five points",
            "six points", "seven points"};

    /** Every English line of the lookout, key → text (datagen writes them; JUnit builds cues with them). */
    public static final Map<String, String> ENGLISH;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(KEY_CUE, "%s %s, %s");
        m.put(KEY_CUE_BLOCKS, "%s blocks");
        m.put(KEY_WHAT_SAIL, "Sail");
        m.put(KEY_WHAT_MERCHANT, "Merchant");
        m.put(KEY_WHAT_NAVY, "Navy ship");
        m.put(KEY_WHAT_PIRATE, "Pirate ship");
        m.put(KEY_WHAT_WRECK, "Wreck");
        m.put(KEY_WHAT_LAND, "Land");
        m.put(KEY_WHAT_SHARK, "Shark");
        m.put(KEY_WHAT_KRAKEN, "Kraken");
        m.put(KEY_CUE_AHEAD, "ahead");
        m.put(KEY_CUE_ASTERN, "astern");
        m.put(KEY_CUE_POINTS, "%s to %s");
        m.put(KEY_CUE_BEAM, "abeam to %s");
        m.put(KEY_CUE_QUARTER, "on the %s quarter");
        m.put(KEY_CUE_SIDE, "to %s");
        m.put(KEY_CUE_AFT, "aft to %s");
        m.put(KEY_STARBOARD, "starboard");
        m.put(KEY_PORT, "port");
        for (int n = 1; n <= NUMBERS.length; n++) {
            m.put(KEY_POINTS + n, NUMBERS[n - 1]);
        }
        m.put(KEY_HAIL_SUBTITLE, "Lookout rings the bell");
        ENGLISH = Collections.unmodifiableMap(m);
    }

    private LookoutLang() {
    }

    /**
     * What a ship looks like to the lookout: a wreck, else by the flag it flies (a struck or missing flag tells
     * nothing). Pure.
     */
    public static SightingPayload.What what(FlagKind flag, boolean flying, boolean wreck) {
        if (wreck) return SightingPayload.What.WRECK;
        if (!flying) return SightingPayload.What.SAIL;
        return switch (flag) {
            case MERCHANT -> SightingPayload.What.MERCHANT;
            case NAVY -> SightingPayload.What.NAVY;
            case JOLLY_ROGER -> SightingPayload.What.PIRATE;
            default -> SightingPayload.What.SAIL;
        };
    }

    /** The translation key of what the cue calls {@code what}. */
    public static String whatKey(SightingPayload.What what) {
        return switch (what) {
            case SAIL -> KEY_WHAT_SAIL;
            case MERCHANT -> KEY_WHAT_MERCHANT;
            case NAVY -> KEY_WHAT_NAVY;
            case PIRATE -> KEY_WHAT_PIRATE;
            case WRECK -> KEY_WHAT_WRECK;
            case LAND -> KEY_WHAT_LAND;
            case SHARK -> KEY_WHAT_SHARK;
            case KRAKEN -> KEY_WHAT_KRAKEN;
        };
    }

    public static void lang(LangBuilder lang) {
        ENGLISH.forEach(lang::add);
    }
}

package com.richardsenger.piratesnships.station.lookout;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import net.minecraft.network.chat.Component;

/**
 * The lookout's calls (CN1): translation keys, the English text for datagen, and the bearing phrase
 * ("two points off the starboard bow") built from {@link Bearings.Relative}.
 */
public final class LookoutLang {

    static final String KEY = "message." + Constants.MOD_ID + ".lookout.";
    /** "Sail ho! %1$s %2$s, %3$s blocks": what, bearing, distance. */
    public static final String KEY_SHIP = KEY + "ship";
    /** "Land ho! Land %1$s, %2$s blocks". */
    public static final String KEY_LAND = KEY + "land";
    /** "Shark in the water %1$s, %2$s blocks!". */
    public static final String KEY_SHARK = KEY + "shark";
    /** "Kraken! Something vast stirs %1$s, %2$s blocks off!". */
    public static final String KEY_KRAKEN = KEY + "kraken";
    /** The speaker of a call from a nest manned by a player only: "Crow's nest". */
    public static final String KEY_NEST = KEY + "nest";

    public static final String KEY_WHAT_SHIP = KEY + "what.ship";
    public static final String KEY_WHAT_MERCHANT = KEY + "what.merchant";
    public static final String KEY_WHAT_NAVY = KEY + "what.navy";
    public static final String KEY_WHAT_PIRATE = KEY + "what.pirate";
    public static final String KEY_WHAT_WRECK = KEY + "what.wreck";

    static final String KEY_BEARING = KEY + "bearing.";
    public static final String KEY_AHEAD = KEY_BEARING + "ahead";
    public static final String KEY_ASTERN = KEY_BEARING + "astern";
    /** "on the %s beam". */
    public static final String KEY_BEAM = KEY_BEARING + "beam";
    /** "%s off the %s bow". */
    public static final String KEY_BOW = KEY_BEARING + "bow";
    /** "%s abaft the %s beam". */
    public static final String KEY_ABAFT = KEY_BEARING + "abaft";
    public static final String KEY_STARBOARD = KEY_BEARING + "starboard";
    public static final String KEY_PORT = KEY_BEARING + "port";
    /** "one point" .. "seven points": {@code KEY_POINTS + n}. */
    public static final String KEY_POINTS = KEY_BEARING + "points.";

    private static final String[] NUMBERS = {"one point", "two points", "three points", "four points", "five points",
            "six points", "seven points"};

    private LookoutLang() {
    }

    /**
     * What a ship looks like to the lookout: a wreck, else by the flag it flies (a struck or missing flag tells
     * nothing). Pure.
     */
    public static String whatKey(FlagKind flag, boolean flying, boolean wreck) {
        if (wreck) return KEY_WHAT_WRECK;
        if (!flying) return KEY_WHAT_SHIP;
        return switch (flag) {
            case MERCHANT -> KEY_WHAT_MERCHANT;
            case NAVY -> KEY_WHAT_NAVY;
            case JOLLY_ROGER -> KEY_WHAT_PIRATE;
            default -> KEY_WHAT_SHIP;
        };
    }

    /** The key of the phrase for {@code r} (one of {@link #KEY_AHEAD} .. {@link #KEY_ABAFT}). Pure. */
    public static String bearingKey(Bearings.Relative r) {
        return switch (r.sector()) {
            case AHEAD -> KEY_AHEAD;
            case ASTERN -> KEY_ASTERN;
            case BEAM -> KEY_BEAM;
            case BOW -> KEY_BOW;
            case ABAFT_BEAM -> KEY_ABAFT;
        };
    }

    /** "two points off the starboard bow", "on the port beam", "dead ahead". */
    public static Component bearing(Bearings.Relative r) {
        Component side = Component.translatable(r.side() == Bearings.Side.STARBOARD ? KEY_STARBOARD : KEY_PORT);
        return switch (r.sector()) {
            case AHEAD, ASTERN -> Component.translatable(bearingKey(r));
            case BEAM -> Component.translatable(KEY_BEAM, side);
            case BOW, ABAFT_BEAM -> Component.translatable(bearingKey(r), Component.translatable(KEY_POINTS + r.count()), side);
        };
    }

    public static void lang(LangBuilder lang) {
        lang.add(KEY_SHIP, "Sail ho! %s %s, %s blocks")
                .add(KEY_LAND, "Land ho! Land %s, %s blocks")
                .add(KEY_SHARK, "Shark in the water %s, %s blocks!")
                .add(KEY_KRAKEN, "Kraken! Something vast stirs %s, %s blocks off!")
                .add(KEY_NEST, "Crow's nest")
                .add(KEY_WHAT_SHIP, "A ship")
                .add(KEY_WHAT_MERCHANT, "A merchant")
                .add(KEY_WHAT_NAVY, "A navy ship")
                .add(KEY_WHAT_PIRATE, "A pirate ship")
                .add(KEY_WHAT_WRECK, "A wreck")
                .add(KEY_AHEAD, "dead ahead")
                .add(KEY_ASTERN, "dead astern")
                .add(KEY_BEAM, "on the %s beam")
                .add(KEY_BOW, "%s off the %s bow")
                .add(KEY_ABAFT, "%s abaft the %s beam")
                .add(KEY_STARBOARD, "starboard")
                .add(KEY_PORT, "port");
        for (int n = 1; n <= NUMBERS.length; n++) {
            lang.add(KEY_POINTS + n, NUMBERS[n - 1]);
        }
    }
}

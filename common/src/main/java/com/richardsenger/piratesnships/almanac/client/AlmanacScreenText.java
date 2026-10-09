package com.richardsenger.piratesnships.almanac.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;

/** Lang keys of the Almanac's screen (ALM3) and their English text for datagen. */
public final class AlmanacScreenText {

    public static final String GUI = "gui." + Constants.MOD_ID + ".almanac.";
    public static final String TITLE = GUI + "title";
    public static final String EMPTY = GUI + "empty";
    public static final String NEXT = GUI + "next";
    public static final String PREVIOUS = GUI + "previous";

    private AlmanacScreenText() {
    }

    public static void lang(LangBuilder lang) {
        lang.add(TITLE, "The Captain's Almanac");
        lang.add(EMPTY, "The pages are blank. No chapters have reached this book yet: the server sends them when you join.");
        lang.add(NEXT, "Next page");
        lang.add(PREVIOUS, "Previous page");
    }
}

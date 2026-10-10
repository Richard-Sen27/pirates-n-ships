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
    /** The index spread (ALM8). */
    public static final String FOREWORD = GUI + "foreword";
    public static final String CONTINUE = GUI + "continue";
    public static final String BEGIN = GUI + "begin";
    public static final String CONTINUE_LINK = GUI + "continue_link";
    /** Recipe panels (ALM8). */
    public static final String SHAPELESS = GUI + "shapeless";
    public static final String RECIPE_MISSING = GUI + "recipe_missing";
    public static final String RECIPE_UNKNOWN = GUI + "recipe_unknown";
    /** WOOD1: the hover of a wood family's panel, which shows each wood's recipe in turn. */
    public static final String EVERY_WOOD = GUI + "every_wood";

    private AlmanacScreenText() {
    }

    public static void lang(LangBuilder lang) {
        lang.add(TITLE, "The Captain's Almanac");
        lang.add(EMPTY, "The pages are blank. No chapters have reached this book yet: the server sends them when you join.");
        lang.add(NEXT, "Next page");
        lang.add(PREVIOUS, "Previous page");
        lang.add(FOREWORD, "Wind, wood and powder, set down for every hand who would be master of a ship. "
                + "Pick a chapter on the left, or follow the ribbons on the book's edges.");
        lang.add(CONTINUE, "Continue where you left off:");
        lang.add(BEGIN, "New aboard? Begin here:");
        lang.add(CONTINUE_LINK, "%s, p. %s");
        lang.add(SHAPELESS, "Shapeless: the ingredients go anywhere in the grid");
        lang.add(RECIPE_MISSING, "This recipe is not in the world");
        lang.add(RECIPE_UNKNOWN, "Made another way");
        lang.add(EVERY_WOOD, "Made in every wood: the panel shows each wood's recipe in turn");
    }
}

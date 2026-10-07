package com.richardsenger.piratesnships.law.net;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.bounty.NoticeBoardListing;

import java.util.Locale;

/** Translation keys of the notice board screen (plain strings, so datagen can use them without client classes). */
public final class NoticeBoardText {

    private static final String P = "screen." + Constants.MOD_ID + ".notice_board.";

    public static final String TITLE = P + "title";
    public static final String COINS = P + "coins";
    public static final String LOADING = P + "loading";
    public static final String EMPTY = P + "empty";
    public static final String OWN_BOUNTY = P + "own_bounty";
    public static final String OWN_NONE = P + "own_none";
    public static final String COL_TARGET = P + "column.target";
    public static final String COL_AMOUNT = P + "column.amount";
    public static final String COL_PLACED_BY = P + "column.placed_by";
    public static final String COL_SINCE = P + "column.since";
    public static final String NAVY = P + "navy";
    public static final String TOTAL = P + "total";
    public static final String FORM = P + "form";
    public static final String FORM_TARGET = P + "form.target";
    public static final String FORM_AMOUNT = P + "form.amount";
    public static final String FORM_PLACE = P + "form.place";
    public static final String FORM_MINIMUM = P + "form.minimum";
    public static final String FORM_DISABLED = P + "form.disabled";
    public static final String CLOSED = P + "closed";

    private NoticeBoardText() {
    }

    /** "just now", "%s min ago", "%s h ago", "%s d ago". */
    public static String age(NoticeBoardListing.AgeUnit unit) {
        return P + "age." + unit.name().toLowerCase(Locale.ROOT);
    }
}

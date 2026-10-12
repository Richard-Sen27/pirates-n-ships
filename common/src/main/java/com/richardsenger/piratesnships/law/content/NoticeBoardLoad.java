package com.richardsenger.piratesnships.law.content;

import net.minecraft.util.StringRepresentable;

/**
 * How much is posted on a market notice board (NB2), shown by its look: an empty board with pins, a few papers, or a
 * board papered over with a wanted poster. The rule is pure: {@link #of}.
 */
public enum NoticeBoardLoad implements StringRepresentable {
    EMPTY("empty"),
    SOME("some"),
    BUSY("busy");

    private final String name;

    NoticeBoardLoad(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /**
     * The load for {@code postings} open postings: {@link #EMPTY} for none, {@link #BUSY} from {@code busyThreshold}
     * on ({@code law.notice_board.busy_threshold}; a threshold below 1 counts as 1), else {@link #SOME}.
     */
    public static NoticeBoardLoad of(int postings, int busyThreshold) {
        if (postings <= 0) return EMPTY;
        return postings >= Math.max(1, busyThreshold) ? BUSY : SOME;
    }
}

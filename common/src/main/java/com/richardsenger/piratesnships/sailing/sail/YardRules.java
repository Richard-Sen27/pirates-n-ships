package com.richardsenger.piratesnships.sailing.sail;

/**
 * The tunable limits of the square sail rule F5a (docs/design.md §5.2), from {@code sailing.sails} in the server
 * config.
 *
 * @param minGap    smallest vertical distance from the upper yard to the lower one [blocks]
 * @param maxGap    largest vertical distance [blocks]; raised to {@code minGap} if lower
 * @param maxLength longest yard [blocks]; a longer row of yard blocks is no yard and carries no sail
 */
public record YardRules(int minGap, int maxGap, int maxLength) {

    public static final YardRules DEFAULTS = new YardRules(2, 8, 15);

    public YardRules {
        minGap = Math.max(1, minGap);
        maxGap = Math.max(minGap, maxGap);
        maxLength = Math.max(1, maxLength);
    }
}

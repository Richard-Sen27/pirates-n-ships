package com.richardsenger.piratesnships.sailing.sail;

/**
 * The tunable limits of the triangular sail rule F5b (docs/design.md §5.2), from {@code sailing.sails} in the server
 * config.
 *
 * @param maxLength longest stay: largest distance between the centers of its two cleats [blocks]
 * @param minDrop   smallest height difference between the two cleats of a stay [blocks]
 */
public record StayRules(int maxLength, int minDrop) {

    public static final StayRules DEFAULTS = new StayRules(16, 2);

    public StayRules {
        maxLength = Math.max(1, maxLength);
        minDrop = Math.max(1, minDrop);
    }
}

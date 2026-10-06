package com.richardsenger.piratesnships.law.bounty;

/**
 * Rank of a captured pirate NPC for the navy turn-in reward (docs/design.md §13.2). Captains are worth extra
 * (§13.3). The mob package maps its pirate entities to these tiers.
 */
public enum PirateTier {
    DECKHAND("deckhand", 10),
    BUCCANEER("buccaneer", 20),
    OFFICER("officer", 50),
    CAPTAIN("captain", 150);

    private final String id;
    private final int defaultReward;

    PirateTier(String id, int defaultReward) {
        this.id = id;
        this.defaultReward = defaultReward;
    }

    public String id() {
        return id;
    }

    public int defaultReward() {
        return defaultReward;
    }
}

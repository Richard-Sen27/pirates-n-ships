package com.richardsenger.piratesnships.law.brig;

import java.util.Locale;

/** Who can be put in shackles (design.md §13.3). Pure. */
public final class CaptureRules {

    /** Outcome of a capture check; {@link #OK} means the capture may happen. */
    public enum Result {
        OK, SELF, DEAD, ALREADY_PRISONER, NOT_CAPTURABLE, PLAYER_CAPTURE_DISABLED, NO_BOUNTY, TOO_HEALTHY;

        public boolean ok() {
            return this == OK;
        }

        /** Translation key of the message shown to the captor. */
        public String messageKey() {
            return "message.pirates_n_ships.brig.capture." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * What the rule needs to know about a target.
     *
     * @param player         the target is a player
     * @param capturableType NPC targets: the entity is a mob and its type is not in {@code #pirates_n_ships:not_capturable}
     * @param hasBounty      player targets: an active bounty is on them
     */
    public record Target(boolean player, boolean capturableType, boolean alive, float health, float maxHealth,
                         boolean alreadyPrisoner, boolean self, boolean hasBounty) {
    }

    private CaptureRules() {
    }

    /**
     * @param healthFraction       the target's health must be at or below this fraction of its max health
     * @param playerCaptureEnabled config toggle {@code flags_brig.player_capture}
     */
    public static Result check(Target t, double healthFraction, boolean playerCaptureEnabled) {
        if (t.self()) return Result.SELF;
        if (!t.alive() || t.health() <= 0) return Result.DEAD;
        if (t.alreadyPrisoner()) return Result.ALREADY_PRISONER;
        if (t.player()) {
            if (!playerCaptureEnabled) return Result.PLAYER_CAPTURE_DISABLED;
            if (!t.hasBounty()) return Result.NO_BOUNTY;
        } else if (!t.capturableType()) {
            return Result.NOT_CAPTURABLE;
        }
        if (t.health() > t.maxHealth() * healthFraction) return Result.TOO_HEALTHY;
        return Result.OK;
    }
}

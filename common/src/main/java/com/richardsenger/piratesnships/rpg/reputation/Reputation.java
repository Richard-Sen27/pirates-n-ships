package com.richardsenger.piratesnships.rpg.reputation;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

/**
 * The reputation API (docs/design.md §15, REP1), server side. Scores live in the
 * {@link ReputationAttachments#REPUTATION} attachment of each player and change only through {@link #adjust} (deeds
 * go through {@code rpg.deeds.Deeds}). Decay toward 0 ({@code reputation.decay_per_day}) is continuous and lazy: every
 * read decays the stored record to the overworld game time without writing it, and {@link #adjust} stores the decayed
 * record, so offline players decay too.
 *
 * <p>The effect queries ({@link #navyStanding}, {@link #piratesFriendly}, {@link #navyHostile},
 * {@link #villagersRefuse}, {@link #priceScore}) answer "no effect" while {@code reputation.enabled} is off; the plain
 * reads still show the stored scores (commands).
 */
public final class Reputation {

    private Reputation() {
    }

    public static boolean enabled() {
        return ReputationConfig.ENABLED.get();
    }

    public static long now(Player player) {
        MinecraftServer server = player.getServer();
        if (server == null) throw new IllegalStateException("Reputation is server-side only");
        return server.overworld().getGameTime();
    }

    /** The player's record decayed to now (not written back). */
    public static ReputationRecord record(Player player) {
        return Services.ATTACHMENTS.get(player, ReputationAttachments.REPUTATION).decayTo(now(player), ReputationConfig.DECAY_PER_DAY.get());
    }

    /** The shown, whole score (−100..100). */
    public static int get(Player player, Faction faction) {
        return record(player).display(faction);
    }

    /** The exact score, with the fraction decay leaves. */
    public static double score(Player player, Faction faction) {
        return record(player).get(faction);
    }

    /**
     * Adds {@code delta} to the player's score with {@code faction}, clamped to −100..100, and returns the new shown
     * score. {@code reason} goes to the debug log. Works while reputation is disabled too (operator commands); deeds
     * check the toggle themselves.
     */
    public static int adjust(Player player, Faction faction, double delta, String reason) {
        ReputationRecord before = record(player);
        ReputationRecord after = before.plus(faction, delta);
        store(player, after);
        Constants.LOG.debug("Reputation of {} with {}: {} -> {} ({})", player.getName().getString(), faction.id(),
                before.display(faction), after.display(faction), reason);
        return after.display(faction);
    }

    /** Sets the score (operator command, quests): an {@link #adjust} by the difference. */
    public static int set(Player player, Faction faction, double value, String reason) {
        return adjust(player, faction, ReputationRules.clamp(value) - score(player, faction), reason);
    }

    /** Stores {@code record} as the player's reputation (the record should be decayed to now). */
    public static void store(Player player, ReputationRecord record) {
        Services.ATTACHMENTS.set(player, ReputationAttachments.REPUTATION, record);
    }

    // --- Effects ------------------------------------------------------------------------------------------------

    /** The navy standing for the false-flag rule and port fees: the navy score, 0 while reputation is disabled. */
    public static int navyStanding(Player player) {
        return enabled() ? get(player, Faction.NAVY) : 0;
    }

    /** Pirates leave this player alone until attacked: pirate score above {@code pirate_friendly_threshold}. */
    public static boolean piratesFriendly(Player player) {
        return enabled() && get(player, Faction.PIRATES) > ReputationConfig.PIRATE_FRIENDLY_THRESHOLD.get();
    }

    /** The navy attacks this player on sight: navy score below {@code navy_hostile_threshold}. */
    public static boolean navyHostile(Player player) {
        return enabled() && get(player, Faction.NAVY) < ReputationConfig.NAVY_HOSTILE_THRESHOLD.get();
    }

    /** Village markets refuse this player: villager score below {@code villager_trade_threshold}. */
    public static boolean villagersRefuse(Player player) {
        return enabled() && get(player, Faction.VILLAGERS) < ReputationConfig.VILLAGER_TRADE_THRESHOLD.get();
    }

    /** The score that swings prices with {@code faction}; 0 (no swing) while reputation is disabled. */
    public static int priceScore(Player player, Faction faction) {
        return enabled() ? get(player, faction) : 0;
    }
}

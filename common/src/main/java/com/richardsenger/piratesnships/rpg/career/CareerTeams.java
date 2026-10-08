package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.Optional;

/**
 * The title as a name prefix (docs/design.md §15, HON1): one vanilla scoreboard team per {@link CareerTitles.Title}
 * ({@code pns_title_<id>}) whose player prefix is the title, so chat, the tab list and the name over the head show
 * "Capt. Steve" without a mixin. {@link #refresh} moves a player onto the team of their current title, or off the
 * title teams when they have none, whenever {@link Careers#store} changes a career and at login.
 *
 * <ul>
 *   <li>A player on any other team (a datapack's, an operator's) is left alone: their team wins.</li>
 *   <li>With {@code careers.name_prefix} or {@code careers.enabled} off, players are only taken off the title teams
 *       (at their next login or career change), never put on one.</li>
 *   <li>Title teams are ordinary teams in the world's scoreboard (saved with it); their friendly-fire rule stays on
 *       and members do not see invisible members, so a shared title changes nothing in play.</li>
 * </ul>
 */
public final class CareerTeams {

    public static final String PREFIX = "pns_title_";

    private CareerTeams() {
    }

    public static String teamName(CareerTitles.Title title) {
        return PREFIX + title.id();
    }

    /** Whether {@code team} is one of the title teams. */
    public static boolean isTitleTeam(PlayerTeam team) {
        return team != null && team.getName().startsWith(PREFIX) && CareerTitles.byId(team.getName().substring(PREFIX.length())).isPresent();
    }

    public static ChatFormatting colour(CareerTitles.Side side) {
        return switch (side) {
            case NAVY -> ChatFormatting.AQUA;
            case PRIVATEER -> ChatFormatting.GREEN;
            case PIRATE -> ChatFormatting.RED;
        };
    }

    /** The prefix component: the (translatable) title in its side's colour and a space. */
    public static Component prefix(CareerTitles.Title title) {
        return Component.empty()
                .append(Component.translatableWithFallback(title.key(), title.text()).withStyle(colour(title.side())))
                .append(Component.literal(" "));
    }

    /** The title team, created with its prefix if missing (the prefix is reset each time in case it was edited). */
    public static PlayerTeam team(Scoreboard scoreboard, CareerTitles.Title title) {
        String name = teamName(title);
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            team = scoreboard.addPlayerTeam(name);
            team.setDisplayName(Component.translatableWithFallback(title.key(), title.text()));
            team.setSeeFriendlyInvisibles(false);
        }
        Component prefix = prefix(title);
        if (!prefix.equals(team.getPlayerPrefix())) team.setPlayerPrefix(prefix);
        return team;
    }

    /** The title the player should show now, or empty: none, or prefixes off. */
    public static Optional<CareerTitles.Title> wanted(CareerRecord record) {
        if (!CareerConfig.NAME_PREFIX.get() || !CareerConfig.ENABLED.get()) return Optional.empty();
        return CareerTitles.of(record);
    }

    /** What {@link #refresh} did. */
    public enum Outcome { JOINED, LEFT, UNCHANGED, OTHER_TEAM }

    /** {@link #refresh(ServerPlayer, CareerRecord)} with the stored record. */
    public static Outcome refresh(ServerPlayer player) {
        return refresh(player, Careers.record(player));
    }

    /** Puts the player on the team of the title {@code record} earns, or takes them off the title teams. */
    public static Outcome refresh(ServerPlayer player, CareerRecord record) {
        Scoreboard scoreboard = player.getScoreboard();
        String entry = player.getScoreboardName();
        PlayerTeam current = scoreboard.getPlayersTeam(entry);
        if (current != null && !isTitleTeam(current)) return Outcome.OTHER_TEAM;
        Optional<CareerTitles.Title> title = wanted(record);
        if (title.isEmpty()) {
            if (current == null) return Outcome.UNCHANGED;
            scoreboard.removePlayerFromTeam(entry, current);
            Constants.LOG.debug("{} left title team {}", entry, current.getName());
            return Outcome.LEFT;
        }
        PlayerTeam target = team(scoreboard, title.get());
        if (current == target) return Outcome.UNCHANGED;
        // addPlayerToTeam takes the entry off its old team first
        scoreboard.addPlayerToTeam(entry, target);
        Constants.LOG.debug("{} joined title team {}", entry, target.getName());
        return Outcome.JOINED;
    }
}

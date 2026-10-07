package com.richardsenger.piratesnships.law.bounty;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * What a notice board shows (docs/design.md §13.2), pure. One line per active bounty, grouped by target.
 *
 * <p>Order: targets as on {@link BountyBoard#notices} (highest total first, then name, then id); within a target the
 * navy's bounty first, then the larger amount, then the older bounty, then the bounty id. So the list is stable from
 * one refresh to the next.
 */
public final class NoticeBoardListing {

    /** Most names offered by the place form. */
    public static final int MAX_NAMES = 32;

    private NoticeBoardListing() {
    }

    /**
     * One line of the board.
     *
     * @param target         the target's id
     * @param targetName     the target's display name
     * @param targetIsPlayer whether the target is a player
     * @param amount         this bounty's doubloons
     * @param navy           placed by the navy (then {@code placedBy} is empty)
     * @param placedBy       the paying player's name
     * @param createdAt      game time the bounty was placed
     * @param targetTotal    all active bounties on the target together
     */
    public record Line(UUID target, String targetName, boolean targetIsPlayer, int amount, boolean navy, String placedBy,
                       long createdAt, long targetTotal) {

        public static final Codec<Line> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("target").forGetter(Line::target),
                Codec.STRING.fieldOf("name").forGetter(Line::targetName),
                Codec.BOOL.fieldOf("player").forGetter(Line::targetIsPlayer),
                Codec.INT.fieldOf("amount").forGetter(Line::amount),
                Codec.BOOL.fieldOf("navy").forGetter(Line::navy),
                Codec.STRING.fieldOf("placed_by").forGetter(Line::placedBy),
                Codec.LONG.fieldOf("created_at").forGetter(Line::createdAt),
                Codec.LONG.fieldOf("total").forGetter(Line::targetTotal)
        ).apply(i, Line::new));
    }

    /** The board's lines at game time {@code now}. */
    public static List<Line> lines(BountyBoard board, long now) {
        Map<UUID, List<Bounty>> byTarget = new LinkedHashMap<>();
        for (Bounty b : board.bounties()) {
            if (!b.expiredAt(now)) byTarget.computeIfAbsent(b.target().id(), k -> new ArrayList<>()).add(b);
        }
        Comparator<Bounty> within = Comparator.comparing((Bounty b) -> !b.isNavy())
                .thenComparing(Comparator.comparingInt(Bounty::amount).reversed())
                .thenComparingLong(Bounty::createdAt)
                .thenComparing(Bounty::id);
        List<Line> out = new ArrayList<>();
        for (BountyBoard.Notice notice : board.notices(now)) {
            List<Bounty> list = new ArrayList<>(byTarget.getOrDefault(notice.target().id(), List.of()));
            list.sort(within);
            for (Bounty b : list) {
                out.add(new Line(notice.target().id(), notice.target().name(), notice.target().kind() == BountyTarget.Kind.PLAYER,
                        b.amount(), b.isNavy(), b.payerName(), b.createdAt(), notice.total()));
            }
        }
        return out;
    }

    /**
     * Names the place form offers: the online players' names except the viewer's, then the names on the board, without
     * duplicates (ignoring case), sorted case-insensitively, at most {@link #MAX_NAMES}.
     */
    public static List<String> names(Collection<String> online, BountyBoard board, long now, String viewer) {
        TreeMap<String, String> byKey = new TreeMap<>();
        for (String n : online) {
            if (!n.equalsIgnoreCase(viewer)) byKey.putIfAbsent(n.toLowerCase(Locale.ROOT), n);
        }
        for (BountyBoard.Notice notice : board.notices(now)) {
            String n = notice.target().name();
            if (!n.isBlank() && !n.equalsIgnoreCase(viewer)) byKey.putIfAbsent(n.toLowerCase(Locale.ROOT), n);
        }
        return byKey.values().stream().limit(MAX_NAMES).toList();
    }

    /** A target on the board whose name matches {@code name} (ignoring case): the one with the newest bounty. */
    public static Optional<BountyTarget> knownTarget(BountyBoard board, String name, long now) {
        String wanted = name.trim();
        if (wanted.isEmpty()) return Optional.empty();
        return board.bounties().stream()
                .filter(b -> !b.expiredAt(now) && b.target().name().equalsIgnoreCase(wanted))
                .max(Comparator.comparingLong(Bounty::createdAt))
                .map(Bounty::target);
    }

    /** How long ago a bounty was placed, in the coarsest unit that fits (real time: 20 ticks a second). */
    public enum AgeUnit { JUST_NOW, MINUTES, HOURS, DAYS }

    public record Age(AgeUnit unit, long value) {
    }

    public static Age age(long ticks) {
        long minutes = Math.max(0, ticks) / (20L * 60L);
        if (minutes < 1) return new Age(AgeUnit.JUST_NOW, 0);
        if (minutes < 60) return new Age(AgeUnit.MINUTES, minutes);
        long hours = minutes / 60;
        if (hours < 24) return new Age(AgeUnit.HOURS, hours);
        return new Age(AgeUnit.DAYS, hours / 24);
    }
}

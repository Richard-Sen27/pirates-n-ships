package com.richardsenger.piratesnships.crew.hiring;

import com.richardsenger.piratesnships.rpg.career.InfamyRank;
import com.richardsenger.piratesnships.rpg.career.NavyRank;
import com.richardsenger.piratesnships.trade.market.PortKind;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The pure hiring rules (CRW1, docs/design.md §7.1, §7.5, §15): today's candidates of a port from a seed, who may hire
 * whom, the crew cap and who may dismiss a crew member. No world access; {@link Hiring} feeds it.
 */
public final class HiringRules {

    /** Why a player may or may not hire at a port; {@link #OK} lets them. */
    public enum Verdict {
        OK,
        /** The candidate is not of the kind this port offers. */
        WRONG_PORT,
        /** Villagers refuse a player below the villagers' trade threshold (REP1). */
        VILLAGERS_REFUSE,
        /** Pirates sign on only with a friend of the pirates or a captain with enough infamy. */
        PIRATES_DISTRUST,
        /** Navy ratings sign on only with an enlisted captain (Midshipman or higher). */
        NOT_ENLISTED;

        public boolean ok() {
            return this == OK;
        }
    }

    /**
     * What the rules need to know about the player: whether villagers refuse them and pirates like them (both false
     * while reputation is off), their ranks, and whether careers and reputation are on at all.
     */
    public record Standing(boolean villagersRefuse, boolean piratesFriendly, InfamyRank infamy, NavyRank navyRank,
                           boolean careersEnabled, boolean reputationEnabled) {
    }

    /** {@code crew.hiring.pirate_min_infamy} and {@code navy_requires_enlistment}. */
    public record Settings(InfamyRank pirateMinInfamy, boolean navyRequiresEnlistment) {
    }

    static final List<String> FIRST_NAMES = List.of(
            "Tom", "Will", "Jack", "Ned", "Sam", "Harry", "Dick", "Ben", "Joe", "Kit", "Abel", "Amos",
            "Caleb", "Eli", "Hugh", "Jem", "Luke", "Matt", "Nat", "Owen", "Pip", "Ralph", "Seth", "Walt",
            "Anne", "Bess", "Kate", "Meg", "Nan", "Peg", "Sally", "Ruth");
    static final List<String> SURNAMES = List.of(
            "Barker", "Cobb", "Dunn", "Fletcher", "Gale", "Hobbs", "Kidd", "Lane", "Moss", "Nash", "Oakes", "Penn",
            "Quinn", "Rook", "Shaw", "Tate", "Vane", "Wade", "Yates", "Bligh", "Cotton", "Drake", "Fry", "Grey",
            "Hale", "Jory", "Lowe", "Marlow", "Pike", "Reed", "Stone", "Ward");

    private HiringRules() {
    }

    /**
     * {@code n} candidates of {@code kind}, each asking {@code fee}, from {@code seed}: the same seed always gives the
     * same candidates (ids included); names are distinct within the list.
     */
    public static List<Candidate> candidates(CandidateKind kind, long seed, int n, int fee) {
        List<Candidate> out = new ArrayList<>();
        Set<String> names = new HashSet<>();
        long s = mix(seed ^ (kind.ordinal() + 1L) * 0x9E3779B97F4A7C15L);
        int guard = 0;
        while (out.size() < Math.max(0, n) && guard++ < n * 20 + 20) {
            long a = mix(s += 0x9E3779B97F4A7C15L);
            long b = mix(s += 0x9E3779B97F4A7C15L);
            String name = FIRST_NAMES.get((int) Long.remainderUnsigned(a, FIRST_NAMES.size())) + " "
                    + SURNAMES.get((int) Long.remainderUnsigned(b, SURNAMES.size()));
            if (!names.add(name)) continue;
            out.add(new Candidate(new UUID(a, b), name, kind, Math.max(0, fee)));
        }
        return out;
    }

    /** The seed of a port's candidates on {@code day} (FNV-1a over the port id, the day mixed in). */
    public static long seed(String portId, long day) {
        long h = 0xcbf29ce484222325L;
        for (byte b : portId.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xff;
            h *= 0x100000001b3L;
        }
        return mix(h ^ (day * 0xC2B2AE3D27D4EB4FL));
    }

    /**
     * Whether a player of {@code standing} may hire a {@code candidate} at a port of {@code port}: villages offer sailors
     * unless the villagers refuse the player; islands offer pirates to a friend of the pirates or a captain with at
     * least {@code pirateMinInfamy} (the infamy route only while careers are on; with careers and reputation both off,
     * pirates sign on with anyone); outposts offer navy ratings to a Midshipman or higher while
     * {@code navyRequiresEnlistment} and careers are on.
     */
    public static Verdict eligible(PortKind port, CandidateKind candidate, Standing standing, Settings settings) {
        if (CandidateKind.at(port) != candidate) return Verdict.WRONG_PORT;
        return switch (candidate) {
            case SAILOR -> standing.villagersRefuse() ? Verdict.VILLAGERS_REFUSE : Verdict.OK;
            case PIRATE -> {
                if (standing.piratesFriendly()) yield Verdict.OK;
                if (standing.careersEnabled() && standing.infamy().atLeast(settings.pirateMinInfamy())) yield Verdict.OK;
                if (!standing.careersEnabled() && !standing.reputationEnabled()) yield Verdict.OK;
                yield Verdict.PIRATES_DISTRUST;
            }
            case NAVY -> {
                if (!settings.navyRequiresEnlistment() || !standing.careersEnabled()) yield Verdict.OK;
                yield standing.navyRank().ordinal() >= NavyRank.MIDSHIPMAN.ordinal() ? Verdict.OK : Verdict.NOT_ENLISTED;
            }
        };
    }

    /**
     * The crew a ship may carry: its bunks ({@code ShipBunks}) when {@code requireBunks}, else at least
     * {@code maxWithoutBunks}.
     */
    public static int crewCap(int bunks, boolean requireBunks, int maxWithoutBunks) {
        return requireBunks ? Math.max(0, bunks) : Math.max(Math.max(0, bunks), maxWithoutBunks);
    }

    /** Whether one more crew member fits: {@code crew} below the cap. */
    public static boolean hasRoom(int crew, int bunks, boolean requireBunks, int maxWithoutBunks) {
        return crew < crewCap(bunks, requireBunks, maxWithoutBunks);
    }

    /**
     * Whether {@code player} may dismiss a crew member hired by {@code hiredBy} aboard a ship owned by
     * {@code shipOwner}: its hirer, the ship's owner, or anyone when the ship has no owner (or it stands on no ship).
     */
    public static boolean mayDismiss(UUID player, Optional<UUID> hiredBy, Optional<UUID> shipOwner) {
        if (hiredBy.isPresent() && hiredBy.get().equals(player)) return true;
        return shipOwner.isEmpty() || shipOwner.get().equals(player);
    }

    /** SplitMix64's finaliser. */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}

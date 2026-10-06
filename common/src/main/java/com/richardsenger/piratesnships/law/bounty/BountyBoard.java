package com.richardsenger.piratesnships.law.bounty;

import com.mojang.serialization.Codec;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The immutable set of active bounties (docs/design.md §13.2). Every operation returns a new board. The world copy
 * lives in {@code law.BountyBoardData} (overworld saved data).
 *
 * <p>Rules:
 * <ul>
 *   <li><b>Navy bounty:</b> at most one per target (its id is derived from the target). Placed when the score reaches
 *       the threshold, raised (never lowered) while the score rises, kept while the score decays, and withdrawn once
 *       the score falls below {@code threshold * withdrawRatio}. Never expires.</li>
 *   <li><b>Player bounties:</b> any number per target, each its own entry; they add up. Optional expiry.</li>
 *   <li><b>Claim:</b> pays every active bounty on the target to the claimant ({@code aliveFactor} more when
 *       delivered alive) and removes them. A target can't claim its own bounty; a payer's own bounties on the target
 *       are removed without payout (the contract is fulfilled, the money was already spent).</li>
 * </ul>
 */
public record BountyBoard(List<Bounty> bounties) {

    public static final BountyBoard EMPTY = new BountyBoard(List.of());

    public static final Codec<BountyBoard> CODEC = Bounty.CODEC.listOf().xmap(BountyBoard::new, BountyBoard::bounties);

    public BountyBoard {
        bounties = List.copyOf(bounties);
    }

    /** The fixed id of the navy bounty on {@code target}. */
    public static UUID navyBountyId(UUID target) {
        return UUID.nameUUIDFromBytes(("pirates_n_ships:navy_bounty:" + target).getBytes(StandardCharsets.UTF_8));
    }

    // --- Queries -----------------------------------------------------------------------------------------------

    /** Active (not expired) bounties on a target. */
    public List<Bounty> forTarget(UUID target, long now) {
        return bounties.stream().filter(b -> b.target().id().equals(target) && !b.expiredAt(now)).toList();
    }

    /** Sum of all active bounties on a target. */
    public long total(UUID target, long now) {
        return forTarget(target, now).stream().mapToLong(Bounty::amount).sum();
    }

    public boolean hasBounty(UUID target, long now) {
        return !forTarget(target, now).isEmpty();
    }

    public Optional<Bounty> navyBounty(UUID target) {
        UUID id = navyBountyId(target);
        return bounties.stream().filter(b -> b.id().equals(id)).findFirst();
    }

    /** One line of a notice board: all active bounties on one target, summed. */
    public record Notice(BountyTarget target, long total, int count, boolean navy) {
    }

    /** Notices for a notice board: highest total first, then by name, then by id (stable). */
    public List<Notice> notices(long now) {
        Map<UUID, List<Bounty>> byTarget = new LinkedHashMap<>();
        for (Bounty b : bounties) {
            if (!b.expiredAt(now)) byTarget.computeIfAbsent(b.target().id(), k -> new ArrayList<>()).add(b);
        }
        List<Notice> out = new ArrayList<>();
        for (List<Bounty> list : byTarget.values()) {
            // Latest placement carries the current display name
            Bounty newest = list.stream().max(Comparator.comparingLong(Bounty::createdAt)).orElseThrow();
            out.add(new Notice(newest.target(), list.stream().mapToLong(Bounty::amount).sum(), list.size(),
                    list.stream().anyMatch(Bounty::isNavy)));
        }
        out.sort(Comparator.comparingLong(Notice::total).reversed()
                .thenComparing(n -> n.target().name())
                .thenComparing(n -> n.target().id()));
        return out;
    }

    // --- Navy bounty --------------------------------------------------------------------------------------------

    public enum NavyChange { PLACED, RAISED, WITHDRAWN, UNCHANGED }

    public record NavySync(BountyBoard board, NavyChange change) {
    }

    /** Brings the navy bounty on {@code target} in line with its current criminal {@code score}. */
    public NavySync syncNavy(BountyTarget target, double score, long now, BountyRules rules) {
        Optional<Bounty> existing = navyBounty(target.id());
        if (existing.isEmpty()) {
            if (!rules.navyBounties() || score < rules.navyThreshold()) return new NavySync(this, NavyChange.UNCHANGED);
            Bounty b = new Bounty(navyBountyId(target.id()), target, Bounty.Source.NAVY, Optional.empty(), "",
                    rules.navyAmount(score), now, Bounty.NEVER);
            return new NavySync(with(b), NavyChange.PLACED);
        }
        Bounty current = existing.get();
        if (!rules.navyBounties() || score < rules.withdrawBelow()) {
            return new NavySync(without(current.id()), NavyChange.WITHDRAWN);
        }
        int amount = rules.navyAmount(score);
        if (amount > current.amount()) {
            Bounty raised = new Bounty(current.id(), target, Bounty.Source.NAVY, Optional.empty(), "", amount,
                    current.createdAt(), Bounty.NEVER);
            return new NavySync(replace(raised), NavyChange.RAISED);
        }
        return new NavySync(this, NavyChange.UNCHANGED);
    }

    // --- Player bounties ----------------------------------------------------------------------------------------

    public enum PlaceOutcome { PLACED, DISABLED, BELOW_MINIMUM, SELF_TARGET }

    public record PlaceResult(BountyBoard board, Optional<Bounty> bounty, PlaceOutcome outcome) {
        public boolean placed() {
            return outcome == PlaceOutcome.PLACED;
        }
    }

    /**
     * A player pays {@code amount} doubloons for a bounty on {@code target}. The caller takes the money only when the
     * result is {@link PlaceOutcome#PLACED}. {@code bountyId} must be fresh (the service uses a random UUID).
     */
    public PlaceResult placePlayerBounty(UUID bountyId, UUID payer, String payerName, BountyTarget target, int amount,
                                         long now, BountyRules rules) {
        if (!rules.playerBounties()) return new PlaceResult(this, Optional.empty(), PlaceOutcome.DISABLED);
        if (payer.equals(target.id())) return new PlaceResult(this, Optional.empty(), PlaceOutcome.SELF_TARGET);
        if (amount < Math.max(1, rules.playerMinimum())) {
            return new PlaceResult(this, Optional.empty(), PlaceOutcome.BELOW_MINIMUM);
        }
        long expires = rules.playerDurationTicks() > 0 ? now + rules.playerDurationTicks() : Bounty.NEVER;
        Bounty b = new Bounty(bountyId, target, Bounty.Source.PLAYER, Optional.of(payer), payerName, amount, now, expires);
        return new PlaceResult(with(b), Optional.of(b), PlaceOutcome.PLACED);
    }

    // --- Claims -------------------------------------------------------------------------------------------------

    public enum ClaimMethod {
        /** The target was defeated and the claimant brings a proof item. */
        DEAD_WITH_PROOF,
        /** The target was captured (shackles) and delivered to a navy officer. */
        ALIVE
    }

    public enum ClaimOutcome { CLAIMED, NO_BOUNTY, SELF_CLAIM }

    /**
     * @param payout      doubloons for the claimant
     * @param claimed     the bounties that were removed (paid or fulfilled)
     * @param scoreFactor multiply the target's criminal score with this (1.0 when nothing was claimed)
     */
    public record ClaimResult(BountyBoard board, int payout, List<Bounty> claimed, double scoreFactor,
                              ClaimOutcome outcome) {
        public boolean success() {
            return outcome == ClaimOutcome.CLAIMED;
        }
    }

    public ClaimResult claim(UUID target, UUID claimant, ClaimMethod method, long now, BountyRules rules) {
        if (target.equals(claimant)) return new ClaimResult(this, 0, List.of(), 1.0, ClaimOutcome.SELF_CLAIM);
        List<Bounty> active = forTarget(target, now);
        if (active.isEmpty()) return new ClaimResult(this, 0, List.of(), 1.0, ClaimOutcome.NO_BOUNTY);
        long sum = active.stream()
                .filter(b -> b.payer().map(p -> !p.equals(claimant)).orElse(true))
                .mapToLong(Bounty::amount).sum();
        double factor = method == ClaimMethod.ALIVE ? rules.aliveFactor() : 1.0;
        int payout = (int) Math.min(Integer.MAX_VALUE, Math.round(sum * factor));
        List<Bounty> rest = bounties.stream().filter(b -> !active.contains(b)).toList();
        return new ClaimResult(new BountyBoard(rest), payout, active, rules.scoreAfterClaimFactor(), ClaimOutcome.CLAIMED);
    }

    // --- Maintenance --------------------------------------------------------------------------------------------

    public record Pruned(BountyBoard board, List<Bounty> expired) {
    }

    /** Removes expired bounties and returns them (so the caller can refund payers once a currency item exists). */
    public Pruned pruneExpired(long now) {
        List<Bounty> expired = bounties.stream().filter(b -> b.expiredAt(now)).toList();
        if (expired.isEmpty()) return new Pruned(this, List.of());
        return new Pruned(new BountyBoard(bounties.stream().filter(b -> !b.expiredAt(now)).toList()), expired);
    }

    /** Removes every bounty on a target (operator command). */
    public BountyBoard clearTarget(UUID target) {
        return new BountyBoard(bounties.stream().filter(b -> !b.target().id().equals(target)).toList());
    }

    private BountyBoard with(Bounty b) {
        List<Bounty> list = new ArrayList<>(bounties);
        list.add(b);
        return new BountyBoard(list);
    }

    private BountyBoard without(UUID id) {
        return new BountyBoard(bounties.stream().filter(b -> !b.id().equals(id)).toList());
    }

    private BountyBoard replace(Bounty b) {
        return new BountyBoard(bounties.stream().map(x -> x.id().equals(b.id()) ? b : x).toList());
    }
}

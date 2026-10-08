package com.richardsenger.piratesnships.worldsim.navy;

import com.richardsenger.piratesnships.law.flag.FlagKind;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The pure rules of the navy's hunt (WS4b, design.md §10.4 "Navy patrols", §13): which ship a patrol chases, and what
 * it does with a chase from one check to the next. No world access.
 *
 * <p>A ship is <b>hunted</b> when it is a player's ship (it has an owner), has not struck its colours, and flies the
 * Jolly Roger, or the navy blew its cover, or its owner has a bounty of at least {@code bountyMinimum} doubloons or is
 * wanted at {@code law.world.navy_hostility_threshold}.
 */
public final class HuntRules {

    /**
     * @param huntRadius           a patrol sights ships within this many blocks
     * @param bountyMinimum        bounty (doubloons) on the owner from which a ship is hunted whatever it flies
     * @param loseDistance         a quarry farther than this is lost
     * @param contactDistance      within this the patrol is in contact (the give-up timer restarts)
     * @param giveUpTicks          ticks without contact before the patrol breaks off
     * @param surrenderLingerTicks ticks the patrol shadows a ship that struck its colours
     */
    public record Params(double huntRadius, long bountyMinimum, double loseDistance, double contactDistance, int giveUpTicks,
                         int surrenderLingerTicks) {
    }

    /**
     * A ship as the patrol sees it.
     *
     * @param ship        its id
     * @param x           centre x
     * @param z           centre z
     * @param playerShip  it has an owner (NPC ships have none)
     * @param shown       the flag it shows ({@code ShipData.flag})
     * @param struck      it has struck its colours
     * @param coverBlown  the navy saw through a false flag ({@code ShipData.coverBlown})
     * @param ownerBounty doubloons of bounty on its owner
     * @param ownerWanted its owner is wanted at {@code law.world.navy_hostility_threshold}
     */
    public record Candidate(UUID ship, double x, double z, boolean playerShip, FlagKind shown, boolean struck, boolean coverBlown,
                            long ownerBounty, boolean ownerWanted) {

        public double distanceTo(double px, double pz) {
            return Math.hypot(x - px, z - pz);
        }
    }

    /** What a patrol does with its chase this check. */
    public enum Step {
        /** Close in and fire. */
        CHASE,
        /** The quarry struck its colours: guns silent, keep it in sight. */
        SHADOW,
        /** The chase is over: back to the route. */
        RESUME
    }

    /** Why a chase ended ({@link Step#RESUME}); NONE while it goes on. */
    public enum Reason { NONE, LOST, OUT_OF_RANGE, NOT_HUNTED, GAVE_UP, SURRENDERED }

    /**
     * The verdict of one check.
     *
     * @param lastContact      the tick of the last contact after this check
     * @param surrenderedUntil the tick the shadowing ends, 0 while the quarry flies its colours
     */
    public record Judgement(Step step, Reason reason, long lastContact, long surrenderedUntil) {
    }

    private HuntRules() {
    }

    /** Whether the navy hunts {@code c} (see the class comment). */
    public static boolean hunted(Candidate c, long bountyMinimum) {
        if (!c.playerShip() || c.struck()) return false;
        return c.shown() == FlagKind.JOLLY_ROGER || c.coverBlown() || c.ownerBounty() >= bountyMinimum || c.ownerWanted();
    }

    /** The nearest hunted ship within {@code huntRadius} of the patrol at {@code (x, z)}, if any. */
    public static Optional<Candidate> pick(double x, double z, List<Candidate> candidates, Params p) {
        Candidate best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Candidate c : candidates) {
            if (!hunted(c, p.bountyMinimum())) continue;
            double d = c.distanceTo(x, z);
            if (d <= p.huntRadius() && d < bestDistance) {
                best = c;
                bestDistance = d;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * One check of a chase by a patrol at {@code (x, z)}: {@code target} is the quarry now (null when it is gone or
     * not loaded). A quarry that struck its colours is shadowed for {@code surrenderLingerTicks} from the first check
     * that saw it struck, then left; one that raises its colours again is chased again. Otherwise the chase ends when
     * the quarry is lost, out of range, no longer hunted, or out of contact for {@code giveUpTicks}.
     */
    public static Judgement judge(@Nullable Candidate target, double x, double z, long now, long lastContact,
                                  long surrenderedUntil, Params p) {
        if (target == null) return new Judgement(Step.RESUME, Reason.LOST, lastContact, surrenderedUntil);
        double d = target.distanceTo(x, z);
        if (d > p.loseDistance()) return new Judgement(Step.RESUME, Reason.OUT_OF_RANGE, lastContact, surrenderedUntil);
        long contact = d <= p.contactDistance() ? now : lastContact;
        if (target.struck()) {
            long until = surrenderedUntil > 0 ? surrenderedUntil : now + p.surrenderLingerTicks();
            if (now >= until) return new Judgement(Step.RESUME, Reason.SURRENDERED, contact, until);
            return new Judgement(Step.SHADOW, Reason.NONE, contact, until);
        }
        if (!hunted(target, p.bountyMinimum())) return new Judgement(Step.RESUME, Reason.NOT_HUNTED, contact, 0);
        if (now - contact >= p.giveUpTicks()) return new Judgement(Step.RESUME, Reason.GAVE_UP, contact, 0);
        return new Judgement(Step.CHASE, Reason.NONE, contact, 0);
    }
}

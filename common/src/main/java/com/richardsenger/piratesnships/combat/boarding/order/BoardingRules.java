package com.richardsenger.piratesnships.combat.boarding.order;

/**
 * The pure rules of the "Board!" order (BRD3, docs/design.md §8.4): who goes, the whistle entry's label, when a cleared
 * deck counts as taken, when an NPC captain gives the order and when he calls his boarders back. No world access;
 * {@link BoardingParties} and {@link NpcBoarding} feed them.
 */
public final class BoardingRules {

    /** What a crew member does when the order comes: by the station it mans. */
    public enum Post {
        /** At no station: a free hand. */
        NONE,
        /** At a cannon or swivel gun. */
        GUN,
        /** At the helm. */
        HELM,
        /** At any other station (winch, pump, capstan, crow's nest). */
        OTHER
    }

    /** What the whistle's boarding entry says now. */
    public enum Label {
        /** No boarding party of this ship is out: the entry gives "Board!". */
        BOARD,
        /** A party is out: the entry gives "Back aboard". */
        BACK_ABOARD
    }

    /** An NPC ship's look at an enemy ship lying close. */
    public enum NpcStep {
        /** Not an enemy it may board, or no way across: the contact clock stops. */
        NONE,
        /** Alongside, the delay is running or the captain is not ready to board. */
        WAIT,
        /** "Board!". */
        BOARD
    }

    private BoardingRules() {
    }

    /** Whether a crew member at {@code post} goes with the boarders ({@code keepStations}: gunners and helmsman stay). */
    public static boolean boards(Post post, boolean keepStations) {
        return switch (post) {
            case NONE, OTHER -> true;
            case GUN, HELM -> !keepStations;
        };
    }

    /** The whistle entry's label: "Back aboard" while a party of the ship is out, else "Board!". */
    public static Label label(boolean partyOut) {
        return partyOut ? Label.BACK_ABOARD : Label.BOARD;
    }

    /**
     * Ticks the boarders have held the other deck clear after {@code dt} more ticks: counting while boarders stand on it
     * and no foe is left aboard, back to 0 otherwise.
     */
    public static int clearTicks(int previous, int foesAboard, int boardersAboard, int dt) {
        if (foesAboard > 0 || boardersAboard <= 0) return 0;
        return Math.max(0, previous) + Math.max(0, dt);
    }

    /** Whether a deck held clear for {@code clearTicks} counts as taken ({@code boarding.capture_seconds}). */
    public static boolean captured(int clearTicks, int captureSeconds) {
        return clearTicks >= captureSeconds * 20;
    }

    /**
     * The facts an NPC captain weighs about an enemy ship lying close.
     *
     * @param enemy        a ship its guns fire at (pirates: a player's ship that is not a pirate; navy: a pirate-flagged
     *                     or wanted player's ship), not struck, not a wreck
     * @param wayAcross    a plank or a gap of at most {@code board_gap} between the two ships
     * @param gunsEngaged  its gun crews have fired on (aimed at) that ship
     * @param faster       its own ship is the faster now
     * @param larger       its own ship is the heavier
     * @param fighters     its fighters alive aboard
     * @param defenders    the fighting people aboard the enemy ship
     */
    public record NpcView(boolean enemy, boolean wayAcross, boolean gunsEngaged, boolean faster, boolean larger,
                          int fighters, int defenders) {
    }

    /**
     * The NPC captain's step: nothing for a ship that is no enemy or out of reach; "Board!" once the ships have lain
     * alongside for {@code delayTicks} ({@code contactTicks} so far), when its guns have fired on her or its ship is
     * the faster or the larger, it has fighters and they are not outnumbered ({@link #outnumbered}); else wait.
     */
    public static NpcStep npcStep(NpcView v, int contactTicks, int delayTicks, double outnumberedRatio) {
        if (!v.enemy() || !v.wayAcross()) return NpcStep.NONE;
        if (contactTicks < delayTicks) return NpcStep.WAIT;
        if (!(v.gunsEngaged() || v.faster() || v.larger())) return NpcStep.WAIT;
        if (v.fighters() <= 0 || outnumbered(v.fighters(), v.defenders(), outnumberedRatio)) return NpcStep.WAIT;
        return NpcStep.BOARD;
    }

    /** Whether {@code fighters} are outnumbered: no fighter left, or at least {@code ratio} defenders per fighter. */
    public static boolean outnumbered(int fighters, int defenders, double ratio) {
        return fighters <= 0 || defenders >= ratio * fighters;
    }

    /**
     * Whether an NPC captain calls his boarders back: his own ship is flooding past {@code floodedLimit}, or the boarders
     * left alive are outnumbered by the defenders.
     */
    public static boolean npcRecalls(int boardersAlive, int defendersAlive, double floodedFraction, double floodedLimit,
                                     double outnumberedRatio) {
        return floodedFraction >= floodedLimit || outnumbered(boardersAlive, defendersAlive, outnumberedRatio);
    }

    /** Whether a boarder heads home: called back, or the deck is taken. */
    public static boolean headsHome(boolean recalled, boolean captured) {
        return recalled || captured;
    }
}

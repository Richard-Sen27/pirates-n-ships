package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.trade.market.PortKind;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a port's plan kept and why it dropped the rest (STR9, the human: "for some reason they are A LOT smaller now").
 * {@code PortStructure.plan} fills one per site: the jigsaw's pieces ({@code planned}), the pieces placed
 * ({@code kept}), and per rule the pieces it dropped itself ({@code trail}: {@link TrailLevels}, {@code land}: the land
 * samples of {@link LandFilter}, {@code corners}: a building's corners, {@code wall}: {@link WallRuns}) and the pieces
 * lost because a piece they hang from was dropped ({@code children}); since STR9 also what was saved instead of dropped
 * ({@code ramps}: trail pieces past the old cut limit kept on a ramp, {@code shifted}: buildings moved inland,
 * {@code swapped}: buildings swapped for a smaller one, {@code rerooted}: children hung on again). Logged once per site
 * and kept for the tests ({@link #latest}). Pure.
 */
public final class PlanReport {

    /** Why a piece was dropped. */
    public enum Reason {
        TRAIL, LAND, CORNERS, WALL, CHILD
    }

    private static final Map<PortKind, PlanReport> LATEST = new ConcurrentHashMap<>();

    public final PortKind kind;
    public final int x;
    public final int z;
    public int planned;
    public int kept;
    public int trail;
    public int land;
    public int corners;
    public int wall;
    public int children;
    public int ramps;
    public int shifted;
    public int swapped;
    public int rerooted;
    /** Children lost, by the reason their dropped ancestor was dropped for (indexed by {@link Reason#ordinal}). */
    public final int[] childrenOf = new int[Reason.values().length];

    public PlanReport(PortKind kind, int x, int z) {
        this.kind = kind;
        this.x = x;
        this.z = z;
    }

    /** Counts one dropped piece. */
    public void drop(Reason reason) {
        switch (reason) {
            case TRAIL -> trail++;
            case LAND -> land++;
            case CORNERS -> corners++;
            case WALL -> wall++;
            case CHILD -> children++;
        }
    }

    /** Counts one piece lost because an ancestor was dropped for {@code root}. */
    public void child(Reason root) {
        children++;
        childrenOf[root.ordinal()]++;
    }

    public int dropped() {
        return trail + land + corners + wall + children;
    }

    /** Records this report as the latest of its kind (the tests read it after a plan). */
    public PlanReport publish() {
        LATEST.put(kind, this);
        return this;
    }

    /** The latest report of a kind's plan, if one was made in this run. */
    public static Optional<PlanReport> latest(PortKind kind) {
        return Optional.ofNullable(LATEST.get(kind));
    }

    /** The log line: {@code STR9: <kind> at x z: planned N, kept K, dropped: trail T, land L, ...}. */
    public String line() {
        return "STR9: " + kind.getSerializedName() + " at " + x + " " + z + ": planned " + planned + ", kept " + kept
                + ", dropped: trail " + trail + " (bed off its ground), land " + land + " (samples over water), corners " + corners
                + " (a building's corner over water), wall " + wall + " (run over water), children of dropped " + children + " (of trail " + childrenOf[0] + ", land " + childrenOf[1]
                + ", corners " + childrenOf[2] + ", wall " + childrenOf[3] + ")"
                + "; saved: ramps " + ramps + ", shifted " + shifted + ", swapped " + swapped + ", rerooted " + rerooted;
    }

    @Override
    public String toString() {
        return line();
    }
}

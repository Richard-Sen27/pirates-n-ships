package com.richardsenger.piratesnships.mob.kraken;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The kraken's eight tentacles (docs/design.md §12), pure: each has its own health pool and a job. A tentacle whose
 * pool is emptied is <b>cut</b>: it drops its job, can't be hit or used, and grows back with a full pool after
 * {@code tentacle_regrow_ticks}. Free tentacles (not cut, no job) are given jobs by {@link #plan}.
 */
public final class KrakenTentacles {

    public static final int COUNT = 8;
    /** Most swimmers held at once. */
    public static final int MAX_HOLDS = 2;

    /** What a tentacle is doing. */
    public enum Job {
        /** Resting by the body (or retreating, when cut). */
        NONE,
        /** Gripping a hull block near the waterline and pulling the ship down and toward the kraken. */
        GRAB_HULL,
        /** Beating on a mast: breaks one mast block every {@code mast_strike_interval_ticks}. */
        MAST_STRIKE,
        /** Sweeping the deck: knocks everyone near it off every {@code swipe_interval_ticks}. */
        DECK_SWIPE,
        /** Holding a swimmer and dragging them under. */
        HOLD_SWIMMER
    }

    /**
     * What there is to do for free tentacles: swimmers in reach that no tentacle holds or reaches for yet (at most
     * {@link #MAX_HOLDS} in all), whether a mast block is in reach and no tentacle beats it yet, whether someone stands on
     * the deck and no tentacle sweeps it yet, and how many grab spots on the hull are still free.
     */
    public record Demand(int swimmers, boolean mast, boolean deck, int grabSpots) {
        public static final Demand NONE = new Demand(0, false, false, 0);
    }

    private final double[] pool = new double[COUNT];
    private final long[] cutUntil = new long[COUNT];
    private final Job[] job = new Job[COUNT];

    public KrakenTentacles(double health) {
        Arrays.fill(pool, health);
        Arrays.fill(job, Job.NONE);
    }

    public Job job(int i) {
        return job[i];
    }

    public void setJob(int i, Job j) {
        job[i] = isCut(i) ? Job.NONE : j;
    }

    public double pool(int i) {
        return pool[i];
    }

    public boolean isCut(int i) {
        return cutUntil[i] > 0;
    }

    /** The game time at which a cut tentacle grows back (0 when it is not cut). */
    public long regrowsAt(int i) {
        return cutUntil[i];
    }

    /**
     * Damages tentacle {@code i}; returns true when this hit cut it. A cut tentacle takes no damage. Cutting drops its
     * job; it grows back at {@code now + regrowTicks}.
     */
    public boolean damage(int i, double amount, long now, int regrowTicks) {
        if (isCut(i) || amount <= 0) {
            return false;
        }
        pool[i] -= amount;
        if (pool[i] > 0) {
            return false;
        }
        pool[i] = 0;
        cutUntil[i] = now + Math.max(1, regrowTicks);
        job[i] = Job.NONE;
        return true;
    }

    /** Grows back the cut tentacles whose time has come, with a full pool of {@code health}. Returns how many did. */
    public int tick(long now, double health) {
        int grown = 0;
        for (int i = 0; i < COUNT; i++) {
            if (cutUntil[i] > 0 && now >= cutUntil[i]) {
                cutUntil[i] = 0;
                pool[i] = health;
                grown++;
            } else if (cutUntil[i] == 0 && pool[i] > health) {
                pool[i] = health; // the config was lowered
            }
        }
        return grown;
    }

    /** Free tentacles: neither cut nor busy, in index order. */
    public List<Integer> free() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) if (!isCut(i) && job[i] == Job.NONE) out.add(i);
        return out;
    }

    /** How many tentacles do {@code j}. */
    public int count(Job j) {
        int n = 0;
        for (int i = 0; i < COUNT; i++) if (job[i] == j) n++;
        return n;
    }

    /** Every tentacle lets go. */
    public void releaseAll() {
        Arrays.fill(job, Job.NONE);
    }

    /** Restores one tentacle (persistence). */
    public void load(int i, double pool, long cutUntil) {
        this.pool[i] = Math.max(0, pool);
        this.cutUntil[i] = Math.max(0, cutUntil);
        this.job[i] = Job.NONE;
    }

    /**
     * Jobs for {@code freeCount} free tentacles, in order: one per waiting swimmer (they drown fastest), one for a mast in
     * reach, one for an occupied deck, then one per free grab spot; the rest stay free ({@link Job#NONE}).
     */
    public static List<Job> plan(int freeCount, Demand d) {
        List<Job> out = new ArrayList<>(freeCount);
        int swimmers = Math.max(0, d.swimmers());
        boolean mast = d.mast();
        boolean deck = d.deck();
        int spots = Math.max(0, d.grabSpots());
        for (int k = 0; k < freeCount; k++) {
            if (swimmers > 0) {
                out.add(Job.HOLD_SWIMMER);
                swimmers--;
            } else if (mast) {
                out.add(Job.MAST_STRIKE);
                mast = false;
            } else if (deck) {
                out.add(Job.DECK_SWIPE);
                deck = false;
            } else if (spots > 0) {
                out.add(Job.GRAB_HULL);
                spots--;
            } else {
                out.add(Job.NONE);
            }
        }
        return out;
    }
}

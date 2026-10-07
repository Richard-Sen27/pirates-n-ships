package com.richardsenger.piratesnships.station.jobs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Who claims which open job of a ship's job board (CR1, docs/design.md §7.2 "Job board"). Pure: no world access, the
 * caller describes the jobs and the hands on board.
 *
 * <p>Rules, applied order by order (the oldest order first, so scarce crew carry out the earlier order):
 * <ol>
 *   <li>Within one order, the closest pair of open job and free hand is assigned first, then the next closest, and so
 *       on: one job per hand, one hand per job; a hand farther than {@code maxDistance} never claims ({@code 0} = no
 *       limit).</li>
 *   <li>When no free hand can take a job of the order any more (none left, or all out of reach), the remaining jobs
 *       may re-task <em>idle</em> board-assigned hands ({@link Status#BOARD_IDLE}) the same way, closest first.</li>
 *   <li>Pinned hands (assigned by hand with the whistle) and busy board-assigned hands never claim.</li>
 * </ol>
 */
public final class JobBoardRules {

    /** What a crew member on the ship is doing, as far as the board cares. */
    public enum Status {
        /** Not at a station: claims first. */
        FREE,
        /** Put at a station by the board and not working an order: may be re-tasked when no free hand is left. */
        BOARD_IDLE,
        /** Put at a station by the board and working an order: never moved. */
        BOARD_BUSY,
        /** Assigned by hand with the whistle or the command: never moved. */
        PINNED
    }

    /**
     * An open job: station {@code key} at a world position, posted by the order with sequence number {@code seq}
     * (lower = issued earlier).
     */
    public record Job<K>(K key, double x, double y, double z, long seq) {
        public Job {
            Objects.requireNonNull(key, "key");
        }
    }

    /** A crew member on the ship at a world position. */
    public record Hand(UUID id, double x, double y, double z, Status status) {
        public Hand {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(status, "status");
        }
    }

    /** {@code crew} takes {@code job}; {@code retask} when it leaves a board-assigned station for it. */
    public record Claim<K>(K job, UUID crew, boolean retask) { }

    private JobBoardRules() {
    }

    /**
     * The claims of one board pass. Jobs and hands keep their list order as the tie-break (equal distances), so the
     * result is deterministic.
     *
     * @param maxDistance largest job-to-hand distance in blocks that still claims; {@code <= 0} = no limit
     */
    public static <K> List<Claim<K>> assign(List<Job<K>> jobs, List<Hand> hands, double maxDistance) {
        TreeMap<Long, List<Job<K>>> byOrder = new TreeMap<>();
        for (Job<K> j : jobs) {
            byOrder.computeIfAbsent(j.seq(), s -> new ArrayList<>()).add(j);
        }
        Set<UUID> used = new HashSet<>();
        List<Claim<K>> out = new ArrayList<>();
        for (List<Job<K>> group : byOrder.values()) {
            List<Job<K>> open = new ArrayList<>(group);
            pair(open, hands, Status.FREE, used, maxDistance, false, out);
            pair(open, hands, Status.BOARD_IDLE, used, maxDistance, true, out);
        }
        return out;
    }

    /** The jobs of {@code jobs} that {@code claims} leaves open, in list order. */
    public static <K> List<K> unclaimed(List<Job<K>> jobs, List<Claim<K>> claims) {
        Set<K> taken = new HashSet<>();
        for (Claim<K> c : claims) taken.add(c.job());
        List<K> out = new ArrayList<>();
        for (Job<K> j : jobs) {
            if (!taken.contains(j.key())) out.add(j.key());
        }
        return out;
    }

    /** Assigns the closest (job, hand) pairs among {@code open} and unused hands with {@code status} until none is left. */
    private static <K> void pair(List<Job<K>> open, List<Hand> hands, Status status, Set<UUID> used, double maxDistance,
                                 boolean retask, List<Claim<K>> out) {
        double limit = maxDistance > 0 ? maxDistance * maxDistance : Double.POSITIVE_INFINITY;
        while (!open.isEmpty()) {
            int bestJob = -1;
            Hand bestHand = null;
            double best = Double.POSITIVE_INFINITY;
            for (int j = 0; j < open.size(); j++) {
                Job<K> job = open.get(j);
                for (Hand h : hands) {
                    if (h.status() != status || used.contains(h.id())) continue;
                    double d = distanceSqr(job, h);
                    if (d <= limit && d < best) {
                        best = d;
                        bestJob = j;
                        bestHand = h;
                    }
                }
            }
            if (bestHand == null) {
                return;
            }
            used.add(bestHand.id());
            out.add(new Claim<>(open.remove(bestJob).key(), bestHand.id(), retask));
        }
    }

    private static double distanceSqr(Job<?> job, Hand hand) {
        double dx = job.x() - hand.x(), dy = job.y() - hand.y(), dz = job.z() - hand.z();
        return dx * dx + dy * dy + dz * dz;
    }
}

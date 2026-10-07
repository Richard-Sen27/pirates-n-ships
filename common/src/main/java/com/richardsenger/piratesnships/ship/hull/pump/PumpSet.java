package com.richardsenger.piratesnships.ship.hull.pump;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;

/**
 * The bilge pumps of one ship and which of them work this tick, pure (no world access). Positions are plot positions;
 * the hull runtime fills them from its grid snapshot and keeps them current on block changes. A pump works while a
 * player's last use has not expired ({@link #use}) or while a crew member operates it (the predicate passed to
 * {@link #activeCounts}). One pump block counts once, however many work it.
 */
public final class PumpSet {

    private final Set<BlockPos> pumps = new HashSet<>();
    /** Plot position → game time until which a player's use keeps the pump working (exclusive). */
    private final Map<BlockPos, Long> usedUntil = new HashMap<>();

    public void replace(Collection<BlockPos> positions) {
        pumps.clear();
        positions.forEach(p -> pumps.add(p.immutable()));
        usedUntil.keySet().retainAll(pumps);
    }

    public void add(BlockPos pos) {
        pumps.add(pos.immutable());
    }

    public void remove(BlockPos pos) {
        pumps.remove(pos);
        usedUntil.remove(pos);
    }

    public boolean contains(BlockPos pos) {
        return pumps.contains(pos);
    }

    public Set<BlockPos> positions() {
        return Set.copyOf(pumps);
    }

    public boolean isEmpty() {
        return pumps.isEmpty();
    }

    /** A player used the pump at {@code pos}: it works until {@code until} (game time, exclusive). */
    public void use(BlockPos pos, long until) {
        if (pumps.contains(pos)) {
            usedUntil.merge(pos.immutable(), until, Math::max);
        }
    }

    /** Whether a player's use keeps the pump at {@code pos} working at game time {@code now}. */
    public boolean usedAt(BlockPos pos, long now) {
        Long until = usedUntil.get(pos);
        return until != null && now < until;
    }

    /** Forgets every player's use (pumps switched off by config). */
    public void clearUses() {
        usedUntil.clear();
    }

    /**
     * Working pumps per compartment at game time {@code now}, for {@code FloodTickInput.withPumps}.
     *
     * @param compartments number of compartments
     * @param crew         whether a crew member operates the pump at a position
     * @param intake       the compartment a pump drains ({@link PumpIntake}), -1 for none
     * @return counts per compartment, or null when no pump works
     */
    public int[] activeCounts(long now, int compartments, Predicate<BlockPos> crew, ToIntFunction<BlockPos> intake) {
        for (Iterator<Map.Entry<BlockPos, Long>> it = usedUntil.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue() <= now) {
                it.remove();
            }
        }
        int[] counts = null;
        for (BlockPos p : pumps) {
            if (!usedAt(p, now) && !crew.test(p)) {
                continue;
            }
            int c = intake.applyAsInt(p);
            if (c < 0 || c >= compartments) {
                continue;
            }
            if (counts == null) {
                counts = new int[compartments];
            }
            counts[c]++;
        }
        return counts;
    }
}

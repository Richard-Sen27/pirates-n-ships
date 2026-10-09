package com.richardsenger.piratesnships.worldsim.materialize;

import com.richardsenger.piratesnships.law.flag.Faction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * Pure rules for the guns of a materialised voyage (WS4c, design.md §8.2, §10.4): which ships carry powder and shot,
 * which shot locker feeds which gun, how many rounds go where, and in which order free hands man loaded guns. No world
 * access, unit tested; {@link VoyageGuns} applies them.
 */
public final class GunStocking {

    /** No locker within reach of the gun. */
    public static final int NO_LOCKER = -1;

    private GunStocking() {
    }

    /** Navy and pirate ships carry powder and shot for their guns; a merchant carries none (it has no gunnery). */
    public static boolean stocks(Faction faction) {
        return faction != Faction.MERCHANTS;
    }

    /**
     * For every gun (plot position of its master), the index of the nearest locker within {@code range} blocks
     * (straight line, the gun crews' supply range), {@link #NO_LOCKER} when none is; on a tie the earlier locker.
     */
    public static int[] lockerOf(List<BlockPos> guns, List<BlockPos> lockers, int range) {
        int[] out = new int[guns.size()];
        double max = (double) range * range;
        for (int g = 0; g < guns.size(); g++) {
            out[g] = NO_LOCKER;
            double best = Double.MAX_VALUE;
            for (int l = 0; l < lockers.size(); l++) {
                double d = guns.get(g).distSqr(lockers.get(l));
                if (range >= 0 && d <= max && d < best) {
                    best = d;
                    out[g] = l;
                }
            }
        }
        return out;
    }

    /** Rounds each of {@code lockers} lockers gets: {@code roundsPerGun} for every gun it feeds ({@link #lockerOf}). */
    public static int[] roundsPerLocker(int[] lockerOf, int lockers, int roundsPerGun) {
        int[] out = new int[lockers];
        for (int l : lockerOf) {
            if (l >= 0 && l < lockers) out[l] += Math.max(0, roundsPerGun);
        }
        return out;
    }

    /** {@code count} items as stacks of at most {@code maxStack}, full stacks first. */
    public static List<Integer> stacks(int count, int maxStack) {
        List<Integer> out = new ArrayList<>();
        int size = Math.max(1, maxStack);
        for (int left = Math.max(0, count); left > 0; left -= size) out.add(Math.min(size, left));
        return out;
    }

    /** A gun as {@link #manningOrder} sees it: its world position and the horizontal direction its muzzle points. */
    public record Gun(double x, double z, double dirX, double dirZ) { }

    /**
     * The order in which free hands man {@code guns} against a quarry at ({@code tx}, {@code tz}): the guns whose
     * muzzle points most nearly at it first (largest cosine between the muzzle and the line to the quarry); returns
     * indices into {@code guns}. Guns facing away come last but still get a hand when hands are left.
     */
    public static List<Integer> manningOrder(List<Gun> guns, double tx, double tz) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < guns.size(); i++) out.add(i);
        out.sort(Comparator.comparingDouble((Integer i) -> -bearing(guns.get(i), tx, tz)).thenComparingInt(i -> i));
        return out;
    }

    /** Cosine of the angle between the gun's muzzle and the line from the gun to (tx, tz); 1 when on the gun. */
    static double bearing(Gun g, double tx, double tz) {
        double dx = tx - g.x(), dz = tz - g.z();
        double d = Math.hypot(dx, dz), m = Math.hypot(g.dirX(), g.dirZ());
        if (d < 1e-9 || m < 1e-9) return 1.0;
        return (dx * g.dirX() + dz * g.dirZ()) / (d * m);
    }
}

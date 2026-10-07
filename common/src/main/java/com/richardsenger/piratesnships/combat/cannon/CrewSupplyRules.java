package com.richardsenger.piratesnships.combat.cannon;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rules of crew loading (C9, docs/design.md §6, §8.2): what a gun still needs, which containers are in reach, how
 * many items come out of which stack, how long the load takes and when the crew reloads by itself. No world access,
 * unit tested; {@link CrewSupply} and {@link CrewLoading} apply them to the world.
 */
public final class CrewSupplyRules {

    private CrewSupplyRules() {
    }

    /** What a load still has to put in: gunpowder (0 or 1) and the ammo items. */
    public record Need(int powder, int ammo) {
        public static final Need NOTHING = new Need(0, 0);

        public boolean nothing() {
            return powder == 0 && ammo == 0;
        }
    }

    /**
     * What the gun with load {@code load} still needs to be loaded: an empty gun one powder and {@code ammoCount}
     * ammo, a powdered gun (a player put the powder in) only the ammo, a loaded gun nothing.
     */
    public static Need need(CannonLoad load, int ammoCount) {
        return switch (load) {
            case EMPTY -> new Need(1, ammoCount);
            case POWDER -> new Need(0, ammoCount);
            case LOADED -> Need.NOTHING;
        };
    }

    /** Whether a container at {@code container} is within {@code range} blocks (straight line) of the gun at {@code gun}. */
    public static boolean inRange(BlockPos gun, BlockPos container, int range) {
        return range >= 0 && gun.distSqr(container) <= (double) range * range;
    }

    /**
     * How many items to take from each of the matching stacks {@code available} (counts in supply order: nearest
     * container first, then slot order) to get {@code needed} items: the earlier stacks are used up first, a stack
     * gives at most what it holds. Null when all of them together hold fewer than {@code needed}.
     */
    public static int @Nullable [] takePlan(int[] available, int needed) {
        int[] take = new int[available.length];
        int left = Math.max(0, needed);
        for (int i = 0; i < available.length && left > 0; i++) {
            int n = Math.min(left, Math.max(0, available[i]));
            take[i] = n;
            left -= n;
        }
        return left > 0 ? null : take;
    }

    /** Whether the stacks {@code available} together hold at least {@code needed} items. */
    public static boolean covers(int[] available, int needed) {
        return takePlan(available, needed) != null;
    }

    /**
     * Work ticks of a crew load: the configured {@code loadTicks}, but never done before the reload cooldown
     * ({@code reloadLeft} ticks) is over, since powder can't go in before that.
     */
    public static int loadTicks(int loadTicks, long reloadLeft) {
        return (int) Math.max(1, Math.max(loadTicks, Math.min(Integer.MAX_VALUE, reloadLeft)));
    }

    /**
     * Work ticks of a "Fire!" given while the crew is still loading: the rest of the load ({@code loadRemaining}),
     * then the fuse. The crew finishes loading and fires instead of dropping the load.
     */
    public static int fireAfterLoad(int loadRemaining, int fuseTicks) {
        return Math.max(0, loadRemaining) + fuseTicks;
    }

    /** Whether the crew loads the gun again by itself right after its shot. */
    public static boolean reloadAfterShot(boolean crewLoading, boolean autoReload, boolean fired) {
        return crewLoading && autoReload && fired;
    }
}

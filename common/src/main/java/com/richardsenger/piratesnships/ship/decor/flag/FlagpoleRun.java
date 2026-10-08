package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.ship.decor.FlagpoleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

/**
 * A pole of stacked flagpole blocks (VIS1a, docs/design.md §4.7 "Tall poles"): a vertical run of contiguous flagpole
 * blocks. The top block is the <b>head</b> and owns the flag; the blocks below are <b>shafts</b> that fly nothing and
 * forward every use to the head. The run is read from the blocks, never from {@link FlagpolePart}, so a stale part
 * cannot move the flag. With {@code flags.stacked_poles} off every block is its own one-block pole.
 * <p>The walks are capped at {@link #WALK_LIMIT} blocks, far above {@code flags.max_pole_height}; only a pole built
 * with stacking off and then switched on can be longer than the configured limit.
 */
public final class FlagpoleRun {

    /** Longest run any walk follows. */
    static final int WALK_LIMIT = 64;

    private FlagpoleRun() {
    }

    /** Whether poles stack into one (the server toggle; the client reads the synced value or the default). */
    public static boolean stacked() {
        return FlagConfig.STACKED_POLES.get();
    }

    public static boolean isPole(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof FlagpoleBlock;
    }

    /** Contiguous flagpole blocks directly above {@code pos} (not counting {@code pos}), up to {@code limit}. */
    public static int countAbove(BlockGetter level, BlockPos pos, int limit) {
        int n = 0;
        BlockPos.MutableBlockPos p = pos.mutable();
        while (n < limit && isPole(level, p.move(0, 1, 0))) n++;
        return n;
    }

    /** Contiguous flagpole blocks directly below {@code pos} (not counting {@code pos}), up to {@code limit}. */
    public static int countBelow(BlockGetter level, BlockPos pos, int limit) {
        int n = 0;
        BlockPos.MutableBlockPos p = pos.mutable();
        while (n < limit && isPole(level, p.move(0, -1, 0))) n++;
        return n;
    }

    /** The head of the pole {@code pos} belongs to: the top of its run, or {@code pos} itself with stacking off. */
    public static BlockPos head(BlockGetter level, BlockPos pos) {
        if (!stacked()) return pos;
        return pos.above(countAbove(level, pos, WALK_LIMIT));
    }

    /** Whether {@code pos} is the head of its pole (always with stacking off). */
    public static boolean isHead(BlockGetter level, BlockPos pos) {
        return !stacked() || !isPole(level, pos.above());
    }

    /**
     * Blocks in the pole whose head is {@code head} (the head and the shafts below it), at least 1; always 1 with
     * stacking off.
     */
    public static int height(BlockGetter level, BlockPos head) {
        if (!stacked()) return 1;
        return 1 + countBelow(level, head, WALK_LIMIT);
    }

    /** The block entity that owns the flag of the pole {@code pos} belongs to (the head's), or null. */
    public static @Nullable FlagpoleBlockEntity owner(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(head(level, pos)) instanceof FlagpoleBlockEntity be ? be : null;
    }

    /**
     * Whether a new flagpole block with {@code below} flagpole blocks under it and {@code above} on it would make a
     * pole longer than {@code maxHeight} (pure).
     */
    public static boolean tooTall(int below, int above, int maxHeight) {
        return below + 1 + above > maxHeight;
    }
}

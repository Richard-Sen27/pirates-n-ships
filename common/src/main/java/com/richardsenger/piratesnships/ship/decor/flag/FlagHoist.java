package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleState.Action;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleState.Pending;
import net.minecraft.world.item.ItemStack;

/**
 * Where the cloth is on the pole while an action is pending (VIS1a, docs/design.md §4.7 "Animated hoisting"; pure,
 * render only). The cloth (one block high, {@link FlagClothModel}) flies at the head with its bottom on the head
 * block's floor; while a hoist or raise is pending it runs up from the foot of the pole, while a strike or take-down is
 * pending it runs down to the foot, linearly over the action's ticks ({@link Pending#startTick()} to
 * {@link Pending#finishTick()}). At the foot the cloth's bottom is {@link #FOOT_DIP} below the foot block's floor, so
 * even a one-block pole shows a short run.
 * <p>What is drawn: during a hoist the flag being hoisted (its kind and item, for a banner's tint); during a raise,
 * strike or take-down the pole's flag; otherwise what the pole shows. After a strike nothing is drawn, and a cancelled
 * action snaps back to what the state says, both because the state then has no pending action.
 */
public final class FlagHoist {

    /** How far below the foot block's floor the cloth's bottom starts a hoist or ends a strike (blocks). */
    public static final double FOOT_DIP = 0.25;

    /**
     * One frame of the cloth.
     *
     * @param kind what to draw, {@link FlagKind#NONE} for nothing
     * @param item the flag item drawn (for a banner's tint), empty when nothing is drawn
     * @param drop how far below its place at the head the cloth is drawn (blocks, 0 at the head)
     */
    public record Frame(FlagKind kind, ItemStack item, double drop) {
        public static final Frame NOTHING = new Frame(FlagKind.NONE, ItemStack.EMPTY, 0);

        public int tint() {
            return FlagTint.clothTint(kind, item);
        }
    }

    private FlagHoist() {
    }

    /** The distance the cloth travels on a pole of {@code poleHeight} blocks (at least one). */
    public static double travel(int poleHeight) {
        return Math.max(1, poleHeight) - 1 + FOOT_DIP;
    }

    /** How far an action is done, 0 at its start, 1 at (and after) its finish; 1 when it has no length. */
    public static double progress(long startTick, long finishTick, double now) {
        if (finishTick <= startTick) return 1.0;
        double p = (now - startTick) / (double) (finishTick - startTick);
        return Math.max(0.0, Math.min(1.0, p));
    }

    /** Whether the action runs the cloth up the pole (hoist, raise) rather than down (strike, take down). */
    public static boolean rising(Action action) {
        return action == Action.HOIST || action == Action.RAISE;
    }

    /** The cloth's drop below the head (blocks) at {@code now} during {@code action}. */
    public static double drop(Action action, long startTick, long finishTick, double now, int poleHeight) {
        double p = progress(startTick, finishTick, now);
        return (rising(action) ? 1.0 - p : p) * travel(poleHeight);
    }

    /**
     * The frame to draw for a pole in {@code state} at game time {@code now} (ticks plus partial tick), on a pole of
     * {@code poleHeight} blocks. With {@code animate} off it is what the pole shows, at the head (the behaviour before
     * VIS1a).
     */
    public static Frame frame(FlagpoleState state, double now, int poleHeight, boolean animate) {
        Frame still = still(state);
        if (!animate || state.pending().isEmpty()) return still;
        Pending p = state.pending().get();
        double drop = drop(p.action(), p.startTick(), p.finishTick(), now, poleHeight);
        return switch (p.action()) {
            case HOIST -> p.kind() == FlagKind.NONE ? still : new Frame(p.kind(), p.item(), drop);
            case RAISE -> state.hasFlag() && state.struck() ? new Frame(state.kind(), state.flagItem(), drop) : still;
            // a struck flag is down already: taking it down draws nothing
            case STRIKE, TAKE_DOWN -> state.hasFlag() && !state.struck() ? new Frame(state.kind(), state.flagItem(), drop) : still;
        };
    }

    /**
     * Which block of the pole draws the cloth at {@code drop}: how many blocks below the head (0 = the head, at most
     * {@code poleHeight - 1} = the foot). It is the block holding the cloth's middle, so the cloth is drawn (and lit
     * and culled) by a block next to it, never by a head whose chunk section may be out of view.
     */
    public static int drawerBelowHead(double drop, int poleHeight) {
        int below = (int) Math.floor(drop + 0.5);
        return Math.max(0, Math.min(Math.max(1, poleHeight) - 1, below));
    }

    private static Frame still(FlagpoleState state) {
        FlagKind shown = state.reading().shown();
        return shown == FlagKind.NONE ? Frame.NOTHING : new Frame(shown, state.flagItem(), 0);
    }
}

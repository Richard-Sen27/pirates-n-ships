package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleState.Action;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleState.Pending;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The flagpole's rules as a pure state machine (docs/design.md §4.7: "Changing the flag takes a few seconds at the
 * flagpole"). No world access: the block entity feeds in the game time and the configured delay, then applies the
 * {@link Outcome} (new state, items to hand out, feedback, change notification).
 *
 * <ul>
 *   <li>{@link Input.Hoist} (using a flag item): the pole takes the item at once and hoists it after the delay; the
 *       flag that was up goes back to the actor. Hoisting also raises struck colors.</li>
 *   <li>{@link Input.Toggle} (empty hand): strike the colors, or raise them if struck, after the delay.</li>
 *   <li>{@link Input.TakeDown} (sneaking, empty hand): take the flag down for good after the delay; it goes back to
 *       the actor.</li>
 *   <li>Any input while an action is pending cancels it first (a pending flag item goes back to whoever started
 *       it). An empty-hand input then stops there ("click again to cancel"); a flag item starts its own hoist.</li>
 *   <li>A delay of 0 or less completes the action immediately.</li>
 * </ul>
 */
public final class FlagpoleMachine {

    /** What the player did at the pole. */
    public sealed interface Input {
        record Hoist(FlagKind kind, ItemStack item) implements Input {
            public Hoist {
                if (kind == FlagKind.NONE || item.isEmpty()) throw new IllegalArgumentException("hoisting needs a flag");
            }
        }

        record Toggle() implements Input {
        }

        record TakeDown() implements Input {
        }
    }

    /** Translatable feedback, key {@code message.pirates_n_ships.flag.<id>}. */
    public enum Feedback {
        NONE, NO_FLAG, CANCELLED, STARTED_HOIST, STARTED_STRIKE, STARTED_RAISE, STARTED_TAKE_DOWN, HOISTED, STRUCK, RAISED, TAKEN_DOWN;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Why the shown flag changed, for {@link FlagpoleEvents}. */
    public enum Cause {
        HOISTED, STRUCK, RAISED, TAKEN_DOWN, BROKEN, COMMAND
    }

    /** An item to hand out: to {@code recipient}, or dropped at the pole when the recipient is null. */
    public record Delivery(@Nullable UUID recipient, ItemStack stack) {
    }

    /**
     * @param feedbackTo who the feedback is for (the actor of the completed or cancelled action)
     * @param cause      set when the flag on the pole or its struck state changed
     */
    public record Outcome(FlagpoleState state, List<Delivery> deliveries, Feedback feedback, @Nullable UUID feedbackTo,
                          Optional<Cause> cause) {
        public Outcome {
            deliveries = List.copyOf(deliveries);
        }
    }

    private FlagpoleMachine() {
    }

    public static Outcome use(FlagpoleState state, Input input, UUID actor, long now, int delayTicks) {
        List<Delivery> deliveries = new ArrayList<>();
        if (state.pending().isPresent()) {
            Pending p = state.pending().get();
            if (!p.item().isEmpty()) deliveries.add(new Delivery(p.actor(), p.item()));
            state = state.withPending(Optional.empty());
            if (!(input instanceof Input.Hoist)) return new Outcome(state, deliveries, Feedback.CANCELLED, actor, Optional.empty());
        }
        Pending pending;
        Feedback started;
        switch (input) {
            case Input.Hoist h -> {
                pending = new Pending(Action.HOIST, actor, now + delayTicks, h.kind(), h.item().copyWithCount(1));
                started = Feedback.STARTED_HOIST;
            }
            case Input.Toggle t -> {
                if (!state.hasFlag()) return new Outcome(state, deliveries, Feedback.NO_FLAG, actor, Optional.empty());
                Action a = state.struck() ? Action.RAISE : Action.STRIKE;
                pending = new Pending(a, actor, now + delayTicks, FlagKind.NONE, ItemStack.EMPTY);
                started = a == Action.RAISE ? Feedback.STARTED_RAISE : Feedback.STARTED_STRIKE;
            }
            case Input.TakeDown d -> {
                if (!state.hasFlag()) return new Outcome(state, deliveries, Feedback.NO_FLAG, actor, Optional.empty());
                pending = new Pending(Action.TAKE_DOWN, actor, now + delayTicks, FlagKind.NONE, ItemStack.EMPTY);
                started = Feedback.STARTED_TAKE_DOWN;
            }
        }
        FlagpoleState next = state.withPending(Optional.of(pending));
        if (delayTicks <= 0) {
            Outcome done = complete(next);
            deliveries.addAll(done.deliveries());
            return new Outcome(done.state(), deliveries, done.feedback(), done.feedbackTo(), done.cause());
        }
        return new Outcome(next, deliveries, started, actor, Optional.empty());
    }

    /** Completes the pending action once {@code now} reaches its finish tick; otherwise nothing happens. */
    public static Outcome tick(FlagpoleState state, long now) {
        Optional<Pending> p = state.pending();
        if (p.isEmpty() || now < p.get().finishTick()) return unchanged(state);
        return complete(state);
    }

    /** Cancels the pending action, if any; a pending flag item goes back to whoever started it. */
    public static Outcome cancel(FlagpoleState state) {
        if (state.pending().isEmpty()) return unchanged(state);
        Pending p = state.pending().get();
        List<Delivery> deliveries = p.item().isEmpty() ? List.of() : List.of(new Delivery(p.actor(), p.item()));
        return new Outcome(state.withPending(Optional.empty()), deliveries, Feedback.CANCELLED, p.actor(), Optional.empty());
    }

    /** The pole is gone: everything it holds drops at its position, a pending action dies with it. */
    public static Outcome breakPole(FlagpoleState state) {
        List<Delivery> drops = new ArrayList<>();
        if (state.hasFlag()) drops.add(new Delivery(null, state.flagItem()));
        state.pending().filter(p -> !p.item().isEmpty()).ifPresent(p -> drops.add(new Delivery(null, p.item())));
        return new Outcome(FlagpoleState.EMPTY, drops, Feedback.NONE, null,
                state.hasFlag() ? Optional.of(Cause.BROKEN) : Optional.empty());
    }

    /**
     * Operator override without delay ({@code /pirates flag set}): replaces the flag, cancels anything pending, and
     * drops the old flag item and a pending item at the pole so nothing is lost.
     *
     * @param item the new flag item, or empty to remove the flag
     */
    public static Outcome set(FlagpoleState state, FlagKind kind, ItemStack item, boolean struck, @Nullable UUID actor) {
        List<Delivery> drops = new ArrayList<>();
        if (state.hasFlag()) drops.add(new Delivery(null, state.flagItem()));
        state.pending().filter(p -> !p.item().isEmpty()).ifPresent(p -> drops.add(new Delivery(null, p.item())));
        FlagpoleState next = new FlagpoleState(kind, item.copyWithCount(item.isEmpty() ? 0 : 1), struck,
                Optional.ofNullable(actor), Optional.empty());
        return new Outcome(next, drops, Feedback.NONE, actor, Optional.of(Cause.COMMAND));
    }

    /** Operator override without delay: strike or raise the current flag (no-op without a flag). */
    public static Outcome setStruck(FlagpoleState state, boolean struck) {
        if (!state.hasFlag() || state.struck() == struck) return unchanged(state);
        FlagpoleState next = new FlagpoleState(state.kind(), state.flagItem(), struck, state.hoistedBy(), state.pending());
        return new Outcome(next, List.of(), Feedback.NONE, null, Optional.of(Cause.COMMAND));
    }

    private static Outcome complete(FlagpoleState state) {
        Pending p = state.pending().orElseThrow();
        FlagpoleState base = state.withPending(Optional.empty());
        return switch (p.action()) {
            case HOIST -> {
                List<Delivery> back = base.hasFlag() ? List.of(new Delivery(p.actor(), base.flagItem())) : List.of();
                FlagpoleState next = new FlagpoleState(p.kind(), p.item(), false, Optional.of(p.actor()), Optional.empty());
                yield new Outcome(next, back, Feedback.HOISTED, p.actor(), Optional.of(Cause.HOISTED));
            }
            case STRIKE, RAISE -> {
                if (!base.hasFlag()) yield new Outcome(base, List.of(), Feedback.NO_FLAG, p.actor(), Optional.empty());
                boolean strike = p.action() == Action.STRIKE;
                Optional<UUID> by = strike ? base.hoistedBy() : Optional.of(p.actor());
                FlagpoleState next = new FlagpoleState(base.kind(), base.flagItem(), strike, by, Optional.empty());
                yield new Outcome(next, List.of(), strike ? Feedback.STRUCK : Feedback.RAISED, p.actor(),
                        Optional.of(strike ? Cause.STRUCK : Cause.RAISED));
            }
            case TAKE_DOWN -> {
                if (!base.hasFlag()) yield new Outcome(base, List.of(), Feedback.NO_FLAG, p.actor(), Optional.empty());
                yield new Outcome(FlagpoleState.EMPTY, List.of(new Delivery(p.actor(), base.flagItem())), Feedback.TAKEN_DOWN,
                        p.actor(), Optional.of(Cause.TAKEN_DOWN));
            }
        };
    }

    private static Outcome unchanged(FlagpoleState state) {
        return new Outcome(state, List.of(), Feedback.NONE, null, Optional.empty());
    }
}

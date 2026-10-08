package com.richardsenger.piratesnships.ship.decor.flag;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything a flagpole stores (immutable value; the item stacks are copied in and must not be mutated).
 *
 * @param kind      the flag on the pole, {@link FlagKind#NONE} if there is none
 * @param flagItem  the item that was hoisted (one of our flag items or a banner with its patterns), given back when
 *                  the flag comes down; empty exactly when {@code kind} is {@code NONE}
 * @param struck    colors lowered: the pole keeps the flag but shows none
 * @param hoistedBy who hoisted (or last raised) the flag
 * @param pending   an action in progress (hoisting takes a few seconds, §4.7)
 */
public record FlagpoleState(FlagKind kind, ItemStack flagItem, boolean struck, Optional<UUID> hoistedBy, Optional<Pending> pending) {

    public static final FlagpoleState EMPTY = new FlagpoleState(FlagKind.NONE, ItemStack.EMPTY, false, Optional.empty(), Optional.empty());

    /** The actions at the pole that take the hoisting delay. */
    public enum Action implements StringRepresentable {
        HOIST("hoist"), STRIKE("strike"), RAISE("raise"), TAKE_DOWN("take_down");

        public static final Codec<Action> CODEC = StringRepresentable.fromEnum(Action::values);
        private final String id;

        Action(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    /**
     * An action in progress.
     *
     * @param startTick  game time at which it started (VIS1a: the client's hoisting animation runs from here to
     *                   {@code finishTick}); a pole saved before VIS1a loads with {@code startTick == finishTick},
     *                   which draws no animation
     * @param finishTick game time at which it completes
     * @param kind       for {@link Action#HOIST}: the kind of the new flag, otherwise {@code NONE}
     * @param item       for {@link Action#HOIST}: the new flag item, held by the pole until the hoist completes or is
     *                   cancelled (then it goes back to {@code actor}); otherwise empty
     */
    public record Pending(Action action, UUID actor, long startTick, long finishTick, FlagKind kind, ItemStack item) {

        public static final Codec<Pending> CODEC = RecordCodecBuilder.create(i -> i.group(
                Action.CODEC.fieldOf("action").forGetter(Pending::action),
                UUIDUtil.CODEC.fieldOf("actor").forGetter(Pending::actor),
                Codec.LONG.optionalFieldOf("start_tick").forGetter(p -> Optional.of(p.startTick())),
                Codec.LONG.fieldOf("finish_tick").forGetter(Pending::finishTick),
                FlagKind.CODEC.optionalFieldOf("kind", FlagKind.NONE).forGetter(Pending::kind),
                ItemStack.OPTIONAL_CODEC.optionalFieldOf("item", ItemStack.EMPTY).forGetter(Pending::item)
        ).apply(i, (action, actor, start, finish, kind, item) -> new Pending(action, actor, start.orElse(finish), finish, kind, item)));

        public Pending {
            item = item.copy();
            if (startTick > finishTick) startTick = finishTick;
        }

        /** An action without a known start (draws no animation): what a pole saved before VIS1a holds. */
        public Pending(Action action, UUID actor, long finishTick, FlagKind kind, ItemStack item) {
            this(action, actor, finishTick, finishTick, kind, item);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Pending p && action == p.action && actor.equals(p.actor) && startTick == p.startTick
                    && finishTick == p.finishTick && kind == p.kind && ItemStack.matches(item, p.item);
        }

        @Override
        public int hashCode() {
            return Objects.hash(action, actor, startTick, finishTick, kind, ItemStack.hashItemAndComponents(item));
        }
    }

    public static final Codec<FlagpoleState> CODEC = RecordCodecBuilder.create(i -> i.group(
            FlagKind.CODEC.optionalFieldOf("kind", FlagKind.NONE).forGetter(FlagpoleState::kind),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("item", ItemStack.EMPTY).forGetter(FlagpoleState::flagItem),
            Codec.BOOL.optionalFieldOf("struck", false).forGetter(FlagpoleState::struck),
            UUIDUtil.CODEC.optionalFieldOf("hoisted_by").forGetter(FlagpoleState::hoistedBy),
            Pending.CODEC.optionalFieldOf("pending").forGetter(FlagpoleState::pending)
    ).apply(i, FlagpoleState::new));

    public FlagpoleState {
        flagItem = flagItem.copy();
        if (kind == FlagKind.NONE || flagItem.isEmpty()) {
            kind = FlagKind.NONE;
            flagItem = ItemStack.EMPTY;
            struck = false;
        }
    }

    public boolean hasFlag() {
        return kind != FlagKind.NONE;
    }

    /** What the pole shows; see {@link FlagReading}. */
    public FlagReading reading() {
        if (!hasFlag()) return FlagReading.NO_FLAG;
        return struck ? FlagReading.struck(kind) : FlagReading.flying(kind);
    }

    public FlagpoleState withPending(Optional<Pending> newPending) {
        return new FlagpoleState(kind, flagItem, struck, hoistedBy, newPending);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FlagpoleState s && kind == s.kind && ItemStack.matches(flagItem, s.flagItem) && struck == s.struck
                && hoistedBy.equals(s.hoistedBy) && pending.equals(s.pending);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, ItemStack.hashItemAndComponents(flagItem), struck, hoistedBy, pending);
    }
}

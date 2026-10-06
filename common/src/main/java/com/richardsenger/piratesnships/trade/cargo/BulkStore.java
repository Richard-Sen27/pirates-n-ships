package com.richardsenger.piratesnships.trade.cargo;

import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The content of a bulk cargo container: one kind of item (item + components, so a plundered and a clean stack are
 * different kinds) and a count, up to a capacity that depends on the kind. No world access; the block entity wraps it.
 *
 * <p>Rules: an empty store accepts any stack that {@code accepts} allows and that is stackable (max stack size above 1,
 * which keeps out tools, filled crates, shulker boxes and bundles). A non-empty store accepts only its own kind. A stack
 * of the same item whose plunder state differs is refused with {@link Refusal#PLUNDER_MIX}, never merged, so plunder
 * can't be laundered by pouring it into a clean crate. When the last item leaves, the store forgets its kind.
 */
public final class BulkStore {

    public enum Refusal {
        NONE, EMPTY_STACK, NOT_STACKABLE, NOT_ACCEPTED, OTHER_KIND, PLUNDER_MIX, FULL;

        public String translationKey() {
            return "pirates_n_ships.cargo.refused." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * @param capacity    how many items of a kind fit (the stack passed has count 1)
     * @param accepts     whether an empty store takes this kind at all
     * @param plundered   whether a stack carries the plunder mark (only used to name the refusal)
     */
    public record Rules(ToIntFunction<ItemStack> capacity, Predicate<ItemStack> accepts, Predicate<ItemStack> plundered) {
    }

    private ItemStack kind = ItemStack.EMPTY;
    private int count;

    public BulkStore() {
    }

    public BulkStore(ItemStack kind, int count) {
        set(kind, count);
    }

    public boolean isEmpty() {
        return count <= 0 || kind.isEmpty();
    }

    /** The held kind as a count-1 copy, or {@link ItemStack#EMPTY}. */
    public ItemStack kind() {
        return isEmpty() ? ItemStack.EMPTY : kind.copyWithCount(1);
    }

    /** The live kind stack (count 1); don't mutate. */
    ItemStack kindView() {
        return isEmpty() ? ItemStack.EMPTY : kind;
    }

    public int count() {
        return isEmpty() ? 0 : count;
    }

    public void set(ItemStack newKind, int newCount) {
        if (newKind.isEmpty() || newCount <= 0) {
            kind = ItemStack.EMPTY;
            count = 0;
        } else {
            kind = newKind.copyWithCount(1);
            count = newCount;
        }
    }

    public void clear() {
        set(ItemStack.EMPTY, 0);
    }

    public boolean isSameKind(ItemStack stack) {
        return !isEmpty() && ItemStack.isSameItemSameComponents(kind, stack);
    }

    /** Capacity for the held kind (0 when empty). */
    public int capacity(Rules rules) {
        return isEmpty() ? 0 : Math.max(0, rules.capacity().applyAsInt(kind));
    }

    /** Why {@code stack} can't go in at all (ignores how many fit, except a full store). */
    public Refusal check(ItemStack stack, Rules rules) {
        if (stack.isEmpty()) return Refusal.EMPTY_STACK;
        if (isEmpty()) {
            if (stack.getMaxStackSize() <= 1) return Refusal.NOT_STACKABLE;
            if (!rules.accepts().test(stack)) return Refusal.NOT_ACCEPTED;
            return rules.capacity().applyAsInt(stack.copyWithCount(1)) > 0 ? Refusal.NONE : Refusal.FULL;
        }
        if (!ItemStack.isSameItemSameComponents(kind, stack)) {
            boolean sameItem = ItemStack.isSameItem(kind, stack);
            return sameItem && rules.plundered().test(kind) != rules.plundered().test(stack) ? Refusal.PLUNDER_MIX : Refusal.OTHER_KIND;
        }
        return count >= capacity(rules) ? Refusal.FULL : Refusal.NONE;
    }

    /** How many items of {@code stack}'s kind still fit (0 if refused). */
    public int room(ItemStack stack, Rules rules) {
        if (check(stack, rules) != Refusal.NONE) return 0;
        int cap = isEmpty() ? rules.capacity().applyAsInt(stack.copyWithCount(1)) : capacity(rules);
        return Math.max(0, cap - count());
    }

    /** Inserts up to {@code stack.getCount()} items; returns how many went in. Never modifies {@code stack}. */
    public int insert(ItemStack stack, Rules rules, boolean simulate) {
        int n = Math.min(stack.getCount(), room(stack, rules));
        if (n <= 0 || simulate) return Math.max(0, n);
        if (isEmpty()) set(stack, n);
        else count += n;
        return n;
    }

    /** Inserts {@code n} items of {@code kindStack}'s kind if all of them fit, else nothing. */
    public boolean insertAll(ItemStack kindStack, int n, Rules rules) {
        if (n <= 0 || room(kindStack, rules) < n) return false;
        if (isEmpty()) set(kindStack, n);
        else count += n;
        return true;
    }

    /** Takes up to {@code max} items as one stack (may exceed a normal stack; split before giving it to a player). */
    public ItemStack extract(int max, boolean simulate) {
        int n = Math.min(Math.max(0, max), count());
        if (n == 0) return ItemStack.EMPTY;
        ItemStack out = kind.copyWithCount(n);
        if (!simulate) {
            count -= n;
            if (count <= 0) clear();
        }
        return out;
    }

    /** Fill level 0..15 like a comparator: 0 only when empty, 15 only when full. */
    public int signal(Rules rules) {
        if (isEmpty()) return 0;
        int cap = capacity(rules);
        if (cap <= 0 || count >= cap) return 15;
        return 1 + (int) Math.floor(14.0 * count / cap);
    }

    /** Crate capacity: {@code stacks} full stacks of the kind. */
    public static int stacksCapacity(ItemStack kind, int stacks) {
        long cap = (long) Math.max(0, stacks) * Math.max(1, kind.getMaxStackSize());
        return (int) Math.min(Integer.MAX_VALUE, cap);
    }
}

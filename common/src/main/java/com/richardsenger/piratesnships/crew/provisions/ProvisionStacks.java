package com.richardsenger.piratesnships.crew.provisions;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bridges real container contents and the pure {@link ProvisionStore}. The pantry block uses it like this:
 *
 * <pre>{@code
 * ProvisionSettings s = ProvisionsConfig.settings();
 * store = ProvisionStacks.toStore(items, s, store);                        // after the container changed
 * ProvisionUpdate u = ProvisionRules.advance(store, crewState, headcount, s, ticksSinceLastUpdate);
 * List<ItemStack> extra = ProvisionStacks.apply(container, ProvisionStacks.removals(items, u.outcome().removed(), s));
 * store = u.store(); crewState = u.state();                                 // persist both; drop or insert `extra`
 * }</pre>
 */
public final class ProvisionStacks {

    private ProvisionStacks() {
    }

    /** One instruction: take {@code count} items out of {@code slot}; each leaves {@code leftover} behind. */
    public record StackRemoval(int slot, int count, ItemStack leftover) {
    }

    /** Units present per provision type id. Non-provision stacks are ignored. */
    public static Map<String, ProvisionStore.Counted> count(List<ItemStack> stacks, ProvisionSettings s) {
        Map<String, ProvisionStore.Counted> out = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            Optional<ProvisionType> type = ProvisionClassifier.classify(stack, s);
            type.ifPresent(t -> out.merge(t.id(), new ProvisionStore.Counted(t, stack.getCount()),
                    (a, b) -> new ProvisionStore.Counted(b.type(), a.units() + b.units())));
        }
        return out;
    }

    /** The store for these stacks, keeping the ages {@code previous} knows (see {@link ProvisionStore#reconcile}). */
    public static ProvisionStore toStore(List<ItemStack> stacks, ProvisionSettings s, ProvisionStore previous) {
        return previous.reconcile(count(stacks, s));
    }

    /**
     * Turns "remove N units of type X" (e.g. {@link ProvisionOutcome#removed()}) into per-slot instructions. Units
     * are taken from the last matching slot first. Units that are not in the stacks any more are skipped.
     */
    public static List<StackRemoval> removals(List<ItemStack> stacks, Map<String, Integer> toRemove, ProvisionSettings s) {
        Map<String, Integer> left = new LinkedHashMap<>(toRemove);
        List<StackRemoval> out = new ArrayList<>();
        for (int slot = stacks.size() - 1; slot >= 0; slot--) {
            ItemStack stack = stacks.get(slot);
            Optional<ProvisionType> type = ProvisionClassifier.classify(stack, s);
            if (type.isEmpty()) {
                continue;
            }
            int want = left.getOrDefault(type.get().id(), 0);
            if (want <= 0) {
                continue;
            }
            int n = Math.min(want, stack.getCount());
            out.add(new StackRemoval(slot, n, ProvisionClassifier.leftover(stack)));
            left.put(type.get().id(), want - n);
        }
        return out;
    }

    /**
     * Applies the instructions to a container. Leftovers (bottles, bowls, buckets) go into the emptied slot when it
     * became empty; whatever doesn't fit there is returned for the caller to insert elsewhere or drop.
     */
    public static List<ItemStack> apply(Container container, List<StackRemoval> removals) {
        List<ItemStack> extra = new ArrayList<>();
        for (StackRemoval r : removals) {
            ItemStack stack = container.getItem(r.slot());
            stack.shrink(r.count());
            int leftoverCount = r.leftover().isEmpty() ? 0 : r.count() * r.leftover().getCount();
            if (stack.isEmpty()) {
                container.setItem(r.slot(), ItemStack.EMPTY);
                if (leftoverCount > 0) {
                    int fit = Math.min(leftoverCount, r.leftover().getMaxStackSize());
                    container.setItem(r.slot(), r.leftover().copyWithCount(fit));
                    leftoverCount -= fit;
                }
            }
            while (leftoverCount > 0) {
                int n = Math.min(leftoverCount, r.leftover().getMaxStackSize());
                extra.add(r.leftover().copyWithCount(n));
                leftoverCount -= n;
            }
        }
        container.setChanged();
        return extra;
    }
}

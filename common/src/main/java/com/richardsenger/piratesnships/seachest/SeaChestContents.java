package com.richardsenger.piratesnships.seachest;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The contents of a sea chest in every state (docs/design.md §11): in the item (and so while worn) they live in
 * vanilla's {@code minecraft:container} component like a shulker box's, in the block entity and the floating entity
 * as a 54-slot list. These helpers move them between the two without copying references, so nothing is shared or
 * duplicated. No registry access: usable in JUnit with a bootstrapped game.
 */
public final class SeaChestContents {

    /** Double-chest capacity. */
    public static final int SIZE = 54;

    private SeaChestContents() {
    }

    /** A fresh empty slot list. */
    public static NonNullList<ItemStack> emptySlots() {
        return NonNullList.withSize(SIZE, ItemStack.EMPTY);
    }

    /** Copies of the item's contents into a fresh 54-slot list (empty slots for a chest without contents). */
    public static NonNullList<ItemStack> fromItem(ItemStack stack) {
        NonNullList<ItemStack> slots = emptySlots();
        stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(slots);
        return slots;
    }

    /**
     * A new chest item of {@code item} holding copies of {@code slots}, with {@code name} as its custom name. An empty
     * chest gets no {@code container} component, so it stacks with fresh ones like an empty shulker box.
     */
    public static ItemStack toItem(Item item, List<ItemStack> slots, @Nullable Component name) {
        ItemStack stack = new ItemStack(item);
        if (!isEmpty(slots)) {
            stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
        }
        if (name != null) {
            stack.set(DataComponents.CUSTOM_NAME, name);
        }
        return stack;
    }

    public static boolean isEmpty(List<ItemStack> slots) {
        for (ItemStack s : slots) {
            if (!s.isEmpty()) return false;
        }
        return true;
    }

    /** Total item count, for tests and messages. */
    public static int count(List<ItemStack> slots) {
        int n = 0;
        for (ItemStack s : slots) n += s.getCount();
        return n;
    }

    /** The non-empty stacks of an item's contents (copies), in slot order. */
    public static List<ItemStack> nonEmpty(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyStream().map(ItemStack::copy).toList();
    }

    /** Whether a stack may go into a sea chest: anything but another sea chest (no nesting). */
    public static boolean canHold(ItemStack stack) {
        return !(stack.getItem() instanceof SeaChestItem);
    }
}

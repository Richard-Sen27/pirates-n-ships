package com.richardsenger.piratesnships.seachest;

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The registered sea chest item is not available in JUnit, so a stick carries the contents here. */
class SeaChestContentsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static NonNullList<ItemStack> sample() {
        NonNullList<ItemStack> slots = SeaChestContents.emptySlots();
        slots.set(0, new ItemStack(Items.DIAMOND, 5));
        slots.set(53, new ItemStack(Items.APPLE, 12));
        return slots;
    }

    @Test
    void fiftyFourSlots() {
        assertEquals(54, SeaChestContents.emptySlots().size());
    }

    @Test
    void roundTripKeepsEverySlot() {
        NonNullList<ItemStack> slots = sample();
        ItemStack item = SeaChestContents.toItem(Items.STICK, slots, Component.literal("Loot"));
        NonNullList<ItemStack> back = SeaChestContents.fromItem(item);
        assertEquals(54, back.size());
        assertTrue(ItemStack.matches(slots.get(0), back.get(0)));
        assertTrue(ItemStack.matches(slots.get(53), back.get(53)), "the last slot of the six rows survives");
        assertEquals(17, SeaChestContents.count(back));
        assertEquals("Loot", item.get(DataComponents.CUSTOM_NAME).getString());
    }

    @Test
    void copiesAreIndependent() {
        NonNullList<ItemStack> slots = sample();
        ItemStack item = SeaChestContents.toItem(Items.STICK, slots, null);
        slots.get(0).setCount(1);
        NonNullList<ItemStack> a = SeaChestContents.fromItem(item);
        assertEquals(5, a.get(0).getCount(), "changing the source after packing doesn't change the item");
        NonNullList<ItemStack> b = SeaChestContents.fromItem(item);
        assertNotSame(a.get(0), b.get(0));
        a.get(0).setCount(2);
        assertEquals(5, b.get(0).getCount(), "unpacking twice gives independent stacks");
    }

    @Test
    void emptyChestHasNoContainerComponentOrName() {
        ItemStack item = SeaChestContents.toItem(Items.STICK, SeaChestContents.emptySlots(), null);
        assertFalse(item.has(DataComponents.CONTAINER));
        assertNull(item.get(DataComponents.CUSTOM_NAME));
        assertTrue(SeaChestContents.isEmpty(SeaChestContents.fromItem(item)));
        assertTrue(SeaChestContents.nonEmpty(item).isEmpty());
    }

    @Test
    void nonEmptyListsStacksInSlotOrder() {
        ItemStack item = SeaChestContents.toItem(Items.STICK, sample(), null);
        List<ItemStack> list = SeaChestContents.nonEmpty(item);
        assertEquals(2, list.size());
        assertTrue(list.get(0).is(Items.DIAMOND));
        assertTrue(list.get(1).is(Items.APPLE));
    }

    @Test
    void holdsOrdinaryItemsAndShulkerBoxes() {
        assertTrue(SeaChestContents.canHold(new ItemStack(Items.DIAMOND)));
        assertTrue(SeaChestContents.canHold(new ItemStack(Items.SHULKER_BOX)));
        assertTrue(SeaChestContents.canHold(ItemStack.EMPTY));
    }
}

package com.richardsenger.piratesnships.trade.cargo;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Insert and extract rules of the bulk store. A vanilla unit component stands in for the plunder mark. */
class BulkStoreTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ItemStack mark(ItemStack s) {
        s.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
        return s;
    }

    private static final BulkStore.Rules CRATE = new BulkStore.Rules(k -> BulkStore.stacksCapacity(k, 4), k -> true, s -> s.has(DataComponents.HIDE_TOOLTIP));
    private static final BulkStore.Rules GOODS_ONLY = new BulkStore.Rules(k -> 100, k -> k.is(Items.SUGAR), s -> false);

    @Test
    void emptyStoreTakesStackablesAndLearnsTheKind() {
        BulkStore s = new BulkStore();
        assertEquals(64, s.insert(new ItemStack(Items.SUGAR, 64), CRATE, false));
        assertEquals(64, s.count());
        assertTrue(s.kind().is(Items.SUGAR));
        assertEquals(256 - 64, s.room(new ItemStack(Items.SUGAR), CRATE));
        assertEquals(BulkStore.Refusal.NOT_STACKABLE, new BulkStore().check(new ItemStack(Items.DIAMOND_SWORD), CRATE));
        assertEquals(BulkStore.Refusal.EMPTY_STACK, s.check(ItemStack.EMPTY, CRATE));
    }

    @Test
    void capacityDependsOnTheStackSize() {
        assertEquals(256, BulkStore.stacksCapacity(new ItemStack(Items.SUGAR), 4));
        assertEquals(64, BulkStore.stacksCapacity(new ItemStack(Items.EGG), 4));
    }

    @Test
    void otherKindsAndPlunderMixingAreRefused() {
        BulkStore s = new BulkStore(new ItemStack(Items.SUGAR), 10);
        assertEquals(BulkStore.Refusal.OTHER_KIND, s.check(new ItemStack(Items.DIRT), CRATE));
        assertEquals(BulkStore.Refusal.PLUNDER_MIX, s.check(mark(new ItemStack(Items.SUGAR)), CRATE));
        assertEquals(0, s.insert(mark(new ItemStack(Items.SUGAR, 5)), CRATE, false));
        assertEquals(10, s.count());
        BulkStore plundered = new BulkStore(mark(new ItemStack(Items.SUGAR)), 10);
        assertEquals(BulkStore.Refusal.PLUNDER_MIX, plundered.check(new ItemStack(Items.SUGAR), CRATE));
        assertEquals(BulkStore.Refusal.NONE, plundered.check(mark(new ItemStack(Items.SUGAR)), CRATE));
    }

    @Test
    void insertStopsAtCapacityAndInsertAllIsAllOrNothing() {
        BulkStore s = new BulkStore(new ItemStack(Items.SUGAR), 250);
        ItemStack in = new ItemStack(Items.SUGAR, 64);
        assertEquals(6, s.insert(in, CRATE, false));
        assertEquals(64, in.getCount(), "the passed stack is never modified");
        assertEquals(BulkStore.Refusal.FULL, s.check(in, CRATE));
        BulkStore t = new BulkStore(new ItemStack(Items.SUGAR), 250);
        assertFalse(t.insertAll(new ItemStack(Items.SUGAR), 7, CRATE));
        assertEquals(250, t.count());
        assertTrue(t.insertAll(new ItemStack(Items.SUGAR), 6, CRATE));
        assertEquals(256, t.count());
    }

    @Test
    void simulateChangesNothing() {
        BulkStore s = new BulkStore(new ItemStack(Items.SUGAR), 10);
        assertEquals(5, s.insert(new ItemStack(Items.SUGAR, 5), CRATE, true));
        assertEquals(4, s.extract(4, true).getCount());
        assertEquals(10, s.count());
    }

    @Test
    void extractEmptiesAndForgetsTheKind() {
        BulkStore s = new BulkStore(new ItemStack(Items.SUGAR), 70);
        assertEquals(64, s.extract(64, false).getCount());
        ItemStack rest = s.extract(64, false);
        assertEquals(6, rest.getCount());
        assertTrue(s.isEmpty());
        assertTrue(s.kind().isEmpty());
        assertEquals(BulkStore.Refusal.NONE, s.check(new ItemStack(Items.DIRT), CRATE));
        assertTrue(s.extract(1, false).isEmpty());
    }

    @Test
    void emptyStoreHonoursTheAcceptRule() {
        BulkStore s = new BulkStore();
        assertEquals(BulkStore.Refusal.NOT_ACCEPTED, s.check(new ItemStack(Items.DIRT), GOODS_ONLY));
        assertEquals(BulkStore.Refusal.NONE, s.check(new ItemStack(Items.SUGAR), GOODS_ONLY));
    }

    @Test
    void signalIsZeroOnlyWhenEmptyAndFifteenOnlyWhenFull() {
        assertEquals(0, new BulkStore().signal(CRATE));
        assertEquals(1, new BulkStore(new ItemStack(Items.SUGAR), 1).signal(CRATE));
        assertEquals(14, new BulkStore(new ItemStack(Items.SUGAR), 255).signal(CRATE));
        assertEquals(15, new BulkStore(new ItemStack(Items.SUGAR), 256).signal(CRATE));
    }

    @Test
    void bulkCargoEqualityAndCodec() {
        BulkCargo a = new BulkCargo(new ItemStack(Items.SUGAR, 5), 300);
        assertEquals(a, new BulkCargo(new ItemStack(Items.SUGAR), 300));
        assertFalse(a.equals(new BulkCargo(mark(new ItemStack(Items.SUGAR)), 300)));
        var json = BulkCargo.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, a).getOrThrow();
        assertEquals(a, BulkCargo.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow());
    }
}

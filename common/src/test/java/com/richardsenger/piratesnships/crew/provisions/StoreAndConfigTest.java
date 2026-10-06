package com.richardsenger.piratesnships.crew.provisions;

import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.richardsenger.piratesnships.crew.provisions.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class StoreAndConfigTest {

    @AfterEach
    void resetConfig() {
        ProvisionsConfig.CONSUMPTION_ENABLED.reset();
        ProvisionsConfig.SCURVY_ONSET_DAYS.reset();
    }

    @Test
    void storeCodecRoundTrip() {
        ProvisionStore s = ProvisionStore.of(new ProvisionLot(BEEF, 7, 3 * DAY), new ProvisionLot(BREAD, 9, 0),
                new ProvisionLot(WATER, 30, 12), new ProvisionLot(RUM, 3, 0), new ProvisionLot(APPLE, 2, 5));
        assertEquals(s, ProvisionStore.CODEC.parse(JsonOps.INSTANCE, ProvisionStore.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow()).getOrThrow());
        assertEquals(s, ProvisionStore.CODEC.parse(NbtOps.INSTANCE, ProvisionStore.CODEC.encodeStart(NbtOps.INSTANCE, s).getOrThrow()).getOrThrow());
        assertEquals(ProvisionStore.EMPTY, ProvisionStore.CODEC.parse(JsonOps.INSTANCE,
                ProvisionStore.CODEC.encodeStart(JsonOps.INSTANCE, ProvisionStore.EMPTY).getOrThrow()).getOrThrow());
    }

    @Test
    void stateCodecRoundTrip() {
        ProvisioningState st = new ProvisioningState(1.5, 0.25, 0.75, 1000.5, 20, 30000, 600);
        assertEquals(st, ProvisioningState.CODEC.parse(NbtOps.INSTANCE, ProvisioningState.CODEC.encodeStart(NbtOps.INSTANCE, st).getOrThrow()).getOrThrow());
        assertEquals(st, ProvisioningState.CODEC.parse(JsonOps.INSTANCE, ProvisioningState.CODEC.encodeStart(JsonOps.INSTANCE, st).getOrThrow()).getOrThrow());
    }

    @Test
    void storeMergesLotsAndDropsEmptyOnes() {
        ProvisionStore s = ProvisionStore.of(new ProvisionLot(KELP, 3, 0), new ProvisionLot(KELP, 4, 0),
                new ProvisionLot(KELP, 2, 10), new ProvisionLot(BREAD, 0, 0));
        assertEquals(2, s.lots().size());
        assertEquals(9, s.units("kelp"));
        assertEquals(0, s.units("bread"));
        assertTrue(ProvisionStore.of().isEmpty());
    }

    @Test
    void reconcileKeepsAgesAddsNewLotsAndRemovesOldestFirst() {
        ProvisionStore s = ProvisionStore.of(new ProvisionLot(BEEF, 5, 3 * DAY), new ProvisionLot(BEEF, 5, DAY),
                new ProvisionLot(KELP, 4, DAY));
        ProvisionType freshBeef = ProvisionType.food("beef", 8, false, false, 0.5, 6 * DAY); // reclassified, new weight

        Map<String, ProvisionStore.Counted> more = new LinkedHashMap<>();
        more.put("beef", new ProvisionStore.Counted(freshBeef, 13));
        ProvisionStore added = s.reconcile(more);
        assertEquals(13, added.units("beef"));
        assertEquals(0, added.units("kelp"), "kelp left the container");
        assertEquals(3, added.lots().stream().filter(l -> l.ageTicks() == 0).mapToInt(ProvisionLot::units).sum());
        assertTrue(added.lots().stream().allMatch(l -> l.type().equals(freshBeef)), "kept lots take the fresh type data");

        Map<String, ProvisionStore.Counted> fewer = Map.of("beef", new ProvisionStore.Counted(freshBeef, 7));
        ProvisionStore removed = s.reconcile(fewer);
        assertEquals(7, removed.units("beef"));
        assertEquals(2, removed.lots().stream().filter(l -> l.ageTicks() == 3 * DAY).mapToInt(ProvisionLot::units).sum());
        assertEquals(5, removed.lots().stream().filter(l -> l.ageTicks() == DAY).mapToInt(ProvisionLot::units).sum());

        Map<String, ProvisionStore.Counted> same = Map.of("beef", new ProvisionStore.Counted(BEEF, 10), "kelp", new ProvisionStore.Counted(KELP, 4));
        assertEquals(s, s.reconcile(same));
    }

    @Test
    void typeValidation() {
        assertThrows(IllegalArgumentException.class, () -> ProvisionType.food("x", 0, false, false, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ProvisionLot(KELP, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CrewHeadcount(-1, 0, 0));
        assertFalse(ProvisionType.food("p", 3, true, false, 0, 1000).perishable(), "preserved food never has a shelf life");
        assertFalse(WATER.perishable());
    }

    @Test
    void configAdapterUsesDefaults() {
        assertEquals(ProvisionSettings.DEFAULTS, ProvisionsConfig.settings());
    }

    @Test
    void configAdapterReadsValues() {
        ProvisionsConfig.CONSUMPTION_ENABLED.set(false);
        ProvisionsConfig.SCURVY_ONSET_DAYS.set(2.0);
        ProvisionSettings s = ProvisionsConfig.settings();
        assertFalse(s.consumptionEnabled());
        assertEquals(2.0, s.scurvyOnsetDays());
        assertEquals(2 * DAY, s.scurvyOnsetTicks(), 1e-9);
    }

    @Test
    void builderRoundTrip() {
        assertEquals(ProvisionSettings.DEFAULTS, ProvisionSettings.DEFAULTS.toBuilder().build());
        assertEquals(ProvisionSettings.DEFAULTS, ProvisionSettings.builder().build());
    }
}

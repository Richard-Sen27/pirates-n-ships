package com.richardsenger.piratesnships.law.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.crime.CrimeRules;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.crime.CriminalRecord;
import com.google.gson.JsonParser;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The plunder crimes: the legacy G13 entry, its config values and the per-port victim id. */
class PlunderCrimesTest {

    @Test
    void legacyFencePlunderKeepsItsCatalogueEntryAndConfig() {
        CrimeType t = CrimeType.FENCE_PLUNDER;
        assertEquals("fence_plunder", t.id());
        assertEquals("crime.pirates_n_ships.fence_plunder", t.nameKey());
        assertEquals(15, t.defaultSeverity());
        assertEquals(60, t.defaultCooldownSeconds());
        assertEquals(15, LawConfig.SEVERITIES.get(t).get());
        assertEquals(60, LawConfig.COOLDOWNS.get(t).get());
        assertEquals(15, CrimeRules.defaults().severityOf(t));
        assertEquals(60 * 20L, CrimeRules.defaults().cooldownOf(t));
        assertTrue(t.legacy());
    }

    /** LAW3b: nothing records fence_plunder any more, but saved records holding it must still load. */
    @Test
    void legacyFencePlunderStillDecodesFromSaves() {
        assertEquals(CrimeType.FENCE_PLUNDER,
                CrimeType.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"fence_plunder\"")).getOrThrow());
        CompoundTag offence = new CompoundTag();
        offence.putString("type", "fence_plunder");
        offence.putLong("time", 1234L);
        ListTag recent = new ListTag();
        recent.add(offence);
        CompoundTag saved = new CompoundTag();
        saved.putDouble("score", 15.0);
        saved.putInt("total_crimes", 1);
        saved.put("recent", recent);
        CriminalRecord r = CriminalRecord.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        assertEquals(List.of(new CriminalRecord.Offence(CrimeType.FENCE_PLUNDER, Optional.empty(), 1234L)), r.recent());
        assertEquals(15.0, r.score());
    }

    @Test
    void onlyTheLegacyCrimeIsNotCommittable() {
        assertFalse(CrimeType.committable().anyMatch(CrimeType::legacy));
        assertFalse(CrimeType.committable().anyMatch(t -> t == CrimeType.FENCE_PLUNDER));
        assertEquals(CrimeType.values().length - 1, CrimeType.committable().count());
        assertTrue(CrimeType.committable().anyMatch(t -> t == CrimeType.SELLING_PLUNDER));
    }

    @Test
    void crimeNameKeysAreUnique() {
        Set<String> keys = new HashSet<>();
        for (CrimeType t : CrimeType.values()) assertTrue(keys.add(t.nameKey()), "duplicate " + t.nameKey());
    }

    @Test
    void portVictimIsStablePerPortAndDistinctBetweenPorts() {
        ResourceLocation a = ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "port/a");
        ResourceLocation b = ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "port/b");
        assertEquals(PlunderCrimes.portVictim(a), PlunderCrimes.portVictim(ResourceLocation.parse("pirates_n_ships:port/a")));
        assertNotEquals(PlunderCrimes.portVictim(a), PlunderCrimes.portVictim(b));
    }
}

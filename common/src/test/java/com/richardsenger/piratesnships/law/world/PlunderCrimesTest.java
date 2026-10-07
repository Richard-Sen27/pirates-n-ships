package com.richardsenger.piratesnships.law.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.crime.CrimeRules;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The plunder crime of G13: its catalogue entry, config values and the per-port victim id. */
class PlunderCrimesTest {

    @Test
    void fencePlunderIsACrimeWithItsOwnConfig() {
        CrimeType t = CrimeType.FENCE_PLUNDER;
        assertEquals("fence_plunder", t.id());
        assertEquals("crime.pirates_n_ships.fence_plunder", t.nameKey());
        assertEquals(15, t.defaultSeverity());
        assertEquals(60, t.defaultCooldownSeconds());
        assertEquals(15, LawConfig.SEVERITIES.get(t).get());
        assertEquals(60, LawConfig.COOLDOWNS.get(t).get());
        assertEquals(15, CrimeRules.defaults().severityOf(t));
        assertEquals(60 * 20L, CrimeRules.defaults().cooldownOf(t));
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

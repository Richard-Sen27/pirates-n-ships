package com.richardsenger.piratesnships.mob.captain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BOS1: captain names are stable per island and generation, varied across islands, and never inherited. */
class CaptainNamesTest {

    @Test
    void theSameIslandAndGenerationAlwaysGiveTheSameName() {
        String id = "pirates_n_ships:pirate_island_120_-340";
        assertEquals(CaptainNames.name(id, 0), CaptainNames.name(id, 0));
        assertEquals(CaptainNames.name(id, 3), CaptainNames.name(id, 3));
    }

    @Test
    void aNameIsEpithetFirstNameAndSurname() {
        String name = CaptainNames.name("pirates_n_ships:pirate_island_0_0", 0);
        String[] parts = name.split(" ");
        assertEquals(3, parts.length, name);
        assertTrue(CaptainNames.EPITHETS.contains(parts[0]), name);
        assertTrue(CaptainNames.FIRST_NAMES.contains(parts[1]), name);
        assertTrue(CaptainNames.SURNAMES.contains(parts[2]), name);
    }

    @Test
    void islandsGetVariedNames() {
        Set<String> names = new HashSet<>();
        Set<String> epithets = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String n = CaptainNames.name("pirates_n_ships:pirate_island_" + (i * 37) + "_" + (-i * 91), 0);
            names.add(n);
            epithets.add(n.split(" ")[0]);
        }
        assertTrue(names.size() >= 195, "200 islands, " + names.size() + " names");
        assertTrue(epithets.size() >= 20, "epithets used: " + epithets.size());
    }

    @Test
    void aSuccessorNeverCarriesHisPredecessorsName() {
        for (int i = 0; i < 300; i++) {
            String id = "pirates_n_ships:pirate_island_" + i + "_" + (i * 7);
            for (int g = 1; g < 6; g++) {
                assertNotEquals(CaptainNames.name(id, g - 1), CaptainNames.name(id, g), id + " generation " + g);
            }
        }
    }

    @Test
    void theListsHoldNoDuplicates() {
        assertEquals(CaptainNames.EPITHETS.size(), Set.copyOf(CaptainNames.EPITHETS).size());
        assertEquals(CaptainNames.FIRST_NAMES.size(), Set.copyOf(CaptainNames.FIRST_NAMES).size());
        assertEquals(CaptainNames.SURNAMES.size(), Set.copyOf(CaptainNames.SURNAMES).size());
    }

    /** The registry's day math: a successor is due {@code respawn_days} whole days after the loss. */
    @Test
    void successorIsDueAfterRespawnDays() {
        CaptainEntry alive = new CaptainEntry(UUID.randomUUID(), "A", 0, true, 0L,
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld")), BlockPos.ZERO, Direction.NORTH);
        assertFalse(alive.successorDue(100, 5), "a living captain has no successor");
        CaptainEntry lost = alive.dead(10);
        assertFalse(lost.alive());
        assertEquals(10, lost.diedDay());
        assertFalse(lost.successorDue(14, 5), "four days later");
        assertTrue(lost.successorDue(15, 5), "five days later");
        assertTrue(lost.successorDue(40, 5), "much later");
        assertTrue(lost.successorDue(10, 0), "respawn_days 0: at the next check");
        CaptainEntry next = lost.successor(UUID.randomUUID(), "B");
        assertTrue(next.alive());
        assertEquals(1, next.generation());
        assertEquals(lost.post(), next.post());
        assertEquals(lost.facing(), next.facing());
    }
}

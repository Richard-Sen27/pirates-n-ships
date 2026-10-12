package com.richardsenger.piratesnships.world.structure;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * STR6: the arithmetic of the port structures' loot without a game: the rolls scale ({@link ConfiguredRolls#scale}),
 * the table family {@link LootChestProcessor} strips while loot is off ({@link StructureLoot#isStructureTable}), and
 * the data it places ({@link LootChestProcessor#placed}).
 */
class StructureLootTest {

    @Test
    void wholeScalesMultiply() {
        assertEquals(3, ConfiguredRolls.scale(3, 1.0, 0.99));
        assertEquals(6, ConfiguredRolls.scale(3, 2.0, 0.0));
        assertEquals(0, ConfiguredRolls.scale(3, 0.0, 0.0), "scale 0: no rolls");
        assertEquals(0, ConfiguredRolls.scale(0, 4.0, 0.0), "no base rolls stay none");
    }

    @Test
    void aFractionRoundsUpWithItsProbability() {
        assertEquals(2, ConfiguredRolls.scale(3, 0.5, 0.49), "1.5 rounds up below 0.5");
        assertEquals(2, ConfiguredRolls.scale(3, 0.5, 0.4));
        assertEquals(1, ConfiguredRolls.scale(3, 0.5, 0.5), "and down from 0.5");
        Random random = new Random(7);
        int total = 0;
        int n = 20_000;
        for (int i = 0; i < n; i++) total += ConfiguredRolls.scale(1, 0.5, random.nextDouble());
        assertEquals(0.5, total / (double) n, 0.02, "on average the scale holds");
        total = 0;
        for (int i = 0; i < n; i++) total += ConfiguredRolls.scale(3, 1.3, random.nextDouble());
        assertEquals(3.9, total / (double) n, 0.03);
    }

    @Test
    void theFamilyIsThePortStructuresTables() {
        assertTrue(StructureLoot.isStructureTable("pirates_n_ships:chests/village/house"));
        assertTrue(StructureLoot.isStructureTable("pirates_n_ships:chests/navy_outpost/armoury"));
        assertTrue(StructureLoot.isStructureTable("pirates_n_ships:chests/pirate_island/captain"));
        assertFalse(StructureLoot.isStructureTable("pirates_n_ships:chests/wreck"), "the wrecks keep theirs");
        assertFalse(StructureLoot.isStructureTable("pirates_n_ships:chests/buried_treasure"), "the treasure keeps its own");
        assertFalse(StructureLoot.isStructureTable("minecraft:chests/village/village_weaponsmith"));
        assertFalse(StructureLoot.isStructureTable(""));
        assertEquals(StructureLoot.VILLAGE_HOUSE.location().toString(), "pirates_n_ships:chests/village/house");
    }

    @Test
    void lootOffDropsOnlyThePortTables() {
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        chest.putString("LootTable", "pirates_n_ships:chests/village/tavern");
        chest.putLong("LootTableSeed", 42L);
        assertSame(chest, LootChestProcessor.placed(chest, true), "loot on: unchanged");
        CompoundTag off = LootChestProcessor.placed(chest, false);
        assertFalse(off.contains("LootTable"));
        assertFalse(off.contains("LootTableSeed"));
        assertEquals("minecraft:chest", off.getString("id"));
        assertTrue(chest.contains("LootTable"), "the template's own data is not touched");

        CompoundTag wreck = new CompoundTag();
        wreck.putString("LootTable", "pirates_n_ships:chests/wreck");
        assertSame(wreck, LootChestProcessor.placed(wreck, false), "a wreck's table stays");
        assertNull(LootChestProcessor.placed(null, false));
        CompoundTag plain = new CompoundTag();
        assertSame(plain, LootChestProcessor.placed(plain, false));
    }
}

package com.richardsenger.piratesnships.world.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.world.WorldConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic of {@link ConfiguredSpreadPlacement} (WG5): with the config not loaded it chooses vanilla's chunks for
 * the JSON numbers, with config values it chooses vanilla's chunks for the config's numbers, and salt and spread type
 * are honoured as in vanilla.
 */
class ConfiguredSpreadPlacementTest {

    private static final int SALT = 584_103_926;
    private static final long[] SEEDS = {0L, 1L, -4_172_144_997_902_289_642L, 123_456_789L};
    private final List<ConfigOverrides.Handle<?>> overrides = new java.util.ArrayList<>();

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restore() {
        overrides.forEach(ConfigOverrides.Handle::restore);
        overrides.clear();
    }

    private <T> void override(com.richardsenger.piratesnships.core.config.ConfigValue<T> value, T v) {
        overrides.add(ConfigOverrides.apply(value, v));
    }

    /** Every region the comparison walks: a few cells around the origin, negative coordinates included. */
    private static void assertSameChunks(RandomSpreadStructurePlacement expected, RandomSpreadStructurePlacement actual) {
        for (long seed : SEEDS) {
            for (int x = -100; x <= 100; x += 7) {
                for (int z = -100; z <= 100; z += 11) {
                    assertEquals(expected.getPotentialStructureChunk(seed, x, z), actual.getPotentialStructureChunk(seed, x, z),
                            "seed " + seed + " region " + x + "," + z);
                }
            }
        }
    }

    @Test
    void withoutTheConfigTheJsonNumbersMatchVanilla() {
        // the JSON numbers differ from the config defaults (30/10): the fallback must use the JSON's
        ConfiguredSpreadPlacement placement = new ConfiguredSpreadPlacement(36, 12, RandomSpreadType.LINEAR, SALT, "seafarer_village");
        assertFalse(WorldConfig.SEAFARER_VILLAGE.spacing().isLive());
        assertEquals(36, placement.spacing());
        assertEquals(12, placement.separation());
        assertSameChunks(new RandomSpreadStructurePlacement(36, 12, RandomSpreadType.LINEAR, SALT), placement);
    }

    @Test
    void configValuesReplaceTheJsonNumbers() {
        ConfiguredSpreadPlacement placement = new ConfiguredSpreadPlacement(36, 12, RandomSpreadType.LINEAR, SALT, "seafarer_village");
        override(WorldConfig.SEAFARER_VILLAGE.spacing(), 20);
        override(WorldConfig.SEAFARER_VILLAGE_SEPARATION, 5);
        assertEquals(20, placement.spacing());
        assertEquals(5, placement.separation());
        assertSameChunks(new RandomSpreadStructurePlacement(20, 5, RandomSpreadType.LINEAR, SALT), placement);
    }

    @Test
    void aChangedSpacingChangesTheGrid() {
        ConfiguredSpreadPlacement placement = new ConfiguredSpreadPlacement(30, 10, RandomSpreadType.LINEAR, SALT, "seafarer_village");
        Set<ChunkPos> before = starts(placement, 1L, 240);
        override(WorldConfig.SEAFARER_VILLAGE.spacing(), 15);
        override(WorldConfig.SEAFARER_VILLAGE_SEPARATION, 5);
        Set<ChunkPos> after = starts(placement, 1L, 240);
        assertEquals(64, before.size(), "one start per 30-chunk cell in 240x240 chunks");
        assertEquals(256, after.size(), "one start per 15-chunk cell");
        assertNotEquals(before, after);
        for (ChunkPos p : after) {
            ChunkPos cellCorner = new ChunkPos(Math.floorDiv(p.x, 15) * 15, Math.floorDiv(p.z, 15) * 15);
            assertTrue(p.x - cellCorner.x < 10 && p.z - cellCorner.z < 10, "start inside spacing - separation of its cell: " + p);
        }
    }

    @Test
    void onlyTheConfigOfItsOwnStructureCounts() {
        ConfiguredSpreadPlacement outposts = new ConfiguredSpreadPlacement(40, 16, RandomSpreadType.LINEAR, SALT, "navy_outpost");
        override(WorldConfig.SEAFARER_VILLAGE.spacing(), 20);
        assertEquals(40, outposts.spacing());
        override(WorldConfig.NAVY_OUTPOST.spacing(), 64);
        assertEquals(64, outposts.spacing());
        assertEquals(16, outposts.separation(), "separation not overridden: the JSON's");
        assertSameChunks(new RandomSpreadStructurePlacement(64, 16, RandomSpreadType.LINEAR, SALT), outposts);
    }

    @Test
    void saltAndSpreadTypeAreHonoured() {
        for (RandomSpreadType type : RandomSpreadType.values()) {
            for (int salt : new int[] {SALT, 902_417_385, 0}) {
                assertSameChunks(new RandomSpreadStructurePlacement(24, 8, type, salt),
                        new ConfiguredSpreadPlacement(24, 8, type, salt, "wreck"));
            }
        }
        ConfiguredSpreadPlacement a = new ConfiguredSpreadPlacement(24, 8, RandomSpreadType.LINEAR, SALT, "wreck");
        ConfiguredSpreadPlacement b = new ConfiguredSpreadPlacement(24, 8, RandomSpreadType.LINEAR, SALT + 1, "wreck");
        ConfiguredSpreadPlacement c = new ConfiguredSpreadPlacement(24, 8, RandomSpreadType.TRIANGULAR, SALT, "wreck");
        assertNotEquals(starts(a, 1L, 240), starts(b, 1L, 240), "another salt, another grid");
        assertNotEquals(starts(a, 1L, 240), starts(c, 1L, 240), "another spread type, another grid");
    }

    @Test
    void aSeparationNotBelowTheSpacingIsClamped() {
        ConfiguredSpreadPlacement placement = new ConfiguredSpreadPlacement(30, 10, RandomSpreadType.LINEAR, SALT, "pirate_island");
        override(WorldConfig.PIRATE_ISLAND.spacing(), 10);
        override(WorldConfig.PIRATE_ISLAND_SEPARATION, 12);
        assertEquals(10, placement.spacing());
        assertEquals(9, placement.separation());
        assertSameChunks(new RandomSpreadStructurePlacement(10, 9, RandomSpreadType.LINEAR, SALT), placement);
        assertEquals(new SpreadGrid(2, 1), new SpreadGrid(0, 0), "spacing at least 2, separation at least 1");
    }

    @Test
    void codecReadsAndWritesTheJsonNumbers() {
        JsonObject json = PortStructureData.structureSet(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE,
                        com.richardsenger.piratesnships.Constants.id("seafarer_village")),
                "seafarer_village", SALT, 30, 10).getAsJsonObject("placement");
        ConfiguredSpreadPlacement placement = ConfiguredSpreadPlacement.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals("seafarer_village", placement.config());
        assertEquals(30, placement.jsonSpacing());
        assertEquals(10, placement.jsonSeparation());
        assertEquals(RandomSpreadType.LINEAR, placement.spreadType());
        override(WorldConfig.SEAFARER_VILLAGE.spacing(), 50);
        JsonElement again = ConfiguredSpreadPlacement.CODEC.codec().encodeStart(JsonOps.INSTANCE, placement).getOrThrow();
        assertEquals(30, again.getAsJsonObject().get("spacing").getAsInt(), "encodes the JSON's spacing, not the config's");
        assertEquals("seafarer_village", again.getAsJsonObject().get("config").getAsString());

        JsonObject unknown = json.deepCopy();
        unknown.addProperty("config", "kraken_lair");
        assertTrue(ConfiguredSpreadPlacement.CODEC.codec().parse(JsonOps.INSTANCE, unknown).isError(), "unknown config name");
        JsonObject inverted = json.deepCopy();
        inverted.addProperty("separation", 30);
        assertTrue(ConfiguredSpreadPlacement.CODEC.codec().parse(JsonOps.INSTANCE, inverted).isError(), "spacing must exceed separation");
        assertEquals(List.of("seafarer_village", "pirate_island", "navy_outpost", "wreck"), WorldConfig.spreadNames());
    }

    @Test
    void newDefaultsAreALittleDenser() {
        assertEquals(30, WorldConfig.SEAFARER_VILLAGE.spacing().defaultValue());
        assertEquals(10, WorldConfig.SEAFARER_VILLAGE_SEPARATION.defaultValue());
        assertEquals(40, WorldConfig.NAVY_OUTPOST.spacing().defaultValue());
        assertEquals(16, WorldConfig.NAVY_OUTPOST_SEPARATION.defaultValue());
        assertEquals(52, WorldConfig.PIRATE_ISLAND.spacing().defaultValue());
        assertEquals(20, WorldConfig.PIRATE_ISLAND_SEPARATION.defaultValue());
        assertEquals(24, WorldConfig.WRECK.spacing().defaultValue());
        assertEquals(8, WorldConfig.WRECK_SEPARATION.defaultValue());
    }

    /** Every chunk in [0, size)² where the placement may start a structure (one per whole cell). */
    private static Set<ChunkPos> starts(ConfiguredSpreadPlacement placement, long seed, int size) {
        Set<ChunkPos> out = new HashSet<>();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                ChunkPos p = placement.getPotentialStructureChunk(seed, x, z);
                if (p.x == x && p.z == z) out.add(p);
            }
        }
        return out;
    }
}

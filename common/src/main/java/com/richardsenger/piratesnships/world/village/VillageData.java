package com.richardsenger.piratesnships.world.village;

import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.world.structure.PortStructureData;
import com.richardsenger.piratesnships.world.structure.ShoreAnchor;

import java.util.Map;

import static com.richardsenger.piratesnships.world.structure.PortStructureData.weights;

/**
 * Datagen of the seafarer village's worldgen files (vanilla formats, raw JSON through {@link PortStructureData}): the
 * structure, its structure set, the five template pools and the biome tag.
 *
 * <p><b>Terrain adaptation {@code none}.</b> Vanilla's beardifier adds a beard for every rigid
 * {@code PoolElementStructurePiece} of a structure with the structure's one adaptation; it cannot exclude single
 * pieces (NeoForge's {@code PieceBeardifierModifier} could, but needs our own piece class and is loader-only). With
 * {@code beard_thin}, the pier (rigid, ground level at its deck) would get land raised under it and around the quay,
 * filling the berths. So the village uses {@code none}: streets follow the terrain (terrain matching), buildings sit
 * on the surface row, and on uneven ground buildings may stand partly in the hill or on a step.
 */
public final class VillageData {

    /** Fixed salt of the structure set's random spread (never change it: it moves every village of existing worlds). */
    public static final int SALT = 584_103_926;
    public static final int SIZE = 7;
    public static final int MAX_DISTANCE_FROM_CENTER = 80;
    public static final int START_HEIGHT = 1;

    /** Weights of the buildings pool (art/README.md "Structures (ST1)"). */
    public static final Map<String, Integer> BUILDINGS = weights("house_small", 3, "tavern", 1, "shipwright", 1);

    private static final String GROUP = "village";

    private VillageData() {
    }

    public static PortStructureData.Spec spec() {
        return new PortStructureData.Spec(VillageKeys.HAS_SEAFARER_VILLAGE, VillageKeys.START, SIZE, START_HEIGHT,
                MAX_DISTANCE_FROM_CENTER, ShoreAnchor.VILLAGE, PortKind.SEAFARER_VILLAGE, new JsonObject());
    }

    public static void gather(DataContributions data) {
        PortStructureData.writeStructure(data, VillageKeys.SEAFARER_VILLAGE, spec());
        PortStructureData.writeStructureSet(data, VillageKeys.SEAFARER_VILLAGES, VillageKeys.SEAFARER_VILLAGE, SALT,
                WorldConfig.SEAFARER_VILLAGE.spacing().defaultValue(), WorldConfig.SEAFARER_VILLAGE_SEPARATION.defaultValue());
        PortStructureData.pool(data, VillageKeys.START, GROUP, "minecraft:empty", "rigid", weights("dock_head", 1));
        PortStructureData.pool(data, VillageKeys.STREETS, GROUP, VillageKeys.TERMINATORS.location().toString(), "terrain_matching",
                weights("street", 1));
        PortStructureData.pool(data, VillageKeys.BUILDINGS, GROUP, "minecraft:empty", "rigid", BUILDINGS);
        PortStructureData.pool(data, VillageKeys.PIER, GROUP, "minecraft:empty", "rigid", weights("pier", 1));
        // The streets' fallback once the depth runs out. It must not be empty: vanilla skips a connector whose fallback
        // pool is empty (and not minecraft:empty), so an empty terminators pool would stop every street.
        PortStructureData.pool(data, VillageKeys.TERMINATORS, GROUP, "minecraft:empty", "terrain_matching", weights("street_end", 1));
        PortStructureData.biomeTag(data, VillageKeys.HAS_SEAFARER_VILLAGE, "#minecraft:is_beach");
    }
}

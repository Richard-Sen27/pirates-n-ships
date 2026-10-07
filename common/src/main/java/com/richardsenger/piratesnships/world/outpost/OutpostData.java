package com.richardsenger.piratesnships.world.outpost;

import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.world.structure.PortStructureData;
import com.richardsenger.piratesnships.world.structure.ShoreAnchor;

import java.util.Map;

import static com.richardsenger.piratesnships.world.structure.PortStructureData.weights;

/**
 * Datagen of the navy outpost's worldgen files (WG3, design.md §10.1): the structure (type
 * {@code pirates_n_ships:port_village}, port kind {@code navy_outpost}, the fort gate's shore anchor (7, 0)), its
 * structure set (random spread 48/20, the rarest port), the five pools of the ST3 pieces and the biome tag (beaches).
 *
 * <p>Every piece is rigid: the curtain walls share the gate's foundation row (their seaward face stands at the
 * waterline), the quay hangs from the sea gate, the buildings stand on the gate's landward apron. The walls fall back
 * to the wall tower once the depth runs out, so each wall run ends in a tower. Terrain adaptation {@code none} for
 * the village's reason (the quay would be bearded). No spawn overrides: the navy is {@code misc} and never spawns
 * naturally; the garrison is placed with the structure ({@link Garrison}).
 */
public final class OutpostData {

    /** Fixed salt of the structure set's random spread (never change it: it moves every outpost of existing worlds). */
    public static final int SALT = 902_417_385;
    public static final int SIZE = 5;
    /** Five walls and a tower per run: about 56 blocks from the gate's centre to a run's end. */
    public static final int MAX_DISTANCE_FROM_CENTER = 64;
    public static final int START_HEIGHT = 1;
    /** The fort gate (15×10×15): its sea gate and quay connector in the middle of its north edge. */
    public static final ShoreAnchor FORT_GATE = new ShoreAnchor(7, 0);

    /** Weights of the buildings pool (WG3): barracks 2, brig 1, watchtower 1. */
    public static final Map<String, Integer> BUILDINGS = weights("barracks", 2, "brig", 1, "watchtower", 1);

    private static final String GROUP = "navy_outpost";

    private OutpostData() {
    }

    public static PortStructureData.Spec spec() {
        return new PortStructureData.Spec(OutpostKeys.HAS_NAVY_OUTPOST, OutpostKeys.START, SIZE, START_HEIGHT,
                MAX_DISTANCE_FROM_CENTER, FORT_GATE, PortKind.NAVY_OUTPOST, new JsonObject());
    }

    public static void gather(DataContributions data) {
        PortStructureData.writeStructure(data, OutpostKeys.NAVY_OUTPOST, spec());
        PortStructureData.writeStructureSet(data, OutpostKeys.NAVY_OUTPOSTS, OutpostKeys.NAVY_OUTPOST, SALT,
                WorldConfig.NAVY_OUTPOST.spacing().defaultValue(), WorldConfig.NAVY_OUTPOST_SEPARATION.defaultValue());
        PortStructureData.pool(data, OutpostKeys.START, GROUP, "minecraft:empty", "rigid", weights("fort_gate", 1));
        PortStructureData.pool(data, OutpostKeys.WALLS, GROUP, OutpostKeys.TERMINATORS.location().toString(), "rigid",
                weights("wall", 1));
        PortStructureData.pool(data, OutpostKeys.BUILDINGS, GROUP, "minecraft:empty", "rigid", BUILDINGS);
        PortStructureData.pool(data, OutpostKeys.QUAY, GROUP, "minecraft:empty", "rigid", weights("quay", 1));
        // The walls' fallback once the depth runs out; never empty (vanilla skips a connector with an empty fallback)
        PortStructureData.pool(data, OutpostKeys.TERMINATORS, GROUP, "minecraft:empty", "rigid", weights("wall_tower", 1));
        PortStructureData.biomeTag(data, OutpostKeys.HAS_NAVY_OUTPOST, "#minecraft:is_beach");
    }
}

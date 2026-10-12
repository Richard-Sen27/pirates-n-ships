package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType;

/**
 * Registry entries of the port structures (WG1, WG2), the connections processor they share with the wrecks (WG4) and
 * the configured spread placement of all four structure sets (WG5), the loot chests' processor and rolls (STR6).
 */
public final class PortStructures {

    /**
     * The structure type {@code pirates_n_ships:port_village} of every port structure (village, pirate island). The
     * id keeps WG1's name, so existing worlds and datapacks stay valid.
     */
    public static final RegistryEntry<StructureType<?>, StructureType<PortStructure>> PORT_STRUCTURE =
            Services.REGISTRY.register(Registries.STRUCTURE_TYPE, "port_village", () -> () -> PortStructure.CODEC);

    /**
     * The processor type {@code pirates_n_ships:connections} ({@link ConnectionsProcessor}): every port pool element
     * (through the processor list {@link ConnectionsProcessor#LIST}) and every wreck piece.
     */
    public static final RegistryEntry<StructureProcessorType<?>, StructureProcessorType<ConnectionsProcessor>> CONNECTIONS =
            Services.REGISTRY.register(Registries.STRUCTURE_PROCESSOR, "connections", () -> () -> ConnectionsProcessor.CODEC);

    /** The processor type {@code pirates_n_ships:boardwalk} ({@link BoardwalkProcessor}): the island's trails (STR2). */
    public static final RegistryEntry<StructureProcessorType<?>, StructureProcessorType<BoardwalkProcessor>> BOARDWALK =
            Services.REGISTRY.register(Registries.STRUCTURE_PROCESSOR, "boardwalk", () -> () -> BoardwalkProcessor.CODEC);

    /**
     * The placement type {@code pirates_n_ships:configured_spread} ({@link ConfiguredSpreadPlacement}, WG5): the random
     * spread of the village, island, outpost and wreck structure sets, with spacing and separation from the server config.
     */
    public static final RegistryEntry<StructurePlacementType<?>, StructurePlacementType<ConfiguredSpreadPlacement>> CONFIGURED_SPREAD =
            Services.REGISTRY.register(Registries.STRUCTURE_PLACEMENT, "configured_spread", () -> () -> ConfiguredSpreadPlacement.CODEC);

    /**
     * The processor type {@code pirates_n_ships:loot_chests} ({@link LootChestProcessor}, STR6): in the connections
     * processor list, drops the port structures' loot tables from their chests while {@code world.structures.loot} is off.
     */
    public static final RegistryEntry<StructureProcessorType<?>, StructureProcessorType<LootChestProcessor>> LOOT_CHESTS =
            Services.REGISTRY.register(Registries.STRUCTURE_PROCESSOR, "loot_chests", () -> () -> LootChestProcessor.CODEC);

    /**
     * The loot number provider type {@code pirates_n_ships:configured_rolls} ({@link ConfiguredRolls}, STR6): the pools of
     * the port structures' loot tables, scaled by {@code world.structures.loot_rolls}.
     */
    public static final RegistryEntry<LootNumberProviderType, LootNumberProviderType> CONFIGURED_ROLLS =
            Services.REGISTRY.register(Registries.LOOT_NUMBER_PROVIDER_TYPE, "configured_rolls",
                    () -> new LootNumberProviderType(ConfiguredRolls.CODEC));

    /**
     * The processor type {@code pirates_n_ships:trail_bed} ({@link TrailBedProcessor}, STR8): the streets', trails' and
     * lanes' processor lists lay each piece level on a cut and filled bed.
     */
    public static final RegistryEntry<StructureProcessorType<?>, StructureProcessorType<TrailBedProcessor>> TRAIL_BED =
            Services.REGISTRY.register(Registries.STRUCTURE_PROCESSOR, "trail_bed", () -> () -> TrailBedProcessor.CODEC);

    private PortStructures() {
    }

    public static void init() {
    }
}

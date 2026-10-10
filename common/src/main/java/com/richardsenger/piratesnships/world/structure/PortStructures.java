package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;

/**
 * Registry entries of the port structures (WG1, WG2), the connections processor they share with the wrecks (WG4) and
 * the configured spread placement of all four structure sets (WG5).
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

    private PortStructures() {
    }

    public static void init() {
    }
}

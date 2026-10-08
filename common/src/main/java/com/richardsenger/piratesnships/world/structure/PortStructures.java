package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;

/** Registry entries of the port structures (WG1, WG2) and the connections processor they share with the wrecks (WG4). */
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

    private PortStructures() {
    }

    public static void init() {
    }
}

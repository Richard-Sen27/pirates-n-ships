package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** Registry entries of the port structures (WG1, WG2). */
public final class PortStructures {

    /**
     * The structure type {@code pirates_n_ships:port_village} of every port structure (village, pirate island). The
     * id keeps WG1's name, so existing worlds and datapacks stay valid.
     */
    public static final RegistryEntry<StructureType<?>, StructureType<PortStructure>> PORT_STRUCTURE =
            Services.REGISTRY.register(Registries.STRUCTURE_TYPE, "port_village", () -> () -> PortStructure.CODEC);

    private PortStructures() {
    }

    public static void init() {
    }
}

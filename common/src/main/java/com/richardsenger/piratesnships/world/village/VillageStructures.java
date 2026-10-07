package com.richardsenger.piratesnships.world.village;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** Registry entries of the seafarer village (WG1); the data keys are in {@link VillageKeys}. */
public final class VillageStructures {

    /** The structure type {@code pirates_n_ships:port_village}. */
    public static final RegistryEntry<StructureType<?>, StructureType<PortVillageStructure>> PORT_VILLAGE =
            Services.REGISTRY.register(Registries.STRUCTURE_TYPE, "port_village", () -> () -> PortVillageStructure.CODEC);

    private VillageStructures() {
    }

    public static void init() {
    }
}

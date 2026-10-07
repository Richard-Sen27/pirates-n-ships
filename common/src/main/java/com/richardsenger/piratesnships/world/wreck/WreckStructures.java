package com.richardsenger.piratesnships.world.wreck;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/** Registry entries of the wrecks (WK1): the structure type and the piece type; the data keys are in {@link WreckKeys}. */
public final class WreckStructures {

    /** The structure type {@code pirates_n_ships:wreck}. */
    public static final RegistryEntry<StructureType<?>, StructureType<WreckStructure>> WRECK_TYPE =
            Services.REGISTRY.register(Registries.STRUCTURE_TYPE, "wreck", () -> () -> WreckStructure.CODEC);

    /** The piece type {@code pirates_n_ships:wreck}, saved with every structure start that holds a wreck. */
    public static final RegistryEntry<StructurePieceType, StructurePieceType> WRECK_PIECE =
            Services.REGISTRY.register(Registries.STRUCTURE_PIECE, "wreck",
                    () -> (StructurePieceType.StructureTemplateType) WreckPiece::new);

    private WreckStructures() {
    }

    public static void init() {
    }
}

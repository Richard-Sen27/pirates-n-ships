package com.richardsenger.piratesnships.station.capstan;

import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Registrations of the capstan in motion (CAP2): the capstan's block entity (for every capstan of
 * {@code sailing.block}, WOOD4). The bars model ({@code block/<wood>_capstan_bars}, WOOD4) is a stand-alone model, see
 * {@code client/CapstanClient}.
 */
public final class CapstanContent {

    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<CapstanBlockEntity>> CAPSTAN_ENTITY =
            SailingBlocks.CAPSTANS.blockEntity("capstan", CapstanBlockEntity::new);

    private CapstanContent() {
    }

    /** Loads the class so the entries above are registered. Called from {@code StationModule.registerContent()}. */
    public static void init() {
    }
}

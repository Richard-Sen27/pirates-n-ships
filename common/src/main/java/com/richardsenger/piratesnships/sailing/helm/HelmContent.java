package com.richardsenger.piratesnships.sailing.helm;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Registrations of wheel steering (HELM1): the helm's block entity (for the helm block of {@code ship.assembly}). The
 * wheel model ({@code block/helm_wheel}) is a stand-alone model, see {@code client/HelmClient}.
 */
public final class HelmContent {

    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<HelmBlockEntity>> HELM_ENTITY =
            ModRegistry.blockEntity("helm", HelmBlockEntity::new, AssemblyContent.HELM);

    private HelmContent() {
    }

    /** Loads the class so the entries above are registered. Called from {@code registerContent()}. */
    public static void init() {
    }
}

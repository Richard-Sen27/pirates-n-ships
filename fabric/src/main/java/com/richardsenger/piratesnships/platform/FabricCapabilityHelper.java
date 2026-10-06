package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.platform.services.ICapabilityHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

/**
 * TODO Fabric port (milestone 22): Fabric Transfer API, {@code ItemStorage.SIDED.registerForBlockEntity(...)} with
 * {@code InventoryStorage.of(container, side)} (which honors {@code WorldlyContainer} face rules).
 */
public class FabricCapabilityHelper implements ICapabilityHelper {

    @Override
    public <T extends BlockEntity> void registerBlockContainer(Supplier<? extends BlockEntityType<T>> type, ContainerView<? super T> view) {
        throw new UnsupportedOperationException("Fabric port: milestone 22");
    }
}

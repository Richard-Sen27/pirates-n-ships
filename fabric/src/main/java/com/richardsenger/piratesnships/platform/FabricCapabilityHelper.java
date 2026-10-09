package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.platform.services.ICapabilityHelper;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Fabric Transfer API twin of the NeoForge item handler capability: each registered container is exposed through
 * {@link ItemStorage#SIDED} as {@link InventoryStorage#of(Container, net.minecraft.core.Direction)}, which applies a
 * {@link WorldlyContainer}'s face rules for a side (all slots for a {@code null} side) and a plain container's
 * {@code canPlaceItem}, and calls {@code setChanged} after a transaction. One difference to NeoForge: for a
 * {@code null} side Fabric wraps a {@link WorldlyContainer} as a plain container (all slots, {@code canPlaceItem}, but no
 * {@code canPlaceItemThroughFace} / {@code canTakeItemThroughFace}), where NeoForge's {@code SidedInvWrapper} still asks
 * the face rules with a {@code null} direction. Pipes always pass a side. Registrations are collected during common
 * init and handed to the lookup by {@link #finish()}, when every block entity type exists.
 */
public final class FabricCapabilityHelper implements ICapabilityHelper {

    private record Entry<T extends BlockEntity>(Supplier<? extends BlockEntityType<T>> type, ContainerView<? super T> view) {
        void register() {
            ItemStorage.SIDED.registerForBlockEntity((be, side) -> {
                Container c = view.get(be, side);
                return c == null ? null : InventoryStorage.of(c, side);
            }, type.get());
        }
    }

    private final List<Entry<?>> entries = new ArrayList<>();
    private boolean finished;

    @Override
    public synchronized <T extends BlockEntity> void registerBlockContainer(Supplier<? extends BlockEntityType<T>> type, ContainerView<? super T> view) {
        Entry<T> entry = new Entry<>(type, view);
        if (finished) entry.register();
        else entries.add(entry);
    }

    /** Called once by the entry point after common init. */
    public synchronized void finish() {
        if (finished) throw new IllegalStateException("FabricCapabilityHelper finished twice");
        finished = true;
        entries.forEach(Entry::register);
    }
}

package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.platform.services.ICapabilityHelper;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Collects container registrations during mod construction and registers them as
 * {@link Capabilities.ItemHandler#BLOCK} providers when {@link RegisterCapabilitiesEvent} fires. A
 * {@link WorldlyContainer} is wrapped in a {@link SidedInvWrapper} (face rules apply), any other {@link Container}
 * in an {@link InvWrapper} ({@code canPlaceItem} applies).
 */
public final class NeoForgeCapabilityHelper implements ICapabilityHelper {

    private record Entry<T extends BlockEntity>(Supplier<? extends BlockEntityType<T>> type, ContainerView<? super T> view) {
        void register(RegisterCapabilitiesEvent event) {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type.get(), (be, side) -> {
                Container c = view.get(be, side);
                if (c == null) return null;
                return c instanceof WorldlyContainer w ? new SidedInvWrapper(w, side) : new InvWrapper(c);
            });
        }
    }

    private final List<Entry<?>> entries = new ArrayList<>();
    private boolean attached;

    @Override
    public synchronized <T extends BlockEntity> void registerBlockContainer(Supplier<? extends BlockEntityType<T>> type, ContainerView<? super T> view) {
        entries.add(new Entry<>(type, view));
    }

    /** Called once by the entry point. */
    public synchronized void attach(IEventBus modBus) {
        if (attached) throw new IllegalStateException("NeoForgeCapabilityHelper attached twice");
        attached = true;
        modBus.addListener(RegisterCapabilitiesEvent.class, this::onRegister);
    }

    private synchronized void onRegister(RegisterCapabilitiesEvent event) {
        entries.forEach(e -> e.register(event));
    }
}

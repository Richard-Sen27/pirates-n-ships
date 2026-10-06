package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.platform.services.IRegistryHelper;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** One {@link DeferredRegister} per vanilla registry, attached to the mod bus by the entry point. */
public final class NeoForgeRegistryHelper implements IRegistryHelper {

    private final Map<ResourceKey<?>, DeferredRegister<?>> registers = new LinkedHashMap<>();
    private final List<AttributeEntry<?>> attributes = new ArrayList<>();
    private IEventBus modBus;

    private record AttributeEntry<E extends LivingEntity>(Supplier<EntityType<E>> type, Supplier<AttributeSupplier.Builder> builder) { }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized <R, T extends R> RegistryEntry<R, T> register(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory) {
        DeferredRegister<R> reg = (DeferredRegister<R>) registers.computeIfAbsent(registry, k -> {
            DeferredRegister<R> created = DeferredRegister.create(registry, Constants.MOD_ID);
            if (modBus != null) created.register(modBus);
            return created;
        });
        DeferredHolder<R, T> holder = reg.register(name, factory);
        return new RegistryEntry<>(holder.getKey(), holder, holder);
    }

    @Override
    public synchronized <E extends LivingEntity> void registerEntityAttributes(Supplier<EntityType<E>> type, Supplier<AttributeSupplier.Builder> attributes) {
        this.attributes.add(new AttributeEntry<>(type, attributes));
    }

    /** Called once by the entry point after common init. */
    public synchronized void attach(IEventBus modBus) {
        this.modBus = modBus;
        registers.values().forEach(r -> r.register(modBus));
        modBus.addListener(EntityAttributeCreationEvent.class, this::onAttributes);
    }

    private synchronized void onAttributes(EntityAttributeCreationEvent event) {
        for (AttributeEntry<?> e : attributes) event.put(e.type().get(), e.builder().get().build());
    }
}

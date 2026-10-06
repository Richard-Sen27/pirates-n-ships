package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.platform.services.IRegistryHelper;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

import java.util.function.Supplier;

/** TODO Fabric port (milestone 22). */
public class FabricRegistryHelper implements IRegistryHelper {

    @Override
    public <R, T extends R> RegistryEntry<R, T> register(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory) {
        throw new UnsupportedOperationException("Fabric port: milestone 22");
    }

    @Override
    public <E extends LivingEntity> void registerEntityAttributes(Supplier<EntityType<E>> type, Supplier<AttributeSupplier.Builder> attributes) {
        throw new UnsupportedOperationException("Fabric port: milestone 22");
    }
}

package com.richardsenger.piratesnships.platform.registry;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * A lazily registered registry entry, declared in common and filled by the loader at registration time.
 * {@link #get()} throws until the registry event has run.
 *
 * @param <R> the registry type (e.g. {@code Block})
 * @param <T> the concrete entry type (e.g. {@code MyBlock})
 */
public final class RegistryEntry<R, T extends R> implements Supplier<T> {

    private final ResourceKey<R> key;
    private final Supplier<T> value;
    private final Holder<R> holder;

    /** Created by the loader's {@code IRegistryHelper}. */
    public RegistryEntry(ResourceKey<R> key, Supplier<T> value, Holder<R> holder) {
        this.key = key;
        this.value = value;
        this.holder = holder;
    }

    @Override
    public T get() {
        return value.get();
    }

    /** The registry key of this entry. */
    public ResourceKey<R> key() {
        return key;
    }

    /** The id of this entry. */
    public ResourceLocation id() {
        return key.location();
    }

    /** A holder for APIs that want {@code Holder<R>} (mob effects, sound events, ...). */
    public Holder<R> holder() {
        return holder;
    }
}

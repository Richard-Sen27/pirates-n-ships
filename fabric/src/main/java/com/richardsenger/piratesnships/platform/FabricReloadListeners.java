package com.richardsenger.piratesnships.platform;

import com.mojang.serialization.DynamicOps;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * {@code CommonEvents.ADD_RELOAD_LISTENERS} on Fabric. NeoForge asks for the listeners at every data reload
 * ({@code AddReloadListenerEvent}); Fabric API takes one listener per id, built per reload by a factory that receives
 * the reload's registry lookup. So one factory fires the common event per reload, collects whatever the listeners add,
 * and runs them as a single {@link Composite} listener.
 *
 * <p>The common event passes a {@link RegistryAccess} (NeoForge's {@code compositeAccess()}); Fabric hands the factory
 * only a {@link HolderLookup.Provider} (the reload's {@code ConfigurableRegistryLookup}). {@link LookupAccess} adapts it:
 * lookups and serialization contexts come from the reload's provider (tags, dynamic registries), {@code registry(key)}
 * answers from the static registries only. Our one listener ({@code DefinitionLoading}) only uses it as a provider.
 */
final class FabricReloadListeners {

    private static final ResourceLocation ID = Constants.id("reload_listeners");

    private FabricReloadListeners() {
    }

    static void attach() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(ID, registries -> {
            List<PreparableReloadListener> children = new ArrayList<>();
            CommonEvents.ADD_RELOAD_LISTENERS.invoker().add(children::add, new LookupAccess(registries));
            return new Composite(List.copyOf(children));
        });
    }

    /** Runs several listeners as one: each prepares, the outer barrier is passed once all of them reached it. */
    private record Composite(List<PreparableReloadListener> children) implements IdentifiableResourceReloadListener {

        @Override
        public ResourceLocation getFabricId() {
            return ID;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources, ProfilerFiller prepProfiler,
                                              ProfilerFiller reloadProfiler, Executor background, Executor game) {
            if (children.isEmpty()) return barrier.wait(Boolean.TRUE).thenAccept(v -> { });
            AtomicInteger waiting = new AtomicInteger(children.size());
            CompletableFuture<Void> passed = new CompletableFuture<>();
            PreparationBarrier inner = new PreparationBarrier() {
                @Override
                public <T> CompletableFuture<T> wait(T value) {
                    if (waiting.decrementAndGet() == 0) {
                        barrier.wait(Boolean.TRUE).whenComplete((v, e) -> {
                            if (e != null) passed.completeExceptionally(e);
                            else passed.complete(null);
                        });
                    }
                    return passed.thenApply(v -> value);
                }
            };
            CompletableFuture<?>[] futures = new CompletableFuture<?>[children.size()];
            for (int i = 0; i < futures.length; i++) {
                futures[i] = children.get(i).reload(inner, resources, prepProfiler, reloadProfiler, background, game);
            }
            return CompletableFuture.allOf(futures);
        }

        @Override
        public String getName() {
            return ID.toString();
        }
    }

    /** A {@link RegistryAccess} view of the reload's provider (see the class comment). */
    private record LookupAccess(HolderLookup.Provider provider) implements RegistryAccess {

        private static final RegistryAccess STATIC = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

        @Override
        public <E> Optional<Registry<E>> registry(ResourceKey<? extends Registry<? extends E>> key) {
            return STATIC.registry(key);
        }

        @Override
        public Stream<RegistryEntry<?>> registries() {
            return STATIC.registries();
        }

        @Override
        public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
            return provider.lookup(key);
        }

        @Override
        public Stream<ResourceKey<? extends Registry<?>>> listRegistries() {
            return provider.listRegistries();
        }

        @Override
        public <V> RegistryOps<V> createSerializationContext(DynamicOps<V> ops) {
            return provider.createSerializationContext(ops);
        }
    }
}

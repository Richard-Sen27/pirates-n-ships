package com.richardsenger.piratesnships.core.datagen;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Knows which tags vanilla defines, so that our tag providers accept required references to them
 * ({@code tags.tag(OURS).addTag(BlockTags.DIRT)}).
 *
 * <p>The answer comes from vanilla's built-in data pack ({@link ServerPacksSource#createVanillaPackSource()}, the same
 * pack the game loads its own data from, on every loader): a tag {@code minecraft:x} of registry {@code R} exists if
 * the pack has {@code data/minecraft/tags/<R>/x.json}. Only the {@code minecraft} namespace is answered; tags of other
 * mods ({@code c:}, {@code sable:}, ...) are unknown here and must be referenced optionally. Experimental feature
 * packs (bundles, trade rebalance) are not part of that pack, so their tags count as missing.
 *
 * <p>Plugged into vanilla's tag provider as its <i>parent lookup</i> ({@code TagsProvider.TagLookup}): the provider
 * treats a reference as present if it is defined in this run or the parent contains it, and fails the run
 * otherwise.
 */
public final class VanillaTags {

    private static volatile VanillaPackResources pack;
    private static final Map<ResourceLocation, Boolean> CACHE = new ConcurrentHashMap<>();

    private VanillaTags() {
    }

    /** Whether vanilla's built-in data pack defines {@code tag}. */
    public static boolean exists(TagKey<?> tag) {
        return exists(tag.registry(), tag.location());
    }

    /** Whether vanilla's built-in data pack defines the tag {@code tag} of {@code registry}. */
    public static boolean exists(ResourceKey<? extends Registry<?>> registry, ResourceLocation tag) {
        if (!ResourceLocation.DEFAULT_NAMESPACE.equals(tag.getNamespace())) {
            return false;
        }
        ResourceLocation file = tag.withPath(p -> Registries.tagsDirPath(registry) + "/" + p + ".json");
        return CACHE.computeIfAbsent(file, f -> pack().getResource(PackType.SERVER_DATA, f) != null);
    }

    /** A parent lookup for a vanilla {@code TagsProvider}: contains exactly the vanilla tags of {@code registry}. */
    public static <T> TagsProvider.TagLookup<T> lookup(ResourceKey<? extends Registry<T>> registry) {
        return key -> exists(registry, key.location()) ? Optional.of(TagBuilder.create()) : Optional.empty();
    }

    private static VanillaPackResources pack() {
        VanillaPackResources p = pack;
        if (p == null) {
            synchronized (VanillaTags.class) {
                p = pack;
                if (p == null) {
                    p = ServerPacksSource.createVanillaPackSource();
                    pack = p;
                }
            }
        }
        return p;
    }
}

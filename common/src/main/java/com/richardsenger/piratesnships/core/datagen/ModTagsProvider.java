package com.richardsenger.piratesnships.core.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Tags for a built-in registry (blocks, items, entity types, ...). {@link #tag} is widened to public for
 * contributors. The returned appender offers {@code add(value)} and {@code addTag(TagKey)};
 * optional entries go through {@link #addOptional} and {@link #addOptionalTag} on this provider.
 *
 * <p>Tag ids may be in any namespace: {@code tag(TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("sable", "heavy")))}
 * writes {@code data/sable/tags/block/heavy.json} with {@code "replace": false}, so it merges with Sable's own file.
 * A <i>reference</i> to another tag ({@code addTag}) must be to a tag defined in this same run; for tags owned by
 * other mods use {@code addOptionalTag}, otherwise the data run fails with "missing following references".
 */
public final class ModTagsProvider<T> extends IntrinsicHolderTagsProvider<T> {

    private final List<Consumer<ModTagsProvider<T>>> contributors;

    ModTagsProvider(PackOutput output, ResourceKey<? extends Registry<T>> registry, CompletableFuture<HolderLookup.Provider> lookup,
                    Function<T, ResourceKey<T>> keyExtractor, List<Consumer<ModTagsProvider<T>>> contributors) {
        super(output, registry, lookup, keyExtractor);
        this.contributors = contributors;
    }

    /** A provider for a built-in registry; element keys come from {@link BuiltInRegistries#REGISTRY}. */
    @SuppressWarnings("unchecked")
    static <T> ModTagsProvider<T> forBuiltIn(PackOutput output, ResourceKey<? extends Registry<T>> registry,
                                             CompletableFuture<HolderLookup.Provider> lookup, List<Consumer<ModTagsProvider<T>>> contributors) {
        Registry<T> builtIn = (Registry<T>) BuiltInRegistries.REGISTRY.get(registry.location());
        if (builtIn == null) {
            throw new IllegalArgumentException("Tag datagen supports built-in registries only, not " + registry.location());
        }
        Function<T, ResourceKey<T>> keys = value -> builtIn.getResourceKey(value).orElseThrow(
                () -> new IllegalStateException("Tagged value " + value + " is not registered in " + registry.location()));
        return new ModTagsProvider<>(output, registry, lookup, keys, contributors);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        contributors.forEach(c -> c.accept(this));
    }

    @Override
    public IntrinsicTagAppender<T> tag(TagKey<T> tag) {
        return super.tag(tag);
    }

    /**
     * Adds an optional element (e.g. another mod's block) to {@code tag}; skipped at load time if it does not exist.
     * (Vanilla's {@code addOptional} sits on a class that is not public in common, hence this helper.)
     */
    public ModTagsProvider<T> addOptional(TagKey<T> tag, ResourceLocation element) {
        super.tag(tag).addOptional(element);
        return this;
    }

    /** Adds an optional reference to another tag (e.g. {@code #c:...} or another mod's tag) to {@code tag}. */
    public ModTagsProvider<T> addOptionalTag(TagKey<T> tag, ResourceLocation otherTag) {
        super.tag(tag).addOptionalTag(otherTag);
        return this;
    }
}

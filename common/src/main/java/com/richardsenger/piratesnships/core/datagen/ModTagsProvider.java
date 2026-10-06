package com.richardsenger.piratesnships.core.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/** Tags for a registry with intrinsic holders (blocks, items). {@link #tag} is widened to public for contributors. */
public final class ModTagsProvider<T> extends IntrinsicHolderTagsProvider<T> {

    private final List<Consumer<ModTagsProvider<T>>> contributors;

    ModTagsProvider(PackOutput output, ResourceKey<? extends Registry<T>> registry, CompletableFuture<HolderLookup.Provider> lookup,
                    Function<T, ResourceKey<T>> keyExtractor, List<Consumer<ModTagsProvider<T>>> contributors) {
        super(output, registry, lookup, keyExtractor);
        this.contributors = contributors;
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        contributors.forEach(c -> c.accept(this));
    }

    @Override
    public IntrinsicTagAppender<T> tag(TagKey<T> tag) {
        return super.tag(tag);
    }
}

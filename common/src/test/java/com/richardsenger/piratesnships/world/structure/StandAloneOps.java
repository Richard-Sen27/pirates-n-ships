package com.richardsenger.piratesnships.world.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit support for the worldgen data tests: registry ops whose holders resolve as unbound stand-alone references (the
 * format is checked, not cross-file references), and the repository's generated and committed files. Call after
 * {@code Bootstrap.bootStrap()}.
 */
public final class StandAloneOps {

    private StandAloneOps() {
    }

    public static RegistryOps<JsonElement> create() {
        return RegistryOps.create(JsonOps.INSTANCE, new RegistryOps.RegistryInfoLookup() {
            private final Map<ResourceKey<?>, RegistryOps.RegistryInfo<?>> infos = new HashMap<>();

            @Override
            @SuppressWarnings("unchecked")
            public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
                return Optional.of((RegistryOps.RegistryInfo<T>) infos.computeIfAbsent(key, k -> standAlone()));
            }

            private <T> RegistryOps.RegistryInfo<T> standAlone() {
                HolderOwner<T> owner = new HolderOwner<>() { };
                HolderGetter<T> getter = new HolderGetter<>() {
                    @Override
                    public Optional<Holder.Reference<T>> get(ResourceKey<T> element) {
                        return Optional.of(Holder.Reference.createStandAlone(owner, element));
                    }

                    @Override
                    public Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
                        return Optional.of(HolderSet.emptyNamed(owner, tag));
                    }
                };
                return new RegistryOps.RegistryInfo<>(owner, getter, Lifecycle.stable());
            }
        });
    }

    public static Path root() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("tools/schem_to_structure.py"))) root = root.getParent();
        assertNotNull(root, "repository root");
        return root;
    }

    /** {@code common/src/generated/resources/data/pirates_n_ships/<path>.json}. */
    public static JsonElement generated(String path) throws IOException {
        Path file = root().resolve("common/src/generated/resources/data/pirates_n_ships/" + path + ".json");
        assertTrue(Files.exists(file), "missing " + file);
        try (Reader r = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(r);
        }
    }
}

package com.richardsenger.piratesnships.platform.attachment;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Declares a typed data attachment for entities, levels and chunks. Pure data: creating a key does not touch any
 * loader service, so classes holding keys stay loadable in unit tests. Register each key once with
 * {@code Services.ATTACHMENTS.register(KEY)} from the module's {@code registerContent()}.
 *
 * <pre>{@code
 * public static final AttachmentKey<Integer> STAMINA = AttachmentKey.builder("stamina", () -> 100)
 *         .persistent(Codec.INT).synced(ByteBufCodecs.VAR_INT).copyOnDeath().build();
 * }</pre>
 *
 * <p>Values should be immutable (records, primitives). Changes are only saved and synced through
 * {@code Services.ATTACHMENTS.set(...)}; mutating a value in place is neither marked dirty nor synced.
 */
public final class AttachmentKey<T> {

    private final ResourceLocation id;
    private final Supplier<T> defaultValue;
    private final @Nullable Codec<T> codec;
    private final @Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec;
    private final boolean copyOnDeath;

    private AttachmentKey(Builder<T> b) {
        this.id = b.id;
        this.defaultValue = b.defaultValue;
        this.codec = b.codec;
        this.streamCodec = b.streamCodec;
        this.copyOnDeath = b.copyOnDeath;
    }

    /** Starts a key {@code pirates_n_ships:<name>} whose missing value is created by {@code defaultValue}. */
    public static <T> Builder<T> builder(String name, Supplier<T> defaultValue) {
        return new Builder<>(Constants.id(name), defaultValue);
    }

    public ResourceLocation id() {
        return id;
    }

    public Supplier<T> defaultValue() {
        return defaultValue;
    }

    /** The codec used to save the value, or {@code null} if the value is not persisted. */
    public @Nullable Codec<T> codec() {
        return codec;
    }

    /** The codec used to sync the value to clients, or {@code null} if it is server-only. */
    public @Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec() {
        return streamCodec;
    }

    /** Whether a player's value survives death (requires persistence). */
    public boolean copyOnDeath() {
        return copyOnDeath;
    }

    @Override
    public String toString() {
        return "AttachmentKey[" + id + "]";
    }

    /** Builder for {@link AttachmentKey}. */
    public static final class Builder<T> {
        private final ResourceLocation id;
        private final Supplier<T> defaultValue;
        private @Nullable Codec<T> codec;
        private @Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec;
        private boolean copyOnDeath;

        private Builder(ResourceLocation id, Supplier<T> defaultValue) {
            this.id = id;
            this.defaultValue = Objects.requireNonNull(defaultValue);
        }

        /** Saves the value with the holder (entity / level / chunk NBT). */
        public Builder<T> persistent(Codec<T> codec) {
            this.codec = Objects.requireNonNull(codec);
            return this;
        }

        /** Syncs the value to every client that receives the holder, whenever it is set. */
        public Builder<T> synced(StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
            this.streamCodec = Objects.requireNonNull(streamCodec);
            return this;
        }

        /** Keeps a player's value when they die and respawn. Requires {@link #persistent}. */
        public Builder<T> copyOnDeath() {
            this.copyOnDeath = true;
            return this;
        }

        public AttachmentKey<T> build() {
            if (copyOnDeath && codec == null) {
                throw new IllegalStateException("copyOnDeath requires persistent(codec): " + id);
            }
            return new AttachmentKey<>(this);
        }
    }
}

package com.richardsenger.piratesnships.core.data;

import com.richardsenger.piratesnships.Constants;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server → client: the full entry set of one synced {@link DefinitionType}. One payload per type, sent on login
 * and after every datapack reload. Generic over all types: the type name goes first, then entries encoded with the
 * type's own stream codec.
 */
public record DefinitionSyncPayload(DefinitionType<?> definitionType, Map<ResourceLocation, ?> entries) implements CustomPacketPayload {

    public static final Type<DefinitionSyncPayload> TYPE = new Type<>(Constants.id("definition_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DefinitionSyncPayload> STREAM_CODEC = StreamCodec.of(
            DefinitionSyncPayload::write, DefinitionSyncPayload::read);

    /** The current server entries of {@code type}, which must be synced. */
    public static <T> DefinitionSyncPayload of(DefinitionType<T> type) {
        if (!type.isSynced()) throw new IllegalArgumentException(type + " has no stream codec");
        return new DefinitionSyncPayload(type, type.server().all());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Client handler: replaces the client store of the type. */
    public void applyOnClient() {
        apply(definitionType, entries);
    }

    @SuppressWarnings("unchecked")
    private static <T> void apply(DefinitionType<T> type, Map<ResourceLocation, ?> entries) {
        type.acceptClient((Map<ResourceLocation, T>) entries);
    }

    private static void write(RegistryFriendlyByteBuf buf, DefinitionSyncPayload payload) {
        buf.writeUtf(payload.definitionType.name());
        writeEntries(buf, payload.definitionType, payload.entries);
    }

    @SuppressWarnings("unchecked")
    private static <T> void writeEntries(RegistryFriendlyByteBuf buf, DefinitionType<T> type, Map<ResourceLocation, ?> entries) {
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec = type.streamCodec().orElseThrow(
                () -> new IllegalStateException(type + " has no stream codec"));
        buf.writeVarInt(entries.size());
        entries.forEach((id, value) -> {
            buf.writeResourceLocation(id);
            codec.encode(buf, (T) value);
        });
    }

    private static DefinitionSyncPayload read(RegistryFriendlyByteBuf buf) {
        String name = buf.readUtf();
        DefinitionType<?> type = DefinitionType.byName(name).orElseThrow(
                () -> new DecoderException("Unknown definition type " + name + " (mod version mismatch?)"));
        return new DefinitionSyncPayload(type, readEntries(buf, type));
    }

    private static <T> Map<ResourceLocation, T> readEntries(RegistryFriendlyByteBuf buf, DefinitionType<T> type) {
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec = type.streamCodec().orElseThrow(
                () -> new DecoderException(type + " is not synced on this side (mod version mismatch?)"));
        int size = buf.readVarInt();
        Map<ResourceLocation, T> entries = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            entries.put(buf.readResourceLocation(), codec.decode(buf));
        }
        return entries;
    }
}

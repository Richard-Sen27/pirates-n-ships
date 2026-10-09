package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;
import com.richardsenger.piratesnships.platform.services.IAttachmentHelper;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps {@link AttachmentKey}s to Fabric Data Attachment API types: the default value as initializer, persistence,
 * sync to every client that sees the holder ({@link AttachmentSyncPredicate#all()}) and copy-on-death all built in.
 * Fabric attaches data to entities, block entities, chunks and levels ({@code Level} through a mixin, client levels
 * included, which is why it is reached through a cast). {@code get} stores the default, as NeoForge's {@code getData}
 * does; setting a chunk value marks the chunk unsaved (Fabric does that itself).
 *
 * <p>Player copies: Fabric API transfers a player's attachments only after the respawn ({@code AFTER_RESPAWN}), while
 * NeoForge copies them inside {@code ServerPlayer#restoreFrom}, before {@code CommonEvents.PLAYER_CLONE}. {@link #attach}
 * copies our keys at {@code ServerPlayerEvents.COPY_FROM} (fired by {@code restoreFrom}), registered before the event
 * forwarder so {@code PLAYER_CLONE} listeners see the copied values: on death the {@code copyOnDeath} keys, otherwise
 * every key present. Fabric's own transfer later sets the same values again.
 */
public final class FabricAttachmentHelper implements IAttachmentHelper {

    private final Map<ResourceLocation, AttachmentType<?>> types = new HashMap<>();

    @Override
    public synchronized <T> void register(AttachmentKey<T> key) {
        if (types.containsKey(key.id())) throw new IllegalStateException("Attachment registered twice: " + key);
        types.put(key.id(), AttachmentRegistry.<T>create(key.id(), b -> {
            b.initializer(key.defaultValue());
            if (key.codec() != null) b.persistent(key.codec());
            if (key.streamCodec() != null) b.syncWith(key.streamCodec(), AttachmentSyncPredicate.all());
            if (key.copyOnDeath()) b.copyOnDeath();
        }));
    }

    /** Called once by the entry point, before {@code FabricEventForwarder.attach()}. */
    public void attach() {
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            List<AttachmentType<?>> all;
            synchronized (this) {
                all = List.copyOf(types.values());
            }
            for (AttachmentType<?> type : all) copy(oldPlayer, newPlayer, type, !alive);
        });
    }

    private static <T> void copy(AttachmentTarget from, AttachmentTarget to, AttachmentType<T> type, boolean death) {
        if (death && !type.copyOnDeath()) return;
        T value = from.getAttached(type);
        if (value != null) to.setAttached(type, value);
    }

    @SuppressWarnings("unchecked")
    private <T> AttachmentType<T> type(AttachmentKey<T> key) {
        AttachmentType<?> type = types.get(key.id());
        if (type == null) throw new IllegalStateException("Attachment not registered: " + key + " (call Services.ATTACHMENTS.register in registerContent)");
        return (AttachmentType<T>) type;
    }

    private <T> T get(AttachmentTarget h, AttachmentKey<T> k) { return h.getAttachedOrCreate(type(k)); }
    private <T> void set(AttachmentTarget h, AttachmentKey<T> k, T v) { h.setAttached(type(k), v); }
    private <T> boolean has(AttachmentTarget h, AttachmentKey<T> k) { return h.hasAttached(type(k)); }
    private <T> void remove(AttachmentTarget h, AttachmentKey<T> k) { h.removeAttached(type(k)); }

    private static AttachmentTarget target(Object holder) {
        return (AttachmentTarget) holder;
    }

    @Override public <T> T get(Entity holder, AttachmentKey<T> key) { return get(target(holder), key); }
    @Override public <T> void set(Entity holder, AttachmentKey<T> key, T value) { set(target(holder), key, value); }
    @Override public <T> boolean has(Entity holder, AttachmentKey<T> key) { return has(target(holder), key); }
    @Override public <T> void remove(Entity holder, AttachmentKey<T> key) { remove(target(holder), key); }

    @Override public <T> T get(Level holder, AttachmentKey<T> key) { return get(target(holder), key); }
    @Override public <T> void set(Level holder, AttachmentKey<T> key, T value) { set(target(holder), key, value); }
    @Override public <T> boolean has(Level holder, AttachmentKey<T> key) { return has(target(holder), key); }
    @Override public <T> void remove(Level holder, AttachmentKey<T> key) { remove(target(holder), key); }

    @Override public <T> T get(ChunkAccess holder, AttachmentKey<T> key) { return get(target(holder), key); }
    @Override public <T> void set(ChunkAccess holder, AttachmentKey<T> key, T value) { set(target(holder), key, value); }
    @Override public <T> boolean has(ChunkAccess holder, AttachmentKey<T> key) { return has(target(holder), key); }
    @Override public <T> void remove(ChunkAccess holder, AttachmentKey<T> key) { remove(target(holder), key); }
}

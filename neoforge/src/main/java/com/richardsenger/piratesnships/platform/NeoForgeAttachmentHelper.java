package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;
import com.richardsenger.piratesnships.platform.services.IAttachmentHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/** Maps {@link AttachmentKey}s to NeoForge {@link AttachmentType}s (persistence, sync and copy-on-death built in). */
public final class NeoForgeAttachmentHelper implements IAttachmentHelper {

    private final DeferredRegister<AttachmentType<?>> register = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Constants.MOD_ID);
    private final Map<ResourceLocation, DeferredHolder<AttachmentType<?>, ? extends AttachmentType<?>>> types = new HashMap<>();

    @Override
    public synchronized <T> void register(AttachmentKey<T> key) {
        if (types.containsKey(key.id())) throw new IllegalStateException("Attachment registered twice: " + key);
        types.put(key.id(), register.register(key.id().getPath(), () -> {
            AttachmentType.Builder<T> b = AttachmentType.builder(key.defaultValue());
            if (key.codec() != null) b.serialize(key.codec());
            if (key.streamCodec() != null) b.sync(key.streamCodec());
            if (key.copyOnDeath()) b.copyOnDeath();
            return b.build();
        }));
    }

    /** Called once by the entry point. */
    public void attach(IEventBus modBus) {
        register.register(modBus);
    }

    @SuppressWarnings("unchecked")
    private <T> AttachmentType<T> type(AttachmentKey<T> key) {
        var holder = types.get(key.id());
        if (holder == null) throw new IllegalStateException("Attachment not registered: " + key + " (call Services.ATTACHMENTS.register in registerContent)");
        return (AttachmentType<T>) holder.get();
    }

    private <T> T get(IAttachmentHolder h, AttachmentKey<T> k) { return h.getData(type(k)); }
    private <T> void set(IAttachmentHolder h, AttachmentKey<T> k, T v) { h.setData(type(k), v); }
    private <T> boolean has(IAttachmentHolder h, AttachmentKey<T> k) { return h.hasData(type(k)); }
    private <T> void remove(IAttachmentHolder h, AttachmentKey<T> k) { h.removeData(type(k)); }

    @Override public <T> T get(Entity holder, AttachmentKey<T> key) { return get((IAttachmentHolder) holder, key); }
    @Override public <T> void set(Entity holder, AttachmentKey<T> key, T value) { set((IAttachmentHolder) holder, key, value); }
    @Override public <T> boolean has(Entity holder, AttachmentKey<T> key) { return has((IAttachmentHolder) holder, key); }
    @Override public <T> void remove(Entity holder, AttachmentKey<T> key) { remove((IAttachmentHolder) holder, key); }

    @Override public <T> T get(Level holder, AttachmentKey<T> key) { return get((IAttachmentHolder) holder, key); }
    @Override public <T> void set(Level holder, AttachmentKey<T> key, T value) { set((IAttachmentHolder) holder, key, value); }
    @Override public <T> boolean has(Level holder, AttachmentKey<T> key) { return has((IAttachmentHolder) holder, key); }
    @Override public <T> void remove(Level holder, AttachmentKey<T> key) { remove((IAttachmentHolder) holder, key); }

    @Override public <T> T get(ChunkAccess holder, AttachmentKey<T> key) { return get((IAttachmentHolder) holder, key); }

    @Override
    public <T> void set(ChunkAccess holder, AttachmentKey<T> key, T value) {
        set((IAttachmentHolder) holder, key, value);
        holder.setUnsaved(true);
    }

    @Override public <T> boolean has(ChunkAccess holder, AttachmentKey<T> key) { return has((IAttachmentHolder) holder, key); }

    @Override
    public <T> void remove(ChunkAccess holder, AttachmentKey<T> key) {
        remove((IAttachmentHolder) holder, key);
        holder.setUnsaved(true);
    }
}

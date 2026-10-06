package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;
import com.richardsenger.piratesnships.platform.services.IAttachmentHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;

/** TODO Fabric port (milestone 22): Fabric Data Attachment API. */
public class FabricAttachmentHelper implements IAttachmentHelper {

    private static UnsupportedOperationException todo() {
        return new UnsupportedOperationException("Fabric port: milestone 22");
    }

    @Override public <T> void register(AttachmentKey<T> key) { throw todo(); }
    @Override public <T> T get(Entity holder, AttachmentKey<T> key) { throw todo(); }
    @Override public <T> void set(Entity holder, AttachmentKey<T> key, T value) { throw todo(); }
    @Override public <T> boolean has(Entity holder, AttachmentKey<T> key) { throw todo(); }
    @Override public <T> void remove(Entity holder, AttachmentKey<T> key) { throw todo(); }
    @Override public <T> T get(Level holder, AttachmentKey<T> key) { throw todo(); }
    @Override public <T> void set(Level holder, AttachmentKey<T> key, T value) { throw todo(); }
    @Override public <T> boolean has(Level holder, AttachmentKey<T> key) { throw todo(); }
    @Override public <T> void remove(Level holder, AttachmentKey<T> key) { throw todo(); }
    @Override public <T> T get(ChunkAccess holder, AttachmentKey<T> key) { throw todo(); }
    @Override public <T> void set(ChunkAccess holder, AttachmentKey<T> key, T value) { throw todo(); }
    @Override public <T> boolean has(ChunkAccess holder, AttachmentKey<T> key) { throw todo(); }
    @Override public <T> void remove(ChunkAccess holder, AttachmentKey<T> key) { throw todo(); }
}

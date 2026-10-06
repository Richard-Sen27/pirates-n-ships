package com.richardsenger.piratesnships.platform.services;

import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Reads and writes {@link AttachmentKey} values on entities, levels and chunks. {@code get} creates (and stores)
 * the default value when none is present. {@code set} marks the holder dirty and syncs synced keys.
 */
public interface IAttachmentHelper {

    /** Registers a key. Call once per key during mod initialization ({@code registerContent()}). */
    <T> void register(AttachmentKey<T> key);

    <T> T get(Entity holder, AttachmentKey<T> key);

    <T> void set(Entity holder, AttachmentKey<T> key, T value);

    <T> boolean has(Entity holder, AttachmentKey<T> key);

    <T> void remove(Entity holder, AttachmentKey<T> key);

    <T> T get(Level holder, AttachmentKey<T> key);

    <T> void set(Level holder, AttachmentKey<T> key, T value);

    <T> boolean has(Level holder, AttachmentKey<T> key);

    <T> void remove(Level holder, AttachmentKey<T> key);

    <T> T get(ChunkAccess holder, AttachmentKey<T> key);

    <T> void set(ChunkAccess holder, AttachmentKey<T> key, T value);

    <T> boolean has(ChunkAccess holder, AttachmentKey<T> key);

    <T> void remove(ChunkAccess holder, AttachmentKey<T> key);
}

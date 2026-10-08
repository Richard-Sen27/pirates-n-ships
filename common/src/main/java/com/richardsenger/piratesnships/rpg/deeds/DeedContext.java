package com.richardsenger.piratesnships.rpg.deeds;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * What a deed was done to, for the listeners (careers, faction state, logs): the victim's entity type and id, the port,
 * and an amount (doubloons of a sale or fine, points of a fine). All optional.
 */
public record DeedContext(Optional<ResourceLocation> victimKind, Optional<UUID> victim, Optional<ResourceLocation> port, long amount) {

    public static final DeedContext NONE = new DeedContext(Optional.empty(), Optional.empty(), Optional.empty(), 0L);

    /** A deed against an entity. */
    public static DeedContext victim(Entity victim) {
        return new DeedContext(Optional.of(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType())), Optional.of(victim.getUUID()),
                Optional.empty(), 0L);
    }

    /** A deed against something known only by id (a ship). */
    public static DeedContext victim(@Nullable UUID victim) {
        return new DeedContext(Optional.empty(), Optional.ofNullable(victim), Optional.empty(), 0L);
    }

    /** A deed at a port, worth {@code amount}. */
    public static DeedContext port(ResourceLocation port, long amount) {
        return new DeedContext(Optional.empty(), Optional.empty(), Optional.of(port), amount);
    }

    /** No particular victim or port, worth {@code amount}. */
    public static DeedContext amount(long amount) {
        return new DeedContext(Optional.empty(), Optional.empty(), Optional.empty(), amount);
    }
}

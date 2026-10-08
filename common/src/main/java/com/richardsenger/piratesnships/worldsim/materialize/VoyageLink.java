package com.richardsenger.piratesnships.worldsim.materialize;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The voyage a materialised ship belongs to (WS3b), saved in the ship's sub-level user data under
 * {@value #USER_DATA_KEY} so that the ship is recognised again after a reload (pattern: {@code SailingRuntimes}).
 *
 * @param voyage    the voyage id
 * @param plundered whether a player already plundered this voyage (the deed counts once)
 * @param overflow  cargo units that did not fit into the ship's containers; they stay with the record
 */
public record VoyageLink(UUID voyage, boolean plundered, Map<ResourceLocation, Integer> overflow) {

    public static final String USER_DATA_KEY = Constants.MOD_ID + "_voyage";

    public static final Codec<VoyageLink> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("voyage").forGetter(VoyageLink::voyage),
            Codec.BOOL.optionalFieldOf("plundered", false).forGetter(VoyageLink::plundered),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("overflow", Map.of()).forGetter(VoyageLink::overflow)
    ).apply(i, VoyageLink::new));

    public VoyageLink {
        overflow = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(overflow));
    }

    public VoyageLink withPlundered(boolean value) {
        return new VoyageLink(voyage, value, overflow);
    }

    /** The user-data form. */
    public CompoundTag toTag() {
        Tag t = CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
        return t instanceof CompoundTag c ? c : new CompoundTag();
    }

    /** Reads the user-data form; empty for an empty tag (no link, or a link cleared after a capture or a sinking). */
    public static Optional<VoyageLink> fromTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return Optional.empty();
        return CODEC.parse(NbtOps.INSTANCE, tag).result();
    }
}

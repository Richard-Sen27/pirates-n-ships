package com.richardsenger.piratesnships.ship.hull;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Block tags that let pack makers override the built-in watertightness rules of {@code world.HullBlockClassifier}.
 * {@link #NOT_WATERTIGHT} ships empty; {@link #WATERTIGHT} holds only our hull patch.
 */
public final class HullTags {

    /** Always watertight (solid hull cell), whatever the shape. */
    public static final TagKey<Block> WATERTIGHT = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "watertight"));
    /** Never watertight (water passes). Wins over {@link #WATERTIGHT} and over door/trapdoor detection. */
    public static final TagKey<Block> NOT_WATERTIGHT = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "not_watertight"));

    private HullTags() {
    }
}

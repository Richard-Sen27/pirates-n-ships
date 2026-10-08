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
    /**
     * World blocks the client does not draw where they stand inside a dry hull (water plants and bubble columns, HV1,
     * docs/design.md §4.4); their water stays and Sable's mask hides it. Read by {@code hull.client.HiddenWaterPlants}.
     */
    public static final TagKey<Block> HIDDEN_IN_DRY_HULL = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "hidden_in_dry_hull"));

    private HullTags() {
    }
}

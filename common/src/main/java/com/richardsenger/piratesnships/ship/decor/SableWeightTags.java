package com.richardsenger.piratesnships.ship.decor;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Sable's built-in block physics tags (refs/sable/wiki/Block Physics Properties.md, defined in
 * refs/sable/common/src/main/resources/data/sable/tags/block/). Our datagen writes into them with
 * {@code replace: false}, so they merge with Sable's own files. Default mass and volume are 1.0.
 */
public final class SableWeightTags {

    /** mass 0.25 (Sable: wool, doors, fences, ladders, iron bars). */
    public static final TagKey<Block> SUPER_LIGHT = tag("super_light");
    /** mass 0.5 (Sable: planks, logs, barrels, chests). */
    public static final TagKey<Block> LIGHT = tag("light");
    /** mass 2.0 (Sable: stone, obsidian). */
    public static final TagKey<Block> HEAVY = tag("heavy");
    /** volume 0.25 for buoyancy (Sable: fences, doors, ladders, iron bars). */
    public static final TagKey<Block> QUARTER_VOLUME = tag("quarter_volume");

    private SableWeightTags() {
    }

    private static TagKey<Block> tag(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("sable", name));
    }
}

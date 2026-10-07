package com.richardsenger.piratesnships.crew.hammock;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Block tags of the hammock (HM1). */
public final class HammockTags {

    /**
     * Posts a hammock can be tied to besides any block with a full solid face toward it: fences, walls and logs
     * (datagen in {@code crew.content.CrewContentModule}).
     */
    public static final TagKey<Block> SUPPORTS = TagKey.create(Registries.BLOCK, Constants.id("hammock_supports"));

    private HammockTags() {
    }
}

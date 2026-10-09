package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Registry entries of the {@code ship.rigging} module (RL1): the ratlines block and item, and their anchor tag. */
public final class RiggingContent {

    /**
     * Blocks that carry a ratlines net on any side although their side faces are not sturdy: fences and walls (a mast of
     * fence posts), and (RL1b) the yard and the crow's nest, so a run passes the yard rows and leans on the nest; a net
     * that only a yard holds is still refused in a sail's cloth ({@link RatlinesBlock#refusedByCloth}). Every block with
     * a sturdy face carries one on that face anyway.
     */
    public static final TagKey<Block> RATLINES_ANCHORS = TagKey.create(Registries.BLOCK, Constants.id("ratlines_anchors"));

    public static final RegistryEntry<Block, RatlinesBlock> RATLINES = ModRegistry.block("ratlines",
            () -> new RatlinesBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.4f).sound(SoundType.WOOL)
                    .noOcclusion().pushReaction(PushReaction.DESTROY).ignitedByLava()));
    public static final RegistryEntry<Item, RatlinesItem> RATLINES_ITEM = ModRegistry.item("ratlines",
            () -> new RatlinesItem(RATLINES.get(), new Item.Properties()));

    private RiggingContent() {
    }

    public static void init() {
    }
}

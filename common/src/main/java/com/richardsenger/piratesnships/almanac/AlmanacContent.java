package com.richardsenger.piratesnships.almanac;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Registry entries of the {@code almanac} module (ALM1, docs/design.md §3.4). */
public final class AlmanacContent {

    /** The placed tome: lies on top of any block, opens while read. */
    public static final RegistryEntry<Block, AlmanacBlock> ALMANAC = ModRegistry.block("captains_almanac",
            () -> new AlmanacBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(0.5f)
                    .sound(SoundType.CHISELED_BOOKSHELF).noOcclusion().pushReaction(PushReaction.DESTROY).ignitedByLava()));
    /** The book item: read in hand, set down with a sneak-use on a block. */
    public static final RegistryEntry<Item, AlmanacItem> ALMANAC_ITEM = ModRegistry.item("captains_almanac",
            () -> new AlmanacItem(ALMANAC.get(), new Item.Properties().stacksTo(1)));
    /** The tome's open state (synced) and its readers (server only); the client animates the book from it. */
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<AlmanacBlockEntity>> ALMANAC_BLOCK_ENTITY =
            ModRegistry.blockEntity("captains_almanac", AlmanacBlockEntity::new, ALMANAC);

    private AlmanacContent() {
    }

    public static void init() {
    }
}

package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Registry entries of the {@code combat.boarding} module (BRD1): the boarding plank block, its item and block entity, and
 * (RAIL1) the tag of the openings a gangway is laid through. BRD4: the plank's shape comes from its block entity (the
 * straight board's height in the cell), so the block has a dynamic shape (no state-cached collision).
 */
public final class BoardingContent {

    public static final RegistryEntry<Block, BoardingPlankBlock> PLANK = ModRegistry.block("boarding_plank",
            () -> new BoardingPlankBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).sound(SoundType.WOOD)
                    .strength(1.0f).noOcclusion().dynamicShape().ignitedByLava().pushReaction(PushReaction.DESTROY)));
    public static final RegistryEntry<Item, BoardingPlankItem> PLANK_ITEM = ModRegistry.item("boarding_plank",
            () -> new BoardingPlankItem(PLANK.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<BoardingPlankBlockEntity>> PLANK_BLOCK_ENTITY =
            ModRegistry.blockEntity("boarding_plank", BoardingPlankBlockEntity::new, PLANK);

    /**
     * RAIL1: openings in a railing (the railing port) with a {@code facing} (their outboard side): a plank used on the
     * top of one, facing that way, lays the gangway from the block beyond it at the opening's own height, as a click on
     * its outboard side would ({@link PlankRun#throughOpening}).
     */
    public static final TagKey<Block> GANGWAY_OPENINGS = TagKey.create(Registries.BLOCK, Constants.id("gangway_openings"));

    private BoardingContent() {
    }

    public static void init() {
    }
}

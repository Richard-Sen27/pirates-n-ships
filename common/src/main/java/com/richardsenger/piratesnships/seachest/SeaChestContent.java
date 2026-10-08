package com.richardsenger.piratesnships.seachest;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * Registration of the sea chest (docs/design.md §11): the block with its 54-slot block entity, the wearable item and
 * the floating entity. Not flammable, so a fire on deck doesn't take the contents with it.
 */
public final class SeaChestContent {

    public static final RegistryEntry<Block, SeaChestBlock> BLOCK = ModRegistry.block("sea_chest",
            () -> new SeaChestBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS)
                    .strength(2.5f).sound(SoundType.WOOD)));

    public static final RegistryEntry<Item, SeaChestItem> ITEM = ModRegistry.item("sea_chest",
            () -> new SeaChestItem(BLOCK.get(), new Item.Properties().stacksTo(1)));

    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<SeaChestBlockEntity>> BLOCK_ENTITY =
            ModRegistry.blockEntity("sea_chest", SeaChestBlockEntity::new, BLOCK);

    /** Seats its holder on a floating sea chest and paddles it (SC2). */
    public static final RegistryEntry<Item, PaddleItem> PADDLE = ModRegistry.item("paddle",
            () -> new PaddleItem(new Item.Properties().stacksTo(1)));

    /** 0.875 wide like the block model's footprint when floating, 0.75 high. */
    public static final RegistryEntry<EntityType<?>, EntityType<SeaChestEntity>> ENTITY = ModRegistry.entity("sea_chest",
            () -> EntityType.Builder.<SeaChestEntity>of(SeaChestEntity::new, MobCategory.MISC).sized(0.875f, 0.75f)
                    .fireImmune().clientTrackingRange(10).updateInterval(3));

    private SeaChestContent() {
    }

    /** Loads the class so the entries are registered; exposes the block's 54 slots to other mods' pipes. */
    public static void init() {
        Services.CAPABILITIES.registerBlockContainer(BLOCK_ENTITY);
    }
}

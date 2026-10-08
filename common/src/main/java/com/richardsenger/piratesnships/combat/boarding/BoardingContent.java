package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Registry entries of the {@code combat.boarding} module (BRD1): the boarding plank block, its item and block entity. */
public final class BoardingContent {

    public static final RegistryEntry<Block, BoardingPlankBlock> PLANK = ModRegistry.block("boarding_plank",
            () -> new BoardingPlankBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).sound(SoundType.WOOD)
                    .strength(1.0f).noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY)));
    public static final RegistryEntry<Item, BoardingPlankItem> PLANK_ITEM = ModRegistry.item("boarding_plank",
            () -> new BoardingPlankItem(PLANK.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<BoardingPlankBlockEntity>> PLANK_BLOCK_ENTITY =
            ModRegistry.blockEntity("boarding_plank", BoardingPlankBlockEntity::new, PLANK);

    private BoardingContent() {
    }

    public static void init() {
    }
}

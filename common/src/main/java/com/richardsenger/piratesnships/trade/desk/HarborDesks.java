package com.richardsenger.piratesnships.trade.desk;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Registration of the harbor master's desk ({@code harbor_desk}) and its block entity. */
public final class HarborDesks {

    public static final RegistryEntry<Block, HarborDeskBlock> HARBOR_DESK = ModRegistry.blockWithItem("harbor_desk",
            () -> new HarborDeskBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD)
                    .noOcclusion().ignitedByLava()));

    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<HarborDeskBlockEntity>> BLOCK_ENTITY = ModRegistry.blockEntity(
            "harbor_desk", HarborDeskBlockEntity::new, HARBOR_DESK);

    private HarborDesks() {
    }

    /** Loads the class so the entries are registered. Called from {@code TradeModule.registerContent()}. */
    public static void init() {
    }
}

package com.richardsenger.piratesnships.ship.assembly;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Registry content of the {@code ship.assembly} module. */
public final class AssemblyContent {

    public static final RegistryEntry<Block, HelmBlock> HELM = ModRegistry.blockWithItem("helm",
            () -> new HelmBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noOcclusion()));

    private AssemblyContent() {
    }

    public static void init() {
    }
}

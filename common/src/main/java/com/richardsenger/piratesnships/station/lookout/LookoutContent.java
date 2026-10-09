package com.richardsenger.piratesnships.station.lookout;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/** Registered content of the lookout (CN1): the crow's nest. */
public final class LookoutContent {

    public static final RegistryEntry<Block, CrowsNestBlock> CROWS_NEST = ModRegistry.blockWithItem("crows_nest",
            () -> new CrowsNestBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS)
                    .strength(2.0f, 3.0f).sound(SoundType.WOOD).noOcclusion().isViewBlocking((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false).ignitedByLava()));

    private LookoutContent() {
    }

    public static void init() {
    }
}

package com.richardsenger.piratesnships.station.lookout;

import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/** Registered content of the lookout (CN1): the crow's nest; LKO2: the hail. */
public final class LookoutContent {

    /** The crow's nest in every wood (WOOD3); the spruce member keeps the id {@code crows_nest}. */
    public static final WoodFamily<CrowsNestBlock> CROWS_NESTS = WoodFamily.register("crows_nest", ShipWood.SPRUCE,
            wood -> new CrowsNestBlock(wood, WoodFamily.properties(wood).noOcclusion().isViewBlocking((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false)));
    /** The spruce crow's nest, legacy id {@code crows_nest}. */
    public static final RegistryEntry<Block, CrowsNestBlock> CROWS_NEST = CROWS_NESTS.entry(ShipWood.SPRUCE);

    /**
     * LKO2: the lookout's hail, played on the client from the nest with every call. The bell lookouts rang from the
     * masthead: vanilla's bell strikes, high and soft ({@code sounds.json} from {@link LookoutModule#gatherData}).
     */
    public static final RegistryEntry<SoundEvent, SoundEvent> HAIL = ModRegistry.sound("lookout.hail");

    private LookoutContent() {
    }

    public static void init() {
    }
}

package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.List;

/**
 * Decorative ship blocks: figureheads and nameplate (design.md §4.8), flagpole (§4.7), and the cargo crate and
 * cargo barrel (§4.9), which are plain solid blocks until the cargo package turns them into containers.
 */
public final class ShipDecor {

    public static final RegistryEntry<Block, FigureheadBlock> FIGUREHEAD_MERMAID = figurehead("figurehead_mermaid");
    public static final RegistryEntry<Block, FigureheadBlock> FIGUREHEAD_LION = figurehead("figurehead_lion");
    public static final RegistryEntry<Block, FigureheadBlock> FIGUREHEAD_EAGLE = figurehead("figurehead_eagle");
    public static final RegistryEntry<Block, FigureheadBlock> FIGUREHEAD_SKULL = figurehead("figurehead_skull");

    /** A 3-pixel plate on the side of a hull (see {@link NameplateBlock}). */
    public static final RegistryEntry<Block, NameplateBlock> NAMEPLATE = ModRegistry.blockWithItem("nameplate",
            () -> new NameplateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD)
                    .noOcclusion().pushReaction(PushReaction.DESTROY).ignitedByLava()));

    /** The ship's name on a nameplate (see {@link NameplateBlockEntity}). */
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<NameplateBlockEntity>> NAMEPLATE_BLOCK_ENTITY =
            ModRegistry.blockEntity("nameplate", NameplateBlockEntity::new, NAMEPLATE);

    public static final RegistryEntry<Block, FlagpoleBlock> FLAGPOLE = ModRegistry.blockWithItem("flagpole",
            () -> new FlagpoleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 3.0f).sound(SoundType.WOOD)
                    .noOcclusion().ignitedByLava()));

    public static final RegistryEntry<Block, Block> CARGO_CRATE = ModRegistry.blockWithItem("cargo_crate", () -> new com.richardsenger.piratesnships.trade.cargo.CargoContainerBlock(woodProps(), com.richardsenger.piratesnships.trade.cargo.CargoContainers.Kind.CRATE));
    public static final RegistryEntry<Block, Block> CARGO_BARREL = ModRegistry.blockWithItem("cargo_barrel", () -> new com.richardsenger.piratesnships.trade.cargo.CargoContainerBlock(woodProps(), com.richardsenger.piratesnships.trade.cargo.CargoContainers.Kind.BARREL));

    private ShipDecor() {
    }

    public static List<RegistryEntry<Block, FigureheadBlock>> figureheads() {
        return List.of(FIGUREHEAD_MERMAID, FIGUREHEAD_LION, FIGUREHEAD_EAGLE, FIGUREHEAD_SKULL);
    }

    private static RegistryEntry<Block, FigureheadBlock> figurehead(String name) {
        return ModRegistry.blockWithItem(name, () -> new FigureheadBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                .instrument(NoteBlockInstrument.BASS).strength(2.0f, 3.0f).sound(SoundType.WOOD).noOcclusion().ignitedByLava()));
    }

    /** Like a vanilla barrel. */
    private static BlockBehaviour.Properties woodProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS)
                .strength(2.5f).sound(SoundType.WOOD).ignitedByLava();
    }

    public static void init() {
    }
}

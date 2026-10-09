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
 * cargo barrel (§4.9), which are plain solid blocks until the cargo package turns them into containers; the ART2 decor
 * (§4.8): ship's lantern, ship's bell, rope coil, stern window, chart table and sea cot.
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

    public static final RegistryEntry<Block, Block> CARGO_CRATE = ModRegistry.blockWithItem("cargo_crate", () -> new com.richardsenger.piratesnships.trade.cargo.CargoContainerBlock(woodProps().noOcclusion(), com.richardsenger.piratesnships.trade.cargo.CargoContainers.Kind.CRATE));
    public static final RegistryEntry<Block, Block> CARGO_BARREL = ModRegistry.blockWithItem("cargo_barrel", () -> new com.richardsenger.piratesnships.trade.cargo.CargoContainerBlock(woodProps().noOcclusion(), com.richardsenger.piratesnships.trade.cargo.CargoContainers.Kind.BARREL));

    // ---------------------------------------------------------------- ART2: ship decor with hand-made models

    public static final RegistryEntry<Block, ShipLanternBlock> SHIP_LANTERN = ModRegistry.blockWithItem("ship_lantern",
            () -> new ShipLanternBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).forceSolidOn().strength(3.5f)
                    .sound(SoundType.LANTERN).lightLevel(s -> 14).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final RegistryEntry<Block, ShipsBellBlock> SHIPS_BELL = ModRegistry.blockWithItem("ships_bell",
            () -> new ShipsBellBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).forceSolidOn().strength(3.0f)
                    .sound(SoundType.ANVIL).noOcclusion().pushReaction(PushReaction.DESTROY)));
    /** BELL1: the bell's last ring and strike direction, which the client swings it by (see {@link ShipsBellBlockEntity}). */
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<ShipsBellBlockEntity>> SHIPS_BELL_BLOCK_ENTITY =
            ModRegistry.blockEntity("ships_bell", ShipsBellBlockEntity::new, SHIPS_BELL);
    public static final RegistryEntry<Block, RopeCoilBlock> ROPE_COIL = ModRegistry.blockWithItem("rope_coil",
            () -> new RopeCoilBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.8f).sound(SoundType.WOOL)
                    .noOcclusion().pushReaction(PushReaction.DESTROY).ignitedByLava()));
    public static final RegistryEntry<Block, SternWindowBlock> STERN_WINDOW = ModRegistry.blockWithItem("stern_window",
            () -> new SternWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD).noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)
                    .isRedstoneConductor((s, l, p) -> false).ignitedByLava()));
    public static final RegistryEntry<Block, ChartTableBlock> CHART_TABLE = ModRegistry.blockWithItem("chart_table",
            () -> new ChartTableBlock(woodProps().noOcclusion()));
    public static final RegistryEntry<Block, SeaCotBlock> SEA_COT = ModRegistry.blockWithItem("sea_cot",
            () -> new SeaCotBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(0.6f).sound(SoundType.WOOD)
                    .noOcclusion().pushReaction(PushReaction.DESTROY).ignitedByLava()));

    private ShipDecor() {
    }

    /** The six ART2 decor blocks. */
    public static List<Block> furnishings() {
        return List.of(SHIP_LANTERN.get(), SHIPS_BELL.get(), ROPE_COIL.get(), STERN_WINDOW.get(), CHART_TABLE.get(), SEA_COT.get());
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

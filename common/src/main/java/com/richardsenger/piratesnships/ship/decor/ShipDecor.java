package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.List;

/**
 * Decorative ship blocks: figureheads and the name board (design.md §4.8, NAME2), flagpole (§4.7), and the cargo crate and
 * cargo barrel (§4.9), which are plain solid blocks until the cargo package turns them into containers; the ART2 decor
 * (§4.8): ship's lantern, ship's bell, rope coil, stern window, chart table and sea cot.
 */
public final class ShipDecor {

    public static final RegistryEntry<Block, FigureheadBlock> FIGUREHEAD_MERMAID = figurehead("figurehead_mermaid");
    public static final RegistryEntry<Block, FigureheadBlock> FIGUREHEAD_LION = figurehead("figurehead_lion");
    public static final RegistryEntry<Block, FigureheadBlock> FIGUREHEAD_EAGLE = figurehead("figurehead_eagle");
    public static final RegistryEntry<Block, FigureheadBlock> FIGUREHEAD_SKULL = figurehead("figurehead_skull");

    /**
     * The name board (NAME2, see {@link NameBoardBlock}): a 2 px plate fixed flat on a hull's side, joining its
     * neighbours on the same face into one board; a wood family (WOOD3) whose oak member keeps the G8 nameplate's
     * registry id {@code nameplate} (block, item and block entity), so plates in saved worlds and ship templates load
     * as oak boards; the other woods are {@code <wood>_nameplate}.
     */
    public static final WoodFamily<NameBoardBlock> NAMEPLATES = WoodFamily.register("nameplate", ShipWood.OAK,
            wood -> new NameBoardBlock(wood, WoodFamily.properties(wood).strength(1.0f).noOcclusion().pushReaction(PushReaction.DESTROY)));
    /** The oak name board, legacy id {@code nameplate}. */
    public static final RegistryEntry<Block, NameBoardBlock> NAMEPLATE = NAMEPLATES.entry(ShipWood.OAK);

    /** The ship's name and the row on a name board of any wood (see {@link NameplateBlockEntity}). */
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<NameplateBlockEntity>> NAMEPLATE_BLOCK_ENTITY =
            NAMEPLATES.blockEntity("nameplate", NameplateBlockEntity::new);

    /** The flagpole in every wood (WOOD3); the spruce member keeps the id {@code flagpole}. */
    public static final WoodFamily<FlagpoleBlock> FLAGPOLES = WoodFamily.register("flagpole", ShipWood.SPRUCE,
            wood -> new FlagpoleBlock(wood, WoodFamily.properties(wood).noOcclusion()));
    /** The spruce flagpole, legacy id {@code flagpole}. */
    public static final RegistryEntry<Block, FlagpoleBlock> FLAGPOLE = FLAGPOLES.entry(ShipWood.SPRUCE);

    /**
     * FLG3: a plinth carved from a log that a flagpole stands in (see {@link FlagPedestalBlock}); in every wood (WOOD3),
     * the spruce member keeps the id {@code flag_pedestal}.
     */
    public static final WoodFamily<FlagPedestalBlock> FLAG_PEDESTALS = WoodFamily.register("flag_pedestal", ShipWood.SPRUCE,
            wood -> new FlagPedestalBlock(wood, WoodFamily.properties(wood).noOcclusion().pushReaction(PushReaction.DESTROY)));
    /** The spruce flag pedestal, legacy id {@code flag_pedestal}. */
    public static final RegistryEntry<Block, FlagPedestalBlock> FLAG_PEDESTAL = FLAG_PEDESTALS.entry(ShipWood.SPRUCE);

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
    // WOOD3: the stern window, chart table and sea cot in every wood; the dark oak members keep the legacy ids
    public static final WoodFamily<SternWindowBlock> STERN_WINDOWS = WoodFamily.register("stern_window", ShipWood.DARK_OAK,
            wood -> new SternWindowBlock(wood, WoodFamily.properties(wood).noOcclusion().isViewBlocking((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false)));
    /** The dark oak stern window, legacy id {@code stern_window}. */
    public static final RegistryEntry<Block, SternWindowBlock> STERN_WINDOW = STERN_WINDOWS.entry(ShipWood.DARK_OAK);
    /** Like a vanilla barrel (strength 2.5). */
    public static final WoodFamily<ChartTableBlock> CHART_TABLES = WoodFamily.register("chart_table", ShipWood.DARK_OAK,
            wood -> new ChartTableBlock(wood, WoodFamily.properties(wood).strength(2.5f).noOcclusion()));
    /** The dark oak chart table, legacy id {@code chart_table}. */
    public static final RegistryEntry<Block, ChartTableBlock> CHART_TABLE = CHART_TABLES.entry(ShipWood.DARK_OAK);
    public static final WoodFamily<SeaCotBlock> SEA_COTS = WoodFamily.register("sea_cot", ShipWood.DARK_OAK,
            wood -> new SeaCotBlock(wood, WoodFamily.properties(wood).strength(0.6f).noOcclusion().pushReaction(PushReaction.DESTROY)));
    /** The dark oak sea cot, legacy id {@code sea_cot}. */
    public static final RegistryEntry<Block, SeaCotBlock> SEA_COT = SEA_COTS.entry(ShipWood.DARK_OAK);

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

package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.chart.data.MapTileDrawing;
import com.richardsenger.piratesnships.chart.tile.MapTileBlock;
import com.richardsenger.piratesnships.chart.tile.MapTileBlockEntity;
import com.richardsenger.piratesnships.chart.tile.MapTileItem;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The chart item (work package MAP1): a rolled parchment with a wax seal that opens the player's own chart. The map
 * tile (work package MAP2): a block that shows a part of a chart for everyone, its block entity, its item and the
 * item component that carries a drawing.
 */
public final class ChartContent {

    public static final RegistryEntry<Item, ChartItem> CHART = ModRegistry.item("chart", () -> new ChartItem(new Item.Properties().stacksTo(1)));

    public static final RegistryEntry<Block, MapTileBlock> MAP_TILE = ModRegistry.block("map_tile",
            () -> new MapTileBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.5f).sound(SoundType.WOOD)
                    .noOcclusion().pushReaction(PushReaction.DESTROY)));

    public static final RegistryEntry<Item, MapTileItem> MAP_TILE_ITEM = ModRegistry.item("map_tile",
            () -> new MapTileItem(MAP_TILE.get(), new Item.Properties()));

    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<MapTileBlockEntity>> MAP_TILE_BLOCK_ENTITY =
            ModRegistry.blockEntity("map_tile", MapTileBlockEntity::new, MAP_TILE);

    public static final RegistryEntry<net.minecraft.core.component.DataComponentType<?>, DataComponentType<MapTileDrawing>> MAP_TILE_DRAWING =
            ModRegistry.dataComponent("map_tile_drawing", b -> b.persistent(MapTileDrawing.CODEC).networkSynchronized(MapTileDrawing.STREAM_CODEC));

    /** Items that pay for drawing on a map board (work package MAP3): ink sacs, glow ink sacs and the kraken's ink. */
    public static final TagKey<Item> CHART_INK = TagKey.create(Registries.ITEM, Constants.id("chart_ink"));
    /** The kraken's ink (module {@code mob}), worth {@code chart.tiles.kraken_ink_tile_value} tiles; named by id, not imported. */
    public static final ResourceLocation KRAKEN_INK = Constants.id("kraken_ink");

    private ChartContent() {
    }

    public static void init() {
    }
}

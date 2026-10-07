package com.richardsenger.piratesnships.chart.tile;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.chart.ChartContent;
import com.richardsenger.piratesnships.chart.data.MapTileDrawing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The drawing on a map tile (work package MAP2), {@code null} while the tile is blank. Saved with the world under
 * {@code drawing} ({@link MapTileDrawing#CODEC}, pixels deflated) and synced to every client that has the
 * chunk through the block entity's update tag (the chunk data when a player comes into range, a block entity data
 * packet when the drawing changes). The tile item carries the drawing as the {@code pirates_n_ships:map_tile_drawing}
 * component: placing the item applies it, the loot table copies it back when the tile is broken.
 */
public class MapTileBlockEntity extends BlockEntity {

    public static final String TAG_DRAWING = "drawing";

    private @Nullable MapTileDrawing drawing;

    public MapTileBlockEntity(BlockPos pos, BlockState state) {
        super(ChartContent.MAP_TILE_BLOCK_ENTITY.get(), pos, state);
    }

    public @Nullable MapTileDrawing drawing() {
        return drawing;
    }

    /** Replaces the drawing (null = blank), saves and sends it to the clients. */
    public void setDrawing(@Nullable MapTileDrawing next) {
        drawing = next;
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (drawing != null) {
            MapTileDrawing.CODEC.encodeStart(NbtOps.INSTANCE, drawing).resultOrPartial(e -> Constants.LOG.warn("Map tile at {}: {}", worldPosition, e))
                    .ifPresent(t -> tag.put(TAG_DRAWING, t));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        drawing = null;
        if (tag.contains(TAG_DRAWING)) {
            drawing = MapTileDrawing.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_DRAWING))
                    .resultOrPartial(e -> Constants.LOG.warn("Map tile at {}: unreadable drawing ({}), left blank", worldPosition, e))
                    .orElse(null);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        drawing = input.get(ChartContent.MAP_TILE_DRAWING.get());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (drawing != null) components.set(ChartContent.MAP_TILE_DRAWING.get(), drawing);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(TAG_DRAWING);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // saveWithoutMetadata leaves the key out for a blank tile, and loadAdditional reads that as blank
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

package com.richardsenger.piratesnships.trade.desk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/** The port a harbor master's desk belongs to (empty = unbound). Server data only; the client never needs it. */
public class HarborDeskBlockEntity extends BlockEntity {

    private ResourceLocation port;

    public HarborDeskBlockEntity(BlockPos pos, BlockState state) {
        super(HarborDesks.BLOCK_ENTITY.get(), pos, state);
    }

    public Optional<ResourceLocation> port() {
        return Optional.ofNullable(port);
    }

    public void setPort(Optional<ResourceLocation> port) {
        ResourceLocation next = port.orElse(null);
        if (java.util.Objects.equals(this.port, next)) return;
        this.port = next;
        setChanged();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        port = tag.contains("port") ? ResourceLocation.tryParse(tag.getString("port")) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (port != null) tag.putString("port", port.toString());
    }
}

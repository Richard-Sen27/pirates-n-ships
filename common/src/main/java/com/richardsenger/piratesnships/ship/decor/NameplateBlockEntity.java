package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The text on a nameplate (design.md §4.8: the ship's name "shown on a nameplate block on the hull"). Server side the
 * plate looks up the ship it is on ({@link SableShips#containing(BlockEntity)}) and that ship's name in
 * {@link ShipRegistry}, on its first tick and then every {@link ShipIdentityConfig#NAMEPLATE_REFRESH_TICKS} ticks, so
 * naming, renaming, placing a plate on a named ship and disassembly all reach it without a hook in the assembly code.
 * Off a ship (never assembled, or after disassembly) or with {@link ShipIdentityConfig#NAMEPLATE_SHOWS_NAME} off the
 * plate shows nothing. The text is saved and synced through the update tag; the client draws it
 * ({@code ship.decor.client.NameplateRenderer}).
 */
public class NameplateBlockEntity extends BlockEntity {

    private static final String TAG_TEXT = "text";

    private String text = "";
    /** Look the name up on the next server tick (after load, placement or a move between world and plot). */
    private boolean refreshSoon = true;

    public NameplateBlockEntity(BlockPos pos, BlockState state) {
        super(ShipDecor.NAMEPLATE_BLOCK_ENTITY.get(), pos, state);
    }

    /** The name shown on the board, empty for none. */
    public String text() {
        return text;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel)) return;
        int interval = ShipIdentityConfig.NAMEPLATE_REFRESH_TICKS.get();
        if (refreshSoon || Math.floorMod(level.getGameTime() + worldPosition.hashCode(), interval) == 0) refresh();
    }

    /** Reads the name of the ship this plate is on now and shows it (server side). */
    public void refresh() {
        if (!(level instanceof ServerLevel server)) return;
        refreshSoon = false;
        ShipBody ship = SableShips.containing(this);
        String name = ship == null ? "" : ShipRegistry.get(server.getServer()).find(ship.id()).map(ShipData::name).orElse("");
        setText(NameplateText.shown(ShipIdentityConfig.NAMEPLATE_SHOWS_NAME.get(), ship != null, name));
    }

    private void setText(String next) {
        if (next.equals(text)) return;
        text = next;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!text.isEmpty()) tag.putString(TAG_TEXT, text);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        text = NameplateText.clean(tag.getString(TAG_TEXT));
        refreshSoon = true;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // Always write the key, so an emptied plate clears on the client too
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.putString(TAG_TEXT, text);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

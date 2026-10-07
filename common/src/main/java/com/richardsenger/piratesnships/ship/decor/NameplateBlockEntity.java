package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
 * plate shows nothing. On an unnamed wreck piece of a split ship (RS1, {@link ShipSplits#lineage}) it shows
 * "Wreck of &lt;name&gt;" ({@link NameplateText#KEY_WRECK_OF}, translated on the client). The text is saved and synced
 * through the update tag; the client draws it ({@code ship.decor.client.NameplateRenderer}).
 */
public class NameplateBlockEntity extends BlockEntity {

    private static final String TAG_TEXT = "text";
    private static final String TAG_WRECK_OF = "wreck_of";

    private String text = "";
    /** The text is the name of the ship this plate's wreck piece broke off from. */
    private boolean wreckOf;
    /** Look the name up on the next server tick (after load, placement or a move between world and plot). */
    private boolean refreshSoon = true;

    public NameplateBlockEntity(BlockPos pos, BlockState state) {
        super(ShipDecor.NAMEPLATE_BLOCK_ENTITY.get(), pos, state);
    }

    /** The name on the board, empty for none (on a wreck piece: the name the ship had, see {@link #wreckOf()}). */
    public String text() {
        return text;
    }

    /** True when the board reads "Wreck of" {@link #text()}. */
    public boolean wreckOf() {
        return wreckOf;
    }

    /** The line as drawn: the name, or {@link NameplateText#KEY_WRECK_OF} with it. */
    public Component display() {
        return wreckOf ? Component.translatable(NameplateText.KEY_WRECK_OF, text) : Component.literal(text);
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
        ShipSplits.Lineage line = ship == null ? null : ShipSplits.lineage(ship);
        setText(NameplateText.shown(ShipIdentityConfig.NAMEPLATE_SHOWS_NAME.get(), ship != null, name,
                line != null && line.wreck(), line == null ? "" : line.wreckOf()));
    }

    private void setText(NameplateText.Shown next) {
        if (next.name().equals(text) && next.wreckOf() == wreckOf) return;
        text = next.name();
        wreckOf = next.wreckOf();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!text.isEmpty()) tag.putString(TAG_TEXT, text);
        if (wreckOf) tag.putBoolean(TAG_WRECK_OF, true);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        text = NameplateText.clean(tag.getString(TAG_TEXT));
        wreckOf = tag.getBoolean(TAG_WRECK_OF);
        refreshSoon = true;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // Always write the keys, so an emptied plate clears on the client too
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.putString(TAG_TEXT, text);
        tag.putBoolean(TAG_WRECK_OF, wreckOf);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

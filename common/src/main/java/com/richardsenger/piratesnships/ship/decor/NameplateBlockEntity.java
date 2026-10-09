package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * A name board's block entity (NAME2; the class keeps its G8 name, the block is {@link NameBoardBlock} under the
 * nameplate's registry id): the ship's name and the board's place in its row.
 *
 * <p><b>The name</b> (design.md §4.8: the ship's name "shown on a nameplate block on the hull"). Server side the board
 * looks up the ship it is on ({@link SableShips#containing(BlockEntity)}) and that ship's name in {@link ShipRegistry},
 * on its first tick and then every {@link ShipIdentityConfig#NAMEPLATE_REFRESH_TICKS} ticks, so naming, renaming at the
 * helm (titles included, they are part of the stored name), placing a board on a named ship and disassembly all reach
 * it without a hook in the assembly code. Off a ship (never assembled, or after disassembly) or with
 * {@link ShipIdentityConfig#NAMEPLATE_SHOWS_NAME} off the board shows nothing. On an unnamed wreck piece of a split ship
 * (RS1, {@link ShipSplits#lineage}) it shows "Wreck of &lt;name&gt;" ({@link NameplateText#KEY_WRECK_OF}, translated on
 * the client). Every board of a row holds the name; only the master draws it.
 *
 * <p><b>The row</b> ({@link NameBoardRow}): {@link #rowIndex()} counts from the row's leftmost board, the master
 * (index 0), whose {@link #rowLength()} is the row's length; the master of any board is {@link #rowIndex()} steps to
 * its left ({@link #masterPos()}). Re-linked for the whole row a tick after a board of it is placed, removed or
 * changes its neighbours ({@link #relinkRow()}, scheduled by the block), and checked for this board on every name
 * refresh, so a row that moved between world and plot (assembly, a turned disassembly: the index counts from the
 * viewer's left and survives turning) or loaded from an old save is right within a refresh.
 *
 * <p>Text and row are saved and synced through the update tag; the client draws them
 * ({@code ship.decor.client.NameplateRenderer}).
 */
public class NameplateBlockEntity extends BlockEntity {

    private static final String TAG_TEXT = "text";
    private static final String TAG_WRECK_OF = "wreck_of";
    private static final String TAG_ROW_INDEX = "row_index";
    private static final String TAG_ROW_LENGTH = "row_length";

    private String text = "";
    /** The text is the name of the ship this board's wreck piece broke off from. */
    private boolean wreckOf;
    /** Look the name up on the next server tick (after load, placement or a move between world and plot). */
    private boolean refreshSoon = true;
    /** Steps to the row's master on the viewer's left; 0 for the master itself. */
    private int rowIndex;
    /** The row's length in boards (meaningful on the master). */
    private int rowLength = 1;

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

    /** This board's index in its row, counted from the master (0) on the viewer's left. */
    public int rowIndex() {
        return rowIndex;
    }

    /** The row's length in boards, as the master knows it (1 for a board on its own). */
    public int rowLength() {
        return rowLength;
    }

    public boolean isMaster() {
        return rowIndex == 0;
    }

    /** The position of this board's row master: {@link #rowIndex()} steps to the viewer's left. */
    public BlockPos masterPos() {
        BlockState state = getBlockState();
        if (rowIndex == 0 || !state.hasProperty(NameBoardBlock.FACING)) return worldPosition;
        return worldPosition.relative(NameBoardRow.left(state.getValue(NameBoardBlock.FACING)), rowIndex);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel)) return;
        int interval = ShipIdentityConfig.NAMEPLATE_REFRESH_TICKS.get();
        if (refreshSoon || Math.floorMod(level.getGameTime() + worldPosition.hashCode(), interval) == 0) refresh();
    }

    /** Reads the name of the ship this board is on now and shows it, and checks the board's place in its row (server side). */
    public void refresh() {
        if (!(level instanceof ServerLevel server)) return;
        refreshSoon = false;
        ShipBody ship = SableShips.containing(this);
        String name = ship == null ? "" : ShipRegistry.get(server.getServer()).find(ship.id()).map(ShipData::name).orElse("");
        ShipSplits.Lineage line = ship == null ? null : ShipSplits.lineage(ship);
        setText(NameplateText.shown(ShipIdentityConfig.NAMEPLATE_SHOWS_NAME.get(), ship != null, name,
                line != null && line.wreck(), line == null ? "" : line.wreckOf()));
        NameBoardRow.Place place = place();
        if (place != null) apply(place);
    }

    /**
     * Re-links every board of this board's row (server side): each one's index, the master's length and the joins in
     * the block states.
     */
    public void relinkRow() {
        if (!(level instanceof ServerLevel) || !(getBlockState().getBlock() instanceof NameBoardBlock block)) return;
        NameBoardRow.Place place = place();
        if (place == null) return;
        Direction right = NameBoardRow.right(getBlockState().getValue(NameBoardBlock.FACING));
        BlockPos master = new BlockPos(place.masterX(), place.masterY(), place.masterZ());
        NameBoardRow.Lookup lookup = block.lookup(level);
        for (int i = 0; i < place.length(); i++) {
            BlockPos p = master.relative(right, i);
            if (level.getBlockEntity(p) instanceof NameplateBlockEntity be) {
                be.apply(NameBoardRow.find(lookup, p.getX(), p.getY(), p.getZ()));
            }
        }
    }

    /** This board's place in its row now, or {@code null} when the block is not a name board. */
    private @Nullable NameBoardRow.Place place() {
        if (level == null || !(getBlockState().getBlock() instanceof NameBoardBlock block)) return null;
        return NameBoardRow.find(block.lookup(level), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
    }

    /** Stores {@code place} (syncing a change) and puts the block state's joins in step with it. */
    private void apply(NameBoardRow.Place place) {
        int length = place.master() ? place.length() : 1;
        if (place.index() != rowIndex || length != rowLength) {
            rowIndex = place.index();
            rowLength = length;
            changed();
        }
        BlockState state = getBlockState();
        if (level != null && state.hasProperty(NameBoardBlock.LEFT)
                && (state.getValue(NameBoardBlock.LEFT) != place.joinedLeft() || state.getValue(NameBoardBlock.RIGHT) != place.joinedRight())) {
            level.setBlock(worldPosition, state.setValue(NameBoardBlock.LEFT, place.joinedLeft())
                    .setValue(NameBoardBlock.RIGHT, place.joinedRight()), Block.UPDATE_CLIENTS);
        }
    }

    private void setText(NameplateText.Shown next) {
        if (next.name().equals(text) && next.wreckOf() == wreckOf) return;
        text = next.name();
        wreckOf = next.wreckOf();
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!text.isEmpty()) tag.putString(TAG_TEXT, text);
        if (wreckOf) tag.putBoolean(TAG_WRECK_OF, true);
        tag.putInt(TAG_ROW_INDEX, rowIndex);
        tag.putInt(TAG_ROW_LENGTH, rowLength);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        text = NameplateText.clean(tag.getString(TAG_TEXT));
        wreckOf = tag.getBoolean(TAG_WRECK_OF);
        // a G8 nameplate has no row yet: a board on its own until the first refresh
        rowIndex = Math.max(0, tag.getInt(TAG_ROW_INDEX));
        rowLength = tag.contains(TAG_ROW_LENGTH) ? Math.max(1, tag.getInt(TAG_ROW_LENGTH)) : 1;
        refreshSoon = true;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // Always write the keys, so an emptied board clears on the client too
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

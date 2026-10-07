package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.sailing.wind.WindService;
import com.richardsenger.piratesnships.ship.decor.FlagpoleBlock;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Holds a flagpole's {@link FlagpoleState} (a block entity because a custom banner flag keeps its patterns as item
 * data). Thin: the rules are in {@link FlagpoleMachine}; this class feeds it game time and config, applies the
 * outcome (block state, items, feedback, {@link FlagpoleEvents}) and saves and syncs the state.
 * <p>
 * <b>Wind (FL1).</b> The cloth points exactly downwind, drawn by {@code client/FlagClothRenderer} at a continuous
 * yaw. The server samples the wind every {@code flags.wind_update_interval_ticks} on land and every
 * {@code flags.ship_update_interval_ticks} on a ship and stores, saves and syncs two angles: {@link #windBearing()},
 * the world bearing the wind blows toward, and {@link #yaw()}, the exact downwind angle in the frame the cloth is
 * drawn in (the plot frame on a ship, {@link FlagWind#downwindAngle}). The client draws the plot angle derived from
 * the synced wind bearing and the ship's render orientation each frame (as the sails do with the synced wind), and
 * falls back to {@link #yaw()} on land or while the flag does not follow the wind.
 * <p>
 * {@link Clearable} so that ship assembly (which saves the block entity, then clears the old one before removing the
 * block, docs/sable-notes.md §4) moves the flag instead of dropping a copy.
 */
public class FlagpoleBlockEntity extends BlockEntity implements Clearable {

    private static final String TAG = "flagpole";
    private static final String YAW_TAG = "yaw";
    private static final String WIND_TAG = "wind_bearing";
    /** Smaller wind or yaw changes (degrees) are not synced. */
    static final float SYNC_THRESHOLD_DEGREES = 0.25f;

    private FlagpoleState state = FlagpoleState.EMPTY;
    /** {@link FlagTint#clothTint(FlagpoleState)} of {@link #state}, cached for the render thread. */
    private volatile int clothTint = FlagTint.NONE;
    /**
     * The bearing the cloth points to in the frame it is drawn in ({@link FlagWind}; 0 = north): the exact downwind
     * angle in the plot frame on a ship, in the world on land. Saved and synced.
     */
    private volatile float yaw;
    /**
     * The world bearing the wind at the pole blows toward, as last sampled, or NaN while the flag does not follow the
     * wind ({@code flags.follow_wind} off, or no wind sampled yet). Saved and synced.
     */
    private volatile float windBearing = Float.NaN;
    /** Client only, for the renderer: the yaw drawn last frame (NaN: nothing drawn yet) and when (game time + partial). */
    public float shownYaw = Float.NaN;
    public double shownTime = Double.NaN;
    /**
     * Players who used the pole and may still get items or feedback (the actor of a pending action), so these reach
     * them even if they are not in the level's player list (GameTest mock players). Not saved; after a reload the
     * level's player list is used.
     */
    private final Map<UUID, Player> actors = new HashMap<>();

    public FlagpoleBlockEntity(BlockPos pos, BlockState blockState) {
        super(Flags.FLAGPOLE_BLOCK_ENTITY.get(), pos, blockState);
        // Poles saved before FL1 only had the four-way FACING: start from it until the wind is checked.
        yaw = blockState.hasProperty(FlagpoleBlock.FACING) ? facingYaw(blockState.getValue(FlagpoleBlock.FACING)) : 0f;
    }

    /** The compass bearing of a horizontal facing (north 0, east 90, south 180, west 270). */
    static float facingYaw(Direction facing) {
        return FlagYaw.wrap(facing.toYRot() + 180f);
    }

    public FlagpoleState state() {
        return state;
    }

    /**
     * RGB the cloth texture is multiplied with: the banner's base colour for a custom flag, white otherwise
     * ({@link FlagTint}). Read by the client's cloth renderer every frame.
     */
    public int clothTint() {
        return clothTint;
    }

    /** The cloth's yaw in the frame it is drawn in (the plot frame on a ship), as the server last set it. */
    public float yaw() {
        return yaw;
    }

    /** The world bearing the wind blows toward at the pole, or NaN while the flag does not follow the wind. */
    public float windBearing() {
        return windBearing;
    }

    public FlagReading reading() {
        return state.reading();
    }

    /** A player used the pole (server side): a flag item hoists it, an empty hand strikes/raises, sneaking takes it down. */
    public void interact(Player player, FlagpoleMachine.Input input) {
        if (!(level instanceof ServerLevel)) return;
        actors.put(player.getUUID(), player);
        apply(FlagpoleMachine.use(state, input, player.getUUID(), level.getGameTime(), FlagConfig.HOIST_DELAY_TICKS.get()));
    }

    /** Completes a pending action when its time has come, and turns a flying flag downwind now and then. */
    public void serverTick() {
        if (!(level instanceof ServerLevel)) return;
        long now = level.getGameTime();
        if (state.pending().isPresent()) apply(FlagpoleMachine.tick(state, now));
        if (!FlagConfig.FOLLOW_WIND.get()) {
            // The cloth keeps its yaw in its own frame; stop the client from turning it with the wind.
            if (!Float.isNaN(windBearing)) {
                windBearing = Float.NaN;
                sync();
            }
            return;
        }
        long phase = now + worldPosition.hashCode();
        int interval = FlagConfig.WIND_UPDATE_INTERVAL_TICKS.get();
        boolean landDue = interval > 0 && Math.floorMod(phase, interval) == 0;
        int shipInterval = FlagConfig.SHIP_UPDATE_INTERVAL_TICKS.get();
        boolean shipDue = shipInterval > 0 && Math.floorMod(phase, shipInterval) == 0;
        // On a ship the yaw is in plot coordinates and the ship turns under the flag, so it re-checks more often there.
        if (landDue || shipDue) updateWind(landDue, shipDue);
    }

    /** The pole was removed: drop the flag and a pending flag at its position. */
    public void dropContents() {
        if (!(level instanceof ServerLevel)) return;
        apply(FlagpoleMachine.breakPole(state), false);
    }

    /** Operator command: set the flag at once (old flag items drop at the pole). */
    public void commandSet(FlagKind kind, boolean struck, @Nullable UUID actor) {
        apply(FlagpoleMachine.set(state, kind, Flags.stackFor(kind), struck, actor));
    }

    /** Operator command: strike or raise at once. */
    public void commandStrike(boolean struck) {
        apply(FlagpoleMachine.setStruck(state, struck));
    }

    @Override
    public void clearContent() {
        state = FlagpoleState.EMPTY;
        clothTint = FlagTint.NONE;
        setChanged();
    }

    private void apply(FlagpoleMachine.Outcome outcome) {
        apply(outcome, true);
    }

    private void apply(FlagpoleMachine.Outcome outcome, boolean updateBlock) {
        if (!(level instanceof ServerLevel server)) return;
        FlagReading before = state.reading();
        boolean changed = !outcome.state().equals(state);
        state = outcome.state();
        clothTint = FlagTint.clothTint(state);
        if (changed) setChanged();
        for (FlagpoleMachine.Delivery d : outcome.deliveries()) deliver(server, d);
        if (outcome.feedback() != FlagpoleMachine.Feedback.NONE && outcome.feedbackTo() != null) {
            Player p = player(server, outcome.feedbackTo());
            if (p != null) p.displayClientMessage(feedback(outcome.feedback(), outcome), true);
        }
        if (updateBlock && changed) updateBlockState(before.shown() != state.reading().shown());
        Optional<UUID> keep = state.pending().map(FlagpoleState.Pending::actor);
        actors.keySet().removeIf(id -> keep.isEmpty() || !keep.get().equals(id));
        outcome.cause().ifPresent(c -> FlagpoleEvents.fire(new FlagpoleEvents.FlagChange(server, worldPosition, before,
                state.reading(), c, outcome.feedbackTo())));
    }

    private Component feedback(FlagpoleMachine.Feedback feedback, FlagpoleMachine.Outcome outcome) {
        FlagKind kind = outcome.state().pending().map(FlagpoleState.Pending::kind).filter(k -> k != FlagKind.NONE)
                .orElse(outcome.state().hasFlag() ? outcome.state().kind() : FlagKind.NONE);
        return Component.translatable(feedbackKey(feedback), Component.translatable(kindKey(kind)));
    }

    public static String feedbackKey(FlagpoleMachine.Feedback feedback) {
        return "message." + Constants.MOD_ID + ".flag." + feedback.id();
    }

    public static String kindKey(FlagKind kind) {
        return "flag." + Constants.MOD_ID + "." + kind.getSerializedName();
    }

    /** Shown flag into the block state; a newly shown flag turns downwind at once. */
    private void updateBlockState(boolean shownChanged) {
        BlockState bs = getBlockState();
        if (!(bs.getBlock() instanceof FlagpoleBlock)) return;
        if (shownChanged && state.reading().shown() != FlagKind.NONE && FlagConfig.FOLLOW_WIND.get()) {
            refreshWind(SableShips.containing(this), false);
        }
        BlockState next = bs.setValue(FlagpoleBlock.FLAG, state.reading().shown());
        // Either way the block entity data (state, yaw) reaches the clients with the block update.
        if (next != bs) level.setBlock(worldPosition, next, 3);
        else level.sendBlockUpdated(worldPosition, bs, bs, 3);
    }

    private void updateWind(boolean landDue, boolean shipDue) {
        if (state.reading().shown() == FlagKind.NONE) return;
        ShipBody ship = SableShips.containing(this);
        if (!(ship == null ? landDue : shipDue)) return;
        refreshWind(ship, true);
    }

    /**
     * Samples the wind and sets {@link #windBearing} and {@link #yaw}. On land: the world wind at the pole. On a ship:
     * the wind sampled at the pole's world position, turned into the ship's plot frame by its orientation, since the
     * cloth is drawn in the plot (docs/sable-notes.md §2). A calm keeps the last angles. Syncs (when {@code sync})
     * only if either angle moved by more than {@link #SYNC_THRESHOLD_DEGREES}.
     */
    private void refreshWind(@Nullable ShipBody ship, boolean sync) {
        if (!(level instanceof ServerLevel server)) return;
        Vec3 at = Vec3.atCenterOf(worldPosition);
        WindSample wind = WindService.sample(server, ship == null ? at : ship.toWorld(at));
        double vx = wind.dirX() * wind.strength();
        double vz = wind.dirZ() * wind.strength();
        float bearing = FlagWind.bearing(vx, vz, windBearing);
        float plot = FlagWind.downwindAngle(vx, vz, ship == null ? null : ship.orientation(), yaw);
        if (!moved(windBearing, bearing) && !moved(yaw, plot)) return;
        windBearing = bearing;
        yaw = plot;
        if (sync) sync();
        else setChanged();
    }

    private static boolean moved(float before, float after) {
        if (Float.isNaN(before) || Float.isNaN(after)) return Float.isNaN(before) != Float.isNaN(after);
        return Math.abs(FlagYaw.delta(before, after)) > SYNC_THRESHOLD_DEGREES;
    }

    /** Saves and sends the block entity data to the clients tracking the pole (no block state change, no re-mesh). */
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private void deliver(ServerLevel server, FlagpoleMachine.Delivery d) {
        ItemStack stack = d.stack().copy();
        if (stack.isEmpty()) return;
        Player p = d.recipient() == null ? null : player(server, d.recipient());
        if (p != null && !p.isRemoved()) {
            p.getInventory().placeItemBackInInventory(stack);
        } else {
            Containers.dropItemStack(server, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
        }
    }

    private @Nullable Player player(ServerLevel server, UUID id) {
        Player known = actors.get(id);
        if (known != null && !known.isRemoved()) return known;
        return server.getPlayerByUUID(id);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        FlagpoleState.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), state)
                .resultOrPartial(e -> Constants.LOG.error("Could not save flagpole at {}: {}", worldPosition, e))
                .ifPresent(t -> tag.put(TAG, t));
        tag.putFloat(YAW_TAG, yaw);
        if (!Float.isNaN(windBearing)) tag.putFloat(WIND_TAG, windBearing);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Optional<Tag> data = Optional.ofNullable(tag.get(TAG));
        state = data.flatMap(t -> FlagpoleState.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), t)
                        .resultOrPartial(e -> Constants.LOG.error("Could not load flagpole at {}: {}", worldPosition, e)))
                .orElse(FlagpoleState.EMPTY);
        if (tag.contains(YAW_TAG, Tag.TAG_FLOAT)) yaw = FlagYaw.wrap(tag.getFloat(YAW_TAG));
        windBearing = tag.contains(WIND_TAG, Tag.TAG_FLOAT) ? FlagYaw.wrap(tag.getFloat(WIND_TAG)) : Float.NaN;
        // The renderer reads the tint every frame, so a new banner needs no re-mesh (before FL1 it was baked into the chunk).
        clothTint = FlagTint.clothTint(state);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

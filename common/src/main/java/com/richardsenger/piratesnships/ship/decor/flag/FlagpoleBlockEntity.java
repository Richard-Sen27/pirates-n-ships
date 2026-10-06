package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.sailing.wind.WindService;
import com.richardsenger.piratesnships.ship.decor.FlagpoleBlock;
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
 * outcome (block state for the look, items, feedback, {@link FlagpoleEvents}) and saves and syncs the state.
 * <p>
 * {@link Clearable} so that ship assembly (which saves the block entity, then clears the old one before removing the
 * block, docs/sable-notes.md §4) moves the flag instead of dropping a copy.
 */
public class FlagpoleBlockEntity extends BlockEntity implements Clearable {

    private static final String TAG = "flagpole";

    private FlagpoleState state = FlagpoleState.EMPTY;
    /**
     * Players who used the pole and may still get items or feedback (the actor of a pending action), so these reach
     * them even if they are not in the level's player list (GameTest mock players). Not saved; after a reload the
     * level's player list is used.
     */
    private final Map<UUID, Player> actors = new HashMap<>();

    public FlagpoleBlockEntity(BlockPos pos, BlockState blockState) {
        super(Flags.FLAGPOLE_BLOCK_ENTITY.get(), pos, blockState);
    }

    public FlagpoleState state() {
        return state;
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
        int interval = FlagConfig.WIND_UPDATE_INTERVAL_TICKS.get();
        if (interval > 0 && Math.floorMod(now + worldPosition.hashCode(), interval) == 0) updateFacing();
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

    /** Shown flag into the block state (the model shows it); a newly shown flag turns downwind at once. */
    private void updateBlockState(boolean shownChanged) {
        BlockState bs = getBlockState();
        if (!(bs.getBlock() instanceof FlagpoleBlock)) return;
        BlockState next = bs.setValue(FlagpoleBlock.FLAG, state.reading().shown());
        if (shownChanged && state.reading().shown() != FlagKind.NONE) next = next.setValue(FlagpoleBlock.FACING, windFacing(bs));
        if (next != bs) level.setBlock(worldPosition, next, 3);
        else level.sendBlockUpdated(worldPosition, bs, bs, 3);
    }

    private void updateFacing() {
        BlockState bs = getBlockState();
        if (!(bs.getBlock() instanceof FlagpoleBlock) || bs.getValue(FlagpoleBlock.FLAG) == FlagKind.NONE) return;
        Direction d = windFacing(bs);
        if (d != bs.getValue(FlagpoleBlock.FACING)) level.setBlock(worldPosition, bs.setValue(FlagpoleBlock.FACING, d), 3);
    }

    private Direction windFacing(BlockState bs) {
        Direction current = bs.getValue(FlagpoleBlock.FACING);
        if (!FlagConfig.FOLLOW_WIND.get() || !(level instanceof ServerLevel server)) return current;
        WindSample wind = WindService.sample(server, Vec3.atCenterOf(worldPosition));
        return FlagWind.downwind(wind.dirX(), wind.dirZ(), current);
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Optional<Tag> data = Optional.ofNullable(tag.get(TAG));
        state = data.flatMap(t -> FlagpoleState.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), t)
                        .resultOrPartial(e -> Constants.LOG.error("Could not load flagpole at {}: {}", worldPosition, e)))
                .orElse(FlagpoleState.EMPTY);
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

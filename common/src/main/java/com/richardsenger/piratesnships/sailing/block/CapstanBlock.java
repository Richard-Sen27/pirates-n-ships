package com.richardsenger.piratesnships.sailing.block;

import com.richardsenger.piratesnships.sailing.anchor.AnchorEntities;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.ship.ShipControls;
import com.richardsenger.piratesnships.station.StationBlock;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.capstan.CapstanStation;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The capstan (docs/design.md §5.3, §6): on an assembled ship, using it drops the ship's anchor, and using it again
 * raises it. The anchor state lives with the ship (see {@code ShipControls}); {@link #ANCHOR} only shows its phase and
 * is set by the runtime. Placing and removing it tells {@code sailing.anchor.AnchorEntities}, which shows the anchor
 * as an entity at the hull side.
 *
 * <p>Model: hand-made in Blockbench ({@code art/models/capstan.bbmodel}, design.md §4.8), a whelped drum on an iron
 * pawl ring and a base plate, with a drumhead and two crossed capstan bars that reach 3 px past the block. It has no
 * facing; every {@link #ANCHOR} phase shows the same model.
 *
 * <p>CRW3: the capstan is a crew station ({@code station.capstan.CapstanStation}): a crew member at it drops and raises
 * the anchor on order, through {@link ShipControls#dropAnchor} and {@link ShipControls#raiseAnchor}.
 */
public class CapstanBlock extends Block implements StationBlock {

    /** Block-state mirror of {@link AnchorState.Phase}. */
    public enum Phase implements StringRepresentable {
        RAISED, DROPPING, HOLDING, RAISING;

        public static Phase of(AnchorState.Phase p) {
            return values()[p.ordinal()];
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Phase> ANCHOR = EnumProperty.create("anchor", Phase.class);

    public CapstanBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ANCHOR, Phase.RAISED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ANCHOR);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel) {
            player.displayClientMessage(ShipControls.useCapstan(serverLevel, pos), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel && !oldState.is(this)) {
            AnchorEntities.capstanPlaced(serverLevel, pos); // a capstan on a ship gets its visible anchor
        }
    }

    @Override
    public StationKind<?> stationKind() {
        return CapstanStation.INSTANCE;
    }

    /** Station tools (the captain's whistle) act through their own {@code useOn} instead of dropping the anchor. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof StationBlock.Tool) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !newState.is(this)) {
            AnchorEntities.capstanRemoved(serverLevel, pos); // the anchor goes with its capstan
            Stations.onStationRemoved(serverLevel, pos); // CRW3: frees the capstan station and removes its seat
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}

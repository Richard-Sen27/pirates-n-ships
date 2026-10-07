package com.richardsenger.piratesnships.sailing.block;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.station.StationBlock;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.winch.WinchStation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Sail winch (docs/design.md §5.2, spike version): using it cycles the trim of every sail of the ship it stands on,
 * furled → half → full → furled. The next trim follows the <em>highest</em> trim currently set on that ship, so a
 * ship with mixed trims is first brought to one trim. It is also a station (docs/design.md §6, spike 4): a crew member
 * assigned to it carries out sail orders ({@link WinchStation}); a player's use keeps working as before.
 *
 * <p>Model: hand-made in Blockbench ({@code art/models/sail_winch.bbmodel}, design.md §4.8), a rope drum along x
 * between two braced uprights, with an iron ratchet and pawl on the west end and the crank on the east end, reaching
 * 3 px past the block. {@link #FACING} is the side the crank is on: set at placement so that the crank faces the
 * placing player (as the helm's wheel does), turned and mirrored with the block. The unrotated model is
 * {@code facing=east}, which is also the default state, so winches from before the facing existed keep their look.
 * The facing is visual only: the trim cycle and the crew station do not depend on it.
 */
public class SailWinchBlock extends Block implements StationBlock {

    static final String KEY = "message." + Constants.MOD_ID + ".sail_winch.";
    public static final String KEY_NOT_ON_SHIP = KEY + "not_on_ship";
    public static final String KEY_NO_SAILS = KEY + "no_sails";
    /** "This ship has no sails: %s", with the rigging's first problem (Q5). */
    public static final String KEY_NO_SAILS_WHY = KEY + "no_sails_why";
    public static final String KEY_SET = KEY + "set";
    public static final String KEY_SAIL_SET = KEY + "sail_set";

    /** The side the crank is on (the model's unrotated crank is east). */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public SailWinchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.EAST));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** The crank faces the placing player. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Translation key of a trim name. */
    public static String trimKey(SailTrim trim) {
        return "sail_trim." + Constants.MOD_ID + "." + trim.getSerializedName();
    }

    @Override
    public StationKind<?> stationKind() {
        return WinchStation.INSTANCE;
    }

    /** Station tools (the captain's whistle) act through their own {@code useOn} instead of cycling the trim. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof StationBlock.Tool) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** Breaking the winch frees the station and removes its seat (the crew member gets off on deck). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            Stations.onStationRemoved(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel) {
            player.displayClientMessage(use(serverLevel, pos), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The winch action at {@code pos} (plot position of the winch); returns the feedback. Public for GameTests. */
    public static Component use(ServerLevel level, BlockPos pos) {
        SailingRuntimes.CycleResult r = SailingRuntimes.cycleTrim(level, pos);
        if (r == null) {
            return Component.translatable(KEY_NOT_ON_SHIP);
        }
        if (r.sails() == 0) {
            var ship = com.richardsenger.piratesnships.ship.sable.SableShips.containing(level, pos);
            Component problem = ship == null ? null : com.richardsenger.piratesnships.station.winch.RiggingReport.of(level, ship).problem();
            return problem == null ? Component.translatable(KEY_NO_SAILS) : Component.translatable(KEY_NO_SAILS_WHY, problem);
        }
        return Component.translatable(KEY_SET, Component.translatable(trimKey(r.trim())), r.sails());
    }
}

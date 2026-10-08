package com.richardsenger.piratesnships.ship.assembly;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationBlock;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.helm.HelmStation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The helm (docs/design.md §4.1, §5.3). In the world, using it assembles the connected blocks into a ship. On an
 * assembled ship, using it steers (handled by the {@link SteeringHandler} the sailing module installs), and sneak-use
 * with an empty hand disassembles the ship. A named name tag names the ship. All logic runs on the server.
 *
 * <p>HL1: breaking the helm of a ship leaves it a registered, helmless ship; a helm placed anywhere on it becomes its
 * steering helm, and while that one stands, any further helm on the ship is a second helm that neither steers nor
 * disassembles ({@link ShipHelm}, {@link HelmRules}).
 *
 * <p>{@link #RUDDER} is the rudder position, stored as {@code step + 5} (5 = midships, 0..4 port, 6..10 starboard; see
 * {@code sailing.ship.RudderSteps}). It lives in the block state, so it is visible (F3) and is saved with the ship's
 * blocks. With the sailing module's wheel steering (HELM1, the default) the rudder follows the wheel's angle instead,
 * which lives in the block entity the sailing module installs ({@link #setBlockEntityFactory}); the property then
 * only matters for the old click steps.
 *
 * <p>WS3a: the helm is also a station ({@link HelmStation}): a crew member assigned to it stands beside the wheel and
 * holds a course ({@code station.helm.CourseOrder}) on the ship's steering helm. Breaking it frees the station.
 */
public class HelmBlock extends HorizontalDirectionalBlock implements EntityBlock, StationBlock {

    public static final MapCodec<HelmBlock> CODEC = simpleCodec(HelmBlock::new);
    public static final int MIDSHIPS = 5;
    public static final IntegerProperty RUDDER = IntegerProperty.create("rudder", 0, 2 * MIDSHIPS);
    public static final String KEY_DISASSEMBLE_HINT = com.richardsenger.piratesnships.Constants.MOD_ID + ".assembly.disassemble_hint";

    /** Steering on an assembled ship. Returns the message for the helmsman (shown in the action bar). */
    @FunctionalInterface
    public interface SteeringHandler {
        Component steer(ServerLevel level, BlockPos pos, BlockState state, Player player, BlockHitResult hit);
    }

    /** Creates the helm's block entity (installed by the sailing module, which owns the wheel). */
    @FunctionalInterface
    public interface BlockEntityFactory {
        @Nullable BlockEntity create(BlockPos pos, BlockState state);
    }

    private static volatile @Nullable SteeringHandler steering;
    private static volatile @Nullable BlockEntityFactory blockEntities;

    /** Installed by the sailing module. Without one, helms have no block entity. */
    public static void setBlockEntityFactory(@Nullable BlockEntityFactory factory) {
        blockEntities = factory;
    }

    /** Installed by the sailing module. Without one, using the helm on a ship only shows the disassembly hint. */
    public static void setSteeringHandler(@Nullable SteeringHandler handler) {
        steering = handler;
    }

    public HelmBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(RUDDER, MIDSHIPS));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RUDDER);
    }

    @Override
    public StationKind<?> stationKind() {
        return HelmStation.INSTANCE;
    }

    /** Breaking the helm frees its station and removes its seat (a rudder change is no removal). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            Stations.onStationRemoved(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        BlockEntityFactory f = blockEntities;
        return f == null ? null : f.create(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel) {
            ShipBody ship = SableShips.containing(serverLevel, pos);
            if (ship == null) {
                player.displayClientMessage(ShipAssembler.assemble(serverLevel, pos, player).message(), false);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            // HL1: only the ship's steering helm steers and disassembles; a helmless ship takes this one
            HelmRules.Role role = ShipHelm.claim(ship, pos);
            if (role == HelmRules.Role.NOT_A_SHIP) {
                // a Sable body without our ship pointer (e.g. one orphaned by a split before HL1): this helm makes it a ship
                player.displayClientMessage(ShipHelm.adopt(ship, pos, player).message(), false);
            } else if (role == HelmRules.Role.SECOND) {
                player.displayClientMessage(Component.translatable(ShipHelm.KEY_SECOND), true);
            } else if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
                player.displayClientMessage(ShipAssembler.disassemble(ship, pos, player).message(), false);
            } else {
                SteeringHandler h = steering;
                player.displayClientMessage(h == null ? Component.translatable(KEY_DISASSEMBLE_HINT)
                        : h.steer(serverLevel, pos, state, player, hit), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * HL1: a helm placed on a helmless ship becomes its steering helm. Runs for every placement on the server, also when
     * Sable moves blocks: into a body that is not registered yet (assembly, a split's new body) nothing happens
     * ({@code NOT_A_SHIP}); moved into a helmless keeper by a rejoin, the helm attaches. A change of the rudder property
     * is not a placement.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel && !oldState.is(this)) {
            ShipBody ship = SableShips.containing(serverLevel, pos);
            if (ship != null) {
                ShipHelm.claim(ship, pos);
            }
        }
    }

    /** Tells the player who placed a helm on a ship whether it steers that ship. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && placer instanceof Player player) {
            ShipBody ship = SableShips.containing(serverLevel, pos);
            if (ship == null || !ShipHelm.isOurShip(ship)) {
                return;
            }
            HelmRules.Role role = ShipHelm.claim(ship, pos);
            if (role == HelmRules.Role.SECOND) {
                player.displayClientMessage(Component.translatable(ShipHelm.KEY_SECOND), true);
            } else if (HelmRules.steers(role)) {
                String name = ShipRegistry.get(serverLevel.getServer()).find(ship.id()).map(ShipData::name).orElse("");
                player.displayClientMessage(Component.translatable(ShipHelm.KEY_ATTACHED,
                        name.isEmpty() ? Component.translatable(ShipHelm.KEY_UNNAMED) : Component.literal(name)), true);
            }
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof StationBlock.Tool) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION; // the captain's whistle assigns crew to the helm
        }
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (!stack.is(Items.NAME_TAG) || name == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel serverLevel) {
            ShipBody ship = SableShips.containing(serverLevel, pos);
            AssemblyResult result = ship == null ? AssemblyResult.of(AssemblyResult.Outcome.NO_SHIP)
                    : ShipAssembler.name(ship, name.getString());
            if (result.success() && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(result.success()
                    ? Component.translatable(result.outcome().key(), name.getString()) : result.message(), false);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}

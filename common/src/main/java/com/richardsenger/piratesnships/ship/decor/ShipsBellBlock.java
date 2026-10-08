package com.richardsenger.piratesnships.ship.decor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The ship's bell (ART2, design.md §4.8): a brass bell on a wooden bracket, either on a wall ({@link #FACE} wall,
 * {@link #FACING} away from the wall) or in a small frame standing on top of a post or any block with a centre support
 * ({@link #FACE} floor, {@link #FACING} towards the player who placed it). Using it rings it: vanilla's bell sound and
 * {@link #RINGING} for {@code ship_decor.bell_ring_ticks}, which shows the swung model ({@code block/ships_bell_ringing},
 * {@code block/ships_bell_wall_ringing}). No other gameplay.
 */
public class ShipsBellBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<ShipsBellBlock> CODEC = simpleCodec(ShipsBellBlock::new);
    /** Floor (in its frame, on a post) or wall (on its bracket); the bell never hangs from a ceiling. */
    public static final EnumProperty<AttachFace> FACE = EnumProperty.create("face", AttachFace.class, AttachFace.FLOOR, AttachFace.WALL);
    public static final BooleanProperty RINGING = BooleanProperty.create("ringing");

    private static final Map<Direction, VoxelShape> FLOOR = DecorShapes.horizontal(DecorShapes.b(2, 0, 5.5, 14, 16, 10.5));
    private static final Map<Direction, VoxelShape> WALL = DecorShapes.horizontal(DecorShapes.b(4.5, 2, 4.5, 11.5, 15.25, 16));

    public ShipsBellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.FLOOR).setValue(FACING, Direction.NORTH)
                .setValue(RINGING, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, RINGING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Direction looking : context.getNearestLookingDirections()) {
            BlockState state;
            if (looking == Direction.UP) {
                continue;
            } else if (looking == Direction.DOWN) {
                state = defaultBlockState().setValue(FACE, AttachFace.FLOOR).setValue(FACING, context.getHorizontalDirection().getOpposite());
            } else {
                state = defaultBlockState().setValue(FACE, AttachFace.WALL).setValue(FACING, looking.getOpposite());
            }
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                return state;
            }
        }
        return null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(FACE) == AttachFace.FLOOR) {
            return Block.canSupportCenter(level, pos.below(), Direction.UP);
        }
        Direction wall = state.getValue(FACING).getOpposite();
        BlockPos behind = pos.relative(wall);
        return level.getBlockState(behind).isFaceSturdy(level, behind, wall.getOpposite());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction support = state.getValue(FACE) == AttachFace.FLOOR ? Direction.DOWN : state.getValue(FACING).getOpposite();
        if (dir == support && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            ring(level, pos, state, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Rings the bell at {@code pos}: the sound, and the swing for {@code ship_decor.bell_ring_ticks}. Server side. */
    public void ring(Level level, BlockPos pos, BlockState state, @Nullable Player player) {
        level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0f, 1.0f);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        level.setBlock(pos, state.setValue(RINGING, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, DecorConfig.BELL_RING_TICKS.get());
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(RINGING)) {
            level.setBlock(pos, state.setValue(RINGING, false), Block.UPDATE_ALL);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(FACE) == AttachFace.FLOOR ? FLOOR : WALL).get(state.getValue(FACING));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}

package com.richardsenger.piratesnships.sailing.block;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.force.SailType;
import com.richardsenger.piratesnships.sailing.force.SailTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One whole sail of a {@link SailType} (docs/design.md §5.2): a single block placed on a mast stands for the sail.
 * Only the fore-and-aft sail still works this way (square sails are built from {@link YardBlock}s since F5a; the
 * triangular sail of F5b replaces this block). {@link #TRIM} (furled / half / full) persists in the block state and
 * selects the model.
 *
 * <p><b>Orientation:</b> {@code FACING} is the sail's normal, the side the canvas faces; placed, it faces the player.
 * A fore-and-aft sail stands <em>along</em> the hull, so place it facing port or starboard. The facing is visual only:
 * the force model assumes the crew sheets the sails optimally, so the force depends on the sail type and the apparent
 * wind angle, never on how the block was turned.
 */
public class SailBlock extends HorizontalDirectionalBlock {

    public static final EnumProperty<SailTrim> TRIM = EnumProperty.create("trim", SailTrim.class);
    public static final MapCodec<SailBlock> CODEC = simpleCodec(p -> new SailBlock(SailTypes.FORE_AND_AFT, p));

    private static final VoxelShape[] SHAPES = {
            Block.box(0, 0, 13, 16, 16, 16), // facing north: plate at the south edge, like an open trapdoor
            Block.box(0, 0, 0, 3, 16, 16),   // east
            Block.box(0, 0, 0, 16, 16, 3),   // south
            Block.box(13, 0, 0, 16, 16, 16)  // west
    };

    private final SailType type;

    public SailBlock(SailType type, Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TRIM, SailTrim.FURLED));
    }

    public SailType type() {
        return type;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TRIM);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[switch (state.getValue(FACING)) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        }];
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!SailingConfig.SAIL_BLOCK_TRIM.get()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            SailTrim next = state.getValue(TRIM).next();
            level.setBlock(pos, state.setValue(TRIM, next), Block.UPDATE_ALL);
            player.displayClientMessage(Component.translatable(SailWinchBlock.KEY_SAIL_SET,
                    Component.translatable(SailWinchBlock.trimKey(next))), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

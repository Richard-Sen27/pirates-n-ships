package com.richardsenger.piratesnships.law.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Vanilla iron bars, plus one rule: the bars also connect to a brig door (either half, any facing, open or closed),
 * so a cell wall of bars meets its door with an arm reaching to the door's block edge. Vanilla iron bars don't connect
 * to doors (a door's face is not sturdy and {@code IronBarsBlock#attachsTo} is final), so placement and neighbour
 * updates add the door connection after vanilla's rule.
 *
 * <p>Model: hand-made (art/models/brig_bars*.bbmodel, design.md §4.8): a capped iron post, and per connected side an
 * arm of two round bars between a top and a bottom rail with a middle brace. Opaque, so no render type is needed.
 */
public class BrigBarsBlock extends IronBarsBlock {

    public static final MapCodec<BrigBarsBlock> CODEC = simpleCodec(BrigBarsBlock::new);

    public BrigBarsBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends IronBarsBlock> codec() {
        return CODEC;
    }

    /** Whether bars connect to {@code neighbour} on top of vanilla's rule: any brig door half. */
    public static boolean connectsToDoor(BlockState neighbour) {
        return neighbour.getBlock() instanceof BrigDoorBlock;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        BlockPos pos = context.getClickedPos();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (connectsToDoor(context.getLevel().getBlockState(pos.relative(d)))) {
                state = state.setValue(PROPERTY_BY_DIRECTION.get(d), true);
            }
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level,
                                     BlockPos currentPos, BlockPos facingPos) {
        BlockState updated = super.updateShape(state, facing, facingState, level, currentPos, facingPos);
        if (facing.getAxis().isHorizontal() && connectsToDoor(facingState)) {
            updated = updated.setValue(PROPERTY_BY_DIRECTION.get(facing), true);
        }
        return updated;
    }
}

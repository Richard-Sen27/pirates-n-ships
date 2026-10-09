package com.richardsenger.piratesnships.ship.rigging;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.YardBlock;
import com.richardsenger.piratesnships.sailing.sail.SquareSail;
import com.richardsenger.piratesnships.sailing.sail.YardLinker;
import com.richardsenger.piratesnships.sailing.sail.YardLookup;
import com.richardsenger.piratesnships.sailing.sail.YardRow;
import com.richardsenger.piratesnships.sailing.sail.YardRules;
import com.richardsenger.piratesnships.sailing.sail.YardSails;
import com.richardsenger.piratesnships.ship.decor.DecorShapes;
import com.richardsenger.piratesnships.ship.rigging.RatlinesRules.Kind;
import com.richardsenger.piratesnships.ship.rigging.RatlinesRules.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Ratlines (RL1, docs/design.md §4.8 "Visual backlog 2", item 1): a rope net to climb a mast, in the
 * {@code minecraft:climbable} tag, so players and mobs climb it like a ladder, also on an assembled ship (Sable's
 * {@code climbing_sub_levels} mixin finds climbable blocks in a ship's plot under the entity). Waterloggable; broken by
 * hand, it drops itself.
 *
 * <p>Two shapes ({@link #KIND}):
 * <ul>
 *   <li><b>{@code wall}</b>: hung against a face like a ladder. {@link #FACING} points away from the support (the
 *       supporting block is on the opposite side), as for a vanilla ladder. The support is any sturdy face (a mast of
 *       logs) or a block in {@link RiggingContent#RATLINES_ANCHORS} (fences and walls, for a mast of fence posts). The
 *       net falls (and drops) when its support goes.</li>
 *   <li><b>{@code slope}</b>: laid at 45 degrees from the bottom edge of the side opposite {@link #FACING} to the top
 *       edge of the facing side, so it rises towards {@code facing}. A run of sloped blocks, each one up and one
 *       forward of the last, climbs from the gunwale to the masthead like real shrouds. It stands on a block below with
 *       a sturdy centre (deck, gunwale), on a ratlines block below, on the previous link of its run (one down, one
 *       back) or leans with its top edge on a support in front (the mast). When a link breaks, the links above it that
 *       have nothing else to stand on fall one after the other.</li>
 * </ul>
 *
 * <p><b>Placement</b> (rules and their order in {@link RatlinesRules#candidates}): click the side of a mast to hang a
 * net on it; click the top of the deck or the gunwale to lay a sloped net rising the way you look; click the bottom of
 * a block to hang it like a ladder. Clicking a ratlines block without sneaking extends its run at the free end
 * ({@link RatlinesItem}); sneaking places against the clicked face as usual.
 *
 * <p><b>Yards and the nest</b> (RL1b): yards and the crow's nest are anchors, so a net hangs on a yard's end face,
 * a sloped run stands on a yard or leans its top on a yard or the nest; a net that only a yard holds is refused in a
 * square sail's cloth ({@link #refusedByCloth}, action bar {@link #KEY_IN_CLOTH}). The check runs at placement only:
 * a sail rigged later next to existing nets leaves them hanging.
 *
 * <p>Collision: a hung net has a ladder's 3 px plate; a sloped net has four 1.5 px treads under its ratlines, 4 px
 * apart, so a player walks up a run like a stair (and climbs it while touching a tread). Hand-made models
 * {@code block/ratlines} and {@code block/ratlines_slope} ({@code art/models/ratlines.bbmodel}).
 */
public class RatlinesBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<RatlinesBlock> CODEC = simpleCodec(RatlinesBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** Action bar text when a net would hang on a yard inside a sail's cloth (RL1b). */
    public static final String KEY_IN_CLOTH = "message." + Constants.MOD_ID + ".ratlines.in_cloth";

    private static final Map<Kind, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(Kind.class);

    static {
        for (Kind kind : Kind.values()) {
            SHAPES.put(kind, DecorShapes.horizontal(RatlinesRules.boxes(kind).toArray(double[][]::new)));
        }
    }

    public RatlinesBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(KIND, Kind.WALL)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, KIND, WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(KIND)).get(state.getValue(FACING));
    }

    // ------------------------------------------------------------------ support

    /** The supports of a ratlines block at {@code pos} with the given facing, read from the world. */
    public static RatlinesRules.Supports supports(LevelReader level, BlockPos pos, Direction facing) {
        return supports(level, pos, facing, true);
    }

    /**
     * The supports of a ratlines block at {@code pos} with the given facing; with {@code yards} false a yard block holds
     * nothing (RL1b: the cloth check asks whether the net needs a yard).
     */
    public static RatlinesRules.Supports supports(LevelReader level, BlockPos pos, Direction facing, boolean yards) {
        BlockPos behind = pos.relative(facing.getOpposite());
        BlockPos below = pos.below();
        BlockPos front = pos.relative(facing);
        BlockState belowState = level.getBlockState(below);
        BlockState previous = level.getBlockState(below.relative(facing.getOpposite()));
        return new RatlinesRules.Supports(
                holds(level, behind, facing, yards),
                belowState.isFaceSturdy(level, below, Direction.UP, SupportType.CENTER)
                        || belowState.is(RiggingContent.RATLINES_ANCHORS) && (yards || !isYard(belowState)),
                belowState.getBlock() instanceof RatlinesBlock,
                previous.getBlock() instanceof RatlinesBlock && previous.getValue(KIND) == Kind.SLOPE
                        && previous.getValue(FACING) == facing,
                holds(level, front, facing.getOpposite(), yards));
    }

    /**
     * Whether the block at {@code pos} carries a net on its {@code face}: a sturdy face or an anchor (a fence, a wall, a
     * yard, the crow's nest); a yard only when {@code yards}.
     */
    private static boolean holds(LevelReader level, BlockPos pos, Direction face, boolean yards) {
        BlockState state = level.getBlockState(pos);
        if (!yards && isYard(state)) {
            return false;
        }
        return state.is(RiggingContent.RATLINES_ANCHORS) || state.isFaceSturdy(level, pos, face);
    }

    private static boolean isYard(BlockState state) {
        return state.getBlock() instanceof YardBlock;
    }

    /**
     * RL1b: whether {@code placement} at {@code pos} is refused because only a yard holds it and the cell lies in the
     * cloth of a square sail of that yard ({@link RatlinesRules#refusedByCloth}). The sails looked at are those of the
     * yards next to the cell (behind, in front, below): the sail each heads and the sail whose lower yard it is.
     */
    public static boolean refusedByCloth(LevelReader level, BlockPos pos, Placement placement) {
        Direction facing = placement.facing();
        RatlinesRules.Supports with = supports(level, pos, facing, true);
        RatlinesRules.Supports without = supports(level, pos, facing, false);
        if (!RatlinesRules.refusedByCloth(placement.kind(), with, without, true)) {
            return false;
        }
        YardRules rules = SailingConfig.yardRules();
        YardLookup lookup = YardSails.lookup(level);
        for (BlockPos yard : new BlockPos[] {pos.relative(facing.getOpposite()), pos.relative(facing), pos.below()}) {
            if (!isYard(level.getBlockState(yard))) {
                continue;
            }
            YardRow row = YardLinker.row(lookup, yard.getX(), yard.getY(), yard.getZ(), rules);
            if (row == null) {
                continue;
            }
            for (SquareSail sail : sailsOf(lookup, row, rules)) {
                if (RatlinesRules.inCloth(sail, pos.getX(), pos.getY(), pos.getZ())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The sails {@code row} belongs to: the one it heads, and the one whose lower yard it is. */
    private static List<SquareSail> sailsOf(YardLookup lookup, YardRow row, YardRules rules) {
        List<SquareSail> out = new ArrayList<>(2);
        SquareSail headed = YardLinker.sailHeadedBy(lookup, row, rules);
        if (headed != null) {
            out.add(headed);
        }
        for (int d = 1; d <= rules.maxGap(); d++) {
            int y = row.y() + d;
            if (!lookup.at(row.middleX(), y, row.middleZ()).isYard()) {
                continue;
            }
            YardRow upper = YardLinker.row(lookup, row.middleX(), y, row.middleZ(), rules);
            SquareSail above = upper == null ? null : YardLinker.sailHeadedBy(lookup, upper, rules);
            if (above != null && above.lower().equals(row)) {
                out.add(above);
            }
            break; // the nearest yard over the column is the only one that can pair with this row
        }
        return out;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return RatlinesRules.survives(state.getValue(KIND), supports(level, pos, state.getValue(FACING)));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (!state.canSurvive(level, pos)) {
            // like a ladder: the caller replaces us with air and drops the item (Block#updateOrDestroy)
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /**
     * A sloped link's next link up the run (one up, one forward) is not a direct neighbour, so it hears nothing when
     * this one goes: tell it with a scheduled tick.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!newState.is(this) && state.getValue(KIND) == Kind.SLOPE && !level.isClientSide) {
            BlockPos up = pos.offset(RatlinesRules.next(Kind.SLOPE, state.getValue(FACING)));
            if (level.getBlockState(up).is(this)) {
                level.scheduleTick(up, this, 1);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    // ------------------------------------------------------------------ placement

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Iterable<Placement> order = context instanceof RatlinesItem.RunContext run
                ? java.util.List.of(run.placement())
                : RatlinesRules.candidates(context.getClickedFace(), context.getHorizontalDirection(),
                context.getNearestLookingDirections());
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean water = level.getFluidState(pos).getType() == Fluids.WATER;
        boolean cloth = false;
        for (Placement p : order) {
            BlockState state = defaultBlockState().setValue(KIND, p.kind()).setValue(FACING, p.facing())
                    .setValue(WATERLOGGED, water);
            if (state.canSurvive(level, pos)) {
                if (refusedByCloth(level, pos, p)) {
                    cloth = true;
                    continue;
                }
                return state;
            }
        }
        if (cloth && context.getPlayer() != null && !level.isClientSide) {
            context.getPlayer().displayClientMessage(Component.translatable(KEY_IN_CLOTH), true);
        }
        return null;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
}

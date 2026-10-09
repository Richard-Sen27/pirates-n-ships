package com.richardsenger.piratesnships.crew.hammock;

import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The hammock (HM1, docs/design.md §7.1): the crew's bunk, a two-block canvas bed that hangs between two supports at
 * the same height ({@link HammockRules}). Two halves like a bed: the {@link BedPart#FOOT} at the clicked block, the
 * {@link BedPart#HEAD} one block further in {@link #FACING}. Each half has its own model ({@code block/hammock_foot},
 * {@code block/hammock_head}, rotated by the block state), so the Blockbench model (ART1d) replaces the placeholders
 * without code changes.
 * <p>
 * Losing either half or either support brings the whole hammock down; only the foot has loot, so that drops one
 * hammock ({@link #updateShape} returns air, which vanilla destroys with drops).
 * <p>
 * SLP1: players sleep in it too, on land and on a ship ({@link PlayerSleep}, {@code crew.hammock.player_sleep}); with
 * that off it stays crew-only. For vanilla's sleeping code it is a bed: the sleeper's sleeping position is the head
 * half, and the loader extension methods below say so ({@link #isBed}, {@link #setBedOccupied},
 * {@link #getRespawnPosition}).
 */
public class HammockBlock extends HorizontalDirectionalBlock implements Bunk, SimpleWaterloggedBlock {

    public static final MapCodec<HammockBlock> CODEC = simpleCodec(HammockBlock::new);
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;

    /** Top of the canvas in pixels at its outer ends (ART1d model: it sags to 4 at the seam, see HammockSeat). */
    public static final int CANVAS_TOP = 7;
    /** Bottom of the canvas in pixels, at the seam where it sags lowest (ART1d model). */
    public static final int CANVAS_BOTTOM = 3;
    /** Inset of the canvas from the block's long sides, in pixels. */
    public static final int CANVAS_INSET = 2;

    private static final VoxelShape SHAPE_Z = Block.box(CANVAS_INSET, CANVAS_BOTTOM, 0, 16 - CANVAS_INSET, CANVAS_TOP, 16);
    private static final VoxelShape SHAPE_X = Block.box(0, CANVAS_BOTTOM, CANVAS_INSET, 16, CANVAS_TOP, 16 - CANVAS_INSET);

    public static final String KEY_CREW_ONLY = "message.pirates_n_ships.hammock.crew_only";

    public HammockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT).setValue(Waterlogging.WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, Waterlogging.WATERLOGGED);
    }

    // ------------------------------------------------------------------ supports

    /** What kind of support the block at {@code pos} is for a hammock end touching its face {@code face}. */
    public static HammockRules.Support supportAt(BlockGetter level, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        if (state.is(HammockTags.SUPPORTS)) {
            return HammockRules.Support.POST;
        }
        return state.isFaceSturdy(level, pos, face) ? HammockRules.Support.SOLID : HammockRules.Support.NONE;
    }

    /** Whether a hammock with its foot at {@code foot} along {@code facing} can hang there (supports only). */
    public static boolean canHang(BlockGetter level, BlockPos foot, Direction facing) {
        return HammockRules.canHang(foot, facing, (p, face) -> supportAt(level, p, face));
    }

    // ------------------------------------------------------------------ placing and breaking

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection();
        BlockPos foot = ctx.getClickedPos();
        BlockPos head = HammockRules.head(foot, facing);
        Level level = ctx.getLevel();
        if (!level.getBlockState(head).canBeReplaced(ctx) || !level.getWorldBorder().isWithinBounds(head)
                || !canHang(level, foot, facing)) {
            return null;
        }
        return Waterlogging.placed(defaultBlockState().setValue(FACING, facing).setValue(PART, BedPart.FOOT), ctx);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            BlockPos head = HammockRules.head(pos, state.getValue(FACING));
            level.setBlock(head, Waterlogging.at(state.setValue(PART, BedPart.HEAD), level, head), Block.UPDATE_ALL);
            level.blockUpdated(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        BedPart part = state.getValue(PART);
        Direction facing = state.getValue(FACING);
        if (dir == HammockRules.towardOther(part, facing)) {
            boolean other = neighbor.is(this) && neighbor.getValue(PART) != part && neighbor.getValue(FACING) == facing;
            return other ? state : Blocks.AIR.defaultBlockState();
        }
        if (dir == HammockRules.outward(part, facing)) {
            return supportAt(level, neighborPos, dir.getOpposite()).holds() ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // creative: take the other half along without drops (vanilla's bed does the same)
        if (!level.isClientSide && player.isCreative()) {
            BlockPos other = HammockRules.otherHalf(pos, state.getValue(PART), state.getValue(FACING));
            BlockState os = level.getBlockState(other);
            if (os.is(this) && os.getValue(PART) != state.getValue(PART)) {
                level.setBlock(other, Waterlogging.leftBehind(os), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, other, Block.getId(os));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // ------------------------------------------------------------------ use, shape

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (HammockConfig.PLAYER_SLEEP.get() && player instanceof ServerPlayer sp) {
                PlayerSleep.use(sp, pos, state);
            } else {
                player.displayClientMessage(Component.translatable(KEY_CREW_ONLY), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ------------------------------------------------------------------ players sleeping (SLP1)

    /** The sleeper's feet: 2 px above the canvas where it sags across the head half (ART1d model), as at a bed. */
    public static final double LYING_HEIGHT = 6;

    @Override
    public double lyingHeight() {
        return LYING_HEIGHT;
    }

    /** It hangs low under a deck beam: no headroom check. */
    @Override
    public boolean needsHeadroom() {
        return false;
    }

    @Override
    public boolean sleepsOnSeat(Level level, BlockPos pos) {
        return true;
    }

    /*
     * Loader extension methods. These have the names and parameters of NeoForge's IBlockExtension methods (no import:
     * common stays vanilla), so on NeoForge they override them; on other loaders they are plain methods nobody calls
     * (the Fabric port needs Fabric API's EntitySleepEvents.ALLOW_BED and MODIFY_SLEEPING_DIRECTION instead).
     * Without isBed vanilla's checkBedExists would wake a sleeper every tick and the client would draw it lying the
     * wrong way (getBedOrientation).
     */

    /** NeoForge {@code IBlockExtension#isBed}: a sleeper's sleeping position may be a hammock. */
    public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, @Nullable LivingEntity sleeper) {
        return true;
    }

    /** NeoForge {@code IBlockExtension#setBedOccupied}: a hammock has no {@code OCCUPIED} property (its seat says so). */
    public void setBedOccupied(BlockState state, Level level, BlockPos pos, LivingEntity sleeper, boolean occupied) {
    }

    /**
     * NeoForge {@code IBlockExtension#getRespawnPosition}: an unforced respawn point at a hammock on land puts the
     * player where it gets up from it ({@link PlayerSleep#standUpInFrame}: beside it, on the floor below it). On a ship Sable answers before this
     * is asked (a tracking point, see {@link PlayerSleep}).
     */
    public Optional<ServerPlayer.RespawnPosAngle> getRespawnPosition(BlockState state, EntityType<?> type, LevelReader level,
                                                                     BlockPos pos, float orientation) {
        if (!(state.getBlock() instanceof HammockBlock)) {
            return Optional.empty();
        }
        BlockPos head = Bunk.head(state, pos);
        return PlayerSleep.standUpInFrame(level, Bunk.foot(state, pos), Bunk.facing(state), orientation)
                .map(v -> ServerPlayer.RespawnPosAngle.of(v, head));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.fluid(state, super.getFluidState(state));
    }
}

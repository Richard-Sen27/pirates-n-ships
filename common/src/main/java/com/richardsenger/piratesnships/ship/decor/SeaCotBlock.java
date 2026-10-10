package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.decor.wood.WoodBlock;
import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.crew.hammock.Bunk;
import com.richardsenger.piratesnships.crew.hammock.PlayerSleep;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.entity.LivingEntity;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The sea cot (ART2, design.md §4.8): a wooden box bed two blocks long, placed and broken like a bed (the
 * {@link BedPart#FOOT} at the clicked block, the {@link BedPart#HEAD} one block further in {@link #FACING}). It is a
 * vanilla {@link BedBlock} drawn with our hand-made models ({@code block/sea_cot_head}, {@code block/sea_cot_foot},
 * {@code art/models/sea_cot.bbmodel}) instead of the bed's block entity renderer, so a player can sleep in it and set
 * the spawn there through vanilla's (and the loader's {@code isBed}) bed handling, with
 * {@code ship_decor.sea_cot_sleeping} on.
 * <p>
 * On an assembled ship (SLP1, with {@code ship_decor.sea_cot_sleeping_aboard} on as well) the player sleeps on a seat
 * that sails with the ship ({@link PlayerSleep}, shared with the hammock): vanilla's bed handling alone would lay the
 * sleeper at the cot's world position once and leave it behind as the ship moves. The respawn point is the cot's plot
 * position, which Sable keeps track of as the ship moves. On land the cot stays pure vanilla.
 * No block entity: vanilla's {@code BedBlockEntity} is valid only for vanilla beds and only carries the colour.
 * <p>
 * ART20: cots side by side join into one wide berth ({@link SeaCotBerth}): {@link #LEFT} and {@link #RIGHT} say whether
 * the half beside it (seen from the foot) is a cot half of the same facing and part, kept up to date by neighbour
 * updates like the name board's row (no block entity). The block state draws the core of the half and per side either
 * the edge (post, rail, the blanket's drape) or the fill that runs the berth on; each cot stays its own bed.
 */
public class SeaCotBlock extends BedBlock implements Bunk, SimpleWaterloggedBlock, WoodBlock {

    public static final MapCodec<SeaCotBlock> CODEC = WoodBlock.codec(SeaCotBlock::new);
    public static final String KEY_ON_SHIP = "message.pirates_n_ships.sea_cot.on_ship";
    public static final String KEY_NO_SLEEPING = "message.pirates_n_ships.sea_cot.no_sleeping";
    /** ART20: a cot half joins the half on its left (seen from the foot, {@link SeaCotBerth#left}). */
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    /** ART20: a cot half joins the half on its right ({@link SeaCotBerth#right}). */
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    /**
     * Outline and collision (facing north), indexed by {@link #sides}: vanilla's 9 px bed slab over the cot (x 0.5..15.5,
     * running to the block's side where a neighbour joins), plus the headboard (to its knobs at 14.75 px) or the
     * footboard (11.75 px) at the outer end. The mattress, blanket and pillow lie under 9.6 px, the sleeper at 11.
     */
    private static final Map<Direction, VoxelShape>[] HEAD = shapes(14.75, 0, 2);
    private static final Map<Direction, VoxelShape>[] FOOT = shapes(11.75, 14, 16);

    @SuppressWarnings("unchecked")
    private static Map<Direction, VoxelShape>[] shapes(double boardTop, double z0, double z1) {
        Map<Direction, VoxelShape>[] out = new Map[4];
        for (int i = 0; i < 4; i++) {
            double x0 = (i & 1) != 0 ? 0 : 0.5;
            double x1 = (i & 2) != 0 ? 16 : 15.5;
            out[i] = DecorShapes.horizontal(DecorShapes.b(x0, 0, 0, x1, 9, 16), DecorShapes.b(x0, 0, z0, x1, boardTop, z1));
        }
        return out;
    }

    private static int sides(BlockState state) {
        return (state.getValue(LEFT) ? 1 : 0) | (state.getValue(RIGHT) ? 2 : 0);
    }

    /** The wood of this member (WOOD3). */
    private final ShipWood wood;

    @Override
    public ShipWood wood() {
        return wood;
    }

    public SeaCotBlock(ShipWood wood, Properties properties) {
        super(DyeColor.RED, properties);
        this.wood = wood;
        // BedBlock's own default comes from stateDefinition.any(), which is waterlogged
        registerDefaultState(defaultBlockState().setValue(Waterlogging.WATERLOGGED, false).setValue(LEFT, false).setValue(RIGHT, false));
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public MapCodec<BedBlock> codec() {
        return (MapCodec) CODEC;
    }

    // ------------------------------------------------------------------ waterlogging (WLOG1)

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(Waterlogging.WATERLOGGED, LEFT, RIGHT);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return Waterlogging.placed(state == null ? null : joined(state, context.getLevel(), context.getClickedPos()), context);
    }

    // ------------------------------------------------------------------ joining (ART20)

    /** {@code state} with {@link #LEFT} and {@link #RIGHT} set from the cot halves beside {@code pos}. */
    public static BlockState joined(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return state.setValue(LEFT, joins(state, level.getBlockState(pos.relative(SeaCotBerth.left(facing)))))
                .setValue(RIGHT, joins(state, level.getBlockState(pos.relative(SeaCotBerth.right(facing)))));
    }

    private static boolean joins(BlockState state, BlockState neighbour) {
        boolean cot = neighbour.getBlock() instanceof SeaCotBlock;
        return SeaCotBerth.joins(state.getValue(FACING), state.getValue(PART),
                cot ? neighbour.getValue(FACING) : null, cot ? neighbour.getValue(PART) : null);
    }

    /**
     * A cot set without placement (a structure, a GameTest, {@code /setblock}) learns its neighbours here; its
     * neighbours learn of it through their {@link #updateShape}.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide) return;
        BlockState next = joined(state, level, pos);
        if (next != state) level.setBlock(pos, next, Block.UPDATE_CLIENTS);
    }

    /** A mirror image swaps the cot's left and right. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        return super.mirror(state.setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT)), mirror);
    }

    /** Vanilla's bed placement, except that the head is waterlogged by the water at its own cell, not the foot's. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide) {
            BlockPos head = pos.relative(state.getValue(FACING));
            level.setBlock(head, Waterlogging.at(joined(state.setValue(PART, BedPart.HEAD), level, head), level, head), Block.UPDATE_ALL);
            level.blockUpdated(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        }
    }

    /** Vanilla's creative break takes the head along with air; this leaves its water instead. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) == BedPart.FOOT) {
            BlockPos head = pos.relative(state.getValue(FACING));
            BlockState hs = level.getBlockState(head);
            if (hs.is(this) && hs.getValue(PART) == BedPart.HEAD && Waterlogging.isWaterlogged(hs)) {
                level.setBlock(head, Waterlogging.leftBehind(hs), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, head, Block.getId(hs));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        BlockState next = super.updateShape(state, direction, neighbor, level, pos, neighborPos);
        if (!next.is(this)) return next;
        Direction facing = next.getValue(FACING);
        if (direction == SeaCotBerth.left(facing)) return next.setValue(LEFT, joins(next, neighbor));
        if (direction == SeaCotBerth.right(facing)) return next.setValue(RIGHT, joins(next, neighbor));
        return next;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.fluid(state, super.getFluidState(state));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.CONSUME;
        }
        if (!DecorConfig.SEA_COT_SLEEPING.get()) {
            player.displayClientMessage(Component.translatable(KEY_NO_SLEEPING), true);
            return InteractionResult.SUCCESS;
        }
        if (SableShips.containing(level, pos) != null) {
            if (!DecorConfig.SEA_COT_SLEEPING_ABOARD.get()) {
                player.displayClientMessage(Component.translatable(KEY_ON_SHIP), true);
            } else if (player instanceof ServerPlayer sp) {
                PlayerSleep.use(sp, pos, state);
            }
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    /** Vanilla's bed height (11 px): the cot's blanket lies at 8.6 px since ART20, vanilla's mattress at 9. */
    @Override
    public double lyingHeight() {
        return 11;
    }

    @Override
    public boolean needsHeadroom() {
        return true;
    }

    /** On an assembled ship: on land vanilla's bed handling does it all. */
    @Override
    public boolean sleepsOnSeat(Level level, BlockPos pos) {
        return SableShips.containing(level, pos) != null;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(PART) == BedPart.HEAD ? HEAD : FOOT)[sides(state)].get(state.getValue(FACING));
    }
}

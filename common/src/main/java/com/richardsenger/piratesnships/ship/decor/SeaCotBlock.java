package com.richardsenger.piratesnships.ship.decor;

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
import net.minecraft.world.level.block.state.StateDefinition;
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
 */
public class SeaCotBlock extends BedBlock implements Bunk, SimpleWaterloggedBlock {

    public static final MapCodec<SeaCotBlock> CODEC = simpleCodec(SeaCotBlock::new);
    public static final String KEY_ON_SHIP = "message.pirates_n_ships.sea_cot.on_ship";
    public static final String KEY_NO_SLEEPING = "message.pirates_n_ships.sea_cot.no_sleeping";

    /** Model frame (facing north): the cot's box up to its rails, plus the headboard or footboard at the outer end. */
    private static final Map<Direction, VoxelShape> HEAD = DecorShapes.horizontal(
            DecorShapes.b(0.5, 0, 0, 15.5, 10.25, 16), DecorShapes.b(0.5, 0, 0, 15.5, 15.75, 2));
    private static final Map<Direction, VoxelShape> FOOT = DecorShapes.horizontal(
            DecorShapes.b(0.5, 0, 0, 15.5, 10.25, 16), DecorShapes.b(0.5, 0, 14, 15.5, 13.25, 16));

    public SeaCotBlock(Properties properties) {
        super(DyeColor.RED, properties);
        // BedBlock's own default comes from stateDefinition.any(), which is waterlogged
        registerDefaultState(defaultBlockState().setValue(Waterlogging.WATERLOGGED, false));
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
        builder.add(Waterlogging.WATERLOGGED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return Waterlogging.placed(super.getStateForPlacement(context), context);
    }

    /** Vanilla's bed placement, except that the head is waterlogged by the water at its own cell, not the foot's. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide) {
            BlockPos head = pos.relative(state.getValue(FACING));
            level.setBlock(head, Waterlogging.at(state.setValue(PART, BedPart.HEAD), level, head), Block.UPDATE_ALL);
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
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
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

    /** Vanilla's bed height: the sleeper's feet 2 px above the blanket (the mattress top is at 9 px). */
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
        return (state.getValue(PART) == BedPart.HEAD ? HEAD : FOOT).get(state.getValue(FACING));
    }
}

package com.richardsenger.piratesnships.ship.decor;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
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
 * {@code ship_decor.sea_cot_sleeping} on. On an assembled ship it is decor only: vanilla sleeping works in world
 * coordinates, and the ship's blocks sit in a far-away plot, so the sleeper and the spawn point would end up there.
 * No block entity: vanilla's {@code BedBlockEntity} is valid only for vanilla beds and only carries the colour.
 */
public class SeaCotBlock extends BedBlock {

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
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public MapCodec<BedBlock> codec() {
        return (MapCodec) CODEC;
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
            player.displayClientMessage(Component.translatable(KEY_ON_SHIP), true);
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
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

package com.richardsenger.piratesnships.crew.galley;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.crew.provisions.ProvisionSettings;
import com.richardsenger.piratesnships.crew.provisions.ProvisionsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The water barrel (design.md §7.4 "Fresh water", rules in {@link WaterBarrelRules}). Water buckets and bottles fill
 * it, empty buckets and glass bottles take water out, rain refills it when open to the sky, use with an empty hand
 * shows the rations. {@link #FILL} shows the level, comparators read it.
 */
public class WaterBarrelBlock extends BaseEntityBlock {

    public static final MapCodec<WaterBarrelBlock> CODEC = simpleCodec(WaterBarrelBlock::new);
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, WaterBarrelRules.MAX_FILL);

    public WaterBarrelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FILL, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILL);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaterBarrelBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WaterBarrelBlockEntity barrel) {
            barrel.resolvePlaced(ProvisionsConfig.settings());
        }
    }

    /** Rations a filled container item adds, or 0 if it is not one. */
    static int rationsIn(ItemStack stack, ProvisionSettings s) {
        if (stack.is(Items.WATER_BUCKET)) {
            return s.waterBucketRations();
        }
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        return stack.is(Items.POTION) && potion != null && potion.is(Potions.WATER) ? 1 : 0;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof WaterBarrelBlockEntity barrel)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ProvisionSettings s = ProvisionsConfig.settings();
        int capacity = WaterBarrelRules.capacity(s);
        int in = rationsIn(stack, s);
        int next;
        ItemStack result;
        SoundEvent sound;
        if (in > 0) {
            next = WaterBarrelRules.fill(barrel.rations(), in, capacity);
            result = new ItemStack(stack.is(Items.WATER_BUCKET) ? Items.BUCKET : Items.GLASS_BOTTLE);
            sound = stack.is(Items.WATER_BUCKET) ? SoundEvents.BUCKET_EMPTY : SoundEvents.BOTTLE_EMPTY;
        } else if (stack.is(Items.BUCKET)) {
            next = WaterBarrelRules.drain(barrel.rations(), s.waterBucketRations());
            result = new ItemStack(Items.WATER_BUCKET);
            sound = SoundEvents.BUCKET_FILL;
        } else if (stack.is(Items.GLASS_BOTTLE)) {
            next = WaterBarrelRules.drain(barrel.rations(), 1);
            result = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            sound = SoundEvents.BOTTLE_FILL;
        } else {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (next < 0) {
            // does not fit / not enough water: fall through to the info text
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            barrel.setRations(next, s);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, result));
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WaterBarrelBlockEntity barrel) {
            player.displayClientMessage(GalleyText.barrelInfo(barrel.rations(), WaterBarrelRules.capacity(ProvisionsConfig.settings())), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(FILL) < WaterBarrelRules.MAX_FILL;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        rainTick(level, pos, random.nextDouble());
    }

    /** One rain check with the given roll; public for tests. */
    public static void rainTick(ServerLevel level, BlockPos pos, double roll) {
        if (level.getBlockEntity(pos) instanceof WaterBarrelBlockEntity barrel) {
            ProvisionSettings s = ProvisionsConfig.settings();
            int next = WaterBarrelRules.rain(barrel.rations(), WaterBarrelRules.capacity(s), ProvisionsConfig.RAIN_REFILL_ENABLED.get(),
                    level.isRainingAt(pos.above()), ProvisionsConfig.RAIN_REFILL_CHANCE.get(), roll);
            barrel.setRations(next, s);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof WaterBarrelBlockEntity barrel
                ? WaterBarrelRules.comparator(barrel.rations(), WaterBarrelRules.capacity(ProvisionsConfig.settings()))
                : 0;
    }
}

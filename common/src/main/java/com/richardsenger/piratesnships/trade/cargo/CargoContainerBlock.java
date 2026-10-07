package com.richardsenger.piratesnships.trade.cargo;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.trade.plunder.PlunderMark;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Cargo crate or barrel: holds one kind of item in bulk ({@link CargoContainerBlockEntity}). No screen:
 * <ul>
 *   <li>use with a stack: put the stack in;</li>
 *   <li>use with an empty hand: take one stack out;</li>
 *   <li>sneak-use with an empty hand: put in everything of the held kind from the inventory (vanilla skips the
 *       block when a sneaking player holds an item, so this is the only sneak interaction that reaches it).</li>
 * </ul>
 * Every interaction shows the content on the action bar. Breaking keeps the cargo in the dropped item. The models are
 * hand-made in Blockbench (art/models/cargo_crate.bbmodel, cargo_barrel.bbmodel, design.md §4.8) and do not change with
 * the load; the blocks are registered with {@code noOcclusion} in {@code ShipDecor}.
 */
public class CargoContainerBlock extends BaseEntityBlock {

    public static final MapCodec<CargoContainerBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CargoContainers.Kind.CODEC.fieldOf("kind").forGetter(CargoContainerBlock::kind),
            propertiesCodec()
    ).apply(i, (k, p) -> new CargoContainerBlock(p, k)));

    private final CargoContainers.Kind kind;

    public CargoContainerBlock(Properties properties, CargoContainers.Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public CargoContainers.Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CargoContainerBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof CargoContainerBlockEntity be)) return ItemInteractionResult.FAIL;
        BulkStore.Refusal refusal = be.check(stack);
        if (refusal != BulkStore.Refusal.NONE) {
            player.displayClientMessage(Component.translatable(refusal.translationKey(), stack.getHoverName(), describe(be)), true);
            return ItemInteractionResult.CONSUME;
        }
        be.insert(stack);
        player.displayClientMessage(describe(be), true);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof CargoContainerBlockEntity be)) return InteractionResult.FAIL;
        if (player.isSecondaryUseActive()) {
            insertAllFrom(player.getInventory(), be);
        } else {
            ItemStack kindStack = be.heldKind();
            if (!kindStack.isEmpty()) {
                ItemStack out = be.extract(kindStack.getMaxStackSize());
                player.getInventory().placeItemBackInInventory(out);
            }
        }
        player.displayClientMessage(describe(be), true);
        return InteractionResult.CONSUME;
    }

    /** Moves every stack of the container's kind from {@code inv} in (nothing if the container is empty). */
    public static int insertAllFrom(Inventory inv, CargoContainerBlockEntity be) {
        int moved = 0;
        if (be.heldKind().isEmpty()) return 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && be.check(s) == BulkStore.Refusal.NONE) {
                moved += be.insert(s);
                inv.setChanged();
            }
        }
        return moved;
    }

    /** "Cargo Crate: 1,234 / 2,048 Sugar (plundered)" or "Cargo Crate: empty". */
    public static Component describe(CargoContainerBlockEntity be) {
        Component name = be.getBlockState().getBlock().getName();
        ItemStack kindStack = be.heldKind();
        if (kindStack.isEmpty()) return Component.translatable(CargoText.EMPTY, name);
        String key = PlunderMark.isPlundered(kindStack) ? CargoText.HOLDS_PLUNDERED : CargoText.HOLDS;
        return Component.translatable(key, name, be.count(), be.capacity(), kindStack.getHoverName());
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CargoContainerBlockEntity be ? be.signal() : 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CargoContainerBlockEntity be) {
            be.dropResidue();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}

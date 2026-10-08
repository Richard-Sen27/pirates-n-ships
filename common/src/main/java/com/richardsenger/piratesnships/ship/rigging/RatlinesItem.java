package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.rigging.RatlinesRules.Kind;
import com.richardsenger.piratesnships.ship.rigging.RatlinesRules.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The ratlines item (RL1). Places a {@link RatlinesBlock} by the rules of {@link RatlinesRules#candidates}; used on a
 * ratlines block without sneaking it extends that block's run at its free end instead (straight up a hung net, one up
 * and one forward along a sloped run, at most {@link RatlinesRules#MAX_EXTEND} blocks along), like scaffolding.
 * With {@code rigging.ratlines_enabled} off it places nothing.
 */
public class RatlinesItem extends BlockItem {

    public static final String KEY_DISABLED = "message." + Constants.MOD_ID + ".ratlines.disabled";
    public static final String KEY_TOOLTIP = "block." + Constants.MOD_ID + ".ratlines.tooltip";

    public RatlinesItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    /** A placement context that extends a run: the block takes exactly {@link #placement()}. */
    public static final class RunContext extends BlockPlaceContext {
        private final Placement placement;

        RunContext(BlockPlaceContext from, BlockPos target, Placement placement) {
            super(from.getLevel(), from.getPlayer(), from.getHand(), from.getItemInHand(),
                    new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false));
            this.placement = placement;
        }

        public Placement placement() {
            return placement;
        }

        @Override
        public BlockPos getClickedPos() {
            return getHitResult().getBlockPos();
        }

        @Override
        public boolean canPlace() {
            return getLevel().getBlockState(getClickedPos()).canBeReplaced(this);
        }
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (!RiggingConfig.RATLINES_ENABLED.get()) {
            if (context.getPlayer() != null && !context.getLevel().isClientSide) {
                context.getPlayer().displayClientMessage(Component.translatable(KEY_DISABLED), true);
            }
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }

    @Override
    public @Nullable BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        if (context.isSecondaryUseActive()) {
            return context;
        }
        BlockPos clicked = context.replacingClickedOnBlock() ? context.getClickedPos()
                : context.getClickedPos().relative(context.getClickedFace().getOpposite());
        Level level = context.getLevel();
        BlockState state = level.getBlockState(clicked);
        if (!(state.getBlock() instanceof RatlinesBlock)) {
            return context;
        }
        BlockPos end = runEnd(level, clicked, state);
        return end == null ? null
                : new RunContext(context, end, new Placement(state.getValue(RatlinesBlock.KIND), state.getValue(RatlinesBlock.FACING)));
    }

    /** The first cell past the end of the run through {@code start}, or null when the run is longer or blocked. */
    static @Nullable BlockPos runEnd(Level level, BlockPos start, BlockState startState) {
        Kind kind = startState.getValue(RatlinesBlock.KIND);
        Direction facing = startState.getValue(RatlinesBlock.FACING);
        BlockPos.MutableBlockPos pos = start.mutable();
        for (int i = 0; i < RatlinesRules.MAX_EXTEND; i++) {
            pos.move(RatlinesRules.next(kind, facing));
            if (!level.isInWorldBounds(pos)) {
                return null;
            }
            BlockState here = level.getBlockState(pos);
            if (!(here.getBlock() instanceof RatlinesBlock) || here.getValue(RatlinesBlock.KIND) != kind
                    || here.getValue(RatlinesBlock.FACING) != facing) {
                return here.canBeReplaced() ? pos.immutable() : null;
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(KEY_TOOLTIP).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}

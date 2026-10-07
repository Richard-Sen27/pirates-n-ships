package com.richardsenger.piratesnships.crew.hammock;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The hammock item (HM1). Places the foot without shape updates first (the head follows in
 * {@link HammockBlock#setPlacedBy}, like vanilla's bed item), and tells the player why a hammock cannot hang.
 */
public class HammockItem extends BlockItem {

    public static final String KEY_NO_SUPPORT = "message.pirates_n_ships.hammock.no_support";

    public HammockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext ctx, BlockState state) {
        return ctx.getLevel().setBlock(ctx.getClickedPos(), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_IMMEDIATE);
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        InteractionResult r = super.place(ctx);
        if (r == InteractionResult.FAIL && ctx.getPlayer() != null && !ctx.getLevel().isClientSide && ctx.canPlace()
                && !HammockBlock.canHang(ctx.getLevel(), ctx.getClickedPos(), ctx.getHorizontalDirection())) {
            ctx.getPlayer().displayClientMessage(Component.translatable(KEY_NO_SUPPORT), true);
        }
        return r;
    }
}

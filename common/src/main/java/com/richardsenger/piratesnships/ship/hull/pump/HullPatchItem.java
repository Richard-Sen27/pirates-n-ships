package com.richardsenger.piratesnships.ship.hull.pump;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.hull.FloodingConfig;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntime;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * Hull patch (docs/design.md §4.5): planks and pitch that close a breach. It places the {@code hull_patch} block like
 * any block item, but only into a breach the ship's hull runtime tracks ({@link PatchTarget}): the plot position of a
 * destroyed hull block. Anywhere else it does nothing and says why. The block is watertight, so the breach set drops
 * the position at once (and the runtime closes its opening, which stops the inflow), and the next hull analysis sees
 * plain hull there.
 *
 * <p>The client cannot know the breaches, so it never predicts the placement; the server's block update shows it.
 */
public class HullPatchItem extends BlockItem {

    static final String KEY = "message." + Constants.MOD_ID + ".hull_patch.";
    public static final String KEY_NOT_A_BREACH = KEY + "not_a_breach";
    public static final String KEY_NOT_ON_SHIP = KEY + "not_on_ship";
    public static final String KEY_BLOCKED = KEY + "blocked";
    public static final String KEY_DISABLED = KEY + "disabled";

    public HullPatchItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        PatchTarget.Result r = check(level, context.getClickedPos(), context.canPlace());
        if (r != PatchTarget.Result.OK) {
            Player player = context.getPlayer();
            if (player != null) {
                player.displayClientMessage(Component.translatable(key(r)), true);
            }
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }

    /** Whether a patch may go into plot position {@code pos}; {@code replaceable}: the block there may be replaced. */
    public static PatchTarget.Result check(ServerLevel level, BlockPos pos, boolean replaceable) {
        ShipBody ship = SableShips.containing(level, pos);
        HullRuntime rt = ship == null ? null : HullRuntimes.get(level, ship.id());
        return PatchTarget.check(FloodingConfig.PATCH_ENABLED.get(), rt != null, rt != null && rt.breaches().contains(pos),
                replaceable);
    }

    public static String key(PatchTarget.Result r) {
        return switch (r) {
            case OK, NOT_A_BREACH -> KEY_NOT_A_BREACH;
            case NOT_ON_SHIP -> KEY_NOT_ON_SHIP;
            case BLOCKED -> KEY_BLOCKED;
            case DISABLED -> KEY_DISABLED;
        };
    }
}

package com.richardsenger.piratesnships.sailing.block;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Sail winch (docs/design.md §5.2, spike version): using it cycles the trim of every sail of the ship it stands on,
 * furled → half → full → furled. The next trim follows the <em>highest</em> trim currently set on that ship, so a
 * ship with mixed trims is first brought to one trim. Stations with assigned sails and crew come in spike 4.
 */
public class SailWinchBlock extends Block {

    static final String KEY = "message." + Constants.MOD_ID + ".sail_winch.";
    public static final String KEY_NOT_ON_SHIP = KEY + "not_on_ship";
    public static final String KEY_NO_SAILS = KEY + "no_sails";
    public static final String KEY_SET = KEY + "set";
    public static final String KEY_SAIL_SET = KEY + "sail_set";

    public SailWinchBlock(Properties properties) {
        super(properties);
    }

    /** Translation key of a trim name. */
    public static String trimKey(SailTrim trim) {
        return "sail_trim." + Constants.MOD_ID + "." + trim.getSerializedName();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel) {
            player.displayClientMessage(use(serverLevel, pos), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The winch action at {@code pos} (plot position of the winch); returns the feedback. Public for GameTests. */
    public static Component use(ServerLevel level, BlockPos pos) {
        SailingRuntimes.CycleResult r = SailingRuntimes.cycleTrim(level, pos);
        if (r == null) {
            return Component.translatable(KEY_NOT_ON_SHIP);
        }
        if (r.sails() == 0) {
            return Component.translatable(KEY_NO_SAILS);
        }
        return Component.translatable(KEY_SET, Component.translatable(trimKey(r.trim())), r.sails());
    }
}

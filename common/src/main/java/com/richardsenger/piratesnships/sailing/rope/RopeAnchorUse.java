package com.richardsenger.piratesnships.sailing.rope;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Using a cleat with a grappling hook out ties the hook's rope off there, as on a mooring ring (RP1). The sailing
 * module does not know the grapple, so the grapple installs its tie-off here ({@code GrappleModule.registerContent});
 * until then every use passes.
 */
public final class RopeAnchorUse {

    /** Server: tie the using player's rope off at an anchor. PASS when the player has no hook out (or the grapple is off). */
    @FunctionalInterface
    public interface TieOff {
        InteractionResult tieOff(ServerLevel level, Player player, BlockPos anchor);
    }

    /** Client: whether the use will tie off (a hook of the player's is out), so the client predicts nothing else. */
    @FunctionalInterface
    public interface Predict {
        boolean willTieOff(Level level, Player player, BlockPos anchor);
    }

    private static volatile TieOff tieOff = (level, player, anchor) -> InteractionResult.PASS;
    private static volatile Predict predict = (level, player, anchor) -> false;

    private RopeAnchorUse() {
    }

    public static void install(TieOff server, Predict client) {
        tieOff = server;
        predict = client;
    }

    public static InteractionResult tieOff(ServerLevel level, Player player, BlockPos anchor) {
        return tieOff.tieOff(level, player, anchor);
    }

    public static boolean willTieOff(Level level, Player player, BlockPos anchor) {
        return predict.willTieOff(level, player, anchor);
    }
}

package com.richardsenger.piratesnships.law.brig;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Extra access to locked brig doors besides the owner. Empty for now; the ship and crew systems register a rule
 * later ("the ship's captain and crew may open the brig"), e.g.
 * {@code BrigDoorAccess.register((level, pos, player) -> ShipCrew.isCrewOfShipAt(level, pos, player))}.
 */
public final class BrigDoorAccess {

    @FunctionalInterface
    public interface Rule {
        boolean allows(Level level, BlockPos lowerHalf, Player player);
    }

    private static final List<Rule> RULES = new CopyOnWriteArrayList<>();

    private BrigDoorAccess() {
    }

    public static void register(Rule rule) {
        RULES.add(rule);
    }

    public static boolean allows(Level level, BlockPos lowerHalf, Player player) {
        for (Rule r : RULES) if (r.allows(level, lowerHalf, player)) return true;
        return false;
    }
}

package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.law.flag.FlagLaw;
import com.richardsenger.piratesnships.law.flag.Reaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Server-side entry point for "which flag is shown here" (docs/design.md §4.7), for the ship integration and NPC AI:
 * <pre>
 * FlagReading flag = FlagAllegiance.ofShip(shipLevel, flagpolePositions);
 * if (flag.isStruck()) { stop firing; attacking now is FlagLaw.crimeForAttackingShip(flag.kind(), true, …) }
 * Reaction r = FlagLaw.react(Faction.NAVY, flag.shown(), LawConfig.NPC_SURRENDER.get());
 * </pre>
 * Positions are in the level the poles are in (for a ship: its sub-level plot positions).
 */
public final class FlagAllegiance {

    private FlagAllegiance() {
    }

    /** What the flagpole at {@code pos} shows; {@link FlagReading#NO_FLAG} if there is no flagpole. */
    public static FlagReading at(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FlagpoleBlockEntity be ? be.reading() : FlagReading.NO_FLAG;
    }

    /** What a ship with flagpoles at {@code poles} shows, by the rule in {@link FlagSelection}. */
    public static FlagReading ofShip(Level level, Collection<BlockPos> poles) {
        List<FlagSelection.PoleReading> readings = new ArrayList<>(poles.size());
        for (BlockPos p : poles) readings.add(new FlagSelection.PoleReading(p.immutable(), at(level, p)));
        return FlagSelection.pick(readings);
    }

    /** How {@code observer} treats a ship showing {@code reading}, taken at face value (no false-colors check). */
    public static Reaction reaction(Faction observer, FlagReading reading, boolean surrenderEnabled) {
        return FlagLaw.react(observer, reading.shown(), surrenderEnabled);
    }
}

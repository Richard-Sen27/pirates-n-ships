package com.richardsenger.piratesnships.crew.hammock;

import com.richardsenger.piratesnships.crew.CrewConfig;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.winch.CaptainsWhistleItem;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.jetbrains.annotations.Nullable;

/**
 * A ship's bunks (HM1, docs/design.md §7.1): its hammocks. The crew limit is {@link #limit}: hammocks ×
 * {@code crew.max_crew_multiplier}, rounded down, at least 1 with one hammock or more, 0 without.
 * <p>
 * <b>Informational for now:</b> the whistle's crew line and {@code /pirates crew info} show "crew 3 / bunks 2"; hiring
 * (a later package) will refuse crew beyond the limit. Nothing removes crew above it.
 */
public final class ShipBunks {

    /** What a ship has: crew on board, its crew limit from the hammocks, and the hammocks themselves. */
    public record Count(int crew, int bunks, int hammocks) { }

    private ShipBunks() {
    }

    /** The crew limit of {@code hammocks} hammocks with {@code multiplier} ({@code crew.max_crew_multiplier}). Pure. */
    public static int limit(int hammocks, double multiplier) {
        if (hammocks <= 0) {
            return 0;
        }
        // a tiny epsilon so that e.g. 100 × 0.29 counts as 29, not 28.999…
        return Math.max(1, (int) Math.floor(hammocks * multiplier + 1e-9));
    }

    /** The plot positions of the feet of the hammocks on {@code ship}. */
    public static List<BlockPos> hammocks(ServerLevel level, ShipBody ship) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : ship.plotBlocks()) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof HammockBlock && s.getValue(HammockBlock.PART) == BedPart.FOOT) {
                out.add(p.immutable());
            }
        }
        return out;
    }

    /**
     * Whether the hammock with its foot at {@code foot} is free: nobody lies in it, crew or (SLP1) a sleeping player.
     * Both lie on a {@link HammockSeat} of that foot.
     */
    public static boolean isFree(ServerLevel level, BlockPos foot) {
        return HammockSeat.at(level, foot).isEmpty();
    }

    /** The plot positions of the feet of the free hammocks on {@code ship} ({@link #isFree}). */
    public static List<BlockPos> freeHammocks(ServerLevel level, ShipBody ship) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos foot : hammocks(level, ship)) {
            if (isFree(level, foot)) out.add(foot);
        }
        return out;
    }

    /** The crew of {@code ship} now: on board, at its stations or in its hammocks; prisoners do not count. */
    public static Count count(ServerLevel level, ShipBody ship) {
        int hammocks = hammocks(level, ship).size();
        return new Count(crewOf(level, ship).size(), limit(hammocks, CrewConfig.MAX_CREW_MULTIPLIER.get()), hammocks);
    }

    /**
     * The crew members of {@code ship}: at one of its stations, in one of its hammocks, or free on its deck. Prisoners
     * and the dead are left out.
     */
    public static List<CrewMember> crewOf(ServerLevel level, ShipBody ship) {
        List<CrewMember> out = new ArrayList<>();
        for (CrewMember c : level.getEntitiesOfClass(CrewMember.class, CrewStations.worldBox(ship, 4), CrewMember::isAlive)) {
            if (!BrigService.isPrisoner(c) && ship.id().equals(shipIdOf(level, c))) out.add(c);
        }
        return out;
    }

    /** The ship a crew member belongs to right now: its station's, its hammock's, or the one it stands on. */
    public static @Nullable UUID shipIdOf(ServerLevel level, CrewMember c) {
        if (c.assignment() != null) {
            return c.assignment().ship();
        }
        if (c.rest() != null) {
            return c.rest().ship();
        }
        ShipBody on = CaptainsWhistleItem.shipOf(level, c);
        return on == null ? null : on.id();
    }
}

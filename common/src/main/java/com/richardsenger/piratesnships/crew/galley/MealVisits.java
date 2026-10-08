package com.richardsenger.piratesnships.crew.galley;

import com.richardsenger.piratesnships.crew.hammock.RestRules;
import com.richardsenger.piratesnships.crew.hammock.ShipBunks;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The crew's meals (CRW2, docs/design.md §7.4): when the clock passes a meal time ({@link MealRules}), every free crew
 * member of every loaded ship (not at a station, not in a hammock, not riding anything) takes a {@link MealSeat} beside
 * the nearest pantry aboard, or beside the nearest water barrel when the pantries have no room left, and stays there for
 * {@code crew.meals.meal_ticks} in the {@code work} pose ({@link CrewMember#isEating()}). An order during the meal seats
 * it at its station at once (the meal seat then removes itself); nightfall puts it into its hammock.
 * <p>
 * Nothing is eaten: the rations stay the per-day CR2 bookkeeping at dawn ({@code crew.upkeep.ShipDayTick}). A crew
 * member with no free spot beside any provisions block, or on a ship without one, stays where it is.
 * <p>
 * Like the station and hammock seats, the meal seat is taken at once, without a walk: the crew has no path finding on a
 * moving ship yet.
 */
public final class MealVisits {

    /** The day time of each level at its last tick, to see the clock pass a meal time. */
    private static final Map<ResourceKey<Level>, Long> LAST = new ConcurrentHashMap<>();

    private MealVisits() {
    }

    /** End of each level tick: a meal on every ship when the clock passes a meal time. */
    public static void onLevelTick(ServerLevel level) {
        long now = level.getDayTime();
        Long before = LAST.put(level.dimension(), now);
        if (before == null || !MealConfig.ENABLED.get() || level.dimensionType().hasFixedTime()) {
            return;
        }
        if (MealRules.mealStarts(before, now, MealConfig.times())) {
            serve(level);
        }
    }

    public static void onServerStopped() {
        LAST.clear();
    }

    /** A meal on every loaded ship of {@code level}. Public for the GameTests. */
    public static void serve(ServerLevel level) {
        for (ShipBody ship : List.copyOf(SableShips.all(level))) {
            serve(level, ship);
        }
    }

    /** A meal on {@code ship}: seats its free crew beside its provisions. Returns the crew members seated. */
    public static List<CrewMember> serve(ServerLevel level, ShipBody ship) {
        List<CrewMember> free = new ArrayList<>();
        for (CrewMember c : ShipBunks.crewOf(level, ship)) {
            if (isFree(c)) free.add(c);
        }
        if (free.isEmpty()) {
            return List.of();
        }
        List<BlockPos> pantries = new ArrayList<>();
        List<BlockPos> barrels = new ArrayList<>();
        for (BlockPos p : ship.plotBlocks()) {
            var be = level.getBlockEntity(p);
            if (be instanceof PantryBlockEntity) pantries.add(p.immutable());
            else if (be instanceof WaterBarrelBlockEntity) barrels.add(p.immutable());
        }
        long until = MealRules.mealEnd(level.getGameTime(), MealConfig.MEAL_TICKS.get());
        Set<BlockPos> taken = new HashSet<>();
        List<CrewMember> seated = new ArrayList<>();
        for (List<BlockPos> blocks : List.of(pantries, barrels)) {
            if (free.isEmpty()) break;
            seat(level, ship, blocks, free, taken, until, seated);
        }
        return seated;
    }

    /** Whether {@code crew} is free for a meal: not at a station, not asleep, not riding anything, not at a meal. */
    public static boolean isFree(CrewMember crew) {
        return crew.isAlive() && crew.assignment() == null && crew.rest() == null && !crew.isPassenger();
    }

    /** Seats as many of {@code free} as there are spots beside {@code blocks}, closest pairs first; removes them from {@code free}. */
    private static void seat(ServerLevel level, ShipBody ship, List<BlockPos> blocks, List<CrewMember> free, Set<BlockPos> taken,
                             long until, List<CrewMember> seated) {
        // a spot's key is its index in `spots`; `owner` is the provisions block it lies beside
        List<BlockPos> spots = new ArrayList<>();
        List<BlockPos> owner = new ArrayList<>();
        List<RestRules.Bed<Integer>> beds = new ArrayList<>();
        for (BlockPos block : blocks) {
            for (BlockPos spot : MealRules.spotsAround(block)) {
                if (taken.contains(spot) || !standable(level, spot) || !MealSeat.on(level, spot).isEmpty()) continue;
                taken.add(spot);
                Vec3 w = ship.toWorld(Vec3.atBottomCenterOf(spot));
                beds.add(new RestRules.Bed<>(spots.size(), w.x, w.y, w.z));
                spots.add(spot);
                owner.add(block);
            }
        }
        if (beds.isEmpty()) return;
        List<RestRules.Sleeper> diners = new ArrayList<>();
        for (CrewMember c : free) diners.add(new RestRules.Sleeper(c.getUUID(), c.getX(), c.getY(), c.getZ()));
        Map<UUID, Integer> pairs = RestRules.assign(beds, diners);
        for (CrewMember c : List.copyOf(free)) {
            Integer i = pairs.get(c.getUUID());
            if (i == null) continue;
            if (sitDown(level, c, owner.get(i), spots.get(i), until)) {
                free.remove(c);
                seated.add(c);
            }
        }
    }

    /** Puts {@code crew} on a meal seat on plot cell {@code spot} beside the provisions at {@code provisions}. */
    static boolean sitDown(ServerLevel level, CrewMember crew, BlockPos provisions, BlockPos spot, long until) {
        MealSeat seat = MealSeat.spawn(level, provisions, spot, until);
        crew.getNavigation().stop();
        if (!crew.startRiding(seat, true)) {
            seat.discard();
            return false;
        }
        seat.positionRider(crew); // place it now (Sable maps it to world space)
        return true;
    }

    /** A cell a crew member can stand in: no collision and no fluid in it and the cell above, a sturdy floor below. */
    private static boolean standable(ServerLevel level, BlockPos p) {
        return open(level, p) && open(level, p.above()) && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP);
    }

    private static boolean open(ServerLevel level, BlockPos p) {
        var state = level.getBlockState(p);
        return state.getCollisionShape(level, p).isEmpty() && state.getFluidState().isEmpty();
    }
}

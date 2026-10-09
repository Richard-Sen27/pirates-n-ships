package com.richardsenger.piratesnships.crew.walk;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.crew.galley.MealSeat;
import com.richardsenger.piratesnships.crew.galley.MealVisits;
import com.richardsenger.piratesnships.crew.hammock.CrewRest;
import com.richardsenger.piratesnships.crew.hammock.HammockSeat;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.seat.StationSeat;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Crew walk to their places (WALK1, docs/design.md §6): to the station they were ordered to, to a meal spot beside the
 * provisions, to their hammock at nightfall. The seat code of each place ({@link CrewStations}, {@link MealVisits},
 * {@link CrewRest}) asks {@link #begin} first and seats the crew member itself only when no walk starts; on arrival
 * (or after {@code crew.walk.timeout_ticks}, the fallback) the walk hands back to that seat code.
 * <p>
 * <b>Pathfinding on a ship</b> (sable-notes §6): Sable's {@code PathNavigationMixin#sable$createPath} pathfinds in the
 * ship's plot when the mob tracks a sub-level or the target lies in a plot. A target given in plot coordinates is used
 * as is (it is inside the ship's plot), a world target is turned into the plot through the ship's pose; the start is
 * the mob's position turned into the plot ({@code WalkNodeEvaluatorMixin}, {@code PathfindingContextMixin}, which do
 * that only while the mob <em>tracks</em> the ship). The path's nodes stay in plot coordinates and
 * {@code PathMixin} projects {@code getNextEntityPos}, {@code getNextNodePos} and {@code getNodePos} back to world
 * space with the ship's current pose, so a path keeps following a moving deck. Hence a walk only starts for a crew
 * member that stands on (tracks) or rides on the target's ship, and the target is always the plot cell.
 * <p>
 * Seated at once instead of walking: walking off ({@code crew.walk.enabled}), a mob without AI (it cannot move), a
 * crew member that is not on the target's ship (ashore, on another ship), one already within
 * {@code arrive_distance}, or no path at all. Distances are measured in ship space, so a moving ship does not count.
 */
public final class CrewWalk {

    /** Ticks between two path searches while the walker has no path (none found yet, or it ran out short). */
    static final int NO_PATH_RETRY_TICKS = 5;
    /** Pathfinding accuracy: the path may end on a cell next to the spot ({@code PathNavigation#createPath}). */
    private static final int ACCURACY = 1;

    private CrewWalk() {
    }

    /**
     * Starts a walk of {@code crew} to plot cell {@code spot} on ship {@code ship} for {@code purpose} at {@code anchor}
     * (station block, provisions block, hammock foot). Returns true when it walks (or already walks there): the caller
     * must not seat it now. False: the caller seats it at once, as before WALK1.
     */
    public static boolean begin(ServerLevel level, CrewMember crew, WalkTarget.Purpose purpose, UUID ship, BlockPos spot, BlockPos anchor) {
        WalkTarget current = crew.walk();
        if (current != null && current.goesTo(purpose, ship, anchor)) {
            return true;
        }
        if (current != null) {
            cancel(crew); // an order during a walk retargets it
        }
        if (!WalkConfig.ENABLED.get() || crew.isNoAi()) {
            return false;
        }
        ShipBody body = SableShips.byId(level, ship);
        ShipBody on = ShipEntities.standingOrRiding(crew);
        if (body == null || on == null || !on.id().equals(ship)) {
            return false; // a walk keeps to the ship: from ashore or another ship it is seated at once
        }
        if (distance(body, crew, spot) <= WalkConfig.ARRIVE_DISTANCE.get()) {
            return false;
        }
        Entity vehicle = crew.getVehicle();
        if (vehicle != null) {
            if (!(vehicle instanceof StationSeat || vehicle instanceof MealSeat || vehicle instanceof HammockSeat)) {
                return false; // riding something else of the ship: leave it there
            }
            crew.stopRiding(); // the seat removes itself; Sable keeps it tracking the ship until it lands on the deck
        }
        if (crew.onGround() && path(crew, spot) == null) {
            Constants.LOG.debug("Crew {} has no path to {} {} on {}: seated at once", crew.getUUID(), purpose, anchor, ship);
            return false;
        }
        crew.setWalk(new WalkTarget(purpose, ship, spot, anchor, level.getGameTime()));
        return true;
    }

    /** Whether {@code crew} walks to {@code anchor} of {@code ship} for {@code purpose}. */
    public static boolean walksTo(CrewMember crew, WalkTarget.Purpose purpose, UUID ship, BlockPos anchor) {
        WalkTarget t = crew.walk();
        return t != null && t.goesTo(purpose, ship, anchor);
    }

    /** Whether {@code crew} walks somewhere for {@code purpose}. */
    public static boolean walksFor(CrewMember crew, WalkTarget.Purpose purpose) {
        WalkTarget t = crew.walk();
        return t != null && t.purpose() == purpose;
    }

    /** Ends the walk without seating (an order elsewhere, the station released, dawn). */
    public static void cancel(CrewMember crew) {
        if (crew.walk() != null) {
            crew.setWalk(null);
            crew.getNavigation().stop();
        }
    }

    /**
     * {@code Stations.Attendance}: the occupant of {@code ref} is there unless it is a crew member still walking to it;
     * the station's work waits for it.
     */
    public static boolean present(ServerLevel level, StationRef ref, UUID occupant) {
        return !(level.getEntity(occupant) instanceof CrewMember crew && walksTo(crew, WalkTarget.Purpose.STATION, ref.ship(), ref.pos()));
    }

    /** Every server tick of a walking crew member (after its AI and movement): arrive, give up, or look for a path. */
    public static void tick(ServerLevel level, CrewMember crew) {
        WalkTarget t = crew.walk();
        if (t == null) {
            return;
        }
        ShipBody body = SableShips.byId(level, t.ship());
        if (body == null || crew.isPassenger()) {
            cancel(crew); // the ship is unloaded or gone (the station's own checks take over), or it was seated elsewhere
            return;
        }
        if (!WalkConfig.ENABLED.get()) {
            finish(level, crew, t);
            return;
        }
        long elapsed = level.getGameTime() - t.started();
        double d = distance(body, crew, t.spot());
        switch (WalkRules.step(d, WalkConfig.ARRIVE_DISTANCE.get(), elapsed, WalkConfig.TIMEOUT_TICKS.get())) {
            case ARRIVED -> finish(level, crew, t);
            case TIMED_OUT -> {
                Constants.LOG.debug("Crew {} did not reach {} {} in {} ticks ({} blocks short): seated there",
                        crew.getUUID(), t.purpose(), t.anchor(), elapsed, String.format("%.1f", d));
                finish(level, crew, t);
            }
            case WALK -> {
                ShipBody on = ShipEntities.standingOrRiding(crew);
                if (on == null || !on.id().equals(t.ship()) || !crew.onGround()) {
                    return; // off the deck for a moment (a jump, a fall): the timeout catches a walker that stays off
                }
                boolean hasPath = !crew.getNavigation().isDone();
                if (!hasPath && elapsed % NO_PATH_RETRY_TICKS != 0) {
                    return;
                }
                boolean moving = WalkRules.moving(body.linearVelocity().length(), body.angularVelocity().length());
                if (WalkRules.repath(hasPath, moving, elapsed, WalkConfig.REPATH_TICKS.get()) && path(crew, t.spot()) == null) {
                    Constants.LOG.debug("Crew {} lost its path to {} {}: seated there", crew.getUUID(), t.purpose(), t.anchor());
                    finish(level, crew, t);
                }
            }
        }
    }

    /** The walk ends at its place (arrived or the fallback): the place's seat code runs. */
    private static void finish(ServerLevel level, CrewMember crew, WalkTarget t) {
        cancel(crew);
        switch (t.purpose()) {
            case STATION -> CrewStations.arrive(level, crew, t.ship(), t.anchor());
            case MEAL -> MealVisits.arrive(level, crew, t.anchor(), t.spot());
            case HAMMOCK -> CrewRest.arrive(level, crew, t.ship(), t.anchor());
        }
    }

    /**
     * A fresh path to plot cell {@code spot} at {@code crew.walk.speed}, or null. The old path is dropped first: Sable's
     * {@code createPath} hands back the current path while it is not done and has the same target.
     */
    private static @Nullable Path path(CrewMember crew, BlockPos spot) {
        PathNavigation nav = crew.getNavigation();
        nav.stop();
        Path p = nav.createPath(spot, ACCURACY);
        if (p != null) {
            nav.moveTo(p, WalkConfig.SPEED.get()); // false when it already stands at the path's end: kept as a path found
        }
        return p;
    }

    /** Distance in blocks, in ship space, from the crew member's feet to the bottom centre of plot cell {@code spot}. */
    static double distance(ShipBody ship, CrewMember crew, BlockPos spot) {
        Vec3 local = ship.toPlot(crew.position());
        return local.distanceTo(Vec3.atBottomCenterOf(spot));
    }
}

package com.richardsenger.piratesnships.crew.hammock;

import com.richardsenger.piratesnships.crew.morale.CrewMorale;
import com.richardsenger.piratesnships.crew.morale.MoraleRules;
import com.richardsenger.piratesnships.crew.morale.NightOutcome;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.crew.upkeep.ShipDayTick;
import com.richardsenger.piratesnships.crew.walk.CrewWalk;
import com.richardsenger.piratesnships.crew.walk.WalkTarget;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * Crew sleep and the hammock rule (HM1, docs/design.md §7.1):
 * <ul>
 *   <li><b>Nightfall</b> ({@link RestRules#isNight} turns true, once per night and level): on every ship, crew at a
 *       station stay on duty ({@link NightOutcome#ON_DUTY}); every free crew member takes the nearest free hammock
 *       ({@link RestRules#assign}) and lies in it on a {@link HammockSeat} ({@link NightOutcome#SLEPT}); the rest find
 *       none ({@link NightOutcome#NO_HAMMOCK}). WALK1: on the ship it walks to the hammock first
 *       ({@code crew.walk.CrewWalk}, {@link #arrive}).</li>
 *   <li><b>Dawn</b> (per crew member, the first tick its night is over): it gets up, the hammock rule changes its
 *       morale ({@link MoraleRules#dawnDelta} through {@link CrewMorale#adjust}), and one without a hammock grumbles.</li>
 *   <li><b>An order</b> at night ({@link CrewStations#assign}, by hand or by the job board) gets a sleeper up at once:
 *       it counts as on duty for that night.</li>
 * </ul>
 * With {@code crew.morale.enabled} off nobody is sent to a hammock and morale stays frozen; sleepers still get up.
 * <p>
 * CR2: the ship day tick of crew upkeep ({@link ShipDayTick}: provisions, wages, desertion, mutiny) runs at the same
 * dawn <em>before</em> the hammock rule: every crew tick and every level tick first lets it look at the clock.
 */
public final class CrewRest {

    /** Whether the last tick of each level was night, to see nightfall once. */
    private static final Map<ResourceKey<Level>, Boolean> NIGHT = new ConcurrentHashMap<>();

    private CrewRest() {
    }

    /** Whether it is night in {@code level} for the crew (never in dimensions with a fixed time). */
    public static boolean isNight(ServerLevel level) {
        return !level.dimensionType().hasFixedTime() && RestRules.isNight(level.getDayTime());
    }

    /** End of each level tick: nightfall once per night. */
    public static void onLevelTick(ServerLevel level) {
        ShipDayTick.observe(level);
        boolean night = isNight(level);
        Boolean before = NIGHT.put(level.dimension(), night);
        if (night && !Boolean.TRUE.equals(before)) {
            nightfall(level);
        }
    }

    public static void onServerStopped() {
        NIGHT.clear();
        ShipDayTick.onServerStopped();
    }

    /** Nightfall in {@code level}: every ship's crew turns in. Public for the GameTests. */
    public static void nightfall(ServerLevel level) {
        if (!CrewMorale.enabled()) {
            return;
        }
        for (ShipBody ship : SableShips.all(level)) {
            turnIn(level, ship);
        }
    }

    /** The crew of {@code ship}: on duty, into a free hammock, or without one. */
    public static void turnIn(ServerLevel level, ShipBody ship) {
        List<CrewMember> free = new ArrayList<>();
        Set<BlockPos> taken = new HashSet<>();
        for (CrewMember c : ShipBunks.crewOf(level, ship)) {
            if (c.assignment() != null) {
                c.setNightOutcome(NightOutcome.ON_DUTY);
            } else if (c.rest() != null) {
                taken.add(c.rest().foot()); // already in its hammock (e.g. loaded mid-night)
            } else if (CrewWalk.walksFor(c, WalkTarget.Purpose.HAMMOCK)) {
                taken.add(c.walk().anchor()); // on its way to its hammock (WALK1)
            } else {
                free.add(c);
            }
        }
        if (free.isEmpty()) {
            return;
        }
        List<RestRules.Bed<BlockPos>> beds = new ArrayList<>();
        for (BlockPos foot : ShipBunks.freeHammocks(level, ship)) { // SLP1: a sleeping player's hammock is taken too
            if (taken.contains(foot)) continue;
            Vec3 w = ship.toWorld(Vec3.atCenterOf(foot));
            beds.add(new RestRules.Bed<>(foot, w.x, w.y, w.z));
        }
        List<RestRules.Sleeper> sleepers = new ArrayList<>();
        for (CrewMember c : free) {
            sleepers.add(new RestRules.Sleeper(c.getUUID(), c.getX(), c.getY(), c.getZ()));
        }
        Map<UUID, BlockPos> pairs = RestRules.assign(beds, sleepers);
        for (CrewMember c : free) {
            BlockPos foot = pairs.get(c.getUUID());
            if (foot != null && lieDown(level, c, ship.id(), foot)) {
                c.setNightOutcome(NightOutcome.SLEPT);
            } else {
                c.setNightOutcome(NightOutcome.NO_HAMMOCK);
            }
        }
    }

    /**
     * Sends {@code crew} to the hammock whose foot is at plot position {@code foot}: it walks there over the deck
     * (WALK1, {@code crew.walk.CrewWalk}) and lies down on arrival, or lies down at once. True when it is on its way or
     * lies in it.
     */
    static boolean lieDown(ServerLevel level, CrewMember crew, UUID ship, BlockPos foot) {
        if (!isHammockFoot(level, foot)) {
            return false;
        }
        if (CrewWalk.begin(level, crew, WalkTarget.Purpose.HAMMOCK, ship, foot, foot)) {
            return true;
        }
        return lieDownNow(level, crew, ship, foot);
    }

    /**
     * WALK1: a walk to the hammock at {@code foot} ended (arrived, or the fallback): it lies down if it is still night
     * and the hammock is there and free; else its night is one without a hammock.
     */
    public static void arrive(ServerLevel level, CrewMember crew, UUID ship, BlockPos foot) {
        if (!isNight(level) || crew.rest() != null || crew.assignment() != null) {
            return;
        }
        boolean free = HammockSeat.at(level, foot).stream().noneMatch(Entity::isVehicle);
        if (!free || !lieDownNow(level, crew, ship, foot)) {
            crew.setNightOutcome(NightOutcome.NO_HAMMOCK);
        }
    }

    private static boolean isHammockFoot(ServerLevel level, BlockPos foot) {
        BlockState state = level.getBlockState(foot);
        return state.getBlock() instanceof HammockBlock && state.getValue(HammockBlock.PART) == BedPart.FOOT;
    }

    /** Puts {@code crew} into the hammock whose foot is at plot position {@code foot} at once. */
    static boolean lieDownNow(ServerLevel level, CrewMember crew, UUID ship, BlockPos foot) {
        if (!isHammockFoot(level, foot)) {
            return false;
        }
        BlockState state = level.getBlockState(foot);
        Direction facing = state.getValue(HammockBlock.FACING);
        HammockSeat seat = HammockSeat.spawn(level, foot, facing);
        crew.stopRiding();
        crew.getNavigation().stop();
        if (!crew.startRiding(seat, true)) {
            seat.discard();
            return false;
        }
        seat.positionRider(crew); // place it now (Sable maps it to world space)
        crew.setRest(new HammockRef(ship, foot));
        orient(level, crew, foot, true);
        return true;
    }

    /**
     * HM2: turns {@code crew} along its hammock (foot at plot or world position {@code foot}): body, head and view face
     * the foot half ({@link SleepAxis}), turned by the ship's current orientation when the hammock is in a ship's plot.
     * {@code snap}: also sets the previous tick's angles, so lying down does not sweep round on the client.
     */
    public static void orient(ServerLevel level, CrewMember crew, BlockPos foot, boolean snap) {
        BlockState state = level.getBlockState(foot);
        if (!(state.getBlock() instanceof HammockBlock)) {
            return;
        }
        ShipBody ship = SableShips.containing(level, foot);
        float yaw = SleepAxis.continuous(crew.getYRot(), SleepAxis.yaw(state.getValue(HammockBlock.FACING),
                ship == null ? null : ship.orientation()));
        crew.setYRot(yaw);
        crew.setYHeadRot(yaw);
        crew.setYBodyRot(yaw);
        crew.setXRot(0);
        if (snap) {
            crew.yRotO = yaw;
            crew.yHeadRotO = yaw;
            crew.yBodyRotO = yaw;
            crew.xRotO = 0;
        }
    }

    /**
     * Gets {@code crew} out of its hammock (seat removed, rest cleared). {@code onDuty}: it got up for an order, which
     * makes the night one on duty.
     */
    public static void getUp(CrewMember crew, boolean onDuty) {
        crew.setRest(null);
        if (CrewWalk.walksFor(crew, WalkTarget.Purpose.HAMMOCK)) {
            CrewWalk.cancel(crew); // on its way to the hammock (WALK1): it stops where it is
        }
        if (crew.getVehicle() instanceof HammockSeat seat) {
            crew.stopRiding();
            seat.discard();
        }
        if (onDuty && crew.nightOutcome() != NightOutcome.NONE) {
            crew.setNightOutcome(NightOutcome.ON_DUTY);
        }
    }

    /** Dawn for one crew member: it gets up and the hammock rule settles its night. */
    public static void dawn(ServerLevel level, CrewMember crew) {
        NightOutcome night = crew.nightOutcome();
        getUp(crew, false);
        crew.setNightOutcome(NightOutcome.NONE);
        int delta = MoraleRules.dawnDelta(CrewMorale.settings(), night);
        if (delta != 0) {
            CrewMorale.adjust(crew, delta, "hammock rule: " + night.id());
        }
        if (night == NightOutcome.NO_HAMMOCK && CrewMorale.enabled()) {
            CrewStations.say(level, crew, Component.translatable(CrewInfo.KEY_NO_HAMMOCK));
        }
    }

    /**
     * Every server tick of a crew member (after its AI and movement): dawn, or keep it in its hammock, lying along it
     * (HM2: recomputed every tick, so it turns with the ship).
     */
    public static void tick(ServerLevel level, CrewMember crew) {
        ShipDayTick.observe(level); // CR2: the ship day tick comes before the hammock rule at dawn
        if (crew.isRemoved()) {
            return; // deserted or mutinied at this dawn
        }
        if ((crew.nightOutcome() != NightOutcome.NONE || crew.rest() != null) && !isNight(level)) {
            dawn(level, crew);
            return;
        }
        HammockRef rest = crew.rest();
        if (rest != null && crew.tickCount % StationConfig.SEAT_CHECK_INTERVAL.get() == 0
                && !(crew.getVehicle() instanceof HammockSeat seat && seat.foot().equals(rest.foot()))) {
            getUp(crew, false); // the hammock is gone, or it was taken off its seat: the night stays as it was
            return;
        }
        if (rest != null && crew.getVehicle() instanceof HammockSeat seat && seat.foot().equals(rest.foot())) {
            orient(level, crew, rest.foot(), false);
        }
    }
}

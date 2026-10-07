package com.richardsenger.piratesnships.crew.hammock;

import com.richardsenger.piratesnships.crew.morale.CrewMorale;
import com.richardsenger.piratesnships.crew.morale.MoraleRules;
import com.richardsenger.piratesnships.crew.morale.NightOutcome;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
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
 *       none ({@link NightOutcome#NO_HAMMOCK}).</li>
 *   <li><b>Dawn</b> (per crew member, the first tick its night is over): it gets up, the hammock rule changes its
 *       morale ({@link MoraleRules#dawnDelta} through {@link CrewMorale#adjust}), and one without a hammock grumbles.</li>
 *   <li><b>An order</b> at night ({@link CrewStations#assign}, by hand or by the job board) gets a sleeper up at once:
 *       it counts as on duty for that night.</li>
 * </ul>
 * With {@code crew.morale.enabled} off nobody is sent to a hammock and morale stays frozen; sleepers still get up.
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
        boolean night = isNight(level);
        Boolean before = NIGHT.put(level.dimension(), night);
        if (night && !Boolean.TRUE.equals(before)) {
            nightfall(level);
        }
    }

    public static void onServerStopped() {
        NIGHT.clear();
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
            } else {
                free.add(c);
            }
        }
        if (free.isEmpty()) {
            return;
        }
        List<RestRules.Bed<BlockPos>> beds = new ArrayList<>();
        for (BlockPos foot : ShipBunks.hammocks(level, ship)) {
            if (taken.contains(foot) || !HammockSeat.at(level, foot).isEmpty()) continue;
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

    /** Puts {@code crew} into the hammock whose foot is at plot position {@code foot}. */
    static boolean lieDown(ServerLevel level, CrewMember crew, UUID ship, BlockPos foot) {
        BlockState state = level.getBlockState(foot);
        if (!(state.getBlock() instanceof HammockBlock) || state.getValue(HammockBlock.PART) != BedPart.FOOT) {
            return false;
        }
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
        return true;
    }

    /**
     * Gets {@code crew} out of its hammock (seat removed, rest cleared). {@code onDuty}: it got up for an order, which
     * makes the night one on duty.
     */
    public static void getUp(CrewMember crew, boolean onDuty) {
        crew.setRest(null);
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

    /** Every server tick of a crew member: dawn, or keep it in its hammock. */
    public static void tick(ServerLevel level, CrewMember crew) {
        if ((crew.nightOutcome() != NightOutcome.NONE || crew.rest() != null) && !isNight(level)) {
            dawn(level, crew);
            return;
        }
        HammockRef rest = crew.rest();
        if (rest != null && crew.tickCount % StationConfig.SEAT_CHECK_INTERVAL.get() == 0
                && !(crew.getVehicle() instanceof HammockSeat seat && seat.foot().equals(rest.foot()))) {
            getUp(crew, false); // the hammock is gone, or it was taken off its seat: the night stays as it was
        }
    }
}

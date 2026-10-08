package com.richardsenger.piratesnships.worldsim.raid;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.cannon.npc.Gunnery;
import com.richardsenger.piratesnships.combat.cannon.npc.GunneryState;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.sailing.block.CapstanBlock;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.ship.ShipAnchor;
import com.richardsenger.piratesnships.sailing.ship.ShipControls;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.helm.CourseOrder;
import com.richardsenger.piratesnships.station.helm.HelmCourses;
import com.richardsenger.piratesnships.station.jobs.JobBoard;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import com.richardsenger.piratesnships.worldsim.faction.FactionEvent;
import com.richardsenger.piratesnships.worldsim.faction.Factions;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.materialize.Materializer;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageCrew;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The raiders at the settlement (WS5, design.md §10.4). Every {@value #INTERVAL} ticks, for each real raiding ship:
 *
 * <ul>
 *   <li>its gun crews fire at will ({@code Gunnery}, WS4a);</li>
 *   <li><b>landing:</b> within {@code landing_distance} of the berth the ship's fighters go ashore onto the standing
 *       spots nearest the berth (within {@value #SHORE_RADIUS} blocks; none found: they stay aboard) and stop being
 *       stationary, so they fight the garrison and players by the mobs' own hostility ({@code mobs.factions_fight},
 *       {@code mobs.pirates_hostile}). The ship furls its sails, drops its anchor and its helmsman takes the course home
 *       (the approach reversed), which holds it in place while the anchor is down and keeps WS3b from giving it the old
 *       course again;</li>
 *   <li><b>withdrawal:</b> when no raider of the ship is alive ashore (repelled) or {@code raid_duration_ticks} after
 *       the landing (the raiders held the shore: succeeded), the survivors are discarded, the anchor comes up, the sails
 *       are hoisted and the ship sails home.</li>
 * </ul>
 *
 * When every ship of a raid is done (withdrawn, sunk, captured, turned away) the raid reports {@code RAID_REPELLED}
 * or {@code RAID_SUCCEEDED} if any ship fought, the settlement's cooldown is set from that day and the end is
 * announced. Raiders do not loot.
 */
public final class RaidLanding {

    static final int INTERVAL = 10;
    static final int SHORE_RADIUS = 16;
    /** Raiders this far around the settlement's box still count as ashore. */
    static final int ASHORE_MARGIN = 48;

    private RaidLanding() {
    }

    /** Server tick: every {@value #INTERVAL} ticks each ship of each raid (GameTest raids are left to their tests). */
    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL != 0) return;
        for (RaidData.ActiveRaid raid : RaidData.get(server).raids()) {
            if (raid.port().getPath().startsWith("gametest/")) continue;
            for (UUID id : raid.ships().keySet()) update(server, id);
        }
    }

    /** One update of raiding voyage {@code id} (also the GameTest entry). */
    public static void update(MinecraftServer server, UUID id) {
        Optional<RaidData.ActiveRaid> found = RaidData.get(server).raidOf(id);
        if (found.isEmpty()) return;
        RaidData.ActiveRaid raid = found.get();
        RaidData.ShipRaid ship = raid.ships().get(id);
        if (ship.phase() == RaidData.Phase.DONE) return;
        Optional<Voyage> v = Voyages.get(server, id);
        if (v.isEmpty()) {
            resolve(server, id, false, false); // ended without us hearing it
            return;
        }
        if (v.get().state() != Voyage.State.MATERIALISED || v.get().shipId().isEmpty()) return;
        ServerLevel level = server.getLevel(raid.dimension());
        ShipBody body = level == null ? null : SableShips.byId(level, v.get().shipId().get());
        if (body == null) return;
        if (Gunnery.state(body.id()).isOff()) Gunnery.set(level, body, GunneryState.AT_WILL);
        long now = level.getGameTime();
        if (ship.phase() == RaidData.Phase.APPROACH) {
            Vec3 c = Materializer.centre(body);
            if (RaidRules.withinLanding(c.x, c.z, raid.target().getX() + 0.5, raid.target().getZ() + 0.5,
                    RaidConfig.LANDING_DISTANCE.get())) {
                land(server, level, raid, v.get(), body);
            }
        } else if (ship.phase() == RaidData.Phase.LANDED) {
            List<LivingEntity> ashore = ashore(level, raid, id);
            if (ashore.isEmpty()) {
                withdraw(server, level, raid, v.get(), body, false);
            } else if (now - ship.landedAt() >= RaidConfig.RAID_DURATION_TICKS.get()) {
                withdraw(server, level, raid, v.get(), body, true);
            }
        }
    }

    // ------------------------------------------------------------------ landing

    static void land(MinecraftServer server, ServerLevel level, RaidData.ActiveRaid raid, Voyage v, ShipBody ship) {
        List<LivingEntity> fighters = VoyageCrew.alive(level, ship, v.id(), VoyageCrew.FIGHTER_TAG);
        List<BlockPos> spots = shore(level, raid.target(), fighters.size());
        for (int i = 0; i < fighters.size(); i++) {
            LivingEntity f = fighters.get(i);
            if (!spots.isEmpty()) {
                BlockPos s = spots.get(i % spots.size());
                f.stopRiding();
                f.teleportTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
            }
            if (f instanceof SeafarerMob m) m.setStationary(false);
        }
        // the course home holds the helm (WS3b would set the old course again on a ship without one)
        Vec3 c = Materializer.centre(ship);
        List<Lane.Point> home = RaidRules.home(v.waypoints(), c.x, c.z);
        Voyage homeward = v.withRoute(v.to(), v.from(), home);
        Voyages.update(server, homeward);
        List<Vec3> points = new ArrayList<>();
        for (int i = 1; i < home.size(); i++) points.add(home.get(i).at(level.getSeaLevel()));
        HelmCourses.set(level, ship, new CourseOrder(points, false));
        JobBoard.post(level, ship, SailOrder.FURL);
        JobBoard.pass(level, ship.id());
        boolean anchored = anchor(level, ship, true);
        RaidData data = RaidData.get(server);
        data.put(data.raidOf(v.id()).orElse(raid).with(v.id(), new RaidData.ShipRaid(RaidData.Phase.LANDED, level.getGameTime()))
                .withResult(true, false));
        Constants.LOG.debug("Raid on {}: voyage {} landed {} fighter(s) on {} spot(s), anchor {}", raid.port(), v.shortId(),
                fighters.size(), spots.size(), anchored);
    }

    /**
     * Up to {@code count} standing spots (feet positions) on land nearest {@code target} within {@value #SHORE_RADIUS}
     * blocks: a sturdy dry top block with two free blocks above it, in a loaded chunk. Fewer when the shore has fewer.
     */
    public static List<BlockPos> shore(ServerLevel level, BlockPos target, int count) {
        record Spot(BlockPos feet, double d) {
        }
        List<Spot> spots = new ArrayList<>();
        for (int dx = -SHORE_RADIUS; dx <= SHORE_RADIUS; dx++) {
            for (int dz = -SHORE_RADIUS; dz <= SHORE_RADIUS; dz++) {
                if (dx * dx + dz * dz > SHORE_RADIUS * SHORE_RADIUS) continue;
                int x = target.getX() + dx, z = target.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (!standable(level, feet)) continue;
                double dy = feet.getY() - target.getY();
                spots.add(new Spot(feet, dx * dx + dz * dz + dy * dy));
            }
        }
        return spots.stream().sorted(Comparator.comparingDouble(Spot::d)).limit(Math.max(0, count)).map(Spot::feet).toList();
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos floor = feet.below();
        BlockState f = level.getBlockState(floor);
        if (!f.getFluidState().isEmpty() || !f.isFaceSturdy(level, floor, Direction.UP)) return false;
        for (BlockPos p : new BlockPos[]{feet, feet.above()}) {
            BlockState s = level.getBlockState(p);
            if (!s.getFluidState().isEmpty() || !s.getCollisionShape(level, p).isEmpty()) return false;
        }
        return true;
    }

    /** Living fighters of {@code voyage} at the settlement (the box plus {@value #ASHORE_MARGIN} blocks). */
    public static List<LivingEntity> ashore(ServerLevel level, RaidData.ActiveRaid raid, UUID voyage) {
        String tag = VoyageCrew.voyageTag(voyage);
        AABB box = AABB.of(raid.area()).inflate(ASHORE_MARGIN);
        return level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e.getTags().contains(tag) && e.getTags().contains(VoyageCrew.FIGHTER_TAG));
    }

    // ------------------------------------------------------------------ withdrawal and the end of a raid

    static void withdraw(MinecraftServer server, ServerLevel level, RaidData.ActiveRaid raid, Voyage v, ShipBody ship, boolean held) {
        int discarded = 0;
        for (LivingEntity e : ashore(level, raid, v.id())) {
            e.discard();
            discarded++;
        }
        anchor(level, ship, false);
        JobBoard.post(level, ship, SailOrder.HOIST);
        JobBoard.pass(level, ship.id());
        Voyages.get(server, v.id()).ifPresent(r -> Voyages.update(server, r.withCrew(r.crew(), 0)));
        Constants.LOG.debug("Raid on {}: voyage {} withdraws ({}, {} survivor(s) gone)", raid.port(), v.shortId(),
                held ? "held the shore" : "repelled", discarded);
        resolve(server, v.id(), true, held);
    }

    /**
     * Marks raiding voyage {@code id} done ({@code fought}: it landed or was lost in a fight; {@code succeeded}: its
     * raiders held the shore). When the raid's last ship is done the raid ends: the world event if any ship fought, the
     * cooldown from today, the announcement.
     */
    public static void resolve(MinecraftServer server, UUID id, boolean fought, boolean succeeded) {
        RaidData data = RaidData.get(server);
        Optional<RaidData.ActiveRaid> found = data.raidOf(id);
        if (found.isEmpty()) return;
        RaidData.ActiveRaid raid = found.get().with(id, new RaidData.ShipRaid(RaidData.Phase.DONE, found.get().ships().get(id).landedAt()))
                .withResult(fought, succeeded);
        if (!raid.done()) {
            data.put(raid);
            return;
        }
        data.remove(raid.port());
        RaidRules.Outcome outcome = RaidRules.outcome(raid.fought(), raid.succeeded());
        if (outcome != RaidRules.Outcome.NONE) {
            Factions.report(server, outcome == RaidRules.Outcome.SUCCEEDED ? FactionEvent.RAID_SUCCEEDED : FactionEvent.RAID_REPELLED);
            data.setLastRaidDay(raid.port(), Factions.currentDay(server));
        }
        ServerLevel level = server.getLevel(raid.dimension());
        if (level != null) RaidAnnouncer.ended(level, raid.port(), raid.area(), outcome);
        Constants.LOG.debug("Raid on {} ended: {}", raid.port(), outcome);
    }

    /** {@code Voyages.onEnd}: a raiding ship sunk or captured counts as beaten off; one cancelled or lost as gone. */
    public static void onVoyageEnded(MinecraftServer server, Voyage voyage, VoyageEnd reason) {
        Optional<RaidData.ActiveRaid> raid = RaidData.get(server).raidOf(voyage.id());
        if (raid.isEmpty() || raid.get().ships().get(voyage.id()).phase() == RaidData.Phase.DONE) return;
        boolean beaten = reason == VoyageEnd.SUNK || reason == VoyageEnd.CAPTURED;
        resolve(server, voyage.id(), beaten, false);
    }

    /** Drops ({@code down}) or raises the anchor at the ship's first capstan. False without a capstan or anchors off. */
    static boolean anchor(ServerLevel level, ShipBody ship, boolean down) {
        BlockPos capstan = capstan(level, ship);
        SailingRuntime rt = SailingRuntimes.getOrCreate(ship);
        if (capstan == null || rt == null) return false;
        ShipAnchor a = rt.anchor();
        AnchorState.Phase phase = a == null ? AnchorState.Phase.RAISED : a.state().phase();
        boolean out = phase == AnchorState.Phase.DROPPING || phase == AnchorState.Phase.HOLDING;
        if (down == out) return true;
        ShipControls.useCapstan(level, capstan);
        return true;
    }

    private static @Nullable BlockPos capstan(ServerLevel level, ShipBody ship) {
        for (BlockPos p : ship.plotBlocks()) {
            if (level.getBlockState(p).getBlock() instanceof CapstanBlock) return p;
        }
        return null;
    }
}

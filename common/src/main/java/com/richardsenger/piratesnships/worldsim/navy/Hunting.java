package com.richardsenger.piratesnships.worldsim.navy;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.cannon.CannonStation.CannonOrder;
import com.richardsenger.piratesnships.combat.cannon.npc.Gunnery;
import com.richardsenger.piratesnships.combat.cannon.npc.GunneryState;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.decor.flag.FlagReading;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.helm.CourseOrder;
import com.richardsenger.piratesnships.station.helm.HelmCourses;
import com.richardsenger.piratesnships.station.jobs.JobBoard;
import com.richardsenger.piratesnships.worldsim.lane.BiomeSeaGrid;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.lane.SeaGrid;
import com.richardsenger.piratesnships.worldsim.materialize.Materializer;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageConfig;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The navy's hunt (WS4b, design.md §10.4 "Navy patrols"): every voyage check, each PATROL looks for a hunted player's
 * ship ({@link HuntRules}) within {@code hunt_radius} and chases it.
 *
 * <ul>
 *   <li><b>Abstract</b> ({@link #updateAbstract}, through {@link PatrolPlanner#update}): the record gets the quarry as
 *       its {@code pursuit} and a straight route toward the point {@code standoff_distance} short of it, capped to open
 *       sea through the lane grid ({@link PatrolRoutes#pursuit}); the scheduler moves it as usual. The route it left
 *       is kept in {@link NavyData}.</li>
 *   <li><b>Materialised</b> ({@link #updateMaterialised}, on the server tick with the voyage checks): the helmsman gets
 *       a looping course on a ring of {@code standoff_distance} around the quarry ({@link PatrolRoutes#ring}), given anew
 *       every {@code course_interval_ticks} when the quarry moved; the gun crews get the quarry as their target
 *       ({@link Gunnery#set}, WS4a), and unmanned guns are posted on the job board for free hands every
 *       {@code course_interval_ticks}. The record follows the ship.</li>
 *   <li><b>Ending</b> ({@link HuntRules#judge}): a quarry that struck its colours is shadowed with silent guns for
 *       {@code surrender_linger_ticks}; a quarry lost, out of range, no longer hunted, or out of contact for
 *       {@code give_up_ticks} is left. The patrol then returns to the nearest point of its route
 *       ({@link PatrolRoutes#rejoin}); a real ship's lane course is given again by the materialiser.</li>
 * </ul>
 *
 * GameTest voyages ({@code gametest/…} origins) are left to their tests. The ship engagement is in memory; the chase
 * itself is saved.
 */
public final class Hunting {

    static final String KEY = "message." + Constants.MOD_ID + ".navy.";
    /** Ring waypoints a patrol ship circles its quarry on. */
    static final int RING_POINTS = 4;
    /** A lane grid holding more cached cells than this is dropped and rebuilt (memory bound). */
    private static final long MAX_GRID_SAMPLES = 500_000;

    /** A materialised patrol's engagement: the course it was given last and whether its guns were manned. */
    private static final class Engaged {
        final UUID ship;
        @Nullable CourseOrder order;
        @Nullable Vec3 courseAt;
        long lastCourse = Long.MIN_VALUE;
        long lastGunPost = Long.MIN_VALUE / 2;

        Engaged(UUID ship) {
            this.ship = ship;
        }
    }

    private static final Map<UUID, Engaged> ENGAGED = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, BiomeSeaGrid> GRIDS = new ConcurrentHashMap<>();

    private Hunting() {
    }

    /** Test voyages ({@code gametest/…} origin) are driven by their GameTests only. */
    public static boolean isTest(Voyage v) {
        return v.from().getPath().startsWith("gametest/");
    }

    // ------------------------------------------------------------------ the check

    /** {@code SERVER_TICK_END}: with every voyage check, each materialised patrol hunts once. */
    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % VoyageConfig.TICK_INTERVAL.get() != 0) return;
        for (Voyage v : Voyages.active(server)) {
            if (v.kind() == VoyageKind.PATROL && v.state() == Voyage.State.MATERIALISED && !isTest(v)) {
                updateMaterialised(server, v.id());
            }
        }
        ENGAGED.keySet().removeIf(id -> Voyages.get(server, id).map(v -> v.state() != Voyage.State.MATERIALISED).orElse(true));
    }

    // ------------------------------------------------------------------ abstract patrols

    /**
     * One check of an abstract (SAILING) patrol: it sights a quarry, or its chase goes on or ends. Returns the voyage to
     * keep (also the GameTest entry). Other voyages are returned unchanged.
     */
    public static Voyage updateAbstract(MinecraftServer server, Voyage v) {
        if (v.kind() != VoyageKind.PATROL || v.state() != Voyage.State.SAILING) return v;
        ServerLevel level = Materializer.levelOf(server, v);
        Lane.Position pos = v.position();
        long now = now(server);
        HuntRules.Params params = NavyConfig.params();
        double standoff = NavyConfig.STANDOFF_DISTANCE.get();
        if (v.pursuit().isEmpty()) {
            if (!NavyConfig.active()) return v;
            Optional<HuntRules.Candidate> sighted = HuntRules.pick(pos.x(), pos.z(), candidates(level, pos.x(), pos.z(), params.huntRadius()), params);
            if (sighted.isEmpty()) return v;
            HuntRules.Candidate c = sighted.get();
            Voyage chasing = start(server, v, c, now);
            return chasing.withRoute(v.from(), v.to(), PatrolRoutes.pursuit(grid(level), pos.x(), pos.z(), c.x(), c.z(), standoff));
        }
        UUID targetId = v.pursuit().get();
        NavyData.Pursuit p = pursuitOf(server, v, targetId, now);
        HuntRules.Candidate target = candidate(level, targetId);
        HuntRules.Judgement j = judge(target, pos.x(), pos.z(), now, p, params);
        if (j.step() == HuntRules.Step.RESUME) return resume(server, v, p, pos.x(), pos.z(), j.reason());
        keep(server, p, j);
        return v.withRoute(v.from(), v.to(), PatrolRoutes.pursuit(grid(level), pos.x(), pos.z(), target.x(), target.z(), standoff));
    }

    /** At the end of its route: a patrol in a chase stays at sea (it waits at the coast it was stopped by). */
    public static Optional<Voyage> onArrive(MinecraftServer server, Voyage v) {
        return v.pursuit().isPresent() ? Optional.of(v) : Optional.empty();
    }

    // ------------------------------------------------------------------ materialised patrols

    /** One check of materialised patrol {@code voyageId}: sight, chase, shadow or break off (also the GameTest entry). */
    public static void updateMaterialised(MinecraftServer server, UUID voyageId) {
        Optional<Voyage> found = Voyages.get(server, voyageId);
        if (found.isEmpty()) return;
        Voyage v = found.get();
        if (v.kind() != VoyageKind.PATROL || v.state() != Voyage.State.MATERIALISED || v.shipId().isEmpty()) return;
        ServerLevel level = Materializer.levelOf(server, v);
        ShipBody ship = SableShips.byId(level, v.shipId().get());
        if (ship == null || ship.isRemoved()) return; // not loaded: the materialiser lets it go
        Vec3 at = Materializer.centre(ship);
        long now = now(server);
        HuntRules.Params params = NavyConfig.params();
        if (v.pursuit().isEmpty()) {
            if (!NavyConfig.active()) return;
            Optional<HuntRules.Candidate> sighted = HuntRules.pick(at.x, at.z, candidates(level, at.x, at.z, params.huntRadius()), params);
            if (sighted.isEmpty()) return;
            v = start(server, v, sighted.get(), now);
            Voyages.update(server, v);
        }
        UUID targetId = v.pursuit().get();
        NavyData.Pursuit p = pursuitOf(server, v, targetId, now);
        HuntRules.Candidate target = candidate(level, targetId);
        HuntRules.Judgement j = judge(target, at.x, at.z, now, p, params);
        if (j.step() == HuntRules.Step.RESUME) {
            Voyages.update(server, resume(server, v, p, at.x, at.z, j.reason()));
            disengage(level, ship, voyageId);
            return;
        }
        keep(server, p, j);
        Engaged e = ENGAGED.compute(voyageId, (id, old) -> old != null && old.ship.equals(ship.id()) ? old : new Engaged(ship.id()));
        if (j.step() == HuntRules.Step.CHASE) {
            Gunnery.set(level, ship, GunneryState.target(targetId));
            if (now - e.lastGunPost >= NavyConfig.COURSE_INTERVAL_TICKS.get()) {
                // free hands to the unmanned guns (no-op on a ship without guns, or with every gun manned)
                e.lastGunPost = now;
                JobBoard.post(level, ship, CannonOrder.LOAD);
            }
        } else {
            Gunnery.clear(ship); // struck colours: guns silent at once
        }
        steer(level, ship, e, at, target, now);
        double[] aim = PatrolRoutes.standoffPoint(at.x, at.z, target.x(), target.z(), NavyConfig.STANDOFF_DISTANCE.get());
        // the record follows the ship, so a ship turned back into a record continues from where it was
        Voyages.update(server, v.withRoute(v.from(), v.to(), List.of(point(at.x, at.z), point(aim[0], aim[1]))));
    }

    /** Gives the helmsman the ring around the quarry when it has none, or when it is due and the quarry moved. */
    private static void steer(ServerLevel level, ShipBody ship, Engaged e, Vec3 at, HuntRules.Candidate target, long now) {
        CourseOrder current = HelmCourses.course(ship.id());
        boolean ours = current != null && current.equals(e.order);
        Vec3 t = new Vec3(target.x(), level.getSeaLevel(), target.z());
        if (ours && (now - e.lastCourse < NavyConfig.COURSE_INTERVAL_TICKS.get()
                || (e.courseAt != null && horizontal(e.courseAt, t) < NavyConfig.COURSE_REFRESH_DISTANCE.get()))) {
            return;
        }
        CourseOrder order = new CourseOrder(PatrolRoutes.ring(target.x(), target.z(), NavyConfig.STANDOFF_DISTANCE.get(), at.x, at.z,
                RING_POINTS, level.getSeaLevel()), true);
        HelmCourses.set(level, ship, order);
        e.order = order;
        e.courseAt = t;
        e.lastCourse = now;
    }

    /** The chase is over for a real ship: guns off, course off (the materialiser gives the lane course again). */
    private static void disengage(ServerLevel level, ShipBody ship, UUID voyageId) {
        ENGAGED.remove(voyageId);
        Gunnery.clear(ship);
        HelmCourses.clear(level, ship.id());
    }

    // ------------------------------------------------------------------ chase bookkeeping

    private static HuntRules.Judgement judge(@Nullable HuntRules.Candidate target, double x, double z, long now, NavyData.Pursuit p,
                                             HuntRules.Params params) {
        if (!NavyConfig.active()) {
            return new HuntRules.Judgement(HuntRules.Step.RESUME, HuntRules.Reason.NOT_HUNTED, p.lastContact(), p.surrenderedUntil());
        }
        return HuntRules.judge(target, x, z, now, p.lastContact(), p.surrenderedUntil(), params);
    }

    /** A chase begins: the route left is saved, the owner told. */
    private static Voyage start(MinecraftServer server, Voyage v, HuntRules.Candidate c, long now) {
        NavyData.get(server).put(new NavyData.Pursuit(v.id(), c.ship(), v.waypoints(), v.progress(), now, now, 0L));
        Constants.LOG.debug("Patrol {} gives chase to ship {}", v.shortId(), c.ship());
        tellOwner(server, c.ship(), "chase");
        return v.withPursuit(Optional.of(c.ship()));
    }

    /** The saved chase of {@code v}; a chase without one (an older save) starts from where the patrol is. */
    private static NavyData.Pursuit pursuitOf(MinecraftServer server, Voyage v, UUID target, long now) {
        NavyData data = NavyData.get(server);
        Optional<NavyData.Pursuit> p = data.get(v.id());
        if (p.isPresent() && p.get().target().equals(target)) return p.get();
        NavyData.Pursuit fresh = new NavyData.Pursuit(v.id(), target, v.waypoints(), v.progress(), now, now, 0L);
        data.put(fresh);
        return fresh;
    }

    /** Stores the timers after a check; the owner hears once that the surrender was seen. */
    private static void keep(MinecraftServer server, NavyData.Pursuit p, HuntRules.Judgement j) {
        if (j.step() == HuntRules.Step.SHADOW && p.surrenderedUntil() == 0) tellOwner(server, p.target(), "surrender");
        NavyData.get(server).put(p.withTimers(j.lastContact(), j.surrenderedUntil()));
    }

    /** The chase is over: back toward the route it left. */
    private static Voyage resume(MinecraftServer server, Voyage v, NavyData.Pursuit p, double x, double z, HuntRules.Reason reason) {
        NavyData.get(server).remove(v.id());
        Constants.LOG.debug("Patrol {} ends its chase of ship {}: {}", v.shortId(), p.target(), reason);
        if (reason == HuntRules.Reason.GAVE_UP || reason == HuntRules.Reason.OUT_OF_RANGE) tellOwner(server, p.target(), "gave_up");
        else if (reason == HuntRules.Reason.SURRENDERED) tellOwner(server, p.target(), "resumes");
        return v.withPursuit(Optional.empty()).withRoute(v.from(), v.to(), PatrolRoutes.rejoin(p.home(), p.homeProgress(), x, z));
    }

    /** {@code Voyages.onEnd}: a patrol that ends forgets its chase. */
    public static void onVoyageEnded(MinecraftServer server, Voyage voyage, VoyageEnd reason) {
        ENGAGED.remove(voyage.id());
        if (voyage.kind() == VoyageKind.PATROL) NavyData.get(server).remove(voyage.id());
    }

    public static void onServerStopped() {
        ENGAGED.clear();
        GRIDS.clear();
    }

    // ------------------------------------------------------------------ what a patrol sees

    /** Every player's ship (and every other registered ship) loaded in {@code level} within {@code radius} of (x, z). */
    static List<HuntRules.Candidate> candidates(ServerLevel level, double x, double z, double radius) {
        List<HuntRules.Candidate> out = new ArrayList<>();
        ShipRegistry registry = ShipRegistry.get(level.getServer());
        for (ShipBody s : SableShips.all(level)) {
            if (s.isRemoved() || ShipSplits.isWreck(s)) continue;
            Vec3 c = centre(s);
            if (Math.hypot(c.x - x, c.z - z) > radius) continue;
            registry.find(s.id()).ifPresent(d -> out.add(view(level, s, d)));
        }
        return out;
    }

    /** The ship {@code id} as a patrol sees it now; null when it is not loaded here, removed, a wreck or unknown. */
    static @Nullable HuntRules.Candidate candidate(ServerLevel level, UUID id) {
        ShipBody s = SableShips.byId(level, id);
        if (s == null || s.isRemoved() || ShipSplits.isWreck(s)) return null;
        return ShipRegistry.get(level.getServer()).find(id).map(d -> view(level, s, d)).orElse(null);
    }

    private static HuntRules.Candidate view(ServerLevel level, ShipBody s, ShipData d) {
        MinecraftServer server = level.getServer();
        Vec3 c = centre(s);
        FlagReading flag = d.flag();
        Optional<UUID> owner = d.owner();
        long bounty = owner.map(o -> LawService.bountyTotal(server, o)).orElse(0L);
        boolean wanted = owner.map(o -> ownerWanted(server, o)).orElse(false);
        return new HuntRules.Candidate(s.id(), c.x, c.z, owner.isPresent(), flag.shown(), flag.isStruck(),
                d.coverBlown(LawService.now(server)), bounty, wanted);
    }

    /** The owner is loaded and wanted at {@code law.world.navy_hostility_threshold}. */
    private static boolean ownerWanted(MinecraftServer server, UUID owner) {
        if (!LawConfig.CRIMINAL_SCORE_ENABLED.get()) return false;
        LivingEntity e = LawService.findLoaded(server, owner);
        return e != null && LawService.wantedLevel(e).atLeast(LawConfig.NAVY_HOSTILITY_THRESHOLD.get());
    }

    /** The lane grid of {@code level} (open sea from the biome map, never loading chunks). */
    static SeaGrid grid(ServerLevel level) {
        int cell = VoyageConfig.CELL_BLOCKS.get();
        BiomeSeaGrid g = GRIDS.get(level.dimension());
        if (g == null || g.cellBlocks() != cell || g.samples() > MAX_GRID_SAMPLES) {
            g = BiomeSeaGrid.of(level, cell);
            GRIDS.put(level.dimension(), g);
        }
        return g;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The world position of the centre of the ship's plot bounds. Not {@code worldBounds}: right after an assembly it
     * still reads the origin until the next physics step (a GameTest quarry sat at 0, 0).
     */
    static Vec3 centre(ShipBody ship) {
        net.minecraft.core.BlockPos[] b = ship.plotBounds();
        Vec3 lo = Vec3.atLowerCornerOf(b[0]);
        Vec3 hi = Vec3.atLowerCornerOf(b[1]).add(1, 1, 1);
        return ship.toWorld(lo.add(hi).scale(0.5));
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    private static Lane.Point point(double x, double z) {
        return new Lane.Point((int) Math.round(x), (int) Math.round(z));
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /** Tells the owner of ship {@code id}, if online, about the chase ({@code announce}). */
    private static void tellOwner(MinecraftServer server, UUID id, String what) {
        if (!NavyConfig.ANNOUNCE.get()) return;
        Optional<ShipData> d = ShipRegistry.get(server).find(id);
        if (d.isEmpty() || d.get().owner().isEmpty()) return;
        ServerPlayer player = server.getPlayerList().getPlayer(d.get().owner().get());
        if (player != null) player.sendSystemMessage(Component.translatable(KEY + what, d.get().name()));
    }

    public static void lang(LangBuilder lang) {
        lang.add(KEY + "chase", "A navy patrol has sighted the %s and gives chase!")
                .add(KEY + "surrender", "The navy patrol sees the %s strike her colours and holds its fire")
                .add(KEY + "resumes", "The navy patrol leaves the %s and returns to its route")
                .add(KEY + "gave_up", "The navy patrol has lost the %s and breaks off the chase");
    }
}

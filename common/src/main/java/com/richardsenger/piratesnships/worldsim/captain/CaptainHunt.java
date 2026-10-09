package com.richardsenger.piratesnships.worldsim.captain;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.cannon.CannonStation.CannonOrder;
import com.richardsenger.piratesnships.combat.cannon.npc.Gunnery;
import com.richardsenger.piratesnships.combat.cannon.npc.GunneryConfig;
import com.richardsenger.piratesnships.combat.cannon.npc.GunneryState;
import com.richardsenger.piratesnships.combat.cannon.npc.ShipHostility;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.law.flag.ShipStance;
import com.richardsenger.piratesnships.law.proof.ProofContent;
import com.richardsenger.piratesnships.mob.captain.CaptainEntry;
import com.richardsenger.piratesnships.rpg.career.Careers;
import com.richardsenger.piratesnships.rpg.career.LetterState;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.decor.flag.FlagReading;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.helm.CourseOrder;
import com.richardsenger.piratesnships.station.helm.HelmCourses;
import com.richardsenger.piratesnships.station.jobs.JobBoard;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.materialize.Materializer;
import com.richardsenger.piratesnships.worldsim.navy.HuntRules;
import com.richardsenger.piratesnships.worldsim.navy.PatrolRoutes;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The captain's hunt at sea (BOS2), the navy's hunt (WS4b) with the captain's own quarry
 * ({@link CaptainVoyageRules#hunted}: a player's ship whose owner holds a letter of marque or carries bounty proofs)
 * within {@code hunt_radius}. Only while the captain is aboard: a ship that lost him sails its course.
 *
 * <ul>
 *   <li><b>Abstract</b> ({@link #updateAbstract}, the CAPTAIN planner's update): the record turns toward the quarry,
 *       straight and capped to open sea ({@link PatrolRoutes#pursuit}); the route it left is kept in
 *       {@link CaptainSeaData}.</li>
 *   <li><b>Materialised</b> ({@link #updateMaterialised}, every voyage check): the gun crews fire at will at hostile ships
 *       ({@code ShipHostility}, WS4a) and are called to the guns when one is within {@code cannons.npc.engage_range};
 *       in a chase the helmsman circles the quarry at {@code standoff_distance} and the guns take it as their target.
 *       A quarry that strikes its colours is left alone after {@code surrender_linger_ticks}, guns silent; the captain
 *       never strikes his own.</li>
 *   <li><b>Ending</b> ({@link HuntRules#judge}): lost, out of range, no longer hunted or out of contact for
 *       {@code give_up_ticks}; the ship returns to the nearest point of its route.</li>
 * </ul>
 */
public final class CaptainHunt {

    static final String KEY = "message." + Constants.MOD_ID + ".captain_hunt.";
    static final int RING_POINTS = 4;

    /** A materialised voyage's engagement: the course it was given last, when its guns were last called. */
    private static final class Engaged {
        final UUID ship;
        @Nullable CourseOrder order;
        @Nullable Vec3 courseAt;
        long lastCourse = Long.MIN_VALUE / 2;
        long lastGunCall = Long.MIN_VALUE / 2;

        Engaged(UUID ship) {
            this.ship = ship;
        }
    }

    private static final Map<UUID, Engaged> ENGAGED = new ConcurrentHashMap<>();

    private CaptainHunt() {
    }

    static void forget(UUID voyage) {
        ENGAGED.remove(voyage);
    }

    static void clear() {
        ENGAGED.clear();
    }

    /** Whether the captain of {@code v} is aboard (at sea on it, alive) and voyages are on. */
    private static boolean hunts(MinecraftServer server, Voyage v) {
        return CaptainVoyageConfig.active() && CaptainVoyages.captainOf(server, v).isPresent();
    }

    // ------------------------------------------------------------------ abstract

    /** One check of an abstract (SAILING) captain's voyage: sight a quarry, chase it or let it go. Returns the voyage to keep. */
    public static Voyage updateAbstract(MinecraftServer server, Voyage v) {
        if (!CaptainVoyages.isCaptainVoyage(v) || v.state() != Voyage.State.SAILING) return v;
        ServerLevel level = Materializer.levelOf(server, v);
        Lane.Position pos = v.position();
        long now = now(server);
        HuntRules.Params params = CaptainVoyageConfig.huntParams();
        double standoff = CaptainVoyageConfig.STANDOFF_DISTANCE.get();
        CaptainSeaData data = CaptainSeaData.get(server);
        if (v.pursuit().isEmpty()) {
            if (!hunts(server, v) || params.huntRadius() <= 0) return v;
            Optional<HuntRules.Candidate> sighted = HuntRules.pick(pos.x(), pos.z(), candidates(level, null, pos.x(), pos.z(), params.huntRadius()), params);
            if (sighted.isEmpty()) return v;
            HuntRules.Candidate c = sighted.get();
            Voyage chasing = start(server, v, c, now);
            return chasing.withRoute(v.from(), v.to(), PatrolRoutes.pursuit(CaptainVoyages.grid(level), pos.x(), pos.z(), c.x(), c.z(), standoff));
        }
        CaptainSeaData.Chase chase = chaseOf(data, v, v.pursuit().get(), now);
        HuntRules.Candidate target = candidate(level, v.pursuit().get());
        HuntRules.Judgement j = judge(server, v, target, pos.x(), pos.z(), now, chase, params);
        if (j.step() == HuntRules.Step.RESUME) return resume(server, v, chase, pos.x(), pos.z(), j.reason());
        data.putChase(chase.withTimers(j.lastContact(), j.surrenderedUntil()));
        return v.withRoute(v.from(), v.to(), PatrolRoutes.pursuit(CaptainVoyages.grid(level), pos.x(), pos.z(), target.x(), target.z(), standoff));
    }

    /** At the end of its route: a voyage in a chase stays at sea (it waits where the coast stopped it). */
    public static Optional<Voyage> onArrive(MinecraftServer server, Voyage v) {
        return v.pursuit().isPresent() ? Optional.of(v) : Optional.empty();
    }

    // ------------------------------------------------------------------ materialised

    /** One check of materialised captain's voyage {@code voyageId}: guns at will, and sight, chase or break off. */
    public static void updateMaterialised(MinecraftServer server, UUID voyageId) {
        Optional<Voyage> found = Voyages.get(server, voyageId);
        if (found.isEmpty()) return;
        Voyage v = found.get();
        if (!CaptainVoyages.isCaptainVoyage(v) || v.state() != Voyage.State.MATERIALISED || v.shipId().isEmpty()) return;
        ServerLevel level = Materializer.levelOf(server, v);
        ShipBody ship = SableShips.byId(level, v.shipId().get());
        if (ship == null || ship.isRemoved()) return;
        Vec3 at = centre(ship);
        long now = now(server);
        HuntRules.Params params = CaptainVoyageConfig.huntParams();
        Engaged e = ENGAGED.compute(voyageId, (id, old) -> old != null && old.ship.equals(ship.id()) ? old : new Engaged(ship.id()));
        if (v.pursuit().isEmpty()) {
            // like any pirate ship: the crews fire at will at hostile ships, and are called to the guns when one is near
            Gunnery.set(level, ship, GunneryState.AT_WILL);
            if (now - e.lastGunCall >= CaptainVoyageConfig.COURSE_INTERVAL_TICKS.get() && hostileNear(level, ship, at)) {
                e.lastGunCall = now;
                JobBoard.post(level, ship, CannonOrder.LOAD);
            }
            if (!hunts(server, v) || params.huntRadius() <= 0) return;
            Optional<HuntRules.Candidate> sighted = HuntRules.pick(at.x, at.z, candidates(level, ship.id(), at.x, at.z, params.huntRadius()), params);
            if (sighted.isEmpty()) return;
            v = start(server, v, sighted.get(), now);
            Voyages.update(server, v);
        }
        UUID targetId = v.pursuit().get();
        CaptainSeaData data = CaptainSeaData.get(server);
        CaptainSeaData.Chase chase = chaseOf(data, v, targetId, now);
        HuntRules.Candidate target = candidate(level, targetId);
        HuntRules.Judgement j = judge(server, v, target, at.x, at.z, now, chase, params);
        if (j.step() == HuntRules.Step.RESUME) {
            Voyages.update(server, resume(server, v, chase, at.x, at.z, j.reason()));
            e.order = null;
            Gunnery.set(level, ship, GunneryState.AT_WILL);
            HelmCourses.clear(level, ship.id()); // the materialiser gives the route's course again
            return;
        }
        data.putChase(chase.withTimers(j.lastContact(), j.surrenderedUntil()));
        if (j.step() == HuntRules.Step.CHASE) {
            Gunnery.set(level, ship, GunneryState.target(targetId));
            if (now - e.lastGunCall >= CaptainVoyageConfig.COURSE_INTERVAL_TICKS.get()) {
                e.lastGunCall = now;
                JobBoard.post(level, ship, CannonOrder.LOAD);
            }
        } else {
            Gunnery.clear(ship); // struck colours: guns silent
        }
        steer(level, ship, e, at, target, now);
        double[] aim = PatrolRoutes.standoffPoint(at.x, at.z, target.x(), target.z(), CaptainVoyageConfig.STANDOFF_DISTANCE.get());
        Voyages.update(server, v.withRoute(v.from(), v.to(), List.of(point(at.x, at.z), point(aim[0], aim[1]))));
    }

    /** Gives the helmsman the ring around the quarry when it has none of ours, or when it is due and the quarry moved. */
    private static void steer(ServerLevel level, ShipBody ship, Engaged e, Vec3 at, HuntRules.Candidate target, long now) {
        CourseOrder current = HelmCourses.course(ship.id());
        boolean ours = current != null && current.equals(e.order);
        Vec3 t = new Vec3(target.x(), level.getSeaLevel(), target.z());
        if (ours && (now - e.lastCourse < CaptainVoyageConfig.COURSE_INTERVAL_TICKS.get()
                || (e.courseAt != null && Math.hypot(e.courseAt.x - t.x, e.courseAt.z - t.z) < CaptainVoyageConfig.COURSE_REFRESH_DISTANCE.get()))) {
            return;
        }
        CourseOrder order = new CourseOrder(PatrolRoutes.ring(target.x(), target.z(), CaptainVoyageConfig.STANDOFF_DISTANCE.get(), at.x, at.z,
                RING_POINTS, level.getSeaLevel()), true);
        HelmCourses.set(level, ship, order);
        e.order = order;
        e.courseAt = t;
        e.lastCourse = now;
    }

    // ------------------------------------------------------------------ chase bookkeeping

    private static HuntRules.Judgement judge(MinecraftServer server, Voyage v, @Nullable HuntRules.Candidate target, double x, double z,
                                             long now, CaptainSeaData.Chase chase, HuntRules.Params params) {
        if (!hunts(server, v)) {
            return new HuntRules.Judgement(HuntRules.Step.RESUME, HuntRules.Reason.NOT_HUNTED, chase.lastContact(), chase.surrenderedUntil());
        }
        return HuntRules.judge(target, x, z, now, chase.lastContact(), chase.surrenderedUntil(), params);
    }

    private static Voyage start(MinecraftServer server, Voyage v, HuntRules.Candidate c, long now) {
        CaptainSeaData.get(server).putChase(new CaptainSeaData.Chase(v.id(), c.ship(), v.waypoints(), v.progress(), now, 0L));
        Constants.LOG.debug("Captain's voyage {} gives chase to ship {}", v.shortId(), c.ship());
        tellOwner(server, v, c.ship(), "chase");
        return v.withPursuit(Optional.of(c.ship()));
    }

    private static CaptainSeaData.Chase chaseOf(CaptainSeaData data, Voyage v, UUID target, long now) {
        Optional<CaptainSeaData.Chase> c = data.chase(v.id());
        if (c.isPresent() && c.get().target().equals(target)) return c.get();
        CaptainSeaData.Chase fresh = new CaptainSeaData.Chase(v.id(), target, v.waypoints(), v.progress(), now, 0L);
        data.putChase(fresh);
        return fresh;
    }

    private static Voyage resume(MinecraftServer server, Voyage v, CaptainSeaData.Chase chase, double x, double z, HuntRules.Reason reason) {
        CaptainSeaData.get(server).removeChase(v.id());
        Constants.LOG.debug("Captain's voyage {} ends its chase of ship {}: {}", v.shortId(), chase.target(), reason);
        if (reason == HuntRules.Reason.GAVE_UP || reason == HuntRules.Reason.OUT_OF_RANGE) tellOwner(server, v, chase.target(), "gave_up");
        return v.withPursuit(Optional.empty()).withRoute(v.from(), v.to(), PatrolRoutes.rejoin(chase.home(), chase.homeProgress(), x, z));
    }

    // ------------------------------------------------------------------ what the captain sees

    /** Every registered ship in {@code level} within {@code radius} of (x, z) except {@code own}, as the captain sees it. */
    static List<HuntRules.Candidate> candidates(ServerLevel level, @Nullable UUID own, double x, double z, double radius) {
        List<HuntRules.Candidate> out = new ArrayList<>();
        ShipRegistry registry = ShipRegistry.get(level.getServer());
        for (ShipBody s : SableShips.all(level)) {
            if (s.isRemoved() || s.id().equals(own) || ShipSplits.isWreck(s)) continue;
            Vec3 c = centre(s);
            if (Math.hypot(c.x - x, c.z - z) > radius) continue;
            registry.find(s.id()).ifPresent(d -> out.add(view(level, s, d)));
        }
        return out;
    }

    /** The ship {@code id} as the captain sees it now; null when it is not loaded here, removed, a wreck or unknown. */
    static @Nullable HuntRules.Candidate candidate(ServerLevel level, UUID id) {
        ShipBody s = SableShips.byId(level, id);
        if (s == null || s.isRemoved() || ShipSplits.isWreck(s)) return null;
        return ShipRegistry.get(level.getServer()).find(id).map(d -> view(level, s, d)).orElse(null);
    }

    private static HuntRules.Candidate view(ServerLevel level, ShipBody s, ShipData d) {
        Vec3 c = centre(s);
        Optional<UUID> owner = d.owner();
        ServerPlayer player = owner.map(o -> level.getServer().getPlayerList().getPlayer(o)).orElse(null);
        boolean letter = player != null && Careers.record(player).letter() == LetterState.ACTIVE;
        int proofs = player == null ? 0 : proofs(player);
        boolean hunted = CaptainVoyageRules.hunted(owner.isPresent(), letter, proofs, CaptainVoyageConfig.quarry());
        return CaptainVoyageRules.candidate(s.id(), c.x, c.z, owner.isPresent(), d.flag().isStruck(), hunted);
    }

    /** Bounty proofs the player carries. */
    static int proofs(Player player) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack st = player.getInventory().getItem(i);
            if (st.is(ProofContent.BOUNTY_PROOF.get())) n += st.getCount();
        }
        return n;
    }

    /** A ship within {@code cannons.npc.engage_range} the pirates' guns would fire at ({@link ShipHostility}). */
    private static boolean hostileNear(ServerLevel level, ShipBody own, Vec3 at) {
        double range = GunneryConfig.ENGAGE_RANGE.get();
        ShipRegistry registry = ShipRegistry.get(level.getServer());
        long lawNow = LawService.now(level.getServer());
        for (ShipBody s : SableShips.all(level)) {
            if (s.isRemoved() || s.id().equals(own.id()) || ShipSplits.isWreck(s)) continue;
            Vec3 c = centre(s);
            if (Math.hypot(c.x - at.x, c.z - at.z) > range) continue;
            Optional<ShipData> d = registry.find(s.id());
            if (d.isEmpty()) continue;
            FlagReading flag = d.get().flag();
            ShipStance stance = ShipStance.of(flag.shown(), flag.isStruck(), d.get().coverBlown(lawNow));
            if (ShipHostility.hostile(Faction.PIRATES, flag, false, stance)) return true;
        }
        return false;
    }

    /** The world position of the centre of the ship's plot bounds (cheap; good enough for distances). */
    static Vec3 centre(ShipBody ship) {
        BlockPos[] b = ship.plotBounds();
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

    /** Tells the owner of ship {@code id}, if online, that the captain gives chase or breaks off. */
    private static void tellOwner(MinecraftServer server, Voyage v, UUID id, String what) {
        if (!CaptainVoyageConfig.ANNOUNCE.get()) return;
        Optional<ShipData> d = ShipRegistry.get(server).find(id);
        if (d.isEmpty() || d.get().owner().isEmpty()) return;
        ServerPlayer player = server.getPlayerList().getPlayer(d.get().owner().get());
        String captain = CaptainVoyages.captainOf(server, v).map(CaptainEntry::name).orElse("?");
        if (player != null) player.sendSystemMessage(Component.translatable(KEY + what, captain, d.get().name()));
    }

    public static void lang(LangBuilder lang) {
        lang.add(KEY + "chase", "Pirate captain %s has sighted the %s and gives chase!")
                .add(KEY + "gave_up", "Pirate captain %s has lost the %s and breaks off the chase");
    }
}

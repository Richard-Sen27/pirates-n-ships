package com.richardsenger.piratesnships.worldsim.materialize;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.assembly.ShipHelm;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleBlockEntity;
import com.richardsenger.piratesnships.ship.decor.flag.ShipAllegiance;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.template.ShipTemplate;
import com.richardsenger.piratesnships.ship.template.ShipTemplatePlacer;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import com.richardsenger.piratesnships.station.helm.CourseEvent;
import com.richardsenger.piratesnships.station.helm.CourseOrder;
import com.richardsenger.piratesnships.station.helm.HelmCourses;
import com.richardsenger.piratesnships.station.jobs.JobBoard;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageConfig;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Materialisation of abstract voyages (WS3b, design.md §10.4 "Abstract voyages"): a SAILING voyage near a player
 * becomes a real template ship on its lane heading with crew, fighters, flag and cargo, sails the rest of its lane
 * with an NPC helmsman ({@code HelmCourses}), and becomes a record again when nobody is near.
 *
 * <ul>
 *   <li><b>Trigger</b> ({@link #check}, every {@code world_simulation.voyages.tick_interval_ticks}): the SAILING voyage
 *       nearest to any player within {@link MaterializeConfig#radius} appears, one per check, while fewer than
 *       {@code max_materialized} are real. Its spawn area must be loaded (else a {@link VoyageChunks} ticket is taken
 *       and the voyage tried in the next check) and its point must be open surface water.</li>
 *   <li><b>Placement</b>: {@code ShipTemplatePlacer.place} toward the cardinal direction nearest the leg's heading
 *       (no force, assembled, no owner), centred on the lane point, then turned and moved by {@code ShipBody.placeAt}
 *       to the exact heading with its centre on the point; named, flag by faction, cargo into the containers, crew and
 *       fighters ({@link VoyageCrew}), the course over the remaining waypoints.</li>
 *   <li><b>Link</b>: the record (MATERIALISED, {@code shipId}), {@link VoyageShips} in memory, {@link VoyageLink} in the
 *       sub-level's user data. After a reload a loaded ship is adopted again by its user data and gets its course back;
 *       a ship whose voyage moved on or ended is removed with its people.</li>
 *   <li><b>Dematerialisation</b> after {@code linger_ticks} with no player within radius + {@code linger_margin}:
 *       progress from the ship's position projected on the route, health from the flooded fraction, cargo from the
 *       containers, crew and fighters counted, everyone discarded, the ship removed. A ship whose chunks unloaded is
 *       let go the same way without being touched (the body is removed when it loads again).</li>
 *   <li><b>Course events</b>: at the last waypoint the ship dematerialises and the voyage arrives; a stuck ship
 *       dematerialises and skips to the waypoint it was heading for (islands, headwinds), then waits
 *       {@code retry_ticks} before it may appear again.</li>
 *   <li><b>Endings</b>: {@link VoyageEndings}.</li>
 * </ul>
 */
public final class Materializer {

    /** What {@link #materialize} did. */
    public enum Outcome {
        SPAWNED, WAITING_FOR_CHUNKS, NO_WATER, OBSTRUCTED, FAILED, CAP, NOT_SAILING, UNKNOWN;

        public boolean spawned() {
            return this == SPAWNED;
        }
    }

    /** GameTest voyages older than this are ended by the check (longer than any test runs). */
    static final long TEST_VOYAGE_TICKS = 1200;
    private static final Map<UUID, Long> RETRY_AFTER = new ConcurrentHashMap<>();
    private static final Map<UUID, CourseEvent.Type> PENDING = new ConcurrentHashMap<>();
    private static final List<String> MERCHANT_NAMES = List.of("Fair Wind", "Silver Gull", "Morning Star", "Good Hope",
            "Sea Swallow", "Patient Grace", "Bountiful", "Lucky Penny", "Spice Maiden", "Laden Lady");
    private static final List<String> NAVY_NAMES = List.of("Resolute", "Vigilant", "Steadfast", "Dauntless", "Guardian",
            "Intrepid", "Sentinel", "Valiant");
    private static final List<String> PIRATE_NAMES = List.of("Black Gull", "Sea Wolf", "Crimson Tide", "Rusty Cutlass",
            "Widow's Revenge", "Salty Dog", "Grinning Skull", "Night Shark");

    private Materializer() {
    }

    // ------------------------------------------------------------------ the scheduler check

    /** {@code SERVER_TICK_END}: one check every {@code tick_interval_ticks}, like the voyage scheduler. */
    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % VoyageConfig.TICK_INTERVAL.get() != 0) return;
        check(server);
    }

    /**
     * One check: tickets that ran out are released, loaded voyage ships are adopted (after a reload), every
     * materialised voyage is updated (endings, plunder, course, progress, dematerialisation), then the nearest voyage
     * may appear. GameTest voyages ({@code gametest/…} origins) are left to their tests, and ended after
     * {@value #TEST_VOYAGE_TICKS} ticks. Nothing appears in a check
     * that removed a ship (Sable reuses a freed plot at once, docs/sable-notes.md §9.0c).
     */
    public static void check(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) VoyageChunks.tick(level, level.getGameTime());
        if (!com.richardsenger.piratesnships.worldsim.WorldSimConfig.ENABLED.get()) return;
        boolean removed = adopt(server);
        long now = server.overworld().getGameTime();
        for (Voyage v : Voyages.active(server)) {
            if (isTest(v)) {
                // a GameTest that failed before it ended its voyage leaves the record behind
                if (now - v.departedTick() > TEST_VOYAGE_TICKS) Voyages.end(server, v.id(), VoyageEnd.CANCELLED);
                continue;
            }
            if (v.state() == Voyage.State.MATERIALISED) removed |= update(server, v.id());
        }
        if (!removed && MaterializeConfig.active()) trigger(server);
    }

    /** Test voyages ({@code gametest/…} origin) are driven by their GameTests only. */
    static boolean isTest(Voyage v) {
        return v.from().getPath().startsWith("gametest/");
    }

    /** Materialises the SAILING voyage nearest to a player within the radius, if there is room. */
    static void trigger(MinecraftServer server) {
        if (VoyageShips.count() >= MaterializeConfig.MAX_MATERIALIZED.get()) return;
        double radius = MaterializeConfig.radius(server);
        long now = server.overworld().getGameTime();
        Voyage best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Voyage v : Voyages.active(server)) {
            if (v.state() != Voyage.State.SAILING || isTest(v) || v.waypoints().size() < 2) continue;
            Long after = RETRY_AFTER.get(v.id());
            if (after != null && now < after) continue;
            ServerLevel level = levelOf(server, v);
            Lane.Position p = v.position();
            double d = nearestPlayer(level, p.x(), p.z());
            if (d <= radius && d < bestDistance) {
                best = v;
                bestDistance = d;
            }
        }
        if (best != null) {
            Outcome o = materialize(server, best.id());
            Constants.LOG.debug("Voyage {} near a player ({} blocks): {}", best.shortId(), (int) bestDistance, o);
        }
    }

    // ------------------------------------------------------------------ materialise

    /**
     * Makes voyage {@code id} a real ship now (the trigger, commands and GameTests). Respects {@code max_materialized};
     * not the radius or the toggles.
     */
    public static Outcome materialize(MinecraftServer server, UUID id) {
        Optional<Voyage> found = Voyages.get(server, id);
        if (found.isEmpty()) return Outcome.UNKNOWN;
        Voyage v = found.get();
        if (v.state() != Voyage.State.SAILING || v.waypoints().size() < 2) return Outcome.NOT_SAILING;
        if (VoyageShips.count() >= MaterializeConfig.MAX_MATERIALIZED.get()) return Outcome.CAP;
        ServerLevel level = levelOf(server, v);
        Lane.Position pos = v.position();
        ShipTemplate template = ShipTemplates.TYPE.server().get(v.template()).orElse(null);
        StructureTemplate structure = template == null ? null : ShipTemplatePlacer.structure(level, template).orElse(null);
        if (structure == null) {
            Constants.LOG.warn("Voyage {}: ship template {} not found", v.shortId(), v.template());
            retryLater(server, id);
            return Outcome.FAILED;
        }
        boolean alongZ = template.bow().getAxis() == Direction.Axis.Z;
        int length = alongZ ? structure.getSize().getZ() : structure.getSize().getX();
        int beam = alongZ ? structure.getSize().getX() : structure.getSize().getZ();
        if (!VoyageChunks.ready(level, pos.x(), pos.z(), Math.max(length, beam) / 2 + 4)) {
            VoyageChunks.request(level, pos.x(), pos.z(), level.getGameTime());
            return Outcome.WAITING_FOR_CHUNKS;
        }
        OptionalInt surface = surfaceY(level, pos.x(), pos.z());
        if (surface.isEmpty()) {
            retryLater(server, id);
            return Outcome.NO_WATER;
        }
        Direction facing = Direction.fromYRot(pos.headingDegrees() + 180.0);
        int off = ShipTemplatePlacer.GAP + (length - 1) / 2;
        BlockPos feet = new BlockPos((int) Math.floor(pos.x()) - facing.getStepX() * off, surface.getAsInt(),
                (int) Math.floor(pos.z()) - facing.getStepZ() * off);
        ShipTemplatePlacer.Result r = ShipTemplatePlacer.place(level, v.template(), feet, facing, false, true, null);
        if (!r.outcome().success) {
            retryLater(server, id);
            return r.outcome() == ShipTemplatePlacer.Outcome.OBSTRUCTED ? Outcome.OBSTRUCTED : Outcome.FAILED;
        }
        AssemblyResult a = r.assembly();
        ShipBody ship = a == null || a.shipId() == null ? null : SableShips.byId(level, a.shipId());
        if (ship == null) {
            Constants.LOG.warn("Voyage {}: the {} placed at {} did not assemble ({})", v.shortId(), v.template(), r.origin(),
                    a == null ? null : a.outcome());
            retryLater(server, id);
            return Outcome.FAILED;
        }
        turnOnto(ship, facing, pos);
        ShipAssembler.name(ship, name(v));
        raiseFlag(ship, v);
        VoyageShips.Active link = new VoyageShips.Active(id, ship.id(), level.dimension(), level.getGameTime());
        link.overflow = VoyageShips.load(level, ship, v.cargo(), MaterializeConfig.CARGO_IS_PLUNDER.get());
        link.lastUnits = VoyageShips.units(VoyageShips.read(level, ship));
        VoyageShips.put(link);
        VoyageShips.writeLink(ship, link.link());
        int crew = v.crew() == Voyage.UNMANNED ? MaterializeConfig.CREW_PER_SHIP.get() + 1 : v.crew();
        int fighters = v.fighters() == Voyage.UNMANNED ? MaterializeConfig.fighters(v.faction()) : v.fighters();
        VoyageCrew.man(level, ship, id, v.faction(), crew, fighters, waterlinePlotY(ship, template, r));
        Voyage m = v.withCrew(crew, fighters).withState(Voyage.State.MATERIALISED, Optional.of(ship.id()));
        Voyages.update(server, m);
        setCourse(level, ship, m);
        RETRY_AFTER.remove(id);
        Constants.LOG.debug("Voyage {} materialised as ship {} at {} {}", v.shortId(), ship.id(), (int) pos.x(), (int) pos.z());
        return Outcome.SPAWNED;
    }

    /** Turns the freshly assembled {@code ship} (bow toward {@code facing}) to the leg heading, its centre on the point. */
    static void turnOnto(ShipBody ship, Direction facing, Lane.Position pos) {
        double facingCompass = (facing.toYRot() + 180.0) % 360.0;
        // A turn by +a about +y takes compass heading h to h - a, so turn by (facing - heading).
        Quaterniond turn = new Quaterniond().rotateAxis(Math.toRadians(facingCompass - pos.headingDegrees()), 0, 1, 0);
        Quaterniond q = turn.mul(ship.orientation());
        Vec3 plotCentre = plotCentre(ship);
        Vec3 worldCentre = ship.toWorld(plotCentre);
        ship.placeAt(plotCentre, new Vec3(pos.x(), worldCentre.y, pos.z()), q);
    }

    /** The plot y of the template's waterline row (decks are searched from there up). */
    private static int waterlinePlotY(ShipBody ship, ShipTemplate template, ShipTemplatePlacer.Result r) {
        BlockPos helmPlot = ShipHelm.steering(ship);
        Optional<BlockPos> helmLocal = template.helm();
        if (helmPlot != null && helmLocal.isPresent()) {
            return helmPlot.getY() - (helmLocal.get().getY() - template.waterlineFor(helmLocal.get()));
        }
        return ship.plotBounds()[0].getY() + template.waterlineFor(helmLocal.orElse(null));
    }

    private static void raiseFlag(ShipBody ship, Voyage v) {
        FlagKind kind = switch (v.faction()) {
            case MERCHANTS -> FlagKind.MERCHANT;
            case NAVY -> FlagKind.NAVY;
            case PIRATES -> FlagKind.JOLLY_ROGER;
        };
        List<FlagpoleBlockEntity> poles = ShipAllegiance.poles(ship);
        if (!poles.isEmpty()) {
            poles.get(0).commandSet(kind, false, null);
            ShipAllegiance.refresh(ship);
        }
    }

    /** A name for the ship, picked by the voyage id from its faction's list. */
    static String name(Voyage v) {
        List<String> names = switch (v.faction()) {
            case MERCHANTS -> MERCHANT_NAMES;
            case NAVY -> NAVY_NAMES;
            case PIRATES -> PIRATE_NAMES;
        };
        return names.get(Math.floorMod(v.id().hashCode(), names.size()));
    }

    /**
     * The water surface at {@code (x, z)}: the top block is a water source open to the sky with at least two more water
     * blocks below it (a sloop draws two). Empty on land, ice, shallows or under a roof.
     */
    public static OptionalInt surfaceY(ServerLevel level, double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz);
        BlockPos surface = new BlockPos(bx, top - 1, bz);
        FluidState water = level.getFluidState(surface);
        // the heightmap's top is open to the sky already (no roof, no ice); sky light is not trusted, it updates late
        if (!water.is(FluidTags.WATER) || !water.isSource()) return OptionalInt.empty();
        for (int d = 1; d <= 2; d++) {
            if (!level.getFluidState(surface.below(d)).is(FluidTags.WATER)) return OptionalInt.empty();
        }
        return OptionalInt.of(surface.getY());
    }

    /** Gives {@code ship} the course over the waypoints ahead of the voyage's progress. */
    static HelmCourses.SetResult setCourse(ServerLevel level, ShipBody ship, Voyage v) {
        List<Vec3> points = new ArrayList<>();
        int y = level.getSeaLevel();
        for (Lane.Point p : RouteMath.ahead(v.waypoints(), v.progress())) points.add(p.at(y));
        return HelmCourses.set(level, ship, new CourseOrder(points, false));
    }

    // ------------------------------------------------------------------ update

    /**
     * Updates materialised voyage {@code id} once: course events, endings, plunder, its course (set again after a
     * reload or when the helmsman was replaced), its progress, and dematerialisation when nobody is near. Returns true
     * when its ship was removed. Also the GameTest entry.
     */
    public static boolean update(MinecraftServer server, UUID id) {
        Optional<Voyage> found = Voyages.get(server, id);
        if (found.isEmpty() || found.get().state() != Voyage.State.MATERIALISED) {
            VoyageShips.remove(id);
            PENDING.remove(id);
            return false;
        }
        Voyage v = found.get();
        ServerLevel level = levelOf(server, v);
        long now = level.getGameTime();
        VoyageShips.Active a = VoyageShips.get(id);
        if (a == null) {
            if (v.shipId().isEmpty()) {
                Voyages.update(server, v.withState(Voyage.State.SAILING, Optional.empty()));
                return false;
            }
            a = new VoyageShips.Active(id, v.shipId().get(), level.dimension(), now); // after a reload, ship not loaded yet
            VoyageShips.put(a);
        }
        int dt = (int) Math.max(0, Math.min(1200, now - a.lastUpdate));
        a.lastUpdate = now;
        ShipBody ship = SableShips.byId(level, a.ship);
        Vec3 at = ship != null ? centre(ship) : v.position().at(level.getSeaLevel());
        if (nearestPlayer(level, at.x, at.z) <= lingerRadius(server)) a.lastNear = now;
        boolean lingered = now - a.lastNear >= MaterializeConfig.LINGER_TICKS.get();
        if (ship == null) {
            if (ShipRegistry.get(server).find(a.ship).isEmpty()) {
                // destroyed for good (every block shot away, or removed by someone else): lost at sea
                VoyageEndings.lost(server, level, v, a.ship);
            } else if (lingered) {
                letGoUnloaded(server, v);
            }
            return false;
        }
        CourseEvent.Type event = PENDING.remove(id);
        if (event == CourseEvent.Type.ARRIVED) {
            arrive(server, id);
            return true;
        }
        if (event == CourseEvent.Type.STUCK) {
            skipAhead(server, id);
            return true;
        }
        if (VoyageEndings.check(server, level, v, a, ship, dt)) return false;
        v = Voyages.get(server, id).orElse(v);
        if (HelmCourses.course(ship.id()) == null && !VoyageCrew.alive(level, ship, id, VoyageCrew.CREW_TAG).isEmpty()) {
            setCourse(level, ship, v);
        }
        double progress = RouteMath.project(v.waypoints(), at.x, at.z, v.progress());
        if (Math.abs(progress - v.progress()) > 0.5) Voyages.update(server, v.withProgress(progress));
        if (lingered) {
            dematerialize(server, id);
            return true;
        }
        return false;
    }

    /** Distance from the player within which a materialised ship stays real. */
    static double lingerRadius(MinecraftServer server) {
        return MaterializeConfig.radius(server) + MaterializeConfig.LINGER_MARGIN.get();
    }

    // ------------------------------------------------------------------ dematerialise

    /**
     * Turns materialised voyage {@code id} back into a SAILING record now (linger, commands, GameTests): progress by the
     * ship's position on the route, health from the flooded fraction, cargo from the containers, crew and fighters
     * counted; its people discarded and the ship removed. False when it was not materialised.
     */
    public static boolean dematerialize(MinecraftServer server, UUID id) {
        Optional<Voyage> found = Voyages.get(server, id);
        if (found.isEmpty() || found.get().state() != Voyage.State.MATERIALISED) return false;
        Voyage v = found.get();
        ServerLevel level = levelOf(server, v);
        VoyageShips.Active a = VoyageShips.get(id);
        UUID shipId = a != null ? a.ship : v.shipId().orElse(null);
        ShipBody ship = shipId == null ? null : SableShips.byId(level, shipId);
        if (ship == null) {
            letGoUnloaded(server, v);
            return true;
        }
        Vec3 c = centre(ship);
        double progress = RouteMath.project(v.waypoints(), c.x, c.z, v.progress());
        double health = 1.0 - VoyageEndings.floodFraction(level, ship);
        Map<net.minecraft.resources.ResourceLocation, Integer> cargo = VoyageShips.plus(VoyageShips.read(level, ship),
                a == null ? Map.of() : a.overflow);
        int crew = VoyageCrew.alive(level, ship, id, VoyageCrew.CREW_TAG).size();
        int fighters = VoyageCrew.alive(level, ship, id, VoyageCrew.FIGHTER_TAG).size();
        VoyageShips.remove(id);
        PENDING.remove(id);
        HelmCourses.clear(level, ship.id());
        JobBoard.clear(ship.id());
        VoyageCrew.discard(level, id);
        VoyageEndings.forgetShip(ship.id());
        VoyageShips.clearLink(ship);
        SableShips.remove(ship);
        Voyages.update(server, v.withProgress(progress).withHealth(health).withCargo(cargo).withCrew(crew, fighters)
                .withState(Voyage.State.SAILING, Optional.empty()));
        Constants.LOG.debug("Voyage {} dematerialised at {} of {} blocks", v.shortId(), (int) progress, (int) v.length());
        return true;
    }

    /** The ship is not loaded: the record sails on from where it was; the body goes when it loads again. */
    private static void letGoUnloaded(MinecraftServer server, Voyage v) {
        VoyageShips.remove(v.id());
        PENDING.remove(v.id());
        Voyages.update(server, v.withState(Voyage.State.SAILING, Optional.empty()));
        Constants.LOG.debug("Voyage {} let go while its ship was unloaded", v.shortId());
    }

    /** At the last waypoint: back to a record at the end of the route, and arrived. */
    private static void arrive(MinecraftServer server, UUID id) {
        dematerialize(server, id);
        Voyages.get(server, id).ifPresent(v -> {
            Voyage end = v.withProgress(v.length());
            Voyages.update(server, end);
            Voyages.arrive(server, end);
        });
    }

    /** Stuck (an island, a headwind): back to a record at the waypoint it was heading for; appears again later. */
    private static void skipAhead(MinecraftServer server, UUID id) {
        dematerialize(server, id);
        Voyages.get(server, id).ifPresent(v -> {
            double target = RouteMath.progressAt(v.waypoints(), v.legIndex() + 1);
            Voyage next = v.withProgress(Math.max(v.progress(), target));
            Voyages.update(server, next);
            Constants.LOG.debug("Voyage {} was stuck and skips to {} blocks", v.shortId(), (int) next.progress());
            if (next.arrived()) Voyages.arrive(server, next);
            else retryLater(server, id);
        });
    }

    // ------------------------------------------------------------------ reload, removal, events

    /**
     * Looks at every loaded ship with a {@link VoyageLink}: a ship of a MATERIALISED voyage that is not linked in memory
     * (after a reload) is adopted; a ship whose voyage moved on without it or ended is removed with its people.
     * Returns true when a ship was removed.
     */
    static boolean adopt(MinecraftServer server) {
        boolean removed = false;
        for (ServerLevel level : server.getAllLevels()) {
            for (ShipBody ship : SableShips.all(level)) {
                if (ship.isRemoved() || VoyageShips.voyageOf(ship.id()).isPresent()) continue;
                Optional<VoyageLink> link = VoyageShips.readLink(ship);
                if (link.isEmpty()) continue;
                Optional<Voyage> v = Voyages.get(server, link.get().voyage());
                if (v.isPresent() && v.get().state() == Voyage.State.MATERIALISED && v.get().shipId().equals(Optional.of(ship.id()))) {
                    VoyageShips.Active a = new VoyageShips.Active(v.get().id(), ship.id(), level.dimension(), level.getGameTime());
                    a.plundered = link.get().plundered();
                    a.overflow = link.get().overflow();
                    VoyageShips.put(a);
                    Constants.LOG.debug("Voyage {} adopted its ship {} again", v.get().shortId(), ship.id());
                } else {
                    Constants.LOG.debug("Removing ship {}: its voyage {} is not at sea with it any more", ship.id(), link.get().voyage());
                    VoyageCrew.discard(level, link.get().voyage());
                    HelmCourses.clear(level, ship.id());
                    JobBoard.clear(ship.id());
                    SableShips.remove(ship);
                    removed = true;
                }
            }
        }
        return removed;
    }

    /** {@code HelmCourses} listener: arrivals and stuck ships are handled in the voyage's next update. */
    public static void onCourseEvent(CourseEvent e) {
        if (e.type() != CourseEvent.Type.ARRIVED && e.type() != CourseEvent.Type.STUCK) return;
        VoyageShips.voyageOf(e.ship()).ifPresent(id -> PENDING.merge(id, e.type(),
                (old, now) -> old == CourseEvent.Type.ARRIVED ? old : now));
    }

    /** {@code Voyages.onEnd}: a voyage that ends while its ship is linked (command, lost lane) takes the ship along. */
    public static void onVoyageEnded(MinecraftServer server, Voyage voyage, VoyageEnd reason) {
        VoyageShips.Active a = VoyageShips.get(voyage.id());
        PENDING.remove(voyage.id());
        RETRY_AFTER.remove(voyage.id());
        if (a == null) return;
        VoyageShips.remove(voyage.id());
        ServerLevel level = levelOf(server, voyage);
        VoyageCrew.discard(level, voyage.id());
        ShipBody ship = SableShips.byId(level, a.ship);
        if (ship != null) {
            HelmCourses.clear(level, ship.id());
            JobBoard.clear(ship.id());
            SableShips.remove(ship);
        }
    }

    /** {@code ShipSplits} listener: when the ship's identity moves to a new body the voyage follows it. */
    public static void onSplit(ShipSplits.SplitEvent event) {
        if (event.keeper().equals(event.parent())) return;
        Optional<UUID> voyage = VoyageShips.voyageOf(event.parent());
        if (voyage.isEmpty()) return;
        VoyageShips.Active a = VoyageShips.get(voyage.get());
        if (a == null) return;
        VoyageShips.relink(a, event.keeper());
        MinecraftServer server = event.level().getServer();
        Voyages.get(server, voyage.get()).ifPresent(v -> Voyages.update(server, v.withState(Voyage.State.MATERIALISED, Optional.of(event.keeper()))));
    }

    /** Server stop: everything in memory goes; the records and user data stay. */
    public static void onServerStopped() {
        VoyageShips.clear();
        VoyageChunks.clear();
        VoyageEndings.clear();
        RETRY_AFTER.clear();
        PENDING.clear();
    }

    // ------------------------------------------------------------------ helpers

    private static void retryLater(MinecraftServer server, UUID id) {
        RETRY_AFTER.put(id, server.overworld().getGameTime() + MaterializeConfig.RETRY_TICKS.get());
    }

    /** The level a voyage sails in: its origin port's, else the overworld. */
    public static ServerLevel levelOf(MinecraftServer server, Voyage v) {
        return PortRegistry.get(server).index().byId(v.from()).map(p -> server.getLevel(p.dimension()))
                .filter(java.util.Objects::nonNull).orElse(server.overworld());
    }

    /** Horizontal distance to the nearest player (not spectating) in {@code level}; MAX_VALUE without one. */
    static double nearestPlayer(ServerLevel level, double x, double z) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            best = Math.min(best, Math.hypot(p.getX() - x, p.getZ() - z));
        }
        return best;
    }

    static Vec3 plotCentre(ShipBody ship) {
        BlockPos[] b = ship.plotBounds();
        return Vec3.atLowerCornerOf(b[0]).add(Vec3.atLowerCornerOf(b[1]).add(1, 1, 1)).scale(0.5);
    }

    /** The world position of the centre of the ship's plot bounds. */
    public static Vec3 centre(ShipBody ship) {
        return ship.toWorld(plotCentre(ship));
    }

    /** Ships of voyages near a position (commands). */
    static @Nullable Voyage voyageOfShip(MinecraftServer server, UUID ship) {
        return VoyageShips.voyageOf(ship).flatMap(id -> Voyages.get(server, id)).orElse(null);
    }
}

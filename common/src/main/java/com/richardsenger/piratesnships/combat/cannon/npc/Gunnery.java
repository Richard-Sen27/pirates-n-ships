package com.richardsenger.piratesnships.combat.cannon.npc;

import com.richardsenger.piratesnships.combat.cannon.CannonBlock;
import com.richardsenger.piratesnships.combat.cannon.CannonBlockEntity;
import com.richardsenger.piratesnships.combat.cannon.CannonConfig;
import com.richardsenger.piratesnships.combat.cannon.CannonLoad;
import com.richardsenger.piratesnships.combat.cannon.CannonRules;
import com.richardsenger.piratesnships.combat.cannon.CannonService;
import com.richardsenger.piratesnships.combat.cannon.CannonStation.CannonOrder;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.law.flag.ShipStance;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.decor.flag.FlagReading;
import com.richardsenger.piratesnships.ship.decor.flag.ShipAllegiance;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * NPC gunnery (WS4a, docs/design.md §8.2, §10.4): the gun crews of a ship aim and fire by themselves. A ship's
 * {@link GunneryState} is set by the whistle's "Fire at will" ({@link CannonOrder#FIRE_AT_WILL}), by
 * {@code /pirates crew order fire_at_will}, or by AI ({@link #set}); "Release crew" turns it off.
 *
 * <p>Every level tick, each crew-manned cannon of an engaged ship whose turn has come (every
 * {@code cannons.npc.aim_interval_ticks}) picks the nearest target its arc and range allow ({@link GunCrewAim}): any
 * hostile ship ({@link ShipHostility}) within {@code engage_range} when firing at will, else the chosen ship. The crew
 * then either turns the barrel one elevation step toward the best elevation ({@link CannonService#aim}), starts a load
 * when the gun is empty, or, aimed and loaded with a shot that strikes a solid block of the target (not a breach,
 * WS4a-b), is given {@link CannonOrder#FIRE} through the station (the fuse, the shot
 * and the auto-reload are the station's as for a captain's "Fire!"), at most once per {@code fire_interval_ticks}.
 * A ship that has struck its colours holds fire, and no ship that has struck its colours is fired upon.
 *
 * <p>Crew shots have no owner (G9), so no crime attaches to the shooter; the law charges the firing ship's owner for
 * hitting a neutral or surrendered ship as for any crew shot ({@code law.world.FlagCrimes}). A ball fired by this loop
 * breaks {@code npc_block_damage_multiplier} times the blocks ({@link #takeShot}).
 *
 * <p>State is kept in memory only: after a restart a player gives "Fire at will" again, AI sets its targets again.
 */
public final class Gunnery {

    private static final Map<UUID, Engagement> SHIPS = new ConcurrentHashMap<>();
    /** Stations whose running "Fire!" was given by this loop: their ball gets the NPC block damage factor. */
    private static final Set<StationRef> OWN_SHOTS = ConcurrentHashMap.newKeySet();

    private static final class Engagement {
        final ResourceKey<Level> dimension;
        volatile GunneryState state;
        final Map<BlockPos, Gun> guns = new ConcurrentHashMap<>();
        volatile @Nullable UUID lastTarget;

        Engagement(ResourceKey<Level> dimension, GunneryState state) {
            this.dimension = dimension;
            this.state = state;
        }
    }

    /** One crewed gun's timers and its current target. */
    private static final class Gun {
        long nextCheck;
        long nextFire;
        @Nullable UUID target;
    }

    /** A candidate target ship as the crews see it this tick. */
    private record Candidate(UUID id, GunCrewAim.Target target) {
    }

    private Gunnery() {
    }

    /** Subscribes the level tick and the clean-ups. Called from {@code CannonModule#registerEvents}. */
    public static void register() {
        CommonEvents.LEVEL_TICK_END.register(Gunnery::onLevelTick);
        CommonEvents.SERVER_STOPPED.register(server -> {
            SHIPS.clear();
            OWN_SHOTS.clear();
        });
        SableShips.onShipRemoved((level, ship, destroyed) -> {
            if (destroyed) SHIPS.remove(ship);
        });
        GunCrewPoses.register(); // ART7: the gun crew's poses
    }

    // ---- API --------------------------------------------------------------------------------------------------------

    /** Sets what the gun crews of {@code ship} do on their own; {@link GunneryState#OFF} clears it. */
    public static void set(ServerLevel level, ShipBody ship, GunneryState state) {
        if (state.isOff()) {
            clear(ship.id());
            return;
        }
        Engagement e = SHIPS.computeIfAbsent(ship.id(), id -> new Engagement(level.dimension(), state));
        if (!state.equals(e.state)) {
            e.state = state;
            e.guns.clear(); // a new order: every crew looks again at once
            e.lastTarget = null;
        }
    }

    /** Stops the ship's crews firing on their own (they still obey a captain's "Fire!"). */
    public static void clear(ShipBody ship) {
        clear(ship.id());
    }

    public static void clear(UUID ship) {
        SHIPS.remove(ship);
    }

    /** What the gun crews of the ship do on their own; {@link GunneryState#OFF} when nothing is set. */
    public static GunneryState state(UUID ship) {
        Engagement e = SHIPS.get(ship);
        return e == null ? GunneryState.OFF : e.state;
    }

    /** The ship one of {@code ship}'s crews aimed at last, while its gunnery is on; empty when none has a target. */
    public static Optional<UUID> engaged(ShipBody ship) {
        Engagement e = SHIPS.get(ship.id());
        return e == null ? Optional.empty() : Optional.ofNullable(e.lastTarget);
    }

    /**
     * Whether the crew at the cannon {@code station} (its master) has a target now, so it is aiming (ART7, the
     * {@code cannon_aim} pose); false while the ship's gunnery is off.
     */
    public static boolean aiming(StationRef station) {
        Engagement e = SHIPS.get(station.ship());
        Gun gun = e == null ? null : e.guns.get(station.pos());
        return gun != null && gun.target != null;
    }

    /**
     * Whether the "Fire!" just finished at {@code station} was given by this loop (and forgets it): its ball breaks
     * {@code npc_block_damage_multiplier} times the blocks. Asked by {@code CannonStation#complete}.
     */
    public static boolean takeShot(StationRef station) {
        return OWN_SHOTS.remove(station);
    }

    /** The block damage factor of a crew shot ({@code takeShot} true) or of any other shot (1). */
    public static double blockDamageFactor(boolean ownShot) {
        return ownShot ? GunneryConfig.NPC_BLOCK_DAMAGE_MULTIPLIER.get() : 1.0;
    }

    // ---- the loop -----------------------------------------------------------------------------------------------------

    static void onLevelTick(ServerLevel level) {
        if (SHIPS.isEmpty() || !GunneryConfig.ENABLED.get() || !CannonConfig.ENABLED.get()) return;
        long now = level.getGameTime();
        for (Map.Entry<UUID, Engagement> me : SHIPS.entrySet()) {
            Engagement e = me.getValue();
            if (e.dimension != level.dimension()) continue;
            ShipBody self = SableShips.byId(level, me.getKey());
            if (self == null) continue; // unloaded: the state waits
            tick(level, self, e, now);
        }
    }

    private static void tick(ServerLevel level, ShipBody self, Engagement e, long now) {
        if (ShipAllegiance.of(self).isStruck() || ShipSplits.isWreck(self)) return; // surrendered or wrecked: hold fire
        List<StationRef> guns = crewedGuns(level, self);
        if (guns.isEmpty()) return;
        List<Candidate> candidates = null;
        int interval = GunneryConfig.AIM_INTERVAL_TICKS.get();
        for (StationRef ref : guns) {
            Gun gun = e.guns.computeIfAbsent(ref.pos(), p -> new Gun());
            if (now < gun.nextCheck) continue;
            gun.nextCheck = now + interval;
            if (candidates == null) candidates = candidates(level, self, e.state);
            work(level, self, e, ref, gun, candidates, now);
        }
        e.guns.keySet().removeIf(p -> guns.stream().noneMatch(r -> r.pos().equals(p)));
    }

    /** The cannon masters of the ship manned by a crew member (players at a gun aim for themselves). */
    private static List<StationRef> crewedGuns(ServerLevel level, ShipBody self) {
        Set<StationRef> out = new LinkedHashSet<>();
        for (CrewMember c : CrewStations.crewOf(level, self.id())) {
            StationRef ref = c.assignment();
            if (ref != null && CannonBlock.isMaster(level.getBlockState(ref.pos()))) out.add(ref);
        }
        return new ArrayList<>(out);
    }

    /** The ships the crews may fire at this tick, by the ship's state. */
    private static List<Candidate> candidates(ServerLevel level, ShipBody self, GunneryState state) {
        List<Candidate> out = new ArrayList<>();
        MinecraftServer server = level.getServer();
        ShipRegistry registry = ShipRegistry.get(server);
        if (state.mode() == GunneryState.Mode.TARGET) {
            ShipBody target = SableShips.byId(level, state.target());
            if (target != null && !target.isRemoved() && !ShipAllegiance.of(target).isStruck() && !ShipSplits.isWreck(target)) {
                out.add(candidate(level, target));
            }
            return out;
        }
        Faction faction = ShipHostility.factionOf(ShipAllegiance.of(self));
        Optional<UUID> selfOwner = registry.find(self.id()).flatMap(ShipData::owner);
        Vec3 centre = self.worldBounds().getCenter();
        double range = GunneryConfig.ENGAGE_RANGE.get();
        long lawNow = LawService.now(server);
        for (ShipBody other : SableShips.all(level)) {
            if (other.id().equals(self.id()) || other.isRemoved() || ShipSplits.isWreck(other)) continue;
            AABB bounds = other.worldBounds();
            Vec3 c = bounds.getCenter();
            if (Math.hypot(c.x - centre.x, c.z - centre.z) > range) continue;
            Optional<ShipData> data = registry.find(other.id());
            if (data.isEmpty()) continue;
            Optional<UUID> owner = data.get().owner();
            if (owner.isPresent() && owner.equals(selfOwner)) continue; // never the own fleet
            FlagReading flag = data.get().flag();
            ShipStance stance = ShipStance.of(flag.shown(), flag.isStruck(), data.get().coverBlown(lawNow));
            boolean wanted = owner.map(id -> ownerWanted(server, id)).orElse(false);
            if (ShipHostility.hostile(faction, flag, wanted, stance)) out.add(candidate(level, other));
        }
        return out;
    }

    /** The owner has a bounty, or is loaded and wanted at {@code law.world.navy_hostility_threshold}. */
    private static boolean ownerWanted(MinecraftServer server, UUID owner) {
        if (LawService.hasBounty(server, owner)) return true;
        LivingEntity e = LawService.findLoaded(server, owner);
        return e != null && LawConfig.CRIMINAL_SCORE_ENABLED.get()
                && LawService.wantedLevel(e).atLeast(LawConfig.NAVY_HOSTILITY_THRESHOLD.get());
    }

    private static Candidate candidate(ServerLevel level, ShipBody ship) {
        Vector3d v = ship.linearVelocity();
        return new Candidate(ship.id(), new GunCrewAim.Target(ship.worldBounds(), new Vec3(v.x, v.y, v.z).scale(1.0 / 20.0),
                solidAt(level, ship)));
    }

    /**
     * Whether a world point lies inside a block of {@code ship} that stops a ball: a block of its plot whose collision
     * shape contains the point (a ball's clip uses the collision shape, {@code ProjectileUtil} with
     * {@code ClipContext.Block.COLLIDER}). A breach the ship's own guns or anyone else knocked open is no hit (WS4a-b).
     */
    static Predicate<Vec3> solidAt(ServerLevel level, ShipBody ship) {
        return world -> {
            Vec3 plot = ship.toPlot(world);
            BlockPos pos = BlockPos.containing(plot);
            if (!ship.plotContains(pos)) return false;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) return false;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) return false;
            Vec3 in = plot.subtract(pos.getX(), pos.getY(), pos.getZ());
            for (AABB box : shape.toAabbs()) {
                if (box.contains(in)) return true;
            }
            return false;
        };
    }

    /** One crew's move: pick a target, then step the barrel, load, or fire. */
    private static void work(ServerLevel level, ShipBody self, Engagement e, StationRef ref, Gun gun, List<Candidate> candidates, long now) {
        BlockPos pos = ref.pos();
        BlockState state = level.getBlockState(pos);
        if (!(level.getBlockEntity(pos) instanceof CannonBlockEntity be)) return;
        List<GunCrewAim.Shot> steps = steps(self, pos, state.getValue(CannonBlock.FACING));
        GunCrewAim.Ballistics ballistics = ballistics();
        GunCrewAim.Rules rules = rules();

        Candidate chosen = null;
        GunCrewAim.Solution solution = null;
        for (Candidate c : candidates) {
            GunCrewAim.Solution s = GunCrewAim.solve(steps, c.target(), ballistics, rules);
            if (!s.engageable()) continue;
            if (c.id().equals(gun.target)) { // stay on the target already engaged while it is in the arc
                chosen = c;
                solution = s;
                break;
            }
            if (solution == null || s.distance() < solution.distance()) { // else the nearest
                chosen = c;
                solution = s;
            }
        }
        gun.target = chosen == null ? null : chosen.id();
        if (chosen == null) return;
        e.lastTarget = chosen.id();

        int step = GunCrewAim.stepToward(be.elevationStep(), solution.bestStep());
        if (step != 0) {
            CannonService.aim(level, pos, step > 0);
            return;
        }
        StationState<Object> st = Stations.state(ref);
        if (st == null || st.occupant() == null || st.occupant().player() || st.order() != null) return; // busy
        CannonLoad load = state.getValue(CannonBlock.LOAD);
        if (!CannonRules.canFire(load)) {
            if (CannonConfig.CREW_ENABLED.get()) Stations.order(level, ref, CannonOrder.LOAD);
            return;
        }
        if (!solution.hits() || now < gun.nextFire) return;
        if (Stations.order(level, ref, CannonOrder.FIRE) == Stations.OrderResult.STARTED) {
            OWN_SHOTS.add(ref);
            gun.nextFire = now + GunneryConfig.FIRE_INTERVAL_TICKS.get();
        }
    }

    /** Every elevation step of the cannon at {@code pos} as a world-space shot from the moving ship. */
    static List<GunCrewAim.Shot> steps(ShipBody self, BlockPos pos, Direction facing) {
        int n = CannonConfig.ELEVATION_STEPS.get();
        List<GunCrewAim.Shot> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            CannonService.Barrel local = CannonService.barrel(pos, facing, CannonConfig.elevationDegrees(i));
            out.add(GunCrewAim.worldShot(local.muzzle(), local.direction(), self::toWorld, self.orientation(),
                    self.velocityAt(local.muzzle())));
        }
        return out;
    }

    /** What the crew at {@code cannon} sees of {@code target} now, for test messages and debugging. */
    static String explain(ServerLevel level, ShipBody self, BlockPos cannon, ShipBody target) {
        BlockState state = level.getBlockState(cannon);
        if (!(level.getBlockEntity(cannon) instanceof CannonBlockEntity be)) return "no cannon at " + cannon;
        Candidate c = candidate(level, target);
        GunCrewAim.Solution s = GunCrewAim.solve(steps(self, cannon, state.getValue(CannonBlock.FACING)), c.target(),
                ballistics(), rules());
        Engagement e = SHIPS.get(self.id());
        StationState<Object> st = Stations.state(new StationRef(self.id(), cannon));
        return "state " + state(self.id()) + ", step " + be.elevationStep() + ", load " + state.getValue(CannonBlock.LOAD)
                + ", station " + (st == null ? "none" : st.phase() + "/" + st.order()) + ", target bounds " + c.target().bounds()
                + ", " + s + ", gun target " + (e == null ? "-" : e.guns.get(cannon) == null ? "-" : e.guns.get(cannon).target);
    }

    static GunCrewAim.Ballistics ballistics() {
        return new GunCrewAim.Ballistics(CannonConfig.MUZZLE_VELOCITY.get(), CannonConfig.GRAVITY.get(),
                CannonConfig.BALL_LIFETIME_TICKS.get());
    }

    static GunCrewAim.Rules rules() {
        return new GunCrewAim.Rules(GunneryConfig.ARC_DEGREES.get(), GunneryConfig.ENGAGE_RANGE.get(),
                GunneryConfig.AIM_HEIGHT.get());
    }
}

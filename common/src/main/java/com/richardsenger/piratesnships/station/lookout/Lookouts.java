package com.richardsenger.piratesnships.station.lookout;

import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.mob.entity.Shark;
import com.richardsenger.piratesnships.mob.kraken.Kraken;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.sailing.ship.BowFrame;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.decor.flag.FlagReading;
import com.richardsenger.piratesnships.ship.decor.flag.ShipAllegiance;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.winch.CaptainsWhistleItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * The lookout's watch (CN1, docs/design.md §6 "Crow's nest", §7): every {@code lookout.scan_interval_ticks} each ship
 * with a manned crow's nest (a crew member seated at a {@link CrowsNestBlock}, or a player standing in one) scans
 * {@code lookout.range} blocks round the nest for
 * <ul>
 *   <li>other ships (every loaded Sable body but its own; described by its wreck state and the flag it flies),</li>
 *   <li>land ({@link LandSampler}: rays round the compass, the nearest column whose surface at sea level is not water
 *       or ice; only in loaded chunks),</li>
 *   <li>sharks and the kraken,</li>
 * </ul>
 * and calls each new sighting ({@link SightingMemory}, per ship, {@code lookout.memory_ticks}) to the players aboard
 * and the players in its nests (LKO2): a {@link SightingPayload} with the bearing relative to the ship's heading and
 * the distance, which the client shows as a short action-bar line ({@link SightingCue}), a hail from the nest and a mark
 * on the ship HUD's compass rose. No chat line. A sighting still in view is called again once it came
 * {@code lookout.recall_blocks} closer or {@code lookout.recall_seconds} after its last call. {@code lookout.announce}
 * off keeps the watch silent (sightings are still remembered). The memory is transient (lost on a restart, a sighting
 * is then called again once).
 */
public final class Lookouts {

    /** How far above and below the nest monsters are looked for. */
    private static final double MONSTER_HEIGHT = 64.0;

    /** Who keeps watch: a crow's nest (plot position) and its crew member or player. */
    public record Observer(BlockPos nest, @Nullable CrewMember crew, @Nullable Player player) {
    }

    public enum Kind { SHIP, LAND, SHARK, KRAKEN }

    /**
     * Something seen: its memory key, what it is, where, its bearing relative to the bow (degrees, positive to
     * starboard) and from north (degrees), and the called distance; {@code name} is a ship's name when the lookout knows
     * it by sight ({@link #renown}, TPL2), else empty.
     */
    public record Sighting(Kind kind, String key, SightingPayload.What what, Vec3 at, double relative, double compass, int distance,
                           String name) {

        /** The bearing in points, as CN1 called it. */
        public Bearings.Relative bearing() {
            return Bearings.points(relative);
        }

        Sighting named(String name) {
            return new Sighting(kind, key, what, at, relative, compass, distance, name == null ? "" : name);
        }
    }

    private static final List<BiFunction<ServerLevel, UUID, Optional<String>>> RENOWNED = new CopyOnWriteArrayList<>();

    /**
     * TPL2: adds a source of ships every lookout knows by sight (a pirate captain's ship: his name is on every tongue);
     * a sighted ship one of them names is called by that name ({@link LookoutLang#KEY_SHIP_NAMED}). The first answer
     * wins.
     */
    public static void renown(BiFunction<ServerLevel, UUID, Optional<String>> source) {
        RENOWNED.add(source);
    }

    /** The name a lookout calls the ship {@code ship} by, or empty when it is not a renowned ship. */
    public static String renownedName(ServerLevel level, UUID ship) {
        for (BiFunction<ServerLevel, UUID, Optional<String>> s : RENOWNED) {
            Optional<String> name = s.apply(level, ship);
            if (name.isPresent() && !name.get().isBlank()) return name.get();
        }
        return "";
    }

    /** A new sighting of {@code ship}'s lookout, its call and who hears it (empty while announcing is off). */
    public record Report(UUID ship, Sighting sighting, SightingPayload payload, List<UUID> recipients) {
    }

    private static final Map<UUID, SightingMemory> MEMORY = new ConcurrentHashMap<>();

    private Lookouts() {
    }

    /** Level tick: scans every {@code scan_interval_ticks} and sends the calls. */
    public static void onLevelTick(ServerLevel level) {
        if (!LookoutConfig.ENABLED.get()) {
            return;
        }
        long now = level.getGameTime();
        if (now % LookoutConfig.SCAN_INTERVAL_TICKS.get() != 0) {
            return;
        }
        List<ServerPlayer> players = level.players();
        for (Report r : scanAll(level, players, now)) {
            deliver(r, players, (p, payload) -> {
                if (p instanceof ServerPlayer sp) Services.NETWORK.sendToPlayer(sp, payload);
            });
        }
    }

    /** Scans every ship with a manned nest; {@code players} are the players that may stand in a nest or aboard. */
    public static List<Report> scanAll(ServerLevel level, List<? extends Player> players, long now) {
        List<Report> out = new ArrayList<>();
        if (!LookoutConfig.ENABLED.get()) {
            return out;
        }
        for (Map.Entry<UUID, List<Observer>> e : observers(level, players).entrySet()) {
            ShipBody ship = SableShips.byId(level, e.getKey());
            if (ship != null) {
                out.addAll(scan(level, ship, e.getValue(), players, now));
            }
        }
        return out;
    }

    /** The lookouts per ship: crew members seated at a crow's nest, and players standing in one. */
    public static Map<UUID, List<Observer>> observers(ServerLevel level, List<? extends Player> players) {
        Map<UUID, List<Observer>> out = new LinkedHashMap<>();
        for (ShipBody ship : SableShips.all(level)) {
            for (CrewMember c : CrewStations.crewOf(level, ship.id())) {
                StationRef ref = c.assignment();
                if (ref != null && c.isAtStation() && level.getBlockState(ref.pos()).getBlock() instanceof CrowsNestBlock) {
                    out.computeIfAbsent(ship.id(), k -> new ArrayList<>()).add(new Observer(ref.pos(), c, null));
                }
            }
        }
        for (Player p : players) {
            Nest n = nestOf(level, p);
            if (n != null) {
                out.computeIfAbsent(n.ship().id(), k -> new ArrayList<>()).add(new Observer(n.pos(), null, p));
            }
        }
        return out;
    }

    /** A crow's nest on a ship. */
    public record Nest(ShipBody ship, BlockPos pos) {
    }

    /**
     * The crow's nest {@code entity} stands in (its feet in the nest's block or just above it, mapped into the ship's
     * plot), or null. Works on the entity's position only, so it needs no Sable standing state.
     */
    public static @Nullable Nest nestOf(ServerLevel level, Entity entity) {
        Vec3 feet = entity.position();
        for (ShipBody ship : SableShips.all(level)) {
            if (!CrewStations.worldBox(ship, 2).contains(feet)) {
                continue;
            }
            BlockPos p = nestOn(level, ship, entity);
            if (p != null) {
                return new Nest(ship, p);
            }
        }
        return null;
    }

    /** The plot position of {@code ship}'s crow's nest {@code entity} stands in (feet in it or just above it), or null. */
    private static @Nullable BlockPos nestOn(ServerLevel level, ShipBody ship, Entity entity) {
        BlockPos local = BlockPos.containing(ship.toPlot(entity.position().add(0, 0.05, 0)));
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos p = local.below(dy);
            if (level.getBlockState(p).getBlock() instanceof CrowsNestBlock) {
                return p;
            }
        }
        return null;
    }

    /**
     * LAW4: whether {@code ship} has a manned crow's nest: a crew member of the ship seated at one of its crow's nests,
     * or a living, non-spectator player (any {@link Player} in the level, mock players included) standing in one. Two
     * entity lookups in a box round the ship and a block read per candidate, so cheap enough per observation; it reads
     * no lookout config (the watch's {@code lookout.enabled} only silences the calls, the nest is manned all the same).
     */
    public static boolean isManned(ServerLevel level, ShipBody ship) {
        UUID id = ship.id();
        for (CrewMember c : level.getEntitiesOfClass(CrewMember.class, CrewStations.worldBox(ship, 4),
                c -> c.isAtStation() && c.assignment() != null && c.assignment().ship().equals(id))) {
            if (level.getBlockState(c.assignment().pos()).getBlock() instanceof CrowsNestBlock) {
                return true;
            }
        }
        for (Player p : level.getEntitiesOfClass(Player.class, CrewStations.worldBox(ship, 2),
                p -> p.isAlive() && !p.isSpectator())) {
            if (nestOn(level, ship, p) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * One scan of {@code ship} from its first observer's nest; returns the new sightings as reports, nearest first,
     * and remembers everything seen.
     */
    public static List<Report> scan(ServerLevel level, ShipBody ship, List<Observer> observers, List<? extends Player> players,
                                    long now) {
        if (observers.isEmpty() || !LookoutConfig.ENABLED.get()) {
            return List.of();
        }
        Observer eye = observers.get(0);
        Vec3 at = ship.toWorld(Vec3.atCenterOf(eye.nest()));
        double heading = heading(ship);
        List<Sighting> seen = look(level, ship, at, heading, LookoutConfig.RANGE.get());
        SightingMemory memory = MEMORY.computeIfAbsent(ship.id(), k -> new SightingMemory());
        long remember = LookoutConfig.MEMORY_TICKS.get();
        int recallBlocks = LookoutConfig.RECALL_BLOCKS.get();
        long recallTicks = LookoutConfig.RECALL_SECONDS.get() * 20L;
        int fresh = LookoutConfig.MARK_SECONDS.get() * 20;
        List<Report> out = new ArrayList<>();
        List<UUID> recipients = LookoutConfig.ANNOUNCE.get() ? recipients(level, ship, observers, players) : List.of();
        synchronized (memory) {
            for (Sighting s : seen) {
                if (memory.sight(s.key(), now, remember, s.distance(), recallBlocks, recallTicks)) {
                    out.add(new Report(ship.id(), s, payload(ship.id(), s, at, fresh), recipients));
                }
            }
            memory.forget(now, remember);
        }
        return out;
    }

    /** Everything within {@code range} of {@code at}, nearest first (pure of memory). */
    static List<Sighting> look(ServerLevel level, ShipBody own, Vec3 at, double heading, double range) {
        List<Sighting> out = new ArrayList<>();
        for (ShipBody other : SableShips.all(level)) {
            if (other.id().equals(own.id())) {
                continue;
            }
            Vec3 c = CrewStations.worldBox(other, 0).getCenter();
            Sighting s = sighting(Kind.SHIP, "ship:" + other.id(), what(other), at, c, heading, range);
            if (s != null) out.add(s.named(renownedName(level, other.id())));
        }
        AABB box = new AABB(at, at).inflate(range, MONSTER_HEIGHT, range);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && (e instanceof Shark || e instanceof Kraken))) {
            Kind kind = e instanceof Kraken ? Kind.KRAKEN : Kind.SHARK;
            SightingPayload.What what = kind == Kind.KRAKEN ? SightingPayload.What.KRAKEN : SightingPayload.What.SHARK;
            Sighting s = sighting(kind, "entity:" + e.getUUID(), what, at, e.position(), heading, range);
            if (s != null) out.add(s);
        }
        LandSampler.Land land = LandSampler.nearest(at.x, at.z, range, LookoutConfig.LAND_DIRECTIONS.get(),
                LookoutConfig.LAND_STEP.get(), LookoutConfig.LAND_MIN_DISTANCE.get(), (x, z) -> column(level, x, z));
        if (land != null) {
            Sighting s = sighting(Kind.LAND, LandSampler.regionKey(land.x(), land.z(), LookoutConfig.LAND_REGION.get()),
                    SightingPayload.What.LAND,
                    at, new Vec3(land.x() + 0.5, at.y, land.z() + 0.5), heading, range);
            if (s != null) out.add(s);
        }
        out.sort(Comparator.comparingInt(Sighting::distance));
        return out;
    }

    private static @Nullable Sighting sighting(Kind kind, String key, SightingPayload.What what, Vec3 from, Vec3 to, double heading,
                                               double range) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > range) {
            return null;
        }
        double compass = Bearings.compass(dx, dz);
        return new Sighting(kind, key, what, to, Bearings.relative(heading, compass), compass, Bearings.calledDistance(d), "");
    }

    /** The call of {@code s} seen from the nest at {@code nest} (world), its mark kept {@code freshTicks}. */
    static SightingPayload payload(UUID ship, Sighting s, Vec3 nest, int freshTicks) {
        return new SightingPayload(ship, s.what(), s.name(), (float) s.relative(), (float) s.compass(), s.distance(), nest.x, nest.y,
                nest.z, s.key().hashCode(), freshTicks);
    }

    /** What the column (x, z) shows at sea level: water (or ice), land, or unknown when its chunk is not loaded. */
    static LandSampler.Column column(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return LandSampler.Column.UNKNOWN;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        if (top < level.getSeaLevel() - 1) {
            return LandSampler.Column.WATER; // under the sea (or nothing at all)
        }
        BlockState s = level.getBlockState(new BlockPos(x, top, z));
        return s.getFluidState().is(FluidTags.WATER) || s.is(BlockTags.ICE) ? LandSampler.Column.WATER : LandSampler.Column.LAND;
    }

    /** What another ship looks like: a wreck, or by the flag it flies. */
    static SightingPayload.What what(ShipBody ship) {
        FlagReading flag = ShipAllegiance.of(ship);
        return LookoutLang.what(flag.kind(), flag.status() == FlagReading.Status.FLYING, ShipSplits.isWreck(ship));
    }

    /**
     * ST5: a lookout in the nest faces the bow (a plot direction), the way its sightings are called; south before the
     * ship has a sailing state. Registered with {@code StationPoses.registerFacing} by {@link LookoutModule}.
     */
    static Vec3 facing(ServerLevel level, StationRef station, BlockPos spot) {
        SailingRuntime rt = SailingRuntimes.get(level, station.ship());
        BowFrame bow = rt == null ? BowFrame.SOUTH : rt.bow();
        Vector3d f = bow.toPlot(new Vector3d(0, 0, 1), new Vector3d());
        return new Vec3(f.x, 0, f.z);
    }

    /** Compass heading of the ship's bow (0 = north). */
    static double heading(ShipBody ship) {
        SailingRuntime rt = SailingRuntimes.get(ship.level(), ship.id());
        BowFrame bow = rt == null ? BowFrame.SOUTH : rt.bow();
        Quaterniond q = bow.shipToWorld(ship.orientation(new Quaterniond()), new Quaterniond());
        Vector3d f = q.transform(new Vector3d(0, 0, 1));
        return Bearings.compass(f.x, f.z);
    }

    /**
     * The players in its nests and the players aboard (LKO2: the call is a cue on the ship's HUD, so an owner ashore no
     * longer hears it).
     */
    static List<UUID> recipients(ServerLevel level, ShipBody ship, List<Observer> observers, List<? extends Player> players) {
        Set<UUID> out = new LinkedHashSet<>();
        for (Observer o : observers) {
            if (o.player() != null) out.add(o.player().getUUID());
        }
        for (Player p : players) {
            ShipBody on = CaptainsWhistleItem.shipOf(level, p);
            if (on != null && on.id().equals(ship.id())) out.add(p.getUUID());
        }
        return List.copyOf(out);
    }

    /**
     * Hands {@code r}'s call to {@code sink} for every recipient among {@code players} (the level's players in the game,
     * mock players in GameTests). Never a chat line.
     */
    public static void deliver(Report r, List<? extends Player> players, BiConsumer<Player, SightingPayload> sink) {
        if (r.recipients().isEmpty()) return;
        for (Player p : players) {
            if (r.recipients().contains(p.getUUID())) sink.accept(p, r.payload());
        }
    }

    /** Forgets what {@code ship}'s lookouts saw (tests, a ship gone for good). */
    public static void forget(UUID ship) {
        MEMORY.remove(ship);
    }

    public static void onShipRemoved(ServerLevel level, UUID ship, boolean destroyed) {
        if (destroyed) MEMORY.remove(ship);
    }

    public static void onServerStopped() {
        MEMORY.clear();
    }
}

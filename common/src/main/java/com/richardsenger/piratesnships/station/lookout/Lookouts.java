package com.richardsenger.piratesnships.station.lookout;

import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.mob.entity.Shark;
import com.richardsenger.piratesnships.mob.kraken.Kraken;
import com.richardsenger.piratesnships.sailing.ship.BowFrame;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * and calls each new sighting once ({@link SightingMemory}, per ship, {@code lookout.memory_ticks}): a chat line from
 * the lookout to the ship's owner (wherever he is), the players aboard and the players in its nests, with the bearing
 * relative to the ship's heading in points ({@link Bearings}) and the distance. {@code lookout.announce} off keeps the
 * watch silent (sightings are still remembered). The memory is transient (lost on a restart, a sighting is then called
 * again once).
 */
public final class Lookouts {

    /** How far above and below the nest monsters are looked for. */
    private static final double MONSTER_HEIGHT = 64.0;

    /** Who keeps watch: a crow's nest (plot position) and its crew member or player. */
    public record Observer(BlockPos nest, @Nullable CrewMember crew, @Nullable Player player) {
    }

    public enum Kind { SHIP, LAND, SHARK, KRAKEN }

    /** Something seen: its memory key, where, its bearing in points and the called distance. */
    public record Sighting(Kind kind, String key, String whatKey, Vec3 at, Bearings.Relative bearing, int distance) {
    }

    /** A new sighting of {@code ship}'s lookout, its chat line and who hears it (empty while announcing is off). */
    public record Report(UUID ship, Sighting sighting, Component line, List<UUID> recipients) {
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
        for (Report r : scanAll(level, level.players(), now)) {
            send(level, r);
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
            BlockPos local = BlockPos.containing(ship.toPlot(feet.add(0, 0.05, 0)));
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos p = local.below(dy);
                if (level.getBlockState(p).getBlock() instanceof CrowsNestBlock) {
                    return new Nest(ship, p);
                }
            }
        }
        return null;
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
        List<Report> out = new ArrayList<>();
        List<UUID> recipients = LookoutConfig.ANNOUNCE.get() ? recipients(level, ship, observers, players) : List.of();
        Component speaker = speaker(observers);
        synchronized (memory) {
            for (Sighting s : seen) {
                if (memory.sight(s.key(), now, remember)) {
                    out.add(new Report(ship.id(), s, line(speaker, s), recipients));
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
            Sighting s = sighting(Kind.SHIP, "ship:" + other.id(), whatKey(other), at, c, heading, range);
            if (s != null) out.add(s);
        }
        AABB box = new AABB(at, at).inflate(range, MONSTER_HEIGHT, range);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && (e instanceof Shark || e instanceof Kraken))) {
            Kind kind = e instanceof Kraken ? Kind.KRAKEN : Kind.SHARK;
            Sighting s = sighting(kind, "entity:" + e.getUUID(), "", at, e.position(), heading, range);
            if (s != null) out.add(s);
        }
        LandSampler.Land land = LandSampler.nearest(at.x, at.z, range, LookoutConfig.LAND_DIRECTIONS.get(),
                LookoutConfig.LAND_STEP.get(), LookoutConfig.LAND_MIN_DISTANCE.get(), (x, z) -> column(level, x, z));
        if (land != null) {
            Sighting s = sighting(Kind.LAND, LandSampler.regionKey(land.x(), land.z(), LookoutConfig.LAND_REGION.get()), "",
                    at, new Vec3(land.x() + 0.5, at.y, land.z() + 0.5), heading, range);
            if (s != null) out.add(s);
        }
        out.sort(Comparator.comparingInt(Sighting::distance));
        return out;
    }

    private static @Nullable Sighting sighting(Kind kind, String key, String what, Vec3 from, Vec3 to, double heading, double range) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > range) {
            return null;
        }
        return new Sighting(kind, key, what, to, Bearings.of(heading, dx, dz), Bearings.calledDistance(d));
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
    static String whatKey(ShipBody ship) {
        FlagReading flag = ShipAllegiance.of(ship);
        return LookoutLang.whatKey(flag.kind(), flag.status() == FlagReading.Status.FLYING, ShipSplits.isWreck(ship));
    }

    /** Compass heading of the ship's bow (0 = north). */
    static double heading(ShipBody ship) {
        SailingRuntime rt = SailingRuntimes.get(ship.level(), ship.id());
        BowFrame bow = rt == null ? BowFrame.SOUTH : rt.bow();
        Quaterniond q = bow.shipToWorld(ship.orientation(new Quaterniond()), new Quaterniond());
        Vector3d f = q.transform(new Vector3d(0, 0, 1));
        return Bearings.compass(f.x, f.z);
    }

    /** The ship's owner, the players aboard and the players in its nests. */
    static List<UUID> recipients(ServerLevel level, ShipBody ship, List<Observer> observers, List<? extends Player> players) {
        Set<UUID> out = new LinkedHashSet<>();
        ShipRegistry.get(level.getServer()).find(ship.id()).flatMap(ShipData::owner).ifPresent(out::add);
        for (Observer o : observers) {
            if (o.player() != null) out.add(o.player().getUUID());
        }
        for (Player p : players) {
            ShipBody on = CaptainsWhistleItem.shipOf(level, p);
            if (on != null && on.id().equals(ship.id())) out.add(p.getUUID());
        }
        return List.copyOf(out);
    }

    /** The crew lookout's name, else "Crow's nest". */
    private static Component speaker(List<Observer> observers) {
        for (Observer o : observers) {
            if (o.crew() != null) return o.crew().getDisplayName();
        }
        return Component.translatable(LookoutLang.KEY_NEST);
    }

    static Component line(Component speaker, Sighting s) {
        Component bearing = LookoutLang.bearing(s.bearing());
        Component body = switch (s.kind()) {
            case SHIP -> Component.translatable(LookoutLang.KEY_SHIP, Component.translatable(s.whatKey()), bearing, s.distance());
            case LAND -> Component.translatable(LookoutLang.KEY_LAND, bearing, s.distance());
            case SHARK -> Component.translatable(LookoutLang.KEY_SHARK, bearing, s.distance());
            case KRAKEN -> Component.translatable(LookoutLang.KEY_KRAKEN, bearing, s.distance());
        };
        return Component.literal("<").append(speaker).append("> ").append(body);
    }

    private static void send(ServerLevel level, Report r) {
        for (UUID id : r.recipients()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) p.sendSystemMessage(r.line());
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

package com.richardsenger.piratesnships.ship.hull.net;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.sailing.ship.BowFrame;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.ship.ShipConfig;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.cargo.ShipCargo;
import com.richardsenger.piratesnships.ship.hull.Compartment;
import com.richardsenger.piratesnships.ship.hull.HullAnalysis;
import com.richardsenger.piratesnships.ship.hull.HullGrid;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodSimulation;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntime;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Server side of the ship HUD (docs/design.md §4.6, HUD1): every {@code ships.ship_status_sync_interval_ticks} each
 * player standing on or riding in a ship ({@link ShipEntities#standingOrRiding}, Sable's tracking) gets a
 * {@link ShipStatusPayload} of that ship, when {@link ShipStatusThrottle} says so. A player who is not aboard gets
 * nothing; the client's HUD goes stale after 3 s and hides.
 *
 * <p>An instance owns its throttle, so GameTests run their own sender with mock players and a recording sink; the
 * game uses {@link #INSTANCE}.
 */
public final class ShipStatusSync {

    /** The game's sender, ticked from the hull module. */
    public static final ShipStatusSync INSTANCE = new ShipStatusSync();

    private final ShipStatusThrottle throttle = new ShipStatusThrottle();

    /** Level tick end: the game's sender, to the level's real players through {@code Services.NETWORK}. */
    public static void onLevelTick(ServerLevel level) {
        if (!due(level.getGameTime())) {
            return;
        }
        INSTANCE.throttle.retain(level.getServer().getPlayerList().getPlayers().stream().map(Player::getUUID).toList());
        INSTANCE.sync(level, level.players(), (player, payload) -> {
            if (player instanceof ServerPlayer sp) {
                Services.NETWORK.sendToPlayer(sp, payload);
            }
        });
    }

    /** Whether game time {@code gameTime} is a sync interval ({@code ships.ship_status_hud} on). */
    public static boolean due(long gameTime) {
        return ShipConfig.SHIP_STATUS_HUD.get() && gameTime % ShipConfig.SHIP_STATUS_SYNC_INTERVAL_TICKS.get() == 0;
    }

    /** One level tick of this sender: a {@link #sync} when the tick is {@link #due}. Public for GameTests. */
    public void tick(ServerLevel level, Collection<? extends Player> players, BiConsumer<Player, ShipStatusPayload> sink) {
        if (due(level.getGameTime())) {
            sync(level, players, sink);
        }
    }

    public static void onServerStopped() {
        INSTANCE.throttle.clear();
    }

    /**
     * One sync interval for {@code players} of {@code level}: builds each ship's status once and hands it to {@code sink}
     * for every player aboard whose throttle allows it. Public for GameTests (mock players are not in
     * {@code level.players()}).
     */
    public void sync(ServerLevel level, Collection<? extends Player> players, BiConsumer<Player, ShipStatusPayload> sink) {
        Map<UUID, ShipStatusPayload> built = new HashMap<>();
        for (Player player : players) {
            ShipBody ship = ShipEntities.standingOrRiding(player);
            if (ship == null || ship.isRemoved()) {
                throttle.forget(player.getUUID());
                continue;
            }
            ShipStatusPayload payload = built.computeIfAbsent(ship.id(), id -> build(level, ship));
            if (throttle.offer(player.getUUID(), payload)) {
                sink.accept(player, payload);
            }
        }
    }

    /** The rounded status of {@code ship} right now. Public for GameTests and commands. */
    public static ShipStatusPayload build(ServerLevel level, ShipBody ship) {
        String name = ShipRegistry.get(level.getServer()).find(ship.id()).map(ShipData::name).orElse("");
        SailingRuntime sailing = SailingRuntimes.getOrCreate(ship);
        BowFrame bow = sailing == null ? BowFrame.SOUTH : sailing.bow();
        double heading = sailing != null ? sailing.headingDegrees(ship) : heading(ship, bow);
        Vector3d v = new Vector3d();
        ship.velocities(v, new Vector3d());
        double speed = Math.sqrt(v.x * v.x + v.z * v.z);
        float rudder = sailing == null || sailing.helm() == null ? Float.NaN : (float) sailing.rudderAngle();
        HullRuntime hull = HullRuntimes.get(level, ship.id());
        List<ShipStatusPayload.Cell> cells = hull == null ? List.of() : CompartmentStrip.cells(entries(hull, bow));
        ShipCargo.Snapshot weighed = ShipCargo.last(level, ship.id()); // CW1's last weighing, cheap
        int load = weighed == null || weighed.level() == null ? -1 : weighed.level().ordinal();
        return new ShipStatusPayload(ship.id(), name, (float) heading, (float) speed, rudder, load, cells).quantize();
    }

    /** Compass bearing of the bow (0 = north) for a ship without a sailing runtime. */
    private static double heading(ShipBody ship, BowFrame bow) {
        Quaterniond q = bow.shipToWorld(ship.orientation(new Quaterniond()), new Quaterniond());
        Vector3d f = q.transform(new Vector3d(0, 0, 1));
        return WindSample.normalizeDegrees(Math.toDegrees(Math.atan2(f.x, -f.z)));
    }

    /** The hull's compartments as strip entries: centroid along the bow axis, water, breaches, pumps. */
    static List<CompartmentStrip.Entry> entries(HullRuntime hull, BowFrame bow) {
        FloodSimulation sim = hull.simulation();
        HullAnalysis a = sim.analysis();
        HullGrid g = a.grid();
        int[] breaches = breachCounts(a, hull.breaches().positions());
        List<CompartmentStrip.Entry> out = new ArrayList<>(a.compartments().size());
        for (Compartment c : a.compartments()) {
            double sx = 0, sy = 0, sz = 0;
            for (int i : c.cellsByHeight()) {
                sx += g.x(i);
                sy += g.y(i);
                sz += g.z(i);
            }
            int n = Math.max(1, c.volume());
            double forward = (sx / n) * bow.dx() + (sz / n) * bow.dz();
            out.add(new CompartmentStrip.Entry(c.id(), forward, sy / n, c.volume(),
                    Math.max(0, Math.min(c.volume(), sim.volume(c.id()))), breaches[c.id()], hull.pumping(c.id())));
        }
        return out;
    }

    /**
     * Open breaches per compartment: a breach (a removed hull block, plot position) counts for every compartment that
     * has a cell next to it. A fresh breach the debounced re-analysis has not seen yet counts too, through its
     * neighbours.
     */
    static int[] breachCounts(HullAnalysis a, Collection<BlockPos> breaches) {
        int[] counts = new int[a.compartments().size()];
        HullGrid g = a.grid();
        for (BlockPos p : breaches) {
            int x = p.getX() - g.originX(), y = p.getY() - g.originY(), z = p.getZ() - g.originZ();
            int[] seen = new int[6];
            int k = 0;
            for (Direction d : Direction.values()) {
                int nx = x + d.getStepX(), ny = y + d.getStepY(), nz = z + d.getStepZ();
                if (!g.inBounds(nx, ny, nz)) {
                    continue;
                }
                int c = a.compartmentAt(nx, ny, nz);
                if (c >= 0 && c < counts.length && !contains(seen, k, c)) {
                    counts[c]++;
                    seen[k++] = c;
                }
            }
        }
        return counts;
    }

    private static boolean contains(int[] a, int n, int v) {
        for (int i = 0; i < n; i++) {
            if (a[i] == v) {
                return true;
            }
        }
        return false;
    }
}

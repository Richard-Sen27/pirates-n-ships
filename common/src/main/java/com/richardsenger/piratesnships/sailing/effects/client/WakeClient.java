package com.richardsenger.piratesnships.sailing.effects.client;

import com.richardsenger.piratesnships.hazard.HazardConfig;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.sailing.effects.SeaEffectsConfig;
import com.richardsenger.piratesnships.sailing.effects.SpawnRules;
import com.richardsenger.piratesnships.sailing.effects.WakeFrame;
import com.richardsenger.piratesnships.sailing.effects.WakeRules;
import com.richardsenger.piratesnships.sailing.effects.WaterlineOutline;
import com.richardsenger.piratesnships.ship.sable.WaterRegions;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ships' wakes (WAKE1, docs/design.md §5.4 "Wake"; physical client only): every client tick, each Sable sub-level
 * within {@code wave_effects.wake_radius} of the camera that moves through the water faster than
 * {@code wake_min_speed} sheds flat foam quads ({@link WakeParticle}, the WD1 foam sprite): a bow wave at the stem
 * moving out along the Kelvin angle, a trail across the stern drifting slowly apart, a thin line along the waterline
 * when fast, and at speed a few splash streaks rising at the stem. Rates, opacity and motion come from the pure
 * {@link WakeRules}; where on the hull from {@link WaterlineOutline} and {@link WakeFrame}. Only renders: nothing here
 * reaches the server or changes gameplay.
 *
 * <p>Sable, client side (all in sable-common): the client's sub-levels come from
 * {@code api/sublevel/SubLevelContainer#getContainer(ClientLevel)} (l.114) and
 * {@code ClientSubLevelContainer#getAllSubLevels} (l.66); a sub-level's per-tick pose is
 * {@code SubLevel#logicalPose} (l.151), which {@code ClientSubLevel#tick} (l.120) sets from Sable's interpolator; its
 * plot block bounds are {@code LevelPlot#getBoundingBox} (l.342, global block coordinates of the plot, sent to the
 * client by {@code ClientboundStartTrackingSubLevelPacket} l.84 and {@code ClientboundChangeBoundsSubLevelPacket}),
 * and the plot's blocks are in the client level itself. The velocity is
 * {@code ActiveSableCompanion#getVelocity(Level, SubLevelAccess, Position)} (l.408), which on the client is the
 * pose delta per tick × 20 (l.392-395); it is smoothed here because interpolated poses jitter.
 *
 * <p>Cost per ship in range: once a second, one block layer of the plot bounds (up to five layers when the waterline
 * row is empty) plus a flood fill, O(w × d) (119 block reads for the starter sloop); every tick while moving, one pose
 * transform per outline cell (about 40 for the sloop) and the frame, plus the spawned particles, at most
 * {@link #MAX_PER_TICK} for all ships together.
 */
public final class WakeClient {

    /** Upper limit of wake quads spawned in one tick for all ships together, whatever the config says. */
    static final int MAX_PER_TICK = 48;
    /** The waterline outline is re-read this often [ticks], and at once when the plot bounds change. */
    static final int REFRESH_TICKS = 20;
    /** A ship not seen for this long is forgotten [ticks]. */
    static final int FORGET_TICKS = 100;
    /** Ships whose plot layer is larger than this many cells are skipped (cost guard). */
    static final int MAX_LAYER_CELLS = 96 * 96;
    /** Ships whose centre is farther than this above or below sea level make no wake [blocks]. */
    static final double MAX_HEIGHT_OFF_SEA = 16.0;
    /** Share of the new velocity sample taken each tick (exponential smoothing). */
    static final double VELOCITY_SMOOTHING = 0.35;
    /** Outlines with fewer cells than this are not ships (a lone block in the water) [cells]. */
    static final int MIN_OUTLINE_CELLS = 4;
    static final double BOW_LENGTH = 1.4, BOW_WIDTH = 0.55, STERN_WIDTH = 0.6, SIDE_LENGTH = 0.8, SIDE_WIDTH = 0.28;

    private static final Map<UUID, ShipWake> SHIPS = new HashMap<>();
    private static @Nullable ClientLevel shipsLevel;

    private WakeClient() {
    }

    public static void init() {
        ClientEvents.CLIENT_TICK_END.register(WakeClient::tick);
    }

    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != shipsLevel) {
            SHIPS.clear(); // hold nothing of a level that was left
            shipsLevel = level;
        }
        if (level == null || mc.isPaused() || mc.player == null) {
            return;
        }
        if (!HazardConfig.WAKE.get()) {
            SHIPS.clear();
            return;
        }
        double setting = SpawnRules.particleSetting(mc.options.particles().get().getId());
        if (setting <= 0.0) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized() || camera.getFluidInCamera() != FogType.NONE) {
            return;
        }
        WakeRules rules = SeaEffectsConfig.wake();
        Vec3 cam = camera.getPosition();
        double seaTop = level.getSeaLevel() - 1.0 + 8.0 / 9.0; // a still water source's top face
        if (!rules.inRange(cam.y, seaTop)) {
            return;
        }
        ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        TextureAtlasSprite sprite = SeaEffectsClient.sprite(mc, SeaEffectsClient.FOAM_STREAK);
        if (sprite == null) {
            return;
        }
        long now = level.getGameTime();
        Spawner spawner = new Spawner(mc, level, rules, setting, sprite, level.random, now);
        for (ClientSubLevel sub : container.getAllSubLevels()) {
            if (sub.isRemoved()) {
                continue;
            }
            Pose3dc pose = sub.logicalPose();
            Vector3dc pos = pose.position();
            if (!rules.withinRadius(pos.x() - cam.x, pos.z() - cam.z) || Math.abs(pos.y() - seaTop) > MAX_HEIGHT_OFF_SEA) {
                continue;
            }
            ShipWake ship = SHIPS.computeIfAbsent(sub.getUniqueId(), id -> new ShipWake());
            ship.seenAt = now;
            ship.refreshIfDue(level, sub, pose, seaTop, now);
            if (ship.cellCount() < MIN_OUTLINE_CELLS) {
                continue;
            }
            ship.updateVelocity(level, sub);
            double speed = Math.sqrt(ship.vx * ship.vx + ship.vz * ship.vz);
            if (!rules.active(speed)) {
                continue;
            }
            WakeFrame frame = ship.frame(pose);
            if (frame != null) {
                spawner.ship(frame, speed, ship.length());
            }
        }
        for (Iterator<ShipWake> it = SHIPS.values().iterator(); it.hasNext(); ) {
            if (now - it.next().seenAt > FORGET_TICKS) {
                it.remove();
            }
        }
    }

    /** What one ship's wake remembers between ticks: its waterline outline in plot space and its smoothed velocity. */
    static final class ShipWake {
        long seenAt;
        private long refreshedAt = Long.MIN_VALUE;
        private @Nullable BoundingBox3i bounds;
        private int layer;
        private int[] cellX = new int[0], cellZ = new int[0];
        private double plotCx, plotCz, plotLength;
        private double vx, vz;
        private boolean hasVelocity;

        int cellCount() {
            return cellX.length;
        }

        double length() {
            return plotLength;
        }

        void refreshIfDue(ClientLevel level, ClientSubLevel sub, Pose3dc pose, double seaTop, long now) {
            BoundingBox3ic b = sub.getPlot().getBoundingBox();
            boolean boundsChanged = bounds == null || !bounds.equals(b);
            if (!boundsChanged && now - refreshedAt < REFRESH_TICKS) {
                return;
            }
            refreshedAt = now;
            cellX = new int[0];
            cellZ = new int[0];
            if (b == BoundingBox3i.EMPTY || b.maxX() < b.minX() || b.maxY() < b.minY() || b.maxZ() < b.minZ()) {
                bounds = null;
                return;
            }
            bounds = new BoundingBox3i(b);
            int w = b.maxX() - b.minX() + 1, d = b.maxZ() - b.minZ() + 1;
            if (w <= 0 || d <= 0 || (long) w * d > MAX_LAYER_CELLS) {
                return;
            }
            // the plot layer the still water cuts below the ship's centre
            Vector3dc p = pose.position();
            Vec3 plotWater = pose.transformPositionInverse(new Vec3(p.x(), seaTop, p.z()));
            int preferred = Mth.floor(plotWater.y);
            Map<Integer, boolean[][]> read = new HashMap<>();
            int y = WaterlineOutline.pickLayer(preferred, ly -> {
                boolean[][] grid = ly < b.minY() || ly > b.maxY() ? null : readLayer(level, b, ly, w, d);
                read.put(ly, grid);
                return grid != null;
            }, 2, 2);
            if (y == Integer.MIN_VALUE) {
                return;
            }
            List<int[]> outline = WaterlineOutline.outline(read.get(y));
            layer = y;
            cellX = new int[outline.size()];
            cellZ = new int[outline.size()];
            double sx = 0.0, sz = 0.0;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE, minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            for (int i = 0; i < outline.size(); i++) {
                cellX[i] = b.minX() + outline.get(i)[0];
                cellZ[i] = b.minZ() + outline.get(i)[1];
                sx += cellX[i] + 0.5;
                sz += cellZ[i] + 0.5;
                minX = Math.min(minX, cellX[i]);
                maxX = Math.max(maxX, cellX[i]);
                minZ = Math.min(minZ, cellZ[i]);
                maxZ = Math.max(maxZ, cellZ[i]);
            }
            if (!outline.isEmpty()) {
                plotCx = sx / outline.size();
                plotCz = sz / outline.size();
                plotLength = Math.max(maxX - minX, maxZ - minZ) + 1.0;
            }
        }

        /** Solid cells of one plot layer as {@code [x][z]}, or null when the layer has none. */
        private static boolean @Nullable [][] readLayer(ClientLevel level, BoundingBox3ic b, int y, int w, int d) {
            boolean[][] grid = new boolean[w][d];
            boolean any = false;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    pos.set(b.minX() + x, y, b.minZ() + z);
                    if (level.getBlockState(pos).blocksMotion()) {
                        grid[x][z] = true;
                        any = true;
                    }
                }
            }
            return any ? grid : null;
        }

        void updateVelocity(ClientLevel level, ClientSubLevel sub) {
            Vec3 v = Sable.HELPER.getVelocity(level, sub, (Position) new Vec3(plotCx, layer + 0.5, plotCz));
            if (!hasVelocity) {
                vx = v.x;
                vz = v.z;
                hasVelocity = true;
            } else {
                vx += (v.x - vx) * VELOCITY_SMOOTHING;
                vz += (v.z - vz) * VELOCITY_SMOOTHING;
            }
        }

        @Nullable WakeFrame frame(Pose3dc pose) {
            int n = cellX.length;
            double[] xs = new double[n], zs = new double[n];
            Vector3d tmp = new Vector3d();
            for (int i = 0; i < n; i++) {
                pose.transformPosition(new Vector3d(cellX[i] + 0.5, layer + 0.5, cellZ[i] + 0.5), tmp);
                xs[i] = tmp.x;
                zs[i] = tmp.z;
            }
            return WakeFrame.of(xs, zs, vx, vz);
        }
    }

    /** Spawns one tick's quads, sharing {@link #MAX_PER_TICK} among all ships. */
    private static final class Spawner {
        private final Minecraft mc;
        private final ClientLevel level;
        private final WakeRules rules;
        private final double setting;
        private final TextureAtlasSprite sprite;
        private final RandomSource r;
        private final long time;
        private int budget = MAX_PER_TICK;

        Spawner(Minecraft mc, ClientLevel level, WakeRules rules, double setting, TextureAtlasSprite sprite,
                RandomSource r, long time) {
            this.mc = mc;
            this.level = level;
            this.rules = rules;
            this.setting = setting;
            this.sprite = sprite;
            this.r = r;
            this.time = time;
        }

        void ship(WakeFrame frame, double speed, double shipLength) {
            double[] c = frame.course();
            double[] stb = WakeRules.starboard(c[0], c[1]);
            double perTick = speed / 20.0; // how far the ship moves in a tick: spread births over that gap
            double alpha = rules.opacity(speed);
            // the hull passes over a bow quad within about its length (plus a margin), so check that long
            int hullPassTicks = (int) Math.ceil((shipLength + 2.0) / Math.max(perTick, 0.01));
            // stern trail
            int n = SpawnRules.count(rules.sternRate(speed, frame.sternWidth()) * setting, r.nextDouble());
            double sternLength = rules.sternLength(speed);
            for (int i = 0; i < n; i++) {
                double u = r.nextDouble() * 2.0 - 1.0;
                double[] p = frame.sternPoint(u);
                double back = r.nextDouble() * perTick;
                double drift = rules.sternLateralPerTick(speed, u) + (r.nextDouble() - 0.5) * 0.004;
                add(p[0] - c[0] * back, p[1] - c[1] * back, c[0], c[1], stb[0] * drift, 0.0, stb[1] * drift,
                        sternLength * vary(), STERN_WIDTH * vary(), alpha, (int) (rules.lifeTicks() * vary()),
                        WakeRules.STERN_FRICTION, 0, SeaStreakParticle.Shape.FLAT);
            }
            // bow wave, both sides of the stem
            double[] stem = frame.stem();
            double lateral = rules.bowLateralPerTick(speed);
            for (int side = -1; side <= 1; side += 2) {
                int m = SpawnRules.count(rules.bowRate(speed) * setting, r.nextDouble());
                double[] arm = rules.armDirection(c[0], c[1], side);
                for (int i = 0; i < m; i++) {
                    double back = r.nextDouble() * perTick;
                    double off = side * 0.3;
                    add(stem[0] + stb[0] * off - c[0] * back, stem[1] + stb[1] * off - c[1] * back, arm[0], arm[1],
                            stb[0] * side * lateral, 0.0, stb[1] * side * lateral, BOW_LENGTH * vary(), BOW_WIDTH * vary(),
                            alpha, (int) (rules.bowLifeTicks() * vary()), WakeRules.BOW_FRICTION, hullPassTicks,
                            SeaStreakParticle.Shape.FLAT);
                }
            }
            // waterline foam along the sides, fast ships only
            int k = frame.sideCount() == 0 ? 0
                    : SpawnRules.count(rules.sideRate(speed, frame.sideCount()) * setting, r.nextDouble());
            for (int i = 0; i < k; i++) {
                int j = r.nextInt(frame.sideCount());
                double[] p = frame.side(j);
                double out = frame.sideSign(j) * 0.01;
                add(p[0], p[1], c[0], c[1], stb[0] * out, 0.0, stb[1] * out, SIDE_LENGTH * vary(), SIDE_WIDTH * vary(),
                        alpha * 0.8, (int) (WakeRules.SIDE_LIFE_TICKS * vary()), 1.0, WakeRules.SIDE_LIFE_TICKS,
                        SeaStreakParticle.Shape.FLAT);
            }
            // a few splash streaks rising at the stem at speed
            int s = SpawnRules.count(rules.splashRate(speed) * setting, r.nextDouble());
            for (int i = 0; i < s; i++) {
                int side = r.nextBoolean() ? 1 : -1;
                double[] arm = rules.armDirection(c[0], c[1], side);
                double v = lateral * 1.5;
                add(stem[0] + stb[0] * side * 0.4, stem[1] + stb[1] * side * 0.4, arm[0], arm[1],
                        arm[0] * v + c[0] * perTick * 0.5, 0.06 + 0.06 * r.nextDouble(), arm[1] * v + c[1] * perTick * 0.5,
                        0.7 * vary(), 0.35 * vary(), alpha * 0.9, (int) (WakeRules.SPLASH_LIFE_TICKS * vary()), 0.9,
                        WakeRules.SPLASH_LIFE_TICKS, SeaStreakParticle.Shape.AIR);
            }
        }

        private double vary() {
            return 0.8 + 0.4 * r.nextDouble();
        }

        private void add(double x, double z, double axisX, double axisZ, double vx, double vy, double vz, double length,
                         double width, double alpha, int life, double friction, int occlusionTicks,
                         SeaStreakParticle.Shape shape) {
            if (budget <= 0) {
                return;
            }
            double y = SeaSurface.y(level, x, z, time);
            if (Double.isNaN(y)) {
                return; // not open sea here
            }
            y += WakeRules.LIFT;
            if (WaterRegions.isOccluded(level, new Vec3(x, y - 0.3, z))) {
                return; // inside a dry hull
            }
            budget--;
            mc.particleEngine.add(new WakeParticle(level, x, y, z, shape, axisX, axisZ, vx, vy, vz, length, width,
                    alpha, Math.max(1, life), friction, occlusionTicks, sprite));
        }
    }
}

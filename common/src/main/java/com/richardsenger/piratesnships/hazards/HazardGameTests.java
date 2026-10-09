package com.richardsenger.piratesnships.hazards;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips.Fixture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.List;

/**
 * GameTests of the hazards module (H1): the fields on items, players, boats and ships, sail tearing, the spawner, the
 * commands and the lifetime. Every hazard is removed when its test ends ({@link HazardTestSupport}); tests that change
 * config have their own batch (one override per value per batch, docs/progress.md Q3).
 */
public final class HazardGameTests {

    private static final String BATCH = "pirates_n_ships_hazards";
    private static final String SHIP_BATCH = "pirates_n_ships_hazards_ships";
    private static final String MASS_BATCH = "pirates_n_ships_config_hazards_ship_mass";
    private static final String TEAR_BATCH = "pirates_n_ships_config_hazards_sail_tear";
    private static final String SPAWN_BATCH = "pirates_n_ships_config_hazards_spawner";
    private static final String DISABLED_BATCH = "pirates_n_ships_config_hazards_disabled";

    /** Whirlpool centre in the 40×40 sailing basin (water up to y=7, so the water line is y=8). */
    private static final Vec3 POOL_CENTRE = new Vec3(20.5, 8.0, 20.5);

    private HazardGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(HazardGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** Stone floor at y=1 over the 24×24 template, water from y=2 to {@code waterTop} (none if below 2). */
    private static void pool(GameTestHelper h, int waterTop) {
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                for (int y = 2; y <= waterTop; y++) {
                    boolean wall = x == 0 || x == 23 || z == 0 || z == 23;
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : Blocks.WATER);
                }
            }
        }
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double horizontal(Vector3d a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static CommandSourceStack commandSource(GameTestHelper h, Vec3 relative) {
        ServerLevel level = h.getLevel();
        return level.getServer().createCommandSourceStack().withLevel(level).withPosition(h.absoluteVec(relative))
                .withPermission(2).withSuppressedOutput();
    }

    private static int command(GameTestHelper h, Vec3 relative, String command) {
        try {
            return h.getLevel().getServer().getCommands().getDispatcher().execute(command, commandSource(h, relative));
        } catch (CommandSyntaxException e) {
            throw new AssertionError("command failed to parse: " + command + ": " + e.getMessage());
        }
    }

    private static List<HazardEntity> hazardsNear(GameTestHelper h, Vec3 relative, double radius) {
        Vec3 at = h.absoluteVec(relative);
        return h.getLevel().getEntitiesOfClass(HazardEntity.class, new AABB(at, at).inflate(radius));
    }

    /**
     * A test spawn site: the player is at sea (and in deep ocean when {@code deep}); every spot is open water at height
     * {@code y}. The spot lies outside the test area, so its chunk is loaded for the new entity (which the test discards).
     */
    private static HazardSpawner.Site testSite(boolean deep, double y) {
        return new HazardSpawner.Site() {
            @Override public boolean atSea(ServerLevel level, Player player) { return true; }
            @Override public boolean inDeepOcean(ServerLevel level, Player player) { return deep; }
            @Override public @Nullable Vec3 openWater(ServerLevel level, double x, double z, HazardKind kind) {
                ChunkPos chunk = new ChunkPos(BlockPos.containing(x, y, z));
                level.getChunk(chunk.x, chunk.z);
                return new Vec3(x, y, z);
            }
        };
    }

    // ------------------------------------------------------------------ waterspout fields

    /** A dropped item 4 blocks from a waterspout's axis is pulled toward the axis and lifted within 40 ticks. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 80, batch = BATCH)
    public static void itemIsPulledTowardTheAxisAndLifted(GameTestHelper h) {
        pool(h, 1);
        HazardEntity spout = HazardTestSupport.hazard(h, HazardKind.WATERSPOUT, new Vec3(12.5, 2.0, 12.5));
        ItemEntity item = new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(Items.COD));
        Vec3 at = h.absoluteVec(new Vec3(16.5, 2.0, 12.5));
        item.moveTo(at.x, at.y, at.z);
        item.setDeltaMovement(Vec3.ZERO);
        h.getLevel().addFreshEntity(item);
        HazardTestSupport.onEnd(h, item::discard);
        double startY = item.getY();
        double startD = horizontal(item.position(), spout.position());
        double[] minD = {startD};
        double[] maxY = {startY};
        h.onEachTick(() -> {
            if (!item.isRemoved()) {
                minD[0] = Math.min(minD[0], horizontal(item.position(), spout.position()));
                maxY[0] = Math.max(maxY[0], item.getY());
            }
        });
        h.runAfterDelay(40, () -> {
            Constants.LOG.info("[hazard test] item: distance {} -> min {}, y {} -> max {}", startD, minD[0], startY, maxY[0]);
            h.assertTrue(minD[0] < startD - 1.0, "the item was not pulled toward the axis: " + startD + " -> min " + minD[0]);
            h.assertTrue(maxY[0] > startY + 1.0, "the item was not lifted: y " + startY + " -> max " + maxY[0]);
            h.succeed();
        });
    }

    /** A player swimming 4 blocks from a waterspout's axis is pulled in and lifted. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 80, batch = BATCH)
    public static void swimmingPlayerIsPulledAndLifted(GameTestHelper h) {
        pool(h, 4);
        HazardEntity spout = HazardTestSupport.hazard(h, HazardKind.WATERSPOUT, new Vec3(12.5, 5.0, 12.5));
        Player player = HazardTestSupport.playerInLevel(h, new Vec3(16.5, 4.2, 12.5));
        double startY = player.getY();
        double startD = horizontal(player.position(), spout.position());
        double[] minD = {startD};
        double[] maxY = {startY};
        h.onEachTick(() -> {
            minD[0] = Math.min(minD[0], horizontal(player.position(), spout.position()));
            maxY[0] = Math.max(maxY[0], player.getY());
        });
        h.runAfterDelay(40, () -> {
            Constants.LOG.info("[hazard test] player: distance {} -> min {}, y {} -> max {}", startD, minD[0], startY, maxY[0]);
            h.assertTrue(minD[0] < startD - 1.0, "the player was not pulled toward the axis: " + startD + " -> min " + minD[0]);
            h.assertTrue(maxY[0] > startY + 0.5, "the player was not lifted: y " + startY + " -> max " + maxY[0]);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ whirlpool fields

    /** A boat a block from a whirlpool's centre is dragged under the water line. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 140, batch = BATCH)
    public static void boatNearTheCentreIsDraggedUnder(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        HazardEntity pool = HazardTestSupport.hazard(h, HazardKind.WHIRLPOOL, POOL_CENTRE);
        Vec3 at = h.absoluteVec(new Vec3(21.5, 8.0, 20.5));
        Boat boat = new Boat(h.getLevel(), at.x, at.y, at.z);
        h.getLevel().addFreshEntity(boat);
        HazardTestSupport.onEnd(h, boat::discard);
        double waterLine = pool.getY();
        double[] minY = {boat.getY()};
        h.onEachTick(() -> minY[0] = Math.min(minY[0], boat.getY()));
        h.runAfterDelay(100, () -> {
            Constants.LOG.info("[hazard test] boat: water line {}, lowest y {}, now {} at {} from the centre", waterLine, minY[0],
                    boat.getY(), horizontal(boat.position(), pool.position()));
            // the boat is 0.5625 high: below waterLine - 1 its top is well under the surface
            h.assertTrue(minY[0] < waterLine - 1.0, "the boat was not dragged under: lowest y " + minY[0] + ", water line " + waterLine);
            h.succeed();
        });
    }

    /** The 5×4×5 test hull six blocks from a whirlpool's centre is pulled toward it within 100 ticks. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = SHIP_BATCH)
    public static void smallShipIsPulledTowardTheCentre(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        Fixture ship = SailingGameTestsShips.assemble(h, SailingGameTestsShips.squareHull(h, 12, 18, SailTrim.FURLED));
        HazardEntity pool = HazardTestSupport.hazard(h, HazardKind.WHIRLPOOL, POOL_CENTRE);
        double[] start = new double[1];
        h.runAfterDelay(10, () -> start[0] = horizontal(com(ship), pool.position()));
        h.runAfterDelay(110, () -> {
            double end = horizontal(com(ship), pool.position());
            Constants.LOG.info("[hazard test] small ship: mass {}, distance {} -> {}", ship.ship().mass(), start[0], end);
            h.assertTrue(start[0] > 4.5 && start[0] < 7.5, "the ship did not start about 6 blocks out: " + start[0]);
            // Before SH1 the unballasted hull rolled over to 57 degrees while it slid in and moved 1.5-1.7 blocks (5.90 ->
            // 4.25..4.38 over four runs). Since SH1's righting torque it stays upright, presents its full draft to Sable's
            // water drag and moves 0.44-0.45 blocks (three runs); a third of that
            h.assertTrue(end < start[0] - 0.15, "the ship was not pulled toward the centre: " + start[0] + " -> " + end);
            h.succeed();
        });
    }

    /**
     * Two hulls six blocks either side of a whirlpool's centre, one ballasted (heavier): with the mass cap below both
     * masses both feel the same force, so the heavier one moves less.
     *
     * <p>The motion is the hull's own velocity, integrated over the window ({@link Track}), not the change of its pose
     * (HZ1). Sable's physics is 32-bit in world coordinates (docs/sable-notes.md §9.0l): Rapier integrates the position
     * 18 times per substep, 720 times a second, and a step below half an f32 unit is rounded away. The heavy hull's
     * inward drift of 0.07 m/s is 1e-4 block per step; at x=2,737 half a unit is 1.2e-4 and the hull's x stayed at
     * exactly 2737.5 for 110 ticks while it reported -0.069 m/s; at x=1,616 the step rounded up to one unit and it drifted
     * 27 % too far (0.47 against 0.37 blocks at x=202). The grid puts this test anywhere within ±4,096 blocks and further
     * out in the full suite, so the pose decided the result: radial moves of the heavy hull from +0.32 to -0.13 between
     * runs, and "light ship was not pulled in: -0.36" when the light hull's slow axis froze while the orbit carried it on.
     * The velocity is what the solver computes from the forces and the water, before that rounding.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = MASS_BATCH)
    public static void heavyShipMovesLessThanALightOne(GameTestHelper h) {
        ConfigOverrides.during(h, HazardsConfig.MAX_SHIP_MASS_EFFECT, 20.0);
        SailingGameTestsShips.basin(h, true);
        Fixture light = SailingGameTestsShips.assemble(h, SailingGameTestsShips.squareHull(h, 12, 18, SailTrim.FURLED));
        BlockPos heavyHelm = SailingGameTestsShips.squareHull(h, 24, 18, SailTrim.FURLED);
        SailingGameTestsShips.ballast(h, 24, 18);
        Fixture heavy = SailingGameTestsShips.assemble(h, heavyHelm);
        HazardEntity pool = HazardTestSupport.hazard(h, HazardKind.WHIRLPOOL, POOL_CENTRE);
        Track lightTrack = new Track(light, pool.position());
        Track heavyTrack = new Track(heavy, pool.position());
        StringBuilder trace = new StringBuilder();
        boolean[] done = {false};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (done[0] || t < MASS_FROM) return;
            lightTrack.tick(t == MASS_FROM);
            heavyTrack.tick(t == MASS_FROM);
            if ((t - MASS_FROM) % 25 == 0) {
                trace.append(String.format(" | t=%d light %s heavy %s", t, lightTrack, heavyTrack));
            }
            if (t < MASS_TO) return;
            done[0] = true;
            Constants.LOG.info("[hazard test] mass cap 20: light {} kpg moved {} in ({} travelled; pose {} in, {} travelled), "
                            + "heavy {} kpg moved {} in ({} travelled; pose {} in, {} travelled), tilt light {} heavy {}{}",
                    light.ship().mass(), lightTrack.radialMove(), lightTrack.travel(), lightTrack.poseRadialMove(), lightTrack.poseTravel(),
                    heavy.ship().mass(), heavyTrack.radialMove(), heavyTrack.travel(), heavyTrack.poseRadialMove(), heavyTrack.poseTravel(),
                    lightTrack.tilt(), heavyTrack.tilt(), trace);
            h.assertTrue(heavy.ship().mass() > light.ship().mass() * 1.5, "the ballast did not make the ship heavier");
            // Integrated velocities over ticks 10-160 (HZ1, twelve runs at x/z from -3,517 to 4,258): light 0.17-0.32 blocks
            // in and 3.08-3.13 travelled, heavy 0.21-0.26 in and 1.74-1.83 travelled, ratio 0.56-0.59. The pose gave the
            // light hull -0.72 to +0.45 and the heavy one -0.25 to +0.42 in the same runs. The lowest light value (0.17)
            // came from the run whose light hull sat at x=4,258.5 with its x frozen, so the field it felt was off; the
            // orbit alone (3.1 blocks along the circle at r=6) would put it 0.75 out, so half that lowest value still
            // proves the pull. The travel ratio keeps its 0.8 bound from SH1b, now with a wide margin.
            h.assertTrue(lightTrack.radialMove() > 0.08, "the light ship was not pulled in: " + lightTrack.radialMove() + trace);
            h.assertTrue(heavyTrack.travel() < lightTrack.travel() * 0.8,
                    "the heavy ship travelled as far as the light one: " + heavyTrack.travel() + " vs " + lightTrack.travel() + trace);
            h.succeed();
        });
    }

    /** The window of {@link #heavyShipMovesLessThanALightOne}: from tick 10 (the hulls have risen to their float) to 160. */
    private static final int MASS_FROM = 10, MASS_TO = 160;

    /**
     * A hull's horizontal motion in a whirlpool test as the integral of its reported velocity (one game tick, 0.05 s, per
     * sample), which does not depend on where the test grid put it; the pose is kept for the log only.
     */
    private static final class Track {
        private final Fixture f;
        private final Vec3 centre;
        private double startX, startZ, poseX, poseZ, dx, dz;

        Track(Fixture f, Vec3 centre) {
            this.f = f;
            this.centre = centre;
        }

        void tick(boolean first) {
            Vector3d c = com(f);
            if (first) {
                startX = poseX = c.x;
                startZ = poseZ = c.z;
                return;
            }
            Vector3d v = f.ship().linearVelocity();
            dx += v.x * 0.05;
            dz += v.z * 0.05;
            poseX = c.x;
            poseZ = c.z;
        }

        double radialMove() {
            return Math.hypot(startX - centre.x, startZ - centre.z) - Math.hypot(startX + dx - centre.x, startZ + dz - centre.z);
        }

        double travel() {
            return Math.hypot(dx, dz);
        }

        double poseRadialMove() {
            return Math.hypot(startX - centre.x, startZ - centre.z) - Math.hypot(poseX - centre.x, poseZ - centre.z);
        }

        double poseTravel() {
            return Math.hypot(poseX - startX, poseZ - startZ);
        }

        double tilt() {
            Vector3d up = f.ship().orientation(new org.joml.Quaterniond()).transform(new Vector3d(0, 1, 0));
            return Math.toDegrees(Math.acos(Math.min(1.0, up.y)));
        }

        @Override
        public String toString() {
            return String.format("x=%.4f z=%.4f d=(%.3f,%.3f) r-move %.3f pose r-move %.3f tilt %.1f",
                    poseX, poseZ, dx, dz, radialMove(), poseRadialMove(), tilt());
        }
    }

    private static Vector3d com(Fixture f) {
        Vector3d c = new Vector3d();
        f.ship().centerOfMass(c);
        return f.ship().toWorld(c, new Vector3d());
    }

    // ------------------------------------------------------------------ sails

    /** With a tear chance of 1, a full square sail inside a waterspout is furled within 10 seconds. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 220, batch = TEAR_BATCH)
    public static void setSailInsideAWaterspoutIsFurled(GameTestHelper h) {
        ConfigOverrides.during(h, HazardsConfig.SAIL_DAMAGE_CHANCE, 1.0);
        SailingGameTestsShips.basin(h, true);
        Fixture ship = SailingGameTestsShips.assemble(h, SailingGameTestsShips.squareHull(h, 17, 18, SailTrim.FULL));
        BlockPos sail = ship.runtime().sailPositions().get(0);
        h.assertTrue(ship.runtime().trimAt(sail) == SailTrim.FULL, "the test sail is not set");
        HazardTestSupport.hazard(h, HazardKind.WATERSPOUT, new Vec3(19.5, 8.0, 20.5));
        h.succeedWhen(() -> h.assertTrue(ship.runtime().trimAt(sail) == SailTrim.FURLED,
                "the sail is still " + ship.runtime().trimAt(sail)));
    }

    // ------------------------------------------------------------------ spawner

    /** With the chance at 1, a player at sea gets a waterspout 48–96 blocks away in a thunderstorm, none in clear weather. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 40, batch = SPAWN_BATCH)
    public static void spawnerPlacesWaterspoutsOnlyInThunderstorms(GameTestHelper h) {
        ConfigOverrides.during(h, HazardsConfig.WATERSPOUT_CHANCE, 1.0);
        ServerLevel level = h.getLevel();
        Player player = HazardTestSupport.playerInLevel(h, new Vec3(12.5, 2, 12.5));
        HazardSpawner.Site site = testSite(false, player.getY());
        HazardEntity[] spawned = new HazardEntity[2];
        HazardTestSupport.withWeather(level, false, () -> spawned[0] = HazardSpawner.trySpawn(level, player, HazardKind.WATERSPOUT, site, level.getRandom()));
        HazardTestSupport.withWeather(level, true, () -> spawned[1] = HazardSpawner.trySpawn(level, player, HazardKind.WATERSPOUT, site, level.getRandom()));
        if (spawned[0] != null) spawned[0].discard();
        if (spawned[1] != null) spawned[1].discard();
        h.assertTrue(spawned[0] == null, "a waterspout formed in clear weather");
        h.assertTrue(spawned[1] instanceof WaterspoutEntity, "no waterspout formed in the thunderstorm");
        double d = horizontal(spawned[1].position(), player.position());
        h.assertTrue(d >= 48 - 1e-6 && d <= 96 + 1e-6, "the waterspout formed " + d + " blocks from the player");

        // the per-player cap: one waterspout near the player already, so no second one forms
        HazardTestSupport.hazard(h, HazardKind.WATERSPOUT, new Vec3(12.5, 2, 20.5));
        HazardEntity[] capped = new HazardEntity[1];
        HazardTestSupport.withWeather(level, true, () -> capped[0] = HazardSpawner.trySpawn(level, player, HazardKind.WATERSPOUT, site, level.getRandom()));
        if (capped[0] != null) capped[0].discard();
        h.assertTrue(capped[0] == null, "a second waterspout formed although max_per_player is 1");
        h.succeed();
    }

    /** Waterspouts switched off: none form in a thunderstorm, the command refuses, and an existing one removes itself. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 40, batch = DISABLED_BATCH)
    public static void disabledWaterspoutsDoNotForm(GameTestHelper h) {
        ConfigOverrides.during(h, HazardsConfig.WATERSPOUT_CHANCE, 1.0);
        ConfigOverrides.during(h, HazardsConfig.WATERSPOUTS_ENABLED, false);
        ServerLevel level = h.getLevel();
        Player player = HazardTestSupport.playerInLevel(h, new Vec3(12.5, 2, 12.5));
        HazardEntity[] spawned = new HazardEntity[1];
        HazardTestSupport.withWeather(level, true, () -> spawned[0] = HazardSpawner.trySpawn(level, player, HazardKind.WATERSPOUT, testSite(false, player.getY()), level.getRandom()));
        if (spawned[0] != null) spawned[0].discard();
        h.assertTrue(spawned[0] == null, "a disabled waterspout formed");
        int result = command(h, new Vec3(12.5, 2, 12.5), "pirates hazard spawn waterspout");
        h.assertTrue(result == 0, "the spawn command did not refuse a disabled waterspout");
        h.assertTrue(hazardsNear(h, new Vec3(12.5, 2, 12.5), 3).isEmpty(), "the refused command left a hazard");
        HazardEntity existing = HazardTestSupport.hazard(h, HazardKind.WATERSPOUT, new Vec3(12.5, 2, 12.5));
        h.runAfterDelay(3, () -> {
            h.assertTrue(existing.isRemoved(), "an existing waterspout stayed although waterspouts are disabled");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ commands and lifetime

    /** {@code /pirates hazard spawn whirlpool} places one, {@code /pirates hazard clear 6} removes it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 40, batch = BATCH)
    public static void commandSpawnsAndClears(GameTestHelper h) {
        Vec3 at = new Vec3(12.5, 2, 12.5);
        int spawned = command(h, at, "pirates hazard spawn whirlpool");
        List<HazardEntity> found = hazardsNear(h, at, 2);
        found.forEach(e -> HazardTestSupport.onEnd(h, e::discard));
        h.assertTrue(spawned == 1, "the spawn command returned " + spawned);
        h.assertTrue(found.size() == 1 && found.get(0) instanceof WhirlpoolEntity, "expected one whirlpool, found " + found);
        h.assertTrue(found.get(0).lifetime() == HazardsConfig.WHIRLPOOL_DURATION.get(), "the whirlpool's lifetime is not the configured one");
        int cleared = command(h, at, "pirates hazard clear 6");
        h.assertTrue(cleared >= 1, "the clear command removed " + cleared);
        h.assertTrue(found.get(0).isRemoved(), "the whirlpool is still there");
        h.succeed();
    }

    /** A hazard is removed when its lifetime runs out. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 60, batch = BATCH)
    public static void hazardExpiresAfterItsLifetime(GameTestHelper h) {
        HazardEntity spout = HazardTestSupport.hazard(h, HazardKind.WATERSPOUT, new Vec3(12.5, 2, 12.5));
        spout.setLifetime(20);
        h.runAfterDelay(10, () -> h.assertTrue(!spout.isRemoved(), "the waterspout vanished early"));
        h.runAfterDelay(25, () -> {
            h.assertTrue(spout.isRemoved(), "the waterspout outlived its lifetime");
            h.succeed();
        });
    }
}

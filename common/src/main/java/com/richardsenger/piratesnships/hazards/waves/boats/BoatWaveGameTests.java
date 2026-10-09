package com.richardsenger.piratesnships.hazards.waves.boats;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.hazard.HazardConfig;
import com.richardsenger.piratesnships.hazards.waves.SeaState;
import com.richardsenger.piratesnships.hazards.waves.SeaStates;
import com.richardsenger.piratesnships.hazards.waves.WaveField;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests of vanilla boats and floating items on the waves (WAV6, docs/design.md §5.4). Like {@code WaveGameTests}
 * every test holds its own sea state (level-wide, so its own batch) with the field's phases pinned to the test, and
 * clears it when it ends. The GameTest world's own sea level lies in its bedrock, so each test builds a sea in its
 * structure and names its level with {@link BoatWaves#setTestSeaLevel}.
 *
 * <p>The structure ({@link #sea}): a sea basin of 7 water blocks (x 1..14, the full wave height) and, behind a wall, a
 * pool one block deep at the same surface level (x 17..22), whose fade is 0.
 */
public final class BoatWaveGameTests {

    private static final double TOWARD_EAST = 90.0;
    /** Ticks a boat gets to settle into its float before a test measures. */
    private static final int SETTLE = 100;
    /** One minute: one period of the wave groups. */
    private static final int WINDOW = 1200;

    private BoatWaveGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(BoatWaveGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** Builds the sea basin and the pool; returns the sea level (world y of the air above the surface blocks, y=8). */
    private static int sea(GameTestHelper h) {
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                boolean edge = x == 0 || x == 15 || x == 16 || x == 23 || z == 0 || z == 23;
                boolean pool = x >= 16;
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                for (int y = 2; y <= 9; y++) {
                    boolean solid = edge || pool && y <= 7;
                    h.setBlock(new BlockPos(x, y, z), solid ? Blocks.STONE : y <= 8 ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
        int seaLevel = h.absolutePos(new BlockPos(0, 9, 0)).getY();
        ServerLevel level = h.getLevel();
        BoatWaves.setTestSeaLevel(level, seaLevel, level.getGameTime() + 3000);
        return seaLevel;
    }

    /** Holds {@code state} toward +X with the phases pinned to the test's start and to {@code at}. */
    private static void hold(GameTestHelper h, SeaState state, Vec3 at, long ticks) {
        ServerLevel level = h.getLevel();
        SeaStates.set(level, state, TOWARD_EAST, level.getGameTime() + ticks,
                new WaveField.Origin(level.getGameTime() - h.getTick(), at.x, at.z));
    }

    private static void release(GameTestHelper h, Entity... entities) {
        SeaStates.clear(h.getLevel());
        BoatWaves.setTestSeaLevel(h.getLevel(), null, 0);
        for (Entity e : entities) {
            e.discard();
        }
    }

    private static Boat boat(GameTestHelper h, double x, double y, double z) {
        Vec3 p = h.absoluteVec(new Vec3(x, y, z));
        Boat boat = EntityType.BOAT.create(h.getLevel());
        if (boat == null) {
            throw new AssertionError("no boat");
        }
        boat.moveTo(p.x, p.y, p.z, 0.0f, 0.0f);
        h.getLevel().addFreshEntity(boat);
        return boat;
    }

    /** Min and max of a boat's y and the ticks its bottom was not below the moving surface. */
    private static final class Track {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        int out, active, samples;

        double half() {
            return (max - min) / 2.0;
        }
    }

    private static Track track(GameTestHelper h, Entity e, long from, long to) {
        Track t = new Track();
        h.onEachTick(() -> {
            long tick = h.getTick();
            if (tick < from || tick >= to || e.isRemoved()) {
                return;
            }
            double y = e.getY();
            t.min = Math.min(t.min, y);
            t.max = Math.max(t.max, y);
            t.samples++;
            BoatWaves.Surface s = BoatWaves.forBoat(e.level(), e.getX(), e.getBoundingBox().minY, e.getZ(), e.onGround());
            if (s != null) {
                t.active++;
                if (e.getBoundingBox().minY >= s.y()) {
                    t.out++;
                }
            }
        });
        return t;
    }

    private static String f3(double d) {
        return String.format("%.3f", d);
    }

    // ------------------------------------------------------------------ tests

    /**
     * A boat at rest in a storm rises and falls with the surface: a half range of its y over a minute between 0.4 and
     * 1.0 × the storm's amplitude, and its bottom stays below the moving water level every tick.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = SETTLE + WINDOW + 100, batch = "pirates_n_ships_wav6_storm_boat")
    public static void aBoatRidesAStormSea(GameTestHelper h) {
        sea(h);
        Boat boat = boat(h, 7.5, 9.0, 11.5);
        hold(h, SeaState.STORM, boat.position(), SETTLE + WINDOW + 200);
        Track t = track(h, boat, SETTLE, SETTLE + WINDOW);
        h.runAfterDelay(SETTLE + WINDOW + 1, () -> {
            double amplitude = SeaState.STORM.amplitude() * HazardConfig.WAVE_AMPLITUDE.get();
            double ratio = t.half() / amplitude;
            Constants.LOG.info("[boat wave test] storm: y half range {} blocks = {} x the amplitude, {} of {} ticks on the moving sea, {} out of the water",
                    f3(t.half()), f3(ratio), t.active, t.samples, t.out);
            release(h, boat);
            h.assertTrue(t.active == t.samples && t.samples >= WINDOW - 1, "the boat was off the moving sea on "
                    + (t.samples - t.active) + " of " + t.samples + " ticks");
            h.assertTrue(t.out == 0, "the boat's bottom rose above the moving water on " + t.out + " ticks");
            h.assertTrue(ratio >= 0.4 && ratio <= 1.0, "storm half range " + f3(ratio) + " x the amplitude, expected 0.4..1.0");
            h.succeed();
        });
    }

    /** A floating item bobs with the storm, near its float level. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = SETTLE + WINDOW + 100, batch = "pirates_n_ships_wav6_storm_item")
    public static void anItemBobsWithAStormSea(GameTestHelper h) {
        sea(h);
        Vec3 p = h.absoluteVec(new Vec3(7.5, 9.2, 11.5));
        ItemEntity item = new ItemEntity(h.getLevel(), p.x, p.y, p.z, new ItemStack(Items.STICK));
        item.setDeltaMovement(Vec3.ZERO);
        item.setNeverPickUp();
        h.getLevel().addFreshEntity(item);
        hold(h, SeaState.STORM, p, SETTLE + WINDOW + 200);
        Track t = new Track();
        double[] worst = {0.0};
        h.onEachTick(() -> {
            if (h.getTick() < SETTLE || h.getTick() >= SETTLE + WINDOW || item.isRemoved()) {
                return;
            }
            t.min = Math.min(t.min, item.getY());
            t.max = Math.max(t.max, item.getY());
            t.samples++;
            BoatWaves.Surface s = BoatWaves.forItem(item.level(), item.getX(), item.getBoundingBox().minY, item.getZ(), item.onGround());
            if (s != null) {
                t.active++;
                worst[0] = Math.max(worst[0], Math.abs(s.y() - BoatWaveRule.ITEM_DRAFT - item.getY()));
            }
        });
        h.runAfterDelay(SETTLE + WINDOW + 1, () -> {
            double ratio = t.half() / (SeaState.STORM.amplitude() * HazardConfig.WAVE_AMPLITUDE.get());
            Constants.LOG.info("[boat wave test] item: y half range {} = {} x the amplitude, {} of {} ticks on the sea, worst gap {}",
                    f3(t.half()), f3(ratio), t.active, t.samples, f3(worst[0]));
            release(h, item);
            h.assertTrue(t.active == t.samples && t.samples > 0, "the item left the moving sea");
            h.assertTrue(ratio >= 0.4 && ratio <= 1.0, "item half range " + f3(ratio) + " x the amplitude");
            h.assertTrue(worst[0] < 0.25, "the item strayed " + f3(worst[0]) + " from its float level");
            h.succeed();
        });
    }

    /** On the GameTest server's own flat sea a boat floats level, as in vanilla. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = SETTLE + 400, batch = "pirates_n_ships_wav6_flat")
    public static void aBoatStaysLevelOnAFlatSea(GameTestHelper h) {
        sea(h);
        Boat boat = boat(h, 7.5, 9.0, 11.5);
        Track t = track(h, boat, SETTLE, SETTLE + 300);
        h.runAfterDelay(SETTLE + 301, () -> {
            Constants.LOG.info("[boat wave test] flat: y half range {}", f3(t.half()));
            release(h, boat);
            h.assertTrue(t.active == 0, "the moving sea acted on a flat sea");
            h.assertTrue(t.half() < 0.01, "the boat moved by " + f3(t.half()) + " on a flat sea");
            h.succeed();
        });
    }

    /** With {@code waves.boats} off a storm leaves a boat level. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = SETTLE + 700, batch = "pirates_n_ships_config_wav6_off")
    public static void withBoatsOffAStormLeavesABoatLevel(GameTestHelper h) {
        ConfigOverrides.during(h, HazardConfig.BOATS, false);
        sea(h);
        Boat boat = boat(h, 7.5, 9.0, 11.5);
        hold(h, SeaState.STORM, boat.position(), SETTLE + 800);
        Track t = track(h, boat, SETTLE, SETTLE + 600);
        h.runAfterDelay(SETTLE + 601, () -> {
            Constants.LOG.info("[boat wave test] boats off: y half range {}", f3(t.half()));
            release(h, boat);
            h.assertTrue(t.active == 0, "the moving sea acted with waves.boats off");
            h.assertTrue(t.half() < 0.01, "the boat moved by " + f3(t.half()) + " with waves.boats off");
            h.succeed();
        });
    }

    /** A boat in a one-block-deep pool beside the stormy sea, at the same surface level, does not move. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = SETTLE + 700, batch = "pirates_n_ships_wav6_pool")
    public static void aBoatInAShallowPoolBesideTheSeaStaysLevel(GameTestHelper h) {
        sea(h);
        Boat boat = boat(h, 19.5, 9.0, 11.5);
        hold(h, SeaState.STORM, boat.position(), SETTLE + 800);
        Track t = track(h, boat, SETTLE, SETTLE + 600);
        h.runAfterDelay(SETTLE + 601, () -> {
            Constants.LOG.info("[boat wave test] pool: y half range {}", f3(t.half()));
            release(h, boat);
            h.assertTrue(t.active == 0, "the moving sea acted in a one-block-deep pool");
            h.assertTrue(t.half() < 0.01, "the boat in the pool moved by " + f3(t.half()));
            h.succeed();
        });
    }

    /**
     * A boat in the dry hold of a floating hull is not on the moving sea although the world's sea lies under it: the
     * rule never acts, and the boat stays on the hold's floor. The hull's own heave is off so the hold holds still.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = SETTLE + 700, batch = "pirates_n_ships_config_wav6_dry_hull")
    public static void aBoatInADryHullIgnoresTheWaves(GameTestHelper h) {
        ConfigOverrides.during(h, HazardConfig.HEAVE, false);
        DryHullGameTests.basin(h, 0, 23, true);
        ServerLevel level = h.getLevel();
        BoatWaves.setTestSeaLevel(level, h.absolutePos(new BlockPos(0, 8, 0)).getY(), level.getGameTime() + 3000);
        DryHullGameTests.Fixture f = DryHullGameTests.assemble(h, DryHullGameTests.hull(h, 9, false));
        Boat[] boat = {null};
        Track t = new Track();
        h.runAfterDelay(20, () -> {
            Vec3 floor = f.ship().toWorld(Vec3.atBottomCenterOf(f.hold(0, -3, 0)));
            Boat b = EntityType.BOAT.create(level);
            if (b == null) {
                throw new AssertionError("no boat");
            }
            b.moveTo(floor.x, floor.y + 0.05, floor.z, 0.0f, 0.0f);
            level.addFreshEntity(b);
            boat[0] = b;
            hold(h, SeaState.STORM, floor, SETTLE + 800);
        });
        h.onEachTick(() -> {
            Boat b = boat[0];
            if (b == null || b.isRemoved() || h.getTick() < SETTLE || h.getTick() >= SETTLE + 600) {
                return;
            }
            double rel = b.getY() - f.ship().toWorld(Vec3.atBottomCenterOf(f.hold(0, -3, 0))).y;
            t.min = Math.min(t.min, rel);
            t.max = Math.max(t.max, rel);
            t.samples++;
            if (BoatWaves.forBoat(level, b.getX(), b.getBoundingBox().minY, b.getZ(), b.onGround()) != null) {
                t.active++;
            }
        });
        h.runAfterDelay(SETTLE + 601, () -> {
            Constants.LOG.info("[boat wave test] dry hull: y over the hold floor {}..{}, {} of {} ticks on the moving sea",
                    f3(t.min), f3(t.max), t.active, t.samples);
            release(h, boat[0] == null ? new Entity[0] : new Entity[] {boat[0]});
            SableShips.remove(f.ship());
            h.assertTrue(t.samples > 0, "no boat in the hold");
            h.assertTrue(t.active == 0, "the moving sea acted on a boat in a dry hold on " + t.active + " ticks");
            h.assertTrue(t.max - t.min < 0.3, "the boat in the hold moved by " + f3(t.max - t.min) + " against its floor");
            h.succeed();
        });
    }
}

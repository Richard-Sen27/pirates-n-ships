package com.richardsenger.piratesnships.trade.cargo;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.cargo.ShipCargo;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.trade.TradeConfig;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * CW1 (design.md §4.9): cargo weight acting on ships. Uses the spike 2 test hull of {@link DryHullGameTests}: a closed
 * 5×4×5 plank box (hold 3×2×3 at y 6..7) floating in a stone basin. Containers go into a hold corner before assembly.
 */
public final class CargoWeightGameTests {

    static final String CONFIG_BATCH = "pirates_n_ships_config_cargo_weight";

    /** Iron ingots weigh 1 unit each (trade good "iron"): 16 stacks = 1,024 units = a full crate (CargoMass.CRATE). */
    private static final int FULL_CRATE_INGOTS = 1024;

    private CargoWeightGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CargoWeightGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** Hull at x in [a, a+4] with {@code block} in the hold corner (a+1, 6, 10); returns the helm. */
    private static BlockPos hullWith(GameTestHelper h, int a, Block block) {
        BlockPos helm = DryHullGameTests.hull(h, a, false);
        h.setBlock(new BlockPos(a + 1, 6, 10), block);
        return helm;
    }

    private record Ship(ShipBody body, BlockPos helmPlot, BlockPos cornerPlot, BlockPos oppositePlot) {
        Vec3 world(BlockPos plot) {
            return body.toWorld(Vec3.atCenterOf(plot));
        }

        /** Height of the container corner above the opposite hold corner [blocks]; negative = trimmed toward it. */
        double trim() {
            return world(cornerPlot).y - world(oppositePlot).y;
        }
    }

    /** Assembles the hull with its helm at {@code helm} (relative); the container corner is helm + (-1, -3, -1). */
    private static Ship assemble(GameTestHelper h, BlockPos helm) {
        ServerLevel level = h.getLevel();
        AssemblyResult r = ShipTestCleanup.assemble(h, helm);
        if (r.shipId() == null) {
            throw new AssertionError("assembly failed: " + r);
        }
        ShipBody ship = SableShips.byId(level, r.shipId());
        if (ship == null) {
            throw new AssertionError("no ship after assembly");
        }
        BlockPos helmPlot = ship.plotBlocks().stream()
                .filter(p -> level.getBlockState(p).is(com.richardsenger.piratesnships.ship.assembly.AssemblyContent.HELM.get()))
                .findFirst().orElseThrow();
        return new Ship(ship, helmPlot, helmPlot.offset(-1, -3, -1), helmPlot.offset(1, -3, 1));
    }

    private static void fillCrate(CargoContainerBlockEntity crate, int ingots) {
        while (ingots > 0) {
            int n = Math.min(64, ingots);
            ItemStack s = new ItemStack(Items.IRON_INGOT, n);
            int in = crate.insert(s);
            if (in <= 0) {
                throw new AssertionError("crate refused iron ingots");
            }
            ingots -= in;
        }
    }

    /** 27 stacks of iron blocks: not a trade good, so 0.25 units each, 432 units (8.64 kpg at factor 1). */
    private static void fillChest(GameTestHelper h, BlockPos chest) {
        ChestBlockEntity be = (ChestBlockEntity) h.getBlockEntity(chest);
        for (int i = 0; i < be.getContainerSize(); i++) {
            be.setItem(i, new ItemStack(Items.IRON_BLOCK, 64));
        }
    }

    // ------------------------------------------------------------------ tests

    /** Filling a crate on an assembled ship sets its load to full and adds the full crate's mass in Sable. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120)
    public static void fillingACrateOnAShipAddsItsMass(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        Ship ship = assemble(h, hullWith(h, 9, ShipDecor.CARGO_CRATE.get()));
        ServerLevel level = h.getLevel();
        double[] before = {0};
        h.runAfterDelay(10, () -> {
            h.assertValueEqual(level.getBlockState(ship.cornerPlot()).getValue(CargoLoad.LOAD), 0, "empty crate load");
            before[0] = ship.body().mass();
            fillCrate((CargoContainerBlockEntity) level.getBlockEntity(ship.cornerPlot()), FULL_CRATE_INGOTS);
        });
        h.runAfterDelay(30, () -> {
            int load = level.getBlockState(ship.cornerPlot()).getValue(CargoLoad.LOAD);
            double after = ship.body().mass();
            SableShips.remove(ship.body());
            h.assertValueEqual(load, CargoMass.MAX_LOAD, "full crate load");
            double expected = CargoMass.CRATE.fullExtraMass();
            h.assertTrue(Math.abs(after - before[0] - expected) < 1e-3,
                    "mass grew by " + (after - before[0]) + ", expected " + expected + " (before " + before[0] + ")");
            h.succeed();
        });
    }

    /**
     * A chest full of iron blocks makes the ship sit lower and trim toward the chest, compared with an empty chest.
     * 8.64 kpg × 11 = 95 N on a hull of about 45 kpg. Measured over two runs (mean of ticks 40..99): helm 0.82 to 0.83
     * blocks lower, the chest corner 1.70 to 1.72 blocks below the opposite hold corner (empty: 0.08 above), because
     * the small box hull has almost no metacentric height (docs/sable-notes.md §9.0f). Thresholds at half of that.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 140)
    public static void chestOfIronSinksAndTrimsTheShip(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 11, true);
        DryHullGameTests.basin(h, 12, 23, true);
        BlockPos emptyHelm = hullWith(h, 3, Blocks.CHEST);
        BlockPos loadedHelm = hullWith(h, 15, Blocks.CHEST);
        fillChest(h, new BlockPos(16, 6, 10));
        Ship empty = assemble(h, emptyHelm);
        Ship loaded = assemble(h, loadedHelm);
        double[] sum = new double[5];
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t >= 40 && t < 100 && !loaded.body().isRemoved() && !empty.body().isRemoved()) {
                sum[0] += empty.world(empty.helmPlot()).y;
                sum[1] += loaded.world(loaded.helmPlot()).y;
                sum[2] += empty.trim();
                sum[3] += loaded.trim();
                sum[4]++;
            }
        });
        h.runAfterDelay(100, () -> {
            ShipCargo.Snapshot snap = ShipCargo.last(h.getLevel(), loaded.body().id());
            double n = sum[4];
            double sink = (sum[0] - sum[1]) / n;
            double trimDiff = (sum[3] - sum[2]) / n;
            String info = String.format("sink %.3f, trim empty %.3f loaded %.3f, vanilla weight %s",
                    sink, sum[2] / n, sum[3] / n, snap == null ? "-" : snap.vanillaWeight());
            com.richardsenger.piratesnships.Constants.LOG.info("CW1 chestOfIronSinksAndTrimsTheShip: {}", info);
            SableShips.remove(empty.body());
            SableShips.remove(loaded.body());
            h.assertTrue(snap != null && Math.abs(snap.vanillaWeight() - 27 * 64 * 0.25) < 1e-6, "chest not weighed: " + info);
            h.assertTrue(sink > 0.4, "loaded ship does not sit lower: " + info);
            h.assertTrue(trimDiff < -0.8, "loaded ship does not trim toward the chest: " + info);
            h.succeed();
        });
    }

    /** {@code /pirates ship info} reports the load level: a crate full of iron overloads the small test hull. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void shipInfoShowsTheLoadLevel(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        Ship ship = assemble(h, hullWith(h, 9, ShipDecor.CARGO_CRATE.get()));
        ServerLevel level = h.getLevel();
        h.runAfterDelay(5, () -> {
            List<Component> before = info(h, ship);
            fillCrate((CargoContainerBlockEntity) level.getBlockEntity(ship.cornerPlot()), FULL_CRATE_INGOTS);
            List<Component> after = info(h, ship);
            SableShips.remove(ship.body());
            assertLoad(h, before, CargoWeight.LoadLevel.LIGHT);
            assertLoad(h, after, CargoWeight.LoadLevel.OVERLOADED);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ config off

    /** With {@code cargo_weight_affects_ships} off a filled crate keeps load 0 and the ship's mass. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = CONFIG_BATCH)
    public static void configOffLeavesTheCrateMassAlone(GameTestHelper h) {
        ConfigOverrides.during(h, TradeConfig.CARGO_WEIGHT_AFFECTS_SHIPS, false);
        DryHullGameTests.basin(h, 0, 23, true);
        Ship ship = assemble(h, hullWith(h, 9, ShipDecor.CARGO_CRATE.get()));
        ServerLevel level = h.getLevel();
        double[] before = {0};
        h.runAfterDelay(10, () -> {
            before[0] = ship.body().mass();
            fillCrate((CargoContainerBlockEntity) level.getBlockEntity(ship.cornerPlot()), FULL_CRATE_INGOTS);
        });
        h.runAfterDelay(30, () -> {
            int load = level.getBlockState(ship.cornerPlot()).getValue(CargoLoad.LOAD);
            double after = ship.body().mass();
            SableShips.remove(ship.body());
            h.assertValueEqual(load, 0, "crate load with the toggle off");
            h.assertTrue(Math.abs(after - before[0]) < 1e-6, "mass changed: " + before[0] + " -> " + after);
            h.succeed();
        });
    }

    /** With the toggle off a full chest pulls nothing: both ships float at the same height. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 140, batch = CONFIG_BATCH)
    public static void configOffChestDoesNotSinkTheShip(GameTestHelper h) {
        ConfigOverrides.during(h, TradeConfig.CARGO_WEIGHT_AFFECTS_SHIPS, false);
        DryHullGameTests.basin(h, 0, 11, true);
        DryHullGameTests.basin(h, 12, 23, true);
        BlockPos emptyHelm = hullWith(h, 3, Blocks.CHEST);
        BlockPos loadedHelm = hullWith(h, 15, Blocks.CHEST);
        fillChest(h, new BlockPos(16, 6, 10));
        Ship empty = assemble(h, emptyHelm);
        Ship loaded = assemble(h, loadedHelm);
        double[] sum = new double[3];
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t >= 40 && t < 100 && !loaded.body().isRemoved() && !empty.body().isRemoved()) {
                sum[0] += empty.world(empty.helmPlot()).y;
                sum[1] += loaded.world(loaded.helmPlot()).y;
                sum[2]++;
            }
        });
        h.runAfterDelay(100, () -> {
            double sink = (sum[0] - sum[1]) / sum[2];
            SableShips.remove(empty.body());
            SableShips.remove(loaded.body());
            h.assertTrue(Math.abs(sink) < 0.05, "ships float differently with the toggle off: sink " + sink);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ helpers

    private static List<Component> info(GameTestHelper h, Ship ship) {
        List<Component> out = new ArrayList<>();
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(Component component) {
                out.add(component);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = h.getLevel().getServer().createCommandSourceStack()
                .withSource(capture).withLevel(h.getLevel()).withPermission(4)
                .withPosition(ship.world(ship.helmPlot()));
        h.getLevel().getServer().getCommands().performPrefixedCommand(source, "pirates ship info");
        return out;
    }

    private static void assertLoad(GameTestHelper h, List<Component> lines, CargoWeight.LoadLevel expected) {
        for (Component c : lines) {
            if (c.getContents() instanceof TranslatableContents t && ShipCargo.KEY_INFO.equals(t.getKey())) {
                Object level = t.getArgs()[0];
                String key = level instanceof Component lc && lc.getContents() instanceof TranslatableContents lt ? lt.getKey() : String.valueOf(level);
                h.assertValueEqual(key, expected.translationKey(), "load level in /pirates ship info (" + c.getString() + ")");
                return;
            }
        }
        throw new AssertionError("no load line in /pirates ship info: " + lines);
    }
}

package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.combat.CombatConfig;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.hull.FloodingConfig;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Cannons in a real server (docs/design.md §8.2, G9). Loading, aiming and firing go through {@link CannonService}, the
 * same calls the block's use makes. A fired ball that would leave the test area is removed at once, so no stray ball
 * hits another test's ship. The ship tests use the closed 5×4×5 plank hull of {@link DryHullGameTests} afloat in its
 * basin (hold 3×2×3; the west wall cell at hold (−2, −3, 0) lies below the waterline).
 */
public final class CannonGameTests {

    private static final String HULL_BATCH = "pirates_n_ships_config_cannon_hull";
    private static final String NO_BLOCK_DAMAGE_BATCH = "pirates_n_ships_config_cannon_no_block_damage";
    private static final String TWO_BLOCKS_BATCH = "pirates_n_ships_config_cannon_two_blocks";
    private static final String DISABLED_BATCH = "pirates_n_ships_config_cannon_disabled";

    private CannonGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CannonGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    private static BlockPos cannon(GameTestHelper h, BlockPos rel, Direction facing) {
        h.setBlock(rel, CannonContent.CANNON.get().defaultBlockState().setValue(CannonBlock.FACING, facing));
        return h.absolutePos(rel);
    }

    private static CannonLoad load(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getValue(CannonBlock.LOAD);
    }

    /** Loads powder and ball with a creative player (no items used). */
    private static void loadFully(GameTestHelper h, BlockPos pos) {
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true;
        ServerLevel level = h.getLevel();
        h.assertTrue(CannonService.load(level, pos, creative, new ItemStack(Items.GUNPOWDER)).outcome() == CannonService.Outcome.POWDER_IN,
                "powder was refused");
        h.assertTrue(CannonService.load(level, pos, creative, new ItemStack(CombatContent.CANNONBALL.get())).outcome()
                == CannonService.Outcome.BALL_IN, "the ball was refused");
    }

    private static List<CannonballEntity> balls(GameTestHelper h) {
        return h.getEntities(CannonContent.CANNONBALL.get());
    }

    private static void assertNear(GameTestHelper h, Vec3 actual, Vec3 expected, double tolerance, String what) {
        h.assertTrue(actual.distanceTo(expected) <= tolerance, what + ": expected " + expected + ", got " + actual);
    }

    /** A plank wall {@code depth} blocks thick from x = 4 eastwards at y 1..2, z 4, with stone behind it. */
    private static void plankWall(GameTestHelper h, int depth) {
        for (int y = 1; y <= 2; y++) {
            for (int d = 0; d < depth; d++) h.setBlock(new BlockPos(4 + d, y, 4), Blocks.OAK_PLANKS);
            h.setBlock(new BlockPos(4 + depth, y, 4), Blocks.STONE);
        }
    }

    // ------------------------------------------------------------------ loading and aiming

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void loadingNeedsPowderThenBall(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = cannon(h, new BlockPos(4, 1, 4), Direction.EAST);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack powder = new ItemStack(Items.GUNPOWDER, 3);
        ItemStack balls = new ItemStack(CombatContent.CANNONBALL.get(), 3);

        h.assertTrue(CannonService.load(level, pos, player, balls).outcome() == CannonService.Outcome.NEEDS_POWDER_FIRST,
                "a ball went in before the powder");
        h.assertTrue(balls.getCount() == 3 && load(level, pos) == CannonLoad.EMPTY, "the refused ball was used");
        h.assertTrue(CannonService.load(level, pos, player, powder).outcome() == CannonService.Outcome.POWDER_IN, "powder refused");
        h.assertTrue(powder.getCount() == 2 && load(level, pos) == CannonLoad.POWDER, "powder not taken: " + powder.getCount());
        h.assertTrue(CannonService.load(level, pos, player, powder).outcome() == CannonService.Outcome.ALREADY_POWDERED,
                "powder went in twice");
        h.assertTrue(powder.getCount() == 2, "the second powder was used");
        h.assertTrue(CannonService.load(level, pos, player, balls).outcome() == CannonService.Outcome.BALL_IN, "ball refused");
        h.assertTrue(balls.getCount() == 2 && load(level, pos) == CannonLoad.LOADED, "ball not taken");
        h.assertTrue(CannonService.load(level, pos, player, balls).outcome() == CannonService.Outcome.ALREADY_LOADED,
                "a second ball went in");
        h.assertTrue(CannonService.load(level, pos, player, powder).outcome() == CannonService.Outcome.ALREADY_LOADED,
                "powder went into a loaded cannon");
        h.assertTrue(balls.getCount() == 2 && powder.getCount() == 2, "a refused load used an item");

        BlockPos other = cannon(h, new BlockPos(2, 1, 2), Direction.EAST);
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true;
        ItemStack one = new ItemStack(Items.GUNPOWDER);
        CannonService.load(level, other, creative, one);
        h.assertTrue(one.getCount() == 1 && load(level, other) == CannonLoad.POWDER, "creative loading used the powder");
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aimingStepsThroughTheElevationsAndStopsAtTheEnds(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = cannon(h, new BlockPos(4, 1, 4), Direction.NORTH);
        CannonBlockEntity be = (CannonBlockEntity) level.getBlockEntity(pos);
        h.assertTrue(be != null, "no block entity");
        h.assertTrue(CannonConfig.elevationDegrees(be.elevationStep()) == 0.0, "a new cannon is not level");
        double[] seen = new double[8];
        for (int i = 0; i < 8; i++) {
            h.assertTrue(CannonService.aim(level, pos, true).outcome() == CannonService.Outcome.AIMED, "aiming failed");
            seen[i] = CannonConfig.elevationDegrees(be.elevationStep());
        }
        h.assertTrue(seen[0] == 5.0 && seen[3] == 20.0 && seen[7] == 20.0, "up steps: " + java.util.Arrays.toString(seen));
        CannonService.Barrel up = CannonService.barrel(level, pos);
        h.assertTrue(Math.abs(up.direction().y - Math.sin(Math.toRadians(20))) < 1e-9 && up.direction().z < 0,
                "the barrel at 20° points " + up.direction());
        for (int i = 0; i < 8; i++) CannonService.aim(level, pos, false);
        h.assertTrue(CannonConfig.elevationDegrees(be.elevationStep()) == -5.0, "the lowest step is not -5°");
        h.succeed();
    }

    // ------------------------------------------------------------------ firing

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 160)
    public static void firingSpawnsOneBallUnloadsAndStartsTheReload(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = cannon(h, new BlockPos(1, 1, 4), Direction.EAST);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        h.assertTrue(CannonService.fire(level, pos, player).outcome() == CannonService.Outcome.NOT_LOADED, "an empty cannon fired");
        h.assertTrue(balls(h).isEmpty(), "an empty cannon made a ball");
        loadFully(h, pos);

        CannonService.Use use = CannonService.fire(level, pos, player);
        h.assertTrue(use.outcome() == CannonService.Outcome.FIRED && use.ball() != null, "no shot: " + use.outcome());
        CannonballEntity ball = use.ball();
        h.assertTrue(balls(h).size() == 1, "expected one ball, found " + balls(h).size());
        h.assertTrue(ball.getOwner() == player, "the firing player does not own the ball");
        Vec3 expected = Vec3.atBottomCenterOf(pos).add(CannonRules.MUZZLE_LENGTH, CannonRules.PIVOT_HEIGHT, 0);
        assertNear(h, ball.position(), expected, 1e-6, "muzzle");
        assertNear(h, ball.getDeltaMovement(), new Vec3(CannonConfig.MUZZLE_VELOCITY.get(), 0, 0), 1e-6, "ball velocity");
        h.assertTrue(load(level, pos) == CannonLoad.EMPTY, "the cannon is still loaded");
        ball.discard();

        ItemStack powder = new ItemStack(Items.GUNPOWDER, 2);
        h.assertTrue(CannonService.load(level, pos, player, powder).outcome() == CannonService.Outcome.RELOADING,
                "powder went into a hot barrel");
        h.assertTrue(powder.getCount() == 2, "the refused powder was used");
        h.runAfterDelay(CannonConfig.RELOAD_TICKS.get() + 1, () -> {
            h.assertTrue(CannonService.load(level, pos, player, powder).outcome() == CannonService.Outcome.POWDER_IN,
                    "the cannon did not take powder after the reload time");
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void ballHitDealsTheConfiguredDamage(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // x = 6.0: the ball's first step (to x = 5.5) then ends inside the golem's box inflated by the hit margin. A
        // step that ends exactly on that box (golem at x = 6.5) is missed by vanilla's clip, and the next step starts
        // inside the box, which vanilla's projectile clip does not count either.
        IronGolem target = h.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new Vec3(6.0, 1, 4.5));
        float full = target.getHealth();
        float damage = CannonConfig.entityDamage();
        BlockPos pos = cannon(h, new BlockPos(1, 1, 4), Direction.EAST);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        loadFully(h, pos);
        CannonService.fire(level, pos, player);
        h.succeedWhen(() -> {
            h.assertTrue(target.getHealth() < full, "the golem has not been hit yet");
            h.assertTrue(Math.abs(target.getHealth() - (full - damage)) < 0.01f,
                    "expected health " + (full - damage) + ", got " + target.getHealth());
            h.assertTrue(target.getLastHurtByMob() == player, "the firing player should be the attacker");
            h.assertTrue(balls(h).isEmpty(), "the ball should be gone after the hit");
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void ballBreaksOnePlankOnLand(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        plankWall(h, 2);
        BlockPos pos = cannon(h, new BlockPos(1, 1, 4), Direction.EAST);
        loadFully(h, pos);
        CannonService.fire(level, pos, null);
        h.succeedWhen(() -> {
            h.assertTrue(balls(h).isEmpty(), "the ball is still flying");
            h.assertBlockPresent(Blocks.AIR, new BlockPos(4, 1, 4));
            h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(5, 1, 4));
            h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(4, 2, 4));
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60, batch = TWO_BLOCKS_BATCH)
    public static void threeBlocksPerHitBreakBothPlanksButNotTheStoneBehind(GameTestHelper h) {
        ConfigOverrides.during(h, CannonConfig.BLOCKS_PER_HIT, 3);
        ServerLevel level = h.getLevel();
        plankWall(h, 2);
        BlockPos pos = cannon(h, new BlockPos(1, 1, 4), Direction.EAST);
        loadFully(h, pos);
        CannonService.fire(level, pos, null);
        h.succeedWhen(() -> {
            h.assertTrue(balls(h).isEmpty(), "the ball is still flying");
            h.assertBlockPresent(Blocks.AIR, new BlockPos(4, 1, 4));
            h.assertBlockPresent(Blocks.AIR, new BlockPos(5, 1, 4));
            h.assertBlockPresent(Blocks.STONE, new BlockPos(6, 1, 4));
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60, batch = NO_BLOCK_DAMAGE_BATCH)
    public static void withoutBlockDamageThePlankStays(GameTestHelper h) {
        ConfigOverrides.during(h, CombatConfig.CANNON_BLOCK_DAMAGE, false);
        ServerLevel level = h.getLevel();
        plankWall(h, 1);
        BlockPos pos = cannon(h, new BlockPos(1, 1, 4), Direction.EAST);
        loadFully(h, pos);
        CannonService.fire(level, pos, null);
        h.runAfterDelay(10, () -> {
            h.assertTrue(balls(h).isEmpty(), "the ball did not stop at the plank");
            h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(4, 1, 4));
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = DISABLED_BATCH)
    public static void disabledCannonIsInert(GameTestHelper h) {
        ConfigOverrides.during(h, CannonConfig.ENABLED, false);
        ServerLevel level = h.getLevel();
        BlockPos pos = cannon(h, new BlockPos(4, 1, 4), Direction.EAST);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack powder = new ItemStack(Items.GUNPOWDER, 2);
        h.assertTrue(CannonService.load(level, pos, player, powder).outcome() == CannonService.Outcome.DISABLED, "a disabled cannon took powder");
        h.assertTrue(powder.getCount() == 2 && load(level, pos) == CannonLoad.EMPTY, "a disabled cannon changed");
        h.assertTrue(CannonService.aim(level, pos, true).outcome() == CannonService.Outcome.DISABLED, "a disabled cannon aimed");
        BlockState loaded = level.getBlockState(pos).setValue(CannonBlock.LOAD, CannonLoad.LOADED);
        level.setBlockAndUpdate(pos, loaded);
        h.assertTrue(CannonService.fire(level, pos, player).outcome() == CannonService.Outcome.DISABLED, "a disabled cannon fired");
        h.assertTrue(balls(h).isEmpty() && load(level, pos) == CannonLoad.LOADED, "a disabled cannon made a ball");
        h.succeed();
    }

    // ------------------------------------------------------------------ ships

    /**
     * A ball fired at the hull of a floating ship below the waterline destroys the plank, the hull runtime tracks the
     * hole as a breach, and water rises in the hold (ten times the default inflow keeps the test short).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300, batch = HULL_BATCH)
    public static void ballBelowTheWaterlineHolesTheHullAndItFloods(GameTestHelper h) {
        ConfigOverrides.during(h, FloodingConfig.INFLOW_RATE, 10.0);
        DryHullGameTests.basin(h, 0, 23, true);
        Fixture f = DryHullGameTests.assemble(h, DryHullGameTests.hull(h, 9, false));
        ServerLevel level = h.getLevel();
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos wall = f.hold(-2, -3, 0);
        double[] seen = {-1};
        h.runAfterDelay(20, () -> {
            h.assertTrue(level.getBlockState(wall).is(Blocks.OAK_PLANKS), "no wall at " + wall);
            h.assertTrue(f.runtime().simulation().totalVolume() == 0, "the hold is wet before the shot");
            Vec3 target = f.ship().toWorld(Vec3.atCenterOf(wall));
            Vec3 from = target.add(-2.0, 0, 0);
            Vec3 velocity = target.subtract(from).normalize().scale(CannonConfig.MUZZLE_VELOCITY.get());
            CannonballEntity ball = new CannonballEntity(level, from, velocity, CannonConfig.entityDamage(), 40);
            ball.setOwner(player);
            level.addFreshEntity(ball);
        });
        h.runAfterDelay(21, () -> h.succeedWhen(() -> {
            h.assertTrue(level.getBlockState(wall).isAir(), "the hull plank is still there");
            h.assertTrue(f.runtime().breaches().contains(wall), "the hole is not a breach");
            double v = f.runtime().simulation().totalVolume();
            if (seen[0] < 0 || v <= 0) {
                seen[0] = v;
                throw new GameTestAssertException("no water in the hold yet: " + v);
            }
            h.assertTrue(v > seen[0], "the water does not rise: " + seen[0] + " -> " + v);
            h.assertTrue(balls(h).isEmpty(), "the ball is still flying");
        }));
    }

    /**
     * A cannon on the deck of a floating ship fires from its muzzle in world space, and the ball carries the ship's
     * velocity: the ship is given 3 m/s east just before an eastward shot. The shot also pushes the ship back.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void cannonOnAShipFiresFromItsWorldMuzzleWithTheShipsVelocity(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        cannon(h, new BlockPos(12, 9, 12), Direction.EAST);
        Fixture f = DryHullGameTests.assemble(h, helm);
        ServerLevel level = h.getLevel();
        ShipBody ship = f.ship();
        BlockPos plot = ship.plotBlocks().stream().filter(p -> level.getBlockState(p).is(CannonContent.CANNON.get()))
                .findFirst().orElseThrow(() -> new GameTestAssertException("the cannon is not in the plot"));
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        loadFully(h, plot);
        h.runAfterDelay(20, () -> {
            ship.addVelocity(new Vector3d(3, 0, 0), new Vector3d());
            Vector3d before = ship.linearVelocity();
            Vec3 center = ship.toWorld(Vec3.atCenterOf(plot));
            Vec3 east = CannonService.rotate(ship.orientation(), new Vec3(1, 0, 0));
            Vec3 expected = center.add(east.scale(CannonRules.MUZZLE_LENGTH));

            CannonService.Use use = CannonService.fire(level, plot, player);
            h.assertTrue(use.outcome() == CannonService.Outcome.FIRED && use.ball() != null, "no shot: " + use.outcome());
            CannonballEntity ball = use.ball();
            Vec3 at = ball.position();
            Vec3 v = ball.getDeltaMovement();
            ball.discard();
            assertNear(h, at, expected, 1.0, "world muzzle");
            h.assertFalse(at.distanceTo(Vec3.atCenterOf(plot)) < 100, "the ball spawned in the plot");
            Vec3 carried = v.subtract(east.scale(CannonConfig.MUZZLE_VELOCITY.get()));
            h.assertTrue(carried.x > 0.1, "the ball did not get the ship's 3 m/s (0.15 blocks per tick): " + carried);
            Vector3d after = ship.linearVelocity();
            double expectedKick = CannonConfig.RECOIL_IMPULSE.get() / ship.mass();
            h.assertTrue(before.x - after.x > 0.5 * expectedKick,
                    "no recoil: " + before.x + " -> " + after.x + " m/s, expected about -" + expectedKick);
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120)
    public static void crewMemberFiresALoadedCannon(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        cannon(h, new BlockPos(10, 9, 10), Direction.WEST);
        for (int z = 1; z < 23; z++) {
            for (int y = 9; y <= 11; y++) h.setBlock(new BlockPos(2, y, z), Blocks.STONE); // backstop for the shot
        }
        Fixture f = DryHullGameTests.assemble(h, helm);
        ServerLevel level = h.getLevel();
        BlockPos plot = f.ship().plotBlocks().stream().filter(p -> level.getBlockState(p).is(CannonContent.CANNON.get()))
                .findFirst().orElseThrow(() -> new GameTestAssertException("the cannon is not in the plot"));
        StationRef ref = new StationRef(f.ship().id(), plot);
        UUID crew = UUID.randomUUID();
        h.runAfterDelay(5, () -> {
            h.assertTrue(Stations.occupy(level, ref, new StationState.Occupant(crew, false)) == StationState.OccupyResult.OCCUPIED,
                    "the cannon is not a station");
            h.assertTrue(Stations.order(level, ref, CannonStation.CannonOrder.FIRE) == Stations.OrderResult.NOTHING_TO_DO,
                    "an empty cannon took a fire order");
            loadFully(h, plot);
            h.assertTrue(Stations.order(level, ref, CannonStation.CannonOrder.FIRE) == Stations.OrderResult.STARTED,
                    "the fire order did not start");
        });
        h.runAfterDelay(6, () -> h.succeedWhen(() -> {
            h.assertTrue(load(level, plot) == CannonLoad.EMPTY, "the crew member has not fired yet");
            balls(h).forEach(CannonballEntity::discard);
            Stations.release(ref, crew);
        }));
    }
}

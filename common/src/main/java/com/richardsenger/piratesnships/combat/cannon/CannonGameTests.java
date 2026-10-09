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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
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

    /**
     * Sets a two-block cannon with its master at {@code rel} (relative) and the rear behind it, without the placement
     * checks; returns the master's absolute position. Also used by {@link CannonOrderGameTests}.
     */
    static BlockPos cannon(GameTestHelper h, BlockPos rel, Direction facing) {
        BlockState master = CannonContent.CANNON.get().defaultBlockState().setValue(CannonBlock.FACING, facing);
        h.setBlock(rel, master);
        h.setBlock(CannonRules.rearOf(rel, facing), CannonContent.CANNON.get().rearState(master));
        return h.absolutePos(rel);
    }

    /**
     * Places the block item {@code stack} into the free block at {@code rel} (relative) as {@code player} would by
     * clicking into that cell: the stack goes into the player's main hand and {@code ItemStack.useOn} runs, with the
     * loader's placement hooks (on NeoForge {@code CommonHooks.onPlaceItemIntoWorld}, which takes the item from the
     * context, i.e. the player's hand; vanilla's {@code GameTestHelper.placeAt} leaves the mock player's hand empty, so
     * it places nothing there). Returns whether the block was placed.
     */
    static boolean place(GameTestHelper h, Player player, ItemStack stack, BlockPos rel) {
        BlockPos pos = h.absolutePos(rel);
        BlockHitResult hit = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction();
    }

    /** The plot position of the master half of the (only) cannon on {@code ship}. */
    static BlockPos masterInPlot(ServerLevel level, ShipBody ship) {
        return ship.plotBlocks().stream().filter(p -> CannonBlock.isMaster(level.getBlockState(p)))
                .findFirst().orElseThrow(() -> new GameTestAssertException("the cannon is not in the plot"));
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

    /**
     * CAN2: the elevation step reaches clients, which draw the barrel at it: the update tag (chunk load) carries it, a
     * freshly loaded client copy shows the same step, an aim sends a block entity data packet, and an unset step stays
     * unset (the client falls back to the level step like the server).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void elevationReachesAFreshClientCopyThroughTheUpdateTag(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var registries = level.registryAccess();
        BlockPos pos = cannon(h, new BlockPos(4, 1, 4), Direction.EAST);
        CannonBlockEntity be = (CannonBlockEntity) level.getBlockEntity(pos);
        h.assertTrue(be != null, "no block entity");

        CannonBlockEntity unset = new CannonBlockEntity(pos, be.getBlockState());
        unset.loadWithComponents(be.getUpdateTag(registries), registries);
        h.assertTrue(unset.elevationStep() == be.elevationStep() && unset.elevationStep() == CannonConfig.levelStep(),
                "an unaimed cannon syncs as level, got step " + unset.elevationStep());

        CannonService.aim(level, pos, true);
        CannonService.aim(level, pos, true);
        int step = be.elevationStep();
        h.assertTrue(step == CannonConfig.levelStep() + 2, "two steps up, got " + step);
        h.assertTrue(be.getUpdatePacket() != null, "the cannon sends a block entity data packet");
        var tag = be.getUpdateTag(registries);
        h.assertTrue(tag.contains("elevation") && !tag.contains("reload_until"), "the update tag carries only the elevation: " + tag);
        CannonBlockEntity client = new CannonBlockEntity(pos, be.getBlockState());
        client.loadWithComponents(tag, registries);
        h.assertTrue(client.elevationStep() == step, "the client copy shows step " + client.elevationStep() + ", not " + step);
        h.assertTrue(CannonConfig.elevationDegrees(client.elevationStep()) == 10.0, "the client draws 10°");
        h.succeed();
    }

    // ------------------------------------------------------------------ the two blocks (P2)

    /**
     * Placing the cannon item (player looking east) makes the clicked block the master and puts the rear behind it;
     * placement is refused when the rear is blocked or has nothing to stand on, and a refused placement keeps the item.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void placingOccupiesBothBlocksAndRefusesWhenTheRearIsBlocked(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int z : new int[]{1, 4, 7}) {
            h.setBlock(new BlockPos(4, 0, z), Blocks.STONE);
            if (z != 1) h.setBlock(new BlockPos(3, 0, z), Blocks.STONE); // z = 1: nothing under the rear
        }
        h.setBlock(new BlockPos(3, 1, 7), Blocks.OAK_PLANKS); // z = 7: the rear's place is taken
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(-90); // looking east
        ItemStack stack = new ItemStack(CannonContent.CANNON.get(), 3);

        h.assertTrue(place(h, player, stack, new BlockPos(4, 1, 4)), "placing the cannon was refused");
        BlockState master = level.getBlockState(h.absolutePos(new BlockPos(4, 1, 4)));
        BlockState rear = level.getBlockState(h.absolutePos(new BlockPos(3, 1, 4)));
        h.assertTrue(CannonBlock.isMaster(master) && master.getValue(CannonBlock.FACING) == Direction.EAST,
                "no master facing east at the clicked block: " + master);
        h.assertTrue(rear.is(CannonContent.CANNON.get()) && rear.getValue(CannonBlock.PART) == CannonPart.REAR
                && rear.getValue(CannonBlock.FACING) == Direction.EAST, "no rear half behind the master: " + rear);
        h.assertTrue(level.getBlockEntity(h.absolutePos(new BlockPos(4, 1, 4))) instanceof CannonBlockEntity,
                "the master has no block entity");
        h.assertTrue(level.getBlockEntity(h.absolutePos(new BlockPos(3, 1, 4))) == null, "the rear has a block entity");
        h.assertTrue(stack.getCount() == 2, "placing took " + (3 - stack.getCount()) + " items");

        h.assertFalse(place(h, player, stack, new BlockPos(4, 1, 7)), "placed with the rear blocked");
        h.assertBlockPresent(Blocks.AIR, new BlockPos(4, 1, 7));
        h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(3, 1, 7));
        h.assertFalse(place(h, player, stack, new BlockPos(4, 1, 1)), "placed with nothing under the rear");
        h.assertBlockPresent(Blocks.AIR, new BlockPos(4, 1, 1));
        h.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 1));
        h.assertTrue(stack.getCount() == 2, "a refused placement used the item");
        h.succeed();
    }

    /** Breaking the rear removes the master and breaking the master removes the rear; each cannon drops one item. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void breakingEitherHalfRemovesBothAndDropsOneCannon(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos a = cannon(h, new BlockPos(4, 1, 2), Direction.EAST);
        BlockPos b = cannon(h, new BlockPos(4, 1, 6), Direction.NORTH);
        level.destroyBlock(CannonRules.rearOf(a, Direction.EAST), true);
        h.assertTrue(level.getBlockState(a).isAir(), "the master stayed after its rear was broken");
        level.destroyBlock(b, true);
        h.assertTrue(level.getBlockState(CannonRules.rearOf(b, Direction.NORTH)).isAir(), "the rear stayed after its master was broken");
        int cannons = h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                .filter(s -> s.is(CannonContent.CANNON.get().asItem())).mapToInt(ItemStack::getCount).sum();
        h.assertTrue(cannons == 2, "expected one cannon item per cannon, got " + cannons);
        h.getEntities(EntityType.ITEM).forEach(ItemEntity::discard);
        h.succeed();
    }

    /** Using, loading, aiming and firing at the rear half act on the master: its load, elevation and muzzle. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void loadingAimingAndFiringFromTheRearActOnTheMaster(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos master = cannon(h, new BlockPos(2, 1, 4), Direction.EAST);
        BlockPos rearRel = CannonRules.rearOf(new BlockPos(2, 1, 4), Direction.EAST);
        BlockPos rear = h.absolutePos(rearRel);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GUNPOWDER, 2));
        h.useBlock(rearRel, player); // the block's own use, at the rear
        h.assertTrue(load(level, master) == CannonLoad.POWDER, "powder used at the rear did not reach the master");
        h.assertTrue(level.getBlockState(rear).getValue(CannonBlock.LOAD) == CannonLoad.EMPTY, "the rear took the powder");
        h.assertTrue(player.getMainHandItem().getCount() == 1, "the powder was not taken");
        h.assertTrue(CannonService.load(level, rear, player, new ItemStack(CombatContent.CANNONBALL.get())).outcome()
                == CannonService.Outcome.BALL_IN && load(level, master) == CannonLoad.LOADED, "the ball did not reach the master");
        CannonBlockEntity be = (CannonBlockEntity) level.getBlockEntity(master);
        int before = be.elevationStep();
        CannonService.aim(level, rear, true);
        h.assertTrue(be.elevationStep() == before + 1, "aiming at the rear did not raise the master's barrel");
        CannonService.aim(level, rear, false);

        CannonService.Use use = CannonService.fire(level, rear, player);
        h.assertTrue(use.outcome() == CannonService.Outcome.FIRED && use.ball() != null, "no shot from the rear: " + use.outcome());
        Vec3 at = use.ball().position();
        use.ball().discard();
        assertNear(h, at, CannonRules.pivot(master.getX(), master.getY(), master.getZ()).add(CannonRules.MUZZLE_LENGTH, 0, 0),
                1e-6, "muzzle of a shot fired at the rear");
        h.assertTrue(load(level, master) == CannonLoad.EMPTY, "the master is still loaded");
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
        BlockPos plot = masterInPlot(level, ship);
        h.assertTrue(level.getBlockState(CannonRules.rearOf(plot, Direction.EAST)).is(CannonContent.CANNON.get()),
                "the rear half did not assemble with the master");
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        loadFully(h, plot);
        h.runAfterDelay(20, () -> {
            ship.addVelocity(new Vector3d(3, 0, 0), new Vector3d());
            Vector3d before = ship.linearVelocity();
            Vec3 pivot = ship.toWorld(CannonRules.pivot(plot.getX(), plot.getY(), plot.getZ()));
            Vec3 east = CannonService.rotate(ship.orientation(), new Vec3(1, 0, 0));
            Vec3 expected = pivot.add(east.scale(CannonRules.MUZZLE_LENGTH));

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
        BlockPos plot = masterInPlot(level, f.ship());
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

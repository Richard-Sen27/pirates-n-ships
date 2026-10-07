package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/**
 * Cannon follow-ups in a real server (docs/design.md §8.2, Q2): block damage respects the {@code mobGriefing} game rule,
 * a broken gun gives back its load, destroyed blocks drop their items, and a glancing ball breaks fewer blocks or
 * bounces off. Shots are spawned as {@link CannonballEntity} directly with a chosen velocity (as a cannon fires them),
 * so the angle of the hit is exact. Spawn protection can't be set up in a test (the spawn can't be moved under the
 * test structures and the GameTest server is not dedicated); its rule is unit tested in {@code CannonRulesTest}.
 */
public final class CannonFollowUpGameTests {

    private static final String NO_GRIEFING_BATCH = "pirates_n_ships_config_cannon_no_mob_griefing";
    private static final String NO_DROPS_BATCH = "pirates_n_ships_config_cannon_no_drops";
    private static final String GLANCING_BATCH = "pirates_n_ships_config_cannon_glancing";

    private CannonFollowUpGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CannonFollowUpGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    /** A ball at the relative point {@code from} with {@code velocity} (blocks per tick), owned by nobody. */
    private static CannonballEntity shoot(GameTestHelper h, Vec3 from, Vec3 velocity) {
        return shootWorld(h.getLevel(), h.absoluteVec(from), velocity);
    }

    private static CannonballEntity shootWorld(ServerLevel level, Vec3 from, Vec3 velocity) {
        CannonballEntity ball = new CannonballEntity(level, from, velocity, CannonConfig.entityDamage(), 40);
        level.addFreshEntity(ball);
        return ball;
    }

    /** Items of {@code item} lying in the test area. */
    private static int items(GameTestHelper h, Item item) {
        return h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).filter(s -> s.is(item))
                .mapToInt(ItemStack::getCount).sum();
    }

    /** A plank wall from x = 1 to 7 at y 1..2 and z 4 .. 4 + depth − 1, with a stone backstop at x = 8. */
    private static void plankWall(GameTestHelper h, int depth) {
        for (int x = 1; x <= 7; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 4; z < 4 + depth; z++) h.setBlock(new BlockPos(x, y, z), Blocks.OAK_PLANKS);
            }
        }
        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z < 4 + depth; z++) h.setBlock(new BlockPos(8, y, z), Blocks.STONE);
        }
    }

    private static int planksLeft(GameTestHelper h, int depth) {
        int n = 0;
        for (int x = 1; x <= 7; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 4; z < 4 + depth; z++) {
                    if (h.getLevel().getBlockState(h.absolutePos(new BlockPos(x, y, z))).is(Blocks.OAK_PLANKS)) n++;
                }
            }
        }
        return n;
    }

    /** A plank at x = 4, y 1, z 4 with stone behind it, hit straight on from the west. */
    private static void straightShotAtAPlank(GameTestHelper h) {
        h.setBlock(new BlockPos(4, 1, 4), Blocks.OAK_PLANKS);
        h.setBlock(new BlockPos(5, 1, 4), Blocks.STONE);
        shoot(h, new Vec3(1.5, 1.5, 4.5), new Vec3(2.0, 0, 0));
    }

    // ------------------------------------------------------------------ world rules

    /** With the {@code mobGriefing} game rule off a ball breaks nothing (the rule is restored afterwards). */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60, batch = NO_GRIEFING_BATCH)
    public static void withoutMobGriefingThePlankSurvives(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        GameRules.BooleanValue rule = level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean before = rule.get();
        rule.set(false, level.getServer());
        try {
            straightShotAtAPlank(h);
        } catch (RuntimeException e) {
            rule.set(before, level.getServer());
            throw e;
        }
        h.runAfterDelay(10, () -> {
            try {
                h.assertTrue(h.getEntities(CannonContent.CANNONBALL.get()).isEmpty(), "the ball did not stop at the plank");
                h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(4, 1, 4));
                h.succeed();
            } finally {
                rule.set(before, level.getServer());
            }
        });
    }

    // ------------------------------------------------------------------ destroyed blocks drop

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void aPlankShotOutOnLandDropsAPlank(GameTestHelper h) {
        straightShotAtAPlank(h);
        h.succeedWhen(() -> {
            h.assertBlockPresent(Blocks.AIR, new BlockPos(4, 1, 4));
            h.assertTrue(items(h, Items.OAK_PLANKS) == 1, "expected one plank item, got " + items(h, Items.OAK_PLANKS));
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60, batch = NO_DROPS_BATCH)
    public static void withoutDropsAPlankShotOutLeavesNothing(GameTestHelper h) {
        ConfigOverrides.during(h, CannonConfig.DESTROYED_BLOCKS_DROP, false);
        straightShotAtAPlank(h);
        h.runAfterDelay(10, () -> {
            h.assertBlockPresent(Blocks.AIR, new BlockPos(4, 1, 4));
            h.assertTrue(items(h, Items.OAK_PLANKS) == 0, "the plank dropped with destroyed_blocks_drop off");
            h.succeed();
        });
    }

    /**
     * A plank shot out of a floating hull (the west wall at deck height, above the water) drops a plank item in the
     * world next to the hole, not in the plot.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void aPlankShotOutOfAHullDropsAPlankBesideTheShip(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        Fixture f = DryHullGameTests.assemble(h, DryHullGameTests.hull(h, 9, false));
        ServerLevel level = h.getLevel();
        BlockPos wall = f.hold(-2, -1, 0);
        Vec3[] target = new Vec3[1];
        h.runAfterDelay(20, () -> {
            h.assertTrue(level.getBlockState(wall).is(Blocks.OAK_PLANKS), "no wall at " + wall);
            target[0] = f.ship().toWorld(Vec3.atCenterOf(wall));
            shootWorld(level, target[0].add(-2.0, 0, 0), new Vec3(2.0, 0, 0));
        });
        h.runAfterDelay(21, () -> h.succeedWhen(() -> {
            h.assertTrue(level.getBlockState(wall).isAir(), "the hull plank is still there");
            ItemEntity plank = h.getEntities(EntityType.ITEM).stream().filter(e -> e.getItem().is(Items.OAK_PLANKS))
                    .findFirst().orElse(null);
            h.assertTrue(plank != null, "no plank item in the test area");
            h.assertTrue(plank.position().distanceTo(target[0]) < 4.0,
                    "the plank lies at " + plank.position() + ", not near the hole at " + target[0]);
            h.getEntities(EntityType.ITEM).forEach(ItemEntity::discard);
        }));
    }

    // ------------------------------------------------------------------ a broken gun returns its load

    private static void loadCannon(GameTestHelper h, BlockPos master) {
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true;
        CannonService.load(h.getLevel(), master, creative, new ItemStack(Items.GUNPOWDER));
        CannonService.load(h.getLevel(), master, creative, new ItemStack(CombatContent.CANNONBALL.get()));
        h.assertTrue(h.getLevel().getBlockState(master).getValue(CannonBlock.LOAD) == CannonLoad.LOADED, "the cannon is not loaded");
    }

    /** A loaded cannon broken at its rear (and one at its master) drops the cannon, the powder and the ball, once each. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aBrokenLoadedCannonDropsItsPowderAndBall(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos a = CannonGameTests.cannon(h, new BlockPos(4, 1, 2), Direction.EAST);
        BlockPos b = CannonGameTests.cannon(h, new BlockPos(4, 1, 6), Direction.EAST);
        loadCannon(h, a);
        loadCannon(h, b);
        level.destroyBlock(CannonRules.rearOf(a, Direction.EAST), true);
        level.destroyBlock(b, true);
        h.assertTrue(level.getBlockState(a).isAir() && level.getBlockState(b).isAir(), "a cannon is still there");
        int cannons = items(h, CannonContent.CANNON.get().asItem());
        int powder = items(h, Items.GUNPOWDER);
        int balls = items(h, CombatContent.CANNONBALL.get());
        h.getEntities(EntityType.ITEM).forEach(ItemEntity::discard);
        h.assertTrue(cannons == 2 && powder == 2 && balls == 2,
                "expected 2 cannons, 2 gunpowder, 2 cannonballs, got " + cannons + ", " + powder + ", " + balls);
        h.succeed();
    }

    /** An empty cannon drops only itself; a powdered one adds the powder only. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void anEmptyCannonDropsOnlyItself(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos a = CannonGameTests.cannon(h, new BlockPos(4, 1, 2), Direction.EAST);
        BlockPos b = CannonGameTests.cannon(h, new BlockPos(4, 1, 6), Direction.EAST);
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true;
        CannonService.load(level, b, creative, new ItemStack(Items.GUNPOWDER));
        level.destroyBlock(a, true);
        int cannons = items(h, CannonContent.CANNON.get().asItem());
        int other = h.getEntities(EntityType.ITEM).size() - h.getEntities(EntityType.ITEM).stream()
                .filter(e -> e.getItem().is(CannonContent.CANNON.get().asItem())).toList().size();
        h.assertTrue(cannons == 1 && other == 0, "an empty cannon dropped " + cannons + " cannons and " + other + " other items");
        level.destroyBlock(CannonRules.rearOf(b, Direction.EAST), true);
        int powder = items(h, Items.GUNPOWDER);
        int balls = items(h, CombatContent.CANNONBALL.get());
        h.getEntities(EntityType.ITEM).forEach(ItemEntity::discard);
        h.assertTrue(powder == 1 && balls == 0, "a powdered cannon dropped " + powder + " powder and " + balls + " balls");
        h.succeed();
    }

    /** In creative, breaking the rear of a loaded cannon drops nothing (P2's creative rule, now with the load). */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aLoadedCannonBrokenInCreativeDropsNothing(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos master = CannonGameTests.cannon(h, new BlockPos(4, 1, 4), Direction.EAST);
        loadCannon(h, master);
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        BlockPos rear = CannonRules.rearOf(master, Direction.EAST);
        BlockState state = level.getBlockState(rear);
        // what ServerPlayerGameMode.destroyBlock does for a creative player: playerWillDestroy, then remove, no drops
        state.getBlock().playerWillDestroy(level, rear, state, creative);
        level.removeBlock(rear, false);
        h.assertTrue(level.getBlockState(master).isAir(), "the master stayed");
        int dropped = h.getEntities(EntityType.ITEM).size();
        h.getEntities(EntityType.ITEM).forEach(ItemEntity::discard);
        h.assertTrue(dropped == 0, "a creative break dropped " + dropped + " items");
        h.succeed();
    }

    /** A loaded swivel gun drops itself, the powder and the shot; an empty one only itself. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aBrokenLoadedSwivelDropsItsPowderAndShot(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        h.setBlock(new BlockPos(2, 2, 2), CannonContent.SWIVEL_GUN.get());
        h.setBlock(new BlockPos(6, 1, 6), Blocks.STONE);
        h.setBlock(new BlockPos(6, 2, 6), CannonContent.SWIVEL_GUN.get());
        BlockPos loaded = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos empty = h.absolutePos(new BlockPos(6, 2, 6));
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true;
        SwivelService.load(level, loaded, creative, new ItemStack(Items.GUNPOWDER));
        SwivelService.load(level, loaded, creative, new ItemStack(SwivelService.ammoItem(), 64));
        h.assertTrue(level.getBlockState(loaded).getValue(SwivelGunBlock.LOAD) == CannonLoad.LOADED, "the swivel is not loaded");
        Item ammo = SwivelService.ammoItem();
        int ammoCount = CannonConfig.SWIVEL_AMMO_COUNT.get();

        level.destroyBlock(empty, true);
        int swivels = items(h, CannonContent.SWIVEL_GUN.get().asItem());
        h.assertTrue(swivels == 1 && h.getEntities(EntityType.ITEM).size() == 1,
                "an empty swivel dropped " + h.getEntities(EntityType.ITEM).size() + " item stacks");
        level.destroyBlock(loaded, true);
        swivels = items(h, CannonContent.SWIVEL_GUN.get().asItem());
        int powder = items(h, Items.GUNPOWDER);
        int shot = items(h, ammo);
        h.getEntities(EntityType.ITEM).forEach(ItemEntity::discard);
        h.assertTrue(swivels == 2 && powder == 1 && shot == ammoCount,
                "expected 2 swivels, 1 gunpowder, " + ammoCount + " shot, got " + swivels + ", " + powder + ", " + shot);
        h.succeed();
    }

    // ------------------------------------------------------------------ glancing hits

    /** A ball grazing a plank wall at about 81.5° bounces off: no plank breaks and it flies on away from the wall. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 40)
    public static void aGrazingBallBouncesOffWithoutBreakingAnything(GameTestHelper h) {
        plankWall(h, 1);
        int before = planksLeft(h, 1);
        CannonballEntity ball = shoot(h, new Vec3(2.0, 1.5, 3.9), new Vec3(1.0, 0, 0.15));
        h.succeedWhen(() -> {
            h.assertTrue(ball.getDeltaMovement().z < 0, "the ball has not bounced off the wall yet: " + ball.getDeltaMovement());
            h.assertTrue(planksLeft(h, 1) == before, "a grazing ball broke " + (before - planksLeft(h, 1)) + " planks");
        });
    }

    /** With three blocks per hit, a ball at 65° to the face (cos ≈ 0.42) breaks one plank, not three. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60, batch = GLANCING_BATCH)
    public static void aBallAtAnAngleBreaksFewerBlocks(GameTestHelper h) {
        ConfigOverrides.during(h, CannonConfig.BLOCKS_PER_HIT, 3);
        plankWall(h, 3);
        int before = planksLeft(h, 3);
        double a = Math.toRadians(65);
        shoot(h, new Vec3(2.0, 1.5, 3.5), new Vec3(Math.sin(a), 0, Math.cos(a)));
        h.succeedWhen(() -> {
            h.assertTrue(h.getEntities(CannonContent.CANNONBALL.get()).isEmpty(), "the ball is still flying");
            h.assertTrue(before - planksLeft(h, 3) == 1, "expected one broken plank, got " + (before - planksLeft(h, 3)));
            h.getEntities(EntityType.ITEM).forEach(ItemEntity::discard);
        });
    }

    /**
     * A ball grazing the west wall of a floating hull at about 83° bounces off it (the face normal is turned from the
     * plot into the world): the plank stays and the ball flies on westwards, away from the hull.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void aBallGrazingAHullBouncesOff(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        Fixture f = DryHullGameTests.assemble(h, DryHullGameTests.hull(h, 9, false));
        ServerLevel level = h.getLevel();
        BlockPos wall = f.hold(-2, -1, 0);
        CannonballEntity[] ball = new CannonballEntity[1];
        h.runAfterDelay(20, () -> {
            h.assertTrue(level.getBlockState(wall).is(Blocks.OAK_PLANKS), "no wall at " + wall);
            Vec3 target = f.ship().toWorld(Vec3.atCenterOf(wall));
            ball[0] = shootWorld(level, target.add(-0.8, 0, -2.5), new Vec3(0.12, 0, 1.0));
        });
        h.runAfterDelay(21, () -> h.succeedWhen(() -> {
            h.assertTrue(ball[0].getDeltaMovement().x < 0, "the ball has not bounced off the hull: " + ball[0].getDeltaMovement());
            h.assertTrue(level.getBlockState(wall).is(Blocks.OAK_PLANKS), "the grazed hull plank broke");
            ball[0].discard();
        }));
    }
}

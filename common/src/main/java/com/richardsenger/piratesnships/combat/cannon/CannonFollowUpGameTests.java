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
}

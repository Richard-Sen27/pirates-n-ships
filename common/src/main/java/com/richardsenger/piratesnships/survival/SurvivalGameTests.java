package com.richardsenger.piratesnships.survival;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import com.richardsenger.piratesnships.survival.cold.ColdWater;
import com.richardsenger.piratesnships.survival.cold.ColdWaterRules;
import com.richardsenger.piratesnships.survival.swim.SwimHungerRules;
import com.richardsenger.piratesnships.trade.content.TradeContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Cold water and swimming hunger in a running world (docs/design.md §14).
 *
 * <p>Cold-water tests turn their own 24×24 area into {@code cold_ocean} (in {@code #pirates_n_ships:cold_water}; not
 * {@code frozen_ocean}, whose surface water would freeze to ice during the test) through
 * {@link SurvivalTestSupport#setBiome}, which restores the old biome when the test ends. They run in their own batch so
 * no other module's test stands next to a cold area. The subject wades in a 1-block-deep pool in the middle of the area
 * (in water, head above it, so it never drowns).
 *
 * <p>Swimming hunger: the mock player is a plain {@code Player}, for which vanilla charges no movement exhaustion at all
 * (that is {@code ServerPlayer.checkMovementStatistics}), so all exhaustion it gains is ours. The test moves it through
 * water, samples each tick's movement exactly as our hook sees it, and compares the gain with (multiplier − 1) ×
 * vanilla's formula ({@link SwimHungerRules#vanillaExhaustion}, pinned to vanilla's constants by the JUnit test).
 */
public final class SurvivalGameTests {

    private static final String COLD = "pirates_n_ships_survival_cold";
    /** The middle of the 24×24 area, standing on the pool floor (y = 1) in 1-deep water (y = 2). */
    private static final Vec3 POOL = new Vec3(12.5, 2, 12.5);
    /** Exempt subjects must stay unfrozen this long: past the 140 ticks a freezing subject needs to fill the meter. */
    private static final int EXEMPT_TICKS = 160;

    private SurvivalGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(SurvivalGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** A stone floor under the whole area and a 7×7 pool (walls of stone) of 1-deep water in its middle. */
    private static void pool(GameTestHelper h) {
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 8; x <= 16; x++) {
            for (int z = 8; z <= 16; z++) {
                boolean wall = x == 8 || x == 16 || z == 8 || z == 16;
                h.setBlock(new BlockPos(x, 2, z), wall ? Blocks.STONE : Blocks.WATER);
            }
        }
    }

    private static void coldPool(GameTestHelper h) {
        SurvivalTestSupport.setBiome(h, Biomes.COLD_OCEAN);
        pool(h);
    }

    private static Player wader(GameTestHelper h, GameType mode) {
        return SurvivalTestSupport.playerInLevel(h, POOL, mode);
    }

    private static BlockPos at(Player p) {
        return p.blockPosition();
    }

    /** The subject is where the test needs it: in the pool, whose biome is cold water. */
    private static void assertInColdWater(GameTestHelper h, Player p) {
        h.assertTrue(ColdWater.isColdWater(h.getLevel(), at(p)), "the pool is not cold water: " + h.getLevel().getBiome(at(p)).getRegisteredName());
    }

    /** Runs the exemption check: after {@link #EXEMPT_TICKS} the subject's meter is still empty. */
    private static void staysUnfrozen(GameTestHelper h, Player p, Consumer<Player> precondition) {
        h.runAfterDelay(20, () -> precondition.accept(p));
        h.onEachTick(() -> h.assertValueEqual(p.getTicksFrozen(), 0, "ticks frozen"));
        h.runAfterDelay(EXEMPT_TICKS, h::succeed);
    }

    // ------------------------------------------------------------------ cold water

    /**
     * A survival player wading in cold water fills the meter like powder snow (full after about 140 ticks: frost overlay
     * and slowdown come with it) and then takes freezing damage on vanilla's 40-tick beat.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 260, batch = COLD)
    public static void coldWaterFreezesAndHurts(GameTestHelper h) {
        coldPool(h);
        Player p = wader(h, GameType.SURVIVAL);
        long[] hurtAt = {-1};
        h.runAfterDelay(20, () -> {
            assertInColdWater(h, p);
            h.assertTrue(p.isInWater(), "the player is not in the water");
            h.assertTrue(p.getTicksFrozen() >= 15, "the meter does not fill: " + p.getTicksFrozen());
        });
        h.onEachTick(() -> {
            DamageSource last = p.getLastDamageSource();
            if (hurtAt[0] < 0 && last != null && last.is(DamageTypeTags.IS_FREEZING)) hurtAt[0] = h.getTick();
        });
        h.runAfterDelay(100, () -> h.assertTrue(p.getTicksFrozen() >= 90 && !p.isFullyFrozen(),
                "after 100 ticks the meter should be about 100 of 140: " + p.getTicksFrozen()));
        h.succeedWhen(() -> {
            h.assertTrue(hurtAt[0] >= 0, "no freezing damage yet (meter " + p.getTicksFrozen() + ")");
            h.assertTrue(hurtAt[0] >= 135 && hurtAt[0] <= 200, "freezing damage at tick " + hurtAt[0] + ", expected 140 to 180");
            h.assertTrue(p.getPercentFrozen() >= 1.0f, "hurt while not fully frozen: " + p.getPercentFrozen());
        });
    }

    /** Out of the cold water the meter thaws at vanilla's rate (2 per tick). */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = COLD)
    public static void meterThawsOutOfColdWater(GameTestHelper h) {
        coldPool(h);
        Player p = wader(h, GameType.SURVIVAL);
        int[] before = {0};
        h.runAfterDelay(80, () -> {
            before[0] = p.getTicksFrozen();
            h.assertTrue(before[0] >= 60, "the meter did not fill: " + before[0]);
            Vec3 dry = h.absoluteVec(new Vec3(4.5, 2, 4.5));
            p.teleportTo(dry.x, dry.y, dry.z);
        });
        h.runAfterDelay(100, () -> {
            h.assertTrue(!p.isInWater(), "still in the water");
            h.assertTrue(p.getTicksFrozen() < before[0] - 30, "the meter does not thaw: " + before[0] + " -> " + p.getTicksFrozen());
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = COLD)
    public static void boatProtects(GameTestHelper h) {
        coldPool(h);
        Vec3 at = h.absoluteVec(POOL);
        Boat boat = new Boat(h.getLevel(), at.x, at.y, at.z);
        h.getLevel().addFreshEntity(boat);
        SurvivalTestSupport.discardAtEnd(h, boat);
        Player p = wader(h, GameType.SURVIVAL);
        h.assertTrue(p.startRiding(boat, true), "the player could not board the boat");
        staysUnfrozen(h, p, q -> {
            assertInColdWater(h, q);
            h.assertTrue(q.getVehicle() == boat, "the player left the boat");
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = COLD)
    public static void leatherArmourProtects(GameTestHelper h) {
        coldPool(h);
        Player p = wader(h, GameType.SURVIVAL);
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        staysUnfrozen(h, p, q -> {
            assertInColdWater(h, q);
            h.assertTrue(q.isInWater(), "the player is not in the water");
            h.assertValueEqual(ColdWaterRules.judge(true, ColdWater.subject(h.getLevel(), q, at(q))),
                    ColdWaterRules.Outcome.CANNOT_FREEZE, "outcome");
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = COLD)
    public static void warmEffectProtects(GameTestHelper h) {
        coldPool(h);
        Player p = wader(h, GameType.SURVIVAL);
        p.addEffect(new MobEffectInstance(SurvivalContent.WARM.holder(), 1200));
        staysUnfrozen(h, p, q -> {
            assertInColdWater(h, q);
            h.assertTrue(q.isInWater(), "the player is not in the water");
            h.assertValueEqual(ColdWaterRules.judge(true, ColdWater.subject(h.getLevel(), q, at(q))),
                    ColdWaterRules.Outcome.WARM, "outcome");
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = COLD)
    public static void creativePlayersDoNotFreeze(GameTestHelper h) {
        coldPool(h);
        Player p = wader(h, GameType.CREATIVE);
        staysUnfrozen(h, p, q -> {
            assertInColdWater(h, q);
            h.assertTrue(q.isInWater(), "the player is not in the water");
        });
    }

    /** Warm ocean water (not in the tag) does nothing. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = COLD)
    public static void warmWaterDoesNotFreeze(GameTestHelper h) {
        SurvivalTestSupport.setBiome(h, Biomes.WARM_OCEAN);
        pool(h);
        Player p = wader(h, GameType.SURVIVAL);
        staysUnfrozen(h, p, q -> {
            h.assertTrue(h.getLevel().getBiome(at(q)).is(Biomes.WARM_OCEAN), "the pool is not warm ocean");
            h.assertTrue(q.isInWater(), "the player is not in the water");
        });
    }

    /**
     * A player on the deck of an assembled ship over cold water is tracked by the ship and never freezes, whatever the
     * game thinks of the water around the hull.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 320, batch = COLD)
    public static void shipDeckProtects(GameTestHelper h) {
        SurvivalTestSupport.setBiome(h, Biomes.COLD_OCEAN);
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 23 || z == 0 || z == 23;
                for (int y = 2; y <= 8; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : y <= 7 ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
        int x0 = 10, z0 = 10;
        for (int x = x0; x <= x0 + 4; x++) {
            for (int z = z0; z <= z0 + 4; z++) {
                for (int y = 5; y <= 8; y++) {
                    boolean shell = y == 5 || y == 8 || x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
                    h.setBlock(new BlockPos(x, y, z), shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
        BlockPos helm = new BlockPos(x0 + 2, 9, z0);
        h.setBlock(helm, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        AssemblyResult r = ShipTestCleanup.assemble(h, helm);
        h.assertTrue(r.shipId() != null, "assembly failed: " + r);
        ShipBody ship = SableShips.byId(h.getLevel(), r.shipId());
        h.assertTrue(ship != null, "no ship after assembly");
        BlockPos helmPlot = ship.plotBlocks().stream().filter(p -> h.getLevel().getBlockState(p).is(AssemblyContent.HELM.get()))
                .findFirst().orElseThrow(() -> new AssertionError("no helm on the ship"));
        Player[] player = new Player[1];
        // a freshly assembled floating ship rises about 1.4 blocks first: board once it has settled
        h.runAfterDelay(60, () -> {
            Vec3 deck = ship.toWorld(Vec3.atBottomCenterOf(helmPlot.offset(0, 0, 2)));
            Player p = h.makeMockPlayer(GameType.SURVIVAL);
            p.moveTo(deck.x, deck.y + 0.05, deck.z, 0f, 0f);
            h.getLevel().addFreshEntity(p);
            SurvivalTestSupport.discardAtEnd(h, p);
            player[0] = p;
        });
        h.runAfterDelay(61, () -> h.onEachTick(() -> h.assertValueEqual(player[0].getTicksFrozen(), 0, "ticks frozen")));
        h.runAfterDelay(80, () -> h.assertTrue(ColdWater.isColdWater(h.getLevel(), player[0].blockPosition()),
                "the ship is not over cold water"));
        h.runAfterDelay(60 + EXEMPT_TICKS, () -> {
            Player p = player[0];
            ShipBody on = ShipEntities.standingOrRiding(p);
            h.assertTrue(on != null && on.id().equals(ship.id()), "the player is not tracked on the ship");
            h.assertTrue(ColdWaterRules.judge(true, ColdWater.subject(h.getLevel(), p, p.blockPosition())) != ColdWaterRules.Outcome.FREEZES,
                    "the rule would freeze the player on deck");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ warming drinks

    /** Drinking rum grants the warm effect for warm_effect_ticks; another drink (honey) does not. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 120)
    public static void rumGrantsWarm(GameTestHelper h) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        }
        Player rum = SurvivalTestSupport.playerInLevel(h, new Vec3(2.5, 2, 4.5), GameType.SURVIVAL);
        Player honey = SurvivalTestSupport.playerInLevel(h, new Vec3(6.5, 2, 4.5), GameType.SURVIVAL);
        rum.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TradeContent.RUM.get()));
        honey.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HONEY_BOTTLE));
        h.assertTrue(rum.getMainHandItem().is(SurvivalContent.WARMING), "rum is not a warming drink");
        rum.getMainHandItem().use(h.getLevel(), rum, InteractionHand.MAIN_HAND);
        honey.getMainHandItem().use(h.getLevel(), honey, InteractionHand.MAIN_HAND);
        h.assertTrue(rum.isUsingItem() && honey.isUsingItem(), "the players do not drink");
        h.succeedWhen(() -> {
            h.assertTrue(!rum.isUsingItem() && !honey.isUsingItem(), "still drinking");
            MobEffectInstance warm = rum.getEffect(SurvivalContent.WARM.holder());
            h.assertTrue(warm != null, "rum did not warm");
            int full = SurvivalConfig.WARM_EFFECT_TICKS.get();
            h.assertTrue(warm.getDuration() > full - 100 && warm.getDuration() <= full, "warm lasts " + warm.getDuration());
            h.assertTrue(!honey.hasEffect(SurvivalContent.WARM.holder()), "honey warmed");
            h.assertTrue(rum.getMainHandItem().is(Items.GLASS_BOTTLE), "the rum bottle was not returned");
        });
    }

    // ------------------------------------------------------------------ config

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = "pirates_n_ships_config_survival_cold_off")
    public static void disabledColdWaterDoesNotFreeze(GameTestHelper h) {
        ConfigOverrides.during(h, SurvivalConfig.COLD_WATER_ENABLED, false);
        coldPool(h);
        Player p = wader(h, GameType.SURVIVAL);
        staysUnfrozen(h, p, q -> {
            assertInColdWater(h, q);
            h.assertTrue(q.isInWater(), "the player is not in the water");
        });
    }

    // ------------------------------------------------------------------ swimming hunger

    /**
     * Wading 0.05 blocks per tick for 100 ticks: the player gains (multiplier − 1) × vanilla's exhaustion for the same
     * movement, i.e. half of it with the default 1.5 (so a real player pays 1.5 × vanilla).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 140)
    public static void swimmingCostsExtraHunger(GameTestHelper h) {
        swimTest(h, SwimHungerRules.DEFAULT_MULTIPLIER);
    }

    /** With the multiplier at 1 the same movement costs nothing extra. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 140, batch = "pirates_n_ships_config_survival_swim_vanilla")
    public static void swimmingAtMultiplierOneIsVanilla(GameTestHelper h) {
        ConfigOverrides.during(h, SurvivalConfig.SWIM_EXHAUSTION_MULTIPLIER, 1.0);
        swimTest(h, 1.0);
    }

    private static void swimTest(GameTestHelper h, double multiplier) {
        h.assertValueEqual(SurvivalConfig.SWIM_EXHAUSTION_MULTIPLIER.get(), multiplier, "multiplier");
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 8 || z == 0 || z == 8;
                h.setBlock(new BlockPos(x, 2, z), wall ? Blocks.STONE : Blocks.WATER);
            }
        }
        Player p = SurvivalTestSupport.playerInLevel(h, new Vec3(1.5, 2, 4.5), GameType.SURVIVAL);
        final int start = 5, end = 105;
        double[] vanilla = {0};
        float[] baseline = {0};
        Vec3[] prev = {null};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t < start || t > end) return;
            Vec3 now = p.position();
            if (prev[0] == null) {
                baseline[0] = p.getFoodData().getExhaustionLevel();
            } else {
                SwimHungerRules.Medium m = SwimHungerRules.medium(p.isSwimming(), p.isEyeInFluid(FluidTags.WATER), p.isInWater());
                h.assertTrue(m != SwimHungerRules.Medium.NONE, "the player left the water at tick " + t);
                vanilla[0] += SwimHungerRules.vanillaExhaustion(m, now.x - prev[0].x, now.y - prev[0].y, now.z - prev[0].z);
            }
            prev[0] = now;
            if (t < end) p.teleportTo(now.x + 0.05, now.y, now.z);
        });
        h.runAfterDelay(end + 2, () -> {
            float gained = p.getFoodData().getExhaustionLevel() - baseline[0];
            double expected = vanilla[0] * (multiplier - 1.0);
            h.assertTrue(vanilla[0] > 0.04, "the player barely moved: vanilla exhaustion " + vanilla[0]);
            h.assertTrue(Math.abs(gained - expected) < 1e-4,
                    "exhaustion gained " + gained + ", expected " + expected + " (vanilla " + vanilla[0] + " x " + (multiplier - 1.0) + ")");
            h.succeed();
        });
    }
}

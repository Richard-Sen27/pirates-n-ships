package com.richardsenger.piratesnships.seachest;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.content.ContentTestSupport;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/**
 * GameTests of paddling a sea chest (SC2, docs/design.md §11 "With a paddle"). The rider's keys are set the way
 * vanilla's passenger input packet sets them on the server ({@code xxa}/{@code zza}), every tick, since the player's
 * tick decays them.
 *
 * <p>All tests of the batch {@value #BATCH} run in the same fixed wind, 3 b/s from the west (drift about 0.9 b/s
 * east), so measurements don't depend on the wind field: paddling heads south (+Z, yaw 0), across the wind.
 */
public final class PaddleGameTests {

    private static final String BATCH = "pirates_n_ships_seachest_paddle";
    private static final double WIND_FROM = 270.0;
    private static final double WIND_SPEED = 3.0;
    /** Ticks after spawning before the chest is in water and settling (isInWater is updated in its first tick). */
    private static final int SETTLE = 10;

    private PaddleGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(PaddleGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** The batch's fixed wind for at least {@code ticks} more ticks (never shortens another test's window). */
    private static void westWind(GameTestHelper h, int ticks) {
        ServerLevel level = h.getLevel();
        String dim = level.dimension().location().toString();
        long until = level.getGameTime() + ticks;
        WindOverride.Entry e = WindOverride.get(dim, level.getGameTime());
        if (e != null && e.untilGameTime() > until) until = e.untilGameTime();
        WindOverride.set(dim, WIND_FROM, WIND_SPEED, until);
    }

    /** An empty floating chest heading south (yaw 0) at the test-relative position. */
    private static SeaChestEntity chestAt(GameTestHelper h, Vec3 relative) {
        ServerLevel level = h.getLevel();
        SeaChestEntity chest = SeaChestEntity.fromItem(level, h.absoluteVec(relative), 0f, new ItemStack(SeaChestContent.ITEM.get()));
        level.addFreshEntity(chest);
        return chest;
    }

    /** A survival player beside {@code chest} facing south, holding a paddle. */
    private static Player paddler(GameTestHelper h, Vec3 relative) {
        Player player = SeaChestTestSupport.playerInLevel(h, relative);
        player.setYRot(0f);
        player.setYHeadRot(0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SeaChestContent.PADDLE.get()));
        return player;
    }

    /** Uses the held paddle on the chest the way vanilla's entity interaction does, and asserts the player sits on it. */
    private static void mount(GameTestHelper h, Player player, SeaChestEntity chest) {
        h.assertTrue(chest.isInWater(), "the chest should float before mounting");
        InteractionResult r = chest.interact(player, InteractionHand.MAIN_HAND);
        h.assertTrue(r.consumesAction(), "using the paddle on the chest should be consumed, got " + r);
        h.assertTrue(player.getVehicle() == chest && chest.rider() == player, "the player should sit on the chest");
    }

    // ------------------------------------------------------------------ riding and motion

    /**
     * 60 ticks of forward input from rest: the speed approaches 1.5 b/s with the water's time constant (retention
     * 0.9 per tick, about 10 ticks), so about 3.8 blocks; the west wind still pushes it east, and it stays afloat.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 160, batch = BATCH)
    public static void paddlesForwardInThreeSeconds(GameTestHelper h) {
        westWind(h, SETTLE + 80);
        ContentTestSupport.assertRecipe(h, "paddle", SeaChestContent.PADDLE.get(), 1);
        SeaChestTestSupport.basin(h, 24, 4);
        SeaChestEntity chest = chestAt(h, new Vec3(12.5, 4.4, 4.5));
        Player player = paddler(h, new Vec3(12.5, 4.0, 3.0));
        Vec3[] start = new Vec3[1];
        h.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    mount(h, player, chest);
                    start[0] = chest.position();
                })
                .thenExecuteFor(60, () -> player.zza = 1.0f)
                .thenExecute(() -> {
                    double forward = chest.getZ() - start[0].z;
                    double east = chest.getX() - start[0].x;
                    double surface = h.absolutePos(new BlockPos(0, 4, 0)).getY() + 8.0 / 9.0;
                    double draft = surface - chest.getY();
                    Constants.LOG.info("[SC2] 60 ticks of forward paddling: forward {} east {} draft {} yaw {}", forward, east, draft, chest.getYRot());
                    h.assertTrue(forward >= 2.0, "paddled only " + forward + " blocks forward in 3 s");
                    h.assertTrue(forward < 5.0, "faster than paddle_speed: " + forward + " blocks in 3 s");
                    h.assertTrue(east > 0.3, "the west wind should still drift it east while paddling, moved " + east);
                    h.assertTrue(chest.isInWater() && Math.abs(draft - SeaChestConfig.DRAFT.get() * chest.getBbHeight()) < 0.2,
                            "the chest should keep floating at its draft with a rider, draft " + draft);
                    h.assertTrue(player.getVehicle() == chest, "still seated");
                })
                .thenSucceed();
    }

    /** One second of left input turns the heading left (yaw down) by paddle_turn_degrees; the rider turns with it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100, batch = BATCH)
    public static void turnsLeftWithLeftInput(GameTestHelper h) {
        westWind(h, SETTLE + 40);
        SeaChestTestSupport.basin(h, 24, 4);
        SeaChestEntity chest = chestAt(h, new Vec3(12.5, 4.4, 12.5));
        Player player = paddler(h, new Vec3(12.5, 4.0, 11.0));
        float[] yaw0 = new float[1];
        h.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    mount(h, player, chest);
                    yaw0[0] = chest.getYRot();
                })
                .thenExecuteFor(20, () -> player.xxa = 1.0f)
                .thenExecute(() -> {
                    float turned = Mth.wrapDegrees(chest.getYRot() - yaw0[0]);
                    double expected = -SeaChestConfig.PADDLE_TURN_DEGREES.get();
                    float riderOff = Mth.wrapDegrees(player.getYRot() - chest.getYRot());
                    Constants.LOG.info("[SC2] 20 ticks of left input: turned {} (expected about {}), rider off heading {}", turned, expected, riderOff);
                    h.assertTrue(Math.abs(turned - expected) < 0.2 * Math.abs(expected) + 1.0, "turned " + turned + ", expected about " + expected);
                    h.assertTrue(Math.abs(riderOff) <= 105.0f, "the rider should face within 105 degrees of the heading, is " + riderOff);
                })
                .thenSucceed();
    }

    /** Without the paddle in hand the keys do nothing: no headway, no turn, no hunger; the wind still drifts it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 160, batch = BATCH)
    public static void withoutThePaddleItOnlyDrifts(GameTestHelper h) {
        westWind(h, SETTLE + 80);
        SeaChestTestSupport.basin(h, 24, 4);
        SeaChestEntity chest = chestAt(h, new Vec3(6.5, 4.4, 8.5));
        Player player = paddler(h, new Vec3(6.5, 4.0, 7.0));
        Vec3[] start = new Vec3[1];
        float[] yaw0 = new float[1];
        float[] food0 = new float[1];
        h.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    mount(h, player, chest);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    start[0] = chest.position();
                    yaw0[0] = chest.getYRot();
                    food0[0] = player.getFoodData().getExhaustionLevel();
                })
                .thenExecuteFor(60, () -> {
                    player.zza = 1.0f;
                    player.xxa = 1.0f;
                })
                .thenExecute(() -> {
                    double forward = chest.getZ() - start[0].z;
                    double east = chest.getX() - start[0].x;
                    float turned = Mth.wrapDegrees(chest.getYRot() - yaw0[0]);
                    float food = player.getFoodData().getExhaustionLevel() - food0[0];
                    Constants.LOG.info("[SC2] 60 ticks of keys without a paddle: forward {} east {} turned {} exhaustion {}", forward, east, turned, food);
                    h.assertTrue(Math.abs(forward) < 0.5, "no paddle, but moved " + forward + " blocks forward");
                    h.assertTrue(Math.abs(turned) < 1.0f, "no paddle, but turned " + turned);
                    h.assertTrue(east > 0.5, "the wind should drift it east, moved " + east);
                    h.assertTrue(food < 1.0e-4f, "drifting costs no hunger, cost " + food);
                })
                .thenSucceed();
    }

    /** Sneaking gets the rider off (vanilla's passenger rule); the chest stays afloat and can be opened again. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 80, batch = BATCH)
    public static void sneakingDismounts(GameTestHelper h) {
        westWind(h, SETTLE + 20);
        SeaChestTestSupport.basin(h, 24, 4);
        SeaChestEntity chest = chestAt(h, new Vec3(18.5, 4.4, 12.5));
        Player player = paddler(h, new Vec3(18.5, 4.0, 11.0));
        h.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    mount(h, player, chest);
                    // Seated, the rider can't open or knock loose the chest they sit on
                    h.assertTrue(chest.interact(player, InteractionHand.OFF_HAND).consumesAction(), "the rider's use is consumed");
                    h.assertFalse(chest.hurt(h.getLevel().damageSources().playerAttack(player), 1.0f), "the rider's hit is ignored");
                    h.assertFalse(chest.isRemoved(), "the chest is still there");
                    player.setShiftKeyDown(true);
                })
                .thenExecuteAfter(3, () -> {
                    h.assertFalse(player.isPassenger(), "sneaking should dismount");
                    h.assertFalse(chest.isVehicle(), "the chest should be free");
                    h.assertFalse(chest.isRemoved(), "the chest stays");
                })
                .thenSucceed();
    }

    /** A worn chest, a placed chest and a chest on dry ground refuse the paddle; a seated chest refuses a second rider. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 80, batch = BATCH)
    public static void wornPlacedOrBeachedChestRefuses(GameTestHelper h) {
        westWind(h, SETTLE + 20);
        SeaChestTestSupport.basin(h, 12, 4);
        // Placed: the paddle has no use on the block
        h.setBlock(new BlockPos(16, 1, 4), Blocks.STONE);
        h.setBlock(new BlockPos(16, 2, 4), SeaChestContent.BLOCK.get());
        Player placer = paddler(h, new Vec3(16.5, 2.0, 2.5));
        InteractionResult onBlock = SeaChestTestSupport.useOnTop(h, placer, new BlockPos(16, 2, 4));
        h.assertFalse(onBlock.consumesAction(), "the paddle should do nothing on a placed chest, got " + onBlock);
        h.assertFalse(placer.isPassenger(), "nobody sits on a placed chest");
        // Worn: using the paddle on the wearer seats nobody
        Player wearer = SeaChestTestSupport.playerInLevel(h, new Vec3(20.5, 2.0, 4.5));
        wearer.setItemSlot(EquipmentSlot.CHEST, new ItemStack(SeaChestContent.ITEM.get()));
        Player other = paddler(h, new Vec3(20.5, 2.0, 3.0));
        other.interactOn(wearer, InteractionHand.MAIN_HAND);
        h.assertFalse(other.isPassenger() || wearer.isVehicle(), "nobody sits on a worn chest");
        // Beached: a floating chest entity on dry ground
        for (int x = 14; x <= 22; x++) {
            for (int z = 14; z <= 22; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        }
        SeaChestEntity beached = chestAt(h, new Vec3(18.5, 2.0, 18.5));
        Player stranded = paddler(h, new Vec3(18.5, 2.0, 17.0));
        // Afloat, occupied: a second paddler is refused
        SeaChestEntity afloat = chestAt(h, new Vec3(6.5, 4.4, 6.5));
        Player first = paddler(h, new Vec3(6.5, 4.0, 5.0));
        Player second = paddler(h, new Vec3(6.5, 4.0, 8.0));
        h.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    h.assertTrue(beached.tryMount(stranded) == SeaChestEntity.MountRefusal.NOT_FLOATING, "a beached chest refuses");
                    h.assertFalse(stranded.isPassenger(), "nobody sits on a beached chest");
                    mount(h, first, afloat);
                    h.assertTrue(afloat.tryMount(second) == SeaChestEntity.MountRefusal.OCCUPIED, "one rider only");
                    h.assertFalse(second.isPassenger(), "the second paddler stays in the water");
                    // Nobody picks up an occupied chest
                    second.setShiftKeyDown(true);
                    afloat.interact(second, InteractionHand.MAIN_HAND);
                    h.assertFalse(afloat.isRemoved(), "an occupied chest can't be picked up");
                })
                .thenSucceed();
    }

    /**
     * Paddling costs hunger like swimming: 0.01 exhaustion per block of still-water stroke at 1.5 b/s, times
     * paddle_hunger_factor 1.5, is 0.001125 per tick, about 0.0675 over 60 ticks.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 160, batch = BATCH)
    public static void paddlingCostsHunger(GameTestHelper h) {
        westWind(h, SETTLE + 80);
        SeaChestTestSupport.basin(h, 24, 4);
        SeaChestEntity chest = chestAt(h, new Vec3(18.5, 4.4, 4.5));
        Player player = paddler(h, new Vec3(18.5, 4.0, 3.0));
        float[] food0 = new float[1];
        h.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    mount(h, player, chest);
                    food0[0] = player.getFoodData().getExhaustionLevel();
                })
                .thenExecuteFor(60, () -> player.zza = 1.0f)
                .thenExecute(() -> {
                    float cost = player.getFoodData().getExhaustionLevel() - food0[0];
                    double expected = 60 * PaddleRules.exhaustion(
                            new PaddleRules.Stroke(0f, 0.0, 0.0, true), SeaChestConfig.paddling());
                    Constants.LOG.info("[SC2] 60 ticks of paddling cost {} exhaustion (expected about {})", cost, expected);
                    h.assertTrue(cost > 0.8 * expected && cost < 1.2 * expected, "exhaustion " + cost + ", expected about " + expected);
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ toggle

    /**
     * {@code sea_chest.paddle_enabled = false}: the paddle seats nobody (use opens the chest as without a paddle), and
     * a rider seated anyway makes no way. Calm wind, so any motion would be paddling.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = "pirates_n_ships_config_seachest_paddle_disabled")
    public static void disabledPaddleSeatsNobody(GameTestHelper h) {
        ConfigOverrides.during(h, SeaChestConfig.PADDLE_ENABLED, false);
        ServerLevel level = h.getLevel();
        String dim = level.dimension().location().toString();
        WindOverride.set(dim, 0.0, 0.0, level.getGameTime() + SETTLE + 60);
        SeaChestTestSupport.basin(h, 24, 4);
        SeaChestEntity chest = chestAt(h, new Vec3(12.5, 4.4, 4.5));
        Player player = paddler(h, new Vec3(12.5, 4.0, 3.0));
        Vec3[] start = new Vec3[1];
        h.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    h.assertTrue(chest.isInWater(), "the chest floats");
                    chest.interact(player, InteractionHand.MAIN_HAND);
                    h.assertFalse(player.isPassenger(), "disabled: the paddle must not seat the player");
                    h.assertTrue(chest.tryMount(player) == SeaChestEntity.MountRefusal.DISABLED, "disabled: refused");
                    // Seated anyway (e.g. switched off while riding): only drifts
                    h.assertTrue(player.startRiding(chest), "a forced ride should work");
                    start[0] = chest.position();
                })
                .thenExecuteFor(40, () -> player.zza = 1.0f)
                .thenExecute(() -> {
                    double forward = chest.getZ() - start[0].z;
                    Constants.LOG.info("[SC2] disabled, 40 ticks of forward keys: forward {}", forward);
                    h.assertTrue(Math.abs(forward) < 0.3, "disabled: no headway, moved " + forward);
                    player.stopRiding();
                })
                .thenSucceed();
    }
}

package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.combat.CombatConfig;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.combat.firearms.FirearmContent;
import com.richardsenger.piratesnships.combat.firearms.FirearmKind;
import com.richardsenger.piratesnships.combat.firearms.FirearmRules;
import com.richardsenger.piratesnships.combat.firearms.FirearmTrigger;
import com.richardsenger.piratesnships.combat.firearms.FirearmService;
import com.richardsenger.piratesnships.combat.firearms.FirearmsConfig;
import com.richardsenger.piratesnships.combat.firearms.LeadBallEntity;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Collection;

/**
 * GR3, GR4 and GR1 in a real server: loading the grappling hook from the off hand into the musket and firing it (GR3;
 * GR4 removed the crossbow), the reach of a level throw and shot (GR4), and the mooring ring (GR1) (docs/design.md
 * §8.3). The launch tests use a survival mock player looking up and drive the sessions the way {@code LivingEntity}
 * does ({@code use}, {@code onUseTick} at the held time, then {@code releaseUsing} with the remaining use time) and
 * measure the hook's speed right after the launch. The reach tests fly a hook by hand high above the test (not added
 * to the level, so only the test ticks it). The ring tests use the two hulls of {@link GrappleGameTests} (ship A at
 * x 2..6, ship B at x 16..20, both z 9..13, deck top at y 9) with rings placed on the deck before assembly.
 */
public final class GrappleLaunchGameTests {

    private static final String LAUNCH_BATCH = "pirates_n_ships_grapple_launch";
    private static final String RING_BATCH = "pirates_n_ships_grapple_ring";
    private static final String OFFHAND_BATCH = "pirates_n_ships_config_grapple_launch";
    private static final String MISFIRE_BATCH = "pirates_n_ships_config_grapple_launch_misfire";
    /** Relative tolerance of a measured launch speed (the weapon's inaccuracy changes the length very slightly). */
    private static final double SPEED_TOLERANCE = 0.03;

    private GrappleLaunchGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(GrappleLaunchGameTests.class);
    }

    // ------------------------------------------------------------------ launch fixtures

    /**
     * A survival mock player with an empty inventory, standing in the middle, looking up (the hook stays in the air),
     * holding {@code main} and {@code off}.
     */
    private static Player shooter(GameTestHelper h, ItemStack main, ItemStack off) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.getInventory().clearContent();
        Vec3 stand = h.absoluteVec(new Vec3(4.5, 1, 4.5));
        p.setPos(stand.x, stand.y, stand.z);
        p.setXRot(-60.0f);
        p.setItemInHand(InteractionHand.MAIN_HAND, main);
        p.setItemInHand(InteractionHand.OFF_HAND, off);
        return p;
    }

    private static ItemStack hook() {
        return new ItemStack(CombatContent.GRAPPLING_HOOK.get());
    }

    private static ItemStack musket() {
        return new ItemStack(CombatContent.MUSKET.get());
    }

    /** Puts {@code stack} into the first free slot that is not the selected hotbar slot (the main hand). */
    private static void give(Player p, ItemStack stack) {
        for (int i = 0; i < p.getInventory().items.size(); i++) {
            if (i != p.getInventory().selected && p.getInventory().items.get(i).isEmpty()) {
                p.getInventory().items.set(i, stack);
                return;
            }
        }
        throw new GameTestAssertException("no free slot");
    }

    private static int hooks(Player p) {
        return p.getInventory().countItem(CombatContent.GRAPPLING_HOOK.get());
    }

    private static int powder(Player p) {
        return p.getInventory().countItem(Items.GUNPOWDER);
    }

    private static int reload() {
        return FirearmsConfig.type(FirearmKind.MUSKET).reloadTicks();
    }

    private static void assertSpeed(GameTestHelper h, GrapplingHookEntity hook, double expected, String what) {
        double v = hook.getDeltaMovement().length();
        h.assertTrue(Math.abs(v - expected) <= SPEED_TOLERANCE * expected,
                what + " launched at " + v + " blocks/tick, expected " + expected);
    }

    /** Holds use on the musket in {@code hand} through a whole loading session (the reload time) and lets go. */
    private static void loadMusket(GameTestHelper h, Player p, InteractionHand hand) {
        ServerLevel level = h.getLevel();
        ItemStack gun = p.getItemInHand(hand);
        InteractionResult r = gun.use(level, p, hand).getResult();
        h.assertTrue(r.consumesAction(), "loading the musket was refused: " + r);
        h.assertTrue(p.isUsingItem() && p.getUseItem() == gun, "no loading session on the musket");
        h.assertTrue(!FirearmRules.isAimSession(p.getUseItemRemainingTicks()), "the session is not a loading session");
        gun.getItem().onUseTick(level, p, gun, FirearmRules.LOAD_SESSION_TICKS - reload());
        gun.releaseUsing(level, p, FirearmRules.LOAD_SESSION_TICKS - reload() - 5);
        p.stopUsingItem();
    }

    /**
     * Aims the loaded musket in {@code hand} for {@code ticks}, pulls the trigger (the attack key, FA1) and lets go
     * (which only lowers the gun).
     */
    private static void aimAndRelease(GameTestHelper h, Player p, InteractionHand hand, int ticks) {
        ServerLevel level = h.getLevel();
        ItemStack gun = p.getItemInHand(hand);
        InteractionResult r = gun.use(level, p, hand).getResult();
        h.assertTrue(r.consumesAction(), "aiming the musket was refused: " + r);
        h.assertTrue(FirearmRules.isAimSession(p.getUseItemRemainingTicks()), "the session is not an aim");
        FirearmTrigger.pullAimedFor(level, p, ticks);
        gun.releaseUsing(level, p, FirearmRules.AIM_SESSION_TICKS - ticks);
        p.stopUsingItem();
    }

    // ------------------------------------------------------------------ musket

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = LAUNCH_BATCH)
    public static void musketLoadsTheHookFromTheOffHand(GameTestHelper h) {
        Player player = shooter(h, musket(), hook());
        give(player, new ItemStack(Items.GUNPOWDER, 3));
        give(player, new ItemStack(CombatContent.LEAD_SHOT.get(), 2));
        loadMusket(h, player, InteractionHand.MAIN_HAND);
        ItemStack gun = player.getMainHandItem();
        h.assertTrue(GrappleContent.isHookLoaded(gun), "the musket holds no hook");
        h.assertTrue(FirearmContent.isLoaded(gun), "the musket is not marked loaded (bar, tooltip)");
        h.assertTrue(player.getOffhandItem().isEmpty(), "the hook is still in the off hand");
        h.assertTrue(powder(player) == 2, "not exactly one gunpowder was used: " + powder(player) + " left of 3");
        h.assertTrue(player.getInventory().countItem(CombatContent.LEAD_SHOT.get()) == 2, "a lead shot was used for the hook");
        h.assertTrue(GrappleService.hookOf(player) == null, "letting go after the load fired the hook");
        LoadedHook loaded = GrappleContent.loadedHook(gun);
        h.assertTrue(loaded != null && loaded.taken() && loaded.hook().is(CombatContent.GRAPPLING_HOOK.get()), "loaded " + loaded);
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = LAUNCH_BATCH)
    public static void cancelledMusketLoadTakesNothing(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = shooter(h, musket(), hook());
        give(player, new ItemStack(Items.GUNPOWDER, 3));
        ItemStack gun = player.getMainHandItem();
        h.assertTrue(gun.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "no loading session");
        int early = FirearmRules.LOAD_SESSION_TICKS - (reload() - 1);
        gun.getItem().onUseTick(level, player, gun, early);
        gun.releaseUsing(level, player, early);
        player.stopUsingItem();
        h.assertTrue(!GrappleContent.isHookLoaded(gun) && !FirearmContent.isLoaded(gun), "an early release loaded the musket");
        h.assertTrue(player.getOffhandItem().is(CombatContent.GRAPPLING_HOOK.get()), "the hook left the off hand");
        h.assertTrue(powder(player) == 3, "powder was used");
        h.assertTrue(GrappleService.hookOf(player) == null, "a hook is out");
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = LAUNCH_BATCH)
    public static void aimedMusketFiresTheHookOnRelease(GameTestHelper h) {
        Player player = shooter(h, musket(), hook());
        give(player, new ItemStack(Items.GUNPOWDER, 1));
        loadMusket(h, player, InteractionHand.MAIN_HAND);
        aimAndRelease(h, player, InteractionHand.MAIN_HAND, FirearmsConfig.AIM_STEADY_TICKS.get() + 5);
        ItemStack gun = player.getMainHandItem();
        GrapplingHookEntity out = GrappleService.hookOf(player);
        h.assertTrue(out != null, "no hook out after firing the musket (a misfire? the test area has a roof)");
        double musket = GrappleConfig.THROW_VELOCITY.get() * GrappleConfig.MUSKET_SPEED.get();
        assertSpeed(h, out, musket, "the musket hook");
        h.assertTrue(out.ropeLength() == GrappleConfig.ropeLength(GrappleLaunch.Mode.MUSKET)
                && out.ropeLength() > GrappleConfig.MAX_ROPE_LENGTH.get(), "rope length " + out.ropeLength());
        h.assertTrue(Math.abs(out.gravityFactor() - GrappleConfig.MUSKET_GRAVITY_FACTOR.get()) < 1.0e-6,
                "the musket hook flies with gravity factor " + out.gravityFactor());
        h.assertTrue(!GrappleContent.isHookLoaded(gun), "the musket still holds the hook");
        h.assertTrue(!FirearmContent.isLoaded(gun), "the musket is still marked loaded");
        h.assertTrue(player.getCooldowns().isOnCooldown(CombatContent.MUSKET.get()), "the musket's cooldown did not start");
        h.assertTrue(h.getLevel().getEntitiesOfClass(LeadBallEntity.class, player.getBoundingBox().inflate(16)).isEmpty(),
                "a lead ball left with the hook");
        h.assertTrue(GrappleService.release(player) && hooks(player) == 1, "the released hook did not come back");
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = LAUNCH_BATCH)
    public static void musketLoadedWithShotRefusesTheHook(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ItemStack gun = musket();
        FirearmContent.setLoaded(gun, true);
        Player player = shooter(h, gun, hook());
        give(player, new ItemStack(Items.GUNPOWDER, 3));
        InteractionResult r = gun.use(level, player, InteractionHand.MAIN_HAND).getResult();
        h.assertTrue(r.consumesAction() && FirearmRules.isAimSession(player.getUseItemRemainingTicks()),
                "a musket loaded with shot does not aim its shot: " + r);
        player.stopUsingItem();
        h.assertTrue(!GrappleContent.isHookLoaded(gun), "the hook was loaded on top of the shot");
        h.assertTrue(player.getOffhandItem().is(CombatContent.GRAPPLING_HOOK.get()), "the hook left the off hand");
        h.assertTrue(powder(player) == 3, "powder was used");
        // the hook's own use does not load or throw it either (the musket had its turn)
        player.getOffhandItem().use(level, player, InteractionHand.OFF_HAND);
        h.assertTrue(GrappleService.hookOf(player) == null && hooks(player) == 1, "the hook was thrown");
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = LAUNCH_BATCH)
    public static void musketWithoutGunpowderClicksAndKeepsTheHook(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = shooter(h, musket(), hook());
        give(player, new ItemStack(CombatContent.LEAD_SHOT.get(), 1)); // shot alone loads nothing: the hook asks for powder
        InteractionResult r = player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND).getResult();
        h.assertTrue(r == InteractionResult.FAIL, "a musket without powder started loading: " + r);
        h.assertTrue(!player.isUsingItem(), "a loading session started");
        h.assertTrue(player.getOffhandItem().getCount() == 1, "the hook left the hand");
        h.succeed();
    }

    // ------------------------------------------------------------------ crossbow (GR4: no launcher)

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = LAUNCH_BATCH)
    public static void aCrossbowNextToTheHookIsJustACrossbow(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = shooter(h, new ItemStack(Items.CROSSBOW), hook());
        give(player, new ItemStack(Items.ARROW, 5));
        ItemStack crossbow = player.getMainHandItem();
        // the main hand gets the use first: vanilla's draw with arrows, the hook stays in the off hand
        InteractionResult r = crossbow.use(level, player, InteractionHand.MAIN_HAND).getResult();
        h.assertTrue(r.consumesAction() && player.isUsingItem() && player.getUseItem() == crossbow, "the crossbow did not draw: " + r);
        int charge = CrossbowItem.getChargeDuration(crossbow, player);
        crossbow.releaseUsing(level, player, crossbow.getUseDuration(player) - (charge + 5));
        player.stopUsingItem();
        h.assertTrue(CrossbowItem.isCharged(crossbow), "the crossbow is not charged with an arrow");
        h.assertTrue(!GrappleContent.isHookLoaded(crossbow), "the hook went into the crossbow");
        h.assertTrue(player.getOffhandItem().is(CombatContent.GRAPPLING_HOOK.get()), "the hook left the off hand");
        h.assertTrue(player.getInventory().countItem(Items.ARROW) == 4, "the draw did not take one arrow");
        h.assertTrue(GrappleService.hookOf(player) == null, "a hook is out");
        // with no arrows the crossbow's use fails and vanilla offers the use to the off hand: the hook is thrown by hand
        Player empty = shooter(h, new ItemStack(Items.CROSSBOW), hook());
        InteractionResult none = empty.getMainHandItem().use(level, empty, InteractionHand.MAIN_HAND).getResult();
        h.assertTrue(!none.consumesAction(), "an empty crossbow without arrows took the use: " + none);
        InteractionResult thrown = empty.getOffhandItem().use(level, empty, InteractionHand.OFF_HAND).getResult();
        h.assertTrue(thrown.consumesAction() && !empty.isUsingItem(), "the off-hand hook was not thrown: " + thrown);
        GrapplingHookEntity out = GrappleService.hookOf(empty);
        h.assertTrue(out != null, "the hook was not thrown");
        assertSpeed(h, out, GrappleConfig.THROW_VELOCITY.get(), "the thrown hook");
        h.assertTrue(GrappleService.release(empty) && hooks(empty) == 1, "the released hook did not come back");
        h.succeed();
    }

    // ------------------------------------------------------------------ reach (GR4)

    /**
     * How a level flight ended: horizontal distance from the thrower's feet, ticks, height above the feet and state
     * then; and the last position still within the rope's length of the feet (distance and height), i.e. where the
     * hook was on the tick before its rope ran out.
     */
    private record Flight(double distance, int ticks, double heightAboveFeet, GrapplingHookEntity.State state,
                          double insideDistance, double insideHeight) {
    }

    /**
     * Flies a hook launched in {@code mode} level along +x from the eyes of a mock player standing 60 blocks above the
     * test (open air), with the mode's speed, gravity and rope ({@code rope} overrides the rope length; NaN keeps
     * it). The hook is not added to the level: the test ticks it until it stops flying or sinks to the thrower's
     * feet, i.e. lands on flat ground at the thrower's height.
     */
    private static Flight flyLevel(GameTestHelper h, GrappleLaunch.Mode mode, double rope) {
        ServerLevel level = h.getLevel();
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.getInventory().clearContent();
        Vec3 feet = h.absoluteVec(new Vec3(4.5, 60, 4.5));
        p.setPos(feet.x, feet.y, feet.z);
        p.setYRot(-90.0f); // +x
        p.setXRot(0.0f);
        Vec3 eye = new Vec3(p.getX(), p.getEyeY() - 0.1, p.getZ()); // where a thrown projectile starts
        GrapplingHookEntity hook = new GrapplingHookEntity(level, p, eye, new Vec3(GrappleConfig.speed(mode), 0, 0), hook(), false);
        GrappleService.configure(hook, mode);
        if (!Double.isNaN(rope)) {
            hook.setRopeLength(rope);
        }
        int t = 0;
        double insideDistance = 0;
        double insideHeight = hook.getY() - feet.y;
        try {
            while (t < 400 && hook.state() == GrapplingHookEntity.State.FLYING && hook.getY() > feet.y) {
                hook.tick();
                t++;
                if (hook.state() == GrapplingHookEntity.State.FLYING && hook.position().distanceTo(feet) <= hook.ropeLength()) {
                    insideDistance = Math.hypot(hook.getX() - feet.x, hook.getZ() - feet.z);
                    insideHeight = hook.getY() - feet.y;
                }
            }
            return new Flight(Math.hypot(hook.getX() - feet.x, hook.getZ() - feet.z), t, hook.getY() - feet.y, hook.state(),
                    insideDistance, insideHeight);
        } finally {
            hook.discard();
        }
    }

    /**
     * A level launch in {@code mode} would land past its rope (with 10 % margin), and with the rope it is still in the
     * air, above the thrower's feet, when the rope runs out: it reaches the whole rope.
     */
    private static void assertReach(GameTestHelper h, GrappleLaunch.Mode mode, String what) {
        double rope = GrappleConfig.ropeLength(mode);
        double speed = GrappleConfig.speed(mode);
        Flight free = flyLevel(h, mode, 1000.0);
        Constants.LOG.info("GR4 reach: a level {} lands {} blocks away after {} ticks (rope {})", what,
                String.format(java.util.Locale.ROOT, "%.1f", free.distance()), free.ticks(), rope);
        h.assertTrue(free.distance() >= 1.1 * rope, "a level " + what + " lands after " + free.distance()
                + " blocks, not past its " + rope + "-block rope with margin");
        Flight roped = flyLevel(h, mode, Double.NaN);
        h.assertTrue(roped.state() == GrapplingHookEntity.State.RETRACTING, "the " + what + "'s rope did not run out: " + roped);
        h.assertTrue(roped.insideDistance() >= rope - speed && roped.insideHeight() > 0.0,
                "the " + what + " fell before its rope ran out: " + roped);
        h.assertTrue(roped.distance() > rope - 0.5 && roped.distance() <= rope + speed,
                "the " + what + "'s rope ran out after " + roped.distance() + " blocks, not at " + rope + ": " + roped);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = LAUNCH_BATCH)
    public static void aLevelThrowReachesTheWholeRope(GameTestHelper h) {
        assertReach(h, GrappleLaunch.Mode.THROW, "throw");
        h.assertTrue(GrappleConfig.ropeLength(GrappleLaunch.Mode.THROW) >= 32.0, "the thrown rope is shorter than 32 blocks");
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = LAUNCH_BATCH)
    public static void aLevelMusketShotReachesTheWholeRope(GameTestHelper h) {
        assertReach(h, GrappleLaunch.Mode.MUSKET, "musket shot");
        h.assertTrue(GrappleConfig.ropeLength(GrappleLaunch.Mode.MUSKET) >= 2 * GrappleConfig.ropeLength(GrappleLaunch.Mode.THROW),
                "the musket does not reach twice as far as a throw");
        h.succeed();
    }

    // ------------------------------------------------------------------ throw, hands, misfire

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = LAUNCH_BATCH)
    public static void hookInTheMainHandWithoutALauncherIsThrown(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = shooter(h, hook(), ItemStack.EMPTY);
        InteractionResult r = player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND).getResult();
        h.assertTrue(r.consumesAction(), "the throw was refused: " + r);
        GrapplingHookEntity out = GrappleService.hookOf(player);
        h.assertTrue(out != null, "the hook was not thrown");
        assertSpeed(h, out, GrappleConfig.THROW_VELOCITY.get(), "the thrown hook");
        h.assertTrue(out.ropeLength() == GrappleConfig.MAX_ROPE_LENGTH.get(), "a thrown hook has rope " + out.ropeLength());
        h.assertTrue(player.getMainHandItem().isEmpty(), "the thrown hook is still in the hand");
        h.assertTrue(GrappleService.release(player) && hooks(player) == 1, "the released hook did not come back");
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = LAUNCH_BATCH)
    public static void swappedHandsThrowWhileTheOffHandIsRequired(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = shooter(h, hook(), musket());
        give(player, new ItemStack(Items.GUNPOWDER, 3));
        player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
        h.assertTrue(!player.isUsingItem(), "the musket in the off hand started loading");
        h.assertTrue(GrappleService.hookOf(player) != null, "the hook was not thrown");
        h.assertTrue(powder(player) == 3, "powder was used");
        h.assertTrue(GrappleService.release(player) && hooks(player) == 1, "the released hook did not come back");
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = OFFHAND_BATCH)
    public static void offhandNotRequiredAcceptsTheSwappedHands(GameTestHelper h) {
        ConfigOverrides.during(h, GrappleConfig.OFFHAND_REQUIRED, false);
        ServerLevel level = h.getLevel();
        Player player = shooter(h, hook(), musket());
        give(player, new ItemStack(Items.GUNPOWDER, 3));
        InteractionResult r = player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND).getResult();
        h.assertTrue(r.consumesAction(), "the hook did not hand the use to the musket: " + r);
        h.assertTrue(player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND, "the musket is not loading");
        ItemStack gun = player.getOffhandItem();
        gun.getItem().onUseTick(level, player, gun, FirearmRules.LOAD_SESSION_TICKS - reload());
        player.stopUsingItem();
        h.assertTrue(GrappleContent.isHookLoaded(gun) && FirearmContent.isLoaded(gun), "the musket in the off hand holds no hook");
        h.assertTrue(player.getMainHandItem().isEmpty(), "the hook is still in the main hand");
        h.assertTrue(powder(player) == 2, "not one gunpowder used");
        aimAndRelease(h, player, InteractionHand.OFF_HAND, 3);
        GrapplingHookEntity out = GrappleService.hookOf(player);
        h.assertTrue(out != null, "the off-hand musket did not fire the hook");
        assertSpeed(h, out, GrappleConfig.THROW_VELOCITY.get() * GrappleConfig.MUSKET_SPEED.get(), "the musket hook");
        h.assertTrue(GrappleService.release(player) && hooks(player) == 1, "the released hook did not come back");
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = MISFIRE_BATCH)
    public static void rainMisfireKeepsTheHookInTheMusket(GameTestHelper h) {
        ConfigOverrides.during(h, CombatConfig.FIREARM_MISFIRE_IN_RAIN, true);
        ConfigOverrides.during(h, CombatConfig.RAIN_MISFIRE_CHANCE, 1.0);
        ServerLevel level = h.getLevel();
        float rain = level.getRainLevel(1.0f);
        // clear the barrier ceiling above the shooter so rain can reach it
        for (int y = 2; y < 32; y++) {
            BlockPos p = new BlockPos(4, y, 4);
            if (h.getBlockState(p).is(Blocks.BARRIER)) h.setBlock(p, Blocks.AIR);
        }
        Player player = shooter(h, musket(), hook());
        give(player, new ItemStack(Items.GUNPOWDER, 1));
        loadMusket(h, player, InteractionHand.MAIN_HAND);
        ItemStack gun = player.getMainHandItem();
        try {
            level.setWeatherParameters(0, 6000, true, false);
            level.setRainLevel(1.0f);
            h.assertTrue(FirearmService.inRain(player), "the shooter should stand in the rain");
            aimAndRelease(h, player, InteractionHand.MAIN_HAND, 3);
            h.assertTrue(GrappleService.hookOf(player) == null, "a misfire launched the hook");
            h.assertTrue(GrappleContent.isHookLoaded(gun) && FirearmContent.isLoaded(gun), "a misfire lost the hook");
            h.assertTrue(player.getCooldowns().isOnCooldown(gun.getItem()), "a misfire still starts the cooldown");
        } finally {
            level.setWeatherParameters(20000000, 20000000, false, false);
            level.setRainLevel(rain);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ ring fixtures

    private record Ships(Fixture a, Fixture b) {
    }

    /** Two hulls with a floor ring on each deck: A's at (5, 9, 12), B's at (17, 9, 10) (relative). */
    private static Ships twoShipsWithRings(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helmA = DryHullGameTests.hull(h, 2, false);
        BlockPos helmB = DryHullGameTests.hull(h, 16, false);
        BlockState ring = GrappleContent.MOORING_RING.get().defaultBlockState()
                .setValue(MooringRingBlock.FACE, AttachFace.FLOOR).setValue(MooringRingBlock.FACING, Direction.NORTH);
        h.setBlock(new BlockPos(5, 9, 12), ring);
        h.setBlock(new BlockPos(17, 9, 10), ring);
        return new Ships(DryHullGameTests.assemble(h, helmA), DryHullGameTests.assemble(h, helmB));
    }

    /** Plot position of A's ring (one east and one south of the helm). */
    private static BlockPos ringA(Fixture a) {
        return a.helmPlot().offset(1, 0, 1);
    }

    /** Plot position of B's ring (one west and one north of the helm). */
    private static BlockPos ringB(Fixture b) {
        return b.helmPlot().offset(-1, 0, -1);
    }

    private static Vec3 ringWorld(ServerLevel level, Fixture f, BlockPos ringPlot) {
        return f.ship().toWorld(MooringRingBlock.ringCenter(level, ringPlot));
    }

    private static Player thrower(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.getInventory().clearContent();
        return p;
    }

    /** Keeps {@code player} on A's deck next to the helm while {@code aboard[0]}. */
    private static void keepAboard(GameTestHelper h, Player player, Fixture a, boolean[] aboard) {
        Runnable put = () -> {
            if (aboard[0] && !a.ship().isRemoved()) {
                Vec3 at = a.ship().toWorld(Vec3.atBottomCenterOf(a.helmPlot().east()));
                player.setPos(at.x, at.y, at.z);
            }
        };
        put.run();
        h.onEachTick(put);
    }

    /** A hook flying along +z over B's ring, passing {@code above} blocks over its middle. */
    private static GrapplingHookEntity passOverRing(ServerLevel level, Player player, Fixture b, double above) {
        Vec3 ring = ringWorld(level, b, ringB(b));
        Vec3 from = ring.add(0, above, -4.5);
        return GrappleService.launch(level, player, from, new Vec3(0, 0, GrappleConfig.THROW_VELOCITY.get()),
                new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
    }

    /** A hook launched from 2 blocks west of B's west wall at deck height straight at it (a plain latch). */
    private static GrapplingHookEntity throwAtWestWall(ServerLevel level, Player player, Fixture b) {
        Vec3 target = b.ship().toWorld(Vec3.atCenterOf(b.helmPlot().offset(-2, -1, 0)));
        Vec3 from = target.add(-2.0, 0, 0);
        return GrappleService.launch(level, player, from, new Vec3(GrappleConfig.THROW_VELOCITY.get(), 0, 0),
                new ItemStack(CombatContent.GRAPPLING_HOOK.get()), true);
    }

    private static Vec3 comWorld(ShipBody ship) {
        Vector3d c = new Vector3d();
        if (!ship.centerOfMass(c)) {
            throw new GameTestAssertException("no center of mass");
        }
        Vector3d w = ship.toWorld(c, new Vector3d());
        return new Vec3(w.x, w.y, w.z);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    // ------------------------------------------------------------------ rings

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = RING_BATCH)
    public static void hookPassingCloseToARingLatchesOntoIt(GameTestHelper h) {
        Ships s = twoShipsWithRings(h);
        ServerLevel level = h.getLevel();
        Player player = thrower(h);
        keepAboard(h, player, s.a(), new boolean[]{true});
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        h.runAfterDelay(20, () -> {
            h.assertTrue(MooringRingBlock.isRing(level, ringB(s.b())), "no ring on ship B at " + ringB(s.b()));
            hook[0] = passOverRing(level, player, s.b(), 0.8);
        });
        h.runAfterDelay(21, () -> h.succeedWhen(() -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook is " + g.state());
            h.assertTrue(s.b().ship().id().equals(g.shipId()), "latched onto the wrong ship");
            h.assertTrue(ringB(s.b()).equals(g.latchedBlock()), "latched on " + g.latchedBlock() + ", not the ring " + ringB(s.b()));
            h.assertTrue(g.onRing(), "the latch does not count as a ring");
            h.assertTrue(g.plotPos().distanceTo(MooringRingBlock.ringCenter(level, ringB(s.b()))) < 1.0e-6, "the hook is not on the ring");
            g.release(GrappleRules.Release.NONE);
        }));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120, batch = RING_BATCH)
    public static void ringLatchedHookHoldsWhereAPlainLatchSnaps(GameTestHelper h) {
        Ships s = twoShipsWithRings(h);
        ServerLevel level = h.getLevel();
        Player plain = thrower(h);
        Player ringed = thrower(h);
        boolean[] aboard = {true};
        keepAboard(h, plain, s.a(), aboard);
        keepAboard(h, ringed, s.a(), aboard);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[2];
        double rope = GrappleConfig.MAX_ROPE_LENGTH.get();
        double hold = GrappleConfig.RING_HOLD_MULTIPLIER.get() * rope;
        h.runAfterDelay(20, () -> {
            hook[0] = throwAtWestWall(level, plain, s.b());
            hook[1] = passOverRing(level, ringed, s.b(), 0.8);
        });
        h.runAfterDelay(30, () -> {
            h.assertTrue(hook[0].state() == GrapplingHookEntity.State.LATCHED && !hook[0].onRing(), "the plain hook did not latch");
            h.assertTrue(hook[1].state() == GrapplingHookEntity.State.LATCHED && hook[1].onRing(), "the ring hook did not latch on the ring");
            aboard[0] = false;
            // between the rope's length and the ring's hold
            double d = (rope + hold) / 2;
            Vec3 p0 = hook[0].position().add(-d, 0, 0);
            Vec3 p1 = hook[1].position().add(-d, 0, 0);
            plain.setPos(p0.x, p0.y, p0.z);
            ringed.setPos(p1.x, p1.y, p1.z);
        });
        h.runAfterDelay(32, () -> {
            h.assertTrue(hook[0].isRemoved(), "the plain latch did not snap beyond the rope's length");
            h.assertTrue(!hook[1].isRemoved() && hook[1].state() == GrapplingHookEntity.State.LATCHED,
                    "the ring latch snapped below ring_hold_multiplier times the rope");
            Vec3 far = hook[1].position().add(-(hold + 2.0), 0, 0);
            ringed.setPos(far.x, far.y, far.z);
        });
        h.runAfterDelay(34, () -> {
            h.assertTrue(hook[1].isRemoved(), "the ring latch did not snap beyond its hold");
            h.assertTrue(hooks(plain) == 1 && hooks(ringed) == 1, "the snapped hooks did not come back");
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 700, batch = RING_BATCH)
    public static void ropeTiedToARingKeepsHaulingAfterThePlayerLetsGo(GameTestHelper h) {
        Ships s = twoShipsWithRings(h);
        ServerLevel level = h.getLevel();
        Player player = thrower(h);
        boolean[] aboard = {true};
        keepAboard(h, player, s.a(), aboard);
        GrapplingHookEntity[] hook = new GrapplingHookEntity[1];
        double[] start = {-1};
        BlockPos ring = ringA(s.a());
        h.runAfterDelay(20, () -> {
            h.assertTrue(MooringRingBlock.isRing(level, ring), "no ring on ship A at " + ring);
            start[0] = horizontal(comWorld(s.a().ship()), comWorld(s.b().ship()));
            hook[0] = throwAtWestWall(level, player, s.b());
        });
        h.runAfterDelay(27, () -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(g.state() == GrapplingHookEntity.State.LATCHED, "the hook did not latch: " + g.state());
            player.setShiftKeyDown(true);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ring), Direction.UP, ring, false);
            InteractionResult r = level.getBlockState(ring).useWithoutItem(level, player, hit);
            h.assertTrue(r.consumesAction(), "using the ring did nothing: " + r);
            h.assertTrue(ring.equals(g.tiedRing()), "the rope is not tied to the ring: " + g.tiedRing());
            h.assertTrue(g.syncedTiedRing().filter(ring::equals).isPresent(), "the tie is not synched");
            // the player lets go and walks far away, beyond any rope length
            aboard[0] = false;
            player.setShiftKeyDown(false);
            Vec3 away = g.position().add(-2.5 * GrappleConfig.MAX_ROPE_LENGTH.get(), 0, 0);
            player.setPos(away.x, away.y, away.z);
        });
        h.runAfterDelay(40, () -> h.succeedWhen(() -> {
            GrapplingHookEntity g = hook[0];
            h.assertTrue(!g.isRemoved() && g.state() == GrapplingHookEntity.State.LATCHED, "the hook let go");
            h.assertTrue(s.a().ship().id().equals(g.throwerShipId()), "the ring's ship is not the hauling end");
            double d = horizontal(comWorld(s.a().ship()), comWorld(s.b().ship()));
            h.assertTrue(d < start[0] - 5.0, "the ships are not hauled together yet: " + start[0] + " -> " + d);
            Vec3 at = s.b().ship().toWorld(g.plotPos());
            double rope = horizontal(ringWorld(level, s.a(), ring), at);
            h.assertTrue(!g.taut(), "the rope is still hauling at " + rope + " blocks");
            h.assertTrue(rope > 0.2, "the hulls were pulled into each other: " + rope);
            h.assertTrue(d > 4.0, "the hulls overlap: centers " + d + " apart");
            g.release(GrappleRules.Release.NONE);
            h.assertTrue(hooks(player) == 1, "the released hook did not come back");
            s.a().ship().addVelocity(s.a().ship().linearVelocity().negate(), new Vector3d());
            s.b().ship().addVelocity(s.b().ship().linearVelocity().negate(), new Vector3d());
        }));
    }
}

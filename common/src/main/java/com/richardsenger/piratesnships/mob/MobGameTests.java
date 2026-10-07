package com.richardsenger.piratesnships.mob;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.melee.MeleeConfig;
import com.richardsenger.piratesnships.combat.melee.MeleeService;
import com.richardsenger.piratesnships.combat.melee.npc.DuelistBrain;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.combat.melee.weapon.DefaultWeapons;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.crime.WantedLevel;
import com.richardsenger.piratesnships.mob.ai.DuelistDebug;
import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.mob.entity.Sailor;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * The humanoid mobs in a real server (M3). Tests whose mobs have AI and pick targets run in their own batch, so mobs
 * of one test never see the players or mobs of another (the framework clears each batch's area before the next).
 * Yaw 90 faces -X, yaw -90 faces +X.
 */
public final class MobGameTests {

    /** Ticks a fresh test ship needs to rise and settle before mobs board it. */
    private static final int SETTLE_TICKS = 60;

    private MobGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(MobGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    private static void floor(GameTestHelper h, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static <T extends SeafarerMob> T spawn(GameTestHelper h, net.minecraft.world.entity.EntityType<T> type, int x, int z, float yaw) {
        T mob = h.spawn(type, new BlockPos(x, 1, z));
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.yBodyRot = yaw;
        return mob;
    }

    // ------------------------------------------------------------------ duelists

    /** A pirate near a survival player picks it as target and swings at it through the melee engine: wind-up, then hit frames. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200, batch = "pirates_n_ships_mob_pirate_windup")
    public static void pirateDuelsAPlayerThroughTheMeleeEngine(GameTestHelper h) {
        floor(h, 9);
        Pirate pirate = spawn(h, MobContent.PIRATE.get(), 2, 4, -90);
        Player player = MobTestSupport.playerInLevel(h, new Vec3(5.5, 1, 4.5), 90);
        Set<Phase> seen = EnumSet.noneOf(Phase.class);
        h.onEachTick(() -> {
            if (MeleeService.isActive(pirate)) seen.add(MeleeService.state(pirate).phase());
        });
        h.succeedWhen(() -> {
            h.assertTrue(pirate.getTarget() == player, "pirate did not target the player");
            h.assertTrue(seen.contains(Phase.WINDUP), "no wind-up seen: " + seen);
            h.assertTrue(seen.contains(Phase.ACTIVE), "no hit frames seen: " + seen);
        });
    }

    /**
     * The player parries the pirate's telegraphed hit through {@code MeleeService.parry}: no damage, the pirate staggers.
     * The scripted player parries at the start of the slash wind-up, which a feinting pirate would bait, so feints are
     * off here ({@code melee.npc_feints}); {@link #pirateFeintBaitsAnEarlyParry} covers them.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 300, batch = "pirates_n_ships_config_mob_player_parry")
    public static void playerParryStaggersThePirate(GameTestHelper h) {
        ConfigOverrides.during(h, MeleeConfig.NPC_FEINTS, false);
        floor(h, 9);
        Pirate pirate = spawn(h, MobContent.PIRATE.get(), 2, 4, -90);
        Player player = MobTestSupport.playerInLevel(h, new Vec3(4.5, 1, 4.5), 90);
        player.setHealth(player.getMaxHealth());
        boolean[] staggered = {false};
        h.onEachTick(() -> {
            CombatState p = MeleeService.state(pirate);
            if (DuelistBrain.hitDueInWindow(p, MeleeConfig.PARRY_WINDOW.get())
                    && MeleeService.state(player).phase() != Phase.PARRYING) {
                MeleeService.parry(player, DefaultWeapons.SABER);
            }
            if (p.phase() == Phase.STAGGERED && MeleeService.state(player).riposteReady()) staggered[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(staggered[0], "the pirate was never staggered by a parry");
            h.assertTrue(player.getHealth() >= player.getMaxHealth(), "the parried hit did damage: " + player.getHealth());
        });
    }

    /**
     * Feints (docs/design.md §8.5): with {@code melee.npc_skill_multiplier} 5 the pirate tier's feint frequency (0.2) is 1,
     * so every attack except the follow-up of a feint starts as one. The player parries as soon as a wind-up shows; the pirate
     * aborts the swing (its synced pose shows the feint recovery), the parry runs out into the lockout, and the real
     * follow-up hits the player.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 400, batch = "pirates_n_ships_config_mob_duelist_feint")
    public static void pirateFeintBaitsAnEarlyParry(GameTestHelper h) {
        ConfigOverrides.during(h, MeleeConfig.NPC_SKILL, 5.0);
        floor(h, 9);
        Pirate pirate = spawn(h, MobContent.PIRATE.get(), 2, 4, -90);
        Player player = MobTestSupport.playerInLevel(h, new Vec3(4.5, 1, 4.5), 90);
        player.setHealth(player.getMaxHealth());
        int[] feints = {0};
        boolean[] wasFeint = {false};
        float[] healthAtFirstFeint = {-1f};
        h.onEachTick(() -> {
            if (MeleeService.state(pirate).phase() == Phase.WINDUP && MeleeService.state(player).phase() != Phase.PARRYING) {
                MeleeService.parry(player, DefaultWeapons.SABER); // refused while locked out
            }
            boolean feint = pirate.meleePose().feint();
            if (feint && !wasFeint[0]) {
                feints[0]++;
                if (healthAtFirstFeint[0] < 0) healthAtFirstFeint[0] = player.getHealth();
            }
            wasFeint[0] = feint;
        });
        h.succeedWhen(() -> {
            h.assertTrue(feints[0] >= 1, "the pirate never feinted");
            h.assertTrue(player.getHealth() < healthAtFirstFeint[0],
                    "no hit after the feint: health " + player.getHealth() + ", at the feint " + healthAtFirstFeint[0]);
        });
    }

    /** {@code mobs.pirates_hostile = false}: a pirate told to attack a player drops the target and never swings. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 100, batch = "pirates_n_ships_config_mob_pirates_hostile")
    public static void piratesHostileOffLeavesThePiratePassive(GameTestHelper h) {
        ConfigOverrides.during(h, MobConfig.PIRATES_HOSTILE, false);
        floor(h, 9);
        Pirate pirate = spawn(h, MobContent.PIRATE.get(), 2, 4, -90);
        Player player = MobTestSupport.playerInLevel(h, new Vec3(4.5, 1, 4.5), 90);
        pirate.setTarget(player);
        h.onEachTick(() -> {
            if (MeleeService.isActive(pirate) && MeleeService.state(pirate).phase().attacking()) {
                h.fail("the passive pirate attacked");
            }
        });
        h.runAfterDelay(60, () -> {
            h.assertTrue(pirate.getTarget() == null, "the pirate kept its target");
            h.assertTrue(player.getHealth() >= player.getMaxHealth(), "the player was hurt");
            h.succeed();
        });
    }

    /** Pirates and navy notice each other and fight. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 100, batch = "pirates_n_ships_mob_factions")
    public static void piratesAndNavyFightEachOther(GameTestHelper h) {
        floor(h, 9);
        Pirate pirate = spawn(h, MobContent.PIRATE.get(), 1, 4, -90);
        NavyOfficer officer = spawn(h, MobContent.NAVY_OFFICER.get(), 7, 4, 90);
        h.succeedWhen(() -> {
            h.assertTrue(pirate.getTarget() == officer, "pirate does not target the officer");
            h.assertTrue(officer.getTarget() == pirate, "officer does not target the pirate");
        });
    }

    // ------------------------------------------------------------------ duelists against moving targets (M5)

    /** Vanilla walking speed in blocks per tick (4.317 blocks per second). */
    private static final double WALK = 0.2158;
    private static final int CHASE_FLOOR = 40;
    /** The strafing player keeps this far from the pirate's centre: just beyond the cutlass's reach. */
    private static final double STRAFE_RADIUS = 3.2;
    /** The player walking away starts walking when the pirate is this close (its centre to the player's box). */
    private static final double WALK_AWAY_GAP = 4.0;

    /** How the scripted player moves: the human walks and turns, the mock player of the other duel tests stands. */
    private enum Mover { STAND, WALK_AWAY, STRAFE, KITE }

    /** A standing player is hit within 3 s. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_mob_chase_stand")
    public static void pirateHitsAStandingPlayer(GameTestHelper h) {
        chase(h, Mover.STAND, 200, 60);
    }

    /**
     * A player that turns and walks straight away at walking speed when the pirate comes running is caught and hit within
     * 8 s, before it reaches the far corner. The pirate ({@code mobs.duelist_chase_speed} 1.15) is only about a fifth
     * faster than a walking player, and each hit knocks the player a block further away.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_mob_chase_walk")
    public static void pirateCatchesAPlayerWalkingAway(GameTestHelper h) {
        chase(h, Mover.WALK_AWAY, 220, 160);
    }

    /**
     * A player strafing sideways around the pirate at walking speed (facing it), keeping just beyond the cutlass's reach,
     * is hit within 5 s.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_mob_chase_strafe")
    public static void pirateHitsAStrafingPlayer(GameTestHelper h) {
        chase(h, Mover.STRAFE, 200, 100);
    }

    /** A player that backs off a block whenever the pirate comes within reach (kiting) is hit within 10 s. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_mob_chase_kite")
    public static void pirateHitsAKitingPlayer(GameTestHelper h) {
        chase(h, Mover.KITE, 240, 200);
    }

    /**
     * Runs a pirate against a scripted survival player for {@code ticks} and checks: the first hit lands within
     * {@code firstHitBound} ticks, at most 5 path recalculations per second, and the target is never dropped. The numbers
     * are logged ({@code [chase]}) so a regression shows how far off it is.
     */
    private static void chase(GameTestHelper h, Mover mover, int ticks, int firstHitBound) {
        floor(h, CHASE_FLOOR);
        // kiting players go +X from near the -X wall, walking ones diagonally from a corner (the longest walk), the
        // others stay in the middle
        boolean walk = mover == Mover.WALK_AWAY;
        int x0 = mover == Mover.KITE ? 4 : walk ? 3 : 17;
        int z0 = walk ? 3 : 20;
        Pirate pirate = spawn(h, MobContent.PIRATE.get(), x0, z0, walk ? -45 : -90);
        Player player = MobTestSupport.playerInLevel(h, walk ? new Vec3(x0 + 4.0, 1, z0 + 4.0) : new Vec3(x0 + 5.5, 1, z0 + 0.5), 90);
        float max = player.getMaxHealth();
        double reach = DefaultWeapons.CUTLASS.slash().reach();
        int[] tick = {0}, hits = {0}, firstHit = {-1}, lost = {0}, kite = {0}, wall = {-1};
        boolean[] fleeing = {false};
        // the pirate's state trace goes to the log, so a failure shows what the pirate was doing
        String traceKey = "chase test " + mover;
        DuelistDebug.listen(traceKey, h.getLevel().dimension(), h.absoluteVec(new Vec3(CHASE_FLOOR / 2.0, 1, CHASE_FLOOR / 2.0)));
        LivingEntity[] last = {null};
        h.onEachTick(() -> {
            int t = tick[0]++;
            if (player.getHealth() < max) {
                hits[0]++;
                if (firstHit[0] < 0) firstHit[0] = t;
                player.setHealth(max);
                Constants.LOG.info("[chase] {}: hit at tick {} (game time {})", mover, t, h.getLevel().getGameTime());
            }
            LivingEntity target = pirate.getTarget();
            if (last[0] == player && target != player) lost[0]++;
            last[0] = target;

            Vec3 pp = player.position(), mp = pirate.position();
            Vec3 away = new Vec3(pp.x - mp.x, 0, pp.z - mp.z);
            double dist = away.length();
            away = dist < 1.0e-6 ? new Vec3(1, 0, 0) : away.scale(1 / dist);
            Vec3 step = switch (mover) {
                case STAND -> Vec3.ZERO;
                case WALK_AWAY -> {
                    // turns and walks away once the pirate comes running (4 blocks), and keeps walking
                    if (dist - player.getBbWidth() / 2 <= WALK_AWAY_GAP) fleeing[0] = true;
                    yield fleeing[0] ? new Vec3(WALK, 0, WALK).scale(Math.sqrt(0.5)) : Vec3.ZERO;
                }
                case STRAFE -> {
                    // sideways around the pirate, stepping out towards just beyond its reach (at most walking speed)
                    double radial = Math.max(-0.6 * WALK, Math.min(0.6 * WALK, STRAFE_RADIUS - dist));
                    double side = Math.sqrt(WALK * WALK - radial * radial);
                    yield away.scale(radial).add(new Vec3(-away.z, 0, away.x).scale(side));
                }
                case KITE -> {
                    // the gap the slash measures: the pirate's centre to the player's box
                    double gap = dist - player.getBbWidth() / 2;
                    if (kite[0] == 0 && gap <= reach) kite[0] = 5; // a block at walking speed
                    if (kite[0] > 0) {
                        kite[0]--;
                        yield away.scale(WALK);
                    }
                    yield Vec3.ZERO;
                }
            };
            Vec3 lo = h.absoluteVec(new Vec3(1.5, 0, 1.5)), hi = h.absoluteVec(new Vec3(CHASE_FLOOR - 1.5, 0, CHASE_FLOOR - 1.5));
            double nx = Math.max(Math.min(lo.x, hi.x), Math.min(Math.max(lo.x, hi.x), pp.x + step.x));
            double nz = Math.max(Math.min(lo.z, hi.z), Math.min(Math.max(lo.z, hi.z), pp.z + step.z));
            if (wall[0] < 0 && (Math.abs(nx - pp.x - step.x) > 1.0e-6 || Math.abs(nz - pp.z - step.z) > 1.0e-6)) {
                wall[0] = t; // cornered from here on
            }
            float yaw = (float) Math.toDegrees(Math.atan2(-(mp.x - nx), mp.z - nz)); // face the pirate
            player.moveTo(nx, pp.y, nz, yaw, 0f);
            player.setYHeadRot(yaw);
            player.yBodyRot = yaw;
        });
        h.runAfterDelay(ticks, () -> {
            DuelistDebug.unlisten(traceKey);
            DuelistDebug.Stats stats = DuelistDebug.stats(pirate);
            double repathsPerSecond = stats.repaths * 20.0 / ticks;
            Constants.LOG.info("[chase] {}: first hit at tick {}, {} hits landed of {} attacks started, {} path recalculations "
                            + "({}/s), target lost {} times, player cornered at tick {}, difficulty {}", mover, firstHit[0], hits[0],
                    stats.attacksStarted, stats.repaths, String.format(java.util.Locale.ROOT, "%.2f", repathsPerSecond), lost[0],
                    wall[0], h.getLevel().getDifficulty());
            h.assertTrue(firstHit[0] >= 0, mover + ": the pirate never hit the player (" + stats.attacksStarted + " attacks started)");
            h.assertTrue(firstHit[0] <= firstHitBound, mover + ": first hit at tick " + firstHit[0] + ", expected by " + firstHitBound);
            h.assertTrue(wall[0] < 0 || firstHit[0] < wall[0], mover + ": first hit at tick " + firstHit[0]
                    + " only after the player was cornered at tick " + wall[0]);
            h.assertTrue(repathsPerSecond <= 5.0, mover + ": " + repathsPerSecond + " path recalculations per second");
            h.assertValueEqual(lost[0], 0, mover + ": times the target was dropped");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ musketeers

    /** A navy soldier fires at a wanted player within aim time plus reload time. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = "pirates_n_ships_mob_navy_wanted")
    public static void navySoldierShootsAWantedPlayer(GameTestHelper h) {
        floor(h, 24);
        NavySoldier soldier = spawn(h, MobContent.NAVY_SOLDIER.get(), 4, 12, -90);
        Player player = MobTestSupport.playerInLevel(h, new Vec3(12.5, 1, 12.5), 90);
        LawService.setScore(player, 100);
        h.assertTrue(LawService.wantedLevel(player).atLeast(WantedLevel.WANTED), "player not wanted: " + LawService.wantedLevel(player));
        h.succeedWhen(() -> {
            h.assertTrue(soldier.getTarget() == player, "soldier does not target the wanted player");
            h.assertTrue(soldier.shotsFired() > 0, "soldier has not fired");
        });
    }

    /** A navy soldier leaves an innocent player alone. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = "pirates_n_ships_mob_navy_innocent")
    public static void navySoldierIgnoresAnInnocentPlayer(GameTestHelper h) {
        floor(h, 24);
        NavySoldier soldier = spawn(h, MobContent.NAVY_SOLDIER.get(), 4, 12, -90);
        Player player = MobTestSupport.playerInLevel(h, new Vec3(12.5, 1, 12.5), 90);
        h.runAfterDelay(MobConfig.MUSKET_AIM_TICKS.get() + MobConfig.MUSKET_RELOAD_TICKS.get() / 2 + 20, () -> {
            h.assertTrue(soldier.getTarget() == null, "soldier targets an innocent player");
            h.assertValueEqual(soldier.shotsFired(), 0, "shots at an innocent player");
            h.assertTrue(player.getHealth() >= player.getMaxHealth(), "innocent player hurt");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ law

    /** Hitting a navy soldier is {@code attack_navy}; hitting a pirate is no crime. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void hittingNavyIsACrimeHittingPiratesIsNot(GameTestHelper h) {
        floor(h, 9);
        NavySoldier soldier = h.spawnWithNoFreeWill(MobContent.NAVY_SOLDIER.get(), 2, 1, 2);
        Pirate pirate = h.spawnWithNoFreeWill(MobContent.PIRATE.get(), 6, 1, 6);
        Player navyHitter = h.makeMockPlayer(GameType.SURVIVAL);
        Player pirateHitter = h.makeMockPlayer(GameType.SURVIVAL);
        soldier.hurt(h.getLevel().damageSources().playerAttack(navyHitter), 1.0f);
        pirate.hurt(h.getLevel().damageSources().playerAttack(pirateHitter), 100.0f);
        h.assertTrue(LawService.isNavy(soldier), "the soldier is not in #pirates_n_ships:navy");
        h.assertTrue(LawService.score(navyHitter) > 0, "attack_navy not recorded");
        h.assertValueEqual(LawService.score(pirateHitter), 0.0, "killing a pirate was a crime");
        h.assertTrue(!pirate.isAlive(), "pirate survived");
        h.succeed();
    }

    // ------------------------------------------------------------------ spawning and config

    /**
     * {@code /pirates mob debug on} traces a nearby pirate's state changes (target acquired, approaching, attacking) to
     * the log; {@code off} stops it.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200, batch = "pirates_n_ships_mob_debug")
    public static void debugCommandTracesNearbyDuelists(GameTestHelper h) {
        floor(h, 9);
        run(h, "pirates mob debug on");
        h.assertTrue(DuelistDebug.active(), "debug did not turn on");
        int before = DuelistDebug.linesLogged();
        spawn(h, MobContent.PIRATE.get(), 2, 4, -90);
        MobTestSupport.playerInLevel(h, new Vec3(6.5, 1, 4.5), 90);
        h.runAfterDelay(60, () -> {
            int lines = DuelistDebug.linesLogged() - before;
            run(h, "pirates mob debug off");
            h.assertTrue(!DuelistDebug.active(), "debug did not turn off");
            h.assertTrue(lines >= 3, "only " + lines + " trace lines (target, duelist, brain expected)");
            int after = DuelistDebug.linesLogged();
            h.runAfterDelay(20, () -> {
                h.assertValueEqual(DuelistDebug.linesLogged(), after, "trace lines after debug off");
                h.succeed();
            });
        });
    }

    /** {@code /pirates mob spawn sailor 3} spawns three sailors; an unknown type spawns nothing. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_mob_command")
    public static void spawnCommandSpawnsTheAskedCount(GameTestHelper h) {
        floor(h, 9);
        run(h, "pirates mob spawn sailor 3");
        run(h, "pirates mob spawn kraken 2");
        h.runAfterDelay(1, () -> {
            h.assertValueEqual(h.getEntities(MobContent.SAILOR.get()).size(), 3, "sailors spawned");
            h.succeed();
        });
    }

    /** A disabled type disappears, and the command refuses to spawn it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_mob_disabled")
    public static void disabledTypeDisappears(GameTestHelper h) {
        ConfigOverrides.during(h, MobConfig.enabled(MobKind.SAILOR), false);
        floor(h, 9);
        Sailor sailor = h.spawn(MobContent.SAILOR.get(), new BlockPos(4, 1, 4));
        run(h, "pirates mob spawn sailor 2");
        h.runAfterDelay(5, () -> {
            h.assertTrue(sailor.isRemoved(), "the disabled sailor is still there");
            h.assertValueEqual(h.getEntities(MobContent.SAILOR.get()).size(), 0, "sailors left");
            h.succeed();
        });
    }

    private static void run(GameTestHelper h, String command) {
        Vec3 pos = h.absoluteVec(new Vec3(4.5, 1, 4.5));
        CommandSourceStack source = h.getLevel().getServer().createCommandSourceStack()
                .withLevel(h.getLevel()).withPosition(pos).withPermission(4).withSuppressedOutput();
        h.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
    }

    // ------------------------------------------------------------------ ships

    /**
     * Mobs standing on an assembled ship stay on its deck while it moves: Sable tracks entities standing on a sub-level
     * (sable-notes §6). They keep their AI (a no-AI mob never runs {@code travel}, so it hangs frozen in world space and
     * Sable can't carry it) but are stationary, so they don't stroll off the small deck.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300, batch = "pirates_n_ships_mob_ship")
    public static void mobsOnADeckMoveWithTheShip(GameTestHelper h) {
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 23 || z == 0 || z == 23;
                for (int y = 2; y <= 8; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : y <= 7 ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
        int x0 = 6, z0 = 9;
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
        Vec3 deckA = Vec3.atBottomCenterOf(helmPlot.offset(-1, 0, 2));
        Vec3 deckB = Vec3.atBottomCenterOf(helmPlot.offset(1, 0, 3));
        SeafarerMob[] mobs = new SeafarerMob[2];
        Vec3[] start = new Vec3[1];
        // a freshly assembled floating ship rises about 1.4 blocks first (GameTest notes in design.md §3.3): mobs placed
        // before that would end up inside the deck, so they board once it has settled
        h.runAfterDelay(SETTLE_TICKS, () -> {
            mobs[0] = onDeck(h, ship, MobContent.SAILOR.get().create(h.getLevel()), deckA);
            mobs[1] = onDeck(h, ship, MobContent.NAVY_SOLDIER.get().create(h.getLevel()), deckB);
        });
        h.runAfterDelay(SETTLE_TICKS + 10, () -> start[0] = ship.toWorld(Vec3.atCenterOf(helmPlot)));
        // water drag stops a single kick within a second (0.9 blocks), so keep pushing like a sail would
        for (int t = SETTLE_TICKS + 10; t < SETTLE_TICKS + 70; t += 10) {
            h.runAfterDelay(t, () -> ship.addVelocity(new Vector3d(1.5, 0, 0), new Vector3d()));
        }
        h.runAfterDelay(SETTLE_TICKS + 80, () -> {
            SeafarerMob sailor = mobs[0], soldier = mobs[1];
            double moved = ship.toWorld(Vec3.atCenterOf(helmPlot)).distanceTo(start[0]);
            h.assertTrue(moved > 1.0, "the ship did not move: " + moved);
            for (SeafarerMob mob : new SeafarerMob[]{sailor, soldier}) {
                Vec3 plot = ship.toPlot(mob.position());
                AABB deck = new AABB(helmPlot.getX() - 2, helmPlot.getY() - 0.5, helmPlot.getZ(),
                        helmPlot.getX() + 3, helmPlot.getY() + 1.0, helmPlot.getZ() + 5);
                h.assertTrue(deck.contains(plot), mob.getName().getString() + " left the deck: plot " + plot + ", deck " + deck);
                ShipBody on = ShipEntities.standingOrRiding(mob);
                h.assertTrue(on != null && on.id().equals(ship.id()), mob.getName().getString() + " is not tracked on the ship");
            }
            sailor.discard();
            soldier.discard();
            h.succeed();
        });
    }

    private static SeafarerMob onDeck(GameTestHelper h, ShipBody ship, SeafarerMob mob, Vec3 plotPos) {
        Vec3 w = ship.toWorld(plotPos);
        mob.setStationary(true);
        mob.moveTo(w.x, w.y + 0.05, w.z, 0, 0);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }
}

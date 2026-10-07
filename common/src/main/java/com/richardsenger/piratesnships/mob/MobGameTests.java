package com.richardsenger.piratesnships.mob;

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

    /** The player parries the pirate's telegraphed hit through {@code MeleeService.parry}: no damage, the pirate staggers. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 300, batch = "pirates_n_ships_mob_player_parry")
    public static void playerParryStaggersThePirate(GameTestHelper h) {
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
     * (sable-notes §6). They have no AI here, so they can't walk off the small deck on their own.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = "pirates_n_ships_mob_ship")
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
        SeafarerMob sailor = onDeck(h, ship, MobContent.SAILOR.get().create(h.getLevel()), deckA);
        SeafarerMob soldier = onDeck(h, ship, MobContent.NAVY_SOLDIER.get().create(h.getLevel()), deckB);
        Vec3 start = ship.toWorld(Vec3.atCenterOf(helmPlot));
        h.runAfterDelay(10, () -> ship.addVelocity(new Vector3d(1.5, 0, 0), new Vector3d()));
        h.runAfterDelay(80, () -> {
            double moved = ship.toWorld(Vec3.atCenterOf(helmPlot)).distanceTo(start);
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
        mob.setNoAi(true);
        mob.moveTo(w.x, w.y + 0.05, w.z, 0, 0);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }
}

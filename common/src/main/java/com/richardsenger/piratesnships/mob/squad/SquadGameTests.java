package com.richardsenger.piratesnships.mob.squad;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.world.OutpostFixture;
import com.richardsenger.piratesnships.world.WorldConfig;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Squads (MOB2) on the navy outpost of {@link OutpostFixture} ({@code EMPTY_48}, the WG3 garrison of 6 soldiers and an
 * officer). Every test has its own batch: the outposts are big, their squads walk, and most tests shorten the pause.
 */
public final class SquadGameTests {

    private static final String BATCH = "pirates_n_ships_config_mob_squad_";
    /**
     * Ticks between placing the outpost and the patrol: a freshly placed mob is not on the ground until it has ticked,
     * and a mob in the air can't plan a path (vanilla's {@code canUpdatePath}), so the officer couldn't draw anyone.
     */
    private static final int SETTLE_TICKS = 20;
    /**
     * The window in which every mob of the squad leaves its post: the default {@code post_pause_seconds} (the tests
     * shorten the pause itself). The members right behind the officer only close up at the first waypoint (the court,
     * a few steps away); they move off for good when he sets off for the second.
     */
    private static final int LEAVE_WINDOW = SquadConfig.POST_PAUSE_SECONDS.defaultValue() * 20;

    private SquadGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(SquadGameTests.class);
    }

    /**
     * A forced patrol: the officer and three soldiers leave their posts at once, walk at least three waypoints in file
     * (at every waypoint each member within 3 blocks of the man in front), pause there, and after {@code return} stand
     * at their posts again, stationary.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 3600, batch = BATCH + "patrol")
    public static void squadPatrolsInFileAndReturnsToItsPosts(GameTestHelper helper) {
        ConfigOverrides.during(helper, SquadConfig.POST_PAUSE_SECONDS, 2);
        ServerLevel level = helper.getLevel();
        OutpostFixture.Placed outpost = OutpostFixture.place(helper, Direction.NORTH);
        NavyOfficer officer = officer(helper, outpost);
        Squad squad = SquadService.of(officer);
        helper.assertTrue(squad != null, "the garrison's officer leads a squad");
        helper.assertTrue(squad.route().size() >= 3, "a route of at least three waypoints, got " + squad.route());
        helper.assertValueEqual(squad.state(), SquadState.AT_POST, "the squad starts at its posts");

        List<SeafarerMob> squadMobs = new ArrayList<>();
        Map<UUID, Vec3> startPositions = new HashMap<>();

        // every waypoint reached: the file closed up
        int[] lastChecked = {-1};
        helper.onEachTick(() -> {
            Squad.Arrival a = squad.lastArrival();
            if (a == null || a.waypoint() <= lastChecked[0]) return;
            lastChecked[0] = a.waypoint();
            if (a.maxGap() > FileFollowing.MAX_GAP) {
                helper.fail("at waypoint " + a.waypoint() + " " + squad.route().get(a.waypoint()).toShortString()
                        + " the file was " + String.format("%.2f", a.maxGap()) + " blocks apart");
            }
            if (a.members() != 3) helper.fail("at waypoint " + a.waypoint() + " the squad had " + a.members() + " members");
        });

        // how far each squad mob got from where it stood when the patrol started
        Map<UUID, Double> farthest = new HashMap<>();
        helper.onEachTick(() -> {
            for (SeafarerMob m : squadMobs) {
                double d = m.position().subtract(startPositions.get(m.getUUID())).horizontalDistance();
                farthest.merge(m.getUUID(), d, Math::max);
            }
        });
        helper.startSequence()
                .thenExecuteAfter(SETTLE_TICKS, () -> {
                    helper.assertTrue(squad.startPatrol(true), "the patrol starts");
                    helper.assertValueEqual(squad.members().size(), 3, "three soldiers follow the officer");
                    squadMobs.addAll(squad.mobs(level));
                    helper.assertValueEqual(squadMobs.size(), 4, "officer and three soldiers loaded");
                    for (SeafarerMob m : squadMobs) {
                        helper.assertTrue(!m.isStationary(), m + " is no longer stationary on patrol");
                        helper.assertTrue(m.isPersistenceRequired(), m + " stays persistent");
                        startPositions.put(m.getUUID(), m.position());
                    }
                })
                .thenExecuteAfter(LEAVE_WINDOW, () -> {
                    for (SeafarerMob m : squadMobs) {
                        double moved = farthest.getOrDefault(m.getUUID(), 0.0);
                        helper.assertTrue(moved > 1.0, m + " left its post within the default post_pause_seconds (got "
                                + String.format("%.2f", moved) + " blocks away)");
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(squad.arrivals() >= 3, "three waypoints reached (" + squad.arrivals() + ")"))
                .thenExecute(() -> {
                    helper.assertTrue(squad.lastArrival() != null && squad.lastArrival().waypoint() >= 2, "the third waypoint was reached");
                    helper.assertTrue(squad.orderReturn(), "the return is ordered");
                    helper.assertValueEqual(squad.state(), SquadState.RETURNING, "the squad returns");
                })
                .thenWaitUntil(() -> helper.assertValueEqual(squad.state(), SquadState.AT_POST, "squad state"))
                .thenExecute(() -> {
                    for (SeafarerMob m : OutpostFixture.navy(level, outpost.box())) {
                        GarrisonPost post = SquadService.post(m);
                        helper.assertTrue(post != null, m + " has a post");
                        helper.assertTrue(SquadService.atPost(m), m + " is back at its post " + post.pos().toShortString()
                                + ", stands at " + m.blockPosition().toShortString());
                        helper.assertTrue(m.isStationary(), m + " is stationary again");
                        helper.assertTrue(m.isPersistenceRequired(), m + " is persistent");
                    }
                    helper.assertTrue(squad.members().isEmpty(), "the squad is dismissed at its posts");
                    OutpostFixture.navy(level, outpost.box()).forEach(SeafarerMob::discard);
                })
                .thenSucceed();
    }

    /** A soldier on patrol is hit: within 20 ticks the officer and every member target the attacker. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 600, batch = BATCH + "alert")
    public static void aHitSoldierAlertsTheWholeSquad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OutpostFixture.Placed outpost = OutpostFixture.place(helper, Direction.NORTH);
        NavyOfficer officer = officer(helper, outpost);
        Squad squad = SquadService.of(officer);
        helper.assertTrue(squad != null, "the garrison's officer leads a squad");

        long[] hitAt = {0};
        Husk[] attacker = {null};
        helper.startSequence()
                .thenExecuteAfter(SETTLE_TICKS, () -> {
                    helper.assertTrue(squad.startPatrol(true), "the patrol starts");
                    helper.assertValueEqual(squad.members().size(), 3, "three soldiers follow the officer");
                })
                .thenExecuteAfter(40, () -> {
                    NavySoldier victim = (NavySoldier) level.getEntity(squad.members().get(0));
                    helper.assertTrue(victim != null && victim.isAlive(), "the first member is there");
                    Husk husk = EntityType.HUSK.create(level);
                    husk.setNoAi(true);
                    husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
                    husk.setHealth(1000);
                    husk.moveTo(victim.getX() + 3, victim.getY(), victim.getZ(), 0f, 0f);
                    level.addFreshEntity(husk);
                    attacker[0] = husk;
                    for (SeafarerMob m : squad.mobs(level)) {
                        helper.assertTrue(m.getTarget() == null, m + " has no target before the hit");
                    }
                    hitAt[0] = level.getGameTime();
                    victim.hurt(level.damageSources().mobAttack(husk), 1f);
                })
                .thenWaitUntil(() -> {
                    for (SeafarerMob m : squad.mobs(level)) {
                        helper.assertTrue(m.getTarget() == attacker[0], m + " targets the attacker");
                    }
                })
                .thenExecute(() -> {
                    long took = level.getGameTime() - hitAt[0];
                    helper.assertTrue(took <= 20, "the whole squad targets the attacker within 20 ticks (took " + took + ")");
                    helper.assertValueEqual(squad.state(), SquadState.FIGHTING, "the squad fights");
                    attacker[0].discard();
                    OutpostFixture.navy(level, outpost.box()).forEach(SeafarerMob::discard);
                })
                .thenSucceed();
    }

    /** A member killed on patrol is replaced from the garrison when the squad reaches the next waypoint. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 2400, batch = BATCH + "refill")
    public static void aKilledMemberIsReplacedAtTheNextWaypoint(GameTestHelper helper) {
        ConfigOverrides.during(helper, SquadConfig.POST_PAUSE_SECONDS, 1);
        // 9 soldiers: the default six (land gate, walls, wall towers), the building's guard and the gate's two
        // sea-side walkway guards, so the garrison has reserves on the walkway whatever building the jigsaw picked
        ConfigOverrides.during(helper, WorldConfig.NAVY_OUTPOST_GARRISON_SOLDIERS, 9);
        ServerLevel level = helper.getLevel();
        OutpostFixture.Placed outpost = OutpostFixture.place(helper, Direction.NORTH);
        NavyOfficer officer = officer(helper, outpost);
        Squad squad = SquadService.of(officer);
        helper.assertTrue(squad != null, "the garrison's officer leads a squad");
        List<UUID> original = new ArrayList<>();

        UUID[] killed = {null};
        int[] arrivalsAtKill = {0};
        helper.startSequence()
                .thenExecuteAfter(SETTLE_TICKS, () -> {
                    helper.assertTrue(squad.startPatrol(true), "the patrol starts");
                    original.addAll(squad.members());
                    helper.assertValueEqual(original.size(), 3, "three soldiers follow the officer");
                })
                .thenWaitUntil(() -> helper.assertTrue(squad.arrivals() >= 1, "the first waypoint is reached"))
                .thenExecute(() -> {
                    killed[0] = squad.members().get(0);
                    NavySoldier victim = (NavySoldier) level.getEntity(killed[0]);
                    helper.assertTrue(victim != null, "the first member is there");
                    arrivalsAtKill[0] = squad.arrivals();
                    victim.kill();
                })
                .thenWaitUntil(() -> helper.assertTrue(squad.arrivals() > arrivalsAtKill[0], "the next waypoint is reached"))
                .thenExecute(() -> {
                    List<UUID> now = squad.members();
                    helper.assertValueEqual(now.size(), 3, "the squad is three strong again");
                    helper.assertTrue(!now.contains(killed[0]), "the dead soldier left the squad");
                    helper.assertTrue(now.stream().anyMatch(id -> !original.contains(id)), "a new soldier joined from the garrison");
                    helper.assertTrue(squad.replacements() >= 1, "the squad counted the replacement");
                    NavySoldier recruit = (NavySoldier) level.getEntity(now.stream().filter(id -> !original.contains(id)).findFirst().orElseThrow());
                    helper.assertTrue(recruit != null && !recruit.isStationary(), "the recruit left his post");
                    OutpostFixture.navy(level, outpost.box()).forEach(SeafarerMob::discard);
                })
                .thenSucceed();
    }

    /** With {@code mobs.squad.enabled} off the officer won't patrol and the garrison keeps its posts. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 400, batch = BATCH + "disabled")
    public static void disabledSquadsLeaveTheGarrisonStationary(GameTestHelper helper) {
        ConfigOverrides.during(helper, SquadConfig.ENABLED, false);
        ServerLevel level = helper.getLevel();
        OutpostFixture.Placed outpost = OutpostFixture.place(helper, Direction.NORTH);
        NavyOfficer officer = officer(helper, outpost);
        Squad squad = SquadService.of(officer);
        helper.assertTrue(squad != null, "the officer still has his route");
        helper.assertTrue(!squad.startPatrol(true), "no patrol while squads are disabled");
        helper.runAfterDelay(200, () -> {
            List<SeafarerMob> garrison = OutpostFixture.navy(level, outpost.box());
            helper.assertValueEqual(garrison.size(), 7, "the garrison");
            for (SeafarerMob m : garrison) {
                helper.assertTrue(m.isStationary(), m + " is stationary");
                helper.assertTrue(SquadService.atPost(m), m + " keeps its post");
            }
            helper.assertValueEqual(squad.state(), SquadState.AT_POST, "squad state");
            helper.assertTrue(squad.members().isEmpty(), "no members");
            garrison.forEach(SeafarerMob::discard);
            helper.succeed();
        });
    }

    private static NavyOfficer officer(GameTestHelper helper, OutpostFixture.Placed outpost) {
        return OutpostFixture.navy(helper.getLevel(), outpost.box()).stream()
                .filter(m -> m instanceof NavyOfficer).map(m -> (NavyOfficer) m).findFirst()
                .orElseThrow(() -> new IllegalStateException("the outpost has no officer"));
    }
}

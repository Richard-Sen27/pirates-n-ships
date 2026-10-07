package com.richardsenger.piratesnships.mob.kraken;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.hazards.HazardsConfig;
import com.richardsenger.piratesnships.mob.kraken.KrakenTentacles.Job;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips.Fixture;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * GameTests of the kraken (K1a) in the 40×40 sailing basin ({@link SailingGameTestsShips#basin}: water from y 2 to 7,
 * open sky). Every test has its own batch, so a kraken never sees another test's ships or swimmers; tests that change
 * config use {@code pirates_n_ships_config_kraken_*} batches. Krakens, players and ships are removed when a test ends.
 */
public final class KrakenGameTests {

    private static final String BATCH = "pirates_n_ships_kraken_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_kraken_";

    /** The kraken's start next to the target ship's starboard side, on the basin floor. */
    private static final Vec3 KRAKEN_BY_SHIP = new Vec3(33.0, 2.1, 19.5);
    /** The target hull x0 (x 22..26, z 17..21); the control hull lies at x 3..7. */
    private static final int SHIP_X = 22;
    private static final int CONTROL_X = 3;
    private static final int SHIP_Z = 17;

    private KrakenGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(KrakenGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    private static List<KrakenPart> partsOf(GameTestHelper h, Kraken k) {
        return h.getLevel().getEntities(KrakenContent.PART.get(), k.getBoundingBox().inflate(40), p -> p.parent() == k);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double comY(ShipBody ship) {
        Vector3d c = new Vector3d();
        ship.centerOfMass(c);
        return ship.toWorld(c, new Vector3d()).y;
    }

    private static int masts(GameTestHelper h, ShipBody ship) {
        int n = 0;
        for (BlockPos p : ship.plotBlocks()) if (h.getLevel().getBlockState(p).is(SailingBlocks.MASTS)) n++;
        return n;
    }

    private static int command(GameTestHelper h, Vec3 relative, String command) {
        ServerLevel level = h.getLevel();
        CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level)
                .withPosition(h.absoluteVec(relative)).withPermission(2).withSuppressedOutput();
        try {
            return level.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            throw new AssertionError("command failed to parse: " + command + ": " + e.getMessage());
        }
    }

    private static List<Kraken> krakensNear(GameTestHelper h, Vec3 relative, double radius) {
        Vec3 at = h.absoluteVec(relative);
        return h.getLevel().getEntitiesOfClass(Kraken.class, new AABB(at, at).inflate(radius));
    }

    /**
     * Switches {@code mobGriefing} off until the test ends, so the kraken leaves the masts alone. Breaking the fence
     * between the yards cuts the upper yard off the hull, the ship splits into two bodies, and the test's ship id may
     * end up on either (seen once in three runs: the "ship" was the 0.55 kpg yard). Tests that measure the hull use this;
     * the game rule is world-wide, so they run in their own {@code config} batch.
     */
    private static void noMastStrikes(GameTestHelper h) {
        GameRules.BooleanValue rule = h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean before = rule.get();
        rule.set(false, h.getLevel().getServer());
        KrakenTestSupport.onEnd(h, () -> rule.set(before, h.getLevel().getServer()));
    }

    private static Fixture targetShip(GameTestHelper h) {
        return SailingGameTestsShips.assemble(h, SailingGameTestsShips.squareHull(h, SHIP_X, SHIP_Z, SailTrim.FURLED));
    }

    // ------------------------------------------------------------------ the body and its parts

    /** The kraken has its eight tentacle and two eye parts, and they follow it when it is moved. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 80, batch = BATCH + "parts")
    public static void partsExistAndFollowTheBody(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        Kraken k = KrakenTestSupport.kraken(h, new Vec3(12.5, 2.1, 12.5));
        h.runAfterDelay(5, () -> {
            List<KrakenPart> parts = partsOf(h, k);
            h.assertTrue(parts.size() == KrakenPart.PARTS, "expected 10 parts, found " + parts.size());
            h.assertTrue(parts.stream().filter(KrakenPart::isEye).count() == 2, "expected two eyes");
            for (KrakenPart p : parts) {
                double d = p.centre().distanceTo(k.bodyCentre());
                h.assertTrue(d < 6.0, "part " + p.index() + " is " + d + " blocks from the body");
                h.assertTrue(p.isEye() ? p.getBbWidth() == 0.6f && p.getBbHeight() == 0.6f
                        : p.getBbWidth() == 0.9f && p.getBbHeight() == 2.5f, "part " + p.index() + " has the wrong size");
            }
            Vec3 to = h.absoluteVec(new Vec3(24.5, 2.1, 24.5));
            k.teleportTo(to.x, to.y, to.z);
        });
        h.runAfterDelay(30, () -> {
            List<KrakenPart> parts = partsOf(h, k);
            h.assertTrue(parts.size() == KrakenPart.PARTS, "parts lost after the move: " + parts.size());
            for (KrakenPart p : parts) {
                double d = p.centre().distanceTo(k.bodyCentre());
                h.assertTrue(d < 6.0, "part " + p.index() + " did not follow: " + d + " blocks from the body");
            }
            h.succeed();
        });
    }

    /** A hit on an eye takes three times the damage of the same hit on the body; the boss bar shows the health. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 80, batch = BATCH + "eye")
    public static void eyeHitsCountThreeTimes(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        Kraken k = KrakenTestSupport.kraken(h, new Vec3(12.5, 2.1, 12.5));
        float[] loss = new float[2];
        h.runAfterDelay(3, () -> {
            float before = k.getHealth();
            k.hurt(k.damageSources().generic(), 4f);
            loss[0] = before - k.getHealth();
        });
        h.runAfterDelay(30, () -> {
            KrakenPart eye = k.part(KrakenPart.EYE_LEFT);
            h.assertTrue(eye != null && eye.isEye(), "no left eye");
            float before = k.getHealth();
            eye.hurt(k.damageSources().generic(), 4f);
            loss[1] = before - k.getHealth();
            Constants.LOG.info("[kraken test] body hit lost {}, eye hit lost {}", loss[0], loss[1]);
            h.assertTrue(loss[0] > 0, "the body hit did no damage");
            h.assertTrue(Math.abs(loss[1] - 3 * loss[0]) < 0.01f, "the eye hit lost " + loss[1] + ", the body hit " + loss[0]);
        });
        h.runAfterDelay(32, () -> {
            float fraction = k.getHealth() / k.getMaxHealth();
            h.assertTrue(Math.abs(k.bossEvent().getProgress() - fraction) < 1e-4, "boss bar " + k.bossEvent().getProgress() + " vs " + fraction);
            h.succeed();
        });
    }

    /** A tentacle that loses its pool is cut (not hittable, the body takes half), and grows back after the regrow time. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 120, batch = CONFIG_BATCH + "regrow")
    public static void tentacleIsCutAndRegrows(GameTestHelper h) {
        ConfigOverrides.during(h, KrakenConfig.TENTACLE_REGROW_TICKS, 40);
        SailingGameTestsShips.basin(h, true);
        Kraken k = KrakenTestSupport.kraken(h, new Vec3(12.5, 2.1, 12.5));
        h.runAfterDelay(3, () -> {
            KrakenPart t = k.part(2);
            h.assertTrue(t != null && !t.isEye(), "no tentacle 2");
            float before = k.getHealth();
            t.hurt(k.damageSources().generic(), 30f);
            h.assertTrue(!k.tentacles().isCut(2), "cut too early");
            h.assertTrue(Math.abs(k.tentacles().pool(2) - 10) < 1e-6, "pool after 30 damage: " + k.tentacles().pool(2));
            h.assertTrue(Math.abs(before - k.getHealth() - 15f) < 0.01f, "the body took " + (before - k.getHealth()) + ", not half");
            t.invulnerableTime = 0;
            t.hurt(k.damageSources().generic(), 30f);
            h.assertTrue(k.tentacles().isCut(2), "not cut after 60 damage");
            h.assertTrue(!t.isPickable() && t.isCut(), "a cut tentacle can still be hit");
            t.invulnerableTime = 0;
            h.assertTrue(!t.hurt(k.damageSources().generic(), 5f), "a cut tentacle took a hit");
        });
        h.runAfterDelay(30, () -> h.assertTrue(k.tentacles().isCut(2), "regrew too early"));
        h.runAfterDelay(50, () -> {
            h.assertTrue(!k.tentacles().isCut(2), "did not regrow");
            h.assertTrue(Math.abs(k.tentacles().pool(2) - KrakenConfig.TENTACLE_HEALTH.get()) < 1e-6, "pool not full: " + k.tentacles().pool(2));
            KrakenPart t = k.part(2);
            h.assertTrue(t != null && t.isPickable() && !t.isCut(), "the regrown tentacle can't be hit");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ ships

    /**
     * Under a small ship the kraken surfaces, grips the hull and pulls it down: the ship's centre of mass sinks
     * measurably below an identical control ship's within 100 ticks.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 160, batch = CONFIG_BATCH + "grab")
    public static void gripsPullTheShipDown(GameTestHelper h) {
        noMastStrikes(h);
        SailingGameTestsShips.basin(h, true);
        Fixture ship = targetShip(h);
        Fixture control = SailingGameTestsShips.assemble(h, SailingGameTestsShips.squareHull(h, CONTROL_X, SHIP_Z, SailTrim.FURLED));
        double[] start = new double[2];
        int[] maxGrips = {0};
        Kraken[] kraken = new Kraken[1];
        h.runAfterDelay(10, () -> {
            start[0] = comY(ship.ship());
            start[1] = comY(control.ship());
            kraken[0] = KrakenTestSupport.kraken(h, KRAKEN_BY_SHIP);
        });
        h.onEachTick(() -> {
            if (kraken[0] != null) maxGrips[0] = Math.max(maxGrips[0], kraken[0].gripping());
        });
        h.runAfterDelay(110, () -> {
            Kraken k = kraken[0];
            double sink = comY(ship.ship()) - start[0];
            double controlSink = comY(control.ship()) - start[1];
            Constants.LOG.info("[kraken test] grab: mass {}, state {}, grips max {} now {}, mast blocks broken {}, ship dy {}, control dy {}",
                    ship.ship().mass(), k.state(), maxGrips[0], k.gripping(), k.mastBlocksBroken(), sink, controlSink);
            h.assertTrue(k.targetShip() != null && k.targetShip().equals(ship.ship().id()), "the kraken did not target the near ship");
            h.assertTrue(maxGrips[0] >= 2, "the kraken gripped with only " + maxGrips[0] + " tentacles");
            h.assertTrue(maxGrips[0] <= KrakenConfig.MAX_GRIPS.get(), "more grips than max_grips: " + maxGrips[0]);
            h.assertTrue(k.mastBlocksBroken() == 0, "the kraken broke a mast although mobGriefing is off");
            h.assertTrue(sink < controlSink - 0.3, "the ship was not pulled down: dy " + sink + " vs control " + controlSink);
            h.succeed();
        });
    }

    /** Cutting a gripping tentacle releases its grip at once. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "cut_grip")
    public static void cuttingAGrippingTentacleReleasesIt(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        targetShip(h);
        Kraken k = KrakenTestSupport.kraken(h, KRAKEN_BY_SHIP);
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0]) return;
            for (int i = 0; i < KrakenTentacles.COUNT; i++) {
                if (k.tentacles().job(i) == Job.GRAB_HULL && k.inContact(i)) {
                    KrakenPart t = k.part(i);
                    int grips = k.gripping();
                    t.hurt(k.damageSources().generic(), (float) (KrakenConfig.TENTACLE_HEALTH.get() + 1));
                    h.assertTrue(k.tentacles().isCut(i), "not cut");
                    h.assertTrue(k.tentacles().job(i) == Job.NONE && !k.inContact(i), "the cut tentacle still grips");
                    h.assertTrue(k.gripping() == grips - 1, "grips " + grips + " -> " + k.gripping());
                    done[0] = true;
                    h.succeed();
                    return;
                }
            }
        });
    }

    /** A tentacle within reach breaks a mast block within the strike interval. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 260, batch = BATCH + "mast")
    public static void mastStrikeBreaksAMastBlock(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        Fixture ship = targetShip(h);
        int before = masts(h, ship.ship());
        h.assertTrue(before > 0, "the test ship has no mast");
        Kraken k = KrakenTestSupport.kraken(h, KRAKEN_BY_SHIP);
        h.succeedWhen(() -> {
            h.assertTrue(k.mastBlocksBroken() >= 1, "no mast block broken yet");
            int now = masts(h, ship.ship());
            h.assertTrue(now < before, "mast blocks " + before + " -> " + now);
            Constants.LOG.info("[kraken test] mast: {} -> {} mast blocks at tick {}", before, now, h.getTick());
        });
    }

    /** Someone standing on deck is swiped off it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 260, batch = CONFIG_BATCH + "swipe")
    public static void swipeKnocksAPlayerOffTheDeck(GameTestHelper h) {
        noMastStrikes(h);
        SailingGameTestsShips.basin(h, true);
        // the deck plank at the bow's starboard corner (free of the helm and the rig)
        BlockPos deckRel = new BlockPos(SHIP_X + 1, 8, SHIP_Z + 3);
        Vec3 deckWorld = Vec3.atCenterOf(h.absolutePos(deckRel));
        Fixture ship = targetShip(h);
        Vec3 plotDeck = ship.ship().toPlot(deckWorld);
        Player[] player = new Player[1];
        Kraken[] kraken = new Kraken[1];
        float[] health = new float[1];
        h.runAfterDelay(10, () -> {
            Vec3 top = ship.ship().toWorld(plotDeck.add(0, 0.55, 0));
            player[0] = KrakenTestSupport.playerInLevel(h, h.relativeVec(top));
            health[0] = player[0].getHealth();
        });
        h.runAfterDelay(30, () -> {
            Vec3 c = ship.ship().worldBounds().getCenter();
            h.assertTrue(horizontal(player[0].position(), c) < 2.6, "the player is not on the deck before the kraken came: "
                    + horizontal(player[0].position(), c));
            kraken[0] = KrakenTestSupport.kraken(h, KRAKEN_BY_SHIP);
        });
        h.runAfterDelay(31, () -> h.succeedWhen(() -> {
            Kraken k = kraken[0];
            h.assertTrue(k.swipes() >= 1, "no swipe yet");
            double off = horizontal(player[0].position(), ship.ship().worldBounds().getCenter());
            h.assertTrue(off > 3.5 || player[0].isInWater(), "the player is still on deck, " + off + " from the centre");
            h.assertTrue(player[0].getHealth() < health[0], "the swipe did no damage");
            Constants.LOG.info("[kraken test] swipe: off the deck at tick {}, {} from the centre", h.getTick(), off);
        }));
    }

    /** At 30 % health the kraken lets go of everything and retreats; the retreat ends with it leaving the world. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 400, batch = BATCH + "retreat")
    public static void retreatsAndReleasesBelowThirtyPercent(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        targetShip(h);
        Kraken k = KrakenTestSupport.kraken(h, KRAKEN_BY_SHIP);
        long[] hurtAt = {-1};
        h.onEachTick(() -> {
            if (hurtAt[0] < 0 && k.gripping() > 0) {
                k.setHealth((float) (k.getMaxHealth() * 0.29));
                hurtAt[0] = h.getTick();
            } else if (hurtAt[0] >= 0 && h.getTick() == hurtAt[0] + 2) {
                h.assertTrue(k.brain().state() == KrakenBrain.State.RETREAT, "not retreating: " + k.brain().state());
                h.assertTrue(k.gripping() == 0 && k.tentacles().free().size() == KrakenTentacles.COUNT, "still holding on");
            } else if (hurtAt[0] >= 0 && h.getTick() > hurtAt[0] + KrakenBrain.RETREAT_TICKS + 5) {
                h.assertTrue(k.isRemoved(), "the kraken did not leave after its retreat");
                Constants.LOG.info("[kraken test] retreat: hurt at tick {}, gone by tick {}", hurtAt[0], h.getTick());
                h.succeed();
            }
        });
    }

    /** {@code peaceful}: next to a ship the kraken stays in the deep and touches nothing. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 160, batch = CONFIG_BATCH + "peaceful")
    public static void peacefulKrakenStaysPassive(GameTestHelper h) {
        ConfigOverrides.during(h, KrakenConfig.PEACEFUL, true);
        SailingGameTestsShips.basin(h, true);
        Fixture ship = targetShip(h);
        int before = masts(h, ship.ship());
        Kraken k = KrakenTestSupport.kraken(h, KRAKEN_BY_SHIP);
        h.onEachTick(() -> {
            if (k.brain().state() != KrakenBrain.State.LURK) h.fail("a peaceful kraken left the deep: " + k.brain().state());
            if (k.gripping() > 0) h.fail("a peaceful kraken gripped the ship");
        });
        h.runAfterDelay(140, () -> {
            h.assertTrue(k.mastBlocksBroken() == 0 && masts(h, ship.ship()) == before, "a peaceful kraken broke the mast");
            h.assertTrue(k.targetShip() == null, "a peaceful kraken targets a ship");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ swimmers

    /** A swimmer near the kraken is grabbed, dragged under and hurt. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 260, batch = BATCH + "swimmer")
    public static void swimmerIsDraggedUnder(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        Vec3 swim = new Vec3(20.5, 6.2, 20.5);
        Player player = KrakenTestSupport.playerInLevel(h, swim);
        float health = player.getHealth();
        Vec3 at = h.absoluteVec(swim);
        Kraken k = KrakenTestSupport.kraken(h, new Vec3(28.5, 2.1, 20.5));
        double[] grabbedY = {Double.NaN};
        long[] grabbedAt = {-1};
        double[] lowest = {Double.MAX_VALUE};
        h.onEachTick(() -> {
            if (grabbedAt[0] < 0) {
                if (k.holding(player)) {
                    grabbedAt[0] = h.getTick();
                    grabbedY[0] = player.getY();
                } else {
                    player.setPos(at.x, at.y, at.z); // treads water until grabbed
                    player.setDeltaMovement(Vec3.ZERO);
                }
                return;
            }
            lowest[0] = Math.min(lowest[0], player.getY());
            if (h.getTick() >= grabbedAt[0] + 45) {
                Constants.LOG.info("[kraken test] swimmer: grabbed at y {}, lowest {}, health {} -> {}", grabbedY[0], lowest[0],
                        health, player.getHealth());
                h.assertTrue(lowest[0] < grabbedY[0] - 1.5, "the swimmer was not dragged under: " + grabbedY[0] + " -> " + lowest[0]);
                h.assertTrue(player.getHealth() < health, "the hold did no damage");
                h.succeed();
            }
        });
    }

    // ------------------------------------------------------------------ toggles, command, loot, spawner

    /** {@code hazards.kraken.enabled} off: an existing kraken disappears and the command refuses. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 40, batch = CONFIG_BATCH + "disabled")
    public static void disabledKrakenDisappearsAndTheCommandRefuses(GameTestHelper h) {
        ConfigOverrides.during(h, HazardsConfig.KRAKEN_ENABLED, false);
        SailingGameTestsShips.basin(h, true);
        Vec3 at = new Vec3(20.5, 3, 20.5);
        int result = command(h, at, "pirates mob spawn kraken");
        h.assertTrue(result == 0, "the command did not refuse: " + result);
        h.assertTrue(krakensNear(h, at, 4).isEmpty(), "the refused command left a kraken");
        Kraken k = KrakenTestSupport.kraken(h, at);
        h.runAfterDelay(3, () -> {
            h.assertTrue(k.isRemoved(), "the kraken stayed although it is disabled");
            h.assertTrue(partsOf(h, k).isEmpty(), "its parts stayed");
            h.succeed();
        });
    }

    /** {@code /pirates mob spawn kraken} places one; its loot table is registered and holds the beak and the ink. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 40, batch = BATCH + "command")
    public static void commandSpawnsAKrakenWithItsLootTable(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        Vec3 at = new Vec3(20.5, 3, 20.5);
        int result = command(h, at, "pirates mob spawn kraken");
        List<Kraken> found = krakensNear(h, at, 4);
        found.forEach(k -> KrakenTestSupport.onEnd(h, k::discard));
        h.assertTrue(result == 1 && found.size() == 1, "expected one kraken, command " + result + ", found " + found.size());
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Constants.id("entities/" + KrakenContent.ID));
        h.assertTrue(found.get(0).getLootTable().equals(key), "the kraken's loot table is " + found.get(0).getLootTable());
        LootTable table = h.getLevel().getServer().reloadableRegistries().getLootTable(key);
        h.assertTrue(table != LootTable.EMPTY, "the kraken loot table is not registered");
        h.assertTrue(found.get(0).getMaxHealth() == 300f && found.get(0).getArmorValue() == 8, "health or armour");
        h.succeed();
    }

    /**
     * With a chance of one, a kraken appears for a player in the deep ocean at a spot asked for 48-96 blocks away, but
     * none outside the deep ocean and none within {@code min_separation} of another kraken. The test site places the
     * kraken in the test area whatever spot was asked for: entities added in a chunk that is loaded but not entity-loaded
     * are invisible to entity lookups, so a kraken placed out there was missed by the separation check (once in three
     * runs).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 40, batch = CONFIG_BATCH + "spawner")
    public static void spawnerPlacesAKrakenButKeepsThemApart(GameTestHelper h) {
        ConfigOverrides.during(h, HazardsConfig.KRAKEN_CHANCE_PER_DAY, 1.0);
        ServerLevel level = h.getLevel();
        Player player = KrakenTestSupport.playerInLevel(h, new Vec3(20.5, 3, 20.5));
        Vec3 place = h.absoluteVec(new Vec3(30.5, 3, 30.5));
        List<Vec3> asked = new ArrayList<>();
        Kraken first = KrakenSpawner.trySpawn(level, player, testSite(false, place, asked), level.getRandom());
        if (first != null) first.discard();
        h.assertTrue(first == null, "a kraken appeared outside the deep ocean");
        KrakenSpawner.Site deep = testSite(true, place, asked);
        Kraken spawned = KrakenSpawner.trySpawn(level, player, deep, level.getRandom());
        h.assertTrue(spawned != null, "no kraken appeared");
        KrakenTestSupport.onEnd(h, spawned::discard);
        h.assertTrue(!asked.isEmpty(), "the spawner asked for no spot");
        double d = horizontal(asked.get(asked.size() - 1), player.position());
        h.assertTrue(d >= HazardsConfig.SPAWN_MIN_DISTANCE.get() - 1e-6 && d <= HazardsConfig.SPAWN_MAX_DISTANCE.get() + 1e-6,
                "the spawner asked for a spot " + d + " blocks away");
        h.assertTrue(spawned.position().distanceTo(place) < 1e-6, "the kraken is not where the site put it");
        Kraken second = KrakenSpawner.trySpawn(level, player, deep, level.getRandom());
        if (second != null) second.discard();
        h.assertTrue(second == null, "a second kraken appeared within " + KrakenConfig.MIN_SEPARATION.get() + " blocks of the first");
        h.succeed();
    }

    /** A test spawn site: deep ocean or not; records each spot asked for and answers {@code place}. */
    private static KrakenSpawner.Site testSite(boolean deep, Vec3 place, List<Vec3> asked) {
        return new KrakenSpawner.Site() {
            @Override public boolean inDeepOcean(ServerLevel level, Player player) { return deep; }
            @Override public @Nullable Vec3 deepWater(ServerLevel level, double x, double z, int minDepth) {
                asked.add(new Vec3(x, place.y, z));
                return place;
            }
        };
    }
}

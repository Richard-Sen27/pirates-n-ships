package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.combat.boarding.PlankRun;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.DelayInstruction;
import net.createmod.ponder.foundation.instruction.KeyframeInstruction;
import net.createmod.ponder.foundation.instruction.PonderInstruction;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.createmod.ponder.foundation.registration.DefaultPonderSceneRegistrationHelper;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every storyboard of {@link AlmanacScenes#ALL} built headless the way Ponder builds it
 * ({@code PonderSceneRegistry.compileScene} with no level, PND1): it runs without an exception, gives itself the
 * scene's id, lasts 20 to 40 seconds of idles, has keyframes, and uses every line of its text. The scenes themselves
 * play only in a client (docs/playtests/ponder.md).
 */
class PonderStoryboardsTest {

    private static final String MOD = "pirates_n_ships";

    @BeforeAll
    static void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<Map.Entry<ResourceLocation, StoryBoardEntry>> entries() {
        PonderSceneRegistry registry = new PonderSceneRegistry(new PonderLocalization());
        new AlmanacPonderPlugin().registerScenes(new DefaultPonderSceneRegistrationHelper(MOD, registry));
        return List.copyOf(registry.getRegisteredEntries());
    }

    private static StoryBoardEntry entryOf(AlmanacScenes.Scene scene) {
        return entries().stream().map(Map.Entry::getValue)
                .filter(e -> e.getSchematicLocation().equals(scene.schematic())).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<PonderInstruction> schedule(PonderScene scene) throws ReflectiveOperationException {
        Field f = PonderScene.class.getDeclaredField("schedule");
        f.setAccessible(true);
        return (List<PonderInstruction>) f.get(scene);
    }

    private static int ticks(TickingInstruction instruction) throws ReflectiveOperationException {
        Field f = TickingInstruction.class.getDeclaredField("totalTicks");
        f.setAccessible(true);
        return f.getInt(instruction);
    }

    @Test
    void everyStoryboardBuildsWithItsIdLengthAndKeyframes() throws ReflectiveOperationException {
        StringBuilder lengths = new StringBuilder();
        StringBuilder wrong = new StringBuilder();
        for (AlmanacScenes.Scene s : AlmanacScenes.ALL) {
            PonderScene scene = PonderSceneRegistry.compileScene(new PonderLocalization(), entryOf(s), null);
            assertEquals(s.id(), scene.getId(), "the storyboard's title() gives it the scene id");
            int idle = 0;
            int keyframes = 0;
            for (PonderInstruction i : schedule(scene)) {
                if (i instanceof DelayInstruction d) idle += ticks(d);
                if (i instanceof KeyframeInstruction) keyframes++;
            }
            int textKeyframes = keyframesOfText(s);
            lengths.append(s.id().getPath()).append(' ').append(idle).append(' ');
            if (idle < 400 || idle > 800) wrong.append(s.id()).append(" lasts ").append(idle).append(" ticks; ");
            assertTrue(keyframes + textKeyframes >= 3, s.id() + " has " + (keyframes + textKeyframes) + " keyframes");
        }
        System.out.println("Ponder scene lengths [ticks]: " + lengths);
        assertEquals("", wrong.toString(), "every scene lasts 20 to 40 s");
    }

    /** attachKeyFrame() on a text window schedules no KeyframeInstruction; count them in the source instead. */
    private static int keyframesOfText(AlmanacScenes.Scene s) {
        try {
            String src = source(s);
            int n = 0;
            for (int i = src.indexOf(".attachKeyFrame()"); i >= 0; i = src.indexOf(".attachKeyFrame()", i + 1)) n++;
            return n;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static String source(AlmanacScenes.Scene s) throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("common/src/main/java"))) {
            root = root.getParent();
        }
        assertNotNull(root, "repository root not found");
        String board = AlmanacPonderPlugin.STORYBOARDS.get(s.id()).getClass().getName();
        // a method reference's class is synthetic; the storyboard class is named after the scene in camel case
        String name = camel(s.id().getPath()) + "Scene";
        Path file = root.resolve("common/src/main/java/com/richardsenger/piratesnships/almanac/ponder/" + name + ".java");
        assertTrue(Files.exists(file), "no storyboard source " + file + " (" + board + ")");
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static String camel(String path) {
        StringBuilder b = new StringBuilder();
        for (String part : path.split("_")) {
            b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return b.toString();
    }

    /** Every line of a scene's text is said in its storyboard (a line written but never shown is a lie by omission). */
    @Test
    void everyLineOfTheTextIsUsedByItsStoryboard() throws IOException {
        for (AlmanacScenes.Scene s : AlmanacScenes.ALL) {
            String src = source(s);
            SceneText text = SceneText.forScene(s.id().getPath()).orElseThrow();
            for (SceneLine line : text.lines()) {
                String enumName = ((Enum<?>) line).getDeclaringClass().getSimpleName();
                assertTrue(src.contains(enumName + "." + line.name() + ")") || src.contains("Line." + line.name() + ")"),
                        s.id() + " never says " + enumName + "." + line.name());
            }
        }
    }

    @Test
    void theAnchorHangsWhereTheHullSideRulePutsIt() {
        // capstan one block to port of the centre line of a five-wide hull (PND3): the nearer side is port (west), the
        // first cell outside x 1, where the default camera from the north-west sees it
        Vec3 h = AnchorAndCapstanScene.HAWSE;
        assertEquals(0.6, h.x, 1e-9);
        assertEquals(5.75, h.y, 1e-9);
        assertEquals(4.5, h.z, 1e-9);
        // 2.75 blocks from the stowed crown to the seabed: 14 ticks at 4 blocks/s, 22 at 2.5
        assertEquals(14, AnchorAndCapstanScene.travelTicks(AnchorAndCapstanScene.SINK_SPEED));
        assertEquals(22, AnchorAndCapstanScene.travelTicks(AnchorAndCapstanScene.RAISE_SPEED));
    }

    @Test
    void theRudderLineIsTheWheelsRudderAngle() {
        assertEquals(270.0, SteeringScene.LOCK, 1e-9, "three quarters of a turn each way");
        assertEquals(35.0, net.minecraft.util.Mth.wrapDegrees(
                com.richardsenger.piratesnships.sailing.helm.WheelMath.rudderAngle(SteeringScene.LOCK, SteeringScene.LOCK,
                        SteeringScene.MAX_RUDDER)), 1e-9);
        Vec3 tip = SteeringScene.rudderTip(Vec3.ZERO, 35);
        assertTrue(tip.x > 0 && tip.z > 0, "a starboard turn swings the rudder's trailing edge to starboard (east), aft");
    }

    /** PND2: the private fields the scenes write through ({@link SceneSync}) exist with the kind the scenes expect. */
    @Test
    void everySyncedFieldTheScenesUseResolves() throws ReflectiveOperationException {
        for (SceneSync.Ref ref : SceneSync.FIELDS) {
            Field f = ref.owner().getDeclaredField(ref.name()); // no class initialisation
            assertTrue(java.lang.reflect.Modifier.isStatic(f.getModifiers()), ref + " is static");
            boolean accessor = net.minecraft.network.syncher.EntityDataAccessor.class.isAssignableFrom(f.getType());
            assertTrue(accessor || f.getType() == String.class, ref + " is a data accessor or a tag key");
        }
        assertEquals(String.class, SceneSync.FLAGPOLE_STATE.owner().getDeclaredField(SceneSync.FLAGPOLE_STATE.name()).getType());
        assertEquals(String.class, SceneSync.BELL_RING_START.owner().getDeclaredField(SceneSync.BELL_RING_START.name()).getType());
        assertEquals(String.class, SceneSync.BELL_STRIKE.owner().getDeclaredField(SceneSync.BELL_STRIKE.name()).getType());
    }

    @Test
    void theLookoutsCallIsTwoPointsOffTheStarboardBow() {
        // bow north (compass 0); the drawn direction is what Bearings calls "two points off the starboard bow"
        Vec3 d = CrowsNestScene.callDirection();
        com.richardsenger.piratesnships.station.lookout.Bearings.Relative r =
                com.richardsenger.piratesnships.station.lookout.Bearings.of(0.0, d.x, d.z);
        assertEquals(2, r.points());
        assertEquals(com.richardsenger.piratesnships.station.lookout.Bearings.Side.STARBOARD, r.side());
        assertEquals(com.richardsenger.piratesnships.station.lookout.Bearings.Sector.BOW, r.sector());
        assertEquals(140, com.richardsenger.piratesnships.station.lookout.Bearings.calledDistance(CrowsNestScene.CALL_DISTANCE));
        // LKO2: the scene quotes the action-bar cue the lookout really shows for that sighting
        String cue = com.richardsenger.piratesnships.station.lookout.SightingCue.text(
                com.richardsenger.piratesnships.station.lookout.SightingPayload.What.SAIL, "", 2 * 11.25, 140,
                k -> com.richardsenger.piratesnships.station.lookout.LookoutLang.ENGLISH.getOrDefault(k, k));
        assertEquals("Sail two points to starboard, 140 blocks", cue);
        assertTrue(CrewSceneText.Nest.CALL.english().contains(cue));
    }

    @Test
    void theOrderWheelIsTheWhistlesMenu() {
        // entry 0 (hoist) straight above the centre, the next one clockwise (to the right, as seen from the south)
        Vec3 first = CrewOrdersScene.wheelSlot(0);
        assertEquals(CrewOrdersScene.WHEEL.x, first.x, 1e-9);
        assertEquals(CrewOrdersScene.WHEEL.y + CrewOrdersScene.WHEEL_RADIUS, first.y, 1e-9);
        assertTrue(CrewOrdersScene.wheelSlot(1).x > CrewOrdersScene.WHEEL.x, "clockwise from the top");
        assertEquals(10, com.richardsenger.piratesnships.station.order.WhistleOrder.entries().size(),
                "the scene names fire at will and release crew; update its text if the wheel changes");
    }

    @Test
    void thePlankIsTheRunTheRuleLaysForThisGap() {
        // clicked on the top of our gunwale, facing east (her side); her hull hauled alongside stands at x 6..9, its
        // gunwale on y 3; the rest is open water. BRD4's rule with the default settings lays a level two-cell board
        // resting on her rail (both rails are on y 3).
        PlankRun.Start start = PlankRun.start(BoardingScene.GUNWALE, net.minecraft.core.Direction.UP, net.minecraft.core.Direction.EAST);
        int herRailX = 9 - BoardingScene.HAUL;
        PlankRun.Probe probe = new PlankRun.Probe() {
            @Override
            public boolean free(int i, int dy) {
                return ground(i, dy) == PlankRun.Ground.NONE;
            }

            @Override
            public boolean open(int i, int dy) {
                return ground(i, dy) == PlankRun.Ground.NONE;
            }

            @Override
            public PlankRun.Ground ground(int i, int dy) {
                net.minecraft.core.BlockPos cell = start.cell(i, dy);
                boolean hers = cell.getX() >= herRailX && cell.getX() <= herRailX + 3 && cell.getY() >= 1 && cell.getY() <= 3;
                return hers ? PlankRun.Ground.STURDY : PlankRun.Ground.NONE;
            }
        };
        PlankRun.Result run = PlankRun.find(new PlankRun.Settings(6, 45.0), probe);
        assertTrue(run.found());
        assertEquals(BoardingScene.RUN.size(), run.length());
        for (int i = 0; i < run.length(); i++) {
            assertEquals(BoardingScene.RUN.get(i), start.cell(i, run.cells().get(i).dy()));
            assertEquals(0.0, run.cells().get(i).near(), 1e-9);
            assertEquals(0.0, run.cells().get(i).far(), 1e-9);
        }
        assertEquals(PlankRun.Landing.LEVEL, run.landing());
        assertEquals(0, run.rise());
    }

    @Test
    void theSwivelScenesAimStaysInTheGunsLimits() {
        for (double[] a : SwivelGunScene.SWEEP) {
            assertTrue(a[1] >= SwivelGunScene.MIN_ELEVATION && a[1] <= SwivelGunScene.MAX_ELEVATION, "elevation " + a[1]);
        }
        assertEquals(180.0, SwivelGunScene.SWEEP[0][0], "it starts on the bow (north)");
        Vec3 m = SwivelGunScene.muzzle();
        assertTrue(m.z < SwivelGunScene.GUN.getZ() + 0.5, "the muzzle points over the bow, out of the scene's deck");
    }

    @Test
    void theGalleyAndHammockScenesUseTheGamesNumbers() {
        // two water buckets of 3 rations in a 16-ration barrel: fill level 2 of 4 (1 after the first)
        assertEquals(1, GalleyAndProvisionsScene.fillAfterBuckets(1));
        assertEquals(2, GalleyAndProvisionsScene.fillAfterBuckets(2));
        assertEquals(2, GalleyAndProvisionsScene.mealSpots().size());
        // the sleeper lies on the canvas where foot and head meet, along the hammock
        Vec3 bunk = HammocksAndRestScene.bunk(HammocksAndRestScene.FEET.get(0));
        assertEquals(3.0, bunk.x, 1e-9);
        assertEquals(2.5, bunk.z, 1e-9);
        assertTrue(bunk.y > 1.0 && bunk.y < 1.5, "on the canvas");
    }

    @Test
    void thePolesOnTheirFootingsShowTheGamesParts() {
        // the schematics' pole parts are what FlagpoleFoot gives on a pedestal and on a crow's nest (FLG3)
        assertEquals(com.richardsenger.piratesnships.ship.decor.flag.FlagpolePart.MIDDLE,
                com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFoot.part(
                        com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFoot.Support.PEDESTAL, true));
        assertEquals(com.richardsenger.piratesnships.ship.decor.flag.FlagpolePart.TOP,
                com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFoot.part(
                        com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFoot.Support.CROWS_NEST, false));
    }

    @Test
    void theSteeringTextNamesNoCommand() {
        for (SceneLine line : SceneText.allLines()) {
            assertTrue(!line.english().contains("/pirates"), line.key() + " names a command");
        }
    }

    @Test
    void aFullCrateWeighsSomeFortyPlanks() {
        // Sable's plank weighs 0.5 kpg (CargoMass); ShipSceneText.Cargo.FULL says "some forty planks"
        double planks = com.richardsenger.piratesnships.trade.cargo.CargoMass.CRATE.fullExtraMass() / 0.5;
        assertTrue(planks >= 38 && planks <= 44, "a full crate is " + planks + " planks");
        assertTrue(ShipSceneText.Cargo.FULL.english().contains("forty planks"));
    }
}

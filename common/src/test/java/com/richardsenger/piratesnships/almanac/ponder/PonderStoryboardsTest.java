package com.richardsenger.piratesnships.almanac.ponder;

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
        // capstan on the centre line of a five-wide hull: a tie, so starboard (east), first cell outside x 5
        Vec3 h = AnchorAndCapstanScene.HAWSE;
        assertEquals(6.4, h.x, 1e-9);
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

    @Test
    void aFullCrateWeighsSomeFortyPlanks() {
        // Sable's plank weighs 0.5 kpg (CargoMass); ShipSceneText.Cargo.FULL says "some forty planks"
        double planks = com.richardsenger.piratesnships.trade.cargo.CargoMass.CRATE.fullExtraMass() / 0.5;
        assertTrue(planks >= 38 && planks <= 44, "a full crate is " + planks + " planks");
        assertTrue(ShipSceneText.Cargo.FULL.english().contains("forty planks"));
    }
}

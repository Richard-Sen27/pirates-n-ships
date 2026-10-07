package com.richardsenger.piratesnships.audio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.audio.creak.ShipCreaks;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.force.ShipFrame;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import com.richardsenger.piratesnships.combat.content.CombatSounds;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.sailing.anchor.AnchorContent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** GameTests of the audio module: the creak sound event and its definition, and creaking ships. */
public final class AudioGameTests {

    private AudioGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(AudioGameTests.class);
    }

    @ModGameTest
    public static void creakSoundIsRegisteredAndDefined(GameTestHelper h) {
        h.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(AudioSounds.SHIP_CREAK.id()),
                "sound event " + AudioSounds.SHIP_CREAK.id() + " is not registered");
        try (InputStream in = AudioGameTests.class.getResourceAsStream("/assets/pirates_n_ships/sounds.json")) {
            h.assertTrue(in != null, "assets/pirates_n_ships/sounds.json is missing (run ./gradlew :neoforge:runData)");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            String key = AudioSounds.SHIP_CREAK.id().getPath();
            h.assertTrue(root.has(key), "sounds.json does not list " + key);
            JsonObject e = root.getAsJsonObject(key);
            h.assertTrue(e.getAsJsonArray("sounds").size() > 0, "ship.creak has no sounds");
            h.assertTrue(e.getAsJsonArray("sounds").size() == AudioModule.CREAK_SOUNDS.size(),
                    "ship.creak lists " + e.getAsJsonArray("sounds") + ", expected " + AudioModule.CREAK_SOUNDS);
            for (com.google.gson.JsonElement s : e.getAsJsonArray("sounds")) {
                String name = s.isJsonPrimitive() ? s.getAsString() : s.getAsJsonObject().get("name").getAsString();
                h.assertTrue(AudioModule.CREAK_SOUNDS.contains(name) && name.startsWith("pirates_n_ships:ship/creak"),
                        "ship.creak still plays a placeholder: " + name);
            }
            h.assertTrue(AudioModule.CREAK_SUBTITLE.equals(e.get("subtitle").getAsString()), "ship.creak has the wrong subtitle");
        } catch (java.io.IOException ex) {
            throw new AssertionError("could not read sounds.json", ex);
        }
        h.succeed();
    }

    /** The G1 sound events (music pools, combat, anchor) are registered and listed in the shared sounds.json. */
    @ModGameTest
    public static void sharedSoundEventsAreRegisteredAndDefined(GameTestHelper h) {
        List<RegistryEntry<SoundEvent, SoundEvent>> events = new java.util.ArrayList<>(List.of(AudioSounds.MUSIC_SEA,
                AudioSounds.MUSIC_SHANTY, AnchorContent.CHAIN, AnchorContent.SPLASH, AnchorContent.THUD));
        CombatSounds.ALL.forEach(d -> events.add(d.event()));
        h.assertTrue(events.size() == 17, "expected 17 shared sound events, got " + events.size());
        try (InputStream in = AudioGameTests.class.getResourceAsStream("/assets/pirates_n_ships/sounds.json")) {
            h.assertTrue(in != null, "assets/pirates_n_ships/sounds.json is missing (run ./gradlew :neoforge:runData)");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (RegistryEntry<SoundEvent, SoundEvent> e : events) {
                h.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(e.id()), "sound event " + e.id() + " is not registered");
                h.assertTrue(BuiltInRegistries.SOUND_EVENT.get(e.id()) == e.get(), "sound event " + e.id() + " is a different instance");
                h.assertTrue(root.has(e.id().getPath()), "sounds.json does not list " + e.id().getPath());
                h.assertTrue(root.getAsJsonObject(e.id().getPath()).getAsJsonArray("sounds").size() > 0, e.id() + " has no sounds");
            }
            JsonObject pistol = root.getAsJsonObject(CombatSounds.PISTOL_SHOT.id().getPath());
            h.assertTrue("subtitles.pirates_n_ships.combat.pistol_shot".equals(pistol.get("subtitle").getAsString()),
                    "combat.pistol_shot has the wrong subtitle");
            h.assertTrue(!root.getAsJsonObject(AudioSounds.MUSIC_SEA.id().getPath()).has("subtitle"), "music has no subtitle");
            h.assertTrue(root.getAsJsonObject(CombatSounds.MELEE_CLASH.id().getPath()).getAsJsonArray("sounds").size() == 6,
                    "combat.melee.clash should have six variants");
        } catch (java.io.IOException ex) {
            throw new AssertionError("could not read sounds.json", ex);
        }
        h.succeed();
    }

    private static void pinCreaks(GameTestHelper h) {
        ConfigOverrides.during(h, CreakConfig.ENABLED, true);
        ConfigOverrides.during(h, CreakConfig.CREAKS_PER_SECOND, 10.0);
        ConfigOverrides.during(h, CreakConfig.MIN_INTERVAL_TICKS, 5);
        ConfigOverrides.during(h, CreakConfig.ROLL_RATE_THRESHOLD, 0.05);
        ConfigOverrides.during(h, SailingConfig.HULL_DAMPING_ENABLED, true);
    }

    /** A ship kicked into rolling every two seconds creaks, and never more often than the minimum interval allows. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_config_audio_creak_rolling")
    public static void rollingShipCreaks(GameTestHelper h) {
        pinCreaks(h);
        SailingGameTestsShips.basin(h, true);
        SailingGameTestsShips.Fixture f = SailingGameTestsShips.assemble(h, SailingGameTestsShips.longHull(h));
        int[] before = new int[1];
        h.onEachTick(() -> {
            long t = h.getTick();
            if (f.ship().isRemoved()) return;
            if (t == 60) before[0] = ShipCreaks.played(f.ship().id());
            if (t >= 60 && t < 260 && t % 40 == 0) {
                Quaterniond q = f.runtime().bow().shipToWorld(f.ship().orientation(new Quaterniond()), new Quaterniond());
                f.ship().addVelocity(new Vector3d(), q.transform(new Vector3d(ShipFrame.FORWARD)).mul(0.6));
            }
        });
        h.runAfterDelay(260, () -> {
            int n = ShipCreaks.played(f.ship().id()) - before[0];
            // 200 ticks with a 5 tick minimum interval: at most 40
            h.assertTrue(n > 0, "a rolling ship did not creak");
            h.assertTrue(n <= 200 / 5, "creaks broke the minimum interval: " + n);
            h.succeed();
        });
    }

    /** A ship lying still in calm water never creaks. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_config_audio_creak_still")
    public static void stillShipIsSilent(GameTestHelper h) {
        pinCreaks(h);
        SailingGameTestsShips.basin(h, true);
        BlockPos helm = SailingGameTestsShips.squareHull(h, 17, 17, SailTrim.FURLED);
        SailingGameTestsShips.ballast(h, 17, 17);
        SailingGameTestsShips.Fixture f = SailingGameTestsShips.assemble(h, helm);
        int[] before = new int[1];
        h.runAfterDelay(100, () -> before[0] = ShipCreaks.played(f.ship().id()));
        h.runAfterDelay(260, () -> {
            int n = ShipCreaks.played(f.ship().id()) - before[0];
            h.assertTrue(n == 0, "a ship at rest creaked " + n + " times");
            h.succeed();
        });
    }
}

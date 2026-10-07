package com.richardsenger.piratesnships.combat.melee.sound;

import com.richardsenger.piratesnships.combat.content.CombatSounds;
import com.richardsenger.piratesnships.combat.melee.MeleeConfig;
import com.richardsenger.piratesnships.combat.melee.MeleeService;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.combat.melee.weapon.DefaultWeapons;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Sword sounds on real entities (P7): every transition point of {@code MeleeService} plays its cue at the right
 * entity. Sounds are recorded per entity ({@link MeleeSoundPlayer#record}), so these tests can share a batch with
 * other melee tests; the config tests run alone in their batches. Pillagers without AI stand in for duelists as in
 * {@code MeleeGameTests}; yaw -90 faces +X, yaw 90 faces -X.
 */
public final class MeleeSoundGameTests {

    private MeleeSoundGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(MeleeSoundGameTests.class);
    }

    private static Mob duelist(GameTestHelper helper, int x, int z, float yaw) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.PILLAGER, x, 1, z);
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.yBodyRot = yaw;
        mob.setXRot(0);
        mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        return mob;
    }

    /** Records the sounds played at the entity; stopped by {@link #done}. */
    private static List<MeleeSoundPlayer.Played> rec(Entity e) {
        return MeleeSoundPlayer.record(e.getId());
    }

    private static void done(GameTestHelper helper, Entity... entities) {
        for (Entity e : entities) MeleeSoundPlayer.stopRecording(e.getId());
        helper.succeed();
    }

    private static List<MeleeSoundPlayer.Played> of(List<MeleeSoundPlayer.Played> rec, MeleeSoundRules.Cue cue) {
        return rec.stream().filter(p -> p.cue().equals(cue)).toList();
    }

    private static List<MeleeSoundPlayer.Played> ofSound(List<MeleeSoundPlayer.Played> rec, MeleeSoundRules.Sound sound) {
        return rec.stream().filter(p -> p.cue().sound() == sound).toList();
    }

    private static void assertAt(GameTestHelper helper, MeleeSoundPlayer.Played p, Entity e, String what) {
        helper.assertTrue(p.emitterId() == e.getId(), what + " played at entity " + p.emitterId() + ", expected " + e.getId());
        // played at the entity's position of that moment; knockback and falling move it a little until the check
        helper.assertTrue(e.position().add(0, e.getBbHeight() * 0.6, 0).distanceTo(new net.minecraft.world.phys.Vec3(p.x(), p.y(), p.z())) < 3.0,
                what + " played at " + p.x() + " " + p.y() + " " + p.z() + ", entity at " + e.position());
        helper.assertTrue(p.source() == (e instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE), what + " source " + p.source());
        MeleeSoundRules.Cue c = p.cue();
        double scale = MeleeConfig.SOUNDS_VOLUME.get();
        helper.assertTrue(p.volume() >= c.minVolume() * scale - 1e-4 && p.volume() <= c.maxVolume() * scale + 1e-4, what + " volume " + p.volume());
        helper.assertTrue(p.pitch() >= c.minPitch() - 1e-4 && p.pitch() <= c.maxPitch() + 1e-4, what + " pitch " + p.pitch());
    }

    private static MeleeSoundPlayer.Played only(GameTestHelper helper, List<MeleeSoundPlayer.Played> list, String what) {
        helper.assertTrue(list.size() == 1, "expected one " + what + ", got " + list);
        return list.get(0);
    }

    /** A slash plays the swing at the attacker when its hit frames begin, then the flesh hit at the target. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void slashSwingsThenHitsFlesh(GameTestHelper helper) {
        Mob attacker = duelist(helper, 1, 4, -90);
        Mob target = duelist(helper, 3, 4, 90);
        List<MeleeSoundPlayer.Played> a = rec(attacker), t = rec(target);
        helper.assertTrue(MeleeService.startSlash(attacker, DefaultWeapons.CUTLASS).accepted(), "slash accepted");
        helper.assertTrue(a.isEmpty(), "the wind-up is silent: " + a);
        helper.runAfterDelay(15, () -> {
            MeleeSoundPlayer.Played swing = only(helper, a, "swing at the attacker");
            helper.assertTrue(swing.cue().equals(MeleeSoundRules.SWING_SLASH), "slash swing cue: " + swing.cue());
            helper.assertTrue(swing.event() == CombatSounds.MELEE_SWING.get(), "swing event " + swing.event());
            assertAt(helper, swing, attacker, "swing");
            MeleeSoundPlayer.Played hit = only(helper, t, "hit at the target");
            helper.assertTrue(hit.cue().equals(MeleeSoundRules.HIT_FLESH), "flesh hit cue: " + hit.cue());
            helper.assertTrue(hit.event() == CombatSounds.MELEE_HIT_HEAVY.get(), "hit event " + hit.event());
            assertAt(helper, hit, target, "hit");
            helper.assertTrue(swing.seq() < hit.seq(), "the swing comes before the hit");
            done(helper, attacker, target);
        });
    }

    /** A thrust swings at the thrust pitch; a target in a chestplate rings. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void thrustSwingsHigherAndArmourRings(GameTestHelper helper) {
        Mob attacker = duelist(helper, 1, 4, -90);
        Mob target = duelist(helper, 3, 4, 90);
        target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        List<MeleeSoundPlayer.Played> a = rec(attacker), t = rec(target);
        helper.assertTrue(MeleeService.startThrust(attacker, DefaultWeapons.CUTLASS).accepted(), "thrust accepted");
        helper.runAfterDelay(20, () -> {
            MeleeSoundPlayer.Played swing = only(helper, a, "swing at the attacker");
            helper.assertTrue(swing.cue().equals(MeleeSoundRules.SWING_THRUST), "thrust swing cue: " + swing.cue());
            assertAt(helper, swing, attacker, "swing");
            MeleeSoundPlayer.Played hit = only(helper, t, "hit at the target");
            helper.assertTrue(hit.cue().equals(MeleeSoundRules.HIT_ARMOR), "armour hit cue: " + hit.cue());
            helper.assertTrue(hit.event() == CombatSounds.MELEE_HIT_ARMOR.get(), "hit event " + hit.event());
            assertAt(helper, hit, target, "armour hit");
            done(helper, attacker, target);
        });
    }

    /** A parried slash clashes loudly at the defender; nothing sounds like a hit, the parried attacker's stagger is silent. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void parriedSlashClashesWithoutAHit(GameTestHelper helper) {
        Mob attacker = duelist(helper, 1, 4, -90);
        Mob defender = duelist(helper, 3, 4, 90);
        List<MeleeSoundPlayer.Played> a = rec(attacker), d = rec(defender);
        helper.assertTrue(MeleeService.parry(defender, DefaultWeapons.RAPIER).accepted(), "parry accepted");
        helper.assertTrue(MeleeService.startSlash(attacker, DefaultWeapons.CUTLASS).accepted(), "slash accepted");
        helper.runAfterDelay(12, () -> {
            helper.assertValueEqual(MeleeService.state(attacker).phase(), Phase.STAGGERED, "attacker phase");
            MeleeSoundPlayer.Played parry = only(helper, d, "sound at the defender");
            helper.assertTrue(parry.cue().equals(MeleeSoundRules.PARRY), "parry cue: " + parry.cue());
            helper.assertTrue(parry.event() == CombatSounds.MELEE_PARRY.get(), "parry event " + parry.event());
            assertAt(helper, parry, defender, "parry");
            helper.assertTrue(ofSound(a, MeleeSoundRules.Sound.HIT_FLESH).isEmpty() && ofSound(a, MeleeSoundRules.Sound.HIT_ARMOR).isEmpty(),
                    "no hit sound at the attacker: " + a);
            only(helper, of(a, MeleeSoundRules.SWING_SLASH), "swing");
            helper.assertTrue(a.size() == 1, "only the swing at the attacker: " + a);
            done(helper, attacker, defender);
        });
    }

    /** A guarded slash clashes low and quiet at the defender. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void guardedSlashClashesLow(GameTestHelper helper) {
        Mob attacker = duelist(helper, 1, 4, -90);
        Mob defender = duelist(helper, 3, 4, 90);
        List<MeleeSoundPlayer.Played> d = rec(defender);
        helper.assertTrue(MeleeService.guardDown(defender, DefaultWeapons.RAPIER).accepted(), "guard accepted");
        helper.assertTrue(MeleeService.startSlash(attacker, DefaultWeapons.CUTLASS).accepted(), "slash accepted");
        helper.runAfterDelay(15, () -> {
            MeleeSoundPlayer.Played guard = only(helper, d, "sound at the defender");
            helper.assertTrue(guard.cue().equals(MeleeSoundRules.GUARD), "guard cue: " + guard.cue());
            helper.assertTrue(guard.event() == CombatSounds.MELEE_PARRY.get(), "guard event " + guard.event());
            assertAt(helper, guard, defender, "guard");
            done(helper, attacker, defender);
        });
    }

    /** A rapier thrust (9) beats a rapier holder's poise (8): the hit and a low stagger thud at the target. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void staggeringThrustThudsLow(GameTestHelper helper) {
        Mob attacker = duelist(helper, 1, 4, -90);
        Mob target = duelist(helper, 3, 4, 90);
        target.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(DefaultWeapons.RAPIER_ID)));
        List<MeleeSoundPlayer.Played> t = rec(target);
        helper.assertTrue(MeleeService.startThrust(attacker, DefaultWeapons.RAPIER).accepted(), "thrust accepted");
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(t.size() == 2, "hit and stagger at the target, got " + t);
            helper.assertTrue(t.get(0).cue().equals(MeleeSoundRules.HIT_FLESH), "first the hit: " + t.get(0).cue());
            helper.assertTrue(t.get(1).cue().equals(MeleeSoundRules.STAGGER), "then the stagger: " + t.get(1).cue());
            assertAt(helper, t.get(1), target, "stagger");
            done(helper, attacker, target);
        });
    }

    /** A feint whooshes quietly at the feinter and never swings. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void feintWhooshesInsteadOfSwinging(GameTestHelper helper) {
        Mob attacker = duelist(helper, 1, 4, -90);
        Mob target = duelist(helper, 3, 4, 90);
        List<MeleeSoundPlayer.Played> a = rec(attacker), t = rec(target);
        helper.assertTrue(MeleeService.startSlash(attacker, DefaultWeapons.CUTLASS).accepted(), "slash accepted");
        helper.runAfterDelay(2, () -> helper.assertTrue(MeleeService.feint(attacker).accepted(), "feint accepted"));
        helper.runAfterDelay(20, () -> {
            MeleeSoundPlayer.Played feint = only(helper, a, "sound at the feinter");
            helper.assertTrue(feint.cue().equals(MeleeSoundRules.FEINT), "feint cue: " + feint.cue());
            assertAt(helper, feint, attacker, "feint");
            helper.assertTrue(t.isEmpty(), "nothing at the target: " + t);
            done(helper, attacker, target);
        });
    }

    /** A vanilla mob hit parried by a sword clashes too. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void parriedVanillaHitClashes(GameTestHelper helper) {
        Mob attacker = duelist(helper, 1, 4, -90);
        Mob defender = duelist(helper, 2, 4, 90);
        List<MeleeSoundPlayer.Played> d = rec(defender);
        helper.assertTrue(MeleeService.parry(defender, DefaultWeapons.SABER).accepted(), "parry accepted");
        attacker.doHurtTarget(defender);
        MeleeSoundPlayer.Played parry = only(helper, d, "sound at the defender");
        helper.assertTrue(parry.cue().equals(MeleeSoundRules.PARRY), "parry cue: " + parry.cue());
        done(helper, attacker, defender);
    }

    /** Drawing a cutlass into the main hand sounds once; switching on to a second sword right away stays silent. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void drawingASwordSoundsOnce(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 1, 4.5)));
        List<MeleeSoundPlayer.Played> p = rec(player);
        MeleeSoundPlayer.onPlayerTick(player); // baseline: empty hand
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(DefaultWeapons.CUTLASS_ID)));
        MeleeSoundPlayer.onPlayerTick(player);
        MeleeSoundPlayer.onPlayerTick(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(DefaultWeapons.RAPIER_ID)));
        MeleeSoundPlayer.onPlayerTick(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        MeleeSoundPlayer.onPlayerTick(player);
        MeleeSoundPlayer.Played draw = only(helper, p, "draw sound");
        helper.assertTrue(draw.cue().equals(MeleeSoundRules.UNSHEATHE), "unsheathe cue: " + draw.cue());
        helper.assertTrue(draw.event() == CombatSounds.MELEE_UNSHEATHE.get(), "unsheathe event " + draw.event());
        assertAt(helper, draw, player, "unsheathe");
        player.discard();
        done(helper, player);
    }

    /** Own batch: with {@code melee.sounds.enabled} off a whole exchange (swing, hit, parry, draw) is silent. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_melee_sounds_off")
    public static void soundsOffPlaysNothing(GameTestHelper helper) {
        ConfigOverrides.during(helper, MeleeConfig.SOUNDS_ENABLED, false);
        List<MeleeSoundPlayer.Played> sunk = new ArrayList<>();
        // global recording sink (this test is alone in its batch); it forwards, so a failed test leaves sounds working
        MeleeSoundPlayer.installSink((e, cue, event, source, x, y, z, volume, pitch) -> {
            sunk.add(new MeleeSoundPlayer.Played(-1, e.getId(), cue, event, source, x, y, z, volume, pitch));
            SoundSink.WORLD.play(e, cue, event, source, x, y, z, volume, pitch);
        });
        Mob attacker = duelist(helper, 1, 4, -90);
        Mob target = duelist(helper, 3, 4, 90);
        Mob parrier = duelist(helper, 3, 6, 90);
        Mob parried = duelist(helper, 2, 6, -90);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        MeleeSoundPlayer.onPlayerTick(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(DefaultWeapons.CUTLASS_ID)));
        MeleeSoundPlayer.onPlayerTick(player);
        helper.assertTrue(MeleeService.startSlash(attacker, DefaultWeapons.CUTLASS).accepted(), "slash accepted");
        helper.assertTrue(MeleeService.parry(parrier, DefaultWeapons.SABER).accepted(), "parry accepted");
        parried.doHurtTarget(parrier);
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(target.getHealth() < target.getMaxHealth(), "the slash still hit");
            helper.assertTrue(sunk.isEmpty(), "sounds played with melee.sounds.enabled off: " + sunk);
            MeleeSoundPlayer.installSink(null);
            player.discard();
            done(helper, attacker, target, parrier, parried, player);
        });
    }

    /** Own batch: {@code melee.sounds.volume} scales every cue's volume. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_melee_sounds_volume")
    public static void volumeScalesTheCues(GameTestHelper helper) {
        ConfigOverrides.during(helper, MeleeConfig.SOUNDS_VOLUME, 0.5);
        Mob attacker = duelist(helper, 1, 4, -90);
        Mob target = duelist(helper, 3, 4, 90);
        List<MeleeSoundPlayer.Played> a = rec(attacker), t = rec(target);
        helper.assertTrue(MeleeService.startSlash(attacker, DefaultWeapons.CUTLASS).accepted(), "slash accepted");
        helper.runAfterDelay(15, () -> {
            MeleeSoundPlayer.Played swing = only(helper, a, "swing");
            MeleeSoundPlayer.Played hit = only(helper, t, "hit");
            Predicate<MeleeSoundPlayer.Played> halved = p -> p.volume() <= p.cue().maxVolume() * 0.5f + 1e-4
                    && p.volume() >= p.cue().minVolume() * 0.5f - 1e-4;
            helper.assertTrue(halved.test(swing), "swing volume not halved: " + swing.volume());
            helper.assertTrue(halved.test(hit), "hit volume not halved: " + hit.volume());
            done(helper, attacker, target);
        });
    }

    /** Every cue's event is in sounds.json with a subtitle, and the subtitle has its English text. */
    @ModGameTest
    public static void everyCueHasASubtitle(GameTestHelper helper) {
        JsonObject sounds = json(helper, "/assets/pirates_n_ships/sounds.json");
        JsonObject lang = json(helper, "/assets/pirates_n_ships/lang/en_us.json");
        Map<MeleeSoundRules.Sound, String> expected = Map.of(
                MeleeSoundRules.Sound.SWING, "Sword swings", MeleeSoundRules.Sound.HIT_FLESH, "Sword hits",
                MeleeSoundRules.Sound.PARRY, "Blades clash", MeleeSoundRules.Sound.HIT_ARMOR, "Armour rings",
                MeleeSoundRules.Sound.UNSHEATHE, "Sword drawn");
        for (MeleeSoundRules.Sound sound : MeleeSoundRules.Sound.values()) {
            String path = BuiltInRegistries.SOUND_EVENT.getKey(MeleeSoundPlayer.event(sound)).getPath();
            helper.assertTrue(sounds.has(path), "sounds.json does not list " + path);
            String key = "subtitles.pirates_n_ships." + path;
            JsonObject entry = sounds.getAsJsonObject(path);
            helper.assertTrue(entry.has("subtitle") && key.equals(entry.get("subtitle").getAsString()), path + " has no subtitle " + key);
            helper.assertTrue(lang.has(key) && expected.get(sound).equals(lang.get(key).getAsString()),
                    key + " should read '" + expected.get(sound) + "', is " + lang.get(key));
        }
        helper.succeed();
    }

    private static JsonObject json(GameTestHelper helper, String resource) {
        try (InputStream in = MeleeSoundGameTests.class.getResourceAsStream(resource)) {
            helper.assertTrue(in != null, resource + " is missing (run ./gradlew :neoforge:runData)");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + resource, e);
        }
    }
}

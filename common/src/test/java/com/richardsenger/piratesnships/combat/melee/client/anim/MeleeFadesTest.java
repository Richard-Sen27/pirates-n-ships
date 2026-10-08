package com.richardsenger.piratesnships.combat.melee.client.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.combat.melee.engine.MeleeEngine;
import com.richardsenger.piratesnships.combat.melee.geometry.Box;
import com.richardsenger.piratesnships.combat.melee.geometry.Vec;
import com.richardsenger.piratesnships.combat.melee.resolve.HitResult;
import com.richardsenger.piratesnships.combat.melee.resolve.IncomingHit;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.CombatRules;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.MeleeParams;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.combat.melee.rules.Refusal;
import com.richardsenger.piratesnships.combat.melee.weapon.DefaultWeapons;
import com.richardsenger.piratesnships.combat.melee.weapon.WeaponDefinition;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.richardsenger.piratesnships.combat.melee.client.anim.MeleeAnimationMapping.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MEL1 cross-fades: the seamless table matches the committed animation files, the fade rules, and a whole duel run
 * through the server's engine with the client's fade bookkeeping on top (the phases and the animation timing are the
 * same with and without fades; only interrupted transitions fade).
 */
class MeleeFadesTest {

    /** The rest pose of art/README.md ("Rest pose"): vanilla's item-holding right arm, everything else zero. */
    static final Map<String, float[]> REST = rest();

    private static Map<String, float[]> rest() {
        Map<String, float[]> m = new HashMap<>();
        MeleeFades.FADED_CHANNELS.forEach((bone, channels) -> channels.forEach(c -> m.put(bone + "." + c, new float[3])));
        m.put("right_arm.rotation", new float[]{-18f, 0f, 0f});
        return m;
    }

    static JsonObject bones(String name) throws IOException {
        return PalAnimationFilesTest.read(name).getAsJsonObject("animations").getAsJsonObject(name).getAsJsonObject("bones");
    }

    /** The first ({@code end = false}) or last keyframe of every channel. */
    static Map<String, float[]> pose(@Nullable String name, boolean end) throws IOException {
        if (name == null) return REST;
        Map<String, float[]> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> bone : bones(name).entrySet()) {
            for (Map.Entry<String, JsonElement> channel : bone.getValue().getAsJsonObject().entrySet()) {
                JsonObject frames = channel.getValue().getAsJsonObject();
                String key = frames.keySet().stream().min(Comparator.comparingDouble(k -> (end ? -1 : 1) * Double.parseDouble(k))).orElseThrow();
                JsonArray v = frames.getAsJsonArray(key);
                out.put(bone.getKey() + "." + channel.getKey(), new float[]{v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat()});
            }
        }
        return out;
    }

    static boolean samePose(Map<String, float[]> a, Map<String, float[]> b) {
        assertEquals(a.keySet(), b.keySet());
        for (String k : a.keySet()) {
            for (int i = 0; i < 3; i++) if (Math.abs(a.get(k)[i] - b.get(k)[i]) > 0.01f) return false;
        }
        return true;
    }

    @Test
    void everyMeleeAnimationKeysExactlyTheFadedChannels() throws IOException {
        for (String name : ALL) {
            Map<String, Set<String>> keyed = new HashMap<>();
            for (Map.Entry<String, JsonElement> bone : bones(name).entrySet()) {
                keyed.put(bone.getKey(), new HashSet<>(bone.getValue().getAsJsonObject().keySet()));
            }
            assertEquals(MeleeFades.FADED_CHANNELS, keyed, name);
        }
    }

    @Test
    void seamlessTableMatchesTheFiles() throws IOException {
        List<String> names = new ArrayList<>(ALL);
        names.add(null);
        for (String from : names) {
            for (String to : names) {
                boolean files = samePose(pose(from, true), pose(to, false));
                assertEquals(files, MeleeFades.seamless(from, to), from + " -> " + to);
            }
        }
    }

    @Test
    void knownJumpsFade() {
        // F9's known snaps: the slash riposte, a parry from rest (it starts at the guard pose)
        assertEquals(3, MeleeFades.fadeTicks(RIPOSTE_WINDUP, 0f, SLASH_ACTIVE, 4, 3));
        assertEquals(0, MeleeFades.fadeTicks(RIPOSTE_WINDUP, 0f, THRUST_ACTIVE, 4, 3));
        assertEquals(4, MeleeFades.fadeTicks(null, 0f, PARRY, 4, 7));
        // a stagger interrupting a wind-up, a parry cut short by its success, a guard released while rising
        assertEquals(4, MeleeFades.fadeTicks(SLASH_WINDUP, 3f, STAGGER, 4, 20));
        assertEquals(4, MeleeFades.fadeTicks(PARRY, 4f, null, 4, 0));
        assertEquals(4, MeleeFades.fadeTicks(GUARD, 3f, GUARD_LOWER, 4, 0));
        // an attack cancelled back to idle while holding its last frame (feint, weapon switch)
        assertEquals(4, MeleeFades.fadeTicks(SLASH_ACTIVE, 0f, null, 4, 0));
    }

    @Test
    void chainedAndRestTransitionsDoNotFade() {
        assertEquals(0, MeleeFades.fadeTicks(null, 0f, SLASH_WINDUP, 4, 5), "the wind-up telegraph stays crisp");
        assertEquals(0, MeleeFades.fadeTicks(SLASH_WINDUP, 0f, SLASH_ACTIVE, 4, 3));
        assertEquals(0, MeleeFades.fadeTicks(SLASH_ACTIVE, 1f, SLASH_RECOVERY, 4, 6), "a tick early is still the chain");
        assertEquals(0, MeleeFades.fadeTicks(SLASH_RECOVERY, 0f, null, 4, 0));
        assertEquals(0, MeleeFades.fadeTicks(GUARD, 0f, PARRY, 4, 7));
        assertEquals(0, MeleeFades.fadeTicks(STAGGER, -5f, SLASH_WINDUP, 4, 5));
        assertEquals(4, MeleeFades.fadeTicks(SLASH_ACTIVE, 2f, SLASH_RECOVERY, 4, 6), "two ticks early is cut short");
    }

    @Test
    void fadeIsCappedByThePhaseAndOffAtZero() {
        assertEquals(2, MeleeFades.fadeTicks(SLASH_WINDUP, 3f, STAGGER, 4, 2));
        assertEquals(1, MeleeFades.fadeTicks(SLASH_WINDUP, 3f, STAGGER, 1, 20));
        assertEquals(8, MeleeFades.fadeTicks(GUARD, 3f, GUARD_LOWER, 8, 0), "open-ended: the configured length");
        for (String to : ALL) assertEquals(0, MeleeFades.fadeTicks(SLASH_WINDUP, 3f, to, 0, 5), to);
        assertEquals(0, MeleeFades.fadeTicks(PARRY, 3f, null, -1, 0));
    }

    @Test
    void alphaIsSmoothAndClamped() {
        assertEquals(0f, MeleeFades.alpha(-1f));
        assertEquals(0f, MeleeFades.alpha(0f));
        assertEquals(0.5f, MeleeFades.alpha(0.5f), 1e-6);
        assertEquals(1f, MeleeFades.alpha(1f));
        assertEquals(1f, MeleeFades.alpha(3f));
        float last = 0f;
        for (int i = 1; i <= 100; i++) {
            float a = MeleeFades.alpha(i / 100f);
            assertTrue(a >= last, "monotonic at " + i);
            last = a;
        }
        assertTrue(MeleeFades.alpha(0.05f) < 0.05f, "eases out of the old pose");
    }

    @Test
    void trackFollowsStretchedAnimationsAndFadesOut() {
        MeleeFades.Track t = new MeleeFades.Track();
        assertNull(t.current());
        // slash wind-up of 5 game ticks: the 5-tick file at speed 1
        assertEquals(0, t.play(SLASH_WINDUP, 5f, 0f, 1f, 100, 4, 5));
        assertEquals(5f, t.remaining(100));
        // stagger 2 ticks in: cut short
        assertEquals(4, t.play(STAGGER, 20f, 0f, 1f, 102, 4, 20));
        // stagger stretched to 10 ticks at speed 2; done at 112, then idle without a fade
        assertEquals(4, t.play(STAGGER, 20f, 0f, 2f, 102, 4, 10));
        assertEquals(10f, t.remaining(102));
        assertEquals(0, t.stop(112, 4));
        assertNull(t.current());
        // parry from rest, then idle 3 ticks into the window: fade out, and a riposte during that fade fades again
        assertEquals(4, t.play(PARRY, 7f, 0f, 1f, 120, 4, 7));
        assertEquals(4, t.stop(123, 4));
        assertEquals(4f, t.remaining(123));
        assertEquals(4, t.play(RIPOSTE_WINDUP, 4f, 0f, 1f, 125, 4, 5));
        // a late start offset shortens the remaining time
        assertEquals(4, t.play(SLASH_WINDUP, 5f, 3f, 1f, 200, 4, 5));
        assertEquals(2f, t.remaining(200));
        t.halt(300);
        assertNull(t.current());
        assertEquals(0f, t.remaining(300));
    }

    // --- a duel through the server's engine, with the client's fade bookkeeping on top ------------------------------

    static final MeleeParams P = MeleeParams.DEFAULTS;

    static final class F implements MeleeEngine.Fighter {
        final int id;
        final WeaponDefinition weapon;
        final Vec feet;
        final Vec look;
        CombatState state = CombatState.fresh(P.staminaMax());

        F(int id, WeaponDefinition weapon, Vec feet, Vec look) {
            this.id = id;
            this.weapon = weapon;
            this.feet = feet;
            this.look = look;
        }

        @Override public int id() { return id; }
        @Override public CombatState state() { return state; }
        @Override public void setState(CombatState s) { state = s; }
        @Override public WeaponDefinition weapon() { return weapon; }
        @Override public Vec eye() { return feet.add(new Vec(0, 1.6, 0)); }
        @Override public Vec look() { return look; }
        @Override public Box box() { return Box.standing(feet, 0.6, 1.8); }
        @Override public boolean valid() { return true; }
    }

    /** One phase change as the client animates it: the animation, its timing and the fade picked. */
    record Shown(long tick, int fighter, Phase phase, @Nullable String animation, float speed, float start, int fade) {
    }

    /**
     * A thrusts with the rapier, B parries in time (A staggers), B ripostes with a slash into the staggered A. Every
     * phase change of either fighter is played on a client-side {@link MeleeFades.Track} as {@code PalMeleeAnimations}
     * does it.
     */
    static List<Shown> duel(int fadeTicks) throws IOException {
        F a = new F(1, DefaultWeapons.RAPIER, new Vec(0, 0, 0), new Vec(1, 0, 0));
        F b = new F(2, DefaultWeapons.CUTLASS, new Vec(2, 0, 0), new Vec(-1, 0, 0));
        List<F> fighters = List.of(a, b);
        MeleeEngine engine = new MeleeEngine();
        MeleeEngine.Arena arena = new MeleeEngine.Arena() {
            @Override public List<? extends MeleeEngine.Fighter> candidates(MeleeEngine.Fighter attacker, double range) { return fighters; }
            @Override public void apply(MeleeEngine.Fighter attacker, MeleeEngine.Fighter target, IncomingHit hit, HitResult r) { }
        };
        Map<Integer, MeleeFades.Track> tracks = Map.of(1, new MeleeFades.Track(), 2, new MeleeFades.Track());
        Map<Integer, CombatState> seen = new HashMap<>(Map.of(1, a.state, 2, b.state));
        List<Shown> shown = new ArrayList<>();
        for (long now = 0; now < 60; now++) {
            if (now == 0) a.state = CombatRules.startAttack(a.state, AttackKind.THRUST, a.weapon, P).state();
            if (now == 4) assertEquals(Refusal.NONE, CombatRules.parry(b.state, b.weapon, P).refusal());
            if (now == 4) b.state = CombatRules.parry(b.state, b.weapon, P).state();
            if (now == 8) b.state = CombatRules.startAttack(b.state, AttackKind.SLASH, b.weapon, P).state();
            for (F f : fighters) observe(f, now, seen, tracks.get(f.id), fadeTicks, shown);
            engine.tick(now, fighters, arena, P);
            for (F f : fighters) observe(f, now, seen, tracks.get(f.id), fadeTicks, shown);
        }
        return shown;
    }

    private static void observe(F f, long now, Map<Integer, CombatState> seen, MeleeFades.Track track, int fadeTicks, List<Shown> out) throws IOException {
        CombatState s = f.state;
        CombatState before = seen.put(f.id, s);
        if (before.phase() == s.phase() && before.attack() == s.attack() && before.riposteAttack() == s.riposteAttack()) return;
        if (s.phase() == Phase.IDLE) {
            String lower = MeleeAnimationMapping.onStop(before.phase());
            if (lower != null) {
                float len = length(lower);
                out.add(new Shown(now, f.id, s.phase(), lower, 1f, 0f, track.play(lower, len, 0f, 1f, now, fadeTicks, 0)));
            } else {
                out.add(new Shown(now, f.id, s.phase(), null, 0f, 0f, track.stop(now, fadeTicks)));
            }
            return;
        }
        String name = MeleeAnimationMapping.forPhase(s.phase(), s.attack(), s.riposteAttack());
        float len = length(name);
        float speed = MeleeAnimationMapping.speed(len, s.duration());
        float start = MeleeAnimationMapping.startTick(s.elapsed(), speed, len);
        out.add(new Shown(now, f.id, s.phase(), name, speed, start, track.play(name, len, start, speed, now, fadeTicks, s.duration())));
    }

    private static float length(String name) throws IOException {
        return PalAnimationFilesTest.read(name).getAsJsonObject("animations").getAsJsonObject(name).get("animation_length").getAsFloat() * 20f;
    }

    @Test
    void fadesNeverChangeThePhasesOrTheAnimationTiming() throws IOException {
        List<Shown> off = duel(0);
        for (int fade : new int[]{1, 4, 20}) {
            List<Shown> on = duel(fade);
            assertEquals(off.size(), on.size(), "same number of phase changes with fade " + fade);
            for (int i = 0; i < off.size(); i++) {
                Shown x = off.get(i);
                Shown y = on.get(i);
                assertEquals(new Shown(x.tick(), x.fighter(), x.phase(), x.animation(), x.speed(), x.start(), 0),
                        new Shown(y.tick(), y.fighter(), y.phase(), y.animation(), y.speed(), y.start(), 0),
                        "phase change " + i + " with fade " + fade);
            }
        }
        assertTrue(off.stream().allMatch(s -> s.fade() == 0), "fade 0 never fades: " + off);
    }

    @Test
    void duelFadesExactlyTheInterruptedTransitions() throws IOException {
        List<Shown> shown = duel(4);
        // A: thrust wind-up from rest (crisp), parried at its first hit frame: the stagger blends in
        Shown aWindup = first(shown, 1, Phase.WINDUP);
        assertEquals(THRUST_WINDUP, aWindup.animation());
        assertEquals(0, aWindup.fade());
        Shown aStagger = first(shown, 1, Phase.STAGGERED);
        assertTrue(aStagger.fade() > 0, "the parried thrust blends into the stagger: " + shown);
        // B: the parry from rest blends in (it starts at the guard pose); its success ends the window early: fade out
        Shown bParry = first(shown, 2, Phase.PARRYING);
        assertEquals(4, bParry.fade());
        Shown bIdle = shown.stream().filter(s -> s.fighter() == 2 && s.phase() == Phase.IDLE && s.tick() > bParry.tick()).findFirst().orElseThrow();
        assertEquals(4, bIdle.fade(), "the parry cut short fades back to rest");
        // B's riposte: the wind-up starts while the parry is still fading out, then the low draw blends into the slash
        Shown bWindup = first(shown, 2, Phase.WINDUP);
        assertEquals(RIPOSTE_WINDUP, bWindup.animation());
        assertTrue(bWindup.fade() > 0, "riposte during the fade-out: " + shown);
        Shown bActive = first(shown, 2, Phase.ACTIVE);
        assertEquals(SLASH_ACTIVE, bActive.animation());
        assertEquals(Math.min(4, DefaultWeapons.CUTLASS.activeTicks(AttackKind.SLASH)), bActive.fade());
        // the slash's own chain (active -> recovery -> rest) stays seamless
        assertEquals(0, first(shown, 2, Phase.RECOVERY).fade());
        // a fade into a timed phase never outlasts it
        for (Shown s : shown) {
            assertTrue(s.fade() <= 4, s.toString());
        }
    }

    private static Shown first(List<Shown> shown, int fighter, Phase phase) {
        return shown.stream().filter(s -> s.fighter() == fighter && s.phase() == phase).findFirst()
                .orElseThrow(() -> new AssertionError("no " + phase + " for " + fighter + " in " + shown));
    }
}

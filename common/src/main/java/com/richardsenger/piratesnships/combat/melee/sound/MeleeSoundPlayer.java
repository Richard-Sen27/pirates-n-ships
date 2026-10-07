package com.richardsenger.piratesnships.combat.melee.sound;

import com.richardsenger.piratesnships.combat.content.CombatSounds;
import com.richardsenger.piratesnships.combat.melee.MeleeConfig;
import com.richardsenger.piratesnships.combat.melee.resolve.HitResult;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.weapon.MeleeWeapons;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Plays the sword sounds of {@link MeleeSoundRules} server-side at the fighters (docs/design.md §8.5, §16). Called by
 * {@code MeleeService} at its transition points (state changes, resolved hits) and every player tick for drawing a
 * sword. Sounds go through {@link #sink} ({@link SoundSink#WORLD} unless a test installs another) with
 * {@link SoundSource#PLAYERS} for players and {@link SoundSource#HOSTILE} for every other entity. Server thread only.
 */
public final class MeleeSoundPlayer {

    /** A sound as seen by {@link #record} (tests); {@code seq} orders sounds across recordings. */
    public record Played(long seq, int emitterId, MeleeSoundRules.Cue cue, SoundEvent event, SoundSource source, double x, double y,
                         double z, float volume, float pitch) {
    }

    private static SoundSink sink = SoundSink.WORLD;
    private static long seq;
    private static final Map<Integer, List<Played>> RECORDINGS = new HashMap<>();
    private static final Map<Player, UnsheatheTracker> DRAWS = new WeakHashMap<>();

    private MeleeSoundPlayer() {
    }

    // --- Hooks --------------------------------------------------------------------------------------------------

    /** A combatant's state changed (swing, feint). */
    public static void onStateChange(LivingEntity entity, CombatState before, CombatState after) {
        if (entity.level().isClientSide() || before.equals(after)) return;
        for (MeleeSoundRules.Cue c : MeleeSoundRules.stateChange(before, after, MeleeConfig.soundParams())) {
            play(entity, c);
        }
    }

    /** A hit was resolved (parry, guard, hit, stagger). {@code modMelee} false = a vanilla melee hit. */
    public static void onHit(LivingEntity attacker, LivingEntity defender, HitResult result, boolean modMelee) {
        if (defender.level().isClientSide()) return;
        for (MeleeSoundRules.Cue c : MeleeSoundRules.hit(result.outcome(), result.defenderStaggered(), modMelee,
                armoured(defender), MeleeConfig.soundParams())) {
            play(c.at() == MeleeSoundRules.At.ATTACKER ? attacker : defender, c);
        }
    }

    /** {@code PLAYER_TICK_END}: drawing a mod sword into the main hand sounds (server side, heard by everyone nearby). */
    public static void onPlayerTick(Player player) {
        if (player.level().isClientSide()) return;
        ItemStack held = player.getMainHandItem();
        Object weapon = !held.isEmpty() && MeleeWeapons.forStack(held, MeleeWeapons.WEAPONS.of(player.level())).isPresent()
                ? held.getItem() : null;
        UnsheatheTracker t = DRAWS.computeIfAbsent(player, p -> new UnsheatheTracker());
        if (t.update(weapon, player.level().getGameTime(), MeleeConfig.UNSHEATHE_COOLDOWN.get())) {
            for (MeleeSoundRules.Cue c : MeleeSoundRules.unsheathe(MeleeConfig.soundParams())) play(player, c);
        }
    }

    public static void onServerStopped() {
        DRAWS.clear();
        RECORDINGS.clear();
    }

    // --- Playback -----------------------------------------------------------------------------------------------

    /** Armour for the hit sound: an armour item in the chest slot (a chestplate; not an elytra or a worn sea chest). */
    public static boolean armoured(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ArmorItem;
    }

    public static SoundEvent event(MeleeSoundRules.Sound sound) {
        return switch (sound) {
            case SWING -> CombatSounds.MELEE_SWING.get();
            case PARRY -> CombatSounds.MELEE_PARRY.get();
            case HIT_FLESH -> CombatSounds.MELEE_HIT_HEAVY.get();
            case HIT_ARMOR -> CombatSounds.MELEE_HIT_ARMOR.get();
            case UNSHEATHE -> CombatSounds.MELEE_UNSHEATHE.get();
        };
    }

    private static void play(LivingEntity at, MeleeSoundRules.Cue cue) {
        RandomSource r = at.getRandom();
        float volume = cue.volume(r.nextDouble(), MeleeConfig.SOUNDS_VOLUME.get());
        float pitch = cue.pitch(r.nextDouble());
        if (volume <= 0f) return;
        SoundEvent event = event(cue.sound());
        SoundSource source = at instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
        double x = at.getX(), y = at.getY() + at.getBbHeight() * 0.6, z = at.getZ();
        List<Played> rec = RECORDINGS.get(at.getId());
        if (rec != null) rec.add(new Played(seq++, at.getId(), cue, event, source, x, y, z, volume, pitch));
        sink.play(at, cue, event, source, x, y, z, volume, pitch);
    }

    // --- Tests --------------------------------------------------------------------------------------------------

    /**
     * Records every sword sound played at the entity from now on, in addition to playing it (tests; server thread).
     * Recording per entity keeps tests that run at the same time apart. Call {@link #stopRecording} at the end.
     */
    public static List<Played> record(int entityId) {
        return RECORDINGS.computeIfAbsent(entityId, id -> new ArrayList<>());
    }

    public static void stopRecording(int entityId) {
        RECORDINGS.remove(entityId);
    }

    /** Replaces the sink (null = {@link SoundSink#WORLD}). Global: only for tests that run alone in their batch. */
    public static void installSink(@Nullable SoundSink s) {
        sink = s == null ? SoundSink.WORLD : s;
    }
}

package com.richardsenger.piratesnships.rpg.deeds;

import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.world.CombatCrimeDetector;
import com.richardsenger.piratesnships.mob.MobFaction;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.rpg.reputation.ReputationConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat deeds (docs/design.md §15): a player hurting or killing navy, pirates or villagers. Listens to
 * {@code LIVING_INCOMING_DAMAGE} and {@code LIVING_DEATH} like the law's {@code CombatCrimeDetector} and uses its
 * notion of the responsible attacker (the shooter, a tamed animal's owner). Killing a pirate is no crime, so this does
 * not go through the law's crime hook. Hitting the same victim again within {@code reputation.attack_repeat_seconds}
 * is not another attack deed; the killing blow also counts as an attack (as for crimes).
 */
public final class CombatDeeds {

    /** Which side of the reputation table a victim is on. */
    public enum Side { NAVY, PIRATE, VILLAGER }

    private record Key(UUID attacker, UUID victim) {
    }

    private static final Map<Key, Long> LAST_ATTACK = new ConcurrentHashMap<>();
    private static final int PRUNE_SIZE = 512;

    private CombatDeeds() {
    }

    /** The deed for hurting ({@code kill} false) or killing a victim of {@code side}. Pure. */
    public static Deed deedFor(Side side, boolean kill) {
        return switch (side) {
            case NAVY -> kill ? Deed.KILL_NAVY : Deed.ATTACK_NAVY;
            case PIRATE -> kill ? Deed.KILL_PIRATE : Deed.ATTACK_PIRATE;
            case VILLAGER -> kill ? Deed.KILL_VILLAGER : Deed.ATTACK_VILLAGER;
        };
    }

    /** Navy beats pirate beats villager (an entity in several groups counts once). Pure. */
    public static Optional<Side> side(boolean navy, boolean pirate, boolean villager) {
        if (navy) return Optional.of(Side.NAVY);
        if (pirate) return Optional.of(Side.PIRATE);
        if (villager) return Optional.of(Side.VILLAGER);
        return Optional.empty();
    }

    /** Whether an attack at {@code now} is a new deed after one at {@code last} ({@code null}: none yet). Pure. */
    public static boolean newAttack(@Nullable Long last, long now, long windowTicks) {
        return last == null || now < last || now - last >= windowTicks;
    }

    /** The side of {@code victim}: navy-tagged, our pirates, or villagers, wandering traders and sailors. */
    public static Optional<Side> sideOf(LivingEntity victim) {
        if (victim instanceof Player) return Optional.empty();
        boolean seafarer = victim instanceof SeafarerMob;
        MobFaction faction = seafarer ? ((SeafarerMob) victim).faction() : null;
        return side(LawService.isNavy(victim), faction == MobFaction.PIRATE,
                LawService.isLawProtected(victim) || faction == MobFaction.CIVILIAN);
    }

    /** Listener for {@code CommonEvents.LIVING_INCOMING_DAMAGE}. Never changes the damage. */
    public static float onIncomingDamage(LivingEntity victim, DamageSource source, float amount) {
        if (amount > 0) report(victim, source, false);
        return amount;
    }

    /** Listener for {@code CommonEvents.LIVING_DEATH}. Never cancels the death. */
    public static boolean onDeath(LivingEntity victim, DamageSource source) {
        report(victim, source, true);
        return false;
    }

    /** Records the deed of {@code source}'s player against {@code victim}, if any; returns it (tests). */
    public static Optional<Deed> report(LivingEntity victim, DamageSource source, boolean kill) {
        if (victim.level().isClientSide()) return Optional.empty();
        if (!(CombatCrimeDetector.offender(source) instanceof ServerPlayer player) || player == victim) return Optional.empty();
        Optional<Side> side = sideOf(victim);
        if (side.isEmpty()) return Optional.empty();
        if (!kill) {
            long now = victim.level().getGameTime();
            Key key = new Key(player.getUUID(), victim.getUUID());
            if (!newAttack(LAST_ATTACK.get(key), now, ReputationConfig.ATTACK_REPEAT_SECONDS.get() * 20L)) return Optional.empty();
            LAST_ATTACK.put(key, now);
            if (LAST_ATTACK.size() > PRUNE_SIZE) prune(now);
        }
        Deed deed = deedFor(side.get(), kill);
        Deeds.record(player, deed, DeedContext.victim(victim));
        return Optional.of(deed);
    }

    private static void prune(long now) {
        long window = ReputationConfig.ATTACK_REPEAT_SECONDS.get() * 20L;
        for (Iterator<Map.Entry<Key, Long>> it = LAST_ATTACK.entrySet().iterator(); it.hasNext(); ) {
            if (newAttack(it.next().getValue(), now, window)) it.remove();
        }
    }

    public static void clear() {
        LAST_ATTACK.clear();
    }
}

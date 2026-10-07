package com.richardsenger.piratesnships.combat.melee.net;

import com.richardsenger.piratesnships.combat.melee.MeleeConfig;
import com.richardsenger.piratesnships.combat.melee.MeleeService;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.Refusal;
import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server side: turns melee state changes into {@link MeleeStatePayload}s. Server thread only.
 *
 * <p>{@link MeleeService#onServerTick} calls {@link #flush} after the combat tick with every active combatant. A
 * change that matters to observers ({@link MeleeSyncRules#observerRelevant}) goes to everyone tracking the entity and
 * to the entity itself if it is a player (stamina left out); a player additionally gets the owner copy with stamina
 * on every such change and, throttled by {@code melee.stamina_sync_interval_ticks}, when only its stamina changed.
 * Refused actions are answered at once with {@link #sendRefusal}.
 *
 * <p>Players who start tracking an entity mid-fight learn its state with its next relevant change.
 */
public final class MeleeStateSync {

    /** Who a payload went to. */
    public enum Target {
        /** Everyone tracking the entity, and the entity itself if it is a player. */
        OBSERVERS,
        /** Only the entity, a player. */
        OWNER
    }

    /** A payload handed to the network, as seen by {@link #record} (tests). */
    public record Sent(Target target, MeleeStatePayload payload) {
    }

    private record OwnerMark(float stamina, long tick) {
    }

    private static final Map<LivingEntity, CombatState> LAST_OBSERVED = new HashMap<>();
    private static final Map<LivingEntity, OwnerMark> LAST_OWNER = new HashMap<>();
    private static final Map<Integer, List<Sent>> RECORDINGS = new HashMap<>();

    private MeleeStateSync() {
    }

    /** Sends what changed since the last flush for each of {@code combatants}. */
    public static void flush(long serverTick, Collection<LivingEntity> combatants) {
        LAST_OBSERVED.keySet().removeIf(e -> e.isRemoved() || !e.isAlive());
        LAST_OWNER.keySet().removeIf(e -> e.isRemoved() || !e.isAlive());
        if (combatants.isEmpty()) return;
        float max = MeleeConfig.STAMINA_MAX.get().floatValue();
        int interval = MeleeConfig.STAMINA_SYNC_INTERVAL.get();
        for (LivingEntity e : combatants) {
            if (e.isRemoved() || !e.isAlive()) continue;
            CombatState s = MeleeService.state(e);
            boolean relevant = MeleeSyncRules.observerRelevant(LAST_OBSERVED.get(e), s);
            if (relevant) {
                LAST_OBSERVED.put(e, s);
                send(e, Target.OBSERVERS, MeleeStatePayload.observed(e.getId(), s, (int) serverTick));
            }
            if (e instanceof Player) {
                OwnerMark m = LAST_OWNER.get(e);
                if (relevant || m == null || MeleeSyncRules.ownerStaminaDue(m.stamina(), m.tick(), s.stamina(), max, serverTick, interval)) {
                    LAST_OWNER.put(e, new OwnerMark(s.stamina(), serverTick));
                    send(e, Target.OWNER, MeleeStatePayload.owned(e.getId(), s, max, Refusal.NONE, (int) serverTick));
                }
            }
        }
    }

    /** Tells the acting player its action was refused (HUD flash, short action bar note). */
    public static void sendRefusal(LivingEntity e, CombatState s, Refusal refusal, long serverTick) {
        if (refusal == Refusal.NONE || !(e instanceof Player)) return;
        float max = MeleeConfig.STAMINA_MAX.get().floatValue();
        send(e, Target.OWNER, MeleeStatePayload.owned(e.getId(), s, max, refusal, (int) serverTick));
    }

    private static void send(LivingEntity e, Target target, MeleeStatePayload payload) {
        List<Sent> rec = RECORDINGS.get(e.getId());
        if (rec != null) rec.add(new Sent(target, payload));
        if (target == Target.OBSERVERS) {
            Services.NETWORK.sendToTrackingEntityAndSelf(e, payload);
        } else if (e instanceof ServerPlayer sp && sp.connection != null) {
            Services.NETWORK.sendToPlayer(sp, payload);
        }
    }

    public static void onServerStopped() {
        LAST_OBSERVED.clear();
        LAST_OWNER.clear();
        RECORDINGS.clear();
    }

    // --- Test support -------------------------------------------------------------------------------------------

    /**
     * GameTests: from now on, every payload about {@code entityId} is also appended to the returned list (server
     * thread). The real sending is unchanged. Call {@link #stopRecording} at the end of the test.
     */
    public static List<Sent> record(int entityId) {
        return RECORDINGS.computeIfAbsent(entityId, id -> new ArrayList<>());
    }

    public static void stopRecording(int entityId) {
        RECORDINGS.remove(entityId);
    }
}

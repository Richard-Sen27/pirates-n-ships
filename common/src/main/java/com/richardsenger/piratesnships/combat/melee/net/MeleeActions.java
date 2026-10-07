package com.richardsenger.piratesnships.combat.melee.net;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.melee.MeleeService;
import com.richardsenger.piratesnships.combat.melee.rules.InputResult;
import com.richardsenger.piratesnships.combat.melee.rules.Refusal;
import com.richardsenger.piratesnships.combat.melee.weapon.WeaponDefinition;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Server handler of {@link MeleeActionPayload}: maps an action to the {@link MeleeService} input with the weapon in
 * the main hand. The service validates everything (phase, stamina, lockout, stagger); a refusal is answered to the
 * player with {@link MeleeStateSync#sendRefusal}. With {@code melee.skill_based_combat} off, without a skill-based
 * sword in hand, for a dead or spectating player, or for an unknown action, the payload is ignored without answer.
 */
public final class MeleeActions {

    private MeleeActions() {
    }

    /** {@code registerToServer} handler (server main thread). */
    public static void handle(MeleeActionPayload payload, Player player) {
        MeleeAction action = payload.decodedAction();
        if (action == null || player == null) return;
        InputResult r = apply(player, action);
        if (r != null && !r.accepted()) {
            MeleeStateSync.sendRefusal(player, r.state(), r.refusal(), serverTick(player));
        }
    }

    /**
     * Applies {@code action} to {@code entity} with its main-hand weapon. Returns the service's result, or
     * {@code null} when the action was ignored (system off, no skill-based weapon, dead or spectating).
     */
    public static @Nullable InputResult apply(LivingEntity entity, MeleeAction action) {
        if (!MeleeService.skillBasedCombat() || !entity.isAlive() || entity.isSpectator()) return null;
        if (action == MeleeAction.GUARD_UP) return MeleeService.guardUp(entity);
        WeaponDefinition weapon = MeleeService.weaponInHand(entity).orElse(null);
        if (weapon == null) return null;
        return switch (action) {
            case SLASH -> MeleeService.startSlash(entity, weapon);
            case THRUST -> MeleeService.startThrust(entity, weapon);
            case GUARD_DOWN -> MeleeService.guardDown(entity, weapon);
            case PARRY -> MeleeService.parry(entity, weapon);
            case GUARD_UP -> MeleeService.guardUp(entity);
        };
    }

    /**
     * Lang key of the action bar note for a refusal, or {@code null} for refusals that only flash the HUD (BUSY is
     * what mashing produces, so it stays silent).
     */
    public static @Nullable String refusalMessageKey(Refusal refusal) {
        return switch (refusal) {
            case NO_STAMINA, LOCKED_OUT, STAGGERED -> "message." + Constants.MOD_ID + ".melee.refused." + refusal.name().toLowerCase(Locale.ROOT);
            case NONE, BUSY, DISABLED -> null;
        };
    }

    private static long serverTick(LivingEntity e) {
        return e.getServer() == null ? 0 : e.getServer().getTickCount();
    }
}

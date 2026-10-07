package com.richardsenger.piratesnships.survival.swim;

import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import com.richardsenger.piratesnships.survival.SurvivalConfig;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The swimming hunger hook (rules in {@link SwimHungerRules}): after every server tick of a player, the movement since
 * the previous tick is charged again at {@code multiplier - 1} times vanilla's swimming cost. Vanilla charges per
 * movement packet; we charge per tick from the position difference, which comes to the same within the rounding to
 * whole centimetres.
 */
public final class SwimHunger {

    /** Each player's position at its previous tick end. Server thread only; weak so logged-out players drop out. */
    private static final Map<Player, Vec3> LAST = new WeakHashMap<>();

    private SwimHunger() {
    }

    /** {@code PLAYER_TICK_END} (both sides; only the server charges). */
    public static void onPlayerTick(Player player) {
        if (player.level().isClientSide()) return;
        Vec3 now = player.position();
        Vec3 last = LAST.put(player, now);
        if (last == null || !SurvivalConfig.SWIM_HUNGER_ENABLED.get()) return;
        double multiplier = SurvivalConfig.SWIM_EXHAUSTION_MULTIPLIER.get();
        if (multiplier <= 1.0 || player.isSpectator() || player.getAbilities().invulnerable) return;
        SwimHungerRules.Medium medium = SwimHungerRules.medium(player.isSwimming(), player.isEyeInFluid(FluidTags.WATER), player.isInWater());
        if (medium == SwimHungerRules.Medium.NONE) return;
        // vanilla charges nothing for passengers; a player carried by a ship isn't swimming either (asks Sable, so last)
        boolean exempt = player.isPassenger() || ShipEntities.standingOrRiding(player) != null;
        float extra = SwimHungerRules.extraExhaustion(true, multiplier, exempt, medium,
                now.x - last.x, now.y - last.y, now.z - last.z);
        if (extra > 0.0F) player.causeFoodExhaustion(extra);
    }

    /** {@code SERVER_STOPPED}: forget every position. */
    public static void clear() {
        LAST.clear();
    }
}

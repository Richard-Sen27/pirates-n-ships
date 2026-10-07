package com.richardsenger.piratesnships.seachest;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Applies {@link WornRules} to a player every tick, on both logical sides ({@code CommonEvents.PLAYER_TICK_END}).
 *
 * <p>The jump and speed modifiers are transient attribute modifiers with fixed ids, added while a sea chest is worn
 * and the feature is enabled and removed otherwise, so config changes (and the toggle) take effect on the next tick
 * and nothing stays behind. Both sides compute the same, and the server's attribute sync agrees with it.
 *
 * <p>Movement is client-authoritative for real players, so the sink pull only touches a player whose movement this
 * side simulates ({@link Player#isLocalPlayer()}: the client's own player, and GameTest mock players on the server).
 * Sprinting and swimming are cleared on both sides; the client additionally releases the sprint key
 * ({@code client.SeaChestClient}), since the client would otherwise start sprinting again before this hook runs.
 */
public final class SeaChestWearing {

    public static final ResourceLocation JUMP_MODIFIER = Constants.id("sea_chest_no_jump");
    public static final ResourceLocation SPEED_MODIFIER = Constants.id("sea_chest_slow");

    private SeaChestWearing() {
    }

    public static boolean isWearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SeaChestItem;
    }

    public static void onPlayerTick(Player player) {
        boolean wearing = isWearing(player);
        WornRules.Effects e = wearing
                ? WornRules.effects(true, player.isInWater(), player.getAbilities().flying, player.isSprinting(), player.isSwimming(),
                SeaChestConfig.worn())
                : WornRules.Effects.NONE;
        modifier(player, Attributes.JUMP_STRENGTH, JUMP_MODIFIER, e.active(), e.jumpModifier());
        modifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, e.active(), e.speedModifier());
        if (!e.active()) {
            return;
        }
        if (e.clearSprint()) {
            player.setSprinting(false);
        }
        if (e.clearSwim()) {
            player.setSwimming(false);
        }
        if (e.extraVy() != 0.0 && player.isLocalPlayer()) {
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, v.y + e.extraVy(), v.z);
        }
    }

    private static void modifier(Player player, Holder<Attribute> attribute, ResourceLocation id, boolean active, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (!active || amount == 0.0) {
            if (current != null) {
                instance.removeModifier(id);
            }
            return;
        }
        if (current == null || current.amount() != amount) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}

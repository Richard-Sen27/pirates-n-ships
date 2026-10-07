package com.richardsenger.piratesnships.combat.firearms;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * What a gun is loaded with and what leaves the barrel when it fires: a "projectile provider" of
 * {@link FirearmItem}'s loading and aiming sessions (docs/design.md §8.1). The lead ball ({@link FirearmLoads#LEAD_BALL})
 * is the default; other modules register their own through {@link FirearmLoads#register} (GR3: the grappling hook
 * loaded into a musket). The gun's {@code firearm_loaded} state is the service's job: a load only takes its materials,
 * puts its own marker on the gun (a data component), launches its projectile and removes the marker again.
 *
 * <p>Choice: when loading starts, the first registered load that is {@link #offered} wins, else the lead ball; when
 * the gun fires, the first registered load that {@link #isIn} the gun wins, else the lead ball.
 */
public interface FirearmLoad {

    /** Whether pressing use on the unloaded gun (held in {@code gunHand}) loads this, e.g. a hook in the other hand. */
    boolean offered(LivingEntity shooter, InteractionHand gunHand, ItemStack gun, FirearmKind kind);

    /** Whether the shooter has what loading takes (always in creative). */
    boolean canLoad(LivingEntity shooter, InteractionHand gunHand, ItemStack gun);

    /**
     * Takes what the load needs (not in creative) and marks the gun as holding it, once the reload time is over.
     * Returns false and changes nothing when the materials are gone by now.
     */
    boolean load(Level level, LivingEntity shooter, InteractionHand gunHand, ItemStack gun);

    /** True when {@code gun} holds this load. */
    boolean isIn(ItemStack gun);

    /**
     * The trigger was pulled and the shot went off (no misfire): launch the projectile along {@code xRot}/{@code yRot}
     * (the aim after the gun's spread) and remove this load's marker from the gun.
     */
    void launch(ServerLevel level, LivingEntity shooter, ItemStack gun, FirearmKind kind, float xRot, float yRot);

    /** A tooltip line for a gun holding this load, or {@code null}. */
    default @Nullable Component describe(ItemStack gun) {
        return null;
    }

    /** Shown when loading is refused because {@link #canLoad} failed, or {@code null} for the plain click. */
    default @Nullable Component missingMessage() {
        return null;
    }

    /**
     * Shown when use is pressed on a gun that already holds another load while this one is {@link #offered} (the gun
     * then aims with what it holds), or {@code null} for no message.
     */
    default @Nullable Component occupiedMessage() {
        return null;
    }
}

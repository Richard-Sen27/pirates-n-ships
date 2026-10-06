package com.richardsenger.piratesnships.law.proof;

import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Hands out bounty proofs (docs/design.md §13.2). When a player kills a target that has an active bounty, the
 * proof goes straight into the killer's inventory (dropped at the killer's feet if full). Not dropped at the
 * target: most bounty kills happen at sea, where a dropped item sinks or is lost with the ship.
 * A kill by a tamed animal or by no player gives no proof.
 */
public final class ProofDrops {

    private ProofDrops() {
    }

    /** Listener for {@code CommonEvents.LIVING_DEATH}. Never cancels. */
    public static boolean onDeath(LivingEntity victim, DamageSource source) {
        give(victim, source);
        return false;
    }

    /** Gives the proof if the rules allow it; returns the stack given, or {@code null}. */
    public static @Nullable ItemStack give(LivingEntity victim, DamageSource source) {
        if (!(victim.level() instanceof ServerLevel level) || !LawConfig.BOUNTY_PROOF_DROPS.get()) return null;
        if (!(source.getEntity() instanceof Player killer) || killer == victim) return null;
        if (!LawService.hasBounty(level.getServer(), victim.getUUID())) return null;
        BountyProof proof = new BountyProof(victim.getUUID(), victim.getName().getString(),
                LawService.now(level.getServer()), killer.getName().getString());
        ItemStack stack = BountyProofItem.create(proof);
        ItemStack given = stack.copy();
        if (!killer.getInventory().add(stack) && !stack.isEmpty()) killer.drop(stack, false);
        return given;
    }
}

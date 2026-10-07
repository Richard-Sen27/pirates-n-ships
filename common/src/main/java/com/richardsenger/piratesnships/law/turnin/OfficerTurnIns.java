package com.richardsenger.piratesnships.law.turnin;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.bounty.PirateTier;
import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.law.brig.PrisonerOutcomes;
import com.richardsenger.piratesnships.law.content.LawContent;
import com.richardsenger.piratesnships.law.proof.BountyProofItem;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Turning in at a navy officer (docs/design.md §9, §13.2), called from the officer's {@code mobInteract}. Rules in
 * {@link TurnInRules}.
 *
 * <ul>
 *   <li><b>Bounty proof</b> in the hand used: the same claim as {@code /pirates law bounty claim proof}
 *       ({@link LawService#claimWithProof}); the payout goes into the inventory ({@link Wallet#give}), the proof is
 *       consumed, the target's score is wiped as configured.</li>
 *   <li><b>Empty main hand</b> with prisoners in the player's shackles standing within
 *       {@code law.bounty.delivery_range} of the officer: each is delivered alive ({@link PrisonerOutcomes#deliver}).
 *       An NPC is led away by the navy (removed), a player is released from the shackles where they stand with their
 *       score wiped by the claim. The shackles go back into the deliverer's inventory.</li>
 * </ul>
 * Anything else passes, so the officer's other interactions still work.
 */
public final class OfficerTurnIns {

    public static final String MSG = "message." + Constants.MOD_ID + ".officer.";

    private OfficerTurnIns() {
    }

    /**
     * @param officer the navy officer that was right-clicked
     * @param hostile whether the officer is hostile to {@code player} (it attacks wanted players and holds a grudge)
     * @param tierOf  the pirate rank of an NPC prisoner, {@code null} for non-pirates (the mob package knows its pirates)
     */
    public static InteractionResult interact(Mob officer, Player player, InteractionHand hand, boolean hostile,
                                             Function<LivingEntity, @Nullable PirateTier> tierOf) {
        ItemStack stack = player.getItemInHand(hand);
        boolean proof = stack.getItem() instanceof BountyProofItem;
        boolean emptyMain = hand == InteractionHand.MAIN_HAND && stack.isEmpty();
        if (!proof && !emptyMain) return InteractionResult.PASS;
        if (player.level().isClientSide) {
            // The server decides; swing the arm when it will most likely act (the prisoner state is synced)
            return proof || !heldPrisonersNear(officer, player).isEmpty() ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (proof) return turnInProof(officer, player, stack, hostile);
        List<LivingEntity> prisoners = heldPrisonersNear(officer, player);
        if (prisoners.isEmpty()) return InteractionResult.PASS;
        return deliver(officer, player, prisoners, hostile, tierOf);
    }

    /** Prisoners in {@code player}'s shackles (led or not) within the delivery range of the officer, nearest first. */
    public static List<LivingEntity> heldPrisonersNear(Mob officer, Player player) {
        double range = LawConfig.DELIVERY_RANGE.get();
        return BrigService.prisonersWithin(officer.level(), officer.getBoundingBox().inflate(range)).stream()
                .filter(p -> p != player && p != officer && BrigService.state(p).heldBy(player.getUUID()))
                .filter(p -> TurnInRules.inRange(p.distanceToSqr(officer), range))
                .sorted(Comparator.comparingDouble(p -> p.distanceToSqr(officer)))
                .toList();
    }

    private static InteractionResult turnInProof(Mob officer, Player player, ItemStack stack, boolean hostile) {
        TurnInRules.Gate gate = TurnInRules.gate(LawConfig.TURN_IN_OFFICERS.get(), hostile);
        if (gate == TurnInRules.Gate.DISABLED) return InteractionResult.PASS;
        if (gate == TurnInRules.Gate.HOSTILE) return refuse(officer, player, Component.translatable(MSG + "hostile"));
        LawService.ProofClaim claim = LawService.claimWithProof(player, stack);
        if (!claim.success()) {
            String name = claim.proof() == null ? "" : claim.proof().targetName();
            return refuse(officer, player, Component.translatable(MSG + TurnInRules.proofMessage(claim.outcome()), name));
        }
        Wallet.give(player, claim.payout());
        say(player, Component.translatable(MSG + TurnInRules.proofMessage(claim.outcome()),
                claim.proof().targetName(), claim.payout()));
        officer.playSound(SoundEvents.VILLAGER_YES, 1.0f, 0.9f);
        officer.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 0.8f);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult deliver(Mob officer, Player player, List<LivingEntity> prisoners, boolean hostile,
                                             Function<LivingEntity, @Nullable PirateTier> tierOf) {
        TurnInRules.Gate gate = TurnInRules.gate(LawConfig.TURN_IN_OFFICERS.get(), hostile);
        if (gate == TurnInRules.Gate.DISABLED) return InteractionResult.PASS;
        if (gate == TurnInRules.Gate.HOSTILE) return refuse(officer, player, Component.translatable(MSG + "hostile"));
        MinecraftServer server = ((ServerLevel) officer.level()).getServer();
        long total = 0;
        int delivered = 0;
        Component refused = null;
        for (LivingEntity prisoner : prisoners) {
            boolean isPlayer = prisoner instanceof Player;
            boolean bounty = LawService.hasBounty(server, prisoner.getUUID());
            PirateTier tier = isPlayer ? null : tierOf.apply(prisoner);
            int reward = tier == null ? 0 : LawConfig.bountyRules().turnInReward(tier);
            TurnInRules.Delivery d = TurnInRules.delivery(isPlayer, bounty, tier, reward);
            Component name = prisoner.getName().copy();
            if (!d.deliverable()) {
                if (refused == null) refused = name;
                continue;
            }
            PrisonerOutcomes.DeliverResult r = PrisonerOutcomes.deliver(prisoner, player, d.pirateTier());
            if (!r.success()) {
                if (refused == null) refused = name;
                continue;
            }
            total += r.payout();
            delivered++;
            player.getInventory().placeItemBackInInventory(new ItemStack(LawContent.SHACKLES.get()));
            if (prisoner instanceof Player p) {
                p.sendSystemMessage(Component.translatable(MSG + "prisoner.released", officer.getName()).withStyle(ChatFormatting.GRAY));
            } else {
                say(player, Component.translatable(MSG + "prisoner.led_away", name));
                if (officer.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.POOF, prisoner.getX(), prisoner.getY() + 0.8, prisoner.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
                }
            }
        }
        if (delivered == 0) return refuse(officer, player, Component.translatable(MSG + "prisoner.nothing", refused));
        Wallet.give(player, total);
        say(player, Component.translatable(MSG + "prisoner.paid", delivered, total));
        officer.playSound(SoundEvents.VILLAGER_YES, 1.0f, 0.9f);
        officer.level().playSound(null, officer.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 0.8f, 1.2f);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult refuse(Mob officer, Player player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        officer.playSound(SoundEvents.VILLAGER_NO, 1.0f, 0.9f);
        return InteractionResult.CONSUME;
    }

    private static void say(Player player, Component message) {
        player.sendSystemMessage(message.copy().withStyle(ChatFormatting.GOLD));
    }
}

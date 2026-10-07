package com.richardsenger.piratesnships.law.turnin;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.bounty.PirateTier;
import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.law.brig.PrisonerInteractionRules;
import com.richardsenger.piratesnships.law.brig.PrisonerOutcomes;
import com.richardsenger.piratesnships.law.brig.RansomRules;
import com.richardsenger.piratesnships.law.content.LawContent;
import com.richardsenger.piratesnships.law.crime.CriminalRecord;
import com.richardsenger.piratesnships.law.proof.BountyProofItem;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.PortRegistry;
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
 * Dealing with a navy officer (docs/design.md §9, §13.1–§13.3), called from the officer's {@code mobInteract}. Rules in
 * {@link TurnInRules} and {@link PrisonerInteractionRules}.
 *
 * <ul>
 *   <li><b>Bounty proof</b> in the hand used: the same claim as {@code /pirates law bounty claim proof}
 *       ({@link LawService#claimWithProof}); the payout goes into the inventory ({@link Wallet#give}), the proof is
 *       consumed, the target's score is wiped as configured.</li>
 *   <li><b>Empty main hand</b> with prisoners in the player's shackles standing within
 *       {@code law.bounty.delivery_range} of the officer: each is delivered alive ({@link PrisonerOutcomes#deliver}).
 *       An NPC is led away by the navy (removed), a player is released from the shackles where they stand with their
 *       score wiped by the claim. The shackles go back into the deliverer's inventory.</li>
 *   <li><b>Ransom</b> (LA2): in the same hand-over, a prisoner the navy pays nothing for as a turn-in but that is a
 *       navy officer, navy soldier or merchant (sailors, villagers, wandering traders) is ransomed
 *       ({@link PrisonerOutcomes#ransom}, {@code brig.ransom_*}): the officer pays, the prisoner is unshackled and walks
 *       over to him, the shackles come back. Pirates are never ransomed. With {@code law.ransom_needs_port} only an
 *       officer inside a registered navy outpost pays. Behind {@code flags_brig.prisoner_interactions}.</li>
 *   <li><b>Doubloons</b> in the main hand (LA2): pays the fine for the player's criminal score, whole points only, as
 *       many as the coins carried cover ({@code law.fine_cost_per_point}); "You owe the Crown nothing" without a score.
 *       Sneaking with doubloons hands over prisoners near the officer first, if there are any. Behind
 *       {@code law.officer_fines}.</li>
 * </ul>
 * Precedence: {@link PrisonerInteractionRules#officerAction}. A hostile officer refuses everything. Anything else
 * passes, so the officer's other interactions still work.
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
        boolean main = hand == InteractionHand.MAIN_HAND;
        boolean emptyMain = main && stack.isEmpty();
        boolean coins = main && Wallet.isCoin(stack);
        if (!proof && !emptyMain && !coins) return InteractionResult.PASS;
        boolean prisonersNear = !proof && !heldPrisonersNear(officer, player).isEmpty();
        PrisonerInteractionRules.OfficerAction action = PrisonerInteractionRules.officerAction(proof, emptyMain, coins,
                player.isShiftKeyDown(), prisonersNear);
        if (action == PrisonerInteractionRules.OfficerAction.FINE && !LawConfig.OFFICER_FINES.get()) return InteractionResult.PASS;
        if (player.level().isClientSide) {
            // The server decides; swing the arm when it will most likely act (the prisoner state is synced)
            return action == PrisonerInteractionRules.OfficerAction.PASS ? InteractionResult.PASS : InteractionResult.SUCCESS;
        }
        return switch (action) {
            case PROOF -> turnInProof(officer, player, stack, hostile);
            case PRISONERS -> deliver(officer, player, heldPrisonersNear(officer, player), hostile, tierOf);
            case FINE -> payFine(officer, player, hostile);
            case PASS -> InteractionResult.PASS;
        };
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
        boolean ransoms = LawConfig.PRISONER_INTERACTIONS.get();
        boolean portAllows = PrisonerInteractionRules.portAllows(LawConfig.RANSOM_NEEDS_PORT.get(), inNavyPort(officer));
        long total = 0;
        int handedOver = 0;
        Component refused = null;
        boolean noPort = false;
        for (LivingEntity prisoner : prisoners) {
            boolean isPlayer = prisoner instanceof Player;
            boolean bounty = LawService.hasBounty(server, prisoner.getUUID());
            PirateTier tier = isPlayer ? null : tierOf.apply(prisoner);
            int reward = tier == null ? 0 : LawConfig.bountyRules().turnInReward(tier);
            TurnInRules.Delivery d = TurnInRules.delivery(isPlayer, bounty, tier, reward);
            RansomRules.Kind kind = ransoms ? PrisonerOutcomes.ransomKindOf(prisoner) : null;
            Component name = prisoner.getName().copy();
            switch (PrisonerInteractionRules.disposition(d, kind, isPlayer, portAllows)) {
                case DELIVER -> {
                    PrisonerOutcomes.DeliverResult r = PrisonerOutcomes.deliver(prisoner, player, d.pirateTier());
                    if (!r.success()) {
                        if (refused == null) refused = name;
                        continue;
                    }
                    total += r.payout();
                    handedOver++;
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
                case RANSOM -> {
                    PrisonerOutcomes.RansomResult r = PrisonerOutcomes.ransom(prisoner, kind, false, officer);
                    if (!r.success()) {
                        if (refused == null) refused = name;
                        continue;
                    }
                    total += r.amount();
                    handedOver++;
                    player.getInventory().placeItemBackInInventory(new ItemStack(LawContent.SHACKLES.get()));
                    say(player, Component.translatable(MSG + "prisoner.ransomed", name, r.amount()));
                }
                case NO_PORT -> {
                    noPort = true;
                    if (refused == null) refused = name;
                }
                case REFUSE -> {
                    if (refused == null) refused = name;
                }
            }
        }
        if (handedOver == 0) {
            return refuse(officer, player, noPort ? Component.translatable(MSG + "prisoner.no_port")
                    : Component.translatable(MSG + "prisoner.nothing", refused));
        }
        Wallet.give(player, total);
        say(player, Component.translatable(MSG + "prisoner.paid", handedOver, total));
        officer.playSound(SoundEvents.VILLAGER_YES, 1.0f, 0.9f);
        officer.level().playSound(null, officer.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 0.8f, 1.2f);
        return InteractionResult.CONSUME;
    }

    /**
     * Pays the player's fine with the doubloons they carry (docs/design.md §13.1, LA2): whole points only, as many as
     * the coins cover ({@link PrisonerInteractionRules#fineQuote}), through {@link LawService#payFine}; the coins are
     * taken from the inventory. A hostile officer refuses.
     */
    private static InteractionResult payFine(Mob officer, Player player, boolean hostile) {
        if (hostile) return refuse(officer, player, Component.translatable(MSG + "hostile"));
        double score = LawService.score(player);
        if (score <= 0) {
            say(player, Component.translatable(MSG + "fine.nothing_owed"));
            officer.playSound(SoundEvents.VILLAGER_AMBIENT, 1.0f, 0.9f);
            return InteractionResult.CONSUME;
        }
        int cost = LawConfig.FINE_COST_PER_POINT.get();
        PrisonerInteractionRules.FineQuote quote = PrisonerInteractionRules.fineQuote(score, Wallet.count(player), cost);
        if (quote.points() <= 0) return refuse(officer, player, Component.translatable(MSG + "fine.too_poor", cost));
        CriminalRecord.FineResult r = LawService.payFine(player, (int) Math.min(Integer.MAX_VALUE, quote.doubloons()));
        switch (r.outcome()) {
            case REFUSED_NOTORIOUS -> {
                return refuse(officer, player, Component.translatable(MSG + "fine.notorious"));
            }
            case NOTHING_OWED, NOTHING_OFFERED -> {
                say(player, Component.translatable(MSG + "fine.nothing_owed"));
                return InteractionResult.CONSUME;
            }
            default -> {
            }
        }
        Wallet.take(player, r.doubloonsSpent());
        say(player, Component.translatable(MSG + (quote.clearsScore() ? "fine.cleared" : "fine.paid"), quote.points(), r.doubloonsSpent()));
        officer.playSound(SoundEvents.VILLAGER_YES, 1.0f, 0.9f);
        officer.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 0.6f);
        return InteractionResult.CONSUME;
    }

    /** Whether the officer stands inside a registered navy outpost (world/port, WG1). */
    private static boolean inNavyPort(Mob officer) {
        if (!(officer.level() instanceof ServerLevel level)) return false;
        return PortRegistry.get(level.getServer()).index().all().stream()
                .anyMatch(p -> p.kind() == PortKind.NAVY_OUTPOST && p.contains(level.dimension(), officer.blockPosition()));
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

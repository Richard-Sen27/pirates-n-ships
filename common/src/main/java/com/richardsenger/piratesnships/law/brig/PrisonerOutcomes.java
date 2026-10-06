package com.richardsenger.piratesnships.law.brig;

import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.bounty.BountyBoard.ClaimMethod;
import com.richardsenger.piratesnships.law.bounty.BountyBoard.ClaimResult;
import com.richardsenger.piratesnships.law.bounty.PirateTier;
import com.richardsenger.piratesnships.law.content.LawContent;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.crime.CriminalRecord.CrimeResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What to do with a prisoner (design.md §13.3), for later NPC interactions (navy officer, harbor master, crew) and
 * the {@code /pirates brig} debug commands. Server side. The caller pays doubloons: amounts are plain numbers.
 * Every method refuses ({@code success == false}, prisoner untouched) if the entity is not a prisoner.
 */
public final class PrisonerOutcomes {

    public enum Failure { NONE, NOT_A_PRISONER, NOTHING_TO_CLAIM, NOT_FOR_PLAYERS }

    /**
     * @param payout  doubloons to give the claimant: the bounty, plus the pirate turn-in reward if a tier was given
     * @param bounty  the law service's claim result ({@code null} if not attempted)
     */
    public record DeliverResult(boolean success, Failure failure, int payout, @Nullable ClaimResult bounty) {
    }

    public record RansomResult(boolean success, Failure failure, int amount) {
    }

    /**
     * For the later crew system: who was press-ganged, with its saved entity data (the entity itself is removed; the
     * crew system spawns its own crew member), and the starting morale. {@code crime} = the PRESS_GANG report.
     */
    public record Recruit(UUID id, EntityType<?> type, String name, CompoundTag data, double morale, CrimeResult crime) {
    }

    public record PressGangResult(boolean success, Failure failure, @Nullable Recruit recruit) {
    }

    /** {@code faction}: whose reputation should rise, once mobs have factions and reputation exists (§15). */
    public record ReleaseResult(boolean success, Failure failure, @Nullable String faction) {
    }

    private PrisonerOutcomes() {
    }

    /**
     * Delivers a prisoner to the navy (§13.2). With a {@code pirateTier} it is a pirate turn-in (rank reward plus any
     * bounty, via {@link LawService#turnInPirate}); without one it is a bounty claim (alive). Without any bounty and
     * without a tier there is nothing to claim. On success a mob is removed (handed over); a player is set free.
     */
    public static DeliverResult deliver(LivingEntity prisoner, Player claimant, @Nullable PirateTier pirateTier) {
        if (!BrigService.isPrisoner(prisoner)) return new DeliverResult(false, Failure.NOT_A_PRISONER, 0, null);
        int payout;
        ClaimResult claim;
        if (pirateTier != null && !(prisoner instanceof Player)) {
            LawService.TurnInResult r = LawService.turnInPirate(prisoner, pirateTier, claimant);
            payout = r.total();
            claim = r.bounty();
        } else {
            claim = LawService.claimBounty(prisoner, claimant, ClaimMethod.ALIVE);
            if (!claim.success()) return new DeliverResult(false, Failure.NOTHING_TO_CLAIM, 0, claim);
            payout = claim.payout();
        }
        handOver(prisoner);
        return new DeliverResult(true, Failure.NONE, payout, claim);
    }

    /** Ransoms a prisoner at its faction's port. NPCs only. The prisoner is handed back to its faction (removed). */
    public static RansomResult ransom(LivingEntity prisoner, RansomRules.Kind kind, boolean captain) {
        if (!BrigService.isPrisoner(prisoner)) return new RansomResult(false, Failure.NOT_A_PRISONER, 0);
        if (prisoner instanceof Player) return new RansomResult(false, Failure.NOT_FOR_PLAYERS, 0);
        int amount = RansomRules.ransom(kind, captain, BrigConfig.ransomParams());
        handOver(prisoner);
        return new RansomResult(true, Failure.NONE, amount);
    }

    /**
     * Press-gangs a prisoner into {@code captain}'s crew. NPCs only. Reports the PRESS_GANG crime for the captain
     * (§13.3: "pirates only, raises the criminal score"); the crime is reported whoever does it, and that is what
     * makes it a pirate act.
     */
    public static PressGangResult pressGang(LivingEntity prisoner, Player captain) {
        if (!BrigService.isPrisoner(prisoner)) return new PressGangResult(false, Failure.NOT_A_PRISONER, null);
        if (prisoner instanceof Player) return new PressGangResult(false, Failure.NOT_FOR_PLAYERS, null);
        CrimeResult crime = LawService.reportCrime(captain, CrimeType.PRESS_GANG, prisoner);
        BrigService.clear(prisoner);
        CompoundTag data = prisoner.saveWithoutId(new CompoundTag());
        Recruit recruit = new Recruit(prisoner.getUUID(), prisoner.getType(), prisoner.getName().getString(), data,
                BrigConfig.PRESS_GANG_MORALE.get(), crime);
        prisoner.discard();
        return new PressGangResult(true, Failure.NONE, recruit);
    }

    /** Sets a prisoner free; the shackles drop at its feet. */
    public static ReleaseResult release(LivingEntity prisoner) {
        if (!BrigService.isPrisoner(prisoner)) return new ReleaseResult(false, Failure.NOT_A_PRISONER, null);
        BrigService.clear(prisoner);
        prisoner.spawnAtLocation(new ItemStack(LawContent.SHACKLES.get()));
        // Reputation gain with the prisoner's faction goes here once factions on mobs and reputation exist.
        return new ReleaseResult(true, Failure.NONE, null);
    }

    private static void handOver(LivingEntity prisoner) {
        BrigService.clear(prisoner);
        if (!(prisoner instanceof Player)) prisoner.discard();
    }
}

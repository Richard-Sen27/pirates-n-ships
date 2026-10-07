package com.richardsenger.piratesnships.law.brig;

import com.richardsenger.piratesnships.crew.CrewConfig;
import com.richardsenger.piratesnships.crew.morale.CrewMorale;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.bounty.BountyBoard.ClaimMethod;
import com.richardsenger.piratesnships.law.bounty.BountyBoard.ClaimResult;
import com.richardsenger.piratesnships.law.bounty.PirateTier;
import com.richardsenger.piratesnships.law.content.LawContent;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.crime.CriminalRecord.CrimeResult;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.mob.entity.Sailor;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.winch.CaptainsWhistleItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What to do with a prisoner (design.md §13.3). Reached through the NPC interactions of LA2
 * ({@link PrisonerInteractions}, {@code law.turnin.OfficerTurnIns}) and the {@code /pirates brig} debug commands,
 * which use the same methods. Server side. The caller pays doubloons: amounts are plain numbers. Every method refuses
 * ({@code success == false}, prisoner untouched) if the entity is not a prisoner.
 */
public final class PrisonerOutcomes {

    public enum Failure { NONE, NOT_A_PRISONER, NOTHING_TO_CLAIM, NOT_FOR_PLAYERS, NOT_A_SAILOR, NOT_ON_SHIP, NOT_YOUR_SHIP, DISABLED }

    /**
     * @param payout  doubloons to give the claimant: the bounty, plus the pirate turn-in reward if a tier was given
     * @param bounty  the law service's claim result ({@code null} if not attempted)
     */
    public record DeliverResult(boolean success, Failure failure, int payout, @Nullable ClaimResult bounty) {
    }

    public record RansomResult(boolean success, Failure failure, int amount) {
    }

    /**
     * @param crew  the crew member that took the sailor's place ({@code null} on failure)
     * @param crime the PRESS_GANG report for the captain ({@code null} on failure)
     */
    public record PressGangResult(boolean success, Failure failure, @Nullable CrewMember crew, @Nullable CrimeResult crime) {
    }

    /** {@code faction}: the released mob's faction as recorded for the releaser ({@code null}: none). */
    public record ReleaseResult(boolean success, Failure failure, @Nullable Faction faction) {
    }

    private PrisonerOutcomes() {
    }

    // --- Who is who ---------------------------------------------------------------------------------------------

    /**
     * The faction whose reputation a release concerns (§15): navy mobs (and {@code #pirates_n_ships:navy}), pirates,
     * and merchants (sailors, villagers, wandering traders: {@code #pirates_n_ships:law_protected}); else none.
     */
    public static @Nullable Faction factionOf(LivingEntity mob) {
        if (mob instanceof Player) return null;
        if (mob instanceof Pirate) return Faction.PIRATES;
        if (LawService.isNavy(mob)) return Faction.NAVY;
        if (mob instanceof Sailor || LawService.isLawProtected(mob)) return Faction.MERCHANTS;
        return null;
    }

    /**
     * What a prisoner is worth as a ransom to the navy, {@code null} if it can't be ransomed (pirates, players,
     * monsters): a navy officer, a navy soldier (common) or a merchant (sailors, villagers, wandering traders).
     */
    @Nullable
    public static RansomRules.Kind ransomKindOf(LivingEntity mob) {
        if (mob instanceof Player || mob instanceof Pirate) return null;
        if (mob instanceof NavyOfficer) return RansomRules.Kind.NAVY_OFFICER;
        if (mob instanceof NavySoldier || LawService.isNavy(mob)) return RansomRules.Kind.COMMON;
        if (mob instanceof Sailor || LawService.isLawProtected(mob)) return RansomRules.Kind.MERCHANT;
        return null;
    }

    // --- Outcomes -----------------------------------------------------------------------------------------------

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

    /** Ransom without anyone to receive the prisoner (debug command): it is handed back to its faction (removed). */
    public static RansomResult ransom(LivingEntity prisoner, RansomRules.Kind kind, boolean captain) {
        return ransom(prisoner, kind, captain, null);
    }

    /**
     * Ransoms a prisoner. NPCs only. With a {@code receiver} (the navy officer who pays) the prisoner is unshackled and
     * walks over to it; without one it is handed back off-screen (removed).
     */
    public static RansomResult ransom(LivingEntity prisoner, RansomRules.Kind kind, boolean captain, @Nullable Mob receiver) {
        if (!BrigService.isPrisoner(prisoner)) return new RansomResult(false, Failure.NOT_A_PRISONER, 0);
        if (prisoner instanceof Player) return new RansomResult(false, Failure.NOT_FOR_PLAYERS, 0);
        int amount = RansomRules.ransom(kind, captain, BrigConfig.ransomParams());
        if (receiver == null) {
            handOver(prisoner);
        } else {
            BrigService.clear(prisoner);
            if (prisoner instanceof Mob mob) mob.getNavigation().moveTo(receiver, 1.0);
        }
        return new RansomResult(true, Failure.NONE, amount);
    }

    /** The press-gang check for {@code prisoner} and {@code captain} in the world (§13.3, LA2). */
    public static PrisonerInteractionRules.PressGang checkPressGang(LivingEntity prisoner, Player captain) {
        boolean prisonerNow = BrigService.isPrisoner(prisoner);
        boolean sailor = prisoner instanceof Sailor;
        ShipBody ship = prisonerNow && sailor && prisoner.level() instanceof ServerLevel level
                ? CaptainsWhistleItem.shipOf(level, prisoner) : null;
        UUID owner = ship == null ? null : ShipRegistry.get(ship.level().getServer()).find(ship.id())
                .flatMap(ShipData::owner).orElse(null);
        return PrisonerInteractionRules.pressGang(LawConfig.PRISONER_INTERACTIONS.get(), prisonerNow, sailor, ship != null,
                owner, captain.getUUID());
    }

    /**
     * Press-gangs a shackled sailor standing on {@code captain}'s ship (owned by the captain or by nobody) into the
     * crew: a {@link CrewMember} takes its place (same position, facing and name; the crew member's own look), with
     * morale at {@code crew.morale.press_gang_start}, and the PRESS_GANG crime is reported for the captain (§13.3:
     * "pirates only, raises the criminal score"). The sailor is removed.
     */
    public static PressGangResult pressGang(LivingEntity prisoner, Player captain) {
        PrisonerInteractionRules.PressGang check = checkPressGang(prisoner, captain);
        if (!check.ok()) return new PressGangResult(false, failureOf(check), null, null);
        ServerLevel level = (ServerLevel) prisoner.level();
        CrewMember crew = StationContent.CREW_MEMBER.get().create(level);
        if (crew == null) return new PressGangResult(false, Failure.NOT_A_SAILOR, null, null);
        crew.moveTo(prisoner.getX(), prisoner.getY(), prisoner.getZ(), prisoner.getYRot(), prisoner.getXRot());
        crew.setYHeadRot(prisoner.getYHeadRot());
        if (prisoner.hasCustomName()) crew.setCustomName(prisoner.getCustomName());
        CrewMorale.adjust(crew, CrewConfig.PRESS_GANG_START.get() - CrewMorale.get(crew), "press-ganged");
        CrimeResult crime = LawService.reportCrime(captain, CrimeType.PRESS_GANG, prisoner);
        BrigService.clear(prisoner);
        prisoner.discard();
        level.addFreshEntity(crew);
        return new PressGangResult(true, Failure.NONE, crew, crime);
    }

    /** Sets a prisoner free without anyone to credit (debug command): the shackles drop at its feet, nothing recorded. */
    public static ReleaseResult release(LivingEntity prisoner) {
        return release(prisoner, null);
    }

    /**
     * Sets a prisoner free: unshackled and no longer led, its own AI and hostility rules back (a released pirate may
     * attack). The shackles go back into {@code releaser}'s inventory and the release is appended to its law record
     * ({@link LawService#recordRelease}); without a releaser they drop at the prisoner's feet.
     */
    public static ReleaseResult release(LivingEntity prisoner, @Nullable Player releaser) {
        if (!BrigService.isPrisoner(prisoner)) return new ReleaseResult(false, Failure.NOT_A_PRISONER, null);
        BrigService.clear(prisoner);
        Faction faction = factionOf(prisoner);
        if (releaser == null) {
            prisoner.spawnAtLocation(new ItemStack(LawContent.SHACKLES.get()));
        } else {
            releaser.getInventory().placeItemBackInInventory(new ItemStack(LawContent.SHACKLES.get()));
            LawService.recordRelease(releaser, faction);
        }
        return new ReleaseResult(true, Failure.NONE, faction);
    }

    private static Failure failureOf(PrisonerInteractionRules.PressGang check) {
        return switch (check) {
            case OK -> Failure.NONE;
            case DISABLED -> Failure.DISABLED;
            case NOT_A_PRISONER -> Failure.NOT_A_PRISONER;
            case NOT_A_SAILOR -> Failure.NOT_A_SAILOR;
            case NOT_ON_SHIP -> Failure.NOT_ON_SHIP;
            case NOT_YOUR_SHIP -> Failure.NOT_YOUR_SHIP;
        };
    }

    private static void handOver(LivingEntity prisoner) {
        BrigService.clear(prisoner);
        if (!(prisoner instanceof Player)) prisoner.discard();
    }
}

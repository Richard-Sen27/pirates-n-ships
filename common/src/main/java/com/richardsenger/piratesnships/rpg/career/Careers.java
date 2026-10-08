package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.rpg.deeds.DeedContext;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * The careers API (docs/design.md §15, CAR1), server side: the navy rank ladder, pirate infamy and the letter of
 * marque. Records live in the {@link CareerAttachments#CAREER} attachment; rules in {@link CareerRules}.
 *
 * <ul>
 *   <li>Deeds ({@code rpg.deeds.Deeds}) reach {@link #onDeed} through {@link CareerDeeds}: counters, desertion, the
 *       letter's void and prize money, then {@link #promoteIfEligible}.</li>
 *   <li>The officer screen ({@link CareerBackend}) calls {@link #enlist}, {@link #resign}, {@link #grantLetter} and
 *       {@link #collectPrize}.</li>
 *   <li>Promotions are automatic and announced to the player; ranks are never lost by falling reputation, only by
 *       desertion ({@link #desert}) or resigning.</li>
 * </ul>
 * Everything that changes a career is a no-op while {@code careers.enabled} is off, except the operator commands.
 */
public final class Careers {

    private Careers() {
    }

    public static boolean enabled() {
        return CareerConfig.ENABLED.get();
    }

    public static CareerRecord record(Player player) {
        return Services.ATTACHMENTS.get(player, CareerAttachments.CAREER);
    }

    public static NavyRank navyRank(Player player) {
        return record(player).navy();
    }

    public static InfamyRank infamy(Player player) {
        return record(player).infamy();
    }

    /** Stores {@code record}, syncs it to the player's client when it changed and moves the player's title team. */
    public static void store(Player player, CareerRecord record) {
        Services.ATTACHMENTS.set(player, CareerAttachments.CAREER, record);
        if (player instanceof ServerPlayer sp) {
            CareerSync.sendIfChanged(sp, record);
            CareerTeams.refresh(sp, record); // HON1: the title prefix follows promotion, demotion and resignation
        }
    }

    static long now(Player player) {
        MinecraftServer server = player.getServer();
        if (server == null) throw new IllegalStateException("Careers are server-side only");
        return server.overworld().getGameTime();
    }

    private static int navyRep(Player player) {
        return Reputation.get(player, Faction.NAVY);
    }

    private static int pirateRep(Player player) {
        return Reputation.get(player, Faction.PIRATES);
    }

    // ------------------------------------------------------------------ promotions

    /**
     * Promotes the player as far as the rules allow on both ladders, announcing each new rank. Returns whether any
     * promotion happened. Nothing while careers are off.
     */
    public static boolean promoteIfEligible(ServerPlayer player) {
        if (!enabled()) return false;
        CareerThresholds t = CareerConfig.thresholds();
        CareerRecord r = record(player);
        int navy = navyRep(player);
        int pirates = pirateRep(player);
        boolean promoted = false;
        for (Optional<NavyRank> next = CareerRules.nextNavyRank(r, navy, pirates, t); next.isPresent();
             next = CareerRules.nextNavyRank(r, navy, pirates, t)) {
            r = r.withNavy(next.get());
            promoted = true;
            say(player, Component.translatable(CareerText.PROMOTED_NAVY, Component.translatable(next.get().nameKey())), ChatFormatting.GOLD);
        }
        for (Optional<InfamyRank> next = CareerRules.nextInfamyRank(r, pirates, t); next.isPresent();
             next = CareerRules.nextInfamyRank(r, pirates, t)) {
            r = r.withInfamy(next.get());
            promoted = true;
            say(player, Component.translatable(CareerText.PROMOTED_INFAMY, Component.translatable(next.get().nameKey())), ChatFormatting.DARK_RED);
        }
        if (promoted) {
            store(player, r);
            Constants.LOG.debug("Career of {}: navy {}, infamy {}", player.getName().getString(), r.navy().id(), r.infamy().id());
        }
        return promoted;
    }

    // ------------------------------------------------------------------ service

    /** Whether the player may enlist now (reputation, bounty, infamy). */
    public static CareerRules.EnlistVerdict enlistVerdict(ServerPlayer player) {
        return CareerRules.enlist(record(player), navyRep(player), pirateRep(player),
                LawService.hasBounty(player.server, player.getUUID()), Reputation.navyHostile(player), enabled(),
                CareerConfig.thresholds());
    }

    /**
     * Enlists the player as a midshipman if {@link #enlistVerdict} allows; a letter of marque the player holds is
     * surrendered (the prize money stays). Then promotes as far as the record allows.
     */
    public static CareerRules.EnlistVerdict enlist(ServerPlayer player) {
        CareerRules.EnlistVerdict v = enlistVerdict(player);
        if (v != CareerRules.EnlistVerdict.OK) return v;
        CareerRecord r = record(player).withNavy(NavyRank.MIDSHIPMAN);
        if (r.letter() == LetterState.ACTIVE) r = r.withLetter(LetterState.NONE, 0L);
        store(player, r);
        say(player, Component.translatable(CareerText.ENLISTED, Component.translatable(NavyRank.MIDSHIPMAN.nameKey())), ChatFormatting.GOLD);
        promoteIfEligible(player);
        return v;
    }

    /** Leaves the navy honourably: the rank is gone, no crime. False if the player did not serve. */
    public static boolean resign(ServerPlayer player) {
        CareerRecord r = record(player);
        if (!r.enlisted()) return false;
        store(player, r.withNavy(NavyRank.NONE));
        say(player, Component.translatable(CareerText.RESIGNED), ChatFormatting.GOLD);
        return true;
    }

    /**
     * Desertion: the player loses the navy rank and, with {@code careers.desertion_is_crime}, the
     * {@link CrimeType#DESERTION} crime goes on the criminal record (the law sets the bounty). False if the player did
     * not serve.
     */
    public static boolean desert(ServerPlayer player) {
        CareerRecord r = record(player);
        if (!r.enlisted()) return false;
        store(player, r.withNavy(NavyRank.NONE));
        onDeserted(player);
        return true;
    }

    private static void onDeserted(ServerPlayer player) {
        boolean crime = CareerConfig.DESERTION_IS_CRIME.get();
        if (crime) LawService.reportCrime(player, CrimeType.DESERTION, (UUID) null);
        say(player, Component.translatable(crime ? CareerText.DESERTED_CRIME : CareerText.DESERTED), ChatFormatting.RED);
        Constants.LOG.debug("{} deserted the navy", player.getName().getString());
    }

    // ------------------------------------------------------------------ letter of marque

    public static CareerRules.LetterVerdict letterVerdict(ServerPlayer player) {
        return CareerRules.letter(record(player), navyRep(player), now(player), Wallet.count(player), enabled(),
                CareerConfig.thresholds());
    }

    /** Sells the player a letter of marque for {@code careers.letter.fee} if {@link #letterVerdict} allows. */
    public static CareerRules.LetterVerdict grantLetter(ServerPlayer player) {
        CareerRules.LetterVerdict v = letterVerdict(player);
        if (v != CareerRules.LetterVerdict.OK) return v;
        long fee = CareerConfig.thresholds().letter().fee();
        if (fee > 0 && !Wallet.take(player, fee)) return CareerRules.LetterVerdict.TOO_POOR;
        store(player, record(player).withLetter(LetterState.ACTIVE, 0L));
        say(player, Component.translatable(CareerText.LETTER_GRANTED, fee), ChatFormatting.GOLD);
        return v;
    }

    /** Operators: a letter without checks or fee (ends a player's service, which a letter excludes). */
    public static void forceLetter(ServerPlayer player) {
        CareerRecord r = record(player);
        store(player, r.withNavy(NavyRank.NONE).withLetter(LetterState.ACTIVE, 0L));
    }

    /** Voids an active letter; a new one only after {@code careers.letter.void_days}. False if none was held. */
    public static boolean voidLetter(ServerPlayer player) {
        CareerRecord r = record(player);
        if (r.letter() != LetterState.ACTIVE) return false;
        store(player, r.withLetter(LetterState.VOIDED, now(player) + CareerConfig.thresholds().letter().voidTicks()));
        say(player, Component.translatable(CareerText.LETTER_VOIDED), ChatFormatting.RED);
        return true;
    }

    /** Pays out the prize money waiting; returns the doubloons paid (0: none owed). */
    public static long collectPrize(ServerPlayer player) {
        CareerRecord r = record(player);
        long prize = r.prizeMoney();
        if (prize <= 0) return 0;
        store(player, r.withPrize(0));
        Wallet.give(player, prize);
        say(player, Component.translatable(CareerText.PRIZE_COLLECTED, prize), ChatFormatting.GOLD);
        return prize;
    }

    // ------------------------------------------------------------------ deeds and quests

    /** What a deed's victim is to the counters: a configured captain or officer kind, or anything else. */
    public static CareerRules.Victim victimOf(DeedContext context) {
        if (context.victimKind().isEmpty()) return CareerRules.Victim.OTHER;
        String kind = context.victimKind().get().toString();
        if (CareerConfig.CAPTAIN_KINDS.get().contains(kind)) return CareerRules.Victim.CAPTAIN;
        if (CareerConfig.OFFICER_KINDS.get().contains(kind)) return CareerRules.Victim.OFFICER;
        return CareerRules.Victim.OTHER;
    }

    /** A deed of the player ({@link CareerDeeds}): counters, desertion, the letter, prize money, promotions. */
    public static CareerRules.DeedOutcome onDeed(ServerPlayer player, Deed deed, DeedContext context) {
        CareerRecord before = record(player);
        if (!enabled()) return new CareerRules.DeedOutcome(before, false, false, 0);
        CareerRules.DeedOutcome out = CareerRules.onDeed(before, deed, victimOf(context), context.amount(), now(player),
                CareerConfig.thresholds());
        if (!out.record().equals(before)) store(player, out.record());
        if (out.deserted()) onDeserted(player);
        if (out.letterVoided()) say(player, Component.translatable(CareerText.LETTER_VOIDED), ChatFormatting.RED);
        if (out.prize() > 0) {
            player.displayClientMessage(Component.translatable(CareerText.PRIZE_EARNED, out.prize(), out.record().prizeMoney())
                    .withStyle(ChatFormatting.GOLD), true);
        }
        promoteIfEligible(player);
        return out;
    }

    /** A quest done for {@code faction} counts toward the careers (QST1's quest deeds arrive here through {@link #onDeed}). */
    public static void recordQuest(ServerPlayer player, Faction faction) {
        if (!enabled()) return;
        CareerCounter c = switch (faction) {
            case NAVY -> CareerCounter.NAVY_QUESTS;
            case PIRATES -> CareerCounter.PIRATE_QUESTS;
            case VILLAGERS -> CareerCounter.VILLAGE_QUESTS;
        };
        store(player, record(player).plus(c, 1));
        promoteIfEligible(player);
    }

    // ------------------------------------------------------------------ operators

    /** Sets the navy rank (operators); {@code NONE} ends the service without desertion. */
    public static void setNavy(ServerPlayer player, NavyRank rank) {
        CareerRecord r = record(player).withNavy(rank);
        if (rank != NavyRank.NONE && r.letter() == LetterState.ACTIVE) r = r.withLetter(LetterState.NONE, 0L);
        store(player, r);
    }

    /** Sets the infamy (operators); turning pirate while in service is desertion. */
    public static void setInfamy(ServerPlayer player, InfamyRank rank) {
        if (CareerRules.turnsPirate(record(player), rank)) desert(player);
        store(player, record(player).withInfamy(rank));
    }

    // ------------------------------------------------------------------ text

    /** One line: navy rank, infamy and the letter (for {@code /pirates rep} and {@code /pirates career}). */
    public static Component describe(Player player) {
        CareerRecord r = record(player);
        return Component.translatable(CareerText.DESCRIBE, navyName(r), Component.translatable(r.infamy().nameKey()),
                Component.translatable(r.letter().nameKey()));
    }

    static Component navyName(CareerRecord r) {
        return r.enlisted() ? Component.translatable(r.navy().nameKey()) : Component.translatable(NavyRank.NONE.nameKey());
    }

    private static void say(ServerPlayer player, Component message, ChatFormatting style) {
        player.sendSystemMessage(message.copy().withStyle(style));
    }
}

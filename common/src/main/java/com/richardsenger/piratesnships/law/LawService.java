package com.richardsenger.piratesnships.law;

import com.richardsenger.piratesnships.law.bounty.BountyBoard;
import com.richardsenger.piratesnships.law.bounty.BountyBoard.ClaimMethod;
import com.richardsenger.piratesnships.law.bounty.BountyBoard.ClaimResult;
import com.richardsenger.piratesnships.law.bounty.BountyBoard.NavyChange;
import com.richardsenger.piratesnships.law.bounty.BountyBoard.PlaceResult;
import com.richardsenger.piratesnships.law.bounty.BountyRules;
import com.richardsenger.piratesnships.law.bounty.BountyTarget;
import com.richardsenger.piratesnships.law.bounty.PirateTier;
import com.richardsenger.piratesnships.law.brig.ReleaseRecord;
import com.richardsenger.piratesnships.law.crime.CrimeRules;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.crime.CriminalRecord;
import com.richardsenger.piratesnships.law.crime.CriminalRecord.CrimeResult;
import com.richardsenger.piratesnships.law.crime.CriminalRecord.FineResult;
import com.richardsenger.piratesnships.law.crime.WantedLevel;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.law.flag.FalseColorsDetection;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.law.flag.FlagLaw;
import com.richardsenger.piratesnships.law.flag.Reaction;
import com.richardsenger.piratesnships.law.flag.ShipStance;
import com.richardsenger.piratesnships.law.world.FlagCrimes;
import com.richardsenger.piratesnships.law.proof.BountyProof;
import com.richardsenger.piratesnships.law.proof.BountyProofItem;
import com.richardsenger.piratesnships.law.world.CrimeLog;
import com.richardsenger.piratesnships.law.world.LawTags;
import com.richardsenger.piratesnships.law.world.NavyHostility;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.rpg.deeds.LawDeeds;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The law API for the rest of the mod (server side only). Reads config through {@link LawConfig}, stores criminal
 * records in {@link LawAttachments#CRIMINAL_RECORD} and bounties in {@link BountyBoardData}. Game time is the
 * overworld game time.
 *
 * <pre>{@code
 * // navy mob hurt by a player
 * LawService.reportCrime(player, CrimeType.ATTACK_NAVY, navySailor);
 * // navy AI
 * if (LawService.wantedLevel(player).atLeast(WantedLevel.WANTED)) attack(player);
 * // notice board
 * List<BountyBoard.Notice> notices = LawService.notices(server);
 * // navy officer, shackled prisoner delivered
 * ClaimResult r = LawService.claimBounty(prisoner, player, ClaimMethod.ALIVE);
 * if (r.success()) giveDoubloons(player, r.payout());
 * }</pre>
 *
 * Decay is applied lazily whenever a record is read or changed, and every {@link #TICK_INTERVAL} ticks for online
 * players (so navy bounties are withdrawn while they play). Offline players and unloaded NPCs decay when next read.
 */
public final class LawService {

    /** How often online players are decayed and their navy bounty synced. */
    public static final int TICK_INTERVAL = 100;

    private LawService() {
    }

    // --- Time and targets ---------------------------------------------------------------------------------------

    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public static BountyTarget targetOf(Entity entity) {
        String name = entity.getName().getString();
        return entity instanceof Player
                ? BountyTarget.player(entity.getUUID(), name)
                : BountyTarget.npc(entity.getUUID(), name);
    }

    // --- Criminal score -----------------------------------------------------------------------------------------

    /** The entity's record, decayed to now. */
    public static CriminalRecord record(LivingEntity entity) {
        CrimeRules rules = LawConfig.crimeRules();
        CriminalRecord stored = Services.ATTACHMENTS.get(entity, LawAttachments.CRIMINAL_RECORD);
        CriminalRecord decayed = stored.decayTo(now(server(entity)), rules);
        // Linear decay: skipping the write when only lastUpdate moved gives the same result later.
        if (decayed.score() != stored.score() || !decayed.recent().equals(stored.recent())) {
            store(entity, decayed);
        }
        return decayed;
    }

    public static double score(LivingEntity entity) {
        return LawConfig.CRIMINAL_SCORE_ENABLED.get() ? record(entity).score() : 0.0;
    }

    public static WantedLevel wantedLevel(LivingEntity entity) {
        CrimeRules rules = LawConfig.crimeRules();
        return rules.enabled() ? rules.wantedLevel(record(entity).score()) : WantedLevel.CLEAN;
    }

    /** The score as shown to players (rounded up), 0 when the criminal score is disabled. */
    public static int displayScore(LivingEntity entity) {
        return LawConfig.CRIMINAL_SCORE_ENABLED.get() ? record(entity).displayScore() : 0;
    }

    /** Reports a crime committed by {@code offender} against {@code victim} (may be {@code null}). */
    public static CrimeResult reportCrime(LivingEntity offender, CrimeType type, @Nullable Entity victim) {
        return reportCrime(offender, type, victim == null ? null : victim.getUUID(),
                victim == null ? "" : victim.getName().getString());
    }

    /** Reports a crime against a victim identified by UUID (e.g. a ship); {@code null} = no particular victim. */
    public static CrimeResult reportCrime(LivingEntity offender, CrimeType type, @Nullable UUID victimId) {
        return reportCrime(offender, type, victimId, victimId == null ? "" : victimId.toString());
    }

    /**
     * Reports a crime against a victim identified by UUID, with the name {@code /pirates law last} shows (e.g. the port
     * id for {@link CrimeType#FENCE_PLUNDER}); {@code null} = no particular victim.
     */
    public static CrimeResult reportCrime(LivingEntity offender, CrimeType type, @Nullable UUID victimId, String victimName) {
        LawDeeds.onCrime(offender, type, victimId); // REP1: crimes that are reputation deeds (false colours, piracy, ...)
        CrimeRules rules = LawConfig.crimeRules();
        long now = now(server(offender));
        if (!rules.enabled()) {
            CrimeLog.record(offender.getUUID(), new CrimeLog.Entry(type, CriminalRecord.CrimeOutcome.DISABLED, 0, victimName, now));
            return new CrimeResult(CriminalRecord.EMPTY, 0, CriminalRecord.CrimeOutcome.DISABLED);
        }
        CriminalRecord stored = Services.ATTACHMENTS.get(offender, LawAttachments.CRIMINAL_RECORD);
        CrimeResult result = stored.addCrime(type, victimId, now, rules);
        store(offender, result.record());
        if (result.counted()) syncNavyBounty(offender);
        CrimeLog.record(offender.getUUID(), new CrimeLog.Entry(type, result.outcome(), result.pointsAdded(), victimName, now));
        return result;
    }

    // --- World queries ------------------------------------------------------------------------------------------

    public static boolean isNavy(Entity entity) {
        return entity.getType().is(LawTags.NAVY);
    }

    public static boolean isLawProtected(Entity entity) {
        return entity.getType().is(LawTags.LAW_PROTECTED);
    }

    /**
     * Hostility hook for navy AI (docs/design.md §9): whether {@code navy} should attack {@code target} on sight.
     * True for wanted players and NPCs at or above {@code law.world.navy_hostility_threshold}; never for navy,
     * creative or spectator players, or itself. {@code navy} may be {@code null} (any navy observer).
     */
    public static boolean navyShouldAttack(@Nullable LivingEntity navy, LivingEntity target) {
        boolean exempt = target instanceof Player p && (p.isCreative() || p.isSpectator());
        boolean enabled = LawConfig.CRIMINAL_SCORE_ENABLED.get();
        WantedLevel level = enabled && !exempt ? wantedLevel(target) : WantedLevel.CLEAN;
        return NavyHostility.shouldAttack(enabled, LawConfig.NAVY_HOSTILITY_THRESHOLD.get(), level,
                isNavy(target), navy == target, exempt);
    }

    /** Pays a fine of up to {@code doubloons}. The caller takes {@code doubloonsSpent} from the payer. */
    public static FineResult payFine(LivingEntity entity, int doubloons) {
        CrimeRules rules = LawConfig.crimeRules();
        CriminalRecord stored = Services.ATTACHMENTS.get(entity, LawAttachments.CRIMINAL_RECORD);
        FineResult result = stored.payFine(doubloons, now(server(entity)), rules);
        store(entity, result.record());
        syncNavyBounty(entity);
        LawDeeds.onFinePaid(entity, result); // REP1: the pay_fine deed
        return result;
    }

    /** Sets the score directly (operator commands, quests, pardons). */
    public static void setScore(LivingEntity entity, double score) {
        CriminalRecord stored = Services.ATTACHMENTS.get(entity, LawAttachments.CRIMINAL_RECORD);
        store(entity, stored.setScore(score, now(server(entity)), LawConfig.crimeRules()));
        syncNavyBounty(entity);
    }

    /** Places, raises or withdraws the navy bounty on {@code entity} to match its score. */
    public static NavyChange syncNavyBounty(LivingEntity entity) {
        MinecraftServer server = server(entity);
        BountyBoardData data = BountyBoardData.get(server);
        double score = LawConfig.CRIMINAL_SCORE_ENABLED.get() ? record(entity).score() : 0.0;
        BountyBoard.NavySync sync = data.board().syncNavy(targetOf(entity), score, now(server), LawConfig.bountyRules());
        data.setBoard(sync.board());
        return sync.change();
    }

    // --- Bounties -----------------------------------------------------------------------------------------------

    public static BountyBoard board(MinecraftServer server) {
        return BountyBoardData.get(server).board();
    }

    /** Notice board listing, highest total first. */
    public static List<BountyBoard.Notice> notices(MinecraftServer server) {
        return board(server).notices(now(server));
    }

    public static long bountyTotal(MinecraftServer server, UUID target) {
        return board(server).total(target, now(server));
    }

    public static boolean hasBounty(MinecraftServer server, UUID target) {
        return board(server).hasBounty(target, now(server));
    }

    /** A player places a bounty. Take the doubloons from the payer only if the result is placed. */
    public static PlaceResult placeBounty(Player payer, Entity target, int amount) {
        return placeBounty(server(payer), payer.getUUID(), payer.getName().getString(), targetOf(target), amount);
    }

    public static PlaceResult placeBounty(MinecraftServer server, UUID payer, String payerName, BountyTarget target, int amount) {
        BountyBoardData data = BountyBoardData.get(server);
        PlaceResult result = data.board().placePlayerBounty(UUID.randomUUID(), payer, payerName, target, amount,
                now(server), LawConfig.bountyRules());
        data.setBoard(result.board());
        return result;
    }

    /**
     * The navy's standing bounty of {@code amount} doubloons on a named NPC (BOS1: a pirate captain), placed under a
     * random id that {@link #syncNavyBounty} never withdraws. Notice boards list it like any bounty, and killing the
     * target gives the proof. Returns the board's new total on the target (0: nothing placed, {@code amount} < 1).
     */
    public static long placeStandingBounty(MinecraftServer server, BountyTarget target, int amount) {
        BountyBoardData data = BountyBoardData.get(server);
        data.setBoard(data.board().placeStandingBounty(UUID.randomUUID(), target, amount, now(server)));
        return bountyTotal(server, target.id());
    }

    /** Withdraws every bounty on {@code target} without payout (a captain who died with no one to claim him). */
    public static void withdrawBounties(MinecraftServer server, UUID target) {
        BountyBoardData data = BountyBoardData.get(server);
        data.setBoard(data.board().clearTarget(target));
    }

    /**
     * Claims all bounties on a loaded {@code target}: dead (proof item) or alive (delivered). Give the claimant
     * {@code payout} doubloons on success. The target's score is reduced as configured.
     */
    public static ClaimResult claimBounty(LivingEntity target, Player claimant, ClaimMethod method) {
        MinecraftServer server = server(claimant);
        ClaimResult result = claimOnBoard(server, target.getUUID(), claimant.getUUID(), method);
        if (result.success()) applyScoreFactor(target, result.scoreFactor());
        return result;
    }

    /**
     * Claims by UUID (e.g. a proof item naming a target that is no longer loaded). If the target is an online player
     * or a loaded entity its score is reduced now; an offline player's reduction is applied at their next login.
     */
    public static ClaimResult claimBounty(MinecraftServer server, UUID target, UUID claimant, ClaimMethod method) {
        ClaimResult result = claimOnBoard(server, target, claimant, method);
        if (result.success()) applyClaimedScoreFactor(server, target, result);
        return result;
    }

    private static ClaimResult claimOnBoard(MinecraftServer server, UUID target, UUID claimant, ClaimMethod method) {
        return claimOnBoard(server, target, claimant, method, Long.MAX_VALUE);
    }

    private static ClaimResult claimOnBoard(MinecraftServer server, UUID target, UUID claimant, ClaimMethod method,
                                            long createdNoLaterThan) {
        BountyBoardData data = BountyBoardData.get(server);
        ClaimResult result = data.board().claim(target, claimant, method, now(server), LawConfig.bountyRules(), createdNoLaterThan);
        if (result.success()) data.setBoard(result.board());
        return result;
    }

    /** Outcome of {@link #claimWithProof}. */
    public enum ProofClaimOutcome { CLAIMED, NOT_A_PROOF, NO_BOUNTY, SELF_CLAIM }

    /** Result of {@link #claimWithProof}: pay the claimant {@code payout} doubloons on success. */
    public record ProofClaim(ProofClaimOutcome outcome, @Nullable BountyProof proof, int payout, int bounties) {
        public boolean success() {
            return outcome == ProofClaimOutcome.CLAIMED;
        }
    }

    /**
     * Claims the bounties on the target named by a proof item (docs/design.md §13.2, dead with proof). Only bounties
     * placed at or before the kill are paid. On success one proof is consumed from {@code proofStack}. Called by
     * the navy officer later; for now by {@code /pirates law bounty claim proof}.
     */
    public static ProofClaim claimWithProof(Player claimant, ItemStack proofStack) {
        BountyProof proof = BountyProofItem.proofOf(proofStack);
        if (proof == null) return new ProofClaim(ProofClaimOutcome.NOT_A_PROOF, null, 0, 0);
        MinecraftServer server = server(claimant);
        ClaimResult result = claimOnBoard(server, proof.target(), claimant.getUUID(), ClaimMethod.DEAD_WITH_PROOF, proof.killedAt());
        if (!result.success()) {
            return new ProofClaim(result.outcome() == BountyBoard.ClaimOutcome.SELF_CLAIM
                    ? ProofClaimOutcome.SELF_CLAIM : ProofClaimOutcome.NO_BOUNTY, proof, 0, 0);
        }
        applyClaimedScoreFactor(server, proof.target(), result);
        proofStack.shrink(1);
        return new ProofClaim(ProofClaimOutcome.CLAIMED, proof, result.payout(), result.claimed().size());
    }

    private static void applyClaimedScoreFactor(MinecraftServer server, UUID target, ClaimResult result) {
        LivingEntity loaded = findLiving(server, target);
        if (loaded != null) {
            applyScoreFactor(loaded, result.scoreFactor());
        } else if (result.claimed().stream().anyMatch(b -> b.target().kind() == BountyTarget.Kind.PLAYER)) {
            BountyBoardData.get(server).addPendingScoreFactor(target, result.scoreFactor());
        }
    }

    /** Result of {@link #turnInPirate}: the rank reward plus any bounty on the pirate (paid as alive). */
    public record TurnInResult(int reward, ClaimResult bounty) {
        public int total() {
            return reward + (bounty.success() ? bounty.payout() : 0);
        }
    }

    /** A captured pirate NPC is delivered to the navy. Pay the claimant {@code total()}. */
    public static TurnInResult turnInPirate(LivingEntity pirate, PirateTier tier, Player claimant) {
        int reward = LawConfig.bountyRules().turnInReward(tier);
        LawDeeds.onPirateTurnedIn(claimant, pirate); // REP1: the turn_in_pirate deed
        return new TurnInResult(reward, claimBounty(pirate, claimant, ClaimMethod.ALIVE));
    }

    // --- Released prisoners (LA2) -------------------------------------------------------------------------------

    /** The prisoners {@code entity} released so far, per faction (docs/design.md §13.3; read by §15 later). */
    public static ReleaseRecord releases(LivingEntity entity) {
        return Services.ATTACHMENTS.get(entity, LawAttachments.RELEASES);
    }

    /** Appends one release of a prisoner of {@code faction} ({@code null}: no faction) to {@code releaser}'s record. */
    public static ReleaseRecord recordRelease(LivingEntity releaser, @Nullable Faction faction) {
        ReleaseRecord next = releases(releaser).with(faction);
        Services.ATTACHMENTS.set(releaser, LawAttachments.RELEASES, next);
        return next;
    }

    // --- Flags --------------------------------------------------------------------------------------------------

    public static Reaction react(Faction observer, FlagKind flag) {
        return FlagLaw.react(observer, flag, LawConfig.NPC_SURRENDER.get());
    }

    /** Whether {@code captain} flies a false flag, with the captain's navy reputation as the standing (REP1). */
    public static boolean isFalseFlag(LivingEntity captain, FlagKind flag) {
        return isFalseFlag(captain, flag, navyStanding(captain));
    }

    /**
     * The captain's navy standing: the navy reputation of a player (0 while reputation is off), raised to
     * {@code navy_flag_min_standing} for a navy officer with the flag right (CAR2); 0 for anyone else.
     */
    public static int navyStanding(LivingEntity captain) {
        return captain instanceof Player p ? com.richardsenger.piratesnships.rpg.career.Careers.effectiveNavyStanding(p) : 0;
    }

    /** Whether {@code captain} flies a false flag, judged with an explicit {@code navyStanding}. */
    public static boolean isFalseFlag(LivingEntity captain, FlagKind flag, int navyStanding) {
        boolean bounty = hasBounty(server(captain), captain.getUUID());
        return FlagLaw.isFalseFlag(flag, new FlagLaw.CaptainStanding(navyStanding, bounty), LawConfig.NAVY_FLAG_MIN_STANDING.get());
    }

    /** Chance that one observation check over {@code intervalTicks} sees through the captain's flag. */
    public static double detectionChance(LivingEntity captain, FlagKind flag, int navyStanding, double distance,
                                         boolean observerHasCrowsNest, long intervalTicks) {
        return FalseColorsDetection.chance(LawConfig.detectionParams(), isFalseFlag(captain, flag, navyStanding),
                distance, observerHasCrowsNest, score(captain), intervalTicks);
    }

    /**
     * What the ship {@code entity} is aboard tells NPCs about it (FL2): {@link ShipStance#NONE} when not aboard,
     * when {@code law.flags.enabled} is off, or when the flag makes no difference. Asked by the mob hostility rules.
     */
    public static ShipStance shipStance(LivingEntity entity) {
        return FlagCrimes.stanceOf(entity);
    }

    /**
     * The false-colours rule for the captain of a ship showing {@code shown} (FL2, REP1): only a navy flag is judged; it
     * is false colours when it is a false flag for the captain ({@link #isFalseFlag(LivingEntity, FlagKind)}: a bounty,
     * or a navy reputation below {@code flags_brig.navy_flag_min_standing}), or when the captain is at least
     * {@code law.flags.false_flag_wanted_threshold} wanted (a known criminal can't hide behind the navy flag).
     */
    public static boolean fliesFalseColours(LivingEntity captain, FlagKind shown) {
        if (shown != FlagKind.NAVY) return false;
        return isFalseFlag(captain, shown) || wantedLevel(captain).ordinal() >= LawConfig.FALSE_FLAG_WANTED_THRESHOLD.get();
    }

    /**
     * A loaded living entity by UUID: an online player, else an entity in any level (a ship's owner, for crimes
     * committed by the ship). {@code null} if it is not loaded.
     */
    public static @Nullable LivingEntity findLoaded(MinecraftServer server, UUID id) {
        return findLiving(server, id);
    }

    // --- Ticking and login --------------------------------------------------------------------------------------

    /** Throttled server tick: decay online players and keep their navy bounties in sync. */
    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % TICK_INTERVAL != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncNavyBounty(player);
        }
        BountyBoardData data = BountyBoardData.get(server);
        data.setBoard(data.board().pruneExpired(now(server)).board());
    }

    /** Applies a claim's score reduction that happened while the player was offline. */
    public static void onLogin(ServerPlayer player) {
        double factor = BountyBoardData.get(player.server).takePendingScoreFactor(player.getUUID());
        if (factor != 1.0) applyScoreFactor(player, factor);
        syncNavyBounty(player);
    }

    // --- Internals ----------------------------------------------------------------------------------------------

    private static void applyScoreFactor(LivingEntity entity, double factor) {
        CriminalRecord r = record(entity);
        store(entity, r.withScore(r.score() * factor));
        syncNavyBounty(entity);
    }

    private static void store(LivingEntity entity, CriminalRecord record) {
        Services.ATTACHMENTS.set(entity, LawAttachments.CRIMINAL_RECORD, record);
    }

    private static @Nullable LivingEntity findLiving(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) return player;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof LivingEntity living) return living;
        }
        return null;
    }

    private static MinecraftServer server(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            throw new IllegalStateException("LawService is server-side only");
        }
        return level.getServer();
    }
}

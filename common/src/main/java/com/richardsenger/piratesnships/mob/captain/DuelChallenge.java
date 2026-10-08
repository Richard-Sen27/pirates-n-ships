package com.richardsenger.piratesnships.mob.captain;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.melee.MeleeService;
import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.MobFaction;
import com.richardsenger.piratesnships.mob.MobKind;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The pirate captain's duel in the world (BOS1, docs/design.md §15); rules in {@link DuelRules}.
 *
 * <ul>
 *   <li>{@link #interact}: sneak-use the captain with a sword in the main hand (called from his {@code mobInteract}).
 *       Accepted: he fights the challenger alone ({@link PirateCaptain#attacksOnSight}), every pirate within
 *       {@code duel_truce_range} gets a truce for the challenger ({@link SeafarerMob#truce}).</li>
 *   <li>{@link #tick}: once a second while the duel runs (from the captain's AI step): renews the truces for the
 *       pirates near him, or ends the duel (the challenger left or died, time ran out).</li>
 *   <li>{@link #end}: on his death (from {@code PirateCaptain#die}) or the challenger's ({@link #onDeath}): the
 *       truces end at once and the crew may fight again.</li>
 * </ul>
 * Which captain a player duels is kept in memory ({@code ACTIVE}), like the duel itself: a restart ends every duel.
 */
public final class DuelChallenge {

    public static final String MSG = "message." + Constants.MOD_ID + ".captain.duel.";
    public static final String KEY_ACCEPTED = MSG + "accepted";
    public static final String KEY_ENDED = MSG + "ended.";

    /** Challenger → captain of the running duels (server thread). */
    private static final Map<UUID, UUID> ACTIVE = new ConcurrentHashMap<>();

    private DuelChallenge() {
    }

    /** Whether {@code stack} is a sword: a melee-engine weapon (rapier, cutlass, saber) or any vanilla sword. */
    public static boolean isSword(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof SwordItem || stack.is(ItemTags.SWORDS);
    }

    /** {@link #isSword} or the melee engine's weapon in {@code player}'s main hand. */
    public static boolean holdsSword(Player player) {
        return isSword(player.getMainHandItem()) || MeleeService.weaponInHand(player).isPresent();
    }

    /** The duel rules' params; a peaceful captain takes no challenge. */
    static DuelRules.Params params() {
        DuelRules.Params p = CaptainConfig.duel();
        boolean peaceful = MobConfig.isPeaceful(MobKind.PIRATE_CAPTAIN);
        return new DuelRules.Params(p.enabled() && !peaceful, p.truceRange(), p.leaveRange(), p.maxTicks());
    }

    /** The challenge gesture; PASS unless it is a sneak-use with a sword in the main hand. */
    public static InteractionResult interact(PirateCaptain captain, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !holdsSword(player)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        DuelRules.Refusal r = challenge(captain, player);
        if (r != DuelRules.Refusal.NONE) {
            player.displayClientMessage(Component.translatable(MSG + r.name().toLowerCase(Locale.ROOT), captain.getName())
                    .withStyle(ChatFormatting.RED), true);
            captain.playSound(SoundEvents.PILLAGER_AMBIENT, 1.0f, 0.8f);
        }
        return InteractionResult.CONSUME;
    }

    /** Checks and, when accepted, starts (or keeps) the duel of {@code captain} with {@code challenger}. Server side. */
    public static DuelRules.Refusal challenge(PirateCaptain captain, Player challenger) {
        DuelRules.Params p = params();
        boolean exempt = challenger.isCreative() || challenger.isSpectator();
        DuelRules.Refusal r = DuelRules.challenge(p, challenger.isShiftKeyDown(), holdsSword(challenger), exempt,
                captain.duelOpponent(), challenger.getUUID(), captain.hasGrudge(challenger));
        if (r != DuelRules.Refusal.NONE || captain.inDuel()) return r;
        start(captain, challenger, p);
        return r;
    }

    private static void start(PirateCaptain captain, Player challenger, DuelRules.Params p) {
        long now = captain.level().getGameTime();
        long deadline = DuelRules.deadline(p, now);
        captain.startDuel(challenger.getUUID(), deadline);
        captain.setTarget(challenger);
        ACTIVE.put(challenger.getUUID(), captain.getUUID());
        renewTruces(captain, challenger, p, now, deadline);
        challenger.sendSystemMessage(Component.translatable(KEY_ACCEPTED, captain.getName()).withStyle(ChatFormatting.GOLD));
        captain.playSound(SoundEvents.PILLAGER_CELEBRATE, 1.0f, 0.9f);
    }

    /** Gives every other pirate within the truce range a truce with {@code challenger} until the next renewal. */
    static int renewTruces(PirateCaptain captain, LivingEntity challenger, DuelRules.Params p, long now, long deadline) {
        double r = p.truceRange();
        long until = DuelRules.truceUntil(now, deadline);
        int n = 0;
        for (SeafarerMob m : crew(captain, r)) {
            if (DuelRules.inTruce(p, m.distanceToSqr(captain))) {
                m.truce(challenger, until);
                n++;
            }
        }
        return n;
    }

    /** The pirates within {@code range} blocks of the captain (him excluded). */
    private static List<SeafarerMob> crew(PirateCaptain captain, double range) {
        AABB box = captain.getBoundingBox().inflate(range);
        return captain.level().getEntitiesOfClass(SeafarerMob.class, box,
                m -> m != captain && m.faction() == MobFaction.PIRATE && m.isAlive());
    }

    /** Once a second while the duel runs: renew the truces or end the duel. */
    static void tick(PirateCaptain captain) {
        UUID id = captain.duelOpponent();
        if (id == null || !(captain.level() instanceof ServerLevel level)) return;
        DuelRules.Params p = params();
        Entity e = level.getEntity(id);
        LivingEntity challenger = e instanceof LivingEntity l ? l : null;
        boolean alive = challenger != null && challenger.isAlive() && !challenger.isRemoved();
        double d = alive ? challenger.distanceToSqr(captain) : 0;
        long now = level.getGameTime();
        DuelRules.End end = DuelRules.check(p, captain.isAlive(), alive, d, now, captain.duelDeadline());
        if (end != DuelRules.End.NONE) {
            end(captain, end);
            return;
        }
        renewTruces(captain, challenger, p, now, captain.duelDeadline());
        if (captain.getTarget() == null) captain.setTarget(challenger);
    }

    /** Ends the captain's duel: the truces of the pirates near him end now, the challenger is told why. */
    public static void end(PirateCaptain captain, DuelRules.End why) {
        UUID id = captain.duelOpponent();
        if (id == null) return;
        captain.endDuel();
        ACTIVE.remove(id, captain.getUUID());
        // the crew within any range they could have been given a truce in; his own target follows the usual rules again
        for (SeafarerMob m : crew(captain, Math.max(params().truceRange(), params().leaveRange()) + 8)) m.endTruce(id);
        if (captain.level() instanceof ServerLevel level && level.getEntity(id) instanceof Player p && why != DuelRules.End.NONE) {
            p.sendSystemMessage(Component.translatable(KEY_ENDED + why.name().toLowerCase(Locale.ROOT), captain.getName())
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /** Listener for {@code CommonEvents.LIVING_DEATH}: a challenger who dies loses the duel. Never cancels. */
    public static boolean onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof Player) || entity.level().isClientSide) return false;
        UUID captainId = ACTIVE.get(entity.getUUID());
        if (captainId == null) return false;
        PirateCaptain captain = find(entity.level().getServer(), captainId);
        if (captain != null && entity.getUUID().equals(captain.duelOpponent())) end(captain, DuelRules.End.CHALLENGER_DIED);
        ACTIVE.remove(entity.getUUID());
        return false;
    }

    /** The captain a player duels now, if he is loaded. */
    public static @Nullable PirateCaptain duelOf(MinecraftServer server, UUID challenger) {
        UUID captainId = ACTIVE.get(challenger);
        return captainId == null ? null : find(server, captainId);
    }

    private static @Nullable PirateCaptain find(@Nullable MinecraftServer server, UUID id) {
        if (server == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof PirateCaptain c) return c;
        }
        return null;
    }

    /** Forgets every duel (server stop). */
    public static void clear() {
        ACTIVE.clear();
    }
}

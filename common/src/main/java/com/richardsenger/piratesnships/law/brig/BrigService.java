package com.richardsenger.piratesnships.law.brig;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Prisoners on real entities (design.md §13.3), server side. Capture with shackles, leading, cells, escapes and the
 * area query for provisions. Outcomes (deliver, ransom, press-gang, release) are in {@link PrisonerOutcomes}.
 *
 * <p>Restraint: every tick, a prisoner mob's goal selectors have their MOVE, LOOK and TARGET flags disabled (JUMP
 * stays so it can swim), its target and brain attack/walk targets are cleared, and all damage it deals is cancelled.
 * So it stops fighting and fleeing whatever AI it has. Movement is ours: a led mob paths after its captor (its own
 * navigation, or the brain's walk target for brain mobs), and beyond {@link BrigConfig#PULL_DISTANCE} the chain pulls
 * it like a lead, so any mob can be led, including ones vanilla can't leash. Prisoner players get slowness, weakness
 * and mining fatigue, can't hurt anyone or break blocks, are pulled like a lead, and go free after
 * {@link BrigConfig#PLAYER_CAPTURE_SECONDS}.
 *
 * <p>Losing the captor (out of {@link BrigConfig#BREAK_DISTANCE}, offline, dead, another dimension) drops the chain:
 * the prisoner stays shackled and stands still, may escape, and anyone with shackles can pick up its chain again.
 */
public final class BrigService {

    public static final AttachmentKey<PrisonerState> PRISONER = AttachmentKey.builder("prisoner", () -> PrisonerState.NONE)
            .persistent(PrisonerState.CODEC).synced(PrisonerState.STREAM_CODEC).build();

    /** Entity types that can never be shackled (bosses and the like). Players are handled by their own rule. */
    public static final TagKey<EntityType<?>> NOT_CAPTURABLE = TagKey.create(Registries.ENTITY_TYPE, Constants.id("not_capturable"));

    private static final DustParticleOptions CHAIN_PARTICLE = new DustParticleOptions(new Vector3f(0.55f, 0.55f, 0.6f), 0.8f);
    private static final String MSG = "message.pirates_n_ships.brig.";

    /** Prisoners currently loaded, filled on capture and when a prisoner joins a level. Server thread only. */
    private static final Set<LivingEntity> TRACKED = new LinkedHashSet<>();

    private BrigService() {
    }

    // --- State --------------------------------------------------------------------------------------------------

    public static PrisonerState state(LivingEntity entity) {
        return Services.ATTACHMENTS.has(entity, PRISONER) ? Services.ATTACHMENTS.get(entity, PRISONER) : PrisonerState.NONE;
    }

    public static boolean isPrisoner(Entity entity) {
        return entity instanceof LivingEntity living && state(living).active();
    }

    /** All loaded prisoners (a copy). */
    public static List<LivingEntity> tracked() {
        TRACKED.removeIf(e -> e.isRemoved() || !state(e).active());
        return new ArrayList<>(TRACKED);
    }

    // --- Capture and leading ------------------------------------------------------------------------------------

    public static CaptureRules.Result canCapture(Player captor, LivingEntity target) {
        boolean player = target instanceof Player;
        boolean bounty = player && target.level() instanceof ServerLevel level
                && LawService.hasBounty(level.getServer(), target.getUUID());
        CaptureRules.Target t = new CaptureRules.Target(player,
                target instanceof Mob && !target.getType().is(NOT_CAPTURABLE),
                target.isAlive(), target.getHealth(), target.getMaxHealth(), isPrisoner(target),
                target == captor || target.getUUID().equals(captor.getUUID()), bounty);
        return CaptureRules.check(t, BrigConfig.CAPTURE_HEALTH_FRACTION.get(), LawConfig.PLAYER_CAPTURE.get());
    }

    /** Puts {@code target} in shackles held by {@code captor} if the rules allow it. Doesn't touch any item. */
    public static CaptureRules.Result capture(Player captor, LivingEntity target) {
        CaptureRules.Result result = canCapture(captor, target);
        if (!result.ok()) return result;
        MinecraftServer server = ((ServerLevel) target.level()).getServer();
        long now = LawService.now(server);
        long releaseAt = target instanceof Player ? now + BrigConfig.PLAYER_CAPTURE_SECONDS.get() * 20L : -1L;
        Services.ATTACHMENTS.set(target, PRISONER, PrisonerState.captured(captor.getUUID(), captor.getName().getString(), now, releaseAt));
        target.stopRiding();
        target.ejectPassengers();
        if (target instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.getNavigation().stop();
            restrain(mob, true);
        }
        if (target instanceof Player p) {
            p.displayClientMessage(Component.translatable(MSG + "captured_player", captor.getName()), false);
        }
        TRACKED.add(target);
        target.level().playSound(null, target.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
        return result;
    }

    /**
     * Whether a shackles click on {@code target} belongs to the brig rather than to the target's own interaction
     * (e.g. a villager's trades): the target is a prisoner, or it could be captured now or once weakened. A refusal
     * for any other reason (not capturable, player capture off, no bounty, ...) leaves the click to vanilla.
     */
    public static boolean claimsShackleClick(Player player, LivingEntity target) {
        if (state(target).active()) return true;
        CaptureRules.Result r = canCapture(player, target);
        return r.ok() || r == CaptureRules.Result.TOO_HEALTHY;
    }

    /**
     * Shackles used on a living entity (server). A prisoner of the user: toggle leading. A prisoner whose chain is
     * loose: the user takes it over. Otherwise: capture, consuming one shackles (not in creative).
     */
    public static InteractionResult useShackles(ItemStack stack, Player player, LivingEntity target) {
        PrisonerState st = state(target);
        if (st.active()) {
            if (st.heldBy(player.getUUID())) {
                setLed(target, !st.led());
                player.displayClientMessage(Component.translatable(MSG + (st.led() ? "lead.stop" : "lead.start"), target.getName()), true);
            } else if (!st.led()) {
                Services.ATTACHMENTS.set(target, PRISONER, st.withCaptor(player.getUUID(), player.getName().getString()).withLed(true));
                player.displayClientMessage(Component.translatable(MSG + "lead.take_over", target.getName()), true);
            } else {
                player.displayClientMessage(Component.translatable(MSG + "lead.someone_else", st.captorName()), true);
            }
            return InteractionResult.CONSUME;
        }
        CaptureRules.Result result = capture(player, target);
        player.displayClientMessage(Component.translatable(result.messageKey(), target.getName()), true);
        if (!result.ok()) return InteractionResult.FAIL;
        stack.consume(1, player);
        return InteractionResult.CONSUME;
    }

    public static void setLed(LivingEntity prisoner, boolean led) {
        PrisonerState st = state(prisoner);
        if (!st.active() || st.led() == led) return;
        Services.ATTACHMENTS.set(prisoner, PRISONER, st.withLed(led));
        if (!led && prisoner instanceof Mob mob) mob.getNavigation().stop();
    }

    /** Removes the shackles state and gives the mob its AI back. No item, no message. */
    public static void clear(LivingEntity prisoner) {
        if (!state(prisoner).active()) return;
        Services.ATTACHMENTS.set(prisoner, PRISONER, PrisonerState.NONE);
        TRACKED.remove(prisoner);
        if (prisoner instanceof Mob mob) {
            for (Goal.Flag f : Goal.Flag.values()) {
                mob.goalSelector.enableControlFlag(f);
                mob.targetSelector.enableControlFlag(f);
            }
            mob.getNavigation().stop();
        }
        if (prisoner instanceof Player p) {
            p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            p.removeEffect(MobEffects.WEAKNESS);
            p.removeEffect(MobEffects.DIG_SLOWDOWN);
        }
    }

    // --- Cells and the area query -------------------------------------------------------------------------------

    /** Whether the prisoner (or any entity) stands in a brig cell: enclosed by walls, bars and a locked brig door. */
    public static boolean isInCell(LivingEntity entity) {
        return WorldCells.check(entity.level(), entity.blockPosition(), BrigConfig.MAX_CELL_SIZE.get()).isCell();
    }

    /** Prisoners within {@code area} (loaded entities). */
    public static List<LivingEntity> prisonersWithin(Level level, AABB area) {
        return level.getEntitiesOfClass(LivingEntity.class, area, BrigService::isPrisoner);
    }

    /**
     * How many prisoners are held within {@code area}: the prisoner count for the provisions rules
     * ({@code CrewHeadcount.withPrisoners(n)}). With {@code inCellsOnly}, only those locked in a cell count.
     */
    public static int countPrisoners(Level level, AABB area, boolean inCellsOnly) {
        int n = 0;
        for (LivingEntity e : prisonersWithin(level, area)) {
            if (!inCellsOnly || isInCell(e)) n++;
        }
        return n;
    }

    // --- Escapes ------------------------------------------------------------------------------------------------

    /**
     * Frees a prisoner on its own: the shackles break (no item). Monsters turn on their captor if it is near,
     * other mobs run away from it.
     */
    public static void escape(LivingEntity prisoner) {
        PrisonerState st = state(prisoner);
        if (!st.active()) return;
        clear(prisoner);
        Player captor = prisoner.level().getPlayerByUUID(st.captor());
        prisoner.level().playSound(null, prisoner.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1.0f, 1.0f);
        if (captor != null) captor.displayClientMessage(Component.translatable(MSG + "escaped", prisoner.getName()), true);
        if (prisoner instanceof Mob mob) {
            boolean captorNear = captor != null && captor.isAlive() && !captor.isSpectator() && !captor.isCreative()
                    && captor.distanceToSqr(mob) < 32 * 32;
            if (mob instanceof Enemy && captorNear) {
                mob.setTarget(captor);
            } else if (mob instanceof PathfinderMob pm) {
                Vec3 to = captor != null ? DefaultRandomPos.getPosAway(pm, 16, 7, captor.position()) : DefaultRandomPos.getPos(pm, 16, 7);
                if (to != null) pm.getNavigation().moveTo(to.x, to.y, to.z, 1.3);
            }
        }
    }

    /** One escape check with the morale and ship-capture inputs (no source yet: the crew and ship systems). */
    public static EscapeRule.Decision checkEscape(LivingEntity prisoner, boolean inCell, boolean crewMoraleLow, boolean shipCapturedByOwnFaction) {
        PrisonerState st = state(prisoner);
        EscapeRule.Inputs in = new EscapeRule.Inputs(LawConfig.PRISONER_ESCAPES.get(), inCell, st.led(), crewMoraleLow, shipCapturedByOwnFaction);
        EscapeRule.Decision d = EscapeRule.decide(in, BrigConfig.escapeParams(), prisoner.getRandom());
        if (d != EscapeRule.Decision.STAY) escape(prisoner);
        return d;
    }

    // --- Events -------------------------------------------------------------------------------------------------

    public static boolean onJoin(Entity entity, Level level) {
        if (!level.isClientSide && entity instanceof LivingEntity living && state(living).active()) TRACKED.add(living);
        return false;
    }

    /** Prisoners can't hurt anyone (melee, projectiles they own, anything sourced to them). */
    public static float onDamage(LivingEntity victim, DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        return attacker != null && attacker != victim && isPrisoner(attacker) ? 0.0f : amount;
    }

    public static boolean onBlockBreak(Player player) {
        return isPrisoner(player);
    }

    public static void onServerStopped(MinecraftServer server) {
        TRACKED.clear();
    }

    // --- Ticking ------------------------------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        if (TRACKED.isEmpty()) return;
        long now = LawService.now(server);
        int interval = BrigConfig.CHECK_INTERVAL_TICKS.get();
        for (LivingEntity e : tracked()) {
            PrisonerState st = state(e);
            if (st.led()) st = follow(e, st);
            if (e instanceof Mob mob) restrain(mob, st.led());
            if (e instanceof Player p) restrainPlayer(p);
            int phase = server.getTickCount() + e.getId();
            if (phase % 20 == 0 && e.level() instanceof ServerLevel level) {
                level.sendParticles(CHAIN_PARTICLE, e.getX(), e.getY() + e.getBbHeight() * 0.55, e.getZ(), 3, 0.25, 0.1, 0.25, 0.0);
            }
            if (phase % interval != 0) continue;
            if (e instanceof Player p) {
                if (st.expired(now)) {
                    clear(p);
                    p.displayClientMessage(Component.translatable(MSG + "player_freed"), false);
                }
                continue;
            }
            boolean inCell = isInCell(e);
            if (inCell && st.led()) {
                setLed(e, false);
                Player captor = e.level().getPlayerByUUID(st.captor());
                if (captor != null) captor.displayClientMessage(Component.translatable(MSG + "in_cell", e.getName()), true);
            }
            checkEscape(e, inCell, false, false);
        }
    }

    /** Keeps a mob from fighting, fleeing or wandering. Call every tick (vanilla re-enables flags every 5 ticks). */
    private static void restrain(Mob mob, boolean led) {
        mob.goalSelector.disableControlFlag(Goal.Flag.MOVE);
        mob.goalSelector.disableControlFlag(Goal.Flag.LOOK);
        mob.goalSelector.disableControlFlag(Goal.Flag.TARGET);
        mob.targetSelector.disableControlFlag(Goal.Flag.TARGET);
        if (mob.getTarget() != null) mob.setTarget(null);
        mob.setAggressive(false);
        Brain<?> brain = mob.getBrain();
        brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        if (!led) brain.eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private static void restrainPlayer(Player p) {
        if (p.tickCount % 20 != 0) return;
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 4, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, 2, false, false, true));
    }

    /**
     * The captor in the prisoner's level: a player, or any loaded living entity with that UUID (room for NPC captors
     * such as a ship's officer later; GameTests use a mob because mock server players can't receive Sable payloads).
     */
    public static @Nullable LivingEntity findCaptor(Level level, PrisonerState st) {
        Player player = level.getPlayerByUUID(st.captor());
        if (player != null) return player;
        return level instanceof ServerLevel sl && sl.getEntity(st.captor()) instanceof LivingEntity living ? living : null;
    }

    /** Walks or pulls a led prisoner after its captor; drops the chain when the captor is gone. Returns the new state. */
    private static PrisonerState follow(LivingEntity e, PrisonerState st) {
        LivingEntity captor = findCaptor(e.level(), st);
        double dist = captor == null ? Double.MAX_VALUE : captor.distanceTo(e);
        if (captor == null || !captor.isAlive() || captor.isSpectator() || dist > BrigConfig.BREAK_DISTANCE.get()) {
            setLed(e, false);
            if (captor instanceof Player p) p.displayClientMessage(Component.translatable(MSG + "lead.lost", e.getName()), true);
            return state(e);
        }
        double speed = BrigConfig.FOLLOW_SPEED.get();
        if (e instanceof Mob mob && (e.tickCount % 10 == 0)) {
            if (dist > BrigConfig.FOLLOW_DISTANCE.get()) {
                Brain<?> brain = mob.getBrain();
                if (brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
                    brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(captor, (float) speed, 2));
                } else {
                    mob.getNavigation().moveTo(captor, speed);
                }
            } else {
                mob.getNavigation().stop();
                mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            }
        }
        double pull = e instanceof Player ? BrigConfig.FOLLOW_DISTANCE.get() : BrigConfig.PULL_DISTANCE.get();
        if (dist > pull) {
            Vec3 dir = captor.position().subtract(e.position()).normalize();
            double strength = Math.min(0.4, 0.06 * (dist - pull));
            e.setDeltaMovement(e.getDeltaMovement().add(dir.scale(strength)));
            e.hurtMarked = true;
            e.resetFallDistance();
        }
        return st;
    }
}

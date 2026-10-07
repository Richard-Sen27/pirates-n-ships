package com.richardsenger.piratesnships.combat.melee;

import com.richardsenger.piratesnships.combat.melee.engine.MeleeEngine;
import com.richardsenger.piratesnships.combat.melee.geometry.Box;
import com.richardsenger.piratesnships.combat.melee.geometry.HitGeometry;
import com.richardsenger.piratesnships.combat.melee.geometry.Vec;
import com.richardsenger.piratesnships.combat.melee.net.MeleeStateSync;
import com.richardsenger.piratesnships.combat.melee.resolve.HitResolver;
import com.richardsenger.piratesnships.combat.melee.resolve.HitResult;
import com.richardsenger.piratesnships.combat.melee.resolve.IncomingHit;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.CombatRules;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.InputResult;
import com.richardsenger.piratesnships.combat.melee.rules.MeleeParams;
import com.richardsenger.piratesnships.combat.melee.rules.Refusal;
import com.richardsenger.piratesnships.combat.melee.weapon.MeleeWeapons;
import com.richardsenger.piratesnships.combat.melee.weapon.WeaponDefinition;
import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Server-side melee integration: the API the input, network and AI layers call. Server thread only.
 *
 * <pre>{@code
 * WeaponDefinition w = MeleeService.weaponInHand(player).orElse(null); // or any definition, e.g. for an NPC
 * if (MeleeService.skillBasedCombat() && w != null) {
 *     InputResult r = MeleeService.startSlash(player, w);    // or startThrust / guardDown / guardUp / parry / feint
 *     if (!r.accepted()) feedback(r.refusal());              // BUSY, NO_STAMINA, LOCKED_OUT, STAGGERED, DISABLED
 * }
 * CombatState s = MeleeService.state(entity);                // for HUD payloads and animation events
 * }</pre>
 *
 * Every tick the service advances all active combatants, sweeps active attacks against real bounding boxes in
 * world coordinates (ship-relative frames can be passed to the pure geometry later), and applies damage through the
 * vanilla damage system. The incoming-damage hook lets guarding or parrying entities block vanilla melee hits from
 * the front; projectiles pass through. With {@code melee.skill_based_combat} off nothing here does anything and mod
 * swords stay vanilla.
 */
public final class MeleeService {

    private static final MeleeEngine ENGINE = new MeleeEngine();
    /** Combatants being ticked, with their (stable) engine wrappers. */
    private static final Map<LivingEntity, EntityFighter> ACTIVE = new LinkedHashMap<>();
    /** Weapons passed explicitly with an action; cleared when the combatant goes dormant. */
    private static final Map<LivingEntity, WeaponDefinition> EXPLICIT_WEAPONS = new HashMap<>();
    /** True while we apply our own damage, so the incoming-damage hook doesn't resolve it a second time. */
    private static boolean applying;

    private MeleeService() {
    }

    // --- Queries ------------------------------------------------------------------------------------------------

    /** The one switch: off = mod swords behave like vanilla swords and this service ignores every input. */
    public static boolean skillBasedCombat() {
        return MeleeConfig.SKILL_BASED.get();
    }

    public static CombatState state(LivingEntity entity) {
        return Services.ATTACHMENTS.get(entity, MeleeAttachments.COMBAT_STATE);
    }

    /** The weapon definition of the main-hand item (empty for anything that isn't a skill-based sword). */
    public static java.util.Optional<WeaponDefinition> weaponInHand(LivingEntity entity) {
        return MeleeWeapons.forStack(entity.getMainHandItem(), MeleeWeapons.WEAPONS.of(entity.level()));
    }

    /** The weapon the entity fights with: the one passed with its last action, else the one in hand. */
    public static @Nullable WeaponDefinition weapon(LivingEntity entity) {
        WeaponDefinition w = EXPLICIT_WEAPONS.get(entity);
        return w != null ? w : weaponInHand(entity).orElse(null);
    }

    public static boolean isActive(LivingEntity entity) {
        return ACTIVE.containsKey(entity);
    }

    // --- Inputs -------------------------------------------------------------------------------------------------

    public static InputResult startSlash(LivingEntity entity, WeaponDefinition weapon) {
        return input(entity, weapon, (s, p) -> CombatRules.startAttack(s, AttackKind.SLASH, weapon, p));
    }

    public static InputResult startThrust(LivingEntity entity, WeaponDefinition weapon) {
        return input(entity, weapon, (s, p) -> CombatRules.startAttack(s, AttackKind.THRUST, weapon, p));
    }

    public static InputResult guardDown(LivingEntity entity, WeaponDefinition weapon) {
        return input(entity, weapon, (s, p) -> CombatRules.guardDown(s, weapon, p));
    }

    public static InputResult guardUp(LivingEntity entity) {
        return input(entity, null, (s, p) -> CombatRules.guardUp(s));
    }

    public static InputResult parry(LivingEntity entity, WeaponDefinition weapon) {
        return input(entity, weapon, (s, p) -> CombatRules.parry(s, weapon, p));
    }

    /**
     * Abort the current attack during its wind-up ({@link CombatRules#feint}). NPC duelists use it now; a player key
     * binding only needs to call this (and a {@code MeleeActionPayload} action for it).
     */
    public static InputResult feint(LivingEntity entity) {
        return input(entity, null, (s, p) -> CombatRules.feint(s, p));
    }

    private static InputResult input(LivingEntity e, @Nullable WeaponDefinition weapon,
                                     BiFunction<CombatState, MeleeParams, InputResult> rule) {
        MeleeParams p = MeleeConfig.params();
        if (!p.skillBased()) return InputResult.refused(state(e), Refusal.DISABLED);
        InputResult r = rule.apply(state(e), p);
        if (r.accepted()) {
            if (weapon != null) EXPLICIT_WEAPONS.put(e, weapon);
            fighter(e).setState(r.state());
        }
        return r;
    }

    // --- Events -------------------------------------------------------------------------------------------------

    public static void onServerTick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        MeleeParams p = MeleeConfig.params();
        ACTIVE.values().removeIf(f -> !f.valid());
        EXPLICIT_WEAPONS.keySet().removeIf(e -> !e.isAlive() || e.isRemoved());
        List<EntityFighter> fighters = new ArrayList<>(ACTIVE.values());
        ENGINE.tick(server.getTickCount(), fighters, ARENA, p);
        // before dormant combatants are dropped, so their final state (full stamina, idle) still goes out
        MeleeStateSync.flush(server.getTickCount(), ACTIVE.keySet());
        if (ENGINE.heldHits() == 0) {
            for (Iterator<Map.Entry<LivingEntity, EntityFighter>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                if (entry.getValue().state().dormant(p.staminaMax())) {
                    EXPLICIT_WEAPONS.remove(entry.getKey());
                    it.remove();
                }
            }
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        ACTIVE.clear();
        EXPLICIT_WEAPONS.clear();
        ENGINE.clear();
        MeleeStateSync.onServerStopped();
    }

    /** {@code LIVING_INCOMING_DAMAGE}: guard and parry against vanilla melee from the front. Returns the new amount. */
    public static float onIncomingDamage(LivingEntity defender, DamageSource source, float amount) {
        if (applying || defender.level().isClientSide() || !skillBasedCombat()) return amount;
        if (source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() instanceof Projectile) return amount;
        if (!(source.getDirectEntity() instanceof LivingEntity attacker) || source.getEntity() != attacker) return amount;
        if (!(source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO))) {
            return amount;
        }
        WeaponDefinition w = weapon(defender);
        if (w == null) return amount;
        MeleeParams p = MeleeConfig.params();
        EntityFighter def = fighter(defender);
        boolean frontal = HitGeometry.inFront(def.eye(), def.look(), vec(attacker.getEyePosition()), w.guard().arcDegrees());
        CombatState attackerState = Services.ATTACHMENTS.has(attacker, MeleeAttachments.COMBAT_STATE)
                ? state(attacker) : CombatState.fresh(p.staminaMax());
        HitResult r = HitResolver.resolve(attackerState, def.state(), w, IncomingHit.vanillaMelee(amount, frontal), 0, p);
        def.setState(r.defender());
        if (!r.attacker().equals(attackerState)) fighter(attacker).setState(r.attacker());
        return r.damage();
    }

    // --- Engine glue --------------------------------------------------------------------------------------------

    private static EntityFighter fighter(LivingEntity e) {
        return ACTIVE.computeIfAbsent(e, EntityFighter::new);
    }

    private static Vec vec(Vec3 v) {
        return new Vec(v.x, v.y, v.z);
    }

    private static final MeleeEngine.Arena ARENA = new MeleeEngine.Arena() {
        @Override
        public List<? extends MeleeEngine.Fighter> candidates(MeleeEngine.Fighter attacker, double range) {
            LivingEntity a = ((EntityFighter) attacker).entity;
            AABB area = a.getBoundingBox().inflate(range + 1.0);
            List<EntityFighter> out = new ArrayList<>();
            for (LivingEntity e : a.level().getEntitiesOfClass(LivingEntity.class, area, e -> e != a && e.isAlive())) {
                EntityFighter f = ACTIVE.get(e);
                out.add(f != null ? f : new EntityFighter(e));
            }
            return out;
        }

        @Override
        public void apply(MeleeEngine.Fighter attacker, MeleeEngine.Fighter target, IncomingHit hit, HitResult result) {
            LivingEntity a = ((EntityFighter) attacker).entity;
            LivingEntity t = ((EntityFighter) target).entity;
            fighter(t); // keep ticking the target (stagger, riposte window, regeneration)
            if (result.damage() <= 0) return;
            DamageSource src = a instanceof Player player ? a.damageSources().playerAttack(player) : a.damageSources().mobAttack(a);
            applying = true;
            try {
                t.invulnerableTime = 0; // our own phase timings limit the hit rate, not vanilla's i-frames
                t.hurt(src, result.damage());
            } finally {
                applying = false;
            }
        }
    };

    /** Engine view of a living entity; state lives in the attachment. */
    private static final class EntityFighter implements MeleeEngine.Fighter {
        final LivingEntity entity;

        EntityFighter(LivingEntity entity) {
            this.entity = entity;
        }

        @Override public int id() { return entity.getId(); }
        @Override public CombatState state() { return MeleeService.state(entity); }
        @Override public void setState(CombatState state) {
            Services.ATTACHMENTS.set(entity, MeleeAttachments.COMBAT_STATE, state);
            ACTIVE.putIfAbsent(entity, this);
        }
        @Override public @Nullable WeaponDefinition weapon() { return MeleeService.weapon(entity); }
        @Override public Vec eye() { return vec(entity.getEyePosition()); }
        @Override public Vec look() { return vec(entity.getViewVector(1.0f)); }
        @Override public Box box() {
            AABB b = entity.getBoundingBox();
            return new Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
        }
        @Override public boolean valid() { return entity.isAlive() && !entity.isRemoved(); }
    }
}

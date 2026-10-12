package com.richardsenger.piratesnships.mob.monsters;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.entity.Sailor;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The monsters' side of MOB3 ({@link MonsterRules}): every monster of {@link #HUNTS_NPCS} that joins a server level gets
 * two target goals beside vanilla's villager goal (same priority, not line-of-sight bound, like it): one for crew
 * members, one for sailors and harbor masters. Each candidate passes {@link #hunts}. A {@code CommonEvents.ENTITY_JOIN_LEVEL}
 * listener; loaded entities join again, so the goals are added once per entity instance.
 */
public final class MonsterTargets {

    /** Monsters that hunt the mod's people as they hunt villagers (the zombie family, the illagers, the ravager). */
    public static final TagKey<EntityType<?>> HUNTS_NPCS = TagKey.create(Registries.ENTITY_TYPE, Constants.id("hunts_npcs"));

    /** Vanilla's priority of the villager goal in zombies, drowned and illagers. */
    static final int PRIORITY = 3;
    /** Vanilla's {@code randomInterval} of a target goal (one scan in about ten ticks). */
    private static final int RANDOM_INTERVAL = 10;

    private MonsterTargets() {
    }

    /** {@code ENTITY_JOIN_LEVEL}: arms a hunting monster with the NPC goals. Never cancels the join. */
    public static boolean onJoin(Entity entity, Level level) {
        if (level.isClientSide() || !(entity instanceof PathfinderMob mob) || !mob.getType().is(HUNTS_NPCS)) return false;
        arm(mob);
        return false;
    }

    /** Adds the two NPC target goals to {@code mob} unless it has them already. Returns whether they were added. */
    public static boolean arm(PathfinderMob mob) {
        for (WrappedGoal g : mob.targetSelector.getAvailableGoals()) {
            if (g.getGoal() instanceof NpcTargetGoal<?>) return false;
        }
        mob.targetSelector.addGoal(PRIORITY, new NpcTargetGoal<>(mob, CrewMember.class));
        mob.targetSelector.addGoal(PRIORITY, new NpcTargetGoal<>(mob, Sailor.class));
        return true;
    }

    /** Whether {@code monster} hunts {@code target} now ({@link MonsterRules#hunts}, config read when asked). */
    public static boolean hunts(Mob monster, LivingEntity target) {
        boolean person = target instanceof CrewMember || target instanceof Sailor;
        if (!person || !MobConfig.MONSTERS_ATTACK_NPCS.get()) return false;
        return MonsterRules.hunts(true, true, shipOf(target), shipOf(monster));
    }

    /** Blocks below the feet within which a ship block makes an entity count as aboard (deck, or a hold floor). */
    static final int ABOARD_DEPTH = 3;

    /**
     * The ship {@code e} is aboard, decided from where it is, the same way for the monster and its prey. Sable's
     * tracking alone is no good here: it is a contact flag set while the entity moves against the deck and dropped
     * on any tick without contact (a hop, a wave lifting the hull away), so a person standing on a deck can read as
     * ashore for a tick. Order: the ship whose blocks lie under its feet (its feet mapped into the ship's plot, up to
     * {@link #ABOARD_DEPTH} blocks down: the deck, or the floor of a hold), else the ship of its vehicle (a station or
     * hammock seat), else the ship Sable tracks it on; {@code null} when none.
     */
    public static @Nullable UUID shipOf(Entity e) {
        if (e.level() instanceof ServerLevel level) {
            Vec3 feet = e.position();
            for (ShipBody ship : SableShips.all(level)) {
                if (!ship.worldBounds().inflate(0.5, ABOARD_DEPTH, 0.5).contains(feet)) continue;
                BlockPos plot = BlockPos.containing(ship.toPlot(feet.add(0, 0.01, 0)));
                for (int dy = 0; dy <= ABOARD_DEPTH; dy++) {
                    BlockPos p = plot.below(dy);
                    if (ship.plotContains(p) && !level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return ship.id();
                }
            }
        }
        ShipBody ship = ShipEntities.vehicleShip(e);
        if (ship == null) ship = ShipEntities.standingOrRiding(e);
        return ship != null ? ship.id() : null;
    }

    /** Vanilla's nearest-target goal for one kind of person, filtered by {@link #hunts} on picking and on keeping. */
    static final class NpcTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {

        NpcTargetGoal(Mob mob, Class<T> type) {
            super(mob, type, RANDOM_INTERVAL, false, false, t -> hunts(mob, t));
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = mob.getTarget();
            return super.canContinueToUse() && t != null && hunts(mob, t);
        }
    }
}

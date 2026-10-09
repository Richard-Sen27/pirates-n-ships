package com.richardsenger.piratesnships.mob;

import com.richardsenger.piratesnships.combat.melee.MeleeService;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The synced melee pose of a {@link MeleePosed} entity (MEL3: shared by {@code SeafarerMob} and {@code CrewMember}, so
 * the mapping from the duelist's state to the pose lives here once). The owner defines an {@code Integer} accessor in
 * its own class ({@code SynchedEntityData.defineId}) and an {@code int} field for when the pose last changed, then calls
 * <ul>
 *   <li>{@link #define} from {@code defineSynchedData},</li>
 *   <li>{@link #update} every server AI step: the melee engine's state ({@link MeleeService#state}, the attachment the
 *       duelist goals drive: wind-up, hit frames, recovery, guard, parry, stagger, riposte, feint) becomes the pose,
 *       and {@link MeleePose#IDLE} once the entity is out of the engine (the fight ended); the data only sends on a
 *       change, so once per phase,</li>
 *   <li>{@link #pose} and {@link #progress} for the client model; the owner stamps its {@code tickCount} into its
 *       field from {@code onSyncedDataUpdated} when the accessor changes.</li>
 * </ul>
 * Static, so nothing here depends on instance fields (vanilla calls {@code onSyncedDataUpdated} from the entity's
 * constructor, before a subclass's fields are set).
 */
public final class MeleePoseSync {

    private MeleePoseSync() {
    }

    /**
     * The pose of a combatant: idle when it is not in the melee engine ({@code active} false), else its state's pose.
     * The one mapping every duelist shares. Pure.
     */
    public static MeleePose poseOf(boolean active, @Nullable CombatState state) {
        return active && state != null ? MeleePose.of(state) : MeleePose.IDLE;
    }

    /** The pose of {@code entity} now, from the melee engine (server). */
    public static MeleePose current(LivingEntity entity) {
        boolean active = MeleeService.isActive(entity);
        return poseOf(active, active ? MeleeService.state(entity) : null);
    }

    public static void define(SynchedEntityData.Builder builder, EntityDataAccessor<Integer> accessor) {
        builder.define(accessor, MeleePose.IDLE.pack());
    }

    /** Server, every AI step: mirrors the melee engine's state of {@code entity} into its synced {@code accessor}. */
    public static void update(LivingEntity entity, EntityDataAccessor<Integer> accessor) {
        entity.getEntityData().set(accessor, current(entity).pack()); // only sends when it changes
    }

    public static MeleePose pose(SynchedEntityData data, EntityDataAccessor<Integer> accessor) {
        return MeleePose.unpack(data.get(accessor));
    }

    /** 0..1 through {@code pose}, {@code ticks} (plus the partial tick) after it began; open-ended phases report 1. Pure. */
    public static float progress(MeleePose pose, int ticks, float partialTick) {
        if (pose.duration() <= 0) return 1f;
        return Math.max(0f, Math.min(1f, (ticks + partialTick) / pose.duration()));
    }
}

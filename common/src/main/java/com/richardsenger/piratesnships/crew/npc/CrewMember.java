package com.richardsenger.piratesnships.crew.npc;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.seat.StationSeat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Test crew member of spike 4 (docs/design.md §7, §9): a plain humanoid mob with a persistent station assignment.
 * Assigned, it rides the station's seat ({@link StationSeat}) and stands still; unassigned it strolls and looks
 * around. Hiring, wages, skills and morale come later.
 */
public class CrewMember extends PathfinderMob {

    static final String TAG_ASSIGNMENT = Constants.MOD_ID + ":assignment";

    private @Nullable StationRef assignment;

    public CrewMember(EntityType<? extends CrewMember> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6) {
            @Override
            public boolean canUse() {
                return assignment == null && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public @Nullable StationRef assignment() {
        return assignment;
    }

    /** Sets the assignment only; use {@link CrewStations} to assign or release with seat handling. */
    void setAssignment(@Nullable StationRef ref) {
        this.assignment = ref;
    }

    /** True when this crew member rides the seat of its assigned station. */
    public boolean isAtStation() {
        return assignment != null && getVehicle() instanceof StationSeat seat && seat.station().equals(assignment.pos());
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level && assignment != null) {
            if (isAtStation()) {
                CrewStations.keepOccupied(level, this);
            } else if (tickCount % StationConfig.SEAT_CHECK_INTERVAL.get() == 0) {
                CrewStations.ensureSeated(level, this);
            }
        }
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        // free the station at once, not after the death animation
        if (!level().isClientSide && assignment != null) {
            CrewStations.onCrewGone(this);
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && assignment != null) {
            CrewStations.onCrewGone(this);
        }
        super.remove(reason);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (assignment != null) {
            StationRef.CODEC.encodeStart(NbtOps.INSTANCE, assignment).ifSuccess(t -> tag.put(TAG_ASSIGNMENT, t));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        assignment = tag.contains(TAG_ASSIGNMENT)
                ? StationRef.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_ASSIGNMENT)).result().orElse(null)
                : null;
    }
}

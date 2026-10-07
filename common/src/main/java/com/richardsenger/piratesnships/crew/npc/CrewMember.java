package com.richardsenger.piratesnships.crew.npc;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.seat.StationSeat;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Test crew member of spike 4 (docs/design.md §7, §9): a plain humanoid mob with a persistent station assignment.
 * Assigned, it rides the station's seat ({@link StationSeat}) and stands still; unassigned it strolls and looks
 * around. Hiring, wages, skills and morale come later.
 * <p>
 * Animated with GeckoLib (M1): one controller {@code body} loops the {@link CrewPose} animation of the rig contract
 * ({@code art/README.md}, "Entities"). The controller runs on the client only; the server's part is the synced
 * {@link #isWorking()} flag. This class and {@code crew/npc/client/} are the only GeckoLib importers.
 */
public class CrewMember extends PathfinderMob implements GeoEntity {

    static final String TAG_ASSIGNMENT = Constants.MOD_ID + ":assignment";
    static final String TAG_PINNED = Constants.MOD_ID + ":pinned";

    /** Ticks GeckoLib blends from one pose animation into the next. */
    private static final int POSE_TRANSITION_TICKS = 5;

    /** True while the station of this crew member carries out an order; set by the server, read by the animation. */
    private static final EntityDataAccessor<Boolean> DATA_WORKING = SynchedEntityData.defineId(CrewMember.class, EntityDataSerializers.BOOLEAN);

    private static final Map<CrewPose, RawAnimation> POSE_ANIMATIONS = new EnumMap<>(CrewPose.class);

    static {
        for (CrewPose pose : CrewPose.values()) {
            POSE_ANIMATIONS.put(pose, RawAnimation.begin().thenLoop(pose.animation()));
        }
    }

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private @Nullable StationRef assignment;
    /** Assigned by hand (whistle, command): the job board never moves it (CR1). Server only, saved. */
    private boolean pinned;

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

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WORKING, false);
    }

    /** Whether this crew member is at its station while the station carries out an order (synced to clients). */
    public boolean isWorking() {
        return entityData.get(DATA_WORKING);
    }

    /**
     * Whether it rides something that seats it. The station seat does not: at a station the crew member stands (and
     * the client may not even know the seat, which lives in the ship's plot).
     */
    public boolean isSeated() {
        return getVehicle() != null && !(getVehicle() instanceof StationSeat);
    }

    /** The pose for the animation, from the synced state and the leg movement the renderer measured. */
    public CrewPose pose(boolean legsMoving) {
        return CrewPose.choose(legsMoving, isWorking(), isSeated());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", POSE_TRANSITION_TICKS,
                state -> state.setAndContinue(POSE_ANIMATIONS.get(pose(state.isMoving())))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    public @Nullable StationRef assignment() {
        return assignment;
    }

    /** Sets the assignment only; use {@link CrewStations} to assign or release with seat handling. */
    void setAssignment(@Nullable StationRef ref) {
        this.assignment = ref;
    }

    /**
     * Whether this crew member was assigned by hand (whistle or command) and the job board leaves it where it is
     * (docs/design.md §7.2, CR1). False when it is free or was put at its station by the board.
     */
    public boolean isPinned() {
        return assignment != null && pinned;
    }

    /** Sets the pinned flag only; {@link CrewStations} sets it with the assignment. */
    void setPinned(boolean pinned) {
        this.pinned = pinned;
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
        if (!level().isClientSide) {
            entityData.set(DATA_WORKING, operatingHere()); // only sends when the value changes
        }
    }

    /** Server: seated at its station, which is carrying out an order for this crew member. */
    private boolean operatingHere() {
        if (assignment == null || !isAtStation()) {
            return false;
        }
        StationState<Object> state = Stations.state(assignment);
        return state != null && state.phase() == StationState.Phase.OPERATING && state.isOccupiedBy(getUUID());
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
            tag.putBoolean(TAG_PINNED, pinned);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        assignment = tag.contains(TAG_ASSIGNMENT)
                ? StationRef.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_ASSIGNMENT)).result().orElse(null)
                : null;
        // a crew member saved before CR1 was always assigned by hand
        pinned = assignment != null && (!tag.contains(TAG_PINNED) || tag.getBoolean(TAG_PINNED));
    }
}

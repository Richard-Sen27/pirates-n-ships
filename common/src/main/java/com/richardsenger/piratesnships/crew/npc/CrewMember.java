package com.richardsenger.piratesnships.crew.npc;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.crew.galley.MealSeat;
import com.richardsenger.piratesnships.crew.hammock.CrewRest;
import com.richardsenger.piratesnships.crew.hammock.HammockRef;
import com.richardsenger.piratesnships.crew.hammock.SleepAxis;
import com.richardsenger.piratesnships.crew.morale.MoraleRules;
import com.richardsenger.piratesnships.crew.morale.NightOutcome;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.seat.StationSeat;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
 * around. Hiring, wages and skills come later.
 * <p>
 * HM1 (docs/design.md §7.1): it also keeps its morale ({@code crew.morale.CrewMorale}, the only writer), its hammock
 * for the night ({@link #rest()}, set by {@code crew.hammock.CrewRest}) and how it spends the night
 * ({@link #nightOutcome()}), all saved; {@link #isResting()} is synced for the lying pose.
 * <p>
 * Animated with GeckoLib (M1): one controller {@code body} loops the {@link CrewPose} animation of the rig contract
 * ({@code art/README.md}, "Entities"). The controller runs on the client only; the server's part is the synced
 * {@link #isWorking()} flag and, since ART7, the synced station pose ({@link #stationPose()}, {@link StationPoses}),
 * with which the crew member also turns to face its wheel or gun. CR2 adds its low-morale day counter and whether it went unpaid at the last dawn (saved); CRW1 the player who hired it ({@link #hiredBy()}, saved); CRW2 its deserting mark
 * ({@link #isDeserting()}, saved) and whether it sits at a meal ({@link #isEating()}, synced).
 * This class and {@code crew/npc/client/} are the only GeckoLib importers.
 */
public class CrewMember extends PathfinderMob implements GeoEntity {

    static final String TAG_ASSIGNMENT = Constants.MOD_ID + ":assignment";
    static final String TAG_PINNED = Constants.MOD_ID + ":pinned";
    static final String TAG_MORALE = Constants.MOD_ID + ":morale";
    static final String TAG_REST = Constants.MOD_ID + ":rest";
    static final String TAG_NIGHT = Constants.MOD_ID + ":night";
    static final String TAG_LOW_MORALE_DAYS = Constants.MOD_ID + ":low_morale_days";
    static final String TAG_UNPAID = Constants.MOD_ID + ":unpaid";
    static final String TAG_HIRED_BY = Constants.MOD_ID + ":hired_by";
    static final String TAG_DESERTING = Constants.MOD_ID + ":deserting";
    static final String TAG_DESERTING_DAYS = Constants.MOD_ID + ":deserting_days";
    static final String TAG_STATIONARY = Constants.MOD_ID + ":stationary";

    /** Ticks GeckoLib blends from one pose animation into the next. */
    private static final int POSE_TRANSITION_TICKS = 5;

    /** True while the station of this crew member carries out an order; set by the server, read by the animation. */
    private static final EntityDataAccessor<Boolean> DATA_WORKING = SynchedEntityData.defineId(CrewMember.class, EntityDataSerializers.BOOLEAN);
    /** True while it lies in a hammock (HM1); set by the server, read by the animation. */
    private static final EntityDataAccessor<Boolean> DATA_RESTING = SynchedEntityData.defineId(CrewMember.class, EntityDataSerializers.BOOLEAN);
    /** The station pose's synced id ({@link CrewPose#stationId()}), -1 for none (ART7); set by the server. */
    private static final EntityDataAccessor<Byte> DATA_STATION_POSE = SynchedEntityData.defineId(CrewMember.class, EntityDataSerializers.BYTE);
    /** True while it sits at a meal beside the pantry or water barrel (CRW2); set by the server, read by the animation. */
    private static final EntityDataAccessor<Boolean> DATA_EATING = SynchedEntityData.defineId(CrewMember.class, EntityDataSerializers.BOOLEAN);

    private static final Map<CrewPose, RawAnimation> POSE_ANIMATIONS = new EnumMap<>(CrewPose.class);

    static {
        for (CrewPose pose : CrewPose.values()) {
            POSE_ANIMATIONS.put(pose, pose.loops()
                    ? RawAnimation.begin().thenLoop(pose.animation())
                    : RawAnimation.begin().thenPlay(pose.animation()));
        }
    }

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private @Nullable StationRef assignment;
    /** Assigned by hand (whistle, command): the job board never moves it (CR1). Server only, saved. */
    private boolean pinned;
    /** Morale 0..100, or {@link MoraleRules#UNSET} (reads as the configured start). Server only, saved. */
    private int morale = MoraleRules.UNSET;
    /** Its hammock while it sleeps (HM1). Server only, saved. */
    private @Nullable HammockRef rest;
    /** How it spends the current night, settled at dawn (HM1). Server only, saved. */
    private NightOutcome nightOutcome = NightOutcome.NONE;
    /** Consecutive dawns with morale below {@code crew.desertion.desert_below} (CR2). Server only, saved. */
    private int lowMoraleDays;
    /** It was not paid at the last dawn (CR2). Server only, saved. */
    private boolean unpaid;
    /** The player who hired it at a harbor desk (CRW1), who may dismiss it. Server only, saved. */
    private Optional<UUID> hiredBy = Optional.empty();
    /** It deserts and leaves at the next port (CRW2, {@code crew.upkeep.Desertions}). Server only, saved. */
    private boolean deserting;
    /** Dawns it has been deserting (CRW2). Server only, saved. */
    private int desertingDays;
    /** It never strolls when idle (WS3c: the deckhands of an NPC ship at sea). Off for every player's crew. Saved. */
    private boolean stationary;
    /** Its station pose faces it towards the wheel or gun (ART7): the look goals leave its head alone. Server only. */
    private boolean facingStation;

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
                return assignment == null && rest == null && !stationary && !isPassenger() && super.canUse();
            }
        });
        // HM2: a sleeper keeps its head and body along the hammock (CrewRest#orient); awake, it looks around again
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f) {
            @Override
            public boolean canUse() {
                return mayLookAround() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return mayLookAround() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return mayLookAround() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return mayLookAround() && super.canContinueToUse();
            }
        });
    }

    /**
     * Whether its look goals (look at a player, look around) may turn its head: not while it lies in a hammock (HM2),
     * where {@code crew.hammock.CrewRest#orient} holds head and body along the hammock.
     */
    public boolean mayLookAround() {
        return !isResting() && !facingStation && !isEating();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WORKING, false);
        builder.define(DATA_RESTING, false);
        builder.define(DATA_STATION_POSE, (byte) -1);
        builder.define(DATA_EATING, false);
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

    /**
     * Whether it sits at a meal beside the pantry or water barrel (CRW2, {@code crew.galley.MealVisits}; synced, the
     * client may not know the meal seat in the plot). It shows the {@link CrewPose#WORK} pose until the rig has an
     * eating clip.
     */
    public boolean isEating() {
        return entityData.get(DATA_EATING);
    }

    /** Whether it lies in a hammock (synced to clients; the client may not know the hammock seat in the plot). */
    public boolean isResting() {
        return entityData.get(DATA_RESTING);
    }

    /**
     * The pose for the animation, from the synced state and the leg movement the renderer measured. In a hammock it
     * plays {@link CrewPose#SLEEP} (ART1d), lying along the hammock ({@code crew.hammock.CrewRest#orient}, HM2).
     */
    public CrewPose pose(boolean legsMoving) {
        return CrewPose.choose(legsMoving, isWorking() || isEating(), isSeated(), isResting(), stationPose());
    }

    /**
     * The pose its station shows (ART7: helmsman, gun crew; {@link StationPoses}), synced; null when it is not at a
     * station or its station has no pose for what it is doing now.
     */
    public @Nullable CrewPose stationPose() {
        return CrewPose.byStationId(entityData.get(DATA_STATION_POSE));
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

    /** Stored morale, {@link MoraleRules#UNSET} when never set; read it through {@code CrewMorale#get}. */
    public int storedMorale() {
        return morale;
    }

    /** Only {@code crew.morale.CrewMorale#adjust} calls this. */
    public void setStoredMorale(int morale) {
        this.morale = morale;
    }

    /** Its hammock while it sleeps there (HM1), else null. */
    public @Nullable HammockRef rest() {
        return rest;
    }

    /** Sets the hammock only; {@code crew.hammock.CrewRest} seats it there and gets it up. */
    public void setRest(@Nullable HammockRef rest) {
        this.rest = rest;
        if (!level().isClientSide) {
            entityData.set(DATA_RESTING, rest != null);
        }
    }

    /** How it spends the current night (HM1); {@link NightOutcome#NONE} during the day. */
    public NightOutcome nightOutcome() {
        return nightOutcome;
    }

    public void setNightOutcome(NightOutcome nightOutcome) {
        this.nightOutcome = nightOutcome;
    }

    /** Consecutive dawns with low morale (CR2, {@code crew.upkeep.ShipDayTick}); 0 when its morale is fine. */
    public int lowMoraleDays() {
        return lowMoraleDays;
    }

    public void setLowMoraleDays(int days) {
        this.lowMoraleDays = Math.max(0, days);
    }

    /** Whether it went unpaid at the last dawn (CR2). */
    public boolean isUnpaid() {
        return unpaid;
    }

    public void setUnpaid(boolean unpaid) {
        this.unpaid = unpaid;
    }

    /** The player who hired it at a harbor desk (CRW1, {@code crew.hiring.Hiring}); empty for crew from elsewhere. */
    public Optional<UUID> hiredBy() {
        return hiredBy;
    }

    public void setHiredBy(Optional<UUID> hiredBy) {
        this.hiredBy = hiredBy;
    }

    /**
     * Whether it deserts and walks off at the next port (CRW2, {@code crew.upkeep.DesertionRules}); set at the dawn it
     * deserts, cleared when a dawn finds its morale fine again.
     */
    public boolean isDeserting() {
        return deserting;
    }

    /** Dawns it has been deserting; 0 when it is not. */
    public int desertingDays() {
        return deserting ? desertingDays : 0;
    }

    /** Sets the deserting mark and its dawns ({@code crew.upkeep.ShipDayTick}). */
    public void setDeserting(boolean deserting, int days) {
        this.deserting = deserting;
        this.desertingDays = deserting ? Math.max(0, days) : 0;
    }

    /**
     * A stationary crew member doesn't stroll about when idle (WS3c: an NPC ship's deckhands, who would walk off the
     * deck); stations, hammocks, meals and orders still move it. Saved with the entity; false for hired crew.
     */
    public void setStationary(boolean stationary) {
        this.stationary = stationary;
    }

    public boolean isStationary() {
        return stationary;
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
        if (level() instanceof ServerLevel level) {
            CrewRest.tick(level, this);
            entityData.set(DATA_WORKING, operatingHere()); // only sends when the value changes
            entityData.set(DATA_EATING, getVehicle() instanceof MealSeat);
            showStationPose(level);
        }
    }

    /** Server, every tick: the station pose ({@link StationPoses}) into the synced data, and the turn to face it. */
    private void showStationPose(ServerLevel level) {
        StationPoses.Shown shown = assignment != null && isAtStation() && getVehicle() instanceof StationSeat seat
                ? StationPoses.resolve(level, assignment, seat.blockPosition()) : null;
        entityData.set(DATA_STATION_POSE, (byte) (shown == null ? -1 : shown.pose().stationId()));
        facingStation = shown != null && shown.facing() != null;
        if (facingStation) {
            ShipBody ship = SableShips.byId(level, assignment.ship());
            float yaw = SleepAxis.continuous(getYRot(), StationPoses.yaw(shown.facing(), ship == null ? null : ship.orientation()));
            setYRot(yaw);
            setYHeadRot(yaw);
            setYBodyRot(yaw);
            setXRot(0);
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
        if (morale != MoraleRules.UNSET) {
            tag.putInt(TAG_MORALE, morale);
        }
        if (rest != null) {
            HammockRef.CODEC.encodeStart(NbtOps.INSTANCE, rest).ifSuccess(t -> tag.put(TAG_REST, t));
        }
        if (nightOutcome != NightOutcome.NONE) {
            tag.putString(TAG_NIGHT, nightOutcome.id());
        }
        if (lowMoraleDays > 0) {
            tag.putInt(TAG_LOW_MORALE_DAYS, lowMoraleDays);
        }
        if (unpaid) {
            tag.putBoolean(TAG_UNPAID, true);
        }
        hiredBy.ifPresent(id -> tag.putUUID(TAG_HIRED_BY, id));
        if (deserting) {
            tag.putBoolean(TAG_DESERTING, true);
            tag.putInt(TAG_DESERTING_DAYS, desertingDays);
        }
        if (stationary) {
            tag.putBoolean(TAG_STATIONARY, true);
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
        morale = tag.contains(TAG_MORALE) ? MoraleRules.clamp(tag.getInt(TAG_MORALE)) : MoraleRules.UNSET;
        setRest(tag.contains(TAG_REST) ? HammockRef.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_REST)).result().orElse(null) : null);
        nightOutcome = tag.contains(TAG_NIGHT) ? NightOutcome.byId(tag.getString(TAG_NIGHT)) : NightOutcome.NONE;
        lowMoraleDays = Math.max(0, tag.getInt(TAG_LOW_MORALE_DAYS));
        unpaid = tag.getBoolean(TAG_UNPAID);
        hiredBy = tag.hasUUID(TAG_HIRED_BY) ? Optional.of(tag.getUUID(TAG_HIRED_BY)) : Optional.empty();
        setDeserting(tag.getBoolean(TAG_DESERTING), tag.getInt(TAG_DESERTING_DAYS));
        stationary = tag.getBoolean(TAG_STATIONARY);
    }
}

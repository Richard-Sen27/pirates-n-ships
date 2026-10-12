package com.richardsenger.piratesnships.mob.village;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.mob.MobKind;
import com.richardsenger.piratesnships.mob.entity.Sailor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A villager of a seafarer village (LIFE1, docs/design.md §10.1): a sailor with a {@link VillageRole} (synced, its
 * outfit on the client) and a {@link ResidentPlan} (its workstations, bed, seats and stall, saved), placed by world
 * generation ({@link Residents}) in the building it belongs to. Its day is {@link ResidentRoutineGoal}: work at its
 * stations, sit at dusk, sleep at night. Like any sailor it never fights, runs from monsters and pirates and panics when
 * hurt (MOB3's monsters hunt it as they hunt sailors), and the routine gives way to all of that. Talking to it opens its
 * stall or says a word; sneak-using it twice hires it ({@link ResidentInteractions}). Its slot is filled again
 * {@code mobs.villagers.respawn_days} after it died or was hired away.
 *
 * <p>The client's pose: {@code sleep} in bed and {@code work} at a station (the synced {@link #shownPose}), {@code sit}
 * on its bench seat (a vehicle), else walk or idle, through the crew's {@link CrewPose#choose}.
 */
public class VillageResident extends Sailor {

    private static final String TAG_ROLE = Constants.MOD_ID + ":role";
    private static final String TAG_PORT = Constants.MOD_ID + ":port";
    private static final String TAG_PLAN = Constants.MOD_ID + ":plan";
    private static final String TAG_LAMPS = Constants.MOD_ID + ":lamps";

    /** Synced pose shown by the client: 0 none, 1 {@code work}, 2 {@code sleep}. */
    public static final byte POSE_NONE = 0, POSE_WORK = 1, POSE_SLEEP = 2;

    private static final EntityDataAccessor<Byte> DATA_ROLE = SynchedEntityData.defineId(VillageResident.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_POSE = SynchedEntityData.defineId(VillageResident.class, EntityDataSerializers.BYTE);

    private static final int POSE_TRANSITION_TICKS = 5;
    private static final Map<CrewPose, RawAnimation> POSE_ANIMATIONS = new EnumMap<>(CrewPose.class);

    static {
        for (CrewPose pose : CrewPose.values()) {
            POSE_ANIMATIONS.put(pose, pose.loops() ? RawAnimation.begin().thenLoop(pose.animation()) : RawAnimation.begin().thenPlay(pose.animation()));
        }
    }

    private @Nullable ResourceLocation port;
    private @Nullable ResidentPlan plan;
    /** The lamplighter's lamp posts (the lanterns), found once ({@link Residents#lampsNear}); saved. */
    private @Nullable List<BlockPos> lamps;
    /** The body yaw the routine holds it to (at a station, in bed, on a seat), NaN while it is free. Server only. */
    private float heldYaw = Float.NaN;
    /** Who offered to hire it and until which game time (a second sneak-use within it hires). Memory only. */
    private @Nullable UUID hireOffer;
    private long hireOfferUntil;

    public VillageResident(EntityType<? extends VillageResident> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Sailor.createAttributes();
    }

    @Override
    public MobKind kind() {
        return MobKind.VILLAGER;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROLE, (byte) VillageRole.VILLAGER.ordinal());
        builder.define(DATA_POSE, POSE_NONE);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // asleep it keeps its head on the pillow: the look goals wait until it is up again
        goalSelector.removeAllGoals(g -> g instanceof LookAtPlayerGoal || g instanceof RandomLookAroundGoal);
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f) {
            @Override
            public boolean canUse() {
                return mayLookAround() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return mayLookAround() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(8, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return mayLookAround() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return mayLookAround() && super.canContinueToUse();
            }
        });
        // after panic (1) and the flight from danger (2), before strolling (6): the day's routine
        goalSelector.addGoal(4, new ResidentRoutineGoal(this));
    }

    /** Whether the look goals may turn its head: not while it lies in bed. */
    public boolean mayLookAround() {
        return shownPose() != POSE_SLEEP;
    }

    // --- role and plan ------------------------------------------------------------------------------------------

    public VillageRole role() {
        return VillageRole.byOrdinal(entityData.get(DATA_ROLE));
    }

    public void setRole(VillageRole role) {
        entityData.set(DATA_ROLE, (byte) role.ordinal());
    }

    /** The port of its village ({@code null}: one spawned outside any village, e.g. by a command). */
    public @Nullable ResourceLocation port() {
        return port;
    }

    /**
     * Its plan; a villager spawned by hand gets one on its own spot (no bed, no seats) the first time the routine asks.
     */
    public ResidentPlan plan() {
        if (plan == null) {
            Direction facing = Direction.fromYRot(getYRot());
            plan = ResidentPlan.standalone(role(), blockPosition(), facing.getAxis().isHorizontal() ? facing : Direction.NORTH);
        }
        return plan;
    }

    public boolean hasPlan() {
        return plan != null;
    }

    /** Binds it to {@code port}'s slot of {@code plan}, with the plan's role. */
    public void assign(@Nullable ResourceLocation port, ResidentPlan plan) {
        this.port = port;
        this.plan = plan;
        this.lamps = null;
        setRole(plan.role());
    }

    public @Nullable List<BlockPos> lamps() {
        return lamps;
    }

    public void setLamps(List<BlockPos> lamps) {
        this.lamps = List.copyOf(lamps);
    }

    // --- pose ---------------------------------------------------------------------------------------------------

    /** The synced pose ({@link #POSE_NONE}, {@link #POSE_WORK}, {@link #POSE_SLEEP}). */
    public byte shownPose() {
        return entityData.get(DATA_POSE);
    }

    /** Server: sets the pose the client shows. */
    public void showPose(byte pose) {
        entityData.set(DATA_POSE, pose);
    }

    /** Whether it lies in bed (synced). */
    public boolean isAsleep() {
        return shownPose() == POSE_SLEEP;
    }

    /** Whether it works at a station (synced). */
    public boolean isWorking() {
        return shownPose() == POSE_WORK;
    }

    /** Server: holds its body, head and view at {@code yaw} (NaN: lets go). */
    public void holdYaw(float yaw) {
        this.heldYaw = yaw;
        if (!Float.isNaN(yaw)) applyHeldYaw(true);
    }

    private void applyHeldYaw(boolean snap) {
        setYRot(heldYaw);
        setYBodyRot(heldYaw);
        if (isAsleep()) {
            setYHeadRot(heldYaw);
            setXRot(0);
        }
        if (snap) {
            yRotO = heldYaw;
            yBodyRotO = heldYaw;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && !Float.isNaN(heldYaw)) applyHeldYaw(false);
    }

    /** The body animation from the synced state and the leg movement the renderer measured. */
    public CrewPose pose(boolean legsMoving) {
        return CrewPose.choose(legsMoving, isWorking(), getVehicle() != null, isAsleep());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", POSE_TRANSITION_TICKS,
                state -> state.setAndContinue(POSE_ANIMATIONS.get(pose(state.isMoving())))));
    }

    // --- talking and hiring -------------------------------------------------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        InteractionResult r = ResidentInteractions.interact(player, this, hand);
        return r == InteractionResult.PASS ? super.mobInteract(player, hand) : r;
    }

    /** Remembers that {@code player} was told the fee, until game time {@code until}. */
    void offerHire(Player player, long until) {
        hireOffer = player.getUUID();
        hireOfferUntil = until;
    }

    /** Whether {@code player} was told the fee a moment ago (and takes the offer up now). */
    boolean hasHireOffer(Player player) {
        return hireOffer != null && hireOffer.equals(player.getUUID()) && level().getGameTime() <= hireOfferUntil;
    }

    void clearHireOffer() {
        hireOffer = null;
    }

    // --- death and removal --------------------------------------------------------------------------------------

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && dead) Residents.onLost(this);
    }

    /** Discarded alive (hired away, a command, a disabled config, a test): its slot is free. */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason == RemovalReason.DISCARDED && !dead) Residents.onLost(this);
        super.remove(reason);
    }

    // --- persistence --------------------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_ROLE, role().id());
        if (port != null) tag.putString(TAG_PORT, port.toString());
        if (plan != null) ResidentPlan.CODEC.encodeStart(NbtOps.INSTANCE, plan).ifSuccess(t -> tag.put(TAG_PLAN, t));
        if (lamps != null) BlockPos.CODEC.listOf().encodeStart(NbtOps.INSTANCE, lamps).ifSuccess(t -> tag.put(TAG_LAMPS, t));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setRole(VillageRole.byId(tag.getString(TAG_ROLE)).orElse(VillageRole.VILLAGER));
        port = tag.contains(TAG_PORT) ? ResourceLocation.tryParse(tag.getString(TAG_PORT)) : null;
        plan = tag.contains(TAG_PLAN) ? ResidentPlan.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_PLAN)).result().orElse(null) : null;
        lamps = tag.contains(TAG_LAMPS) ? BlockPos.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get(TAG_LAMPS)).result().orElse(null) : null;
    }
}
